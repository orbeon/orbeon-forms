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
package org.orbeon.oxf.util

import java.util.{List ⇒ JList, Map ⇒ JMap}
import org.apache.commons.pool.ObjectPool
import org.apache.commons.pool.PoolableObjectFactory
import org.orbeon.oxf.cache.InternalCacheKey
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.XPathCacheStaticContext
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.Expression
import org.orbeon.saxon.expr.ExpressionTool
import org.orbeon.saxon.expr.ExpressionVisitor
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.functions.FunctionLibraryList
import org.orbeon.saxon.instruct.SlotManager
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NamePool
import org.orbeon.saxon.om.ValueRepresentation
import org.orbeon.saxon.style.AttributeValueTemplate
import org.orbeon.saxon.sxpath.IndependentContext
import org.orbeon.saxon.sxpath.XPathEvaluator
import org.orbeon.saxon.sxpath.XPathExpression
import org.orbeon.saxon.sxpath.XPathVariable
import org.orbeon.saxon.value.SequenceExtent
import collection.JavaConverters._

/**
 * Use the object cache to cache XPath expressions, which are costly to parse.
 *
 * It is mandatory to call returnToPool() on the returned PooledXPathExpression after use. It is
 * good to do this within a finally() block enclosing the use of the expression.
 */
object XPathCache {

    private val Configuration = new Configuration {
        
        setNamePool(new NamePool)
        
        override def setAllowExternalFunctions(allowExternalFunctions: Boolean): Unit =
            throw new IllegalStateException("Global XPath configuration is read-only")

        override def setConfigurationProperty(name: String, value: AnyRef): Unit =
            throw new IllegalStateException("Global XPath configuration is read-only")
    }
    
    private val XPathCacheName = "cache.xpath"
    private val XPathCacheDefaultSize = 200
    
    private val Logger = LoggerFactory.createLogger(getClass)

    // Marker for XPath function context
    trait FunctionContext
    
    case class XPathContext(
        namespaceMapping: NamespaceMapping,
        variableToValueMap: JMap[String, ValueRepresentation],
        functionLibrary: FunctionLibrary,
        functionContext: FunctionContext,
        baseURI: String,
        locationData: LocationData)
    
    
    def getGlobalConfiguration = Configuration

    // Evaluate an XPath expression on the document
    def evaluate(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): JList[_] =
        evaluate(
            Seq(contextItem).asJava,
            1,
            xpathString,
            namespaceMapping,
            variableToValueMap,
            functionLibrary,
            functionContext,
            baseURI,
            locationData)

