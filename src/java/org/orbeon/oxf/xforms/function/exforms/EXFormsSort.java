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

import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsSort;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.NamespaceResolver;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.trans.DynamicError;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.AtomicValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
            // NOTE: It would be better if we could use XPathCache/PooledXPathExpression instead of rewriting custom
            // code below. This would provide caching of compiled expressions, abstraction and some simplicity.

            // Prepare sorting expression
            final PreparedExpression preparedExpression = prepareExpression(xpathContext, selectExpression);

            // Create new dynamic context
            newXPathContext = xpathContext.newCleanContext();
            newXPathContext.openStackFrame(preparedExpression.stackFrameMap);
            newXPathContext.setCurrentIterator(xpathContext.getCurrentIterator());

            // Set variable values
            if (preparedExpression.variables != null) {
                for (Iterator i = preparedExpression.variables.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry entry = (Map.Entry) i.next();
                    final String name = (String) entry.getKey();
                    final Variable variable = (Variable) entry.getValue();

                    final Object object = preparedExpression.inScopeVariables.get(name);
                    if (object != null) {
                        // Convert Java object to Saxon object
                        final ValueRepresentation valueRepresentation = XFormsUtils.convertJavaObjectToSaxonObject(object);
                        newXPathContext.setLocalVariable(variable.getLocalSlotNumber(), valueRepresentation);
                    }
                }
            }

            sortKeyExpression = preparedExpression.expression;
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

    // The following is inspired by saxon:evaluate()
    private PreparedExpression prepareExpression(XPathContext xpathContext, Expression expressionToPrepare) throws XPathException {

        final PreparedExpression preparedExpression = new PreparedExpression();

        final String exprText;
        {
            final AtomicValue exprSource = (AtomicValue) expressionToPrepare.evaluateItem(xpathContext);
            exprText = exprSource.getStringValue();
        }

        // Copy static context information
        final IndependentContext env = staticContext.copy();
        // We do staticContext.setFunctionLibrary(env.getFunctionLibrary()) above, so why would we need this?
//        env.setFunctionLibrary(getExecutable().getFunctionLibrary());
        preparedExpression.expStaticContext = env;

        // Propagate in-scope variable definitions since they are not copied automatically
        final XFormsContextStack contextStack = getContextStack(xpathContext);
        preparedExpression.inScopeVariables = contextStack.getCurrentBindingContext().getInScopeVariables();
        preparedExpression.variables = new HashMap();
        {
            if (preparedExpression.inScopeVariables != null) {
                for (Iterator i = preparedExpression.inScopeVariables.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String name = (String) currentEntry.getKey();

                    final Variable variable = env.declareVariable(name);
                    variable.setUseStack(true);// "Indicate that values of variables are to be found on the stack, not in the Variable object itself"

                    preparedExpression.variables.put(name, variable);
                }
            }
        }

        // Create expression
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

        // Prepare expression
        expr = expr.typeCheck(env, Type.ITEM_TYPE);
        preparedExpression.stackFrameMap = env.getStackFrameMap();
        ExpressionTool.allocateSlots(expr, preparedExpression.stackFrameMap.getNumberOfVariables(), preparedExpression.stackFrameMap);
        preparedExpression.expression = expr;

        return preparedExpression;
    }

    public static class PreparedExpression implements java.io.Serializable {
        public IndependentContext expStaticContext;
        public Expression expression;
        public Map /* <String name, ValueRepresentation value> */ inScopeVariables;
        public Map /* <String name, Variable variable> */ variables;
        public SlotManager stackFrameMap;
    }
}
