/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.util;

import org.apache.commons.pool.ObjectPool;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.om.VirtualNode;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PooledXPathExpression {

    private Expression expression;
    private Configuration configuration;
    private SlotManager stackFrameMap;
    private ObjectPool pool;
    private Map<String, Variable> variables;

    // Dynamic context
    private Map<String, ValueRepresentation> variableToValueMap;
    private List<Item> contextItems;
    private int contextPosition;

    public PooledXPathExpression(Expression expression, ObjectPool pool, IndependentContext context, Map<String, Variable> variables) {
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

    private Item getContextItem() {
        return (contextPosition > 0 && contextItems.size() > contextPosition - 1) ? contextItems.get(contextPosition - 1) : null;
    }

    /**
     * Evaluate and return an iterator over native Java objects, including underlying wrapped nodes.
     */
    public Iterator iterate() throws XPathException {

        final Item contextItem = getContextItem();
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, null);

        return new Iterator() {

            private Item currentItem = iter.next();

            public boolean hasNext() {
                return currentItem != null;
            }

            public Object next() {
                final Item itemToReturn = currentItem;
                // Advance
                try {
                    currentItem = iter.next();
                } catch (XPathException e) {
                    throw new OXFException(e);
                }
                // Convert
                if (itemToReturn instanceof AtomicValue) {
                    try {
                        return ((AtomicValue)itemToReturn).convertToJava(Object.class, xpathContext);
                    } catch (XPathException e) {
                        throw new OXFException(e);
                    }
                } else if (itemToReturn instanceof VirtualNode) {
                    return ((VirtualNode)itemToReturn).getUnderlyingNode();
                } else {
                    return itemToReturn;
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Evaluate and return a List of native Java objects, including underlying wrapped nodes.
     */
    public List evaluate() throws XPathException {

        final Item contextItem = getContextItem();
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, null);

        final SequenceExtent extent = new SequenceExtent(iter);
        return (List) extent.convertToJava(Object.class, xpathContext);
    }

    /**
     * Evaluate and return a List of native Java objects, but keep NodeInfo objects.
     */
    public List<Object> evaluateKeepNodeInfo(Object functionContext) throws XPathException {
        final Item contextItem = getContextItem();
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        final SequenceExtent extent = new SequenceExtent(iter);
        return convertToJavaKeepNodeInfo(extent, xpathContext);
    }

    /**
     * Evaluate and return a List of Item objects.
     */
    public List<Item> evaluateKeepItems(Object functionContext) throws XPathException {
        final Item contextItem = getContextItem();
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        final List<Item> result = new ArrayList<Item>();
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
        final Item contextItem = getContextItem();
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        final SequenceIterator iter = evaluate(xpathContext, functionContext);

        return new SequenceExtent(iter);
    }

    private List<Object> convertToJavaKeepNodeInfo(SequenceExtent extent, XPathContext context) throws XPathException {
        final List<Object> result = new ArrayList<Object>(extent.getLength());
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

        final Item contextItem = getContextItem();
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

        final Item contextItem = getContextItem();
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
        prepareContext(xpathContext, functionContext);
        return expression.iterate(xpathContext);
    }

    /**
     * Evaluate the expression as a boolean value.
     */
    public boolean evaluateAsBoolean(Object functionContext) throws XPathException {
        final Item contextItem = getContextItem();
        final XPathContextMajor xpathContext = new XPathContextMajor(contextItem, this.configuration);
        prepareContext(xpathContext, functionContext);
        // TODO: this actually doesn't work like cast as xs:boolean, which is the purpose of this
//        return expression.effectiveBooleanValue(xpathContext);
        throw new OXFException("NIY");
    }

    private void prepareContext(XPathContextMajor xpathContext, Object functionContext) throws XPathException {

        // Pass function context to controller
        xpathContext.getController().setUserData("", this.getClass().getName(), functionContext);

        // Use low-level Expression object and implement context node-set and context position
        final SlotManager slotManager = this.stackFrameMap; // this is already set on XPathExpressionImpl but we can't get to it
        xpathContext.setCurrentIterator(new ListSequenceIterator(contextItems, contextPosition));
        xpathContext.openStackFrame(slotManager);

        // Set variable values if any
        if (variableToValueMap != null) {
            for (Map.Entry<String, Variable> entry: variables.entrySet()) {
                final String name = entry.getKey();
                final Variable variable = entry.getValue();

                final ValueRepresentation object = variableToValueMap.get(name);
                if (object != null) {
                    // Convert Java object to Saxon object
                    final ValueRepresentation valueRepresentation = XFormsUtils.convertJavaObjectToSaxonObject(object);
                    xpathContext.setLocalVariable(variable.getLocalSlotNumber(), valueRepresentation);
                }
            }
        }
    }

    private static class ListSequenceIterator implements SequenceIterator, Cloneable {

        private List<Item> contextItems;
        private int currentPosition; // 1-based

        public ListSequenceIterator(List<Item> contextItems, int currentPosition) {
            this.contextItems = contextItems;
            this.currentPosition = currentPosition;
        }

        public Item current() {
            if (currentPosition > 0 && contextItems.size() > currentPosition - 1)
                return contextItems.get(currentPosition - 1);
            else
                return null;
        }

        public SequenceIterator getAnother() {
            return new ListSequenceIterator(contextItems, 0);
        }

        public Item next() {
            if (currentPosition < contextItems.size()) {
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
    public void setContextItems(List<Item> contextItems, int contextPosition) {
        this.contextItems = contextItems;
        this.contextPosition = contextPosition;
    }

    public void setVariables(Map<String, ValueRepresentation> variableToValueMap) {
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