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
  val OXF_PROCESSORS_URI = "http://www.orbeon.com/oxf/processors"
  val OXF_PROCESSORS_NAMESPACE = Namespace("oxf", OXF_PROCESSORS_URI)
  val FORMATTING_URL_TYPE_QNAME = QName("url-type", OPS_FORMATTING_NAMESPACE)
  val FORMATTING_URL_NOREWRITE_QNAME = QName("url-norewrite", OPS_FORMATTING_NAMESPACE)
  val IDENTITY_PROCESSOR_QNAME = QName("identity", OXF_PROCESSORS_NAMESPACE)
  val REQUEST_PROCESSOR_QNAME = QName("request", OXF_PROCESSORS_NAMESPACE)
  val NULL_PROCESSOR_QNAME = QName("null", OXF_PROCESSORS_NAMESPACE)
  val NULL_SERIALIZER_PROCESSOR_QNAME = QName("null-serializer", OXF_PROCESSORS_NAMESPACE)
  val PIPELINE_PROCESSOR_QNAME = QName("pipeline", OXF_PROCESSORS_NAMESPACE)
  val PAGE_FLOW_PROCESSOR_QNAME = QName("page-flow", OXF_PROCESSORS_NAMESPACE)
  val REDIRECT_PROCESSOR_QNAME = QName("redirect", OXF_PROCESSORS_NAMESPACE)
  val HTML_SERIALIZER_PROCESSOR_QNAME = QName("html-serializer", OXF_PROCESSORS_NAMESPACE)
  val UNSAFE_XSLT_PROCESSOR_QNAME = QName("unsafe-xslt", OXF_PROCESSORS_NAMESPACE)
  val XSLT_PROCESSOR_QNAME = QName("xslt", OXF_PROCESSORS_NAMESPACE)
  val PFC_XSLT10_PROCESSOR_QNAME = QName("pfc-xslt-1.0", OXF_PROCESSORS_NAMESPACE)
  val PFC_XSLT20_PROCESSOR_QNAME = QName("pfc-xslt-2.0", OXF_PROCESSORS_NAMESPACE)
  val INSTANCE_TO_PARAMETERS_PROCESSOR_QNAME = QName("instance-to-parameters", OXF_PROCESSORS_NAMESPACE)
  val URL_GENERATOR_PROCESSOR_QNAME = QName("url-generator", OXF_PROCESSORS_NAMESPACE)
  val DOM_GENERATOR_PROCESSOR_QNAME = QName("dom-generator", OXF_PROCESSORS_NAMESPACE)
  val ERROR_PROCESSOR_QNAME = QName("error", OXF_PROCESSORS_NAMESPACE)
  val XINCLUDE_PROCESSOR_QNAME = QName("xinclude", OXF_PROCESSORS_NAMESPACE)
  val DEBUG_PROCESSOR_QNAME = QName("debug", OXF_PROCESSORS_NAMESPACE)
  val XS_STRING_QNAME = QName("string", XSD_NAMESPACE)
  //    public final static String XS_STRING_EXPLODED_QNAME = XS_STRING_QNAME.clarkName();
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
  val OPS_TYPES_URI = "http://orbeon.org/oxf/xml/datatypes"
  val OPS_XMLFRAGMENT_QNAME = QName("xmlFragment", Namespace("ops", OPS_TYPES_URI))
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
