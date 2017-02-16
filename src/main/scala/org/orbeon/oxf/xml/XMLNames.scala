/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.xforms.XFormsConstants.XXFORMS_NAMESPACE_SHORT

object XMLNames {

  val XIncludeURI               = "http://www.w3.org/2001/XInclude"
  val XIncludeLegacyURI         = "http://www.w3.org/2003/XInclude"
  val XXIncludeURI              = "http://orbeon.org/oxf/xml/xinclude"

  val XXIncludeNS               = Namespace("xxi", XXIncludeURI)
  val XXIncludeOmitXmlBaseQName = QName.get("omit-xml-base", XXIncludeNS)
  val XIncludeFixupXMLBaseQName = QName.get("fixup-xml-base", Namespace.EmptyNamespace)
}
