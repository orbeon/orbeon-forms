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
package org.orbeon.oxf.xforms.function

import java.{util => ju}

import org.orbeon.oxf.xml.SaxonUtils.parseQName
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.{RuntimeDependentFunction, SaxonUtils}
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.trans.XPathException
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsNames.XXFORMS_NAMESPACE_URI

import scala.jdk.CollectionConverters._

/**
 * XForms property() function.
 *
 * As an extension, supports XForms properties in the xxforms namespace to access XForms engine properties. E.g.:
 *
 * property('xxf:noscript')
 */
private object Property {
  val Version                  = stringToStringValue("1.1")
  val ConformanceLevel         = stringToStringValue("full")
  val VersionProperty          = "version"
  val ConformanceLevelProperty = "conformance-level"
}

class Property extends XFormsFunction with RuntimeDependentFunction {

  import Property._

  private var arg: Either[(String, String), Map[String, String]] = null

  override def evaluateItem(xpathContext: XPathContext): Item = {

    implicit val ctx = xpathContext

    val uriLocal =
      arg match {
        case Left(uriLocal)    => uriLocal
        case Right(namespaces) =>
          val propertyNameString = stringArgument(0)
          val qName =
            Extensions.resolveQName(namespaces.get, propertyNameString, unprefixedIsNoNamespace = false) getOrElse
              (throw new XPathException(s"Missing property name"))
          (qName.namespace.uri, qName.localName)
      }

    uriLocal match {
      case (_, local) if local.toLowerCase.contains("password") =>
        // Never return any property containing the string "password" as a first line of defense
        null
      case ("", VersionProperty) =>
        Version
      case ("", ConformanceLevelProperty) =>
        ConformanceLevel
      case (XXFORMS_NAMESPACE_URI, local) =>
        // Property in the xxforms namespace: return our properties
        Option(XFormsFunction.getContainingDocument.getProperty(local)) map
          (v => SaxonUtils.convertJavaObjectToSaxonObject(v).asInstanceOf[Item]) orNull
      case (_, _) =>
        throw new XPathException(s"Unknown property: `property('${stringArgument(0)}')`")
    }
  }

  // See also Saxon Evaluate.java
  override def checkArguments(visitor: ExpressionVisitor): Unit = {
    super.checkArguments(visitor)
    if (arg eq null) {
      val namespaceResolver = visitor.getStaticContext.getNamespaceResolver
      arg = arguments.head match {
        case sl: StringLiteral =>
          // This is the most common case where the parameter value is known at expression compilation time
          val (prefix, local) = parseQName(sl.getStringValue)
          Left(namespaceResolver.getURIForPrefix(prefix, false) -> local)
        case _ =>
          // NOTE: Event.java has the exact same code in Java
          val pairs =
            for {
              prefix <- namespaceResolver.iteratePrefixes.asInstanceOf[ju.Iterator[String]].asScala
              if prefix != ""
              uri = namespaceResolver.getURIForPrefix(prefix, false)
            } yield
              prefix -> uri

          Right(pairs.toMap)
      }
    }
  }

  // Default behavior: depend on arguments
  // We don't consider that a change to the properties should cause a reevaluation
  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet =
    addSubExpressionsToPathMap(pathMap, pathMapNodeSet)
}