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
package org.orbeon.oxf.processor;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.XMLChar;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * OXFPropertiesSerializer is a special internal processor that reads a properties file and allows
 * for retrieveing a resulting PropertyStore class.
 */
public class OXFPropertiesSerializer extends ProcessorImpl {

    public interface Converter {
        public Object convert(final String val, final Element elt);
    }

    private static class StringConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            return val;
        }
    }

    private static class IntegerConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            return new Integer(val);
        }
    }

    private static class BooleanConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            return Boolean.valueOf(val);
        }
    }

    private static class DateConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            return ISODateUtils.parseDate(val);
        }
    }

    private static class QNameConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            return Dom4jUtils.extractAttributeValueQName(elt, "value");
        }
    }

    private static class URIConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            try {
                return new URI(val);
            } catch (URISyntaxException e) {
                throw new ValidationException(e, null);
            }
        }
    }

    private static class NCNameConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            if (!XMLChar.isValidNCName(val)) {
                throw new ValidationException("Not an NCName: " + val, null);
            }
            return val;
        }
    }

    private static class NMTOKENConverter implements Converter {
        public Object convert(final String val, final Element elt) {
            if (!XMLChar.isValidNmtoken(val)) {
                throw new ValidationException("Not an NMTOKEN: " + val, null);
            }
            return val;
        }
    }

    private static class NonNegativeIntegerConverter extends IntegerConverter {
        public Object convert(final String val, final Element elt) {
            final Integer ret = (Integer) super.convert(val, elt);
            if (ret.intValue() < 0) {
                throw new ValidationException("Not a nonnegatvie integer: " + val, null);
            }
            return ret;
        }
    }

    public static final String PROPERTIES_SCHEMA_URI = "http://www.orbeon.com/oxf/properties";

    private static final String SUPPORTED_TYPES_URI = XMLConstants.XSD_URI;
    private static final java.util.Map supportedTypes = new java.util.HashMap();

    static {
        supportedTypes.put(XMLConstants.XS_STRING_QNAME, new StringConverter());
        supportedTypes.put(XMLConstants.XS_INTEGER_QNAME, new IntegerConverter());
        supportedTypes.put(XMLConstants.XS_BOOLEAN_QNAME, new BooleanConverter());
        supportedTypes.put(XMLConstants.XS_DATE_QNAME, new DateConverter());
        supportedTypes.put(XMLConstants.XS_DATETIME_QNAME, new DateConverter());
        supportedTypes.put(XMLConstants.XS_QNAME_QNAME, new QNameConverter());
        supportedTypes.put(XMLConstants.XS_ANYURI_QNAME, new URIConverter());
        supportedTypes.put(XMLConstants.XS_NCNAME_QNAME, new NCNameConverter());
        supportedTypes.put(XMLConstants.XS_NMTOKEN_QNAME, new NMTOKENConverter());
        supportedTypes.put
                (XMLConstants.XS_NONNEGATIVEINTEGER_QNAME, new NonNegativeIntegerConverter());
    }

    public OXFPropertiesSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, PROPERTIES_SCHEMA_URI));
    }

    public PropertyStore getPropertyStore(PipelineContext context) {
        return (PropertyStore) context.getAttribute(this);
    }

    public void start(PipelineContext context) {
        PropertyStore propertyStore = (PropertyStore) readCacheInputAsObject(context, getInputByName(INPUT_DATA),
                new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        final org.dom4j.Document propertiesDocument = readInputAsDOM4J(context, input);
                        return createPropertyStore(propertiesDocument);
                    }
                });
        context.setAttribute(this, propertyStore);
    }

    public static Object getObject(final String val, final org.dom4j.QName type, final Element elt) {
        final Converter converter = (Converter) supportedTypes.get(type);
        return (converter == null) ? null : converter.convert(val, elt);
    }

    public static PropertyStore createPropertyStore(final org.dom4j.Document propertiesNode) {
        PropertyStore propertyStore = new PropertyStore();
        for (final java.util.Iterator i = XPathUtils.selectIterator(propertiesNode, "/properties/property | /attributes/attribute"); i.hasNext();) {
            final Element propertyElement = (Element) i.next();

            // Extract attributes
            String processorName = propertyElement.attributeValue("processor-name");
            String as = propertyElement.attributeValue("as");
            String name = propertyElement.attributeValue("name");
            String value = propertyElement.attributeValue("value");

            if (as != null) {
                // We only support one namespace for types for now
                final org.dom4j.QName typeQName = Dom4jUtils.extractAttributeValueQName(propertyElement, "as");

                if (supportedTypes.get(typeQName) == null)
                    throw new ValidationException("Invalid as attribute: " + typeQName.getQualifiedName(), (LocationData) propertyElement.getData());

                if (processorName != null) {
                    // Processor-specific property
                    final org.dom4j.QName processorQName = Dom4jUtils.extractAttributeValueQName(propertyElement, "processor-name");
                    propertyStore.setProperty(propertyElement, processorQName, name, typeQName, value);
                } else {
                    // Global property
                    propertyStore.setProperty(propertyElement, name, typeQName, value);
                }
            } else {
                // [BACKWARD COMPATIBILITY] get type attribute
                String type = propertyElement.attributeValue("type");
                if (supportedTypes.get(type) == null)
                    throw new ValidationException("Invalid type attribute: " + type, (LocationData) propertyElement.getData());
                propertyStore.setProperty
                        (propertyElement, name, new org.dom4j.QName(type
                                , new org.dom4j.Namespace("xs", SUPPORTED_TYPES_URI)), value);
            }
        }
        return propertyStore;
    }

    public static class PropertyStore {

        private final OXFProperties.PropertySet globalPropertySet = new OXFProperties.PropertySet();
        private final java.util.Map processorPropertySets = new java.util.HashMap();

        public void setProperty
                (final Element element, String name, final org.dom4j.QName type, String value) {
            globalPropertySet.setProperty(element, name, type, value);
        }

        public void setProperty
                (final Element element, final org.dom4j.QName processorName, String name
                        , final org.dom4j.QName type, String value) {
            getProcessorPropertySet(processorName).setProperty(element, name, type, value);
        }


        public OXFProperties.PropertySet getGlobalPropertySet() {
            return globalPropertySet;
        }

        public OXFProperties.PropertySet getProcessorPropertySet
                (final org.dom4j.QName processorName) {
            OXFProperties.PropertySet propertySet = (OXFProperties.PropertySet) processorPropertySets.get(processorName);
            if (propertySet == null) {
                propertySet = new OXFProperties.PropertySet();
                processorPropertySets.put(processorName, propertySet);
            }
            return propertySet;
        }
    }
}
