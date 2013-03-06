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
package org.orbeon.oxf.xforms.function;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.Int64Value;

/**
 * XForms index() function.
 *
 * 7.8.5 The index() Function
 */
public class Index extends XFormsFunction {

    public Item evaluateItem(XPathContext xpathContext) throws XPathException {
        final String repeatId = argument[0].evaluateAsString(xpathContext).toString();
        return findIndexForRepeatId(xpathContext, repeatId);
    }

    protected Item findIndexForRepeatId(XPathContext xpathContext, String repeatStaticId) {
        final int index = getXBLContainer(xpathContext).getRepeatIndex(getSourceEffectiveId(xpathContext), repeatStaticId);
        if (index == -1) {
            // Throw runtime exception
            final String message = "Function index uses repeat id '" + repeatStaticId + "' which is not in scope";
            throw new ValidationException(message, new LocationData(getSystemId(), getLineNumber(), getColumnNumber()));
        } else {
            // Return value found
            return new Int64Value(index);
        }
    }
}
