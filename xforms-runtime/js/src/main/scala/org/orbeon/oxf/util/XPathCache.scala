package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.StaticXPath.{SaxonConfiguration, ValueRepresentationType, makeStringExpression}
import org.orbeon.oxf.util.XPath.withFunctionContext
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.expr.XPathContextMajor
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om
import org.orbeon.saxon.om.{SequenceIterator, SequenceTool}
import org.orbeon.saxon.sxpath.{IndependentContext, XPathDynamicContext, XPathExpression, XPathVariable}
import org.orbeon.saxon.value.{AtomicValue, ObjectValue, SequenceExtent}
import org.orbeon.scaxon.Implicits
import org.orbeon.xml.NamespaceMapping

import scala.collection.compat._
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


object XPathCache extends XPathCacheTrait {

  private val Explain = false

  import Private._

  // If passed a sequence of size 1, return the contained object. This makes sense since XPath 2 says that "An item is
  // identical to a singleton sequence containing that item." It's easier for callers to switch on the item type.
  def normalizeSingletons(seq: Seq[AnyRef]): AnyRef = if (seq.size == 1) seq.head else seq

  def evaluateSingleKeepItems(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): om.Item = {

    XPath.Logger.debug(s"xxx XPathCache.evaluateSingleKeepItems for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        contextItems,
        contextPosition,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = getContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        evaluateImpl(xpathExpression, contextItem, contextPos, variableToValueMap, variables).next()
      }
    }
  }

  def evaluateAsExtent(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): SequenceExtent = {
    XPath.Logger.debug(s"xxx XPathCache.evaluateAsExtent for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        contextItems,
        contextPosition,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = getContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        new SequenceExtent(evaluateImpl(xpathExpression, contextItem, contextPos, variableToValueMap, variables))
      }
    }
  }

  def evaluateKeepItemsJava(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): ju.List[om.Item] = {
    XPath.Logger.debug(s"xxx XPathCache.evaluateKeepItemsJava for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        contextItems,
        contextPosition,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = getContextItem(contextItems, contextPosition)

    XPath.Logger.debug(s"xxx  contextItem, contextPos: $contextItem, $contextPos")

    if (Explain) {
      import _root_.java.io.PrintStream
      import org.orbeon.saxon.lib.StandardLogger

      xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
    }

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        val r = scalaIteratorToJavaList(Implicits.asScalaIterator(evaluateImpl(xpathExpression, contextItem, contextPos, variableToValueMap, variables)))

        XPath.Logger.debug(s"xxxx ${r.size}")

        r
      }
    }
  }

  def evaluateKeepItems(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): List[om.Item] = {
    XPath.Logger.debug(s"xxx XPathCache.evaluateKeepItems 2 for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        contextItems,
        contextPosition,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = getContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        val r = Implicits.asScalaIterator(evaluateImpl(xpathExpression, contextItem, contextPos, variableToValueMap, variables)).toList

        XPath.Logger.debug(s"xxxx ${r.size}")

        r
      }
    }
  }

  def evaluateAsStringOpt(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): Option[String] = {
    XPath.Logger.debug(s"xxx XPathCache.evaluateAsStringOpt for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        contextItems,
        contextPosition,
        makeStringExpression(xpathString),
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    if (Explain) {
      import _root_.java.io.PrintStream
      import org.orbeon.saxon.lib.StandardLogger

      xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
    }

    val (contextItem, contextPos) = getContextItem(contextItems, contextPosition)

    XPath.Logger.debug(s"xxx  contextItem, contextPos: $contextItem, $contextPos")

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        val r =
          Option(singleItemToJavaKeepNodeInfoOrNull(evaluateImpl(xpathExpression, contextItem, contextPos, variableToValueMap, variables).next())) map (_.toString)
        XPath.Logger.debug(s"xxx result = $r")

        r
      }
    }
  }

  def evaluate(
    contextItem        : om.Item,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): ju.List[AnyRef] = {
    XPath.Logger.debug(s"xxx XPathCache.evaluate for `$xpathString`")
    ???
  }

  def evaluate(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): ju.List[AnyRef] = {
    XPath.Logger.debug(s"xxx XPathCache.evaluate 2 for `$xpathString`")
    ???
  }

  def evaluateAsAvt(
    xpathContext : XPathContext,
    contextItem  : om.Item,
    xpathString  : String,
    reporter     : Reporter
  ): String =
    evaluateAsAvt(
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

  def evaluateAsAvt(
    contextItem         : om.Item,
    xpathString         : String,
    namespaceMapping    : NamespaceMapping,
    variableToValueMap  : ju.Map[String, ValueRepresentationType],
    functionLibrary     : FunctionLibrary,
    functionContext     : FunctionContext,
    baseURI             : String,
    locationData        : LocationData,
    reporter: Reporter
  ): String =
    evaluateAsAvt(
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

  def evaluateAsAvt(
    contextItems       : ju.List[om.Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ) : String = {
    XPath.Logger.debug(s"xxx XPathCache.evaluateAsAvt for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        contextItems,
        contextPosition,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = true,
        locationData
      )

    if (Explain) {
      import _root_.java.io.PrintStream
      import org.orbeon.saxon.lib.StandardLogger

      xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
    }

    val (contextItem, contextPos) = getContextItem(contextItems, contextPosition)

    XPath.Logger.debug(s"xxx  contextItem, contextPos: $contextItem, $contextPos")

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        val r =
          evaluateImpl(xpathExpression, contextItem, contextPos, variableToValueMap, variables).next()
        XPath.Logger.debug(s"xxx result = $r")

        r.getStringValue // it *should* always be a `StringValue` in the first place
      }
    }
  }

  def evaluateSingleWithContext(
    xpathContext : XPathContext,
    contextItem  : om.Item,
    xpathString  : String,
    reporter     : Reporter
  ): AnyRef = {
    println(s"xxx XPathCache.evaluateSingleWithContext for `$xpathString`")
    ???
  }

    private def newDynamicAndMajorContexts(
    expression      : XPathExpression,
    contextItem     : om.Item,
    contextPosition : Int
  ): (XPathDynamicContext, XPathContextMajor) = {
    val dynamicContext = expression.createDynamicContext(contextItem) // XXX TODO: `contextPosition`
    (dynamicContext, dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor])
  }

  private object Private {

    def evaluateImpl(
      expression         : XPathExpression,
      contextItem        : om.Item,
      contextPosition    : Int,
      variableToValueMap : ju.Map[String, ValueRepresentationType],
      variables          : List[(String, XPathVariable)]
    ): SequenceIterator = {
      val (dynamicContext, xpathContext) = newDynamicAndMajorContexts(expression, contextItem, contextPosition)
      prepareDynamicContext(xpathContext, variableToValueMap, variables)
      expression.iterate(dynamicContext)
    }

    def prepareDynamicContext(
      xpathContext       : XPathContextMajor,
      variableToValueMap : ju.Map[String, ValueRepresentationType],
      variables          : List[(String, XPathVariable)]
    ): Unit =
      if (variableToValueMap ne null)
        for ((name, variable) <- variables) {
          val value = variableToValueMap.get(name)
          if (value ne null) // FIXME: this should never happen, right?
            xpathContext.setLocalVariable(variable. getLocalSlotNumber, value)
        }

    def scalaIteratorToJavaList[T](i: Iterator[T]): ju.List[T] =
      new ju.ArrayList(i.to(mutable.ArrayBuffer).asJava)

    def getXPathExpression(
      configuration      : SaxonConfiguration,
      contextItems       : ju.List[om.Item],
      contextPosition    : Int,
      xpathString        : String,
      namespaceMapping   : NamespaceMapping,
      variableToValueMap : ju.Map[String, ValueRepresentationType],
      functionLibrary    : FunctionLibrary,
      baseURI            : String,
      isAVT              : Boolean,
      locationData       : LocationData
    ): (XPathExpression, List[(String, XPathVariable)]) = {
      try {
        // Find pool from cache
  //      val validity = 0L
  //      val cache = ObjectCache.instance(XPathCacheName, XPathCacheDefaultSize)
  //      val cacheKeyString = new StringBuilder(xpathString)
  //
  //      if (functionLibrary ne null) {// This is ok
  //        cacheKeyString.append('|')
  //        cacheKeyString.append(functionLibrary.hashCode.toString)
  //      }
  //      // NOTE: Mike Kay confirms on 2007-07-04 that compilation depends on the namespace context, so we need
  //      // to use it as part of the cache key.
  //      if (namespaceMapping ne null) {
  //        // NOTE: Hash is mandatory in NamespaceMapping
  //        cacheKeyString.append('|')
  //        cacheKeyString.append(namespaceMapping.hash)
  //      }

        // NOTE: Make sure to copy the values in the key set, as the set returned by the map keeps a pointer to the
        // Map! This can cause the XPath cache to keep a reference to variable values, which in turn can keep a
        // reference all the way to e.g. an XFormsContainingDocument.
        val variableNames = Option(variableToValueMap) map (_.keySet.asScala.toList) getOrElse List()

  //      if (variableNames.nonEmpty) {
  //        // There are some variables in scope. They must be part of the key
  //        // TODO: Put this in static state as this can be determined statically once and for all
  //        for (variableName <- variableNames) {
  //          cacheKeyString.append('|')
  //          cacheKeyString.append(variableName)
  //        }
  //      }
  //
  //      // Add this to the key as evaluating "name" as XPath or as AVT is very different!
  //      cacheKeyString.append('|')
  //      cacheKeyString.append(isAVT.toString)

        // TODO: Add baseURI to cache key (currently, baseURI is pretty much unused)
  //
  //      val pooledXPathExpression = {
  //        val cacheKey = new InternalCacheKey("XPath Expression2", cacheKeyString.toString)
  //        var pool = cache.findValid(cacheKey, validity).asInstanceOf[ObjectPool[PooledXPathExpression]]
  //        if (pool eq null) {
  //          pool = createXPathPool(configuration, xpathString, namespaceMapping, variableNames, functionLibrary, baseURI, isAVT, locationData)
  //          cache.add(cacheKey, validity, pool)
  //        }
  //        // Get object from pool
  //        pool.borrowObject
  //      }


        val independentContext = new IndependentContext(configuration)
  //          independentContext.getConfiguration.setURIResolver(XPath.URIResolver)

        // Set the base URI if specified
        if (baseURI ne null)
          independentContext.setBaseURI(baseURI)

        // Declare namespaces
        if (namespaceMapping ne null)
          for ((prefix, uri) <- namespaceMapping.mapping)
            independentContext.declareNamespace(prefix, uri)

        // Declare variables (we don't use the values here, just the names)
        val variables: List[(String, XPathVariable)] =
          if (variableNames ne null)
            for {
              name <- variableNames
              variable = independentContext.declareVariable("", name)
            } yield
              name -> variable
          else
            Nil

        // Add function library
        if (functionLibrary ne null)
          independentContext.getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.add(0, functionLibrary)

        val expr = StaticXPath.compileExpressionWithStaticContext(independentContext, xpathString, isAVT)

        // Set context items and position
  //      pooledXPathExpression.setContextItems(contextItems, contextPosition)
  //
  //      // Set variables
  //      pooledXPathExpression.setVariables(variableToValueMap)
  //
  //      pooledXPathExpression
        (expr, variables)
      } catch {
        case NonFatal(t) => throw handleXPathException(t, xpathString, "preparing XPath expression", locationData)
      }
    }

    def getContextItem(contextItems: ju.List[om.Item], contextPosition: Int): (om.Item, Int) =
      if (contextPosition > 0 && contextPosition <= contextItems.size)
        (contextItems.get(contextPosition - 1), contextPosition)
      else
        (null, 0)

    def handleXPathException(t: Throwable, xpathString: String, description: String, locationData: LocationData): ValidationException = {

      val validationException =
        OrbeonLocationException.wrapException(
          t,
          XmlExtendedLocationData(locationData, Option(description), List("expression" -> xpathString))
        )

      // Details of ExtendedLocationData passed are discarded by the constructor for ExtendedLocationData above,
      // so we need to explicitly add them.
      if (locationData.isInstanceOf[ExtendedLocationData])
        validationException.addLocationData(locationData)

      validationException
    }

    def withEvaluation[T](xpathString: String, locationData: LocationData, reporter: Reporter)(body: => T): T =
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
        case NonFatal(t) =>
          throw handleXPathException(t, xpathString, "evaluating XPath expression", locationData)
      }

    def singleItemToJavaKeepNodeInfoOrNull(item: om.Item): Any = item match {
      case null => null
      case item => itemToJavaKeepNodeInfoOrNull(item)
    }

    def itemToJavaKeepNodeInfoOrNull(item: om.Item): Any =
      item match {
        case v: ObjectValue[_] => v // don't convert for `Array` and `Map` types
        case v: AtomicValue    => SequenceTool.convertToJava(v)
        case v                 => v
      }
  }
}
