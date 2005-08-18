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
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.xpath.*;
import org.orbeon.saxon.xpath.Variable;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.Value;
import org.orbeon.saxon.instruct.SlotManager;

import java.util.List;
import java.util.Map;

public class PooledXPathExpression {
    private StandaloneContext context;
    private XPathExpression expression;
    private ObjectPool pool;

    // Dynamic context
    private Map variables;
    private List contextNodeSet;
    private int contextPosition;

    public PooledXPathExpression(XPathExpression exp, ObjectPool pool, StandaloneContext context, final java.util.Map vars) {
        this.expression = exp;
        this.pool = pool;
        this.context = context;
        variables = vars;
    }

    public void returnToPool() {
        try {
            pool.returnObject(this);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

//    public List evaluate() throws XPathException {
//        return expression.evaluate();
//    }

    /**
     * Evaluate the XPath expression by providing a context node-set and initial position.
     *
     * @return result of the evaluation
     * @throws XPathException
     */
    public List evaluate() throws XPathException {

//        if (!(expression instanceof org.orbeon.saxon.xpath.XPathExpressionImpl))
//            throw new OXFException("XPath expression object not an instance of XPathExpressionImpl");

        final NodeInfo contextNode = (NodeInfo) contextNodeSet.get(contextPosition - 1);
        if (false) {// TODO
//            // Use low-level Expression object and implement context node-set and context position
//            final Expression expression;
//            {
//                final XPathExpressionImpl xPathExpressionImpl = (XPathExpressionImpl) this.expression;
//                expression = xPathExpressionImpl.getInternalExpression();
//            }
//
//            final XPathContextMajor xpathContext = new XPathContextMajor(contextNode, this.context.getConfiguration());
//            final SlotManager map = this.context.getConfiguration().makeSlotManager(); // this is already set on XPathExpressionImpl but we can't get to it
//            xpathContext.setCurrentIterator(new ListSequenceIterator(contextNodeSet, contextPosition));
//            xpathContext.openStackFrame(map);
//
//            final SequenceIterator iter = expression.iterate(xpathContext);
//
//            // Format
//            final SequenceExtent extent = new SequenceExtent(iter);
//            return (List) extent.convertToJava(Object.class, xpathContext);
            return null;
        } else {
            // Use high-level API
            expression.setContextNode(contextNode);
            return expression.evaluate();
        }
    }

    public Object evaluateSingle() throws XPathException {
//        if (!(expression instanceof org.orbeon.saxon.xpath.XPathExpressionImpl))
//            throw new OXFException("XPath expression object not an instance of XPathExpressionImpl");

        final NodeInfo contextNode = (NodeInfo) contextNodeSet.get(contextPosition - 1);
        if (false) {// TODO
//            // Use low-level Expression object and implement context node-set and context position
//            final Expression expression;
//            {
//                final XPathExpressionImpl xPathExpressionImpl = (XPathExpressionImpl) this.expression;
//                expression = xPathExpressionImpl.getInternalExpression();
//            }
//
//            final XPathContextMajor xpathContext = new XPathContextMajor(contextNode, this.context.getConfiguration());
//            final SlotManager map = this.context.getConfiguration().makeSlotManager(); // this is already set on XPathExpressionImpl but we can't get to it
//            xpathContext.setCurrentIterator(new ListSequenceIterator(contextNodeSet, contextPosition));
//            xpathContext.openStackFrame(map);
//
//            final SequenceIterator iter = expression.iterate(xpathContext);
//
//            Item item = iter.next();
//            if (item == null) {
//                return null;
//            } else {
//                return Value.convert(item);
//            }
            return null;
        } else {
            // Use high-level API
            return expression.evaluateSingle();
        }
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
            try {
                return (SequenceIterator) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new OXFException(e);
            }
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

    // TODO
    public void setContextNode(NodeInfo contextNode) {
        expression.setContextNode(contextNode);
    }

    /**
     * Set context variable.
     *
     * @param name   variable name
     * @param value  variable value
     */
    public void setVariable(String name, Object value) {
        try {
            Variable v = (Variable) variables.get(name);
            if (v != null)
                v.setValue(value);
        } catch (XPathException e) {
            throw new OXFException(e);
        }
    }

    public void destroy() {
        context = null;
        expression = null;
        pool = null;
        variables = null;
        contextNodeSet = null;
    }
}