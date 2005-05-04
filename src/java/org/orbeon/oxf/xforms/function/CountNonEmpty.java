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
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.value.IntegerValue;
import org.orbeon.saxon.xpath.XPathException;

public class CountNonEmpty extends XFormsFunction {

    public Item evaluateItem(XPathContext c) throws XPathException {
        int count = 0;
        SequenceIterator iterator = argument[0].iterate(c);
        for(Item item = iterator.next(); item != null; item = iterator.next()) {
            if(item.getStringValue().length() > 0)
                count++;
        }
        return new IntegerValue(count);
    }
}
