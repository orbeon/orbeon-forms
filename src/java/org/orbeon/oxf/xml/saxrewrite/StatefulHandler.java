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
package org.orbeon.oxf.xml.saxrewrite;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Driver for a state machine that response to SAX events. Just forwards SAX events to a State which in turn returns
 * the next State.
 */
public final class StatefulHandler implements XMLReceiver {
    /**
     * The current state.
     */
    private State state;

    public StatefulHandler(final State initialState) {
        state = initialState;
    }

    public void characters(final char[] ch, final int strt, final int len)
            throws SAXException {
        state = state.characters(ch, strt, len);
    }

    public void endDocument() throws SAXException {
        state = state.endDocument();
    }

    public void endElement(final String ns, final String lnam, final String qnam)
            throws SAXException {
        state = state.endElement(ns, lnam, qnam);
    }

    public void endPrefixMapping(final String pfx) throws SAXException {
        state = state.endPrefixMapping(pfx);
    }

    public void ignorableWhitespace(final char[] ch, final int strt, final int len)
            throws SAXException {
        state = state.ignorableWhitespace(ch, strt, len);
    }

    public void processingInstruction(final String trgt, final String dat)
            throws SAXException {
        state = state.processingInstruction(trgt, dat);
    }

    public void setDocumentLocator(final Locator loc) {
        state = state.setDocumentLocator(loc);
    }

    public void skippedEntity(final String nam) throws SAXException {
        state = state.skippedEntity(nam);
    }

    public void startDocument() throws SAXException {
        state = state.startDocument();
    }

    public void startElement
            (final String ns, final String lnam, final String qnam, final Attributes atts)
            throws SAXException {
        state = state.startElement(ns, lnam, qnam, atts);
    }

    public void startPrefixMapping(final String pfx, final String uri) throws SAXException {
        state = state.startPrefixMapping(pfx, uri);
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        state = state.startDTD(name, publicId, systemId);
    }

    public void endDTD() throws SAXException {
        state = state.endDTD();
    }

    public void startEntity(String name) throws SAXException {
        state = state.startEntity(name);
    }

    public void endEntity(String name) throws SAXException {
        state = state.endEntity(name);
    }

    public void startCDATA() throws SAXException {
        state = state.startCDATA();
    }

    public void endCDATA() throws SAXException {
        state = state.endCDATA();
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        state = state.comment(ch, start, length);
    }
}
