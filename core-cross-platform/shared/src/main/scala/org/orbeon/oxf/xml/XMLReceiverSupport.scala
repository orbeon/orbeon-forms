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

import org.orbeon.dom.QName
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.xml.SaxSupport.{AttributesImplOps, EmptyAttributes}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


object XMLReceiverSupport {

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
    atts      : Attributes = SaxSupport.EmptyAttributes,
    extraNs   : Seq[(String, String)] = Nil
  )(
    body      : => T
  )(implicit
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
    atts      : Attributes = SaxSupport.EmptyAttributes,
    text      : String = ""
  )(implicit
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
    atts      : Attributes = SaxSupport.EmptyAttributes
  )(implicit
    receiver  : XMLReceiver
  ): Unit =
    receiver.startElement(uri, localName, XMLUtils.buildQName(prefix, localName), atts)

  def closeElement(
    localName : String,
    prefix    : String = "",
    uri       : String = ""
  )(implicit
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

  implicit def pairsToAttributes(atts: Nil.type): Attributes =
    EmptyAttributes

  implicit def pairsToAttributes(atts: Iterable[(String, String)]): AttributesImpl = {
    val saxAtts = new AttributesImpl
    atts foreach {
      case (name, value) =>
        require(name ne null)
        if (value ne null)
          saxAtts.addOrReplace(name, value)
    }
    saxAtts
  }

  implicit def qnamePairsToAttributes(atts: Iterable[(QName, String)]): AttributesImpl = {
    val saxAtts = new AttributesImpl
    atts foreach {
      case (name, value) =>
        require(name ne null)
        if (value ne null)
          saxAtts.addOrReplace(name, value)
    }
    saxAtts
  }
}
