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

import org.dom4j.DocumentHelper;
import org.dom4j.XPath;
import org.dom4j.Node;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.xpath.XPathExpression;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.XPathException;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.FunctionLibraryList;

import java.util.Map;
import java.util.Iterator;

/**
 * Use the object cache to cache XPath expressions. Those are costly to parse.
 */
public class XPathCache {

    public static XPath createCacheXPath(PipelineContext context, String xpathExpression) {
        final boolean doCache = true;
        if (doCache) {
            Long validity = new Long(0);
            Cache cache = ObjectCache.instance();
            InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression", xpathExpression);
            XPath xpath = (XPath) cache.findValid(context, cacheKey, validity);
            if (xpath == null) {
                xpath = DocumentHelper.createXPath(xpathExpression);
                cache.add(context, cacheKey, validity, xpath);
            }
            return xpath;
        } else {
            return DocumentHelper.createXPath(xpathExpression);
        }
    }

    public static XPathExpression createCacheXPath20
            (PipelineContext context, DocumentWrapper documentWrapper, NodeInfo nodeInfo,
             String xpathExpression, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary) {
        try {
            // Create Saxon XPath Evaluator
            final XPathEvaluator xpathEvaluator = new XPathEvaluator(documentWrapper);
            StandaloneContext standaloneContext = (StandaloneContext) xpathEvaluator.getStaticContext();

            // Declare namespaces
            if (prefixToURIMap != null) {
                for (Iterator i = prefixToURIMap.keySet().iterator(); i.hasNext();) {
                    String prefix = (String) i.next();
                    standaloneContext.declareNamespace(prefix, (String) prefixToURIMap.get(prefix));
                }
            }

            // Declare variables
            if (variableToValueMap != null) {
                for (Iterator i = variableToValueMap.keySet().iterator(); i.hasNext();) {
                    String repeatId = (String) i.next();
                    Integer index = (Integer) variableToValueMap.get(repeatId);
                    standaloneContext.declareVariable(repeatId, index);
                }
            }

            // Add function library
            if (functionLibrary != null) {
                ((FunctionLibraryList) standaloneContext.getFunctionLibrary()).libraryList.add(0, functionLibrary);
            }

            XPathExpression exp = xpathEvaluator.createExpression(xpathExpression);
            // Context node
            if (nodeInfo != null) {
                //somehow we need to set it both to the evaluator and the expression
                xpathEvaluator.setContextNode(nodeInfo);
                exp.setContextNode(nodeInfo);
            }

            return exp;
        } catch (XPathException e) {
            throw new OXFException(e);
        }
    }
}
