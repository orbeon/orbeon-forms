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
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.xforms.XFormsNames._


object ModelDefs {

  // MIP enumeration
  sealed trait MIP         { def name: String; val aName: QName;                  val eName: QName }
  sealed trait StdMIP extends MIP { val name: String; val aName = QName(name);    val eName = xfQName(name) }
  sealed trait ExtMIP extends MIP { val name: String; val aName = xxfQName(name); val eName = xxfQName(name) }

  sealed trait ComputedMIP extends MIP
  sealed trait ValidateMIP extends MIP
  sealed trait XPathMIP    extends MIP
  sealed trait BooleanMIP  extends XPathMIP
  sealed trait StringMIP   extends XPathMIP

  // NOTE: "required" is special: it is evaluated during recalculate, but used during revalidate. In effect both
  // recalculate AND revalidate depend on it. Ideally maybe revalidate would depend on the the *value* of the
  // "required" MIP, not on the XPath of it. See also what we would need for valid(), etc. functions.
  case object Relevant     extends { val name = "relevant"   } with StdMIP with BooleanMIP with ComputedMIP
  case object Readonly     extends { val name = "readonly"   } with StdMIP with BooleanMIP with ComputedMIP
  case object Required     extends { val name = "required"   } with StdMIP with BooleanMIP with ComputedMIP with ValidateMIP
  case object Constraint   extends { val name = "constraint" } with StdMIP with BooleanMIP with ValidateMIP

  case object Calculate    extends { val name = "calculate"  } with StdMIP with StringMIP  with ComputedMIP
  case object Default      extends { val name = "default"    } with ExtMIP with StringMIP  with ComputedMIP
  case object Type         extends { val name = "type"       } with StdMIP with ValidateMIP
  case object Whitespace   extends { val name = "whitespace" } with ExtMIP with ComputedMIP

  //case class Custom(n: String) extends { val name = n }        with StdMIP with XPathMIP

  val AllMIPs                  = Set[MIP](Relevant, Readonly, Required, Constraint, Calculate, Default, Type, Whitespace)
  val AllMIPsInOrder           = AllMIPs.toList.sortBy(_.name)
  val AllMIPNamesInOrder       = AllMIPsInOrder map (_.name)
  val AllMIPsByName            = AllMIPs map (m => m.name -> m) toMap
  val AllMIPNames              = AllMIPs map (_.name)
  val MIPNameToAttributeQName  = AllMIPs map (m => m.name -> m.aName) toMap

  val AllComputedMipsByName    = AllMIPs collect { case m: ComputedMIP => m.name -> m } toMap
  val AllXPathMipsByName       = AllMIPs collect { case m: XPathMIP    => m.name -> m } toMap

  val QNameToXPathComputedMIP  = AllMIPs collect { case m: XPathMIP with ComputedMIP => m.aName -> m } toMap
  val QNameToXPathValidateMIP  = AllMIPs collect { case m: XPathMIP with ValidateMIP => m.aName -> m } toMap
  val QNameToXPathMIP          = QNameToXPathComputedMIP ++ QNameToXPathValidateMIP

  val CalculateMIPNames        = AllMIPs collect { case m: ComputedMIP => m.name }
  val ValidateMIPNames         = AllMIPs collect { case m: ValidateMIP => m.name }
  val BooleanXPathMIPNames     = AllMIPs collect { case m: XPathMIP with BooleanMIP => m.name }
  val StringXPathMIPNames      = AllMIPs collect { case m: XPathMIP with StringMIP => m.name }

  val StandardCustomMIPsQNames = Set(XXFORMS_EVENT_MODE_QNAME)
  val NeverCustomMIPsURIs      = Set(XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)

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

  val XFormsSchemaTypeNames = Set(
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

  val XFormsVariationTypeNames =
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

  val XFormsTypeNames =
    XForms11TypeNames ++ XForms20TypeNames

  val StringQNames =
    Set(XS_STRING_QNAME, XFORMS_STRING_QNAME)
}
