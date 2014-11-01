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

import collection.JavaConverters._
import java.util.{List ⇒ JList, Map ⇒ JMap}
import org.apache.commons.pool.{BasePoolableObjectFactory, ObjectPool}
import org.orbeon.oxf.cache.InternalCacheKey
import org.orbeon.oxf.cache.ObjectCache
import org.orbeon.oxf.xml.dom4j.LocationData
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.functions.FunctionLibraryList
import org.orbeon.saxon.om.{Item, ValueRepresentation}
import org.orbeon.saxon.sxpath._
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.value.SequenceExtent
import scala.util.control.NonFatal

/**
 * XPath expressions cache.
 */
object XPathCache {

    import XPath._

    private val XPathCacheName = "cache.xpath"
    private val XPathCacheDefaultSize = 200
    
    private val Logger = LoggerFactory.createLogger(getClass)
    
    case class XPathContext(
        namespaceMapping   : NamespaceMapping,
        variableToValueMap : JMap[String, ValueRepresentation],
        functionLibrary    : FunctionLibrary,
        functionContext    : FunctionContext,
        baseURI            : String,
        locationData       : LocationData
    )

    def isDynamicXPathError(t: Throwable) = t match {
        case e: XPathException if ! e.isStaticError ⇒ true
        case _ ⇒ false
    }
    // Evaluate an XPath expression on the document and return a List of native Java objects (i.e. String, Boolean,
    // etc.), but NodeInfo wrappers are preserved.
    // 7 external usages
    def evaluate(
            contextItem        : Item,
            xpathString        : String,
            namespaceMapping   : NamespaceMapping,
            variableToValueMap : JMap[String, ValueRepresentation],
            functionLibrary    : FunctionLibrary,
            functionContext    : FunctionContext,
            baseURI            : String,
            locationData       : LocationData,
            reporter           : Reporter
    ): JList[AnyRef] =
        evaluate(
            Seq(contextItem).asJava,
            1,
            xpathString,
            namespaceMapping,
            variableToValueMap,
            functionLibrary,
            functionContext,
            baseURI,
            locationData,
            reporter
        )

