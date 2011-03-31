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
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XPathCacheStaticContext;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.ExpressionTool;
import org.orbeon.saxon.expr.ExpressionVisitor;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.functions.FunctionLibraryList;
import org.orbeon.saxon.instruct.SlotManager;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.style.AttributeValueTemplate;
import org.orbeon.saxon.sxpath.IndependentContext;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.sxpath.XPathExpression;
import org.orbeon.saxon.sxpath.XPathVariable;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.Type;
import org.orbeon.saxon.value.SequenceExtent;

import java.util.*;

/**
 * Use the object cache to cache XPath expressions, which are costly to parse.
 *
 * It is mandatory to call returnToPool() on the returned PooledXPathExpression after use. It is
 * good to do this within a finally() block enclosing the use of the expression.
 */
public class XPathCache {

    private static NamePool NAME_POOL = new NamePool();
    private static Configuration CONFIGURATION = new Configuration() {
        @Override
        public void setAllowExternalFunctions(boolean allowExternalFunctions) {
            throw new IllegalStateException("Global XPath configuration is be read-only");
        }

        @Override
        public void setConfigurationProperty(String name, Object value) {
            throw new IllegalStateException("Global XPath configuration is be read-only");
        }
    };
    static {
        CONFIGURATION.setNamePool(NAME_POOL);
    }

    public static final String XPATH_CACHE_NAME = "cache.xpath";
    private static final int XPATH_CACHE_DEFAULT_SIZE = 200;

    private static final Logger logger = LoggerFactory.createLogger(XPathCache.class);

    public static class XPathContext {
        public final NamespaceMapping namespaceMapping;
        public final Map<String, ValueRepresentation> variableToValueMap;
        public final FunctionLibrary functionLibrary;
        public final FunctionContext functionContext;
        public final String baseURI;
        public final LocationData locationData;

        public XPathContext(NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                            FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {
            this.namespaceMapping = namespaceMapping;
            this.variableToValueMap = variableToValueMap;
            this.functionLibrary = functionLibrary;
            this.functionContext = functionContext;
            this.baseURI = baseURI;
            this.locationData = locationData;
        }
    }

