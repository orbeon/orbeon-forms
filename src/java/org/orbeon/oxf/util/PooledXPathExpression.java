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
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.Variable;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.xpath.XPathExpression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PooledXPathExpression {
    private XPathExpression expression;
    private ObjectPool pool;
    private StandaloneContext context;
    private Map variables = new HashMap();

    public PooledXPathExpression(XPathExpression exp, ObjectPool pool, StandaloneContext context, Map variables) {
        this.expression = exp;
        this.pool = pool;
        this.context = context;
        this.variables = variables;
    }

    public void returnToPool() {
        try {
            pool.returnObject(this);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public List evaluate() throws XPathException {
        return expression.evaluate();
    }

    public Object evaluateSingle() throws XPathException {
        return expression.evaluateSingle();
    }


    public void setContextNode(NodeInfo contextNode) {
        expression.setContextNode(contextNode);
    }

    public StandaloneContext getContext() {
        return context;
    }

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
        this.context = null;
        this.expression = null;
        this.pool = null;
    }
}