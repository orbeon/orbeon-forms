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

import org.orbeon.dom.*
import org.orbeon.xforms.XFormsNames.*

sealed trait MipName { def name: String; def aName: QName; def eName: QName }

object MipName {

  // MIP enumeration
  sealed trait Std extends MipName { val name: String; lazy val aName = QName(name);    lazy val eName = xfQName(name) }
  sealed trait Ext extends MipName { val name: String; lazy val aName = xxfQName(name); lazy val eName = xxfQName(name) }

  sealed trait Computed      extends MipName
  sealed trait Validate      extends MipName
  sealed trait XPath         extends MipName
  sealed trait BooleanXPath  extends XPath
  sealed trait StringXPath   extends XPath

  // NOTE: `required` is special: it is evaluated during recalculate, but used during revalidate. In effect both
  // recalculate AND revalidate depend on it. Ideally maybe revalidate would depend on the *value* of the
  // `required` MIP, not on the XPath of it. See also what we would need for `valid()`, etc. functions.
  case object Relevant   extends Std with BooleanXPath with Computed               { val name = "relevant"   }
  case object Readonly   extends Std with BooleanXPath with Computed               { val name = "readonly"   }
  case object Required   extends Std with BooleanXPath with Computed with Validate { val name = "required"   }
  case object Constraint extends Std with BooleanXPath with Validate               { val name = "constraint" }
  case object Calculate  extends Std with StringXPath  with Computed               { val name = "calculate"  }
  case object Default    extends Ext with StringXPath  with Computed               { val name = "default"    }
  case object Type       extends Std with Validate                                 { val name = "type"       }
  case object Whitespace extends Ext with Computed                                 { val name = "whitespace" }

  case class Custom(qName: QName) extends StringXPath { // with `ComputedMIP`?
    def name = buildInternalCustomMIPName(qName)
    def aName: QName = qName
    def eName: QName = qName
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