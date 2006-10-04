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
import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XPathCacheStandaloneContext;
import org.orbeon.saxon.expr.ExpressionTool;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ComputedExpression;
import org.orbeon.saxon.expr.Container;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.FunctionLibraryList;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.sxpath.XPathExpression;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.instruct.Executable;
import org.orbeon.saxon.instruct.LocationMap;
import org.orbeon.saxon.event.LocationProvider;

import java.util.*;

/**
 * Use the object cache to cache XPath expressions, which are costly to parse.
 *
 * It is mandatory to call returnToPool() on the returned PooledXPathExpression after use. It is
 * good to do this within a finally() block enclosing the use of the expression.
 */
public class XPathCache {

    private static final String XPATH_CACHE_NAME = "xpath";

    private static final Logger logger = LoggerFactory.createLogger(XPathCache.class);

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
                                                           String xpathExpressionString,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI) {
        try {
            // Find pool from cache
            final Long validity = new Long(0);
            final Cache cache = ObjectCache.instance(XPATH_CACHE_NAME);
            String cacheKeyString = xpathExpressionString;
            {
                if (functionLibrary != null)// This is ok
                    cacheKeyString = cacheKeyString + functionLibrary.hashCode();
            }

            // Get or create pool
            final InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString);
            ObjectPool pool = (ObjectPool) cache.findValid(pipelineContext, cacheKey, validity);
            if (pool == null) {
                pool = createXPathPool(xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
                cache.add(pipelineContext, cacheKey, validity, pool);
            }

            // Get object from pool
            final Object o = pool.borrowObject();
            final PooledXPathExpression expr = (PooledXPathExpression) o;

            // Set context node and position
            expr.setContextNodeSet(contextNodeSet, contextPosition);

            // Set variables
            expr.setVariables(variableToValueMap);

            return expr;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static ObjectPool createXPathPool(String xpathExpression,
                                              Map prefixToURIMap,
                                              Map variableToValueMap,
                                              FunctionLibrary functionLibrary,
                                              String baseURI) {
        try {
            final SoftReferenceObjectPool pool = new SoftReferenceObjectPool();
            pool.setFactory(new CachedPoolableObjetFactory(pool, xpathExpression,
                    prefixToURIMap, variableToValueMap, functionLibrary, baseURI));

            return pool;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class CachedPoolableObjetFactory implements PoolableObjectFactory {
        private final String xpathExpression;
        private final Map prefixToURIMap;
        private final Map variableToValueMap;
        private final FunctionLibrary functionLibrary;
        private final ObjectPool pool;
        private final String baseURI;

        public CachedPoolableObjetFactory(ObjectPool pool,
                                          String xpathExpression,
                                          Map prefixToURIMap,
                                          Map variableToValueMap,
                                          FunctionLibrary functionLibrary,
                                          String baseURI) {
            this.pool = pool;
            this.xpathExpression = xpathExpression;
            this.prefixToURIMap = prefixToURIMap;
            this.variableToValueMap = variableToValueMap;
            this.functionLibrary = functionLibrary;
            this.baseURI = baseURI;
        }

        public void activateObject(Object o) throws Exception {
        }

        public void destroyObject(Object o) throws Exception {
            if (o instanceof PooledXPathExpression) {
                PooledXPathExpression xp = (PooledXPathExpression) o;
                xp.destroy();
            } else
                throw new OXFException(o.toString() + " is not a PooledXPathExpression");
        }

        /**
         * Create and compile an XPath expression object.
         */
        public Object makeObject() throws Exception {
            if (logger.isDebugEnabled())
                logger.debug("makeObject(" + xpathExpression + ")");

            // Create context
            final IndependentContext independentContext = new XPathCacheStandaloneContext();

            // Set the base URI if specified
            if (baseURI != null)
                independentContext.setBaseURI(baseURI);

            // Create Saxon XPath Evaluator
            final XPathEvaluator evaluator = new XPathEvaluator(independentContext.getConfiguration());
            evaluator.setStaticContext(independentContext);

            // Declare namespaces
            if (prefixToURIMap != null) {
                for (Iterator i = prefixToURIMap.keySet().iterator(); i.hasNext();) {
                    String prefix = (String) i.next();
                    independentContext.declareNamespace(prefix, (String) prefixToURIMap.get(prefix));
                }
            }

            // Declare variables
            final Map variables = new HashMap();
            if (variableToValueMap != null) {
                for (Iterator i = variableToValueMap.keySet().iterator(); i.hasNext();) {
                    final String name = (String) i.next();
                    final Variable var = independentContext.declareVariable(name);
                    var.setUseStack(true);// "Indicate that values of variables are to be found on the stack, not in the Variable object itself"
                    variables.put(name, var);
                }
            }

            // Add function library
            if (functionLibrary != null) {
                // This is ok
                ((FunctionLibraryList) independentContext.getFunctionLibrary()).libraryList.add(0, functionLibrary);
            }

            // Create and compile the expression
            try {
                final XPathExpression exp = evaluator.createExpression(xpathExpression);
                ExpressionTool.allocateSlots(exp.getInternalExpression(), independentContext.getStackFrameMap().getNumberOfVariables(), independentContext.getStackFrameMap());

                {
                    // Provide an Executable with the only purpose of allowing the evaluate() function find the right
                    // FunctionLibrary
                    final Expression expression = exp.getInternalExpression();
                    if (expression instanceof ComputedExpression) {
                        final ComputedExpression computedExpression = (ComputedExpression) expression;
                        computedExpression.setParentExpression(new Container() {

                            public Executable getExecutable() {
                                return new Executable() {
                                    {
                                        setFunctionLibrary(functionLibrary);
                                        setLocationMap(new LocationMap());
                                    }
                                };
                            }

                            public LocationProvider getLocationProvider() {
                                return computedExpression.getLocationProvider();
                            }

                            public int getHostLanguage() {
                                return computedExpression.getHostLanguage();
                            }

                            public boolean replaceSubExpression(Expression expression, Expression expression1) {
                                return computedExpression.replaceSubExpression(expression, expression1);
                            }

                            public int getColumnNumber() {
                                return computedExpression.getColumnNumber();
                            }

                            public int getLineNumber() {
                                return computedExpression.getLineNumber();
                            }

                            public String getPublicId() {
                                return computedExpression.getPublicId();
                            }

                            public String getSystemId() {
                                return computedExpression.getSystemId();
                            }
                        });
                    }
                }

                return new PooledXPathExpression(exp, pool, independentContext, variables);
            } catch (Throwable t) {
                System.out.println(xpathExpression);
                throw new OXFException(t);
            }
        }

        public void passivateObject(Object o) throws Exception {
        }

        public boolean validateObject(Object o) {
            return true;
        }
    }
}