    // Evaluate an XPath expression on the document and return a List of native Java objects (i.e. String, Boolean,
    // etc.), but NodeInfo wrappers are preserved.
    // 2 external usages
    def evaluate(
        contextItems       : JList[Item],
        contextPosition    : Int,
        xpathString        : String,
        namespaceMapping   : NamespaceMapping,
        variableToValueMap : JMap[String, ValueRepresentation],
        functionLibrary    : FunctionLibrary,
        functionContext    : FunctionContext,
        baseURI            : String,
        locationData       : LocationData,
        reporter           : Reporter
    ): JList[AnyRef] = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, baseURI, isAVT = false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            xpathExpression.evaluateKeepNodeInfo(functionContext)
        }
    }

    // If passed a sequence of size 1, return the contained object. This makes sense since XPath 2 says that "An item is
    // identical to a singleton sequence containing that item." It's easier for callers to switch on the item time.
    def normalizeSingletons(seq: Seq[AnyRef]): AnyRef = if (seq.size == 1) seq(0) else seq

    // Evaluate an XPath expression on the document and keep Item objects in the result
    // 4 external usages
    def evaluateKeepItems(
        contextItems       : JList[Item],
        contextPosition    : Int,
        xpathString        : String,
        namespaceMapping   : NamespaceMapping,
        variableToValueMap : JMap[String, ValueRepresentation],
        functionLibrary    : FunctionLibrary,
        functionContext    : FunctionContext,
        baseURI            : String,
        locationData       : LocationData,
        reporter           : Reporter
    ): JList[Item] = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, baseURI, isAVT = false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            xpathExpression.evaluateKeepItems(functionContext)
        }
    }

    // Evaluate an XPath expression on the document and keep Item objects in the result
    // 1 external usage
    def evaluateSingleKeepItems(
        contextItems       : JList[Item],
        contextPosition    : Int,
        xpathString        : String,
        namespaceMapping   : NamespaceMapping,
        variableToValueMap : JMap[String, ValueRepresentation],
        functionLibrary    : FunctionLibrary,
        functionContext    : FunctionContext,
        baseURI            : String,
        locationData       : LocationData,
        reporter           : Reporter
    ): Item = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap, functionLibrary, baseURI, isAVT = false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            xpathExpression.evaluateSingleKeepItemOrNull(functionContext)
        }
    }

    // Evaluate the expression as a variable value usable by Saxon in further XPath expressions
    // 1 external usage
    def evaluateAsExtent(
        contextItems       : JList[Item],
        contextPosition    : Int,
        xpathString        : String,
        namespaceMapping   : NamespaceMapping,
        variableToValueMap : JMap[String, ValueRepresentation],
        functionLibrary    : FunctionLibrary,
        functionContext    : FunctionContext,
        baseURI            : String,
        locationData       : LocationData,
        reporter           : Reporter
    ): SequenceExtent = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap,
                functionLibrary, baseURI, isAVT = false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            xpathExpression.evaluateAsExtent(functionContext)
        }
    }

    // Evaluate an XPath expression on the document
    // 2 external usages
    def evaluateSingle(
        xpathContext : XPathContext,
        contextItem  : Item,
        xpathString  : String,
        reporter     : Reporter
    ): AnyRef =
        evaluateSingle(
            Seq(contextItem).asJava,
            1,
            xpathString,
            xpathContext.namespaceMapping,
            xpathContext.variableToValueMap,
            xpathContext.functionLibrary,
            xpathContext.functionContext,
            xpathContext.baseURI,
            xpathContext.locationData,
            reporter
        )

    // Evaluate an XPath expression on the document
    // 2 external usages
    def evaluateSingle(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData,
            reporter: Reporter): AnyRef =
        evaluateSingle(
            Seq(contextItem).asJava, 1, xpathString, namespaceMapping, variableToValueMap,
            functionLibrary, functionContext, baseURI, locationData, reporter)

    // Evaluate an XPath expression on the document
    // 2 external usages
    def evaluateSingle(
        contextItems: JList[Item],
        contextPosition: Int,
        xpathString: String,
        namespaceMapping: NamespaceMapping,
        variableToValueMap: JMap[String, ValueRepresentation],
        functionLibrary: FunctionLibrary,
        functionContext: FunctionContext,
        baseURI: String,
        locationData: LocationData,
        reporter: Reporter
    ) = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap,
                functionLibrary, baseURI, isAVT = false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            xpathExpression.evaluateSingleKeepNodeInfoOrNull(functionContext)
        }
    }

    // Evaluate an XPath expression on the document as an attribute value template, and return its string value
    // 1 external usage
    def evaluateAsAvt(xpathContext: XPathContext, contextItem: Item, xpathString: String, reporter: Reporter): String =
        evaluateAsAvt(
            Seq(contextItem).asJava, 1, xpathString, xpathContext.namespaceMapping,
            xpathContext.variableToValueMap, xpathContext.functionLibrary, xpathContext.functionContext,
            xpathContext.baseURI, xpathContext.locationData, reporter)

    // Evaluate an XPath expression on the document as an attribute value template, and return its string value
    // 1 external usage
    def evaluateAsAvt(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData,
            reporter: Reporter): String =
        evaluateAsAvt(
            Seq(contextItem).asJava, 1, xpathString, namespaceMapping, variableToValueMap,
            functionLibrary, functionContext, baseURI, locationData, reporter)

    // Evaluate an XPath expression on the document as an attribute value template, and return its string value
    // 3 external usages
    def evaluateAsAvt(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData,
            reporter: Reporter): String = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, xpathString, namespaceMapping,
                variableToValueMap,
                functionLibrary, baseURI, isAVT = true, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            Option(xpathExpression.evaluateSingleKeepNodeInfoOrNull(functionContext)) map (_.toString) orNull // FIXME: can ever return null?
        }
    }

    // Evaluate an XPath expression and return its string value
    // 3 external usages
    def evaluateAsString(
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData,
            reporter: Reporter): String =
        evaluateAsString(
            Seq(contextItem).asJava, 1, xpathString, namespaceMapping, variableToValueMap,
            functionLibrary, functionContext, baseURI, locationData, reporter)

    // Evaluate an XPath expression and return its string value
    // 6 external usages
    def evaluateAsString(
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            functionContext: FunctionContext,
            baseURI: String,
            locationData: LocationData,
            reporter: Reporter): String = {

        val xpathExpression =
            getXPathExpression(
                XPath.GlobalConfiguration, contextItems, contextPosition, makeStringExpression(xpathString),
                namespaceMapping, variableToValueMap, functionLibrary, baseURI, isAVT = false, locationData)

        withEvaluation(xpathString, xpathExpression, locationData, reporter) {
            Option(xpathExpression.evaluateSingleKeepNodeInfoOrNull(functionContext)) map (_.toString) orNull
        }
    }

    // No call from XForms
    def getXPathExpression(
        configuration: Configuration, contextItem: Item, xpathString: String,
        locationData: LocationData): PooledXPathExpression =
        getXPathExpression(configuration, contextItem, xpathString, null, null, null, null, locationData)

    // No call from XForms
    def getXPathExpression(
            configuration: Configuration,
            contextItem: Item,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            locationData: LocationData): PooledXPathExpression =
        getXPathExpression(configuration, contextItem, xpathString, namespaceMapping, null, null, null, locationData)

    // No call from XForms
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
            isAVT = false, locationData)

    private def getXPathExpression(
            configuration: Configuration,
            contextItems: JList[Item],
            contextPosition: Int,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableToValueMap: JMap[String, ValueRepresentation],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            isAVT: Boolean,
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

            // NOTE: Make sure to copy the values in the key set, as the set returned by the map keeps a pointer to the
            // Map! This can cause the XPath cache to keep a reference to variable values, which in turn can keep a
            // reference all the way to e.g. an XFormsContainingDocument.
            val variableNames = Option(variableToValueMap) map (_.keySet.asScala.toList) getOrElse List()

            if (variableNames.size > 0) {
                // There are some variables in scope. They must be part of the key
                // TODO: Put this in static state as this can be determined statically once and for all
                for (variableName ← variableNames) {
                    cacheKeyString.append('|')
                    cacheKeyString.append(variableName)
                }
            }

            // Add this to the key as evaluating "name" as XPath or as AVT is very different!
            cacheKeyString.append('|')
            cacheKeyString.append(isAVT.toString)

            // TODO: Add baseURI to cache key (currently, baseURI is pretty much unused)

            val pooledXPathExpression = {
                val cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString.toString)
                var pool = cache.findValid(cacheKey, validity).asInstanceOf[ObjectPool[PooledXPathExpression]]
                if (pool eq null) {
                    pool = createXPathPool(configuration, xpathString, namespaceMapping, variableNames, functionLibrary, baseURI, isAVT, locationData)
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
            case NonFatal(t) ⇒ throw handleXPathException(t, xpathString, "preparing XPath expression", locationData)
        }
    }

    private def createXPathPool(
            xpathConfiguration: Configuration,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableNames: List[String],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            isAVT: Boolean,
            locationData: LocationData): ObjectPool[PooledXPathExpression] = {

        // TODO: pool should have at least one hard reference
        val factory = new XPathCachePoolableObjectFactory(
            configurationOrDefault(xpathConfiguration), xpathString, namespaceMapping, variableNames,
            functionLibrary, baseURI, isAVT, locationData)
        val pool = new SoftReferenceObjectPool(factory)
        factory.pool = pool
        pool
    }

    def createPoolableXPathExpression(
        independentContext : IndependentContext,
        xpathString        : String,
        isAVT              : Boolean,
        pool               : ObjectPool[PooledXPathExpression],
        variables          : List[(String, XPathVariable)]
    ): PooledXPathExpression =
        new PooledXPathExpression(
            compileExpressionWithStaticContext(independentContext, xpathString, isAVT),
            pool,
            variables
        )

    // Not sure if/when configuration can be null, but it shouldn't be
    private def configurationOrDefault(configuration: Configuration) =
        Option(configuration) getOrElse XPath.GlobalConfiguration

    private class XPathCachePoolableObjectFactory(
            xpathConfiguration: Configuration,
            xpathString: String,
            namespaceMapping: NamespaceMapping,
            variableNames: List[String],
            functionLibrary: FunctionLibrary,
            baseURI: String,
            isAVT: Boolean,
            locationData: LocationData)
        extends BasePoolableObjectFactory[PooledXPathExpression] {

        // NOTE: storing the FunctionLibrary in cache is ok if it doesn't hold dynamic references (case of global XFormsFunctionLibrary)

        var pool: ObjectPool[PooledXPathExpression] = _

        // Create and compile an XPath expression object
        def makeObject: PooledXPathExpression = {
            if (Logger.isDebugEnabled)
                Logger.debug("makeObject(" + xpathString + ")")
            
            // Create context
            val independentContext = new IndependentContext(xpathConfiguration)
            independentContext.getConfiguration.setURIResolver(XPath.URIResolver)
            
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
                        name ← variableNames
                        variable = independentContext.declareVariable("", name)
                    } yield
                        name → variable
                else
                    Nil
            
            // Add function library
            if (functionLibrary ne null)
                independentContext.getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.asInstanceOf[JList[FunctionLibrary]].add(0, functionLibrary)
            
            createPoolableXPathExpression(independentContext, xpathString, isAVT, pool, variables)
        }

        override def destroyObject(o: PooledXPathExpression): Unit = ()
    }

    private def withEvaluation[T](xpathString: String, xpathExpression: PooledXPathExpression, locationData: LocationData, reporter: Reporter)(body: ⇒ T): T =
        try {
            if (reporter ne null) {
                val startTime = System.nanoTime
                val result = body
                val totalTimeMicroSeconds = (System.nanoTime - startTime) / 1000 // never smaller than 1000 ns on OS X
                if (totalTimeMicroSeconds > 0)
                    reporter(xpathString, totalTimeMicroSeconds)

                result
            } else
                body
        } catch {
            case NonFatal(t) ⇒
                throw handleXPathException(t, xpathString, "evaluating XPath expression", locationData)
        } finally
            xpathExpression.returnToPool()
}