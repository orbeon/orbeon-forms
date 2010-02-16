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
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.log4j.Logger;
import org.orbeon.oxf.cache.Cache;
import org.orbeon.oxf.cache.InternalCacheKey;
import org.orbeon.oxf.cache.ObjectCache;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.function.Instance;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsInstance;
import org.orbeon.oxf.xml.XPathCacheStaticContext;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.event.LocationProvider;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.functions.Doc;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.FunctionLibraryList;
import org.orbeon.saxon.instruct.Executable;
import org.orbeon.saxon.instruct.LocationMap;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.style.AttributeValueTemplate;
import org.orbeon.saxon.trans.IndependentContext;
import org.orbeon.saxon.trans.Variable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.SequenceExtent;
import org.orbeon.saxon.value.StringValue;

import java.util.*;

/**
 * Use the object cache to cache XPath expressions, which are costly to parse.
 *
 * It is mandatory to call returnToPool() on the returned PooledXPathExpression after use. It is
 * good to do this within a finally() block enclosing the use of the expression.
 */
public class XPathCache {

    public static final String XPATH_CACHE_NAME = "cache.xpath";
    private static final int XPATH_CACHE_DEFAULT_SIZE = 200;

    private static final boolean DEBUG_TEST_KEY_OPTIMIZATION = false;

    private static final Logger logger = LoggerFactory.createLogger(XPathCache.class);

    public static class XPathContext {
        public final Map<String, String> prefixToURIMap;
        public final Map<String, ValueRepresentation> variableToValueMap;
        public final FunctionLibrary functionLibrary;
        public final FunctionContext functionContext;
        public final String baseURI;
        public final LocationData locationData;

