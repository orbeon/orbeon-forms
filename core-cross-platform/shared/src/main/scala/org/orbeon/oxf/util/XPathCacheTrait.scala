package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.datatypes.LocationData
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.xml.NamespaceMapping

import StaticXPath._

import scala.jdk.CollectionConverters._

trait XPathCacheTrait {

  case class XPathContext(
    namespaceMapping   : NamespaceMapping,
    variableToValueMap : ju.Map[String, ValueRepresentationType],
    functionLibrary    : FunctionLibrary,
    functionContext    : XPathTrait#FunctionContext,
    baseURI            : String,
    locationData       : LocationData
  )

  object XPathContext {

    def apply(
      ns              : NamespaceMapping                 = NamespaceMapping.EmptyMapping,
      vars            : Map[String, ValueRepresentationType] = Map.empty,
      functionLibrary : FunctionLibrary                  = null,
      functionContext : XPathTrait#FunctionContext       = null,
      baseURI         : String                           = null,
      locationData    : LocationData                     = null,
      reporter        : XPathTrait#Reporter              = null
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
}
