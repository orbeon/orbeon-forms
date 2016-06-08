package org.orbeon.saxon.dom

import java.io.Serializable
import javax.xml.transform.{Result, Source}

import org.orbeon.dom._
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.ItemType
import org.orbeon.saxon.event.{PipelineConfiguration, Receiver}
import org.orbeon.saxon.expr.{JPConverter, PJConverter, XPathContext}
import org.orbeon.saxon.om._
import org.orbeon.saxon.pattern.AnyNodeTest
import org.orbeon.saxon.value.{SingletonNode, Value}

// Wrap Orbeon DOM documents as instances of the Saxon NodeInfo interface.
// This started as a Saxon object model for DOM4J and got converted to Scala to support the Orbeon DOM based on DOM4J.
object OrbeonDOMObjectModel extends ExternalObjectModel with Serializable {

  val getIdentifyingURI = "http://orbeon.org/oxf/xml/dom"

  def getPJConverter(targetClass: Class[_]): PJConverter =
    if (isRecognizedNodeClass(targetClass)) {
      new PJConverter {
        def convert(value: ValueRepresentation, targetClass: Class[_], context: XPathContext) =
          convertXPathValueToObject(Value.asValue(value), targetClass)
      }
    } else {
      null
    }

  def getJPConverter(targetClass: Class[_]): JPConverter =
    if (isRecognizedNodeClass(targetClass)) {
      new JPConverter {
        def convert(obj: Any, context: XPathContext) = convertObjectToXPathValue(obj, context.getConfiguration)
        def getItemType: ItemType                    = AnyNodeTest.getInstance
      }
    } else {
      null
    }

  private def isRecognizedNodeClass(nodeClass: Class[_]) = classOf[Node].isAssignableFrom(nodeClass)

  private def convertObjectToXPathValue(obj: Any, config: Configuration): ValueRepresentation = {

    def wrapDocument(node: Document) =
      new DocumentWrapper(node.getDocument, null, config)

    obj match {
      case document : Document ⇒ wrapDocument(document)
      case node     : Node     ⇒ wrapDocument(node.getDocument).wrap(node)
      case _                   ⇒ throw new IllegalStateException
    }
  }

  private def convertXPathValueToObject(value: Value, targetClass: Class[_]): AnyRef =
    value match {
      case singletonNode: SingletonNode ⇒
        singletonNode.getNode match {
          case virtualNode: VirtualNode ⇒
            val underlying = virtualNode.getUnderlyingNode
            if (targetClass.isAssignableFrom(underlying.getClass))
              underlying
            else
              null
          case _ ⇒
            null
        }
      case _ ⇒
        null
    }

  def getNodeListCreator(node: Any): PJConverter = null
  def getDocumentBuilder(result: Result): Receiver = null
  def sendSource(source: Source, receiver: Receiver, pipe: PipelineConfiguration): Boolean = false
  def unravel(source: Source, config: Configuration): NodeInfo = null
}


//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): Gunther Schadow (changes to allow access to public fields; also wrapping
// of extensions and mapping of null to empty sequence).
//
