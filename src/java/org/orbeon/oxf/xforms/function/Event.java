/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

import java.util.Collections;

/**
 * 2.4 Accessing Context Information for Events
 *
 * This is the event() function which returns "context specific information" for an event.
 */
public class Event extends XFormsFunction {
    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
    * (because the value of the expression depends on the runtime context)
    */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {
        // Get parameter name
        final Expression instanceIdExpression = argument[0];
        final String attributeName = instanceIdExpression.evaluateAsString(xpathContext);

        // Get the current event
        final XFormsEvent event = getXFormsContainingDocument().getCurrentEvent();

        // TODO: Currently the spec doesn't specify what happens when we call event() outside of an event handler
        if (event == null)
            return new ListIterator(Collections.EMPTY_LIST);

        // Simply ask the event for the attribute
        return event.getAttribute(attributeName);
    }
}
