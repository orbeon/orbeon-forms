/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.processor.xforms.output.function;

import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.oxf.processor.xforms.output.element.XFormsElementContext;

public class Last extends SystemFunction {

    private XFormsElementContext xformsElementContext;

    public Last(XFormsElementContext xformsElementContext) {
        this.xformsElementContext = xformsElementContext;
    }

    public Item evaluateItem(XPathContext c) throws XPathException {
        return new IntegerValue(xformsElementContext.getCurrentNodeset().size());
    }
}
