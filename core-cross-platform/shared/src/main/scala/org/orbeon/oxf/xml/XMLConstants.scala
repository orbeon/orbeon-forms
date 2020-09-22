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
package org.orbeon.oxf.xml

import org.orbeon.dom.Namespace
import org.orbeon.dom.QName

object XMLConstants {
  val XML_PREFIX = "xml"
  val XML_URI = "http://www.w3.org/XML/1998/namespace"

  val XML_BASE_QNAME = QName("base", Namespace(XML_PREFIX, XML_URI))
  val XML_LANG_QNAME = QName("lang", Namespace(XML_PREFIX, XML_URI))

  val XSI_PREFIX = "xsi"
  val XSI_URI = "http://www.w3.org/2001/XMLSchema-instance"
  val XSI_TYPE_QNAME = QName("type", Namespace(XSI_PREFIX, XSI_URI))

  val XSD_PREFIX = "xs"
  val XSD_URI = "http://www.w3.org/2001/XMLSchema"
  val XSD_NAMESPACE = Namespace(XSD_PREFIX, XSD_URI)

  val XML_SCHEMA_QNAME = QName("schema", XSD_NAMESPACE)

  val OPS_FORMATTING_URI = "http://orbeon.org/oxf/xml/formatting"
  val OPS_FORMATTING_NAMESPACE = Namespace("f", XMLConstants.OPS_FORMATTING_URI)
  val FORMATTING_URL_TYPE_QNAME = QName("url-type", OPS_FORMATTING_NAMESPACE)
  val FORMATTING_URL_NOREWRITE_QNAME = QName("url-norewrite", OPS_FORMATTING_NAMESPACE)

  val XS_STRING_QNAME = QName("string", XSD_NAMESPACE)
  val XS_BOOLEAN_QNAME = QName("boolean", XSD_NAMESPACE)
  val XS_INTEGER_QNAME = QName("integer", XSD_NAMESPACE)
  val XS_DATE_QNAME = QName("date", XSD_NAMESPACE)
  val XS_DATETIME_QNAME = QName("dateTime", XSD_NAMESPACE)
  val XS_QNAME_QNAME = QName("QName", XSD_NAMESPACE)
  val XS_ANYURI_QNAME = QName("anyURI", XSD_NAMESPACE)
  val XS_BASE64BINARY_QNAME = QName("base64Binary", XSD_NAMESPACE)
  val XS_NCNAME_QNAME = QName("NCName", XSD_NAMESPACE)
  val XS_NMTOKEN_QNAME = QName("NMTOKEN", XSD_NAMESPACE)
  val XS_NMTOKENS_QNAME = QName("NMTOKENS", XSD_NAMESPACE)
  val XS_NONNEGATIVEINTEGER_QNAME = QName("nonNegativeInteger", XSD_NAMESPACE)
  val XS_INT_QNAME = QName("int", XSD_NAMESPACE)
  val XS_DECIMAL_QNAME = QName("decimal", XSD_NAMESPACE)
  val XS_FLOAT_QNAME = QName("float", XSD_NAMESPACE)
  val XS_DOUBLE_QNAME = QName("double", XSD_NAMESPACE)

  val XSI_NAMESPACE = Namespace(XSI_PREFIX, XSI_URI)
  val XSI_NIL_ATTRIBUTE = "nil"
  val XSI_NIL_QNAME = QName(XSI_NIL_ATTRIBUTE, XSI_NAMESPACE)

  val XSLT_PREFIX = "xsl"
  val XSLT_NAMESPACE_URI = "http://www.w3.org/1999/XSL/Transform"
  val XSLT_NAMESPACE = Namespace("xsl", XSLT_NAMESPACE_URI)
  val XSLT_VERSION_QNAME = QName("version", XSLT_NAMESPACE)

  val XHTML_PREFIX = "xhtml"
  val XHTML_SHORT_PREFIX = "xh"
  val XHTML_NAMESPACE_URI = "http://www.w3.org/1999/xhtml"

  val XPATH_FUNCTIONS_NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions"
  val XPATH_ARRAY_FUNCTIONS_NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/array"
  val XPATH_MAP_FUNCTIONS_NAMESPACE_URI = "http://www.w3.org/2005/xpath-functions/map"

  val SAX_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler"
}
