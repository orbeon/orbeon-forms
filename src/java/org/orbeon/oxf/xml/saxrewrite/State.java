/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xml.saxrewrite;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Base state. Simply forwards data to the destination content handler and returns itself. That is unless the (element)
 * depth becomes negative after an end element event. In this case the previous state is returned. This means btw that
 * we are really only considering state changes on start and end element events.
 */
public abstract class State {
    /**
     * At the moment are state transitions only happen on start element and end element events.
     * Therefore we track element depth and by default when the depth becomes negative we switch
     * to the previous state.
     */
    private int depth = 0;

    /**
     * The destination of the rewrite transformation.
     */
    protected final XMLReceiver xmlReceiver;

    /**
     * What you think.
     */
    protected final State previousState;

    /**
     *
     * @param previousState previous state
     * @param xmlReceiver   destination for the rewrite transformation

     * @see #previousState
     * @see #xmlReceiver
     */
    public State(final State previousState, final XMLReceiver xmlReceiver) {
        this.previousState = previousState;
        this.xmlReceiver = xmlReceiver;
    }

    /**
     * Just forwards the event to the content handler.
     *
     * @see #endElement( String, String, String )
     */
    protected void endElementStart(final String ns, final String lnam, final String qnam)
            throws SAXException {
        xmlReceiver.endElement(ns, lnam, qnam);
    }

    /**
     * @return What you think
     */
    protected int getDepth() {
        return depth;
    }

    /**
     * @see #startElement(String, String, String, Attributes)
     */
    protected abstract State startElementStart
            (final String ns, final String lnam, final String qnam, final Attributes atts)
            throws SAXException;

    public State characters(final char[] ch, final int strt, final int len)
            throws SAXException {
        xmlReceiver.characters(ch, strt, len);
        return this;
    }

    public State endDocument() throws SAXException {
        xmlReceiver.endDocument();
        return this;
    }

    /**
     * Template method. Calls endElementStart and then decrements depth. If the depth == 0 then the previous state is
     * returned. Otherwise this state is returned.
     */
    public final State endElement(final String ns, final String lnam, final String qnam)
            throws SAXException {
        endElementStart(ns, lnam, qnam);
        depth--;
        return depth == 0 ? previousState : this;
    }

    public State endPrefixMapping(final String pfx) throws SAXException {
        xmlReceiver.endPrefixMapping(pfx);
        return this;
    }

    public State ignorableWhitespace(final char[] ch, final int strt, final int len)
            throws SAXException {
        xmlReceiver.ignorableWhitespace(ch, strt, len);
        return this;
    }

    public State processingInstruction(final String trgt, final String dat)
            throws SAXException {
        xmlReceiver.processingInstruction(trgt, dat);
        return this;
    }

    public State setDocumentLocator(final Locator lctr) {
        xmlReceiver.setDocumentLocator(lctr);
        return this;
    }

    public State skippedEntity(final String nam) throws SAXException {
        xmlReceiver.skippedEntity(nam);
        return this;
    }

    public State startDocument() throws SAXException {
        xmlReceiver.startDocument();
        return this;
    }

    /**
     * Template method.  Calls startElementStart.  If startElementStart returns this increments
     * depth.  Lastly, returns the value returned by startElementStart.
     */
    public final State startElement
            (final String ns, final String lnam, final String qnam, final Attributes atts)
            throws SAXException {
        final State ret = startElementStart(ns, lnam, qnam, atts);
        if (ret == this) depth++;
        return ret;
    }

    public State startPrefixMapping(final String pfx, final String uri) throws SAXException {
        xmlReceiver.startPrefixMapping(pfx, uri);
        return this;
    }

    public State startDTD(String name, String publicId, String systemId) throws SAXException {
        xmlReceiver.startDTD(name, publicId, systemId);
        return this;
    }

    public State endDTD() throws SAXException {
        xmlReceiver.endDTD();
        return this;
    }

    public State startEntity(String name) throws SAXException {
        xmlReceiver.startEntity(name);
        return this;
    }

    public State endEntity(String name) throws SAXException {
        xmlReceiver.endEntity(name);
        return this;
    }

    public State startCDATA() throws SAXException {
        xmlReceiver.startCDATA();
        return this;
    }

    public State endCDATA() throws SAXException {
        xmlReceiver.endCDATA();
        return this;
    }

    public State comment(char[] ch, int start, int length) throws SAXException {
        xmlReceiver.comment(ch, start, length);
        return this;
    }
}
