/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xml;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;
import org.orbeon.oxf.common.OXFException;

/**
 * Just like ForwardingContentHandler (a SAX content handler
 * that forwards SAX events to another content handler), but
 * checks the validity of the SAX stream.
 *
 * TODO
 *   - Check the closing element corresponds to opening element
 *   - Check consistency of uri, localname, and qname in startElement/endElement
 *   - Namespace consistency
 */
public class InspectingContentHandler extends ForwardingContentHandler {

    boolean documentStarted = false;
    boolean documentEnded = false;
    int elementDepth = 0;

    public InspectingContentHandler(ContentHandler contentHandler) {
        super(contentHandler);
    }

    public void startDocument() throws SAXException {
        documentStarted = true;
        super.startDocument();
    }

    public void endDocument() throws SAXException {
        if (elementDepth != 0)
            throw new OXFException("Document ended before all the elements are closed");
        documentEnded = false;
        super.endDocument();
    }

    public void startElement(String uri, String localname, String qname, Attributes attributes) throws SAXException {
        String error = checkInDocument();
        if (error != null)
            throw new OXFException(error + ": element " + qname);
        elementDepth++;
        checkName(uri, localname, qname);
        for (int i = 0; i < attributes.getLength(); i++)
             checkName(attributes.getURI(i), attributes.getLocalName(i), attributes.getQName(i));
        super.startElement(uri, localname, qname, attributes);
    }

    public void endElement(String uri, String localname, String qname) throws SAXException {
        String error = checkInElement();
        if (error != null)
            throw new OXFException(error + ": element " + qname);
        elementDepth--;
        checkName(uri, localname, qname);
        super.endElement(uri, localname, qname);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        String error = checkInElement();
        if (error != null)
            throw new OXFException(error + ": '" + new String(chars, start, length) + "'");
        super.characters(chars, start, length);
    }

    private String checkInElement() {
        String error = checkInDocument();
        if (error != null) {
            return error;
        } else if (elementDepth == 0) {
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

    private void checkName(String uri, String localname, String qname) {
        if (localname == null || "".equals(localname))
            throw new OXFException("Empty local name in SAX event");
        if (qname == null || "".equals(qname))
            throw new OXFException("Empty qualified name in SAX event");
    }
}
