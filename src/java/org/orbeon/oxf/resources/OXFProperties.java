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

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.OXFPropertiesSerializer;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.XMLConstants;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class provides access to global, configurable properties, as well as to processor-specific
 * properties. This is an example of properties file:
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

    private static final int RELOAD_DELAY = 5*1000;

    private static OXFProperties instance;
    private static String key = DEFAULT_PROPERTIES_URI;
    private static boolean initializing = false;

    private URLGenerator urlGenerator = null;
    private OXFPropertiesSerializer propsSerializer = null;
    private PipelineContext context = null;
    private OXFPropertiesSerializer.PropertyStore propertyStore = null;
    private long lastUpdate;

    private OXFProperties() {
        if (!initializing) {
            try {
                initializing = true;
                context = new PipelineContext();
                urlGenerator = (URLGenerator) PipelineUtils.createURLGenerator(key);
                propsSerializer = new OXFPropertiesSerializer();
                PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, propsSerializer, ProcessorImpl.INPUT_DATA);

                propsSerializer.reset(context);
                propsSerializer.start(context);
                this.propertyStore = propsSerializer.getPropertyStore(context);
            } catch (Exception e) {
                throw new OXFException("Failure to initialize Presentation Server properties", e);
            } finally {
                initializing = false;
            }
        }
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
            initializing = true;
            try {
                long current = System.currentTimeMillis();
                if(lastUpdate + RELOAD_DELAY  < current) {
                    propsSerializer.reset(context);
                    propsSerializer.start(context);
                    this.propertyStore = propsSerializer.getPropertyStore(context);
                    lastUpdate = current;
                }
            } finally {
                initializing = false;
            }
        }
    }

    public PropertySet getPropertySet() {
        if (propertyStore == null)
            return null;
        update();
        return propertyStore.getGlobalPropertySet();
    }

    public PropertySet getPropertySet(QName processorName) {
        if (propertyStore == null)
            return null;
        update();
        return propertyStore.getProcessorPropertySet(processorName);
    }

    public Set keySet() {
        if (propertyStore == null)
            return null;

        return propertyStore.getGlobalPropertySet().keySet();
    }

    public static class PropertySet {

        private static class TypeValue {
            public QName type;
            public Object value;

            public TypeValue(QName type, Object value) {
                this.type = type;
                this.value = value;
            }
        }

        private Map properties = new HashMap();

        public Set keySet() {
            return properties.keySet();
        }

        public void setProperty(Element element, String name, QName type, String value) {
            properties.put(name, new TypeValue(type, getObject(element, type.getName(), value)));
        }

        public Object getProperty(String name, QName type) {
            TypeValue typeValue = (TypeValue) properties.get(name);
            if (typeValue == null)
                return null;
            if (type != null && !type.equals(typeValue.type))
                throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                        + type.getQualifiedName() + ", found " + typeValue.type.getQualifiedName());
            return typeValue.value;
        }

        private Object getObject(Element element, String type, String value) {
            try {
                return "string".equals(type) ? (Object) value :
                       "boolean".equals(type) ? (Object) new Boolean(value) :
                       "integer".equals(type) ? (Object) new Integer(value) :
                       ("date".equals(type) || "dateTime".equals(type)) ? (Object) ISODateUtils.parseDate(value) :
                       "QName".equals(type) ? (Object) XMLUtils.extractAttributeValueQName(element, "value") :
                       "anyURI".equals(type) ? (Object) URLFactory.createURL(value) :
                       null;
            } catch (MalformedURLException e) {
                throw new ValidationException(e, (LocationData) element.getData());
            }
        }

        public Object getObject(String name) {
            return getProperty(name, null);
        }

        public String getStringOrURIAsString(String name) {
            Object property = getObject(name);
            if (property == null)
                return null;

            if (property instanceof String) {
                return getString(name);
            } else if (property instanceof URL) {
                return getURL(name).toExternalForm();
            } else {
                throw new OXFException("Invalid attribute type requested for property '" + name + "': expected "
                        + XMLConstants.XS_STRING_QNAME.getQualifiedName()
                        + " or " + XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
            }
        }

        public URL getStringOrURIAsURL(String name) {
            String result = getStringOrURIAsString(name);
            if (result == null)
                return null;
            try {
                return URLFactory.createURL(name);
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }
        }

        public String getString(String name) {
            String result = (String) getProperty(name, XMLConstants.XS_STRING_QNAME);
            if (result == null)
                return null;
            result = result.trim();
            return (result.length() == 0) ? null : result;
        }

        public String getString(String name, String defaultValue) {
            String result = getString(name);
            return (result == null) ? defaultValue : result;
        }

        public Integer getInteger(String name) {
            return (Integer) getProperty(name, XMLConstants.XS_INTEGER_QNAME);
        }

        public Boolean getBoolean(String name) {
            return (Boolean) getProperty(name, XMLConstants.XS_BOOLEAN_QNAME);
        }

        public Boolean getBoolean(String name, boolean defaultValue) {
            Boolean result = getBoolean(name);
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

        /**
         * For now, the type xs:anyURI is used, but we really expect URLs.
         */
        public URL getURL(String name) {
            return (URL) getProperty(name, XMLConstants.XS_ANYURI_QNAME);
        }
    }
}
