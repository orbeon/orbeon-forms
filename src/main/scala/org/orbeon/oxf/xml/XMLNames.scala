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

import org.dom4j.{Namespace, QName}

object XMLNames {

  // NOTE: "2003" was used at some point; the correct value for XInclude 1.0 is "2001"
  val XINCLUDE_URI = "http://www.w3.org/2001/XInclude"
  val OLD_XINCLUDE_URI = "http://www.w3.org/2003/XInclude"
  val XXINCLUDE_NAMESPACE_URI = "http://orbeon.org/oxf/xml/xinclude"
  val XXINCLUDE_NAMESPACE = Namespace("xxi", XXINCLUDE_NAMESPACE_URI)
  val XXINCLUDE_OMIT_XML_BASE = new QName("omit-xml-base", XXINCLUDE_NAMESPACE)
  val XINCLUDE_FIXUP_XML_BASE = new QName("fixup-xml-base", Namespace.EmptyNamespace)
}
