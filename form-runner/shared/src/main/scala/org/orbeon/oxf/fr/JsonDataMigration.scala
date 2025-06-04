/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom.{Document, Element}
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.XPath
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*

// Convert form data in edge format to JSON-XML format suitable for serialization="application/json"
object JsonDataMigration {

  //@XPathFunction
  def jsonXmlFromEdge(data: DocumentNodeInfoType): DocumentNodeInfoType = {
    val jsonDocument = Document()
    val jsonRootElem = jsonDocument.addElement("json")
    jsonRootElem.addAttribute(TypeAttribute, "object")
    processElement(data.rootElement, jsonRootElem)
    new DocumentWrapper(jsonDocument, null, XPath.GlobalConfiguration)
  }

  private val TypeAttribute = "type"

  private def processElement(sourceElem: NodeInfo, targetElem: Element): Unit = {
    sourceElem.child(*).foreach { sourceChildElem =>
      val childName = sourceChildElem.localname
      if (sourceChildElem.getURI.nonEmpty) {
        // Skip elements with namespaces
      } else if (childName.endsWith("-iteration")) {
        processElement(sourceChildElem, targetElem)
      } else if (hasIterationChildren(sourceChildElem)) {
        val targetChildElem = targetElem.addElement(childName)
        targetChildElem.addAttribute(TypeAttribute, "array")
        sourceChildElem.child(*).foreach { iterationElem =>
          if (iterationElem.localname.endsWith("-iteration")) {
            val iterationObjectElem = targetChildElem.addElement("_")
            iterationObjectElem.addAttribute(TypeAttribute, "object")
            processElement(iterationElem, iterationObjectElem)
          }
        }
      } else if (hasChildren(sourceChildElem)) {
        val containerElem = targetElem.addElement(childName)
        containerElem.addAttribute(TypeAttribute, "object")
        processElement(sourceChildElem, containerElem)
      } else {
        val fieldElem = targetElem.addElement(childName)
        fieldElem.addAttribute(TypeAttribute, "string")
        fieldElem.setText(sourceChildElem.stringValue)
      }
    }
  }

  private def hasChildren(elem: NodeInfo): Boolean =
    (elem child *).nonEmpty
  private def hasIterationChildren(elem: NodeInfo): Boolean =
    (elem child *).exists(_.localname.endsWith("-iteration"))
}