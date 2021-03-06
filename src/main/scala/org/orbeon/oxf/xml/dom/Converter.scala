/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xml.dom

import org.orbeon.dom.Element
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.xml.sax.helpers.AttributesImpl


object Converter {

  implicit class ScalaElemConverterOps(private val e: scala.xml.Elem) extends AnyVal {
    def toDocument: org.orbeon.dom.Document = IOSupport.readDom4j(e.toString)
  }

  implicit class DomElemConverterOps(private val e: Element) extends AnyVal {
    def attributesAsSax: AttributesImpl = {
      val result = new AttributesImpl
      for (att <- e.attributeIterator)
        result.addAttribute(att.getNamespaceURI, att.getName, att.getQualifiedName, XMLReceiverHelper.CDATA, att.getValue)
      result
    }
  }
}
