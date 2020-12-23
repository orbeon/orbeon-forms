/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.library

import cats.syntax.option._
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.function._
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.value.StringValue
import org.orbeon.xforms.XFormsNames.XXFORMS_NAMESPACE_URI


/**
 * XForms functions that depend on the XForms environment.
 */
trait XFormsEnvFunctions extends OrbeonFunctionLibrary {

  import XFormsEnvFunctions._

  @XPathFunction
  def index(repeatStaticId: String)(implicit xfc: XFormsFunction.Context): Int =
    findIndexForRepeatId(repeatStaticId)

  @XPathFunction
  def property(propertyName: String)(implicit xpc: XPathContext,  xfc: XFormsFunction.Context): Option[om.Item] = {

    import Property._

//    var arg: Either[(String, String), Map[String, String]] = null
//
//    val uriLocal =
//      arg match {
//        case Left(uriLocal)    => uriLocal
//        case Right(namespaces) =>
//          val propertyNameString = propertyName
//          val qName =
//            Extensions.resolveQName(namespaces, propertyNameString, unprefixedIsNoNamespace = false) getOrElse
//              (throw new XPathException(s"Missing property name"))
//          (qName.namespace.uri, qName.localName)
//      }

    // XXX FIXME hardcoding `xxf` prefix for now
    val uriLocal = {
      SaxonUtils.parseQName(propertyName) match {
        case ("xxf", local) => (XXFORMS_NAMESPACE_URI, local)
        case other => other
      }
    }

    uriLocal match {
      case (_, local) if local.toLowerCase.contains("password") =>
        // Never return any property containing the string "password" as a first line of defense
        None
      case ("", VersionProperty) =>
        StringValue.makeStringValue(Version).some
      case ("", ConformanceLevelProperty) =>
        StringValue.makeStringValue(ConformanceLevel) .some
      case (XXFORMS_NAMESPACE_URI, local) =>
        // Property in the `xxf` namespace: return our properties
        Option(xfc.containingDocument.getProperty(local)) map
          (v => SaxonUtils.convertJavaObjectToSaxonObject(v).asInstanceOf[om.Item])
      case (_, _) =>
        throw new XPathException(s"Unknown property: property('$propertyName')")
    }
  }

  @XPathFunction
  def instance(instanceId: Option[String]): Option[om.Item] = {
    ???
  }

  @XPathFunction
  def current: Option[om.Item] = ???

  @XPathFunction
  def context: Option[om.Item] = ???

  @XPathFunction
  def event(name: String): Option[om.Item] = ???

  // TODO: Should be `Iterator`?
  @XPathFunction
  def valid(items: Iterable[om.Item], relevant: Boolean = true, recurse: Boolean = true): Boolean = ???

  // XForms 2.0
  @XPathFunction
  def bind(bindId: String): Iterable[om.NodeInfo] = ???
//
//  Namespace(XFormsEnvFunctionsNS) {
//    Fun("instance", classOf[Instance], op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("current", classOf[Current], op = 0, min = 0, Type.ITEM_TYPE, ALLOWS_ZERO_OR_ONE)
//
//    Fun("context", classOf[Context], op = 0, min = 0, Type.ITEM_TYPE, ALLOWS_ZERO_OR_ONE)
//
//    Fun("event", classOf[Event], op = 0, min = 1, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//
//    Fun("valid", classOf[XFormsValid], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(BOOLEAN, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
//
//    // XForms 2.0
//    Fun("bind", classOf[Bind], op = 0, min = 1, Type.NODE_TYPE, ALLOWS_ZERO_OR_MORE,
//      Arg(STRING, EXACTLY_ONE)
//    )
//  }
}

object XFormsEnvFunctions {

  object Property {
    val Version                  = "2.0"
    val ConformanceLevel         = "full"
    val VersionProperty          = "version"
    val ConformanceLevelProperty = "conformance-level"
  }

  def findIndexForRepeatId(repeatStaticId: String)(implicit xfc: XFormsFunction.Context): Int =
    xfc.container.getRepeatIndex(xfc.sourceEffectiveIdOrThrow, repeatStaticId) match {
      case Some(index) =>
        index
      case None =>
        // CHECK: Was `BasicLocationData(getSystemId, getLineNumber, getColumnNumber)`.
        throw new OXFException(s"Function index uses repeat id `$repeatStaticId` which is not in scope")
    }
}