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

import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;

public class XXFormsProperty extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get property name
        final Expression propertyNameExpression = argument[0];
        final String propertyName = propertyNameExpression.evaluateAsString(xpathContext);

        // Get property value
        final String propertyValue = property(propertyName);

        // Return iterator
        if (propertyValue != null)
            return new ListIterator(Collections.singletonList(new StringValue(propertyValue)));
        else
            return EmptyIterator.getInstance();
    }

    public static String property(String propertyName) {
        // Never return any property containing the string "password" as a first line of defense
        if (propertyName.trim().indexOf("password") != -1) {
            return null;
        }

        // Get property value
        final Object propertyValue = OXFProperties.instance().getPropertySet().getObject(propertyName);
        return (propertyValue == null) ? null : propertyValue.toString();
    }
}
