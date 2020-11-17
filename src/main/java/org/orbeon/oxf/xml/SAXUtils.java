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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.ContentHandlerOutputStream;
import org.orbeon.oxf.util.NetUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class SAXUtils {

    public static final Attributes EMPTY_ATTRIBUTES = new Attributes() {
        public int getLength() {
            return 0;
        }
        public String getURI(int i) {
            return null;
        }
        public String getLocalName(int i) {
            return null;
        }
        public String getQName(int i) {
            return null;
        }
        public String getType(int i) {
            return null;
        }
        public String getValue(int i) {
            return null;
        }
        public int getIndex(String s, String s1) {
            return -1;
        }
        public int getIndex(String s) {
            return -1;
        }
        public String getType(String s, String s1) {
            return null;
        }
        public String getType(String s) {
            return null;
        }
        public String getValue(String s, String s1) {
            return null;
        }
        public String getValue(String s) {
            return null;
        }
    };

    private SAXUtils() {}

    /**
     * Convert an Object to a String and generate SAX characters events.
     */
    public static void objectToCharacters(Object o, ContentHandler contentHandler) {
        try {
            char[] charValue = (o == null) ? null : o.toString().toCharArray();
            if (charValue != null)
                contentHandler.characters(charValue, 0, charValue.length);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Append classes to existing attributes. This creates a new AttributesImpl object.
     *
     * @param attributes    existing attributes
     * @param newClasses    new classes to append
     * @return              new attributes
     */
    public static AttributesImpl appendToClassAttribute(Attributes attributes, String newClasses) {
        final String oldClassAttribute = attributes.getValue("class");
        final String newClassAttribute = oldClassAttribute == null ? newClasses : oldClassAttribute + ' ' + newClasses;
        return addOrReplaceAttribute(attributes, "", "", "class", newClassAttribute);
    }

    /**
     * Append an attribute value to existing mutable attributes.
     *
     * @param attributes        existing attributes
     * @param attributeName     attribute name
     * @param attributeValue    value to set or append
     */
    public static void addOrAppendToAttribute(AttributesImpl attributes, String attributeName, String attributeValue) {

        final int oldAttributeIndex = attributes.getIndex(attributeName);

        if (oldAttributeIndex == -1) {
            // No existing attribute
            attributes.addAttribute("", attributeName, attributeName, XMLReceiverHelper.CDATA, attributeValue);
        } else {
            // Existing attribute
            final String oldAttributeValue = attributes.getValue(oldAttributeIndex);
            final String newAttributeValue = oldAttributeValue + ' ' + attributeValue;

            attributes.setValue(oldAttributeIndex, newAttributeValue);
        }
    }

    public static AttributesImpl addOrReplaceAttribute(Attributes attributes, String uri, String prefix, String localname, String value) {
        final AttributesImpl newAttributes = new AttributesImpl();
        boolean replaced = false;
        for (int i = 0; i < attributes.getLength(); i++) {
            final String attributeURI = attributes.getURI(i);
            final String attributeValue = attributes.getValue(i);
            final String attributeType = attributes.getType(i);
            final String attributeQName = attributes.getQName(i);
            final String attributeLocalname = attributes.getLocalName(i);

            if (uri.equals(attributeURI) && localname.equals(attributeLocalname)) {
                // Found existing attribute
                replaced = true;
                newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), XMLReceiverHelper.CDATA, value);
            } else {
                // Not a matched attribute
                newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
            }
        }
        if (!replaced) {
            // Attribute did not exist already so add it
            newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), XMLReceiverHelper.CDATA, value);
        }
        return newAttributes;
    }

    public static AttributesImpl removeAttribute(Attributes attributes, String uri, String localname) {
        final AttributesImpl newAttributes = new AttributesImpl();
        for (int i = 0; i < attributes.getLength(); i++) {
            final String attributeURI = attributes.getURI(i);
            final String attributeValue = attributes.getValue(i);
            final String attributeType = attributes.getType(i);
            final String attributeQName = attributes.getQName(i);
            final String attributeLocalname = attributes.getLocalName(i);

            if (!uri.equals(attributeURI) || !localname.equals(attributeLocalname)) {
                // Not a matched attribute
                newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
            }
        }
        return newAttributes;
    }

    public static void streamNullDocument(ContentHandler contentHandler) throws SAXException {
        contentHandler.startDocument();
        contentHandler.startPrefixMapping(XMLConstants.XSI_PREFIX(), XMLConstants.XSI_URI());
        final AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(XMLConstants.XSI_URI(), "nil", "xsi:nil", "CDATA", "true");
        contentHandler.startElement("", "null", "null", attributes);
        contentHandler.endElement("", "null", "null");
        contentHandler.endPrefixMapping(XMLConstants.XSI_PREFIX());
        contentHandler.endDocument();
    }

    private static void mapPrefixIfNeeded(Set<String> declaredPrefixes, String uri, String qName, StringBuilder sb) {
        final String prefix = XMLUtils.prefixFromQName(qName);
        if (prefix.length() > 0 && !declaredPrefixes.contains(prefix)) {
            sb.append(" xmlns:");
            sb.append(prefix);
            sb.append("=\"");
            sb.append(uri);
            sb.append("\"");

            declaredPrefixes.add(prefix);
        }
    }

    public static String saxElementToDebugString(String uri, String qName, Attributes attributes) {
        // Open start tag
        final StringBuilder sb = new StringBuilder("<");
        sb.append(qName);

        final Set<String> declaredPrefixes = new HashSet<String>();
        mapPrefixIfNeeded(declaredPrefixes, uri, qName, sb);

        // Attributes if any
        for (int i = 0; i < attributes.getLength(); i++) {
            mapPrefixIfNeeded(declaredPrefixes, attributes.getURI(i), attributes.getQName(i), sb);

            sb.append(' ');
            sb.append(attributes.getQName(i));
            sb.append("=\"");
            sb.append(attributes.getValue(i));
            sb.append('\"');
        }

        // Close start tag
        sb.append('>');

        // Content
        sb.append("[...]");

        // Close element with end tag
        sb.append("</");
        sb.append(qName);
        sb.append('>');

        return sb.toString();
    }

    /**
     * Read bytes from an InputStream and generate SAX characters events in Base64 encoding. The
     * InputStream is closed when done.
     *
     * The caller has to close the stream if needed.
     */
    public static void inputStreamToBase64Characters(InputStream is, ContentHandler contentHandler) {

        try {
            final OutputStream os = new ContentHandlerOutputStream(contentHandler, false);
            NetUtils.copyStream(new BufferedInputStream(is), os);
            os.close(); // necessary with ContentHandlerOutputStream to make sure all extra characters are written
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Read characters from a Reader and generate SAX characters events.
     *
     * The caller has to close the Reader if needed.
     */
    public static void readerToCharacters(Reader reader, ContentHandler contentHandler) {
        try {
            // Work with buffered Reader
            reader = new BufferedReader(reader);
            // Read and write in chunks
            char[] buf = new char[1024];
            int count;
            while ((count = reader.read(buf)) != -1)
                contentHandler.characters(buf, 0, count);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
