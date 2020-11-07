package org.orbeon.oxf.util

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.util.XPath.{FunctionContext, Reporter}
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.value.SequenceExtent
import java.{util => ju}

import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType

object XPathCache {

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

}
