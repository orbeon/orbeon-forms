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
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.PathMap;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.sort.AtomicComparer;
import org.orbeon.saxon.sort.SortKeyDefinition;
import org.orbeon.saxon.sort.SortKeyEvaluator;
import org.orbeon.saxon.sort.SortedIterator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.Value;

/**
 * exforms:sort() function
 */
public class XXFormsSort extends XFormsFunction {

    @Override
    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression sequenceToSortExpression = argument[0];
        final Expression sortKeyExpression = argument[1];

        return sort(xpathContext, null, sequenceToSortExpression, sortKeyExpression);
    }

    protected SequenceIterator sort(XPathContext xpathContext, final XPathContext keyXPathContext, Expression sequenceToSortExpression, final Expression sortKeyExpression) throws XPathException {
        final SortKeyEvaluator sortKeyEvaluator = new SortKeyEvaluator() {
            public Item evaluateSortKey(int n, XPathContext context) throws XPathException {

                // The context may be provided from "outside" (e.g. because the expression was compiled at runtime).
                // In that case, use it, and make sure it has the correct current iterator.

                final XPathContext sortKeyContext; {
                    if (keyXPathContext != null) {
                        sortKeyContext = keyXPathContext;
                        sortKeyContext.setCurrentIterator(context.getCurrentIterator());
                    } else {
                        sortKeyContext = context;
                    }
                }

                Item c = sortKeyExpression.evaluateItem(sortKeyContext);
                if (c instanceof NodeInfo) {
                    final Value v = ((NodeInfo)c).atomize();
                    if (v.getLength() == 0) {
                        c = null;
                    } else if (v.getLength() == 1) {
                        c = v.itemAt(0);
                    } else {
                        throw new XPathException("error in saxon:sort() - a node has a typed value of length > 1");
                    }
                }
                return c;
            }
        };
        final SortKeyDefinition sortKeyDefinition = getSortKeyDefinition(sortKeyExpression);
        final AtomicComparer comparer = sortKeyDefinition.makeComparator(xpathContext);
        final AtomicComparer[] comparers = { comparer };
        return new SortedIterator(xpathContext, sequenceToSortExpression.iterate(xpathContext), sortKeyEvaluator, comparers);
    }
    
    private SortKeyDefinition getSortKeyDefinition(Expression sortKeyExpression) {
        final Expression datatypeExpression = (argument.length > 2) ? argument[2] : null;
        final Expression orderExpression = (argument.length > 3) ? argument[3] : null;
        final Expression caseOrderExpression = (argument.length > 4) ? argument[4] : null;

//        Expression langExpression = argument[5];// new in XSLT 2.0
//        Expression collationExpression = argument[6];// new in XSLT 2.0
//        Expression stableExpression = argument[7];// new in XSLT 2.0

        final SortKeyDefinition sortKeyDefinition = new SortKeyDefinition();
        sortKeyDefinition.setSortKey(sortKeyExpression);

        if (datatypeExpression != null)
            sortKeyDefinition.setDataTypeExpression(datatypeExpression);
        if (orderExpression != null)
            sortKeyDefinition.setOrder(orderExpression);
        if (caseOrderExpression != null)
            sortKeyDefinition.setCaseOrder(caseOrderExpression);

//        sortKey.setLanguage(langExpression);
//        sortKey.setCollationName(collationExpression);
//        sortKey.setStable(stableExpression);

        return sortKeyDefinition;
    }

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        final PathMap.PathMapNodeSet target = argument[0].addToPathMap(pathMap, pathMapNodeSet);

        final SortKeyDefinition sortKeyDefinition = getSortKeyDefinition(argument[1]);

        // Sort key
        sortKeyDefinition.getSortKey().addToPathMap(pathMap, target);

        // Sort key parameters
        Expression e = sortKeyDefinition.getOrder();
        if (e != null) {
            e.addToPathMap(pathMap, pathMapNodeSet);
        }
        e = sortKeyDefinition.getCaseOrder();
        if (e != null) {
            e.addToPathMap(pathMap, pathMapNodeSet);
        }
        e = sortKeyDefinition.getDataTypeExpression();
        if (e != null) {
            e.addToPathMap(pathMap, pathMapNodeSet);
        }
        e = sortKeyDefinition.getLanguage();
        if (e != null) {
            e.addToPathMap(pathMap, pathMapNodeSet);
        }
        e = sortKeyDefinition.getCollationNameExpression();
        if (e != null) {
            e.addToPathMap(pathMap, pathMapNodeSet);
        }

        return target;
    }
}
