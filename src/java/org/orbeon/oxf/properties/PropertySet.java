/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.properties;

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.net.URI;
import java.util.*;

/**
 * Represent a set of properties.
 *
 * A property name can be exact, e.g. foo.bar.gaga, or it can contain wildcards, like ".*.bar.gaga", "foo.*.gaga", or
 * "foo.bar.*", or "*.bar.*", etc.
 */
public class PropertySet {

    public static class Property {
        public final QName type;
        public final Object value;
        public final Map<String, String> namespaces;

        public Property(final QName type, final Object value, final Map<String, String> namespaces) {
            this.type = type;
            this.value = value;
            this.namespaces = namespaces;
        }
    }

    private static class PropertyNode {
        public Property property;
        public Map<String, PropertyNode> children;// Map<String, PropertyNode> of token to property node
    }

    private Map<String, Property> exactProperties = new HashMap<String, Property>();// Map<String, TypeValue> of property name to typed value
    private PropertyNode wildcardProperties = new PropertyNode();

    /**
     * Return the set of property names.
     *
     * @return set of property names
     */
    public Set<String> keySet() {
        return exactProperties.keySet();
    }

    /**
     * Return the number of properties.
     *
     * @return number of properties
     */
    public int size() {
        return exactProperties.size();
    }

    /**
     * Return an unmodifiable Map<String, Boolean> of all Boolean properties.
     */
    public Map<String, Boolean> getBooleanProperties() {
        if (size() > 0) {
            final Map<String, Boolean> result = new HashMap<String, Boolean>();
            for (String key : keySet()) {
                final Object o = getObject(key);
                if (o instanceof Boolean)
                    result.put(key, (Boolean) o);
            }
            return Collections.unmodifiableMap(result);
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Set a property. Used by PropertyStore.
     *
     * @param element         Element on which the property is defined. Used for QName resolution if needed.
     * @param name            property name
     * @param type            property type, or null
     * @param stringValue     property string value
     */
    public void setProperty(final Element element, String name, final QName type, String stringValue) {

        final Object value = PropertyStore.getObjectFromStringValue(stringValue, type, element);
        final Property property = new Property(type, value, Dom4jUtils.getNamespaceContext(element));

        // Store exact property name anyway
        exactProperties.put(name, property);

        // Also store in tree (in all cases, not only when contains wildcard, so we get find all the properties that start with some token)
        final StringTokenizer st = new StringTokenizer(name, ".");
        PropertyNode currentNode = wildcardProperties;
        while (st.hasMoreTokens()) {
            final String currentToken = st.nextToken();
            if (currentNode.children == null) {
                currentNode.children = new LinkedHashMap<String, PropertyNode>();
            }
            PropertyNode newNode = currentNode.children.get(currentToken);
            if (newNode == null) {
                newNode = new PropertyNode();
                currentNode.children.put(currentToken, newNode);
            }
            currentNode = newNode;
        }

        // Store value
        currentNode.property = property;
    }


    private List<String> getPropertiesStartsWithWorker(PropertyNode propertyNode, String consumed, String[] tokens, int currentTokenPosition) {
        final List<String> result = new ArrayList<String>();
        final String token = currentTokenPosition >= tokens.length ? null : tokens[currentTokenPosition];

        if (token == null || "*".equals(token)) {
            if (propertyNode.children == null && token == null) {
                result.add(consumed);
            }

            // Go through all children
            if (propertyNode.children != null) {
                for (final String key: propertyNode.children.keySet()) {
                    final String newConsumed = consumed.length() == 0 ? key : consumed + "." + key;
                    final List<String> keyProperties = getPropertiesStartsWithWorker(propertyNode.children.get(key), newConsumed, tokens, currentTokenPosition + 1);
                    result.addAll(keyProperties);
                }
            }
        } else {
            // Regular token
            final PropertyNode[] newPropertyNodes = new PropertyNode[2];
            // Find property node with exact name
            newPropertyNodes[0] = propertyNode.children.get(token);
            // Find property node with *
            newPropertyNodes[1] = propertyNode.children.get("*");
            for (int newPropertNodesIndex = 0; newPropertNodesIndex < 2; newPropertNodesIndex++) {
                final PropertyNode newPropertyNode = newPropertyNodes[newPropertNodesIndex];
                if (newPropertyNode != null) {
                    final String actualToken = newPropertNodesIndex == 0 ? token : "*";
                    final String newConsumed = consumed.length() == 0 ? actualToken : consumed + "." +  actualToken;
                    final List<String> keyProperties = getPropertiesStartsWithWorker(newPropertyNode, newConsumed, tokens, currentTokenPosition + 1);
                    result.addAll(keyProperties);
                }
            }
        }
        return result;
    }

    public List<String> getPropertiesStartsWith(String name) {
        final List<String> tokensList = new ArrayList<String>();
        for (StringTokenizer nameTokenizer = new StringTokenizer(name, "."); nameTokenizer.hasMoreTokens();)
            tokensList.add(nameTokenizer.nextToken());
        final String[] tokensArray = tokensList.toArray(new String[tokensList.size()]);
        return getPropertiesStartsWithWorker(wildcardProperties, "", tokensArray, 0);
    }


    private Property getPropertyWorker(PropertyNode propertyNode, String[] tokens, int currentTokenPosition) {

        if (propertyNode == null) {
            // Dead end
            return null;
        } else if (currentTokenPosition == tokens.length) {
            // We're done with the search, see if we found something here
            return propertyNode.property != null ? propertyNode.property : null;
        } else {
            // Dead end
            if (propertyNode.children == null) return null;
            final String currentToken = tokens[currentTokenPosition];
            // Look for value with actual token
            PropertyNode newNode = propertyNode.children.get(currentToken);
            Property result = getPropertyWorker(newNode, tokens, currentTokenPosition + 1);
            if (result != null) return result;
            // If we couldn't find a value with the actual token, look for value with *
            newNode = propertyNode.children.get("*");
            return getPropertyWorker(newNode, tokens, currentTokenPosition + 1);
        }
    }

    /**
     * Get a property.
     *
     * @param name      property name
     * @param type      property type to check against, or null
     * @return          property object if found
     */
    private Property getProperty(String name, final QName type) {

        // Try first from exact properties
        Property property = exactProperties.get(name);
        if (property == null) {
            // If not found try traversing tree which contains properties with wildcards

            // Parse name and put into array
            final String[] tokensArray;
            {
                final List<String> tokensList = new ArrayList<String>();
                for (StringTokenizer nameTokenizer = new StringTokenizer(name, "."); nameTokenizer.hasMoreTokens();)
                    tokensList.add(nameTokenizer.nextToken());
                tokensArray = tokensList.toArray(new String[tokensList.size()]);
            }
            // Call recursive worker
            property = getPropertyWorker(wildcardProperties, tokensArray, 0);
            if (property == null) return null;
        }

        // Found a value, check type
        if (type != null && !type.equals(property.type))
            throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                    + type.getQualifiedName() + ", found " + property.type.getQualifiedName());

        // Return value
        return property;
    }

    private Object getPropertyValue(String name, final QName type) {
        final Property property = getProperty(name, type);
        return (property == null) ? null : property.value;
    }

    /* All getters */

    public Property getProperty(String name) {
        return getProperty(name, null);
    }

    public Object getObject(String name) {
        return getPropertyValue(name, null);
    }

    public Object getObject(String name, Object defaultValue) {
        final Object result = getObject(name);
        return (result == null) ? defaultValue : result;
    }

    public String getStringOrURIAsString(String name) {
        final Object property = getObject(name);
        if (property == null)
            return null;

        if (property instanceof String) {
            return StringUtils.trimToNull(getString(name));
        } else if (property instanceof java.net.URI) {
            return StringUtils.trimToNull(getURI(name).toString());
        } else {
            throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                    + XMLConstants.XS_STRING_QNAME.getQualifiedName()
                    + " or " + XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
        }
    }

    public String getStringOrURIAsString(String name, String defaultValue) {
        final String result = getStringOrURIAsString(name);
        return (result == null) ? defaultValue : result;
    }

    public String getString(String name) {
        String result = (String) getPropertyValue(name, XMLConstants.XS_STRING_QNAME);
        return StringUtils.trimToNull(result);
    }

    public Set<String> getNmtokens(String name) {
        return (Set<String>) getPropertyValue(name, XMLConstants.XS_NMTOKENS_QNAME);
    }

    public String getString(String name, String defaultValue) {
        final String result = getString(name);
        return (result == null) ? defaultValue : result;
    }

    public Integer getInteger(String name) {
        return (Integer) getPropertyValue(name, XMLConstants.XS_INTEGER_QNAME);
    }

    public Integer getInteger(String name, int defaultValue) {
        final Integer result = getInteger(name);
        return (result == null) ? new Integer(defaultValue) : result;
    }

    public Boolean getBoolean(String name) {
        return (Boolean) getPropertyValue(name, XMLConstants.XS_BOOLEAN_QNAME);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        final Boolean result = getBoolean(name);
        return (result == null) ? defaultValue : result;
    }

    public Date getDate(String name) {
        return (Date) getPropertyValue(name, XMLConstants.XS_DATE_QNAME);
    }

    public Date getDateTime(String name) {
        return (Date) getPropertyValue(name, XMLConstants.XS_DATETIME_QNAME);
    }

    public QName getQName(String name) {
        return (QName) getPropertyValue(name, XMLConstants.XS_QNAME_QNAME);
    }

    public QName getQName(String name, QName defaultValue) {
        final QName result = getQName(name);
        return (result == null) ? defaultValue : result;
    }

    public URI getURI(String name) {
        return (URI) getPropertyValue(name, XMLConstants.XS_ANYURI_QNAME);
    }

    public Integer getNonNegativeInteger(final String nm) {
        return (Integer) getPropertyValue(nm, XMLConstants.XS_NONNEGATIVEINTEGER_QNAME);
    }

    public String getNCName(final String nm) {
        return (String) getPropertyValue(nm, XMLConstants.XS_NCNAME_QNAME);
    }

    public String getNMTOKEN(final String nm) {
        return (String) getPropertyValue(nm, XMLConstants.XS_NMTOKEN_QNAME);
    }
}
