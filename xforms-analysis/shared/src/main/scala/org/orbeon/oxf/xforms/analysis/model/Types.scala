package org.orbeon.oxf.xforms.analysis.model

import org.orbeon.dom.QName
import org.orbeon.oxf.xml.XMLConstants.{XSD_URI, XS_STRING_QNAME}
import org.orbeon.xforms.XFormsNames.{XFORMS_NAMESPACE_URI, XFORMS_STRING_QNAME}


object Types {

  // MIP default values
  val DEFAULT_RELEVANT   = true
  val DEFAULT_READONLY   = false
  val DEFAULT_REQUIRED   = false
  val DEFAULT_VALID      = true
  val DEFAULT_CONSTRAINT = true

  // NOTE: If changed, `QName` returned has an empty prefix.
  def getVariationTypeOrKeep(datatype: QName): QName =
    if (Types.XFormsVariationTypeNames(datatype.localName))
      if (datatype.namespace.uri == XFORMS_NAMESPACE_URI)
        QName(datatype.localName, "", XSD_URI)
      else if (datatype.namespace.uri == XSD_URI)
        QName(datatype.localName, "", XFORMS_NAMESPACE_URI)
      else
        datatype
    else
      datatype

  def uriForBuiltinTypeName(builtinTypeString: String, required: Boolean): String =
    if (XFormsTypeNames(builtinTypeString) || ! required && Types.XFormsVariationTypeNames(builtinTypeString))
      XFORMS_NAMESPACE_URI
    else
      XSD_URI

  // NOTE: QName returned has an empty prefix.
  def qNameForBuiltinTypeName(builtinTypeString: String, required: Boolean): QName =
    QName(builtinTypeString, "", uriForBuiltinTypeName(builtinTypeString, required))

  val StringQNames: Set[QName] =
    Set(XS_STRING_QNAME, XFORMS_STRING_QNAME)

  val XFormsSchemaTypeNames: Set[String] = Set(
    "dayTimeDuration",
    "yearMonthDuration",
    "card-number"
  )

  val XFormsVariationTypeNames: Set[String] = {

    val CoreXFormsVariationTypeNames = Set(
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

    val SecondaryXFormsVariationTypeNames = Set(
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

    CoreXFormsVariationTypeNames ++ SecondaryXFormsVariationTypeNames
  }

  private val XFormsTypeNames = {

    val XForms11TypeNames = Set(
      "listItem",
      "listItems",
      "dayTimeDuration",
      "yearMonthDuration",
      "email",
      "card-number"
    )

    val XForms20TypeNames = Set(
      "HTMLFragment"
    )

    XForms11TypeNames ++ XForms20TypeNames
  }
}
