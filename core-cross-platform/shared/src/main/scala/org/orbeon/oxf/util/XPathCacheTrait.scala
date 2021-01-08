package org.orbeon.oxf.util

import java.{util => ju}
import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters._

trait XPathCacheTrait {

  // FIXME: Type duplicate in `XPathTrait`
  // To report timing information
  type Reporter = (String, Long) => Unit

  case class XPathContext(
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData
  )

  object XPathContext {

    def apply(
      ns              : NamespaceMapping                     = NamespaceMapping.EmptyMapping,
      vars            : Map[String, ValueRepresentationType] = Map.empty,
      functionLibrary : FunctionLibrary                      = null,
      functionContext : FunctionContext                      = null,
      baseURI         : String                               = null,
      locationData    : LocationData                         = null,
      reporter        : XPathTrait#Reporter                  = null
    ): XPathContext =
      XPathContext(
        namespaceMapping   = ns,
        variableToValueMap = vars.asJava,
        functionLibrary    = null,
        functionContext    = null,
        baseURI            = null,
        locationData       = null
      )
  }

  // If passed a sequence of size 1, return the contained object. This makes sense since XPath 2 says that "An item is
  // identical to a singleton sequence containing that item." It's easier for callers to switch on the item type.
  def normalizeSingletons(seq: Seq[Any]): Any = if (seq.size == 1) seq.head else seq

  // Evaluate an XPath expression on the document and keep Item objects in the result
  // 2 external usages
  def evaluateKeepItems(
    xpathString     : String,
    contextItem     : om.Item,
    contextPosition : Int = 1)(implicit
    xpathContext    : XPathContext
  ): Seq[om.Item] =
    evaluateKeepItems(
      contextItems       = List(contextItem).asJava,
      contextPosition    = contextPosition,
      xpathString        = xpathString,
      namespaceMapping   = xpathContext.namespaceMapping,
      variableToValueMap = xpathContext.variableToValueMap,
      functionLibrary    = xpathContext.functionLibrary,
      functionContext    = xpathContext.functionContext,
      baseURI            = xpathContext.baseURI,
      locationData       = xpathContext.locationData,
      reporter           = null
    )

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
  ): List[om.Item]

  // Evaluate an XPath expression on the document and return a List of native Java objects (i.e. String, Boolean,
  // etc.), but NodeInfo wrappers are preserved.
  // 7 external usages
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
  ): ju.List[Any] =
    evaluate(
      List(contextItem).asJava,
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
  ): ju.List[Any]
}
