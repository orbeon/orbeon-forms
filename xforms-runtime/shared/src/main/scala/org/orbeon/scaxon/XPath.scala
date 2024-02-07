/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.scaxon

import org.orbeon.oxf.util.FunctionContext
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.util.XPath.Reporter
import org.orbeon.oxf.util.XPathCache.{evaluate, evaluateAsAvt, evaluateSingleKeepItems}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters._


// Convenience methods for `the `XPathCache` API
// Q: Do we need those? Why not just use `XPathCache` directly?
object XPath {

  def evalOne(
      item            : om.Item,
      expr            : String,
      namespaces      : NamespaceMapping                                = NamespaceMapping.EmptyMapping,
      variables       : collection.Map[String, ValueRepresentationType] = null,
      reporter        : Reporter                                        = null,
      functionContext : FunctionContext                                 = null)(
      implicit library: FunctionLibrary                                 = null
  ): om.Item =
    evaluateSingleKeepItems(
      List(item).asJava,
      1,
      expr,
      namespaces,
      if (variables eq null) null else variables.asJava,
      library,
      functionContext,
      null,
      null,
      reporter
    )

  // Evaluate an XPath expression and return a Seq of native Java objects (String, Boolean, etc.), but NodeInfo
  // wrappers are preserved.
  def eval(
      item            : om.Item,
      expr            : String,
      namespaces      : NamespaceMapping                                = NamespaceMapping.EmptyMapping,
      variables       : collection.Map[String, ValueRepresentationType] = null,
      reporter        : Reporter                                        = null,
      functionContext : FunctionContext                                 = null)(
      implicit library: FunctionLibrary                                 = null
  ): collection.Seq[Any] =
    evaluate(item,
      expr,
      namespaces,
      if (variables eq null) null else variables.asJava,
      library,
      functionContext,
      null,
      null,
      reporter
    ).asScala

    // Evaluate an XPath expression as a value template
  def evalValueTemplate(
      item            : om.Item,
      expr            : String,
      namespaces      : NamespaceMapping                                = NamespaceMapping.EmptyMapping,
      variables       : collection.Map[String, ValueRepresentationType] = null,
      reporter        : Reporter                                        = null,
      functionContext : FunctionContext                                 = null)(
      implicit library: FunctionLibrary                                 = null
  ): String =
    evaluateAsAvt(
      item,
      expr,
      namespaces,
      if (variables eq null) null else variables.asJava,
      library,
      functionContext,
      null,
      null,
      reporter
    )
}
