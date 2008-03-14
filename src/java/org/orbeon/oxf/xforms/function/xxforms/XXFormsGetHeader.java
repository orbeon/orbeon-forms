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

import org.orbeon.oxf.pipeline.StaticExternalContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.StringValue;

import java.util.Collections;

public class XXFormsGetHeader extends XFormsFunction {

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        // Get header name
        final Expression headerNameExpression = argument[0];
        final String headerName = headerNameExpression.evaluateAsString(xpathContext);

        // Get header value

        // Obtain PipelineContext - this function is always called from controls so
        // PipelineContext should be present
        final StaticExternalContext.StaticContext staticContext = StaticExternalContext.getStaticContext();
        final ExternalContext externalContext = staticContext.getExternalContext();

        // TODO: getHeaderMap() returns a single header, but should really return all occurrences
        final String headerValue = (String) externalContext.getRequest().getHeaderMap().get(headerName.toLowerCase());

        if (headerValue != null)
            return new ListIterator(Collections.singletonList(new StringValue(headerValue)));
        else
            return new ListIterator(Collections.EMPTY_LIST);
    }
}
