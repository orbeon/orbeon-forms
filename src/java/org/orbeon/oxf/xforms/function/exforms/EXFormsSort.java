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
package org.orbeon.oxf.xforms.function.exforms;

import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.sort.SortExpression;
import org.orbeon.saxon.sort.SortKeyDefinition;

/**
 * exforms:sort() function
 */
public class EXFormsSort extends XFormsFunction {


    public Expression preEvaluate(StaticContext staticContext) throws XPathException {
        return super.preEvaluate(staticContext);
    }

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression sequenceToSortExpression = argument[0];
        final Expression sortKeyExpression;
        {
            final Expression selectExpression = argument[1];

            sortKeyExpression = ExpressionTool.make(selectExpression.evaluateAsString(xpathContext),
                    new IndependentContext(),
                    0, Token.EOF,
                    getLineNumber());

        }
        final Expression datatypeExpression = (argument.length > 2) ? argument[2] : null;
        final Expression orderExpression = (argument.length > 3) ? argument[3] : null;
        final Expression caseOrderExpression = (argument.length > 4) ? argument[4] : null;

//        Expression langExpression = argument[5];// new in XSLT 2.0
//        Expression collationExpression = argument[6];// new in XSLT 2.0
//        Expression stableExpression = argument[7];// new in XSLT 2.0

        final SortKeyDefinition sortKey = new SortKeyDefinition();
        sortKey.setSortKey(sortKeyExpression);

        if (datatypeExpression != null)
            sortKey.setDataTypeExpression(datatypeExpression);
        if (orderExpression != null)
            sortKey.setOrder(orderExpression);
        if (caseOrderExpression != null)
            sortKey.setCaseOrder(caseOrderExpression);

//        sortKey.setLanguage(langExpression);
//        sortKey.setCollationName(collationExpression);
//        sortKey.setStable(stableExpression);

        final SortKeyDefinition[] sortKeys = { sortKey };

        return new SortExpression(sequenceToSortExpression, sortKeys).iterate(xpathContext);
    }
}
