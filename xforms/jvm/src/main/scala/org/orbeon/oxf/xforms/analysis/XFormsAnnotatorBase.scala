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

import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.{SAXStore, SAXUtils, XMLReceiver, XMLReceiverUnneededEvents}
import org.orbeon.xforms.Constants.DocumentId
import org.orbeon.xforms.Namespaces
import org.orbeon.xforms.XFormsNames._
import org.xml.sax.{Attributes, Locator}
import shapeless.syntax.typeable._

abstract class XFormsAnnotatorBase(
  templateReceiver  : XMLReceiver,
  extractorReceiver : XMLReceiver,
  metadata          : Metadata,
  isTopLevel        : Boolean
) extends XMLReceiver
     with XMLReceiverUnneededEvents {

  private val keepLocationData = XFormsProperties.isKeepLocation
  private var _documentLocator: Locator = null
  def documentLocator = _documentLocator

  protected val templateSAXStoreOpt: Option[SAXStore] = templateReceiver.narrowTo[SAXStore]

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

  case class StackElement(
    parent                     : Option[StackElement],
    uri                        : String,
    localname                  : String,
    isXForms                   : Boolean,
    isXXForms                  : Boolean,
    isEXForms                  : Boolean,
    isXBL                      : Boolean,
    isXFormsOrBuiltinExtension : Boolean,
    isXHTML                    : Boolean,
    isFullUpdate               : Boolean
  ) {

    // Urgh, this is ugly
    private var endElementName: Option[(String, String, String)] = None

    def startElement(uri: String, localname: String, qName: String, atts: Attributes): Unit = {
      endElementName = Some((uri, localname, qName))
      startElement2(uri, localname, qName, atts)
    }

    def endElement(): Unit = endElementName foreach {
      case (uri, localname, qName) => endElement2(uri, localname, qName)
    }

    def element(uri: String, localname: String, qName: String, atts: Attributes): Unit = {
      startElement2(uri, localname, qName, atts)
      endElement2(uri, localname, qName)
    }

    def ancestors =
      Iterator.iterate(parent.orNull)(_.parent.orNull) takeWhile (_ ne null)
  }

  object StackElement {

    def apply(parent: Option[StackElement], uri: String, localname: String, atts: Attributes): StackElement = {

      val isXForms  = uri == XFORMS_NAMESPACE_URI
      val isXXForms = uri == XXFORMS_NAMESPACE_URI
      val isEXForms = uri == EXFORMS_NAMESPACE_URI

      StackElement(
        parent                     = parent,
        uri                        = uri,
        localname                  = localname,
        isXForms                   = isXForms,
        isXXForms                  = isXXForms,
        isEXForms                  = isEXForms,
        isXBL                      = uri == Namespaces.XBL,
        isXFormsOrBuiltinExtension = isXForms || isXXForms || isEXForms,
        isXHTML                    = uri == XHTML_NAMESPACE_URI,
        isFullUpdate               = atts.getValue(XXFORMS_UPDATE_QNAME.namespace.uri, XXFORMS_UPDATE_QNAME.localName) == XFORMS_FULL_UPDATE
      )
    }
  }

  protected def handleFullUpdateIfNeeded(
    stackElement    : StackElement,
    atts            : Attributes,
    xformsElementId : String
  ): Attributes =
    templateSAXStoreOpt map { templateSAXStore =>

      def isSwitch =
        stackElement.isXForms && stackElement.localname == "switch"

      def isCase =
        stackElement.isXForms && stackElement.localname == "case"

      // See https://github.com/orbeon/orbeon-forms/issues/4011
      def isRepeat =
        stackElement.isXForms && stackElement.localname == "repeat"

      def putMark(): Unit =
        metadata.putMark(templateSAXStore.getMark(rewriteId(xformsElementId)))

      def attsWithNewClass =
        SAXUtils.appendToClassAttribute(atts, "xforms-update-full")

      if (isSwitch && stackElement.isFullUpdate) {
        // Don't remember mark but produce class
        attsWithNewClass
      } else if (isCase && (stackElement.parent exists (_.isFullUpdate))) {
        // Remember mark but don't produce class
        putMark() // side effect: remember mark
        atts
      } else if (isRepeat || stackElement.isFullUpdate) {
        putMark() // side effect: remember mark
        attsWithNewClass
      } else {
        atts
      }
    } getOrElse
      atts

  protected def rewriteId(id: String): String = id

  private var stack: List[StackElement] = Nil

  def currentStackElement = stack.head

  def startElement(uri: String, localname: String, atts: Attributes): StackElement = {

    val parentOpt = stack.headOption

    val newStackElement =
      StackElement(
        parentOpt,
        uri,
        localname,
        atts
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
    currentStackElement.ancestors find (_.isXHTML) exists (e => SeparatorAppearanceElements(e.localname))

  def setDocumentLocator(locator: Locator): Unit = {
    this._documentLocator = locator

    if (keepLocationData) {
      if (templateReceiver ne null)
        templateReceiver.setDocumentLocator(locator)
      if (extractorReceiver ne null)
        extractorReceiver.setDocumentLocator(locator)
    }
  }

  def startDocument(): Unit = {
    if (templateReceiver ne null)
      templateReceiver.startDocument()
    if (extractorReceiver ne null)
      extractorReceiver.startDocument()

    // https://github.com/orbeon/orbeon-forms/issues/153
    metadata.addNamespaceMapping(rewriteId(DocumentId), Map.empty)
  }

  def endDocument(): Unit = {
    if (templateReceiver ne null)
      templateReceiver.endDocument()
    if (extractorReceiver ne null)
      extractorReceiver.endDocument()
  }

  // TODO: Fix endElement() then enable below
  private def isOutputToTemplate =
    (templateReceiver ne null) && ! isInXBLBinding // && ! (inHead && inXForms && ! inTitle);

  def characters(ch: Array[Char], start: Int, length: Int): Unit =
    if (length > 0) {
      if (isOutputToTemplate)
        templateReceiver.characters(ch, start, length)
      if (extractorReceiver ne null)
        extractorReceiver.characters(ch, start, length)
    }

  def endPrefixMapping(prefix: String): Unit = ()

  def processingInstruction(target: String, data: String): Unit =
    if (isInPreserve) {
      // Preserve comments within e.g. instances
      if (isOutputToTemplate)
        templateReceiver.processingInstruction(target, data)
      if (extractorReceiver ne null)
        extractorReceiver.processingInstruction(target, data)
    }

  def comment(ch: Array[Char], start: Int, length: Int): Unit =
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
