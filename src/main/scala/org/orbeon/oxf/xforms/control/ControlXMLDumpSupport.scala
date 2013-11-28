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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import collection.JavaConverters._
import org.orbeon.oxf.xml.XMLUtils.DebugXML
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.saxon.om.{NodeInfo, Item}

trait ControlXMLDumpSupport extends DebugXML{

    self: XFormsControl ⇒

    def toXML(helper: XMLReceiverHelper, attributes: List[String])(content: ⇒ Unit): Unit = {

        def itemToString(i: Item) = i match {
            case atomic: AtomicValue ⇒ atomic.getStringValue
            case node: NodeInfo ⇒ node.getDisplayName
            case _ ⇒ throw new IllegalStateException
        }

        helper.startElement(localName, Array(
            "id", getId,
            "effectiveId", effectiveId,
            "isRelevant", isRelevant.toString,
            "wasRelevant", wasRelevant.toString,
            "binding-names", bindingContext.nodeset.asScala map itemToString mkString ("(", ", ", ")"),
            "binding-position", bindingContext.position.toString
        ))
        content
        helper.endElement()
    }

    def toXML(helper: XMLReceiverHelper): Unit = {
        helper.startDocument()
        toXML(helper, List.empty)()
        helper.endDocument()
    }

    def toXMLString =
        Dom4jUtils.domToPrettyString(XMLUtils.createDocument(this))

    def dumpXML(): Unit =
        println(toXMLString)
}