    public static Configuration getGlobalConfiguration() {
        return CONFIGURATION;
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static List evaluate(Item contextItem, String xpathString,
                                NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        return evaluate(Collections.singletonList(contextItem), 1, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static List evaluate(List<Item> contextItems, int contextPosition, String xpathString,
                                NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(
                getGlobalConfiguration(), contextItems, contextPosition,
                xpathString, namespaceMapping, variableToValueMap, functionLibrary, baseURI, false, locationData);
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
    public static List<Item> evaluateKeepItems(List<Item> contextItems, int contextPosition,
                                               String xpathString, NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                               FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(
                getGlobalConfiguration(), contextItems, contextPosition,
                xpathString, namespaceMapping, variableToValueMap, functionLibrary, baseURI, false, locationData);
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
    public static SequenceExtent evaluateAsExtent(List<Item> contextItems, int contextPosition,
                                                  String xpathString, NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                                  FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(
                getGlobalConfiguration(), contextItems, contextPosition,
                xpathString, namespaceMapping, variableToValueMap, functionLibrary, baseURI, false, locationData);
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
    public static Object evaluateSingle(XPathContext xpathContext, Item contextItem, String xpathString) {
        return evaluateSingle(Collections.singletonList(contextItem), 1, xpathString,
                xpathContext.namespaceMapping, xpathContext.variableToValueMap, xpathContext.functionLibrary,
                xpathContext.functionContext, xpathContext.baseURI, xpathContext.locationData);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(Item contextItem, String xpathString,
                                        NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                        FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        return evaluateSingle(Collections.singletonList(contextItem), 1, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression on the document.
     */
    public static Object evaluateSingle(List<Item> contextItems, int contextPosition, String xpathString,
                                        NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                        FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(
                getGlobalConfiguration(), contextItems, contextPosition,
                xpathString, namespaceMapping, variableToValueMap, functionLibrary, baseURI, false, locationData);
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
    public static String evaluateAsAvt(XPathContext xpathContext, Item contextItem, String xpathString) {
        return evaluateAsAvt(Collections.singletonList(contextItem), 1, xpathString, xpathContext.namespaceMapping,
                xpathContext.variableToValueMap, xpathContext.functionLibrary, xpathContext.functionContext, xpathContext.baseURI, xpathContext.locationData);
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(Item contextItem, String xpathString, NamespaceMapping namespaceMapping,
                                       Map<String, ValueRepresentation> variableToValueMap, FunctionLibrary functionLibrary,
                                       FunctionContext functionContext, String baseURI, LocationData locationData) {

        return evaluateAsAvt(Collections.singletonList(contextItem), 1, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression on the document as an attribute value template, and return its string value.
     */
    public static String evaluateAsAvt(List<Item> contextItems, int contextPosition, String xpathString,
                                       NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                       FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression = XPathCache.getXPathExpression(
                getGlobalConfiguration(), contextItems, contextPosition, xpathString,
                namespaceMapping, variableToValueMap, functionLibrary, baseURI, true, locationData);
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
    public static String evaluateAsString(Item contextItem, String xpathString,
                                          NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                          FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        return evaluateAsString(Collections.singletonList(contextItem), 1, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, functionContext, baseURI, locationData);
    }

    /**
     * Evaluate an XPath expression and return its string value.
     */
    public static String evaluateAsString(List<Item> contextItems, int contextPosition,
                                          String xpathString, NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap,
                                          FunctionLibrary functionLibrary, FunctionContext functionContext, String baseURI, LocationData locationData) {

        final PooledXPathExpression xpathExpression =  XPathCache.getXPathExpression(
                getGlobalConfiguration(), contextItems, contextPosition, "xs:string((" + xpathString + ")[1])",
                namespaceMapping, variableToValueMap, functionLibrary, baseURI, false, locationData);
        try {
            final Object result = xpathExpression.evaluateSingleKeepNodeInfo(functionContext);
            return (result != null) ? result.toString() : null;
        } catch (XPathException e) {
            throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData);
        } finally {
            if (xpathExpression != null) xpathExpression.returnToPool();
        }
    }

    // NOTE: called from DelegationProcessor and ConcreteForEachProcessor
    public static PooledXPathExpression getXPathExpression(Configuration configuration,
                                                           Item contextItem,
                                                           String xpathString,
                                                           LocationData locationData) {
        return getXPathExpression(configuration, contextItem, xpathString, null, null, null, null, locationData);
    }

    public static PooledXPathExpression getXPathExpression(Configuration configuration,
                                                           Item contextItem,
                                                           String xpathString,
                                                           NamespaceMapping namespaceMapping,
                                                           LocationData locationData) {
        return getXPathExpression(configuration, contextItem, xpathString, namespaceMapping, null, null, null, locationData);
    }

    public static PooledXPathExpression getXPathExpression(Configuration configuration,
                                                           Item contextItem,
                                                           String xpathString,
                                                           NamespaceMapping namespaceMapping,
                                                           Map<String, ValueRepresentation> variableToValueMap,
                                                           FunctionLibrary functionLibrary,
                                                           String baseURI,
                                                           LocationData locationData) {
        final List<Item> contextItems = Collections.singletonList(contextItem);
        return getXPathExpression(configuration, contextItems, 1, xpathString, namespaceMapping, variableToValueMap,
                functionLibrary, baseURI, false, locationData);
    }

    /**
     * Just attempt to compile an XPath expression. An exception is thrown if the expression is not statically correct.
     * Any variable used by the expression is assumed to be in scope. The expression is not added to the cache.
     *
     * @param xpathString       XPath string
     * @param namespaceMapping  namespace mapping
     * @param functionLibrary   function library
     * @throws Exception        if the expression is not correct
     */
    public static void checkXPathExpression(Configuration configuration, String xpathString, NamespaceMapping namespaceMapping,
                                            FunctionLibrary functionLibrary) throws Exception {

        new XPathCachePoolableObjectFactory(null, configuration, xpathString, namespaceMapping, null, functionLibrary, null, false, true, null).makeObject();
    }

    public static Expression createExpression(Configuration configuration, String xpathString, NamespaceMapping namespaceMapping, FunctionLibrary functionLibrary) {
        try {
            return ((PooledXPathExpression) new XPathCachePoolableObjectFactory(null, configuration, xpathString, namespaceMapping,
                    null, functionLibrary, null, false, true, null).makeObject()).getExpression();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static PooledXPathExpression getXPathExpression(Configuration configuration,
                                                            List<Item> contextItems, int contextPosition,
                                                            String xpathString,
                                                            NamespaceMapping namespaceMapping,
                                                            Map<String, ValueRepresentation> variableToValueMap,
                                                            FunctionLibrary functionLibrary,
                                                            String baseURI,
                                                            boolean isAvt,
                                                            LocationData locationData) {

        try {

            // Find pool from cache
            final Long validity = (long) 0;
            final Cache cache = ObjectCache.instance(XPATH_CACHE_NAME, XPATH_CACHE_DEFAULT_SIZE);
            final StringBuilder cacheKeyString = new StringBuilder(xpathString);

            if (functionLibrary != null) {// This is ok
                cacheKeyString.append('|');
                cacheKeyString.append(Integer.toString(functionLibrary.hashCode()));
            }
            // NOTE: Mike Kay confirms on 2007-07-04 that compilation depends on the namespace context, so we need
            // to use it as part of the cache key.
            if (namespaceMapping != null) {
                // NOTE: Hash is mandatory in NamespaceMapping
                cacheKeyString.append('|');
                cacheKeyString.append(namespaceMapping.hash);
            }

            if (variableToValueMap != null && variableToValueMap.size() > 0) {
                // There are some variables in scope. They must be part of the key
                // TODO: Put this in static state as this can be determined statically once and for all
                for (final String variableName: variableToValueMap.keySet()) {
                    cacheKeyString.append('|');
                    cacheKeyString.append(variableName);
                }
            }

            // Add this to the key as evaluating "name" as XPath or as AVT is very different!
            cacheKeyString.append('|');
            cacheKeyString.append(Boolean.toString(isAvt));

            // TODO: Add baseURI to cache key (currently, baseURI is pretty much unused)

            // NOTE: Copy HashSet, as the one returned by the map keeps a pointer to the Map! This can cause the XPath
            // cache to keep a reference to variable values, which in turn can keep a reference all the way to e.g. an
            // XFormsContainingDocument.
            final Set<String> variableNames = (variableToValueMap != null) ? new LinkedHashSet<String>(variableToValueMap.keySet()) : null;
            final PooledXPathExpression pooledXPathExpression;
            {
                // Get or create pool
                final InternalCacheKey cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString.toString());
                ObjectPool pool = (ObjectPool) cache.findValid(cacheKey, validity);
                if (pool == null) {
                    pool = createXPathPool(configuration, xpathString, namespaceMapping, variableNames, functionLibrary, baseURI, isAvt, locationData);
                    cache.add(cacheKey, validity, pool);
                }

                // Get object from pool
                final Object o = pool.borrowObject();
                pooledXPathExpression = (PooledXPathExpression) o;
            }

            // Set context items and position
            pooledXPathExpression.setContextItems(contextItems, contextPosition);

            // Set variables
            pooledXPathExpression.setVariables(variableToValueMap);

            return pooledXPathExpression;
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

    private static ObjectPool createXPathPool(Configuration xpathConfiguration,
                                              String xpathString,
                                              NamespaceMapping namespaceMapping,
                                              Set<String> variableNames,
                                              FunctionLibrary functionLibrary,
                                              String baseURI,
                                              boolean isAvt,
                                              LocationData locationData) {
        try {
            // TODO: pool should have at least one hard reference
            final SoftReferenceObjectPool pool = new SoftReferenceObjectPool();
            pool.setFactory(new XPathCachePoolableObjectFactory(pool, xpathConfiguration, xpathString,
                    namespaceMapping, variableNames, functionLibrary, baseURI, isAvt, false, locationData));

            return pool;
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static class XPathCachePoolableObjectFactory implements PoolableObjectFactory {
        private final ObjectPool pool;
        private Configuration xpathConfiguration;
        private final String xpathString;
        private final NamespaceMapping namespaceMapping;
        private final Set<String> variableNames;
        // NOTE: storing the FunctionLibrary in cache is ok if it doesn't hold dynamic references (case of global XFormsFunctionLibrary)
        private final FunctionLibrary functionLibrary;
        private final String baseURI;
        private final boolean isAvt;
        private final boolean allowAllVariables;
        private final LocationData locationData;

        public XPathCachePoolableObjectFactory(ObjectPool pool,
                                               Configuration xpathConfiguration,
                                               String xpathString,
                                               NamespaceMapping namespaceMapping,
                                               Set<String> variableNames,
                                               FunctionLibrary functionLibrary,
                                               String baseURI,
                                               boolean isAvt,
                                               boolean allowAllVariables,
                                               LocationData locationData) {
            this.pool = pool;

            this.xpathConfiguration = (xpathConfiguration != null) ? xpathConfiguration : XPathCache.getGlobalConfiguration();

            this.xpathString = xpathString;
            this.namespaceMapping = namespaceMapping;
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
            final IndependentContext independentContext = new XPathCacheStaticContext(xpathConfiguration, allowAllVariables);

            // Set the base URI if specified
            if (baseURI != null)
                independentContext.setBaseURI(baseURI);

            // Declare namespaces
            if (namespaceMapping != null) {
                for (final Map.Entry<String, String> entry : namespaceMapping.mapping.entrySet()) {
                    independentContext.declareNamespace(entry.getKey(), entry.getValue());
                }
            }

            // Declare variables (we don't use the values here, just the names)
            final Map<String, XPathVariable> variables = new HashMap<String, XPathVariable>();
            if (variableNames != null) {
                for (final String name : variableNames) {
                    final XPathVariable variable = independentContext.declareVariable("", name);
                    variables.put(name, variable);
                }
            }

            // Add function library
            if (functionLibrary != null) {
                ((FunctionLibraryList) independentContext.getFunctionLibrary()).libraryList.add(0, functionLibrary);
            }

            return createPoolableXPathExpression(pool, independentContext, xpathString, variables, isAvt);
        }

        public void passivateObject(Object o) throws Exception {
        }

        public boolean validateObject(Object o) {
            return true;
        }
    }

    public static PooledXPathExpression createPoolableXPathExpression(ObjectPool pool, IndependentContext independentContext,
                                                                      String xpathString, Map<String, XPathVariable> variables, boolean isAvt) {
        // Create and compile the expression
        try {
            final XPathExpression expression;
            if (isAvt) {
                // AVT
                final Expression tempExpression = AttributeValueTemplate.make(xpathString, -1, independentContext);
                expression = prepareExpression(independentContext, tempExpression);
            } else {
                // Regular expression
                final XPathEvaluator evaluator = new XPathEvaluator();
                evaluator.setStaticContext(independentContext);
                expression = evaluator.createExpression(xpathString);

            }
            return new PooledXPathExpression(expression, pool, variables);
        } catch (Throwable t) {
            throw new OXFException(t);
        }
    }

    // Ideally: add this to Saxon XPathEvaluator
    public static XPathExpression prepareExpression(IndependentContext independentContext, Expression expression) throws XPathException {
        // Based on XPathEvaluator.createExpression()
        expression.setContainer(independentContext);
        ExpressionVisitor visitor = ExpressionVisitor.make(independentContext);
        visitor.setExecutable(independentContext.getExecutable());
        expression = visitor.typeCheck(expression, Type.ITEM_TYPE);
        expression = visitor.optimize(expression, Type.ITEM_TYPE);
        final SlotManager map = independentContext.getStackFrameMap();
        final int numberOfExternalVariables = map.getNumberOfVariables();
        ExpressionTool.allocateSlots(expression, numberOfExternalVariables, map);

        // Set an evaluator as later it might be requested
        final XPathEvaluator evaluator = new XPathEvaluator();
        evaluator.setStaticContext(independentContext);

        return new CustomXPathExpression(evaluator, expression, map, numberOfExternalVariables);
    }

    private static class CustomXPathExpression extends XPathExpression {

//        private SlotManager slotManager;

        protected CustomXPathExpression(XPathEvaluator evaluator, Expression exp, SlotManager map, int numberOfExternalVariables) {
            super(evaluator, exp);
            setStackFrameMap(map, numberOfExternalVariables);
//            this.slotManager = map;
        }

        // Ideally: put this here instead of modifying Saxon, but then we need our own XPathEvaluator as well to return CustomXPathExpression
//        public XPathDynamicContext createDynamicContext(XPathContextMajor context, Item contextItem) {
//            // Set context item
//            final UnfailingIterator contextIterator = SingletonIterator.makeIterator(contextItem);
//            contextIterator.next();
//            context.setCurrentIterator(contextIterator);
//
//            context.openStackFrame(slotManager);
//            return new XPathDynamicContext(context, slotManager);
//        }
    }

    /**
     * Marker interface for XPath function context.
     */
    public interface FunctionContext {}
}
