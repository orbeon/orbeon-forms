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

import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.DoubleValue;

/**
 * XForms random() function (XForms 1.1).
 */
public class Random extends XFormsFunction {

    private static java.util.Random random;

    public Item evaluateItem(XPathContext c) throws XPathException {

        // TODO: We should also support the "non-seeded" mode, but this seems to imply that, in order to keep a
        // reproducible sequence, we need to keep the state per containing document, and also to be able to serialize
        // the state to the dynamic state.

//        final Expression seedExpression = (argument == null || argument.length == 0) ? null : argument[0];
//        final boolean isSeed = (seedExpression != null) && argument[0].effectiveBooleanValue(c);

        if (random == null) {
            // Initialize with seed
            random = new java.util.Random();
        }

        return new DoubleValue(random.nextDouble());

//        final java.util.Random random = isSeed ? new java.util.Random() : new java.util.Random(0);
//        return new StringValue(XMLUtils.removeScientificNotation(random.nextDouble()));
    }
}
