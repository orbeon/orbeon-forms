/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.util.MarkupUtils._
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import scala.collection.immutable.Seq

trait XMLReceiverSupport {

  support =>

  def withDocument[T](body: => T)(implicit receiver: XMLReceiver): T = {
    receiver.startDocument()
    val result = body
    receiver.endDocument()
    result
  }

  def withElement[T](
    localName : String,
    prefix    : String = "",
    uri       : String = "",
    atts      : Attributes = XMLReceiverSupport.EmptyAttributes)(
    body      : => T)(implicit
    receiver  : XMLReceiver
  ): T = {
    val qName = XMLUtils.buildQName(prefix, localName)
    receiver.startElement(uri, localName, qName, atts)
    val result = body
    receiver.endElement(uri, localName, qName)
    result
  }

  def element(
    localName : String,
    prefix    : String = "",
    uri       : String = "",
    atts      : Attributes = XMLReceiverSupport.EmptyAttributes,
    text      : String = "")(implicit
    receiver  : XMLReceiver
  ): Unit =
    withElement(localName, prefix, uri, atts) {
      if (text.nonEmpty)
        support.text(text)
    }

  def openElement(
    localName : String,
    prefix    : String = "",
    uri       : String = "",
    atts      : Attributes = XMLReceiverSupport.EmptyAttributes)(implicit
    receiver  : XMLReceiver
  ): Unit =
    receiver.startElement(uri, localName, XMLUtils.buildQName(prefix, localName), atts)

  def closeElement(
    localName : String,
    prefix    : String = "",
    uri       : String = "")(implicit
    receiver  : XMLReceiver
  ): Unit =
    receiver.endElement(uri, localName, XMLUtils.buildQName(prefix, localName))

  def text(text: String)(implicit receiver: XMLReceiver): Unit =
    if (text.nonEmpty) {
      val chars = text.toCharArray
      receiver.characters(chars, 0, chars.length)
    }

  def addAttributes(attributesImpl: AttributesImpl, atts: Seq[(String, String)]): Unit =
    atts foreach {
      case (name, value) =>
        require(name ne null)
        if (value ne null)
          attributesImpl.addAttribute("", name, name, "CDATA", value)
    }

  // NOTE: Encode attributes as space-separated XML attribute-like pairs
  def processingInstruction(name: String, atts: Seq[(String, String)] = Nil)(implicit receiver: XMLReceiver): Unit =
    receiver.processingInstruction(
      name,
      atts map { case (name, value) => s"""$name="${value.escapeXmlForAttribute}"""" } mkString " "
    )

  implicit def pairsToAttributes(atts: Seq[(String, String)]): Attributes = {
    val saxAtts = new AttributesImpl
    addAttributes(saxAtts, atts)
    saxAtts
  }
}

object XMLReceiverSupport extends XMLReceiverSupport {

  val EmptyAttributes: Attributes = new Attributes {
    def getLength                              : Int    = 0
    def getURI      (i: Int)                   : String = null
    def getLocalName(i: Int)                   : String = null
    def getQName    (i: Int)                   : String = null
    def getType     (i: Int)                   : String = null
    def getValue    (i: Int)                   : String = null
    def getIndex    (s: String, s1: String)    : Int    = -1
    def getIndex    (s: String)                : Int    = -1
    def getType     (s: String, s1: String)    : String = null
    def getType     (s: String)                : String = null
    def getValue    (s: String, s1: String)    : String = null
    def getValue    (s: String)                : String = null
  }

  /**
   * Append an attribute value to existing mutable attributes.
   *
   * @param attributes     existing attributes
   * @param attributeName  attribute name
   * @param attributeValue value to set or append
   */
  // Used by handlers
  def addOrAppendToAttribute(attributes: AttributesImpl, attributeName: String, attributeValue: String): Unit = {
    val oldAttributeIndex = attributes.getIndex(attributeName)
    if (oldAttributeIndex == -1) {
      // No existing attribute
      attributes.addAttribute("", attributeName, attributeName, XMLReceiverHelper.CDATA, attributeValue)
    } else {
      // Existing attribute
      val oldAttributeValue = attributes.getValue(oldAttributeIndex)
      val newAttributeValue = oldAttributeValue + ' ' + attributeValue
      attributes.setValue(oldAttributeIndex, newAttributeValue)
    }
  }
}