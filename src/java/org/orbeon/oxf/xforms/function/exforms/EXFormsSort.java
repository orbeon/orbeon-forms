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

import org.orbeon.oxf.xforms.function.xxforms.XXFormsSort;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.functions.Evaluate;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.trans.DynamicError;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.ItemType;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.QNameValue;

import java.util.Iterator;

/**
 * exforms:sort() function
 */
public class EXFormsSort extends XXFormsSort {

    // See comments in Saxon Evaluate.java
    private IndependentContext staticContext;

    public SequenceIterator iterate(XPathContext xpathContext) throws XPathException {

        final Expression sequenceToSortExpression = argument[0];
        final Expression selectExpression = argument[1];

        final Expression sortKeyExpression;
        final XPathContextMajor newXPathContext;
        {
            Evaluate.PreparedExpression preparedExpression = prepareExpression(xpathContext, selectExpression);
            for (int i = 1; i < argument.length; i++) {
                preparedExpression.variables[i - 1].setXPathValue(ExpressionTool.eagerEvaluate(argument[i], xpathContext));
            }
            newXPathContext = xpathContext.newCleanContext();
            newXPathContext.openStackFrame(preparedExpression.stackFrameMap);
            newXPathContext.setCurrentIterator(xpathContext.getCurrentIterator());

            sortKeyExpression = preparedExpression.expression;

//            return Value.getIterator(
//                    ExpressionTool.lazyEvaluate(pexpr.expression,  c2, 1));
        }

        return sort(newXPathContext, sequenceToSortExpression, sortKeyExpression);
    }

    // The following copies all the StaticContext information into a new StaticContext
    public void checkArguments(StaticContext env) throws XPathException {
        // See same method in Saxon Evaluate.java
        if (staticContext == null) { // only do this once
            super.checkArguments(env);

            final NamespaceResolver namespaceResolver = env.getNamespaceResolver();

            staticContext = new IndependentContext(env.getConfiguration());

            staticContext.setBaseURI(env.getBaseURI());
            staticContext.setImportedSchemaNamespaces(env.getImportedSchemaNamespaces());
            staticContext.setDefaultFunctionNamespace(env.getDefaultFunctionNamespace());
            staticContext.setDefaultElementNamespace(env.getNamePool().getURIFromURICode(env.getDefaultElementNamespace()));
            staticContext.setFunctionLibrary(env.getFunctionLibrary());

            for (Iterator iterator = namespaceResolver.iteratePrefixes(); iterator.hasNext();) {
                final String prefix = (String) iterator.next();
                if (!"".equals(prefix)) {
                    final String uri = namespaceResolver.getURIForPrefix(prefix, true);
                    staticContext.declareNamespace(prefix, uri);
                }
            }
        }
    }

    // The following is heavily inspired by saxon:evaluate(). Ideally, we would just use call up the saxon:evaluate() code.
    private Evaluate.PreparedExpression prepareExpression(XPathContext xpathContext, Expression expressionToPrepare) throws XPathException {

        final Evaluate.PreparedExpression preparedExpression = new Evaluate.PreparedExpression();

        final AtomicValue exprSource = (AtomicValue) expressionToPrepare.evaluateItem(xpathContext);
        final String exprText = exprSource.getStringValue();
        final IndependentContext env = staticContext.copy();
        env.setFunctionLibrary(getExecutable().getFunctionLibrary());
        preparedExpression.expStaticContext = env;
        preparedExpression.variables = new Variable[10];
        for (int i = 1; i < 10; i++) {
            final QNameValue qname = new QNameValue("", "", "p" + i, null);
            preparedExpression.variables[i - 1] = env.declareVariable(qname);
        }

        Expression expr;
        try {
            expr = ExpressionTool.make(exprText, env, 0, Token.EOF, 1);
        } catch (XPathException e) {
            final String name = xpathContext.getNamePool().getDisplayName(getFunctionNameCode());
            final DynamicError err = new DynamicError("Static error in XPath expression supplied to " + name + ": " +
                    e.getMessage().trim());
            err.setXPathContext(xpathContext);
            throw err;
        }
        final ItemType contextItemType = Type.ITEM_TYPE;
        expr = expr.typeCheck(env, contextItemType);
        preparedExpression.stackFrameMap = env.getStackFrameMap();
        ExpressionTool.allocateSlots(expr, preparedExpression.stackFrameMap.getNumberOfVariables(), preparedExpression.stackFrameMap);
        preparedExpression.expression = expr;

        return preparedExpression;
    }
}