    // Evaluate an XPath expression on the document
    def evaluate(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): JList[_] = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, baseURI, false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            xpathExpression.evaluateKeepNodeInfo(functionContext)
        }
    }

    // Evaluate an XPath expression on the document and keep Item objects in the result
    def evaluateKeepItems(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): JList[Item] = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, baseURI, false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            xpathExpression.evaluateKeepItems(functionContext)
        }
    }

    // Evaluate an XPath expression on the document and keep Item objects in the result
    def evaluateSingleKeepItems(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): Item = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, baseURI, false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            xpathExpression.evaluateSingleKeepItems(functionContext)
        }
    }

    // Evaluate the expression as a variable value usable by Saxon in further XPath expressions
    def evaluateAsExtent(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): SequenceExtent = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap,
                functionLibrary, baseURI, false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            xpathExpression.evaluateAsExtent(functionContext)
        }
    }

    // Evaluate an XPath expression on the document
    def evaluateSingle(xpathContext: XPathContext, contextItem: Item, xpathString: String): AnyRef =
        evaluateSingle(
            Seq(contextItem).asJava, 1, xpathString, xpathContext.namespaceMapping,
            xpathContext.variableToValueMap, xpathContext.functionLibrary, xpathContext.functionContext,
            xpathContext.baseURI, xpathContext.locationData)

    // Evaluate an XPath expression on the document
    def evaluateSingle(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): AnyRef =
        evaluateSingle(
            Seq(contextItem).asJava, 1, xpathString, namespaceMapping, variableToValueMap,
            functionLibrary, functionContext, baseURI, locationData)

    // Evaluate an XPath expression on the document
    def evaluateSingle(
        contextItems: JList[Item], contextPosition: Int, xpathString: String, namespaceMapping: NamespaceMapping,
        variableToValueMap: JMap[String, ValueRepresentation], functionLibrary: FunctionLibrary,
        functionContext: FunctionContext, baseURI: String, locationData: LocationData) = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap,
                functionLibrary, baseURI, false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            xpathExpression.evaluateSingleKeepNodeInfo(functionContext)
        }
    }

    // Evaluate an XPath expression on the document as an attribute value template, and return its string value
    def evaluateAsAvt(xpathContext: XPathContext, contextItem: Item, xpathString: String): String =
        evaluateAsAvt(
            Seq(contextItem).asJava, 1, xpathString, xpathContext.namespaceMapping,
            xpathContext.variableToValueMap, xpathContext.functionLibrary, xpathContext.functionContext,
            xpathContext.baseURI, xpathContext.locationData)

    // Evaluate an XPath expression on the document as an attribute value template, and return its string value
    def evaluateAsAvt(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): String =
        evaluateAsAvt(
            Seq(contextItem).asJava, 1, xpathString, namespaceMapping, variableToValueMap,
            functionLibrary, functionContext, baseURI, locationData)

    // Evaluate an XPath expression on the document as an attribute value template, and return its string value
    def evaluateAsAvt(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): String = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap,
                functionLibrary, baseURI, true, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            Option(xpathExpression.evaluateSingleKeepNodeInfo(functionContext)) map (_.toString) orNull
        }
    }

    // Evaluate an XPath expression and return its string value
    def evaluateAsString(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): String =
        evaluateAsString(
            Seq(contextItem).asJava, 1, xpathString, namespaceMapping, variableToValueMap,
            functionLibrary, functionContext, baseURI, locationData)

    // Evaluate an XPath expression and return its string value
    def evaluateAsString(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData): String = {

        val xpathExpression =
            getXPathExpression(
                getGlobalConfiguration, contextItems, contextPosition, "xs:string((" + xpathString + ")[1])",
                namespaceMapping, variableToValueMap, functionLibrary, baseURI, false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData) {
            Option(xpathExpression.evaluateSingleKeepNodeInfo(functionContext)) map (_.toString) orNull
        }
    }

    def getXPathExpression(
        configuration: Configuration, contextItem: Item, xpathString: String,
        locationData: LocationData): PooledXPathExpression =
        getXPathExpression(configuration, contextItem, xpathString, null, null, null, null, locationData)

    def getXPathExpression(
            configuration: Configuration,
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            locationData: LocationData): PooledXPathExpression =
        getXPathExpression(configuration, contextItem, xpathString, namespaceMapping, null, null, null, locationData)

    def getXPathExpression(
            configuration: Configuration,
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            locationData: LocationData): PooledXPathExpression =
        getXPathExpression(
            configuration, Seq(contextItem).asJava, 1, xpathString, namespaceMapping,
            variableToValueMap, functionLibrary, baseURI,
            false, locationData)

    /**
     * Just attempt to compile an XPath expression. An exception is thrown if the expression is not statically correct.
     * Any variable used by the expression is assumed to be in scope. The expression is not added to the cache.
     *
     * @param xpathString       XPath string
     * @param namespaceMapping  namespace mapping
     * @param functionLibrary   function library
     * @throws Exception        if the expression is not correct
     */
    def checkXPathExpression(
            configuration: Configuration,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            functionLibrary: FunctionLibrary): Unit =
        new XPathCachePoolableObjectFactory(configurationOrDefault(configuration), xpathString, namespaceMapping, null, functionLibrary, null, false, true, null).makeObject

    def createExpression(
            configuration: Configuration,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            functionLibrary: FunctionLibrary): Expression =
        (new XPathCachePoolableObjectFactory(configuration, xpathString, namespaceMapping, null, functionLibrary, null, false, true, null)).makeObject.getExpression

    private def getXPathExpression(
            configuration: Configuration,
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            isAvt: Boolean,
            locationData: LocationData): PooledXPathExpression = {
        try {
            // Find pool from cache
            val validity = 0L
            val cache = ObjectCache.instance(XPathCacheName, XPathCacheDefaultSize)
            val cacheKeyString = new StringBuilder(xpathString)

            if (functionLibrary ne null) {// This is ok
                cacheKeyString.append('|')
                cacheKeyString.append(functionLibrary.hashCode.toString)
            }
            // NOTE: Mike Kay confirms on 2007-07-04 that compilation depends on the namespace context, so we need
            // to use it as part of the cache key.
            if (namespaceMapping ne null) {
                // NOTE: Hash is mandatory in NamespaceMapping
                cacheKeyString.append('|')
                cacheKeyString.append(namespaceMapping.hash)
            }
            if ((variableToValueMap ne null) && variableToValueMap.size > 0) {
                // There are some variables in scope. They must be part of the key
                // TODO: Put this in static state as this can be determined statically once and for all
                for (variableName ← variableToValueMap.asScala.keys) {
                    cacheKeyString.append('|')
                    cacheKeyString.append(variableName)
                }
            }

            // Add this to the key as evaluating "name" as XPath or as AVT is very different!
            cacheKeyString.append('|')
            cacheKeyString.append(isAvt.toString)

            // TODO: Add baseURI to cache key (currently, baseURI is pretty much unused)

            // NOTE: Make sure to copy the values in the key set, as the set returned by the map keeps a pointer to the
            // Map! This can cause the XPath cache to keep a reference to variable values, which in turn can keep a
            // reference all the way to e.g. an XFormsContainingDocument.
            val variableNames = Option(variableToValueMap) map (_.keySet.asScala.toList) getOrElse List()

            val pooledXPathExpression = {
                val cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString.toString)
                var pool = cache.findValid(cacheKey, validity).asInstanceOf[ObjectPool[PooledXPathExpression]]
                if (pool eq null) {
                    pool = createXPathPool(configuration, xpathString, namespaceMapping, variableNames, functionLibrary, baseURI, isAvt, locationData)
                    cache.add(cacheKey, validity, pool)
                }
                // Get object from pool
                pool.borrowObject
            }

            // Set context items and position
            pooledXPathExpression.setContextItems(contextItems, contextPosition)

            // Set variables
            pooledXPathExpression.setVariables(variableToValueMap)

            pooledXPathExpression
        } catch {
            case e: Exception ⇒ throw handleXPathException(e, xpathString, "preparing XPath expression", locationData)
        }
    }

    private def createXPathPool(
            xpathConfiguration: Configuration,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableNames: List[String],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            isAvt: Boolean,
            locationData: LocationData): ObjectPool[PooledXPathExpression] = {

        // TODO: pool should have at least one hard reference
        val factory = new XPathCachePoolableObjectFactory(
            configurationOrDefault(xpathConfiguration), xpathString, namespaceMapping, variableNames,
            functionLibrary, baseURI, isAvt, false, locationData)
        val pool = new SoftReferenceObjectPool(factory)
        factory.pool = pool
        pool
    }

    def createPoolableXPathExpression(
        pool: ObjectPool[PooledXPathExpression], independentContext: IndependentContext, xpathString: String,
        variables: JMap[String, XPathVariable], isAvt: Boolean): PooledXPathExpression = {
        // Create and compile the expression
        val expression =
            if (isAvt) {
                val tempExpression: Expression = AttributeValueTemplate.make(xpathString, -1, independentContext)
                prepareExpression(independentContext, tempExpression)
            }
            else {
                val evaluator = new XPathEvaluator
                evaluator.setStaticContext(independentContext)
                evaluator.createExpression(xpathString)
            }
        new PooledXPathExpression(expression, pool, variables)
    }

    // Ideally: add this to Saxon XPathEvaluator
    def prepareExpression(independentContext: IndependentContext, expression: Expression): XPathExpression = {
        // Based on XPathEvaluator.createExpression()
        expression.setContainer(independentContext)
        val visitor = ExpressionVisitor.make(independentContext)
        visitor.setExecutable(independentContext.getExecutable)
        var newExpression = visitor.typeCheck(expression, Type.ITEM_TYPE)
        newExpression = visitor.optimize(newExpression, Type.ITEM_TYPE)
        val map = independentContext.getStackFrameMap
        val numberOfExternalVariables = map.getNumberOfVariables
        ExpressionTool.allocateSlots(expression, numberOfExternalVariables, map)

        // Set an evaluator as later it might be requested
        val evaluator = new XPathEvaluator
        evaluator.setStaticContext(independentContext)

        new CustomXPathExpression(evaluator, newExpression, map, numberOfExternalVariables)
    }

    // Not sure if/when configuration can be null, but it shouldn't be
    private def configurationOrDefault(configuration: Configuration) =
        Option(configuration) getOrElse getGlobalConfiguration

    private class XPathCachePoolableObjectFactory(
            xpathConfiguration: Configuration,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableNames: List[String],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            isAvt: Boolean,
            allowAllVariables: Boolean,
            locationData: LocationData)
        extends PoolableObjectFactory[PooledXPathExpression] {

        // NOTE: storing the FunctionLibrary in cache is ok if it doesn't hold dynamic references (case of global XFormsFunctionLibrary)

        var pool: ObjectPool[PooledXPathExpression] = _

        // Create and compile an XPath expression object
        def makeObject: PooledXPathExpression = {
            if (Logger.isDebugEnabled)
                Logger.debug("makeObject(" + xpathString + ")")
            
            // Create context
            val independentContext = new XPathCacheStaticContext(xpathConfiguration, allowAllVariables)
            
            // Set the base URI if specified
            if (baseURI ne null)
                independentContext.setBaseURI(baseURI)
            
            // Declare namespaces
            if (namespaceMapping ne null)
                for ((prefix, uri) ← namespaceMapping.mapping.asScala)
                    independentContext.declareNamespace(prefix, uri)
            
            // Declare variables (we don't use the values here, just the names)
            val variables =
                if (variableNames ne null)
                    for {
                        name ← variableNames.toIterable
                        variable = independentContext.declareVariable("", name)
                    } yield
                        name → variable
                else
                    Nil
            
            // Add function library
            if (functionLibrary ne null)
                independentContext.getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.asInstanceOf[JList[FunctionLibrary]].add(0, functionLibrary)
            
            createPoolableXPathExpression(pool, independentContext, xpathString, variables.toMap.asJava, isAvt)
        }

        def destroyObject(o: PooledXPathExpression): Unit = o.destroy()
        def activateObject(o: PooledXPathExpression) = ()
        def passivateObject(o: PooledXPathExpression) = ()
        def validateObject(o: PooledXPathExpression) = true
    }

    private class CustomXPathExpression(evaluator: XPathEvaluator, exp: Expression, map: SlotManager, numberOfExternalVariables: Int)
            extends XPathExpression(evaluator, exp) {

        //        private SlotManager slotManager;

        setStackFrameMap(map, numberOfExternalVariables)
        //            this.slotManager = map;

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

    private def withEvaluation[T](xpathString: String, xpathExpression: PooledXPathExpression, locationData: LocationData)(body: ⇒ T): T =
        try body
        catch { case e: Exception ⇒ throw handleXPathException(e, xpathString, "evaluating XPath expression", locationData) }
        finally if (xpathExpression ne null) xpathExpression.returnToPool()

    private def handleXPathException(e: Exception, xpathString: String, description: String, locationData: LocationData) = {
        val validationException = ValidationException.wrapException(e, new ExtendedLocationData(locationData, description, "expression", xpathString))

        // Details of ExtendedLocationData passed are discarded by the constructor for ExtendedLocationData above,
        // so we need to explicitly add them.
        if (locationData.isInstanceOf[ExtendedLocationData])
            validationException.addLocationData(locationData)

        validationException
    }
}