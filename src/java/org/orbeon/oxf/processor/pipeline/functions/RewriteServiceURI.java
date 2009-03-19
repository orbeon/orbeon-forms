/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.processor.pipeline.functions;

import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.StaticContext;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.functions.SystemFunction;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;

public class RewriteServiceURI extends SystemFunction {

    /**
     * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * (because the value of the expression depends on the runtime context)
     */
    public Expression preEvaluate(StaticContext env) {
        return this;
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get URI
        final Expression uriExpression = argument[0];
        final String uri = uriExpression.evaluateAsString(xpathContext);

        // Get mode
        final Expression modeExpression = (argument.length < 2) ? null : argument[1];
        final boolean absolute = (modeExpression == null) ? false : modeExpression.effectiveBooleanValue(xpathContext);

        // Get property value
        final String rewrittenURI = rewriteServiceURI(uri, absolute);

        return new ListIterator(Collections.singletonList(new StringValue(rewrittenURI)));
    }

    public static String rewriteServiceURI(String uri, boolean absolute) {
        return StaticExternalContext.rewriteServiceURL(uri, absolute);
    }
}
