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

import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;

import java.util.List;


public class XXFormsComponentContext extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get container binding context
        final XFormsContextStack.BindingContext bindingContext = getXBLContainer(xpathContext).getBindingContext();
        if (bindingContext == null)
            return EmptyIterator.getInstance();

        // Get current context
        final List currentContext = bindingContext.getNodeset();
        if (currentContext != null)
            return new ListIterator(currentContext);
        else
            return EmptyIterator.getInstance();
    }
}
