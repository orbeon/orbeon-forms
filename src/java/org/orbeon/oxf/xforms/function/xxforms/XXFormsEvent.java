/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.function.Event;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

public class XXFormsEvent extends Event {
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {
        // Get parameter name
        final Expression instanceIdExpression = argument[0];
        final String attributeName = instanceIdExpression.evaluateAsString(xpathContext);

        // Get the current event's original event
        final XFormsEvent event = getContainingDocument(xpathContext).getCurrentEvent().getOriginalEvent();

        return getEventAttribute(event, attributeName);
    }
}
