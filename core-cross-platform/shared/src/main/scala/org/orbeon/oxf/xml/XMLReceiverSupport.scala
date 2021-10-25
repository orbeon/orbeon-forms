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
    atts      : Attributes = XMLReceiverSupport.EmptyAttributes,
    extraNs   : Seq[(String, String)] = Nil)(
    body      : => T)(implicit
    receiver  : XMLReceiver
  ): T = {

    extraNs foreach { case (prefix, uri) =>
      receiver.startPrefixMapping(prefix, uri)
    }

    val qName = XMLUtils.buildQName(prefix, localName)
    receiver.startElement(uri, localName, qName, atts)
    val result = body
    receiver.endElement(uri, localName, qName)

    extraNs foreach { case (prefix, _) =>
      receiver.endPrefixMapping(prefix)
    }

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

  def comment(text: String)(implicit receiver: XMLReceiver): Unit = {
    val chars = text.toCharArray
    receiver.comment(chars, 0, chars.length)
  }

  // NOTE: Encode attributes as space-separated XML attribute-like pairs
  def processingInstruction(name: String, atts: Seq[(String, String)] = Nil)(implicit receiver: XMLReceiver): Unit =
    receiver.processingInstruction(
      name,
      atts map { case (name, value) => s"""$name="${value.escapeXmlForAttribute}"""" } mkString " "
    )

  private def addAttributes(attributesImpl: AttributesImpl, atts: Iterable[(String, String)]): Unit =
    atts foreach {
      case (name, value) =>
        require(name ne null)
        if (value ne null)
          attributesImpl.addAttribute("", name, name, "CDATA", value)
    }

  implicit def pairsToAttributes(atts: Iterable[(String, String)]): Attributes = {
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

  // Must be used only with non-prefixed attributes!
  // Used by handlers
  def addOrAppendToAttribute(
    attributes     : AttributesImpl,
    attributeName  : String,
    attributeValue : String
  ): Unit = {
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

  // Match on URI and localname
  // Used by handlers/extractor/annotator
  def addOrReplaceAttribute(
    attributes : Attributes,
    uri        : String,
    prefix     : String,
    localname  : String,
    value      : String
  ): AttributesImpl = {
    val newAttributes = new AttributesImpl
    var replaced = false
    for (i <- 0 until attributes.getLength) {
      val attributeURI       = attributes.getURI(i)
      val attributeValue     = attributes.getValue(i)
      val attributeType      = attributes.getType(i)
      val attributeQName     = attributes.getQName(i)
      val attributeLocalname = attributes.getLocalName(i)
      if (uri == attributeURI && localname == attributeLocalname) {
        // Found existing attribute
        replaced = true
        newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), XMLReceiverHelper.CDATA, value)
      } else {
        // Not a matched attribute
        newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue)
      }
    }

    if (! replaced) // attribute did not exist already so add it
      newAttributes.addAttribute(uri, localname, XMLUtils.buildQName(prefix, localname), XMLReceiverHelper.CDATA, value)

    newAttributes
  }

  // Append classes to existing attributes. This creates a new `AttributesImpl` object.
  // Used by handlers/annotator
  def appendToClassAttribute(attributes: Attributes, newClasses: String): AttributesImpl = {
    val oldClassAttribute = attributes.getValue("class")
    val newClassAttribute =
      if (oldClassAttribute == null)
        newClasses
      else
        oldClassAttribute + ' ' + newClasses
    addOrReplaceAttribute(attributes, "", "", "class", newClassAttribute)
  }

  // Used by handlers
  def removeAttribute(attributes: Attributes, uri: String, localname: String): AttributesImpl = {
    val newAttributes = new AttributesImpl
    for (i <- 0 until attributes.getLength) {
      val attributeURI       = attributes.getURI(i)
      val attributeValue     = attributes.getValue(i)
      val attributeType      = attributes.getType(i)
      val attributeQName     = attributes.getQName(i)
      val attributeLocalname = attributes.getLocalName(i)
      if (uri != attributeURI || localname != attributeLocalname) // not a matched attribute
        newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue)
    }
    newAttributes
  }
}