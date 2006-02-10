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

import org.dom4j.Document;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Encapsulate a Document to faciliate XPath evaluation.
 */
public class DocumentXPathEvaluator {

    private Document document;
    private DocumentWrapper documentWrapper;

    public DocumentXPathEvaluator(Document document) {
        this.document = document;
        this.documentWrapper = new DocumentWrapper(document, null);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public List evaluate(PipelineContext pipelineContext, Node contextNode, String xpathExpression,
                         Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final List contextNodeSet = Collections.singletonList(documentWrapper.wrap(contextNode));
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, 1,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return expr.evaluate();
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
    public Object evaluateSingle(PipelineContext pipelineContext, Node contextNode, String xpathExpression,
                                 Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final List contextNodeSet = Collections.singletonList(documentWrapper.wrap(contextNode));
        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, 1,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return expr.evaluateSingle();
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
    public String evaluateAsString(PipelineContext pipelineContext, Node contextNode, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final List contextNodeSet = Collections.singletonList(documentWrapper.wrap(contextNode));
        PooledXPathExpression xpathExpression =
                XPathCache.getXPathExpression(pipelineContext, contextNodeSet, 1, "string(" + xpath + ")",
                        prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return (String) xpathExpression.evaluateSingle();
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public List evaluate(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpathExpression,
                         Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, wrapNodeSet(contextNodeSet), contextPosition,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {

            return expr.evaluate();
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
    public Object evaluateSingle(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpathExpression,
                                 Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, wrapNodeSet(contextNodeSet), contextPosition,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return expr.evaluateSingle();
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
    public String evaluateAsString(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        PooledXPathExpression xpathExpression =
                XPathCache.getXPathExpression(pipelineContext, wrapNodeSet(contextNodeSet), contextPosition, "string(" + xpath + ")",
                        prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
        try {
            return (String) xpathExpression.evaluateSingle();
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    private List wrapNodeSet(List nodeSet) {
        final List result = new ArrayList(nodeSet.size());
        for (Iterator i = nodeSet.iterator(); i.hasNext();) {
            final Node currentNode = (Node) i.next();
            result.add(documentWrapper.wrap(currentNode));
        }
        return result;
    }

    /**
     * Return a dom4j node wrapped into a NodeInfo. The node must belong to this particular
     * document.
     */
    public NodeInfo wrapNode(Node node) {
        return documentWrapper.wrap(node);
    }
}
