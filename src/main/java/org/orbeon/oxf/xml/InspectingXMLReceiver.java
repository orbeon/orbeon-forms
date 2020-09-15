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

import org.apache.commons.lang3.StringUtils;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom.XmlLocationData;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Stack;

/**
 * Just like ForwardingXMLReceiver (a SAX handler that forwards SAX events to another handler), but
 * checks the validity of the SAX stream.
 *
 * TODO: check for duplicate attributes.
 */
public class InspectingXMLReceiver extends ForwardingXMLReceiver {

    private Locator locator;

    private Stack<NameInfo> elementStack = new Stack<NameInfo>();

    private boolean documentStarted = false;
    private boolean documentEnded = false;

    private NamespaceContext namespaceContext = new NamespaceContext();

    public InspectingXMLReceiver(XMLReceiver xmlReceiver) {
        super(xmlReceiver);
    }

    public void startDocument() throws SAXException {

        if (documentStarted)
            throw new ValidationException("startDocument() called twice", XmlLocationData.apply(locator));

        documentStarted = true;
        super.startDocument();
    }

    public void endDocument() throws SAXException {
        if (elementStack.size() != 0)
            throw new ValidationException("Document ended before all the elements are closed", XmlLocationData.apply(locator));

        if (documentEnded)
            throw new ValidationException("endDocument() called twice", XmlLocationData.apply(locator));

        documentEnded = true;
        super.endDocument();
    }

    public void startElement(String uri, String localname, String qname, Attributes attributes) throws SAXException {
        namespaceContext.startElement();
        final String error = checkInDocument();
        if (error != null)
            throw new ValidationException(error + ": element " + qname, XmlLocationData.apply(locator));

        elementStack.push(new NameInfo(uri, localname, qname, new AttributesImpl(attributes)));

        // Check names
        checkElementName(uri, localname, qname);
        for (int i = 0; i < attributes.getLength(); i++)
            checkAttributeName(attributes.getURI(i), attributes.getLocalName(i), attributes.getQName(i));

        super.startElement(uri, localname, qname, attributes);
    }

    public void endElement(String uri, String localname, String qname) throws SAXException {
        final String error = checkInElement();
        if (error != null)
            throw new ValidationException(error + ": element " + qname, XmlLocationData.apply(locator));

        final NameInfo startElementNameInfo = elementStack.pop();
        final NameInfo endElementNameInfo = new NameInfo(uri, localname, qname, null);
        if (!startElementNameInfo.compareNames(endElementNameInfo))
            throw new ValidationException("endElement() doesn't match startElement(). startElement(): "
                    + startElementNameInfo.toString() + "; endElement(): " + endElementNameInfo.toString(), XmlLocationData.apply(locator));

        // Check name
        checkElementName(uri, localname, qname);

        namespaceContext.endElement();

        super.endElement(uri, localname, qname);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        String error = checkInElement();
        if (error != null)
            throw new ValidationException(error + ": '" + new String(chars, start, length) + "'", XmlLocationData.apply(locator));
        super.characters(chars, start, length);
    }

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }

    private String checkInElement() {
        String error = checkInDocument();
        if (error != null) {
            return error;
        } else if (elementStack.size() == 0) {
            return "SAX event received after close of root element";
        } else {
            return null;
        }
    }

    private String checkInDocument() {
        if (!documentStarted) {
            return "SAX event received before document start";
        } else if (documentEnded) {
            return "SAX event received after document end";
        } else {
            return null;
        }
    }

    private void checkAttributeName(String uri, String localname, String qname) {
        if (uri != null && qname != null && !"".equals(uri) && qname.indexOf(':') == -1)
            throw new ValidationException("Non-prefixed attribute cannot be in a namespace. URI: " + uri + "; localname: " + localname + "; QName: " + qname, XmlLocationData.apply(locator));
        checkName(uri, localname, qname);
    }

    private void checkElementName(String uri, String localname, String qname) {
        checkName(uri, localname, qname);
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        namespaceContext.startPrefixMapping(prefix, uri);

        super.startPrefixMapping(prefix, uri);
    }

    private void checkName(String uri, String localname, String qname) {
        if (StringUtils.isEmpty(localname))
            throw new ValidationException("Empty local name in SAX event. QName: " + qname, XmlLocationData.apply(locator));
        if (StringUtils.isEmpty(qname))
            throw new ValidationException("Empty qualified name in SAX event. Localname: " + localname + "; QName: " + qname, XmlLocationData.apply(locator));
        if (uri == null)
            throw new ValidationException("Null URI. Localname: " + localname, XmlLocationData.apply(locator));
        if (uri.equals("") && !localname.equals(qname))
            throw new ValidationException("Localname and QName must be equal when name is in no namespace. Localname: " + localname + "; QName: " + qname, XmlLocationData.apply(locator));
        if (!uri.equals("") && !localname.equals(qname.substring(qname.indexOf(':') + 1)))
            throw new ValidationException("Local part or QName must be equal to localname when name is in namespace. Localname: " + localname + "; QName: " + qname, XmlLocationData.apply(locator));

        final int colonIndex = qname.indexOf(':');
        // Check namespace mappings
        if (!uri.equals("")) {
            // We are in a namespace
            if (colonIndex == -1) {
                // QName is not prefixed, check that we match the default namespace
                if (!uri.equals(namespaceContext.getURI("")))
                    throw new ValidationException("Namespace doesn't match default namespace. Namespace: " + uri + "; QName: " + qname, XmlLocationData.apply(locator));
            } else if (colonIndex == 0 || colonIndex == (qname.length() - 1)) {
                // Invalid position of colon in QName
                throw new ValidationException("Invalid position of colon in QName: " + qname, XmlLocationData.apply(locator));
            } else {
                // Name is prefixed: check that prefix is bound and maps to namespace
                final String prefix = qname.substring(0, colonIndex);
                if (namespaceContext.getURI(prefix) == null)
                    throw new ValidationException("QName prefix is not in scope: " + qname, XmlLocationData.apply(locator));
                if (!uri.equals(namespaceContext.getURI(prefix)))
                    throw new ValidationException("QName prefix maps to URI: " + namespaceContext.getURI(prefix) + "; but namespace provided is: " + uri, XmlLocationData.apply(locator));
            }
        } else {
            // We are not in a namespace
            if (colonIndex != -1)
                throw new ValidationException("QName has prefix but we are not in a namespace: " + qname, XmlLocationData.apply(locator));
        }
    }

    private static class NameInfo {
        private String uri;
        private String localname;
        private String qname;
        private AttributesImpl attributes;

        public NameInfo(String uri, String localname, String qname, AttributesImpl attributes) {
            this.uri = uri;
            this.localname = localname;
            this.qname = qname;
            this.attributes = attributes;
        }

        public String getUri() {
            return uri;
        }

        public String getLocalname() {
            return localname;
        }

        public String getQname() {
            return qname;
        }

        public AttributesImpl getAttributes() {
            return attributes;
        }

        public boolean compareNames(NameInfo other) {
            if (!uri.equals(other.uri))
                return false;
            if (!localname.equals(other.localname))
                return false;
            if (!qname.equals(other.qname))
                return false;

            return true;
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder();

            sb.append('[');
            sb.append("uri = ");
            sb.append(uri);
            sb.append(" | localname = ");
            sb.append(localname);
            sb.append(" | qname = ");
            sb.append(qname);
            sb.append(']');

            return sb.toString();
        }
    }
}
