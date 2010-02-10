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

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Writer;

/**
 * Minimalist HTML fragment serializer.
 * 
 * o This assumes that all input elements are HTML or XHTML elements.
 * o Only attributes in no namespace are serialized.
 */
public class HTMLFragmentSerializer implements ContentHandler {

    private final Writer writer;
    private final boolean skipRootElement;

    private int level = 0;

    public HTMLFragmentSerializer(Writer writer, boolean skipRootElement) {
        this.writer = writer;
        this.skipRootElement = skipRootElement;
    }

    public void startDocument() {}
    public void endDocument() {}
    public void startPrefixMapping(String s, String s1) {}
    public void endPrefixMapping(String s) {}
    public void setDocumentLocator(Locator locator) {}

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (!skipRootElement || level > 0) {
            try {
                writer.write('<');
                writer.write(localname);
                final int attributesCount = attributes.getLength();
                if (attributesCount > 0) {
                    for (int i = 0; i < attributesCount; i++) {
                        final String currentAttributeName = attributes.getLocalName(i);
                        final String currentAttributeValue = attributes.getValue(i);

                        // Only consider attributes in no namespace
                        if ("".equals(attributes.getURI(i))) {
                            writer.write(' ');
                            writer.write(currentAttributeName);
                            writer.write("=\"");
                            if (currentAttributeValue != null)
                                writer.write(XMLUtils.escapeXMLMinimal(currentAttributeValue));
                            writer.write('"');
                        }
                    }
                }
                writer.write('>');
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
        level++;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        level--;
        if (!skipRootElement || level > 0) {
            try {
                writer.write("</");
                writer.write(localname);
                writer.write('>');
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (!skipRootElement || level > 0) {
            try {
                writer.write(XMLUtils.escapeXMLMinimal(new String(chars, start, length)));
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {}
    public void processingInstruction(String s, String s1) throws SAXException {}
    public void skippedEntity(String s) throws SAXException {}
}