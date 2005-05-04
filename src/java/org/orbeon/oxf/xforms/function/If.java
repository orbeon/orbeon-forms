/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xforms.function;

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.xpath.XPathException;

public class If extends XFormsFunction {



    public Item evaluateItem(XPathContext c) throws XPathException {
        if(argument[0].effectiveBooleanValue(c))
            return argument[1].evaluateItem(c);
        else
            return argument[2].evaluateItem(c);
    }
}
