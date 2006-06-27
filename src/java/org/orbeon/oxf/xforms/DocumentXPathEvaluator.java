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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.trans.XPathException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Facilitate XPath evaluation.
 */
public class DocumentXPathEvaluator {

    public DocumentXPathEvaluator() {
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public List evaluate(PipelineContext pipelineContext, NodeInfo contextNode, String xpathExpression,
                         Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final List contextNodeSet = Collections.singletonList(contextNode);
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, 1,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return expr.evaluateKeepNodeInfo();
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public Object evaluateSingle(PipelineContext pipelineContext, NodeInfo contextNode, String xpathExpression,
                                 Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final List contextNodeSet = Collections.singletonList(contextNode);
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, 1,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return expr.evaluateSingleKeepNodeInfo();
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document and return its string value.
     */
    public String evaluateAsString(PipelineContext pipelineContext, NodeInfo contextNode, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final List contextNodeSet = Collections.singletonList(contextNode);
        PooledXPathExpression xpathExpression =
                XPathCache.getXPathExpression(pipelineContext, contextNodeSet, 1, "string(" + xpath + ")",
                        prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return xpathExpression.evaluateSingleKeepNodeInfo().toString();
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document and return its string value.
     */
    public String evaluateAsString(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        PooledXPathExpression xpathExpression =
                XPathCache.getXPathExpression(pipelineContext, contextNodeSet, contextPosition, "string(" + xpath + ")",
                        prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return xpathExpression.evaluateSingleKeepNodeInfo().toString();
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }
}
