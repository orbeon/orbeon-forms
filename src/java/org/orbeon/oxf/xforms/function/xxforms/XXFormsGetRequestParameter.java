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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * xxforms:get-request-parameter($a as xs:string) xs:string*
 *
 * Return the value(s) of the given HTTP request parameter. Only supported during initialization.
 */
public class XXFormsGetRequestParameter extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        if (getContainingDocument(xpathContext).isInitializing()) {
            // Get parameter name
            final Expression parameterNameExpression = argument[0];
            final String parameterName = parameterNameExpression.evaluateAsString(xpathContext).toString();

            // Get parameter value
            final Object[] parameterValues = NetUtils.getExternalContext().getRequest().getParameterMap().get(parameterName);
            if (parameterValues != null) {
                final List<StringValue> result = new ArrayList<StringValue>(parameterValues.length);
                for (final Object currentValue: parameterValues) {
                    if (currentValue instanceof String) {
                        result.add(new StringValue((CharSequence) currentValue));
                    }
                }

                return result.size() <= 0 ? EmptyIterator.getInstance() : (SequenceIterator) new ListIterator(result);
            } else {
                return EmptyIterator.getInstance();
            }
        } else {
            throw new OXFException("xxforms:get-request-parameter() can only be called during XForms initialization.");
        }
    }
}
