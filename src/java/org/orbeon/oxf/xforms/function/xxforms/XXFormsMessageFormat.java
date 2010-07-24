/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms;

import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.value.Value;

import java.text.MessageFormat;
import java.util.ArrayList;

public class XXFormsMessageFormat extends XFormsFunction {

    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {
        final String template = argument[0].evaluateAsString(xpathContext).toString();
        final SequenceIterator messageArguments = argument[1].iterate(xpathContext);

        // Convert sequence to array of Java objects
        final ArrayList arguments = new ArrayList();
        {
            Item currentItem = messageArguments.next();
            while (currentItem != null) {
                arguments.add(Value.convertToJava(currentItem));
                currentItem = messageArguments.next();
            }
        }

        final MessageFormat format = new MessageFormat(template);
        // TODO: format.setLocale() with locale obtained from xml:lang, including AVT
        return StringValue.makeStringValue(format.format(arguments.toArray()));
    }
}
