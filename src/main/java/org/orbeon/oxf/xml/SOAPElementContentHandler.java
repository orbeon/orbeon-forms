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

import org.apache.axis.message.PrefixedQName;
import org.apache.axis.soap.SOAPFactoryImpl;
import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;

public class SOAPElementContentHandler extends ForwardingXMLReceiver {

    private SOAPElement currentElement;
    private SOAPFactory soapFactory = new SOAPFactoryImpl();

    public SOAPElementContentHandler(SOAPElement rootElement) {
        currentElement = rootElement;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        try {
            SOAPElement newElement = qName.equals(localname)
                ? soapFactory.createElement(localname)
                : soapFactory.createElement(localname, qName.substring(0, qName.indexOf(':')), uri);
            currentElement.addChildElement(newElement);
            currentElement = newElement;
            for (int i = 0; i < attributes.getLength(); i++) {
                String attributeQName = attributes.getQName(i);
                int prefixLength = attributeQName.indexOf(':');
                String prefix = prefixLength == -1 ? "" : attributeQName.substring(0, prefixLength);
                currentElement.addAttribute(new PrefixedQName
                        (attributes.getURI(i), attributes.getLocalName(i), prefix), attributes.getValue(i));
            }
            super.startElement(uri, localname, qName, attributes);
        } catch (SOAPException e) {
            throw new OXFException(e);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        currentElement = currentElement.getParentElement();
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        try {
            currentElement.addTextNode(new String(chars, start, length));
        } catch (SOAPException e) {
            throw new OXFException(e);
        }
    }

    public SOAPElement getSOAPElement() {
        return currentElement;
    }
}
