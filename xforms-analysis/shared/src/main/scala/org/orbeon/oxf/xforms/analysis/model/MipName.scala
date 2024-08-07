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
    def name = qName.qualifiedName
    val aName: QName = qName
    val eName: QName = qName
  }

//  def fromQualifiedName(qualifiedName: String): MipName = qualifiedName match {
//    case "relevant"   => Relevant
//    case "readonly"   => Readonly
//    case "required"   => Required
//    case "constraint" => Constraint
//    case "calculate"  => Calculate
//    case "default"    => Default
//    case "type"       => Type
//    case "whitespace" => Whitespace
//    case other        => throw new IllegalArgumentException(other)
//  }

  def fromQName(mipQName: QName): MipName =
    AllMipsByQName.getOrElse(mipQName, Custom(mipQName))

  val AllMipNames              = Set[MipName](Relevant, Readonly, Required, Constraint, Calculate, Default, Type, Whitespace)
  val AllMipNamesInOrder       = AllMipNames.toList.sortBy(_.name)
  val AllMIPNamesInOrder       = AllMipNamesInOrder.map(_.name)
  val AllMipsByQName           = AllMipNames.map(mipName => mipName.aName -> mipName).toMap

  val AllComputedMipsByName    = AllMipNames collect { case m: Computed => m.name -> m } toMap
  val AllXPathMipsByName       = AllMipNames collect { case m: XPath    => m.name -> m } toMap

  val QNameToXPathComputedMip  = AllMipNames collect { case m: XPath with Computed => m.aName -> m } toMap
  val QNameToXPathValidateMip  = AllMipNames collect { case m: XPath with Validate => m.aName -> m } toMap
  val QNameToXPathMIP          = QNameToXPathComputedMip ++ QNameToXPathValidateMip

  val CalculateMipNames   : Set[MipName] = AllMipNames collect { case m: Computed => m }
  val ValidateMipNames    : Set[MipName] = AllMipNames collect { case m: Validate => m }
  val BooleanXPathMipNames: Set[MipName] = AllMipNames collect { case m: XPath with BooleanXPath => m }

  val StandardCustomMipQNames = Set(XXFORMS_EVENT_MODE_QNAME)
  val NeverCustomMipUris      = Set(XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)

  def buildInternalCustomMIPName(qName: QName): String = qName.qualifiedName
  def buildExternalCustomMIPName(name: String): String = name.replace(':', '-')

  // MIP default values
  val DEFAULT_RELEVANT   = true
  val DEFAULT_READONLY   = false
  val DEFAULT_REQUIRED   = false
  val DEFAULT_VALID      = true
  val DEFAULT_CONSTRAINT = true

  // NOTE: If changed, QName returned has an empty prefix.
  def getVariationTypeOrKeep(datatype: QName): QName =
    if (XFormsVariationTypeNames(datatype.localName))
      if (datatype.namespace.uri == XFORMS_NAMESPACE_URI)
        QName(datatype.localName, "", XSD_URI)
      else if (datatype.namespace.uri == XSD_URI)
        QName(datatype.localName, "", XFORMS_NAMESPACE_URI)
      else
        datatype
    else
      datatype

  def uriForBuiltinTypeName(builtinTypeString: String, required: Boolean): String =
    if (XFormsTypeNames(builtinTypeString) || ! required && XFormsVariationTypeNames(builtinTypeString))
      XFORMS_NAMESPACE_URI
    else
      XSD_URI

  // NOTE: QName returned has an empty prefix.
  def qNameForBuiltinTypeName(builtinTypeString: String, required: Boolean): QName =
    QName(builtinTypeString, "", uriForBuiltinTypeName(builtinTypeString, required))

  val XFormsSchemaTypeNames: Set[String] = Set(
    "dayTimeDuration",
    "yearMonthDuration",
    "card-number"
  )

  private val CoreXFormsVariationTypeNames = Set(
    "dateTime",
    "time",
    "date",
    "gYearMonth",
    "gYear",
    "gMonthDay",
    "gDay",
    "gMonth",
    "string",
    "boolean",
    "base64Binary",
    "hexBinary",
    "float",
    "decimal",
    "double",
    "anyURI",
    "QName"
  )

  private val SecondaryXFormsVariationTypeNames = Set(
    "normalizedString",
    "token",
    "language",
    "Name",
    "NCName",
    "ID",
    "IDREF",
    "IDREFS",
    "NMTOKEN",
    "NMTOKENS",
    "integer",
    "nonPositiveInteger",
    "negativeInteger",
    "long",
    "int",
    "short",
    "byte",
    "nonNegativeInteger",
    "unsignedLong",
    "unsignedInt",
    "unsignedShort",
    "unsignedByte",
    "positiveInteger"
  )

  val XFormsVariationTypeNames: Set[String] =
    CoreXFormsVariationTypeNames ++ SecondaryXFormsVariationTypeNames

  private val XForms11TypeNames = Set(
    "listItem",
    "listItems",
    "dayTimeDuration",
    "yearMonthDuration",
    "email",
    "card-number"
  )

  private val XForms20TypeNames = Set(
    "HTMLFragment"
  )

  private val XFormsTypeNames =
    XForms11TypeNames ++ XForms20TypeNames

  val StringQNames: Set[QName] =
    Set(XS_STRING_QNAME, XFORMS_STRING_QNAME)
}
