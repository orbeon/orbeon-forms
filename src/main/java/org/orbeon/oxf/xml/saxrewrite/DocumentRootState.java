/**
 * Copyright (C) 2009 Orbeon, Inc.
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
import org.xml.sax.SAXException;

/**
 * Ignores everything before start element except for processing instructions. On startElement switches to nextState.
 *
 * So if this is used as the initial state then the result is that the prologue and epilogue are ignored (except
 * processing instructions) while the root element is passed to the next state. nextState is initialized to this,
 * consequently nothing interesting will happen unless setNext is called.
 */
public class DocumentRootState extends State {

    protected State nextState = this;

    public DocumentRootState(final State previousState, final XMLReceiver xmlReceiver) {
        super(previousState, xmlReceiver);
    }

    /**
     * @return nextState
     */
    protected State startElementStart(final String ns, final String lnam, final String qnam, final Attributes atts) throws SAXException {
        return nextState == this ? this : nextState.startElement(ns, lnam, qnam, atts);
    }

    public void setNextState(final State nextState) {
        this.nextState = nextState;
    }

    /**
     * @return this. Does nothing else.
     */
    public State characters(final char[] ch, final int strt, final int len) {
        return this;
    }

    /**
     * @return this. Does nothing else.
     */
    public State ignorableWhitespace(final char[] ch, final int strt, final int len) {
        return this;
    }
}