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
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.SoftReferenceObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.XPath;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XPathCacheStandaloneContext;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.FunctionLibraryList;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.xpath.StandaloneContext;
import org.orbeon.saxon.xpath.Variable;
import org.orbeon.saxon.xpath.XPathEvaluator;
import org.orbeon.saxon.xpath.XPathExpression;

import java.util.*;

/**
 * Use the object cache to cache XPath expressions. Those are costly to parse.
 */
public class XPathCache {
    private static final Logger logger = LoggerFactory.createLogger(XPathCache.class);
    private static final boolean doCache = true;

    public static XPath createCacheXPath(PipelineContext context, String xpathExpression) {
        if (doCache) {
            Long validity = new Long(0);
            Cache cache = ObjectCache.instance();
            InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression", xpathExpression);
            XPath xpath = (XPath) cache.findValid(context, cacheKey, validity);
            if (xpath == null) {
                xpath = Dom4jUtils.createXPath(xpathExpression);
                cache.add(context, cacheKey, validity, xpath);
            }
            return xpath;
        } else {
            return Dom4jUtils.createXPath(xpathExpression);
        }
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           NodeInfo contextNode,
                                                           String xpathExpression) {
        return getXPathExpression(pipelineContext, contextNode, xpathExpression, null, null, null, null);
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           NodeInfo contextNode,
                                                           String xpathExpression,
                                                           Map prefixToURIMap) {
        return getXPathExpression(pipelineContext, contextNode, xpathExpression, prefixToURIMap, null, null, null);
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           NodeInfo contextNode,
                                                           String xpathExpression,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap) {
        return getXPathExpression(pipelineContext, contextNode, xpathExpression, prefixToURIMap, variableToValueMap, null, null);
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           NodeInfo contextNode,
                                                           String xpathExpression,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary) {
        return getXPathExpression(pipelineContext, contextNode, xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, null);
    }

