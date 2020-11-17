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

    // 2020-11-16: Duplicated as `XMLReceiverSupport.EmptyAttributes`
    // This is now only used by processors.
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
