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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ExpressionVisitor;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

public class XXFormsRewriteServiceURI extends SystemFunction {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    @Override
    public Expression preEvaluate(ExpressionVisitor visitor) throws XPathException {
        return this;
    }

    @Override
    public Item evaluateItem(XPathContext xpathContext) throws XPathException {

        // Get URI
        final Expression uriExpression = argument[0];
        final String uri = uriExpression.evaluateAsString(xpathContext).toString();

        // Get mode
        final Expression modeExpression = (argument.length < 2) ? null : argument[1];
        final boolean absolute = (modeExpression != null) && modeExpression.effectiveBooleanValue(xpathContext);

        // Get property value
        final String rewrittenURI = rewriteServiceURI(uri, absolute);

        return StringValue.makeStringValue(rewrittenURI);
    }

    public static String rewriteServiceURI(String uri, boolean absolute) {
        return NetUtils.getExternalContext().rewriteServiceURL(uri,
                absolute ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE : ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
    }
}
