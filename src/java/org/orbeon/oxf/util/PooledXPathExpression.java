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
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PooledXPathExpression {

    private Expression expression;
    private Configuration configuration;
    private SlotManager stackFrameMap;
    private ObjectPool pool;
    private Map variables;

    // Dynamic context
    private Map variableToValueMap;
    private List contextItems;
    private int contextPosition;

    public PooledXPathExpression(Expression expression, ObjectPool pool, IndependentContext context, Map variables) {
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
            // Free up dynamic context references
            variableToValueMap = null;
            contextItems = null;

            // Return object to pool
            if (pool != null) // may be null for testing
                pool.returnObject(this);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Evaluate and return a List of native Java objects, including underlying wrapped nodes.
     */
    public List evaluate() throws XPathException {

        final Item contextItem = (Item) contextItems.get(contextPosition - 1);
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, null);

        final SequenceExtent extent = new SequenceExtent(iter);
        return (List) extent.convertToJava(Object.class, xpathContext);
    }

    /**
     * Evaluate and return a List of native Java objects, but keep NodeInfo objects.
     */
    public List evaluateKeepNodeInfo(Object functionContext) throws XPathException {
        final Item contextItem = (contextItems.size() > contextPosition - 1) ? (Item) contextItems.get(contextPosition - 1) : null;
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        final SequenceExtent extent = new SequenceExtent(iter);
        return convertToJavaKeepNodeInfo(extent, xpathContext);
    }

    /**
     * Evaluate and return a List of Item objects.
     */
    public List evaluateKeepItems(Object functionContext) throws XPathException {
        final Item contextItem = (contextItems.size() > contextPosition - 1) ? (Item) contextItems.get(contextPosition - 1) : null;
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        final ArrayList result = new ArrayList();
        Item next;
        while ((next = iter.next()) != null) {
            result.add(next);
        }
        return result;
    }

    /**
     * Evaluate the expression as a variable value usable by Saxon in further XPath expressions.
     */
    public SequenceExtent evaluateAsExtent(Object functionContext) throws XPathException {
        final Item contextItem = (contextItems.size() > contextPosition - 1) ? (Item) contextItems.get(contextPosition - 1) : null;
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        return new SequenceExtent(iter);
    }

    private List convertToJavaKeepNodeInfo(SequenceExtent extent, XPathContext context) throws XPathException {
        final List result = new ArrayList(extent.getLength());
        final SequenceIterator iter = extent.iterate(null);
        while (true) {
            final Item currentItem = iter.next();
            if (currentItem == null) {
                return result;
            }
            if (currentItem instanceof AtomicValue) {
                result.add(((AtomicValue) currentItem).convertToJava(Object.class, context));
            } else {
                result.add(currentItem);
            }
        }
    }

    /**
     * Evaluate and return a single native Java object, including underlying wrapped nodes. Return null if the
     * evaluation doesn't return any item.
     */
    public Object evaluateSingle() throws XPathException {

        final Item contextItem = (Item) contextItems.get(contextPosition - 1);
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, null);

        final Item firstItem = iter.next();
        if (firstItem == null) {
            return null;
        } else {
            return Value.convert(firstItem);
        }
    }

    /**
     * Evaluate and return a single native Java object, but keep NodeInfo objects. Return null if the evaluation
     * doesn't return any item.
     */
    public Object evaluateSingleKeepNodeInfo(Object functionContext) throws XPathException {

        final Item contextItem = (Item) contextItems.get(contextPosition - 1);
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        final Item firstItem = iter.next();
        if (firstItem == null) {
            return null;
        } else {
            if (firstItem instanceof AtomicValue) {
                return Value.convert(firstItem);
            } else {
                return firstItem;
            }
        }
    }

    /**
     * Return the function context passed to the expression if any.
     */
    public static Object getFunctionContext(XPathContext xpathContext) {
        return xpathContext.getController().getUserData("", PooledXPathExpression.class.getName());
    }

    private SequenceIterator evaluate(XPathContextMajor xpathContext, Object functionContext) throws XPathException {

        // Pass function context to controller
        xpathContext.getController().setUserData("", this.getClass().getName(), functionContext);

        // Use low-level Expression object and implement context node-set and context position
        final SlotManager slotManager = this.stackFrameMap; // this is already set on XPathExpressionImpl but we can't get to it
        xpathContext.setCurrentIterator(new ListSequenceIterator(contextItems, contextPosition));
        xpathContext.openStackFrame(slotManager);

        // Set variable values if any
        if (variableToValueMap != null) {
            for (Iterator i = variables.entrySet().iterator(); i.hasNext();) {
                final Map.Entry entry = (Map.Entry) i.next();
                final String name = (String) entry.getKey();
                final Variable variable = (Variable) entry.getValue();

                final Object object = variableToValueMap.get(name);
                if (object != null) {
                    final ValueRepresentation valueRepresentation;
                    if (object instanceof ValueRepresentation) {
                        // Native Saxon variable value
                        valueRepresentation = (ValueRepresentation) object;
                    } else if (object instanceof String) {
                        valueRepresentation = new StringValue((String) object);
                    } else if (object instanceof Integer) {
                        valueRepresentation = new IntegerValue(((Integer) object).intValue());
                    } else if (object instanceof Float) {
                        valueRepresentation = new FloatValue(((Float) object).floatValue());
                    } else if (object instanceof Double) {
                        valueRepresentation = new DoubleValue(((Double) object).doubleValue());
                    } else {
                        throw new OXFException("Invalid variable type: " + object.getClass());
                    }

                    xpathContext.setLocalVariable(variable.getLocalSlotNumber(), valueRepresentation);
                }
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
            if (currentPosition != -1 && contextNodeset.size() > currentPosition - 1)
                return (Item) contextNodeset.get(currentPosition - 1);
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
     * @param contextItems          List of Item
     * @param contextPosition       1-based current position
     */
    public void setContextItems(List contextItems, int contextPosition) {
        this.contextItems = contextItems;
        this.contextPosition = contextPosition;
    }

    public void setVariables(Map variableToValueMap) {
        // NOTE: We used to attempt to decect whether the expression required variables or not and throw an exception,
        // but we can't really detect this because there may be variables in scope even if the expression does not use
        // them. Conversely, if there are undeclared variables, we let the XPath engine complain about that.
        this.variableToValueMap = variableToValueMap;
    }

    public void destroy() {
        expression = null;
        pool = null;
        configuration = null;
        stackFrameMap = null;
        variables = null;

        variableToValueMap = null;
        contextItems = null;
    }
}