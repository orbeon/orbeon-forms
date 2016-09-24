/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverUnneededEvents}
import org.xml.sax.{Attributes, Locator}

abstract class XFormsAnnotatorBase(
  templateReceiver  : XMLReceiver,
  extractorReceiver : XMLReceiver
) extends XMLReceiver
     with XMLReceiverUnneededEvents {

  private val keepLocationData = XFormsProperties.isKeepLocation
  private var _documentLocator: Locator = null
  def documentLocator = _documentLocator

  def isInXBLBinding: Boolean
  def isInPreserve: Boolean

  // Name of container elements that require the use of separators for handling visibility
  private val SeparatorAppearanceElements = Set(
    "table",
    "tbody",
    "colgroup",
    "thead",
    "tfoot",
    "tr",
    "ol",
    "ul",
    "dl"
  )

  case class StackElement(parent: Option[StackElement], uri: String, localname: String) {
    val isXForms                   = uri == XFORMS_NAMESPACE_URI
    val isXXForms                  = uri == XXFORMS_NAMESPACE_URI
    val isEXForms                  = uri == EXFORMS_NAMESPACE_URI
    val isXBL                      = uri == XBL_NAMESPACE_URI
    val isXFormsOrBuiltinExtension = isXForms || isXXForms || isEXForms
    def isXHTML                    = uri == XHTML_NAMESPACE_URI

    private var endElementName: Option[(String, String, String)] = None

    def startElement(uri: String, localname: String, qName: String, atts: Attributes): Unit = {
      endElementName = Some((uri, localname, qName))
      startElement2(uri, localname, qName, atts)
    }

    def endElement() = endElementName foreach {
      case (uri, localname, qName) ⇒ endElement2(uri, localname, qName)
    }

    def element(uri: String, localname: String, qName: String, atts: Attributes): Unit = {
      startElement2(uri, localname, qName, atts)
      endElement2(uri, localname, qName)
    }

    def ancestors =
      Iterator.iterate(parent.orNull)(_.parent.orNull) takeWhile (_ ne null)
  }

  private var stack: List[StackElement] = Nil

  def currentStackElement = stack.head

  def startElement(uri: String, localname: String): StackElement = {

    val parentOpt = stack.headOption

    val newStackElement =
      StackElement(
        parentOpt,
        uri,
        localname
      )

    stack ::= newStackElement
    newStackElement
  }

  def endElement(): StackElement = {
    val stackElement = currentStackElement
    stack = stack.tail
    stackElement
  }

  def doesClosestXHTMLRequireSeparatorAppearance =
    currentStackElement.ancestors find (_.isXHTML) exists (e ⇒ SeparatorAppearanceElements(e.localname))

  override def setDocumentLocator(locator: Locator): Unit = {
    this._documentLocator = locator

    if (keepLocationData) {
      if (templateReceiver ne null)
        templateReceiver.setDocumentLocator(locator)
      if (extractorReceiver ne null)
        extractorReceiver.setDocumentLocator(locator)
    }
  }

  override def startDocument(): Unit = {
    if (templateReceiver ne null)
      templateReceiver.startDocument()
    if (extractorReceiver ne null)
      extractorReceiver.startDocument()
  }

  override def endDocument(): Unit = {
    if (templateReceiver ne null)
      templateReceiver.endDocument()
    if (extractorReceiver ne null)
      extractorReceiver.endDocument()
  }

  // TODO: Fix endElement() then enable below
  private def isOutputToTemplate =
    (templateReceiver ne null) && ! isInXBLBinding // && ! (inHead && inXForms && ! inTitle);

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    if (length > 0) {
      if (isOutputToTemplate)
        templateReceiver.characters(ch, start, length)
      if (extractorReceiver ne null)
        extractorReceiver.characters(ch, start, length)
    }

  def endPrefixMapping(prefix: String): Unit = ()

  override def processingInstruction(target: String, data: String): Unit =
    if (isInPreserve) {
      // Preserve comments within e.g. instances
      if (isOutputToTemplate)
        templateReceiver.processingInstruction(target, data)
      if (extractorReceiver ne null)
        extractorReceiver.processingInstruction(target, data)
    }

  override def comment(ch: Array[Char], start: Int, length: Int): Unit =
    if (isInPreserve) {
      // Preserve comments within e.g. instances
      if (isOutputToTemplate)
        templateReceiver.comment(ch, start, length)
      if (extractorReceiver ne null)
        extractorReceiver.comment(ch, start, length)
    }

  private def startElement2(namespaceURI: String, localName: String, qName: String, atts: Attributes): Unit = {
    if (isOutputToTemplate)
      templateReceiver.startElement(namespaceURI, localName, qName, atts)

    if (extractorReceiver ne null)
      extractorReceiver.startElement(namespaceURI, localName, qName, atts)
  }

  def endElement2(namespaceURI: String, localName: String, qName: String): Unit = {
    if (isOutputToTemplate)
      templateReceiver.endElement(namespaceURI, localName, qName)

    if (extractorReceiver ne null)
      extractorReceiver.endElement(namespaceURI, localName, qName)
  }

  def startPrefixMapping2(prefix: String, uri: String): Unit = {
    if (isOutputToTemplate)
      templateReceiver.startPrefixMapping(prefix, uri)

    if (extractorReceiver ne null)
      extractorReceiver.startPrefixMapping(prefix, uri)
  }

  def endPrefixMapping2(prefix: String): Unit = {
    if (isOutputToTemplate)
      templateReceiver.endPrefixMapping(prefix)

    if (extractorReceiver ne null)
      extractorReceiver.endPrefixMapping(prefix)
  }
}
