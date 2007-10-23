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
package org.orbeon.oxf.resources;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.OXFPropertiesSerializer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class provides access to global, configurable properties, as well as to processor-specific properties. This is
 * an example of properties file:
 *
 * <properties xmlns:xs="http://www.w3.org/2001/XMLSchema"
 *             xmlns:oxf="http://www.orbeon.com/oxf/processors">
 *
 *   <property as="xs:integer" name="oxf.cache.size" value="200"/>
 *   <property as="xs:string"  processor-name="oxf:page-flow" name="instance-passing" value="redirect"/>
 *
 * </properties>
 */
public class OXFProperties {

    public static final String DEFAULT_PROPERTIES_URI = "oxf:/properties.xml";

    private static final int RELOAD_DELAY = 5 * 1000;

    private static OXFProperties instance;
    private static String key = DEFAULT_PROPERTIES_URI;
    private static boolean initializing = false;

    private OXFPropertiesSerializer.PropertyStore propertyStore = null;
    private long lastUpdate = Long.MIN_VALUE;

    private OXFProperties() {
        update();
    }

    /**
     * Set name of the resource we will read the properties from.
     */
    public static void init(String key) {
        OXFProperties.key = key;
        instance();
    }

    public static OXFProperties instance() {
        if (instance == null)
            instance = new OXFProperties();
        return instance;
    }

    /**
     * Make sure we have the latest properties, and if we don't (resource changed), reload them.
     */
    private void update() {
        if (!initializing) {
            Throwable throwable = null;
            done:
            try {
                initializing = true;
                final long current = System.currentTimeMillis();

                if (lastUpdate + RELOAD_DELAY >= current) break done;

                final URL url = URLFactory.createURL(key);
                final URLConnection uc = url.openConnection();

                if (propertyStore != null && uc.getLastModified() <= lastUpdate) {
                    lastUpdate = current;
                    break done;
                }

                final java.io.InputStream in = uc.getInputStream();
                final org.dom4j.Document doc = Dom4jUtils.readDom4j(in);
                propertyStore = OXFPropertiesSerializer.createPropertyStore(doc);

                lastUpdate = current;

            } catch (final IOException e) {
                throwable = e;
            } catch (final SAXException e) {
                throwable = e;
            } catch (final DocumentException e) {
                throwable = e;
            } finally {
                initializing = false;
            }
            if (throwable != null) {
                throw new OXFException("Failure to initialize Orbeon Forms properties", throwable);
            }
        }
    }

    public PropertySet getPropertySet() {
        if (propertyStore == null)
            return null;
        update();
        return propertyStore.getGlobalPropertySet();
    }

    public PropertySet getPropertySet(final QName processorName) {
        if (propertyStore == null)
            return null;
        update();
        return propertyStore.getProcessorPropertySet(processorName);
    }

    public java.util.Set keySet() {
        if (propertyStore == null)
            return null;

        return propertyStore.getGlobalPropertySet().keySet();
    }

    public static class PropertySet {

        private static class TypeValue {
            public final QName type;
            public Object value;

            public TypeValue(final QName typ, final Object val) {
                type = typ;
                value = val;
            }
        }

        private java.util.Map properties = new java.util.HashMap();

        public java.util.Set keySet() {
            return properties.keySet();
        }

        public int size() {
            return properties.size();
        }

        public java.util.Map getObjectMap() {
            if (size() > 0) {
                final java.util.Map result = new java.util.HashMap();
                for (final java.util.Iterator i = keySet().iterator(); i.hasNext();) {
                    String key = (String) i.next();
                    result.put(key, getObject(key));
                }
                return result;
            } else {
                return null;
            }
        }

        public void setProperty(final Element elt, String name, final QName typ, String value) {
            final Object o = OXFPropertiesSerializer.getObject(value, typ, elt);
            properties.put(name, new TypeValue(typ, o));
        }

        public Object getProperty(String name, final QName type) {
            TypeValue typeValue = (TypeValue) properties.get(name);
            if (typeValue == null)
                return null;
            if (type != null && !type.equals(typeValue.type))
                throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                        + type.getQualifiedName() + ", found " + typeValue.type.getQualifiedName());
            return typeValue.value;
        }

        public Object getObject(String name) {
            return getProperty(name, null);
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

        // This is not used
//        public URL getStringOrURIAsURL(String name) {
//            String result = getStringOrURIAsString(name);
//            if (result == null)
//                return null;
//            try {
//                return URLFactory.createURL(name);
//            } catch (final java.net.MalformedURLException e) {
//                throw new OXFException(e);
//            }
//        }

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

        public java.util.Date getDate(String name) {
            return (java.util.Date) getProperty(name, XMLConstants.XS_DATE_QNAME);
        }

        public java.util.Date getDateTime(String name) {
            return (java.util.Date) getProperty(name, XMLConstants.XS_DATETIME_QNAME);
        }

        public QName getQName(String name) {
            return (QName) getProperty(name, XMLConstants.XS_QNAME_QNAME);
        }
        
        public URI getURI(String name) {
            return (URI) getProperty(name, XMLConstants.XS_ANYURI_QNAME);
        }

//        public URI getURI(String name, URI defaultValue) {
//            final URI result = (URI) getProperty(name, XMLConstants.XS_ANYURI_QNAME);
//            return (result == null) ? defaultValue : result;
//        }

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
}