     public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           NodeInfo contextNode,
                                                           String xpathExpressionString,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI) {
     final List contextNodeSet = Collections.singletonList(contextNode);
        return getXPathExpression(pipelineContext, contextNodeSet, 1, xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
     }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           List contextNodeSet, int contextPosition,
                                                           String xpathExpression) {
        return getXPathExpression(pipelineContext, contextNodeSet, contextPosition, xpathExpression, null, null, null, null);
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           List contextNodeSet, int contextPosition,
                                                           String xpathExpression,
                                                           Map prefixToURIMap) {
        return getXPathExpression(pipelineContext, contextNodeSet, contextPosition, xpathExpression, prefixToURIMap, null, null, null);
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           List contextNodeSet, int contextPosition,
                                                           String xpathExpression,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap) {
        return getXPathExpression(pipelineContext, contextNodeSet, contextPosition, xpathExpression, prefixToURIMap, variableToValueMap, null, null);
    }

    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           List contextNodeSet, int contextPosition,
                                                           String xpathExpression,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary) {
        return getXPathExpression(pipelineContext, contextNodeSet, contextPosition, xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, null);
    }


    public static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           List contextNodeSet, int contextPosition,
                                                           String xpathExpressionString,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI) {
        try {
            // Find pool from cache
            final Long validity = new Long(0);
            final Cache cache = ObjectCache.instance();
            String cacheKeyString = xpathExpressionString;
            if (variableToValueMap != null) {
                for (Iterator i = variableToValueMap.keySet().iterator(); i.hasNext();) {
                    cacheKeyString = cacheKeyString + (String) i.next();
                }
            }
            if (functionLibrary != null)
                cacheKeyString = cacheKeyString + functionLibrary.hashCode();

            final InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString);
            ObjectPool pool = (ObjectPool) cache.findValid(pipelineContext, cacheKey, validity);
            if (pool == null) {
                final NodeInfo currentNode = (NodeInfo) contextNodeSet.get(contextPosition - 1);
                // TODO
//                final Configuration config = currentNode.getDocumentRoot().getConfiguration();
//                pool = createXPathPool(config, xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
                    pool = createXPathPool(currentNode, xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
                cache.add(pipelineContext, cacheKey, validity, pool);
            }

            // Get object from pool
            Object o = pool.borrowObject();

            // Set variables
            final PooledXPathExpression expr = (PooledXPathExpression) o;
            if (variableToValueMap != null) {
                for (Iterator i = variableToValueMap.keySet().iterator(); i.hasNext();) {
                    String name = (String) i.next();
                    expr.setVariable(name, variableToValueMap.get(name));
                }
            }

            // Set context
            // TODO
            expr.setContextNodeSet(contextNodeSet, contextPosition);
            expr.setContextNode((NodeInfo) contextNodeSet.get(contextPosition - 1));

            return expr;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    // TODO
//    private static ObjectPool createXPathPool(Configuration config,
//                                              String xpathExpression,
//                                              Map prefixToURIMap,
//                                              Map variableToValueMap,
//                                              FunctionLibrary functionLibrary,
//                                              String baseURI) {
//        try {
//            SoftReferenceObjectPool pool = new SoftReferenceObjectPool();
//
//            pool.setFactory(new CachedPoolableObjetFactory(pool, config, xpathExpression,
//                    prefixToURIMap, variableToValueMap, functionLibrary, baseURI));
//
//            return pool;
//        } catch (Exception e) {
//            throw new OXFException(e);
//        }
//    }

    private static ObjectPool createXPathPool(NodeInfo currentNode,
                                              String xpathExpression,
                                              Map prefixToURIMap,
                                              Map variableToValueMap,
                                              FunctionLibrary functionLibrary,
                                              String baseURI) {
        try {
            SoftReferenceObjectPool pool = new SoftReferenceObjectPool();

            pool.setFactory(new CachedPoolableObjetFactory(pool, currentNode, xpathExpression,
                    prefixToURIMap, variableToValueMap, functionLibrary, baseURI));

            return pool;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class CachedPoolableObjetFactory implements PoolableObjectFactory {
        private final NodeInfo currentNode;
//        private final Configuration config;
        private final String xpathExpression;
        private final Map prefixToURIMap;
        private final Map variableToValueMap;
        private final FunctionLibrary functionLibrary;
        private final ObjectPool pool;
        private final String baseURI;

        public CachedPoolableObjetFactory(ObjectPool pool,
                                          NodeInfo currentNode,
                                          String xpathExpression,
                                          Map prefixToURIMap,
                                          Map variableToValueMap,
                                          FunctionLibrary functionLibrary,
                                          String baseURI) {
            this.pool = pool;
            this.currentNode = currentNode;
            this.xpathExpression = xpathExpression;
            this.prefixToURIMap = prefixToURIMap;
            this.variableToValueMap = variableToValueMap;
            this.functionLibrary = functionLibrary;
            this.baseURI = baseURI;
        }

        // TODO
//        public CachedPoolableObjetFactory(ObjectPool pool,
//                                          Configuration config,
//                                          String xpathExpression,
//                                          Map prefixToURIMap,
//                                          Map variableToValueMap,
//                                          FunctionLibrary functionLibrary,
//                                          String baseURI) {
//            this.pool = pool;
//            this.config = config;
//            this.xpathExpression = xpathExpression;
//            this.prefixToURIMap = prefixToURIMap;
//            this.variableToValueMap = variableToValueMap;
//            this.functionLibrary = functionLibrary;
//            this.baseURI = baseURI;
//        }

        public void activateObject(Object o) throws Exception {
        }

        public void destroyObject(Object o) throws Exception {
            if (o instanceof PooledXPathExpression) {
                PooledXPathExpression xp = (PooledXPathExpression) o;
                xp.destroy();
            } else
                throw new OXFException(o.toString() + " is not a PooledXPathExpression");
        }

        public Object makeObject() throws Exception {
            if (logger.isDebugEnabled())
                logger.debug("makeObject(" + xpathExpression + ")");

            // Create Saxon XPath Evaluator
//            XPathEvaluator evaluator = new XPathEvaluator(config);
            XPathEvaluator evaluator = new XPathEvaluator(currentNode);
            StandaloneContext origContext = (StandaloneContext) evaluator.getStaticContext();

            // HACK: Workaround Saxon bug: need to allocate enough Slots to accomodate all variables
            int numberVariables = 1;  // at least 1 if LetExpression is used
            int index = 0;
            while (index != -1) {
                index = xpathExpression.indexOf("$", index + 1);
                numberVariables++;
            }
            // Set the base URI if specified
            if (baseURI != null)
                origContext.setBaseURI(baseURI);

            StandaloneContext standaloneContext = new XPathCacheStandaloneContext(origContext, numberVariables);
            evaluator.setStaticContext(standaloneContext);

            // Declare namespaces
            if (prefixToURIMap != null) {
                for (Iterator i = prefixToURIMap.keySet().iterator(); i.hasNext();) {
                    String prefix = (String) i.next();
                    standaloneContext.declareNamespace(prefix, (String) prefixToURIMap.get(prefix));
                }
            }

            // Declare variables
            Map variables = new HashMap();
            if (variableToValueMap != null) {
                for (Iterator i = variableToValueMap.keySet().iterator(); i.hasNext();) {
                    String name = (String) i.next();
                    Object value = variableToValueMap.get(name);
                    Variable var = standaloneContext.declareVariable(name, value);
                    variables.put(name, var);
                }
            }

            // Add function library
            if (functionLibrary != null) {
                ((FunctionLibraryList) standaloneContext.getFunctionLibrary()).libraryList.add(0, functionLibrary);
            }

            XPathExpression exp = evaluator.createExpression(xpathExpression);
            return new PooledXPathExpression(exp, pool, standaloneContext, variables);
        }

        public void passivateObject(Object o) throws Exception {
        }

        public boolean validateObject(Object o) {
            return true;
        }
    }


}
