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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XPathCacheStandaloneContext;
import org.orbeon.saxon.expr.ExpressionTool;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ComputedExpression;
import org.orbeon.saxon.expr.Container;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.FunctionLibraryList;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.sxpath.XPathExpression;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.instruct.Executable;
import org.orbeon.saxon.instruct.LocationMap;
import org.orbeon.saxon.event.LocationProvider;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.style.AttributeValueTemplate;

import java.util.*;

/**
 * Use the object cache to cache XPath expressions, which are costly to parse.
 *
 * It is mandatory to call returnToPool() on the returned PooledXPathExpression after use. It is
 * good to do this within a finally() block enclosing the use of the expression.
 */
public class XPathCache {

    private static final String XPATH_CACHE_NAME = "cache.xpath";
    private static final int XPATH_CACHE_DEFAULT_SIZE = 200;

    private static final Logger logger = LoggerFactory.createLogger(XPathCache.class);

    /**
     * Evaluate an XPath expression on the document.
     */
    public static List evaluate(PipelineContext pipelineContext, NodeInfo contextNode, String xpathExpression,
                         Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return evaluate(pipelineContext, Collections.singletonList(contextNode), 1, xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static List evaluate(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpathExpression,
                         Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, contextPosition,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false);
        try {
            return expr.evaluateKeepNodeInfo();
        } catch (XPathException e) {
            throw new ValidationException("Exception evaluating XPath expression: " + xpathExpression, e, null);
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(PipelineContext pipelineContext, NodeInfo contextNode, String xpathExpression,
                                 Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return evaluateSingle(pipelineContext, Collections.singletonList(contextNode), 1, xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpathExpression,
                                 Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {

        final PooledXPathExpression expr = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, contextPosition,
                xpathExpression, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false);
        try {
            return expr.evaluateSingleKeepNodeInfo();
        } catch (XPathException e) {
            throw new ValidationException("Exception evaluating XPath expression: " + xpathExpression, e, null);
        } finally {
            if (expr != null)
                expr.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(PipelineContext pipelineContext, NodeInfo contextNode, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return evaluateAsAvt(pipelineContext, Collections.singletonList(contextNode), 1, xpath, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, contextPosition, xpath,
            prefixToURIMap, variableToValueMap, functionLibrary, baseURI, true, false);
        try {
            return xpathExpression.evaluateSingleKeepNodeInfo().toString();
        } catch (XPathException e) {
            throw new ValidationException("Exception evaluating XPath expression: " + xpathExpression, e, null);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document and return its string value.
     */
    public static String evaluateAsString(PipelineContext pipelineContext, NodeInfo contextNode, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        return evaluateAsString(pipelineContext, Collections.singletonList(contextNode), 1, xpath, prefixToURIMap, variableToValueMap, functionLibrary, baseURI);
    }

    /**
     * Evaluate an XPath expression on the document and return its string value.
     */
    public static String evaluateAsString(PipelineContext pipelineContext, List contextNodeSet, int contextPosition, String xpath, Map prefixToURIMap, Map variableToValueMap, FunctionLibrary functionLibrary, String baseURI) {
        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(pipelineContext, contextNodeSet, contextPosition, "string(subsequence(" + xpath + ", 1, 1))",
            prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false);
        try {
            return xpathExpression.evaluateSingleKeepNodeInfo().toString();
        } catch (XPathException e) {
            throw new ValidationException("Exception evaluating XPath expression: " + xpathExpression, e, null);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
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
                                                           String xpathExpressionString,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI) {
        final List contextNodeSet = Collections.singletonList(contextNode);
        return getXPathExpression(pipelineContext, contextNodeSet, 1, xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false);
     }

    private static PooledXPathExpression getXPathExpression(PipelineContext pipelineContext,
                                                           List contextNodeSet, int contextPosition,
                                                           String xpathExpressionString,
                                                           Map prefixToURIMap,
                                                           Map variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI,
                                                           boolean isAvt,
                                                           boolean testNoCache) {

        try {
            // Find pool from cache
            final Long validity = new Long(0);
            final Cache cache = ObjectCache.instance(XPATH_CACHE_NAME, XPATH_CACHE_DEFAULT_SIZE);
            final FastStringBuffer cacheKeyString = new FastStringBuffer(xpathExpressionString);
            {
                if (functionLibrary != null) {// This is ok
                    cacheKeyString.append('|');
                    cacheKeyString.append(Integer.toString(functionLibrary.hashCode()));
                }
            }
            {
                // NOTE: It is not clear whether we actually need to cache the expression with a key that depends on
                // the namespace context. The question is whether XPath "compilation" depends on that context. But it is
                // safe to do so functionally, while not always optimal in memory as you may cache several times the
                // same expression, except for the namespace context. If we do not cache per the namespace context, then
                // we need to restore the namespace context before using the expression.

                // TODO: PERF: It turns out that this takes a lot of time.
                if (prefixToURIMap != null) {
                    final Map sortedMap = new TreeMap(prefixToURIMap);// this should make sure we always get the keys in the same order
                    for (Iterator i = sortedMap.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        cacheKeyString.append('|');
                        cacheKeyString.append((String) currentEntry.getKey());
                        cacheKeyString.append('=');
                        cacheKeyString.append((String) currentEntry.getValue());
                    }
                }
            }
            {
                // Add this to the key as evaluating "name" as XPath or as AVT is very different!
                cacheKeyString.append('|');
                cacheKeyString.append(Boolean.toString(isAvt));
            }

            final PooledXPathExpression expr;
            if (testNoCache) {
                // For testing only: don't get expression from cache
                final Object o = new XFormsCachePoolableObjetFactory(null, xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, isAvt).makeObject();
                expr = (PooledXPathExpression) o;
            } else {
                // Get or create pool
                final InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString.toString());
                ObjectPool pool = (ObjectPool) cache.findValid(pipelineContext, cacheKey, validity);
                if (pool == null) {
                    pool = createXPathPool(xpathExpressionString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, isAvt);
                    cache.add(pipelineContext, cacheKey, validity, pool);
                }

                // Get object from pool
                final Object o = pool.borrowObject();
                expr = (PooledXPathExpression) o;
            }

            // Set context node and position
            expr.setContextNodeSet(contextNodeSet, contextPosition);

            // Set variables
            expr.setVariables(variableToValueMap);

            return expr;
        } catch (Exception e) {
            throw new ValidationException("Exception evaluating XPath expression: " + xpathExpressionString, e, null);
        }
    }

    private static ObjectPool createXPathPool(String xpathExpression,
                                              Map prefixToURIMap,
                                              Map variableToValueMap,
                                              FunctionLibrary functionLibrary,
                                              String baseURI,
                                              boolean isAvt) {
        try {
            final SoftReferenceObjectPool pool = new SoftReferenceObjectPool();
            pool.setFactory(new XFormsCachePoolableObjetFactory(pool, xpathExpression,
                    prefixToURIMap, variableToValueMap, functionLibrary, baseURI, isAvt));

            return pool;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class  XFormsCachePoolableObjetFactory implements PoolableObjectFactory {
        private final String xpathExpression;
        private final Map prefixToURIMap;
        private final Map variableToValueMap;// TODO: should not store values in cache
        private final FunctionLibrary functionLibrary;// TODO: should not store in cache, at least not store function library with references to XForms data structures
        private final ObjectPool pool;
        private final String baseURI;
        private final boolean isAvt;

        public XFormsCachePoolableObjetFactory(ObjectPool pool,
                                          String xpathExpression,
                                          Map prefixToURIMap,
                                          Map variableToValueMap,
                                          FunctionLibrary functionLibrary,
                                          String baseURI,
                                          boolean isAvt) {
            this.pool = pool;
            this.xpathExpression = xpathExpression;
            this.prefixToURIMap = prefixToURIMap;
            this.variableToValueMap = variableToValueMap;
            this.functionLibrary = functionLibrary;
            this.baseURI = baseURI;
            this.isAvt = isAvt;
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

            // Declare namespaces
            if (prefixToURIMap != null) {
                for (Iterator i = prefixToURIMap.keySet().iterator(); i.hasNext();) {
                    String prefix = (String) i.next();
                    independentContext.declareNamespace(prefix, (String) prefixToURIMap.get(prefix));
                }
            }

            // Declare variables (we don't use the values here, just the names)
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
                final Expression expression;
                if (isAvt) {
                    final Expression tempExpression = AttributeValueTemplate.make(xpathExpression, -1, independentContext);
                    // Running typeCheck() is mandatory otherwise things break! This is also done when using evaluator.createExpression()
                    expression = tempExpression.typeCheck(independentContext, Type.ITEM_TYPE);
                } else {
                    // Create Saxon XPath Evaluator
                    final XPathEvaluator evaluator = new XPathEvaluator(independentContext.getConfiguration());
                    evaluator.setStaticContext(independentContext);
                    final XPathExpression exp = evaluator.createExpression(xpathExpression);
                    expression = exp.getInternalExpression();
                    ExpressionTool.allocateSlots(expression, independentContext.getStackFrameMap().getNumberOfVariables(), independentContext.getStackFrameMap());
                }

                {
                    // Provide an Executable with the only purpose of allowing the evaluate() function find the right
                    // FunctionLibrary
                    if (expression instanceof ComputedExpression) {
                        final ComputedExpression computedExpression = (ComputedExpression) expression;
                        computedExpression.setParentExpression(new Container() {

                            public Executable getExecutable() {
                                return new Executable() {
                                    {
                                        setFunctionLibrary(independentContext.getFunctionLibrary());
                                        setLocationMap(new LocationMap());
                                        setConfiguration(independentContext.getConfiguration());
                                    }
                                };
                            }

                            public LocationProvider getLocationProvider() {
                                return computedExpression.getLocationProvider();
                            }

                            public int getHostLanguage() {
                                return Configuration.JAVA_APPLICATION;
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

                return new PooledXPathExpression(expression, pool, independentContext, variables);
            } catch (Throwable t) {
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
