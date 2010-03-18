/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.ISODateUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Name10Checker;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Represent property sets grouped as global and per-processor properties.
 */
public class PropertyStore {

    private final PropertySet globalPropertySet = new PropertySet();
    private final Map<QName, PropertySet> processorPropertySets = new HashMap<QName, PropertySet>();

    public static final Map<QName, Converter> SUPPORTED_TYPES = new HashMap<QName, Converter>();

    static {
        SUPPORTED_TYPES.put(XMLConstants.XS_STRING_QNAME, new StringConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_INTEGER_QNAME, new IntegerConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_BOOLEAN_QNAME, new BooleanConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_DATE_QNAME, new DateConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_DATETIME_QNAME, new DateConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_QNAME_QNAME, new QNameConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_ANYURI_QNAME, new URIConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_NCNAME_QNAME, new NCNameConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_NMTOKEN_QNAME, new NMTOKENConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_NMTOKENS_QNAME, new NMTOKENSConverter());
        SUPPORTED_TYPES.put(XMLConstants.XS_NONNEGATIVEINTEGER_QNAME, new NonNegativeIntegerConverter());
    }

    /**
     * Convert a property's string value to an object.
     *
     * @param stringValue   string value
     * @param type          type
     * @param element       Element on which the property is defined. Used for QName resolution if needed.
     * @return              object, or null
     */
    public static Object getObjectFromStringValue(final String stringValue, final QName type, final Element element) {
        final Converter converter = SUPPORTED_TYPES.get(type);
        return (converter == null) ? null : converter.convert(stringValue, element);
    }

    /**
     * Construct a new property store.
     *
     * @param propertiesDocument Document containing the properties definitions
     */
    public PropertyStore(final Document propertiesDocument) {

        // NOTE: the use of "attributes" and "attribute" is for special use of the property store by certain processors
        for (final Iterator i = XPathUtils.selectIterator(propertiesDocument, "/properties//property | /attributes//attribute"); i.hasNext();) {
            final Element propertyElement = (Element) i.next();

            // Extract attributes
            final String processorName = propertyElement.attributeValue("processor-name");
            final String as = propertyElement.attributeValue("as");
            final String name = propertyElement.attributeValue("name");
            final String value = propertyElement.attributeValue("value");

            if (as != null) {
                // Read QName
                final QName typeQName = Dom4jUtils.extractAttributeValueQName(propertyElement, "as");

                if (SUPPORTED_TYPES.get(typeQName) == null)
                    throw new ValidationException("Invalid as attribute: " + typeQName.getQualifiedName(), (LocationData) propertyElement.getData());

                if (processorName != null) {
                    // Processor-specific property
                    final QName processorQName = Dom4jUtils.extractAttributeValueQName(propertyElement, "processor-name");
                    getProcessorPropertySet(processorQName).setProperty(propertyElement, name, typeQName, value);
                } else {
                    // Global property
                    getGlobalPropertySet().setProperty(propertyElement, name, typeQName, value);
                }
            }
        }
    }

    /**
     * Return the global property set for this store.
     *
     * @return PropertySet
     */
    public PropertySet getGlobalPropertySet() {
        return globalPropertySet;
    }

    /**
     * Return the property set for the given processor.
     *
     * @param processorQName processor QName
     * @return PropertySet   PropertySet
     */
    public PropertySet getProcessorPropertySet(final QName processorQName) {
        PropertySet propertySet = processorPropertySets.get(processorQName);
        if (propertySet == null) {
            propertySet = new PropertySet();
            processorPropertySets.put(processorQName, propertySet);
        }
        return propertySet;
    }

    /* All converters */

    private interface Converter {
        public Object convert(final String value, final Element element);
    }

    public static class StringConverter implements Converter {
        public Object convert(final String value, final Element element) {
            return value;
        }
    }

    public static class IntegerConverter implements Converter {
        public Object convert(final String value, final Element element) {
            return new Integer(value);
        }
    }

    public static class BooleanConverter implements Converter {
        public Object convert(final String value, final Element element) {
            return Boolean.valueOf(value);
        }
    }

    public static class DateConverter implements Converter {
        public Object convert(final String value, final Element element) {
            return ISODateUtils.parseDate(value);
        }
    }

    public static class QNameConverter implements Converter {
        public Object convert(final String value, final Element element) {
            return Dom4jUtils.extractAttributeValueQName(element, "value");
        }
    }

    public static class URIConverter implements Converter {
        public Object convert(final String value, final Element element) {
            try {
                return new URI(value);
            } catch (URISyntaxException e) {
                throw new ValidationException(e, null);
            }
        }
    }

    public static class NCNameConverter implements Converter {
        public Object convert(final String value, final Element element) {
            if (!Name10Checker.getInstance().isValidNCName(value)) {
                throw new ValidationException("Not an NCName: " + value, null);
            }
            return value;
        }
    }

    public static class NMTOKENConverter implements Converter {
        public Object convert(final String value, final Element element) {
            if (!Name10Checker.getInstance().isValidNmtoken(value)) {
                throw new ValidationException("Not an NMTOKEN: " + value, null);
            }
            return value;
        }
    }

    public static class NMTOKENSConverter implements Converter {
        public Object convert(final String value, final Element element) {
            // Create Set from String
            final Set<String> tokens = new HashSet<String>(Arrays.asList(StringUtils.split(value)));
            // Check validity of individual tokens
            for (String token: tokens) {
                if (!Name10Checker.getInstance().isValidNmtoken(token)) {
                    throw new ValidationException("Not an NMTOKENS: " + value, null);
                }
            }
            return tokens;
        }
    }

    public static class NonNegativeIntegerConverter extends IntegerConverter {
        public Object convert(final String value, final Element element) {
            final Integer ret = (Integer) super.convert(value, element);
            if (ret < 0) {
                throw new ValidationException("Not a non-negative integer: " + value, null);
            }
            return ret;
        }
    }
}
