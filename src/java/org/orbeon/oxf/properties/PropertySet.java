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

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLConstants;

import java.net.URI;
import java.util.*;

/**
 * Represent a set of properties.
 *
 * A property name can be exact, e.g. foo.bar.gaga, or it can contain wildcards, like ".*.bar.gaga", "foo.*.gaga", or
 * "foo.bar.*", or "*.bar.*", etc.
 */
public class PropertySet {

    private static class TypeValue {
        public final QName type;
        public Object value;

        public TypeValue(final QName typ, final Object value) {
            type = typ;
            this.value = value;
        }
    }

    private static class PropertyNode {
        public TypeValue typeValue;
        public Map<String, PropertyNode> children;// Map<String, PropertyNode> of token to property node
    }

    private Map<String, TypeValue> exactProperties = new HashMap<String, TypeValue>();// Map<String, TypeValue> of property name to typed value
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
     * Return a Map<String, Object> of property names to property objects.
     *
     * @return Map
     */
    public Map<String, Object> getObjectMap() {
        if (size() > 0) {
            final Map<String, Object> result = new HashMap<String, Object>();
            for (String key: keySet()) {
                result.put(key, getObject(key));
            }
            return result;
        } else {
            return null;
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
        final TypeValue typeValue = new TypeValue(type, value);

        // Store exact property name anyway
        exactProperties.put(name, typeValue);

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
        currentNode.typeValue = typeValue;
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
                final PropertyNode newPropertNode = newPropertyNodes[newPropertNodesIndex];
                if (newPropertNode != null) {
                    final String actualToken = newPropertNodesIndex == 0 ? token : "*";
                    final String newConsumed = consumed.length() == 0 ? actualToken : consumed + "." +  actualToken;
                    final List<String> keyProperties = getPropertiesStartsWithWorker(newPropertNode, newConsumed, tokens, currentTokenPosition + 1);
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


    private TypeValue getPropertyWorker(PropertyNode propertyNode, String[] tokens, int currentTokenPosition) {

        if (propertyNode == null) {
            // Dead end
            return null;
        } else if (currentTokenPosition == tokens.length) {
            // We're done with the search, see if we found something here
            return propertyNode.typeValue != null ? propertyNode.typeValue : null;
        } else {
            // Dead end
            if (propertyNode.children == null) return null;
            final String currentToken = tokens[currentTokenPosition];
            // Look for value with actual token
            PropertyNode newNode = propertyNode.children.get(currentToken);
            TypeValue result = getPropertyWorker(newNode, tokens, currentTokenPosition + 1);
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
    private Object getProperty(String name, final QName type) {

        // Try first from exact properties
        TypeValue typeValue = exactProperties.get(name);
        if (typeValue == null) {
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
            typeValue = getPropertyWorker(wildcardProperties, tokensArray, 0);
            if (typeValue == null) return null;
        }

        // Found a value, check type
        if (type != null && !type.equals(typeValue.type))
            throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                    + type.getQualifiedName() + ", found " + typeValue.type.getQualifiedName());

        // Return value
        return typeValue.value;
    }

    /* All getters */

    public Object getObject(String name) {
        return getProperty(name, null);
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
            return getString(name);
        } else if (property instanceof java.net.URI) {
            return getURI(name).toString();
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
        String result = (String) getProperty(name, XMLConstants.XS_STRING_QNAME);
        if (result == null)
            return null;
        result = result.trim();
        return (result.length() == 0) ? null : result;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getNmtokens(String name) {
        final Set<String> result = (Set<String>) getProperty(name, XMLConstants.XS_NMTOKENS_QNAME);
        if (result == null)
            return null;
        return result;
    }

    public String getString(String name, String defaultValue) {
        final String result = getString(name);
        return (result == null) ? defaultValue : result;
    }

    public Integer getInteger(String name) {
        return (Integer) getProperty(name, XMLConstants.XS_INTEGER_QNAME);
    }

    public Integer getInteger(String name, int defaultValue) {
        final Integer result = getInteger(name);
        return (result == null) ? new Integer(defaultValue) : result;
    }

    public Boolean getBoolean(String name) {
        return (Boolean) getProperty(name, XMLConstants.XS_BOOLEAN_QNAME);
    }

    public Boolean getBoolean(String name, boolean defaultValue) {
        final Boolean result = getBoolean(name);
        return (result == null) ? Boolean.valueOf(defaultValue) : result;
    }

    public Date getDate(String name) {
        return (Date) getProperty(name, XMLConstants.XS_DATE_QNAME);
    }

    public Date getDateTime(String name) {
        return (Date) getProperty(name, XMLConstants.XS_DATETIME_QNAME);
    }

    public QName getQName(String name) {
        return (QName) getProperty(name, XMLConstants.XS_QNAME_QNAME);
    }

    public URI getURI(String name) {
        return (URI) getProperty(name, XMLConstants.XS_ANYURI_QNAME);
    }

    public Integer getNonNegativeInteger(final String nm) {
        return (Integer) getProperty(nm, XMLConstants.XS_NONNEGATIVEINTEGER_QNAME);
    }

    public String getNCName(final String nm) {
        return (String) getProperty(nm, XMLConstants.XS_NCNAME_QNAME);
    }

    public String getNMTOKEN(final String nm) {
        return (String) getProperty(nm, XMLConstants.XS_NMTOKEN_QNAME);
    }
}
