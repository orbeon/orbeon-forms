/*
 * Copyright 1998-2002, Orbeon, Inc.
 * All Rights Reserved
 *
 * This is unpublished proprietary source code of Orbeon, Inc. The
 * copyright notice above does not evidence any actual or intended
 * publication of such source code.
 */
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.xpath.XPathException;

public class BooleanFromString extends XFormsFunction {

    public Item evaluateItem(XPathContext c) throws XPathException {
        String value = argument[0].evaluateAsString(c);
        if("1".equals(value) || "true".equals(value))
            return BooleanValue.TRUE;
        else if("0".equals(value) || "false".equals(value))
            return BooleanValue.FALSE;
        else
            throw new OXFException("boolean-to-string() argument must be in (0,1,true,false) and was " + value );
    }
}
