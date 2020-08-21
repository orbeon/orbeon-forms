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
package org.orbeon.oxf.processor

import org.orbeon.dom.Namespace
import org.orbeon.dom.QName

object XPLConstants {
  val OXF_PROCESSORS_URI = "http://www.orbeon.com/oxf/processors"
  val OXF_PROCESSORS_NAMESPACE = Namespace("oxf", OXF_PROCESSORS_URI)

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

  val OPS_TYPES_URI = "http://orbeon.org/oxf/xml/datatypes"
  val OPS_XMLFRAGMENT_QNAME = QName("xmlFragment", Namespace("ops", OPS_TYPES_URI))
}
