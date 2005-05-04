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
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.xpath.StaticError;
import org.orbeon.saxon.xpath.XPathException;

public class Property extends XFormsFunction {

    private static final StringValue VERSION = new StringValue("1.0");
    private static final StringValue CONFORMANCE = new StringValue("full");

    private static final String VERSTION_PROPERTY = "version";
    private static final String CONFORMANCE_PROPERTY = "conformance-level";

    public Item evaluateItem(XPathContext c) throws XPathException {
        String arg = argument[0].evaluateAsString(c);
        if(VERSTION_PROPERTY.equals(arg))
            return VERSION;
        else if(CONFORMANCE_PROPERTY.equals(arg))
            return CONFORMANCE;
        else
            throw new StaticError("property() function accepts only two property names: 'version' and 'conformance-level'");

    }
}
