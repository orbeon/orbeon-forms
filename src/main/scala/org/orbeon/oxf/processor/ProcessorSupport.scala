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
package org.orbeon.oxf.processor

import org.orbeon.dom.{Document, Element, Node, QName}
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom4j.LocationData


object ProcessorSupport {

  // NOTE: This should be an immutable document, but we don't have support for this yet.
  val NullDocument: Document = {
    val d = Document()
    val nullElement = Element("null")
    nullElement.addAttribute(XMLConstants.XSI_NIL_QNAME, "true")
    d.setRootElement(nullElement)
    d
  }

  def makeSystemId(e: Element): String =
    Option(e.getData.asInstanceOf[LocationData]) flatMap
      (d => Option(d.file))                      getOrElse
      DOMGenerator.DefaultContext

  // 1 Java caller
  def normalizeTextNodesJava(nodeToNormalize: Node): Node =
    nodeToNormalize.normalizeTextNodes

  def qNameToExplodedQName(qName: QName): String =
    if (qName eq null) null else qName.clarkName
}
