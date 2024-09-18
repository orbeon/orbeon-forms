package org.orbeon.dom.saxon

import java.io.Serializable
import javax.xml.transform.{Result, Source}

import org.orbeon.dom.{Document, Node}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.ItemType
import org.orbeon.saxon.event.{PipelineConfiguration, Receiver}
import org.orbeon.saxon.expr.{JPConverter, PJConverter, XPathContext}
import org.orbeon.saxon.om.{ExternalObjectModel, NodeInfo, ValueRepresentation, VirtualNode}
import org.orbeon.saxon.pattern.AnyNodeTest
import org.orbeon.saxon.value.{SingletonNode, Value}


/**
  * Copyright (C) 2007 Orbeon, Inc.
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
// Wrap Orbeon DOM documents as instances of the Saxon NodeInfo interface.
// This started as a Saxon object model for DOM4J and got converted to Scala to support the Orbeon DOM based on DOM4J.
// Used by `XPath`.
object OrbeonDOMObjectModel extends ExternalObjectModel with Serializable {

  val getIdentifyingURI = "http://orbeon.org/oxf/xml/dom"

  def getPJConverter(targetClass: Class[?]): PJConverter =
    if (isRecognizedNodeClass(targetClass))
      (value: ValueRepresentation, targetClass: Class[?], _: XPathContext) =>
        convertXPathValueToObject(Value.asValue(value), targetClass)
    else
      null

  def getJPConverter(targetClass: Class[?]): JPConverter =
    if (isRecognizedNodeClass(targetClass)) {
      new JPConverter {
        def convert(obj: Any, context: XPathContext): ValueRepresentation = convertObjectToXPathValue(obj, context.getConfiguration)
        def getItemType: ItemType = AnyNodeTest.getInstance
      }
    } else {
      null
    }

  private def isRecognizedNodeClass(nodeClass: Class[?]) = classOf[Node].isAssignableFrom(nodeClass)

  private def convertObjectToXPathValue(obj: Any, config: Configuration): NodeInfo = {

    def wrapDocument(node: Document) =
      new DocumentWrapper(node.getDocument, null, config)

    obj match {
      case document : Document => wrapDocument(document)
      case node     : Node     => wrapDocument(node.getDocument).wrap(node)
      case _                   => throw new IllegalStateException
    }
  }

  private def convertXPathValueToObject(value: Value, targetClass: Class[?]): AnyRef =
    value match {
      case singletonNode: SingletonNode =>
        singletonNode.getNode match {
          case virtualNode: VirtualNode =>
            val underlying = virtualNode.getUnderlyingNode
            if (targetClass.isAssignableFrom(underlying.getClass))
              underlying
            else
              null
          case _ =>
            null
        }
      case _ =>
        null
    }

  def getNodeListCreator(node: Any): PJConverter = null
  def getDocumentBuilder(result: Result): Receiver = null
  def sendSource(source: Source, receiver: Receiver, pipe: PipelineConfiguration): Boolean = false
  def unravel(source: Source, config: Configuration): NodeInfo = null
}
