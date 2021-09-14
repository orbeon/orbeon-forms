package org.orbeon.oxf.util

import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.StaticXPath.{SaxonConfiguration, ValueRepresentationType, makeStringExpression}
import org.orbeon.oxf.util.XPath.{adjustContextItem, newDynamicAndMajorContexts, withFunctionContext}
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.expr.XPathContextMajor
import org.orbeon.saxon.functions.{FunctionLibrary, FunctionLibraryList}
import org.orbeon.saxon.om
import org.orbeon.saxon.om.{SequenceIterator, SequenceTool}
import org.orbeon.saxon.sxpath.{IndependentContext, XPathExpression, XPathVariable}
import org.orbeon.saxon.value.{AtomicValue, ObjectValue, SequenceExtent}
import org.orbeon.scaxon.Implicits
import org.orbeon.xml.NamespaceMapping

import scala.collection.compat._
import java.{util => ju}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


object XPathCache extends XPathCacheTrait {

  private val Explain             = false

  import Private._

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

    XPath.Logger.debug(s"`XPathCache.evaluateSingleKeepItems()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        evaluateImpl(xpathExpression, contextItem, contextPos, contextItems.size, variableToValueMap, variables).next()
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
    XPath.Logger.debug(s"`XPathCache.evaluateAsExtent()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        new SequenceExtent(evaluateImpl(xpathExpression, contextItem, contextPos, contextItems.size, variableToValueMap, variables))
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

    XPath.Logger.debug(s"`XPathCache.evaluateKeepItemsJava()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    if (Explain) {
      import org.orbeon.saxon.lib.StandardLogger

      import _root_.java.io.PrintStream

      xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
    }

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        scalaIteratorToJavaList(Implicits.asScalaIterator(evaluateImpl(xpathExpression, contextItem, contextItems.size, contextPos, variableToValueMap, variables)))
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

    XPath.Logger.debug(s"`XPathCache.evaluateKeepItems()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        Implicits.asScalaIterator(evaluateImpl(xpathExpression, contextItem, contextPos, contextItems.size, variableToValueMap, variables)).toList
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

    XPath.Logger.debug(s"`XPathCache.evaluateAsStringOpt()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        makeStringExpression(xpathString),
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    if (Explain) {
      import org.orbeon.saxon.lib.StandardLogger

      import _root_.java.io.PrintStream

      xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
    }

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        Option(singleItemToJavaKeepNodeInfoOrNull(evaluateImpl(xpathExpression, contextItem, contextPos, contextItems.size, variableToValueMap, variables).next())) map (_.toString)
      }
    }
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
  ): ju.List[Any] = {

    XPath.Logger.debug(s"`XPathCache.evaluate()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = false,
        locationData
      )

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        scalaIteratorToJavaList(
          Implicits.asScalaIterator(evaluateImpl(xpathExpression, contextItem, contextPos, contextItems.size, variableToValueMap, variables)) map
            itemToJavaKeepNodeInfoOrNull
        )
      }
    }
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
    reporter            : Reporter
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

    XPath.Logger.debug(s"`XPathCache.evaluateAsAvt()` for `$xpathString`")

    val (xpathExpression, variables) =
      getXPathExpression(
        XPath.GlobalConfiguration,
        xpathString,
        namespaceMapping,
        variableToValueMap,
        functionLibrary,
        baseURI,
        isAVT = true,
        locationData
      )

    if (Explain) {
      import org.orbeon.saxon.lib.StandardLogger

      import _root_.java.io.PrintStream

      xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
    }

    val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

