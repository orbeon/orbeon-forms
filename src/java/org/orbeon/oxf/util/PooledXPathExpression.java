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
package org.orbeon.oxf.util;

import org.apache.commons.pool.ObjectPool;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.om.VirtualNode;
import org.orbeon.saxon.sxpath.XPathDynamicContext;
import org.orbeon.saxon.sxpath.XPathExpression;
import org.orbeon.saxon.sxpath.XPathVariable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.Value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PooledXPathExpression {

    private XPathExpression  expression;
    private ObjectPool pool;
    private Map<String, XPathVariable> variables;

    // Dynamic context
    private Map<String, ValueRepresentation> variableToValueMap;
    private Item contextItem;
    private int contextPosition;

    public PooledXPathExpression(XPathExpression expression, ObjectPool pool, Map<String, XPathVariable> variables) {
        this.expression = expression;
        this.pool = pool;
        this.variables = variables;
    }

    /**
     * This *must* be called in a finally block to return the expression to the pool.
     */
    public void returnToPool() {
        try {
            // Free up dynamic context references
            variableToValueMap = null;
            contextItem = null;

            // Return object to pool
            if (pool != null) // may be null for testing
                pool.returnObject(this);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public Expression getExpression() {
        return expression.getInternalExpression();
    }

    private Item getContextItem() {
        return contextItem;
    }

    /**
     * Evaluate and return an iterator over native Java objects, including underlying wrapped nodes.
     */
    public Iterator iterate() throws XPathException {

        final Item contextItem = getContextItem();
        final SequenceIterator iter = expression.iterate(expression.createDynamicContext(contextItem));

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
                        return Value.convertToJava(itemToReturn);
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
        return convertToJava(evaluate(null));
    }

    /**
     * Evaluate and return a List of native Java objects, but keep NodeInfo objects.
     */
    public List<Object> evaluateKeepNodeInfo(Object functionContext) throws XPathException {
        final SequenceIterator iter = evaluate(functionContext);

        final SequenceExtent extent = new SequenceExtent(iter);
        return convertToJavaKeepNodeInfo(extent);
    }

    /**
     * Evaluate and return a List of Item objects.
     */
    public List<Item> evaluateKeepItems(Object functionContext) throws XPathException {
        final SequenceIterator iter = evaluate(functionContext);

        final List<Item> result = new ArrayList<Item>();
        Item next;
        while ((next = iter.next()) != null) {
            result.add(next);
        }
        return result;
    }

    /**
     * Evaluate and return a List of Item objects.
     */
    public Item evaluateSingleKeepItems(Object functionContext) throws XPathException {
        final SequenceIterator iter = evaluate(functionContext);
        return iter.next();
    }

    /**
     * Evaluate the expression as a variable value usable by Saxon in further XPath expressions.
     */
    public SequenceExtent evaluateAsExtent(Object functionContext) throws XPathException {
        final SequenceIterator iter = evaluate(functionContext);

        return new SequenceExtent(iter);
    }

    private List<Object> convertToJavaKeepNodeInfo(SequenceExtent extent) throws XPathException {
        final List<Object> result = new ArrayList<Object>(extent.getLength());
        final SequenceIterator iter = extent.iterate();
        while (true) {
            final Item currentItem = iter.next();
            if (currentItem == null) {
                return result;
            }
            if (currentItem instanceof AtomicValue) {
                result.add(Value.convertToJava(currentItem));
            } else {
                result.add(currentItem);
            }
        }
    }

//    private List<Object> convertToJava(SequenceExtent extent) throws XPathException {
//        final List<Object> result = new ArrayList<Object>(extent.getLength());
//        final SequenceIterator iter = extent.iterate(null);
//        while (true) {
//            final Item currentItem = iter.next();
//            if (currentItem == null) {
//                return result;
//            }
//            result.add(Value.convertToJava(currentItem));
//        }
//    }

    private List<Object> convertToJava(SequenceIterator iterator) throws XPathException {
        final List<Object> result = new ArrayList<Object>();
        while (true) {
            final Item currentItem = iterator.next();
            if (currentItem == null) {
                return result;
            }
            result.add(Value.convertToJava(currentItem));
        }
    }

    /**
     * Evaluate and return a single native Java object, including underlying wrapped nodes. Return null if the
     * evaluation doesn't return any item.
     */
    public Object evaluateSingle() throws XPathException {
        final SequenceIterator iter = evaluate(null);

        final Item firstItem = iter.next();
        if (firstItem == null) {
            return null;
        } else {
            return Value.convertToJava(firstItem);
        }
    }

    /**
     * Evaluate and return a single native Java object, but keep NodeInfo objects. Return null if the evaluation
     * doesn't return any item.
     */
    public Object evaluateSingleKeepNodeInfo(Object functionContext) throws XPathException {
        final SequenceIterator iter = evaluate(functionContext);

        final Item firstItem = iter.next();
        if (firstItem == null) {
            return null;
        } else {
            if (firstItem instanceof AtomicValue) {
                return Value.convertToJava(firstItem);
            } else {
                return firstItem;
            }
        }
    }

    public Expression prepareExpression(XPathContextMajor xpathContext, Object functionContext) throws XPathException {
        prepareDynamicContext(xpathContext, functionContext);
        return expression.getInternalExpression();
    }

    /**
     * Return the function context passed to the expression if any.
     */
    public static Object getFunctionContext(XPathContext xpathContext) {
        return xpathContext.getController().getUserData("", PooledXPathExpression.class.getName());
    }

    private SequenceIterator evaluate(Object functionContext) throws XPathException {
        final XPathDynamicContext dynamicContext = prepareDynamicContext(null, functionContext);
        return expression.iterate(dynamicContext);
    }

    public XPathDynamicContext prepareDynamicContext(XPathContextMajor xpathContext, Object functionContext) throws XPathException {

        final XPathDynamicContext dynamicContext;
        if (xpathContext == null) {
            dynamicContext = expression.createDynamicContext(contextItem, contextPosition);
            xpathContext = (XPathContextMajor) dynamicContext.getXPathContextObject();
        } else {
            dynamicContext = expression.createDynamicContext(xpathContext, contextItem, contextPosition);
        }

        // Pass function context to controller
        xpathContext.getController().setUserData("", this.getClass().getName(), functionContext);

        // Set variable values if any
        if (variableToValueMap != null) {
            for (Map.Entry<String, XPathVariable> entry: variables.entrySet()) {
                final String name = entry.getKey();
                final XPathVariable variable = entry.getValue();

                final ValueRepresentation object = variableToValueMap.get(name);
                if (object != null) {
                    // Convert Java object to Saxon object
                    final ValueRepresentation valueRepresentation = XFormsUtils.convertJavaObjectToSaxonObject(object);
                    xpathContext.setLocalVariable(variable.getLocalSlotNumber(), valueRepresentation);
                }
            }
        }

        return dynamicContext;
    }

    /**
     * Set context node-set and initial position.
     *
     * @param contextItems          List of Item
     * @param contextPosition       1-based current position
     */
    public void setContextItems(List<Item> contextItems, int contextPosition) {

        if (contextPosition > 0 && contextPosition <= contextItems.size())
            setContextItem(contextItems.get(contextPosition - 1), contextPosition);
        else
            setContextItem(null, 0);
    }

    public void setContextItem(Item contextItem, int contextPosition) {
        this.contextItem = contextItem;
        this.contextPosition = contextPosition;
    }

    public void setVariables(Map<String, ValueRepresentation> variableToValueMap) {
        // NOTE: We used to attempt to detect whether the expression required variables or not and throw an exception,
        // but we can't really detect this because there may be variables in scope even if the expression does not use
        // them. Conversely, if there are undeclared variables, we let the XPath engine complain about that.
        this.variableToValueMap = variableToValueMap;
    }

    public void destroy() {
        expression = null;
        pool = null;
        variables = null;

        variableToValueMap = null;
        contextItem = null;
    }
}