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

import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.BooleanValue;

/**
 * xxforms:is-user-in-role(xs:string) as xs:boolean
 *
 * Returns user name of current user, or empty sequence
 */
public class XXFormsIsUserInRole extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {
        // Get role property
        final Expression propertyNameExpression = argument[0];
        final String role = propertyNameExpression.evaluateAsString(xpathContext).toString();

        return SingletonIterator.makeIterator(
                NetUtils.getExternalContext().getRequest().isUserInRole(role) ? BooleanValue.TRUE : BooleanValue.FALSE);
    }
}