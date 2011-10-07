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
 * xxforms:get-request-header($header-name as xs:string) as xs:string*
 *
 * Return the value(s) of the given HTTP request header. Only supported during initialization.
 */
public class XXFormsGetRequestHeader extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        if (getContainingDocument(xpathContext).isInitializing()) {
            // Get header name
            final Expression headerNameExpression = argument[0];
            final String headerName = headerNameExpression.evaluateAsString(xpathContext).toString();

            // Get all header values
            final String[] headerValues = NetUtils.getExternalContext().getRequest().getHeaderValuesMap().get(headerName.toLowerCase());

            if (headerValues != null && headerValues.length > 0) {
                final List<StringValue> result = new ArrayList<StringValue>(headerValues.length);
                for (String headerValue: headerValues)
                    result.add(new StringValue(headerValue));
                return new ListIterator(result);
            } else {
                return EmptyIterator.getInstance();
            }
        } else {
            throw new OXFException("xxforms:get-request-header() can only be called during XForms initialization.");
        }
    }
}
