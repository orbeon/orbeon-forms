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

public class FragmentRootState extends State {

    protected State nextState = this;

    public FragmentRootState(final State previousState, final XMLReceiver xmlReceiver) {
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
}