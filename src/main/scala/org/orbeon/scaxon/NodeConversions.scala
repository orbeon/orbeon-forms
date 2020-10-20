/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.scaxon

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.util
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.oxf.xml.{ParserConfiguration, TransformerUtils, XMLParsing, XMLReceiver}
import org.orbeon.saxon.om

import scala.xml.Elem


object NodeConversions {

  def elemToSAX(e: Elem, xmlReceiver: XMLReceiver): Unit =
    XMLParsing.stringToSAX(e.toString, "", xmlReceiver, ParserConfiguration.Plain, handleLexical = true)

  def elemToDom4j(e: Elem): Document =
    IOSupport.readDom4j(e.toString)

  def elemToDom4jElem(e: Elem): Element =
    IOSupport.readDom4j(e.toString).getRootElement

  def elemToDocumentInfo(e: Elem, readonly: Boolean = true): om.DocumentInfo =
    if (readonly)
      TransformerUtils.stringToTinyTree(util.XPath.GlobalConfiguration, e.toString, false, false)
    else
      new DocumentWrapper(elemToDom4j(e), null, util.XPath.GlobalConfiguration)

  def nodeInfoToElem(nodeInfo: om.NodeInfo): Elem =
    scala.xml.XML.loadString(TransformerUtils.tinyTreeToString(nodeInfo))

  import org.orbeon.scaxon.SimplePath._

  implicit def elemToNodeInfo(e: Elem): om.NodeInfo = elemToNodeInfoSeq(e).head
  implicit def elemToNodeInfoSeq(e: Elem): scala.collection.Seq[om.NodeInfo] = elemToDocumentInfo(e) / *
}
