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

import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This ContentHandler receives an XHTML document or pseudo-XHTML document (XHTML in no namespace), and outputs SAX
 * events for the content of the body only.
 */
public class HTMLBodyXMLReceiver extends ForwardingXMLReceiver {

    private String xhtmlPrefix;
    private int level = 0;
    private boolean inBody = false;


    public HTMLBodyXMLReceiver(XMLReceiver xmlReceiver, String xhtmlPrefix) {
        super(xmlReceiver);
        this.xhtmlPrefix = xhtmlPrefix;
    }

    public void startDocument() {
    }

    public void endDocument() {
    }

    public void startPrefixMapping(String s, String s1) {
    }

    public void endPrefixMapping(String s) {
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (!inBody && level == 1 && "body".equals(localname)) {
            inBody = true;
        } else if (inBody && level > 1) {
            final String xhtmlQName = XMLUtils.buildQName(xhtmlPrefix, localname);

            final Attributes newAttributes;

            // Filter out attributes in a namespace
            final int attributesCount = attributes.getLength();
            if (attributesCount > 0) {
                final AttributesImpl newAttributesImpl = new AttributesImpl();
                for (int i = 0; i < attributesCount; i++) {
                    final String currentAttributeName = attributes.getLocalName(i);
                    final String currentAttributeValue = attributes.getValue(i);

                    if ("".equals(attributes.getURI(i)))
                        newAttributesImpl.addAttribute("", currentAttributeName, currentAttributeName, XMLReceiverHelper.CDATA, currentAttributeValue);
                }
                newAttributes = newAttributesImpl;
            } else
                newAttributes = attributes;

            super.startElement(XMLConstants.XHTML_NAMESPACE_URI(), localname, xhtmlQName, newAttributes);
        }

        level++;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        level--;

        if (inBody && level == 1) {
            inBody = false;
        } else if (inBody && level > 1) {
            final String xhtmlQName = XMLUtils.buildQName(xhtmlPrefix, localname);
            super.endElement(XMLConstants.XHTML_NAMESPACE_URI(), localname, xhtmlQName);
        }
    }


    public void characters(char[] chars, int start, int length) throws SAXException {
        if (inBody)
            super.characters(chars, start, length);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        if (inBody)
            super.ignorableWhitespace(chars, start, length);
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        if (inBody)
            super.processingInstruction(s, s1);
    }

    public void skippedEntity(String s) throws SAXException {
        if (inBody)
            super.skippedEntity(s);
    }
}