        public XPathContext(Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
            this.prefixToURIMap = prefixToURIMap;
            this.variableToValueMap = variableToValueMap;
            this.functionLibrary = functionLibrary;
            this.functionContext = functionContext;
            this.baseURI = baseURI;
            this.locationData = locationData;
        }
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static List evaluate(PropertyContext propertyContext, Item contextItem, String xpathString,
                         Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        return evaluate(propertyContext, Collections.singletonList(contextItem), 1, xpathString, prefixToURIMap, variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static List evaluate(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString,
                         Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition,
                xpathString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
        try {
            return xpathExpression.evaluateKeepNodeInfo(functionContext);
        } catch (Exception e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document and keep Item objects in the result.
     */
    public static List<Item> evaluateKeepItems(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString,
                         Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition,
                xpathString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
        try {
            return xpathExpression.evaluateKeepItems(functionContext);
        } catch (Exception e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate the expression as a variable value usable by Saxon in further XPath expressions.
     */
    public static SequenceExtent evaluateAsExtent(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString,
                         Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition,
                xpathString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
        try {
            return xpathExpression.evaluateAsExtent(functionContext);
        } catch (Exception e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(PropertyContext propertyContext, XPathCache.XPathContext xpathContext, Item contextItem, String xpathString) {
        return evaluateSingle(propertyContext, Collections.singletonList(contextItem), 1, xpathString,
                xpathContext.prefixToURIMap, xpathContext.variableToValueMap, xpathContext.functionLibrary,
                xpathContext.functionContext, xpathContext.baseURI, xpathContext.locationData);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(PropertyContext propertyContext, Item contextItem, String xpathString,
                                 Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        return evaluateSingle(propertyContext, Collections.singletonList(contextItem), 1, xpathString, prefixToURIMap, variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString,
                                 Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition,
                xpathString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
        try {
            return xpathExpression.evaluateSingleKeepNodeInfo(functionContext);
        } catch (XPathException e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(PropertyContext propertyContext, XPathCache.XPathContext xpathContext, Item contextItem, String xpathString) {
        return evaluateAsAvt(propertyContext, Collections.singletonList(contextItem), 1, xpathString, xpathContext.prefixToURIMap,
                xpathContext.variableToValueMap, xpathContext.functionLibrary, xpathContext.functionContext, xpathContext.baseURI, xpathContext.locationData);
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(PropertyContext propertyContext, Item contextItem, String xpathString, Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        return evaluateAsAvt(propertyContext, Collections.singletonList(contextItem), 1, xpathString, prefixToURIMap, variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString, Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition, xpathString,
                prefixToURIMap, variableToValueMap, functionLibrary, baseURI, true, false, locationData);
        try {
            final Object result = xpathExpression.evaluateSingleKeepNodeInfo(functionContext);
            return (result != null) ? result.toString() : null;
        } catch (XPathException e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression and return its string value.
     */
    public static String evaluateAsString(PropertyContext propertyContext, Item contextItem, String xpathString,
                                          Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap,
                                          FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        return evaluateAsString(propertyContext, Collections.singletonList(contextItem), 1, xpathString, prefixToURIMap, variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression and return its string value.
     */
    public static String evaluateAsString(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString, Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        final PooledXPathExpression xpathExpression =  XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition, "string(subsequence(" + xpathString + ", 1, 1))",
                prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
        try {
            final Object result = xpathExpression.evaluateSingleKeepNodeInfo(functionContext);
            return (result != null) ? result.toString() : null;
        } catch (XPathException e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    /**
     * Evaluate an XPath expression as a boolean value.
     */
    public static boolean evaluateAsBoolean(PropertyContext propertyContext, List<Item> contextItems, int contextPosition, String xpathString, Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
        final PooledXPathExpression xpathExpression =  XPathCache.getXPathExpression(propertyContext, contextItems, contextPosition, "string(subsequence(" + xpathString + ", 1, 1))",
                prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
        try {
            return xpathExpression.evaluateAsBoolean(functionContext);
        } catch (XPathException e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    public static PooledXPathExpression getXPathExpression(PropertyContext propertyContext,
                                                           Item contextItem,
                                                           String xpathString,
                                                           LocationData locationData) {
        return getXPathExpression(propertyContext, contextItem, xpathString, null, null, null, null, locationData);
    }

    public static PooledXPathExpression getXPathExpression(PropertyContext propertyContext,
                                                           Item contextItem,
                                                           String xpathString,
                                                           Map<String, String> prefixToURIMap,
                                                           LocationData locationData) {
        return getXPathExpression(propertyContext, contextItem, xpathString, prefixToURIMap, null, null, null, locationData);
    }

    public static PooledXPathExpression getXPathExpression(PropertyContext propertyContext,
                                                           Item contextItem,
                                                           String xpathString,
                                                           Map<String, String> prefixToURIMap,
                                                           Map<String, ValueRepresentation> variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI,
                                                           LocationData locationData) {
        final List<Item> contextItems = Collections.singletonList(contextItem);
        return getXPathExpression(propertyContext, contextItems, 1, xpathString, prefixToURIMap, variableToValueMap, functionLibrary, baseURI, false, false, locationData);
    }

    /**
     * Just attempt to compile an XPath expression. An exception is thrown if the expression is not statically correct.
     * Any variable used by the expression is assumed to be in scope. The expression is not added to the cache.
     *
     * @param xpathString       XPath string
     * @param prefixToURIMap    namespaces in scope
     * @param functionLibrary   function library
     * @throws Exception        if the expression is not correct
     */
    public static void checkXPathExpression(String xpathString, Map<String, String> prefixToURIMap, FunctionLibrary functionLibrary) throws Exception {
        new XFormsCachePoolableObjetFactory(null, xpathString, prefixToURIMap, null, functionLibrary, null, false, true, null).makeObject();
    }

    public static PooledXPathExpression getXPathExpression(PropertyContext propertyContext,
                                                           List<Item> contextItems, int contextPosition,
                                                           String xpathString,
                                                           Map<String, String> prefixToURIMap,
                                                           Map<String, ValueRepresentation> variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI,
                                                           boolean isAvt,
                                                           boolean testNoCache,
                                                           LocationData locationData) {

        try {
            // Find pool from cache
            final Long validity = (long) 0;
            final Cache cache = ObjectCache.instance(XPATH_CACHE_NAME, XPATH_CACHE_DEFAULT_SIZE);
            final FastStringBuffer cacheKeyString = new FastStringBuffer(xpathString);
            {
                if (functionLibrary != null) {// This is ok
                    cacheKeyString.append('|');
                    cacheKeyString.append(Integer.toString(functionLibrary.hashCode()));
                }
            }
            {
                // NOTE: Mike Kay confirms on 2007-07-04 that compilation depends on the namespace context, so we need
                // to use it as part of the cache key.

                // TODO: PERF: It turns out that this takes a lot of time. Now that the namespace information is computed statically, we can do better.
                if (DEBUG_TEST_KEY_OPTIMIZATION) {
                    // PERF TEST ONLY
                    cacheKeyString.append("|DUMMYNSVAR|");
                } else {

                    if (prefixToURIMap != null) {
                        final Map<String, String> sortedMap = (prefixToURIMap instanceof TreeMap) ? prefixToURIMap : new TreeMap<String, String>(prefixToURIMap);// this should make sure we always get the keys in the same order
                        for (Map.Entry<String,String> currentEntry: sortedMap.entrySet()) {
                            cacheKeyString.append('|');
                            cacheKeyString.append(currentEntry.getKey());
                            cacheKeyString.append('=');
                            cacheKeyString.append(currentEntry.getValue());
                        }
                    }

                }
            }
            if (DEBUG_TEST_KEY_OPTIMIZATION) {
                // PERF TEST ONLY
                // NOP
            } else {

                if (variableToValueMap != null && variableToValueMap.size() > 0) {
                    // There are some variables in scope. They must be part of the key
                    // TODO: Put this in static state as this can be determined statically once and for all
                    for (final String variableName: variableToValueMap.keySet()) {
                        cacheKeyString.append('|');
                        cacheKeyString.append(variableName);
                    }
                }
            }
            {
                // Add this to the key as evaluating "name" as XPath or as AVT is very different!
                cacheKeyString.append('|');
                cacheKeyString.append(Boolean.toString(isAvt));
            }

            // TODO: Add baseURI to cache key (currently, baseURI is pretty much unused)

            final Set<String> variableNames = (variableToValueMap != null) ? variableToValueMap.keySet() : null;
            final PooledXPathExpression expr;
            if (testNoCache) {
                // For testing only: don't get expression from cache
                final Object o = new XFormsCachePoolableObjetFactory(null, xpathString, prefixToURIMap, variableNames, functionLibrary, baseURI, isAvt, false, locationData).makeObject();
                expr = (PooledXPathExpression) o;
            } else {
                // Get or create pool
                final InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString.toString());
                ObjectPool pool = (ObjectPool) cache.findValid(propertyContext, cacheKey, validity);
                if (pool == null) {
                    pool = createXPathPool(xpathString, prefixToURIMap, variableNames, functionLibrary, baseURI, isAvt, locationData);
                    cache.add(propertyContext, cacheKey, validity, pool);
                }

                // Get object from pool
                final Object o = pool.borrowObject();
                expr = (PooledXPathExpression) o;
            }

            // Set context items and position
            expr.setContextItems(contextItems, contextPosition);

            // Set variables
            expr.setVariables(variableToValueMap);

            return expr;
        } catch (Exception e) {
            throw handleXPathException(e, xpathString, "preparing XPath expression", locationData);
        }
    }

    private static ValidationException handleXPathException(Exception e, String xpathString, String description, LocationData locationData) {
        final ValidationException validationException = ValidationException.wrapException(e, new ExtendedLocationData(locationData, description,
                "expression", xpathString));

        // Details of ExtendedLocationData passed are discarded by the constructor for ExtendedLocationData above,
        // so we need to explicitly add them.
        if (locationData instanceof ExtendedLocationData)
            validationException.addLocationData(locationData);

        return validationException;
    }

    private static ObjectPool createXPathPool(String xpathString,
                                              Map<String, String> prefixToURIMap,
                                              Set<String> variableNames,
                                              FunctionLibrary functionLibrary,
                                              String baseURI,
                                              boolean isAvt,
                                              LocationData locationData) {
        try {
            // TODO: pool should have at least one hard reference
            final SoftReferenceObjectPool pool = new SoftReferenceObjectPool();
            pool.setFactory(new XFormsCachePoolableObjetFactory(pool, xpathString,
                    prefixToURIMap, variableNames, functionLibrary, baseURI, isAvt, false, locationData));

            return pool;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class  XFormsCachePoolableObjetFactory implements PoolableObjectFactory {
        private final String xpathString;
        private final Map<String, String> prefixToURIMap;
        private final Set<String> variableNames;
        // NOTE: storing the FunctionLibrary in cache is ok if it doesn't hold dynamic references (case of global XFormsFunctionLibrary)
        private final FunctionLibrary functionLibrary;
        private final ObjectPool pool;
        private final String baseURI;
        private final boolean isAvt;
        private final boolean allowAllVariables;
        private final LocationData locationData;

        public XFormsCachePoolableObjetFactory(ObjectPool pool,
                                          String xpathString,
                                          Map<String, String> prefixToURIMap,
                                          Set<String> variableNames,
                                          FunctionLibrary functionLibrary,
                                          String baseURI,
                                          boolean isAvt,
                                          boolean allowAllVariables,
                                          LocationData locationData) {
            this.pool = pool;
            this.xpathString = xpathString;
            this.prefixToURIMap = prefixToURIMap;
            this.variableNames = variableNames;
            this.functionLibrary = functionLibrary;
            this.baseURI = baseURI;
            this.isAvt = isAvt;
            this.allowAllVariables = allowAllVariables;
            this.locationData = locationData;
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
                logger.debug("makeObject(" + xpathString + ")");

            // Create context
            final IndependentContext independentContext = new XPathCacheStaticContext(allowAllVariables);

            // Set the base URI if specified
            if (baseURI != null)
                independentContext.setBaseURI(baseURI);

            // Declare namespaces
            if (prefixToURIMap != null) {
                for (final String prefix: prefixToURIMap.keySet()) {
                    independentContext.declareNamespace(prefix, prefixToURIMap.get(prefix));
                }
            }

            // Declare variables (we don't use the values here, just the names)
            final Map<String, Variable> variables = new HashMap<String, Variable>();
            if (variableNames != null) {
                for (final String name: variableNames) {
                    final Variable variable = independentContext.declareVariable(name);
                    variable.setUseStack(true);// "Indicate that values of variables are to be found on the stack, not in the Variable object itself"
                    variables.put(name, variable);
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
                    final Expression tempExpression = AttributeValueTemplate.make(xpathString, -1, independentContext);
                    // Running typeCheck() is mandatory otherwise things break! This is also done when using evaluator.createExpression()
                    expression = tempExpression.typeCheck(independentContext, Type.ITEM_TYPE);
                } else {
                    // We used to use XPathEvaluator.createExpression(), but there is a bug in it related to slots allocation, so we do the work ourselves instead.
                    final Expression tempExpression = ExpressionTool.make(xpathString, independentContext, 0, Token.EOF, 1);
                    expression = tempExpression.typeCheck(independentContext, Type.ITEM_TYPE);
                }

                // Allocate variable slots in all cases
                ExpressionTool.allocateSlots(expression, independentContext.getStackFrameMap().getNumberOfVariables(), independentContext.getStackFrameMap());

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
                                return (locationData != null) ? locationData.getCol() : -1;
                            }

                            public int getLineNumber() {
                                return (locationData != null) ? locationData.getLine() : -1;
                            }

                            public String getPublicId() {
                                return (locationData != null) ? locationData.getPublicID() : null;
                            }

                            public String getSystemId() {
                                return (locationData != null) ? locationData.getSystemID() : null;
                            }
                        });
                    }
                }

                // TODO: For now only play with XForms expressions. But should decide probably based on flag?
                if (false && functionLibrary == XFormsContainingDocument.getFunctionLibrary()) {
                    final List<String> instanceIds = analyzeExpression(expression, xpathString);
                    if (instanceIds == null)
                        logger.info("  XXX EXPRESSION DEPENDS ON MORE THAN INSTANCES: " + xpathString);
                    else {
                        logger.info("  XXX EXPRESSION DEPENDS ON INSTANCES: " + xpathString);
                        for (String instanceId: instanceIds) {
                            logger.info("    instance: " + instanceId);
                        }
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

    private static List<String> analyzeExpression(Expression expression, String xpathString) {
        if (expression instanceof ComputedExpression) {
            try {
                final PathMap pathmap = new PathMap((ComputedExpression) expression, new Configuration());
                logger.info("TEST XPATH PATHS - path for expression: " + xpathString);
                pathmap.diagnosticDump(System.out);

                final int dependencies = expression.getDependencies();

                if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
                    System.out.println("  xxx DEPENDS_ON_CONTEXT_ITEM");
                    return null;
                }
                if ((dependencies & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
                    System.out.println("  xxx DEPENDS_ON_CURRENT_ITEM");
                    return null;
                }
                if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT) != 0) {
                    System.out.println("  xxx DEPENDS_ON_CONTEXT_DOCUMENT");
                    return null;
                }
                if ((dependencies & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
                    System.out.println("  xxx DEPENDS_ON_LOCAL_VARIABLES");
                    // Some day we'll have variables
                    return null;
                }
                if ((dependencies & StaticProperty.NON_CREATIVE) != 0) {
                    System.out.println("  xxx NON_CREATIVE");
                }

                final List<String> instancesList = new ArrayList<String>();

                final PathMap.PathMapRoot[] roots = pathmap.getPathMapRoots();
                for (final PathMap.PathMapRoot root: roots) {
                    final Expression rootExpression = root.getRootExpression();

                    if (rootExpression instanceof Instance || rootExpression instanceof XXFormsInstance) {
                        final FunctionCall functionCall = (FunctionCall) rootExpression;

                        // TODO: Saxon 9.0 expressions should test "instanceof StringValue" to "instanceof StringLiteral"
                        if (functionCall.getArguments()[0] instanceof StringValue) {
                            final String instanceName = ((StringValue) functionCall.getArguments()[0]).getStringValue();
                            instancesList.add(instanceName);
                        } else {
                            // Instance name is not known at compile time
                            return null;
                        }
                    } else if (rootExpression instanceof Doc) {// don't need document() function as that is XSLT
                        final FunctionCall functionCall = (FunctionCall) rootExpression;

                        // TODO: Saxon 9.0 expressions should test "instanceof StringValue" to "instanceof StringLiteral"
                        if (functionCall.getArguments()[0] instanceof StringValue) {
//                            final String literalURI = ((StringValue) functionCall.getArguments()[0]).getStringValue();
                            return null;
                        } else {
                            // Document name is not known at compile time
                            return null;
                        }
                    } else if (rootExpression instanceof ContextItemExpression) {
                        return null;
                    } else if (rootExpression instanceof RootExpression) {
                        // We depend on the current XForms model.
                        return null;
                    }

//                                final PathMap.PathMapArc[] rootArcs = root.getArcs();
//
//                                for (int j = 0; j < rootArcs.length; j++) {
//                                    final PathMapArc currentArc = rootArcs[j];
//                                    final AxisExpression getStep
//                                }

                }
                return instancesList;

            } catch (Exception e) {
                logger.error("EXCEPTION WHILE ANALYZING PATHS: " + xpathString);
                return null;
            }
        } else {
            logger.info("TEST XPATH PATHS - expression not a ComputedExpression: " + xpathString);
            return Collections.emptyList();
        }
    }

    /**
     * Marker interface for XPath function context.
     */
    public interface FunctionContext {

    }
}
