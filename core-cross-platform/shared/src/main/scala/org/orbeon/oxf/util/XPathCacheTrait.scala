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
}
