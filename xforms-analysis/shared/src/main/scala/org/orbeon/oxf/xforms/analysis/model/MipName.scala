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
package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.dom._
import org.orbeon.xforms.XFormsNames._


sealed trait MipName { def name: String; val aName: QName; val eName: QName }

object MipName {

  // MIP enumeration
  sealed trait Std extends MipName { val name: String; val aName = QName(name);    val eName = xfQName(name) }
  sealed trait Ext extends MipName { val name: String; val aName = xxfQName(name); val eName = xxfQName(name) }

  sealed trait Computed      extends MipName
  sealed trait Validate      extends MipName
  sealed trait XPath         extends MipName
  sealed trait BooleanXPath  extends XPath
  sealed trait StringXPath   extends XPath

  // NOTE: `required` is special: it is evaluated during recalculate, but used during revalidate. In effect both
  // recalculate AND revalidate depend on it. Ideally maybe revalidate would depend on the the *value* of the
  // `required` MIP, not on the XPath of it. See also what we would need for `valid()`, etc. functions.
  case object Relevant     extends { val name = "relevant"   } with Std with BooleanXPath with Computed
  case object Readonly     extends { val name = "readonly"   } with Std with BooleanXPath with Computed
  case object Required     extends { val name = "required"   } with Std with BooleanXPath with Computed with Validate
  case object Constraint   extends { val name = "constraint" } with Std with BooleanXPath with Validate

  case object Calculate    extends { val name = "calculate"  } with Std with StringXPath  with Computed
  case object Default      extends { val name = "default"    } with Ext with StringXPath  with Computed
  case object Type         extends { val name = "type"       } with Std with Validate
  case object Whitespace   extends { val name = "whitespace" } with Ext with Computed

  case class Custom(qName: QName) extends StringXPath { // with `ComputedMIP`?
    def name = buildInternalCustomMIPName(qName)
    val aName: QName = qName
    val eName: QName = qName
  }

  val AllMipNames            : Set[MipName]          = Set(Relevant, Readonly, Required, Constraint, Calculate, Default, Type, Whitespace)
  val AllMipNamesInOrder     : List[String]          = AllMipNames.toList.sortBy(_.name).map(_.name)

  val AllComputedMipsByName  : Map[String, Computed] = AllMipNames.collect { case m: Computed => m.name -> m } .toMap
  val AllXPathMipsByName     : Map[String, XPath]    = AllMipNames.collect { case m: XPath    => m.name -> m } .toMap

  // Should be all except `Type` and `Whitespace` (and of course `Custom(_)`)
  val QNameToXPathMIP        : Map[QName, XPath]     =
    AllMipNames
      .collect {
        case m: XPath with Computed => m.aName -> m
        case m: XPath with Validate => m.aName -> m
      }
      .toMap

  val CalculateMipNames      : Set[MipName]          = AllMipNames.collect { case m: Computed => m }
  val ValidateMipNames       : Set[MipName]          = AllMipNames.collect { case m: Validate => m }
  val BooleanXPathMipNames   : Set[MipName]          = AllMipNames.collect { case m: XPath with BooleanXPath => m }

  val StandardCustomMipQNames: Set[QName]            = Set(XXFORMS_EVENT_MODE_QNAME)
  val NeverCustomMipUris     : Set[String]           = Set(XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)

  def buildInternalCustomMIPName(qName: QName): String = qName.qualifiedName
  def buildExternalCustomMIPName(name: String): String = name.replace(':', '-')

  private val AllMipsByQName = AllMipNames.map(mipName => mipName.aName -> mipName).toMap

  def fromQName(mipQName: QName): MipName =
    AllMipsByQName.getOrElse(mipQName, Custom(mipQName))
}
