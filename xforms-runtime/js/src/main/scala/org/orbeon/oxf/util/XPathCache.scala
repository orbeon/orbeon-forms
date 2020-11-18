package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.XPath.{FunctionContext, Reporter}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.om.{Item, ValueRepresentation}
import org.orbeon.saxon.value.SequenceExtent
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters._

object XPathCache extends XPathCacheTrait {

  // If passed a sequence of size 1, return the contained object. This makes sense since XPath 2 says that "An item is
  // identical to a singleton sequence containing that item." It's easier for callers to switch on the item type.
  def normalizeSingletons(seq: Seq[AnyRef]): AnyRef = if (seq.size == 1) seq.head else seq

  def evaluateSingleKeepItems(
    contextItems       : ju.List[Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): Item = ???

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
  ): SequenceExtent = ???

  def evaluateKeepItemsJava(
    contextItems       : ju.List[Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): ju.List[Item] = ???

  def evaluateKeepItems(
    xpathString     : String,
    contextItem     : Item,
    contextPosition : Int = 1)(implicit
    xpathContext    : XPathContext
  ): Seq[Item] = ???

  def evaluateKeepItems(
    contextItems       : ju.List[Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): List[Item] = ???

  def evaluateAsStringOpt(
    contextItems       : ju.List[Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): Option[String] = ???

  def evaluate(
    contextItem        : Item,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): ju.List[AnyRef] = ???

  def evaluate(
    contextItems       : ju.List[Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ): ju.List[AnyRef] = ???

  def evaluateAsAvt(
    xpathContext : XPathContext,
    contextItem  : Item,
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
    contextItem         : Item,
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
    contextItems       : ju.List[Item],
    contextPosition    : Int,
    xpathString        : String,
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : FunctionContext,
    baseURI            : String,
    locationData       : LocationData,
    reporter           : Reporter
  ) : String = ???

  def evaluateSingleWithContext(
    xpathContext : XPathContext,
    contextItem  : Item,
    xpathString  : String,
    reporter     : Reporter
  ): AnyRef = ???
}