    withEvaluation(xpathString, locationData, reporter) {
      withFunctionContext(functionContext) {
        evaluateImpl(xpathExpression, contextItem, contextPos, contextItems.size, variableToValueMap, variables).next()
          .getStringValue // *should* always be a `StringValue` in the first place
      }
    }
  }

  def evaluateSingleWithContext(
    xpathContext : XPathContext,
    contextItem  : om.Item,
    xpathString  : String,
    reporter     : Reporter
  ): Any = {
    XPath.Logger.debug(s"NIY: `XPathCache.evaluateSingleWithContext()` for `$xpathString`")
    ???
  }

  private object Private {

    // This is the in-memory XPath exprssion cache, simply implemented as a `Map`
    private var cache = Map[String, (XPathExpression, IndependentContext)]()

    def evaluateImpl(
      expression         : XPathExpression,
      contextItem        : om.Item,
      contextPosition    : Int,
      contextSize        : Int,
      variableToValueMap : ju.Map[String, ValueRepresentationType],
      variables          : List[(String, XPathVariable)]
    ): SequenceIterator = {
      val (dynamicContext, xpathContext) =
        newDynamicAndMajorContexts(expression, contextItem, contextPosition, contextSize)
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
      xpathString        : String,
      namespaceMapping   : NamespaceMapping,
      variableToValueMap : ju.Map[String, ValueRepresentationType],
      functionLibrary    : FunctionLibrary,
      baseURI            : String,
      isAVT              : Boolean,
      locationData       : LocationData
    ): (XPathExpression, List[(String, XPathVariable)]) = {
      try {

        val cacheKeyBuilder = new StringBuilder(xpathString)

        if (functionLibrary ne null) {// This is ok
          cacheKeyBuilder.append('|')
          cacheKeyBuilder.append(functionLibrary.hashCode.toString) // works only if the `FunctionLibrary` is not recreated!
        }

        // NOTE: Mike Kay confirms on 2007-07-04 that compilation depends on the namespace context, so we need
        // to use it as part of the cache key.
        if (namespaceMapping ne null) {
          // NOTE: Hash is mandatory in NamespaceMapping
          cacheKeyBuilder.append('|')
          cacheKeyBuilder.append(namespaceMapping.hash)
        }

        // NOTE: Make sure to copy the values in the key set, as the set returned by the map keeps a pointer to the
        // Map! This can cause the XPath cache to keep a reference to variable values, which in turn can keep a
        // reference all the way to e.g. an XFormsContainingDocument.
        val variableNames = Option(variableToValueMap) map (_.keySet.asScala.toList) getOrElse List()

        if (variableNames.nonEmpty) {
          // There are some variables in scope. They must be part of the key
          // TODO: Put this in static state as this can be determined statically once and for all
          for (variableName <- variableNames) {
            cacheKeyBuilder.append('|')
            cacheKeyBuilder.append(variableName)
          }
        }

        // Add this to the key as evaluating "name" as XPath or as AVT is very different!
        cacheKeyBuilder.append('|')
        cacheKeyBuilder.append(isAVT.toString)

        // MAYBE: Add baseURI to cache key (currently, baseURI is pretty much unused)

        val cacheKeyString = cacheKeyBuilder.toString

        val (expr, independentContext) =
          cache.getOrElse(cacheKeyString, {

            // 2021-01-13: Only use of `IndependentContext`. We use `ShareableXPathStaticContext` for
            // `StaticXPath` and `XPath`.
            val independentContext = new IndependentContext(configuration)
//            independentContext.getConfiguration.setURIResolver(XPath.URIResolver)

            // Set the base URI if specified
            if (baseURI ne null)
              independentContext.setBaseURI(baseURI)

            // Declare namespaces
            if (namespaceMapping ne null)
              for ((prefix, uri) <- namespaceMapping.mapping)
                independentContext.declareNamespace(prefix, uri)

            // Declare variables
            variableNames foreach (independentContext.declareVariable("", _))

            // Add function library
            if (functionLibrary ne null)
              independentContext.getFunctionLibrary.asInstanceOf[FunctionLibraryList].libraryList.add(0, functionLibrary)

            val expr = StaticXPath.compileExpressionWithStaticContext(independentContext, xpathString, isAVT)

            // Store in cache as side-effect
            cache += cacheKeyString -> (expr, independentContext)

            (expr, independentContext)
          })

        val variables: List[(String, XPathVariable)] =
          if (variableNames ne null)
            for {
              name <- variableNames
              variable = independentContext.declareVariable("", name)
            } yield
              name -> variable
          else
            Nil

        (expr, variables)
      } catch {
        case NonFatal(t) => throw handleXPathException(t, xpathString, "preparing XPath expression", locationData)
      }
    }

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
