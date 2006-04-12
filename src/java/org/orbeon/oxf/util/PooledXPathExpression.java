/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.util;

import org.apache.commons.pool.ObjectPool;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.sxpath.XPathExpression;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.Value;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.saxon.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Iterator;

public class PooledXPathExpression {

    private XPathExpression expression;
    private Configuration configuration;
    private SlotManager stackFrameMap;
    private ObjectPool pool;

    // Dynamic context
    private Map variables;
    private Map variableToValueMap;
    private List contextNodeSet;
    private int contextPosition;

    public PooledXPathExpression(XPathExpression expression, ObjectPool pool, IndependentContext context, Map variables) {
        this.expression = expression;
        this.pool = pool;
        this.configuration = context.getConfiguration();
        this.stackFrameMap = context.getStackFrameMap();
        this.variables = variables;
    }

    /**
     * This *must* be called in a finally block to return the expression to the pool.
     */
    public void returnToPool() {
        try {
            pool.returnObject(this);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public List evaluate() throws XPathException {

        final NodeInfo contextNode = (NodeInfo) contextNodeSet.get(contextPosition - 1);
        final XPathContextMajor xpathContext = new XPathContextMajor(contextNode, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext);

        final SequenceExtent extent = new SequenceExtent(iter);
        return (List) extent.convertToJava(Object.class, xpathContext);
    }

    public Object evaluateSingle() throws XPathException {

        final NodeInfo contextNode = (NodeInfo) contextNodeSet.get(contextPosition - 1);
        final XPathContextMajor xpathContext = new XPathContextMajor(contextNode, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext);

        Item item = iter.next();
        if (item == null) {
            return null;
        } else {
            return Value.convert(item);
        }
    }

    private SequenceIterator evaluate(XPathContextMajor xpathContext) throws XPathException {

        // Use low-level Expression object and implement context node-set and context position
        final Expression expression = this.expression.getInternalExpression();

        final SlotManager slotManager = this.stackFrameMap; // this is already set on XPathExpressionImpl but we can't get to it
        xpathContext.setCurrentIterator(new ListSequenceIterator(contextNodeSet, contextPosition));
        xpathContext.openStackFrame(slotManager);

        // Set variable values if any
        if (variableToValueMap != null) {
            for (Iterator i = variables.entrySet().iterator(); i.hasNext();) {
                final Map.Entry entry = (Map.Entry) i.next();
                final String name = (String) entry.getKey();
                final Variable variable = (Variable) entry.getValue();
                final String value = (String) variableToValueMap.get(name);// for now we require String values
                xpathContext.setLocalVariable(variable.getLocalSlotNumber(), new StringValue(value));
            }
        }

        return expression.iterate(xpathContext);
    }

    private static class ListSequenceIterator implements SequenceIterator, Cloneable {

        private List contextNodeset;
        private int currentPosition; // 1-based

        public ListSequenceIterator(List contextNodeset, int currentPosition) {
            this.contextNodeset = contextNodeset;
            this.currentPosition = currentPosition;
        }

        public Item current() {
            if (currentPosition != -1)
                return (NodeInfo) contextNodeset.get(currentPosition - 1);
            else
                return null;
        }

        public SequenceIterator getAnother() {
            return new ListSequenceIterator(contextNodeset, 0);
        }

        public Item next() {
            if (currentPosition < contextNodeset.size()) {
                currentPosition++;
            } else {
                currentPosition = -1;
            }
            return current();
        }

        public int position() {
            return currentPosition;
        }

        public int getProperties() {
            return 0; // "It is always acceptable to return the value zero, indicating that there are no known special properties."
        }
    }

    /**
     * Set context node-set and initial position.
     *
     * @param contextNodeSet        List of NodeInfo
     * @param contextPosition       1-based current position
     */
    public void setContextNodeSet(List contextNodeSet, int contextPosition) {
        this.contextNodeSet = contextNodeSet;
        this.contextPosition = contextPosition;
    }

    public void setVariables(Map variableToValueMap) {
        this.variableToValueMap = variableToValueMap;

        if ((variables != null && variables.size() > 0) && (variableToValueMap == null || variableToValueMap.size() == 0))
            throw new OXFException("Expression requires variables.");

        if ((variables == null || variables.size() ==0) && (variableToValueMap != null && variableToValueMap.size() > 0))
            throw new OXFException("Expression does not require variables.");
    }

    public void destroy() {
        configuration = null;
        expression = null;
        pool = null;
        variables = null;
        contextNodeSet = null;
    }
}