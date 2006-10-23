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

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.NodeInfo;

import java.util.List;
import java.util.Map;

/**
 * Facilitate XPath evaluation.
 *
 * NOTE: This is here for historical reasons. It now just delegates to XPathCache. Should everybody just use
 * XPathCache directly?
 */
public class DocumentXPathEvaluator {

    public DocumentXPathEvaluator() {
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public List evaluate(PipelineContext pipelineContext, NodeInfo contextNode, String xpathExpression,
                         Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return XPathCache.evaluate(pipelineContext, contextNode, xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public Object evaluateSingle(PipelineContext pipelineContext, NodeInfo contextNode, String xpathExpression,
                                 Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return XPathCache.evaluateSingle(pipelineContext, contextNode, xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document and return its string value.
     */
    public String evaluateAsString(PipelineContext pipelineContext, NodeInfo contextNode, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return XPathCache.evaluateAsString(pipelineContext, contextNode, xpath, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document and return its string value.
     */
    public String evaluateAsString(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return XPathCache.evaluateAsString(pipelineContext, contextNodeSet, contextPosition, xpath, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }
}
