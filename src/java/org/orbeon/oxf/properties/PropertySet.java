/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.properties;

import org.dom4j.QName;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLConstants;

import java.util.*;
import java.net.URI;

/**
 * Represent a set of properties.
 */
public class PropertySet {

    private static class TypeValue {
        public final QName type;
        public Object value;

        public TypeValue(final QName typ, final Object val) {
            type = typ;
            value = val;
        }
    }

    private Map properties = new HashMap();

    /**
     * Return the set of property names.
     *
     * @return set of property names
     */
    public Set keySet() {
        return properties.keySet();
    }

    /**
     * Return the number of properties.
     *
     * @return number of properties
     */
    public int size() {
        return properties.size();
    }

    /**
     * Return a Map<String, Object> of property names to property objects.
     *
     * @return Map
     */
    public Map getObjectMap() {
        if (size() > 0) {
            final Map result = new HashMap();
            for (final Iterator i = keySet().iterator(); i.hasNext();) {
                String key = (String) i.next();
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
        final Object object = PropertyStore.getObject(stringValue, type, element);
        properties.put(name, new TypeValue(type, object));
    }

    /**
     * Get a property.
     *
     * @param name      property name
     * @param type      property type to check against, or null
     * @return          property object if found
     */
    private Object getProperty(String name, final QName type) {
        TypeValue typeValue = (TypeValue) properties.get(name);
        if (typeValue == null)
            return null;
        if (type != null && !type.equals(typeValue.type))
            throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                    + type.getQualifiedName() + ", found " + typeValue.type.getQualifiedName());
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
        return (result == null) ? new Boolean(defaultValue) : result;
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
