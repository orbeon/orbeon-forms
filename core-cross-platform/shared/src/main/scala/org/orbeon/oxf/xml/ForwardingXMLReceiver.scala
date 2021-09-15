/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.ext.LexicalHandler

class ForwardingXMLReceiver extends XMLReceiver {

  private var xmlReceiver   : XMLReceiver = null
  private var contentHandler: ContentHandler = null
  private var lexicalHandler: LexicalHandler = null

  private var forwardContent = false
  private var forwardLexical = false

  def this(xmlReceiver: XMLReceiver) = {
    this()
    setXMLReceiver(xmlReceiver)
  }

  def this(contentHandler: ContentHandler, lexicalHandler: LexicalHandler) = {
    this()
    this.contentHandler = contentHandler
    this.lexicalHandler = lexicalHandler
    setForward(true)
  }

  def this(contentHandler: ContentHandler) = {
    this()
    setContentHandler(contentHandler)
  }

  def getXMLReceiver: XMLReceiver = xmlReceiver

  def setContentHandler(contentHandler: ContentHandler): Unit = {
    this.xmlReceiver = null
    this.contentHandler = contentHandler
    this.lexicalHandler = null
    setForward(true)
  }

  def setXMLReceiver(xmlReceiver: XMLReceiver): Unit = {
    this.xmlReceiver = xmlReceiver
    this.contentHandler = xmlReceiver
    this.lexicalHandler = xmlReceiver
    setForward(true)
  }

  def setForward(forward: Boolean): Unit = {
    this.forwardContent = forward && contentHandler != null
    this.forwardLexical = forward && lexicalHandler != null
  }

  // ContentHandler methods
  @throws[SAXException]
  override def characters(chars: Array[Char], start: Int, length: Int): Unit =
    if (forwardContent)
      contentHandler.characters(chars, start, length)

  @throws[SAXException]
  override def endDocument(): Unit = {
    if (forwardContent)
      contentHandler.endDocument()
    setXMLReceiver(null)
  }

  @throws[SAXException]
  override def endElement(uri: String, localname: String, qName: String): Unit =
    if (forwardContent)
      contentHandler.endElement(uri, localname, qName)

  @throws[SAXException]
  override def endPrefixMapping(s: String): Unit =
    if (forwardContent)
      contentHandler.endPrefixMapping(s)

  @throws[SAXException]
  override def ignorableWhitespace(chars: Array[Char], start: Int, length: Int): Unit =
    if (forwardContent)
      contentHandler.ignorableWhitespace(chars, start, length)

  @throws[SAXException]
  override def processingInstruction(s: String, s1: String): Unit =
    if (forwardContent)
      contentHandler.processingInstruction(s, s1)

  override def setDocumentLocator(locator: Locator): Unit =
    if (forwardContent)
      contentHandler.setDocumentLocator(locator)

  @throws[SAXException]
  override def skippedEntity(s: String): Unit =
    if (forwardContent)
      contentHandler.skippedEntity(s)

  @throws[SAXException]
  override def startDocument(): Unit =
    if (forwardContent)
      contentHandler.startDocument()

  @throws[SAXException]
  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    if (forwardContent)
      contentHandler.startElement(uri, localname, qName, attributes)

  @throws[SAXException]
  override def startPrefixMapping(s: String, s1: String): Unit =
    if (forwardContent)
      contentHandler.startPrefixMapping(s, s1)

  // LexicalHandler methods
  @throws[SAXException]
  override def startDTD(name: String, publicId: String, systemId: String): Unit =
    if (forwardLexical)
      lexicalHandler.startDTD(name, publicId, systemId)

  @throws[SAXException]
  override def endDTD(): Unit =
    if (forwardLexical)
      lexicalHandler.endDTD()

  @throws[SAXException]
  override def startEntity(name: String): Unit =
    if (forwardLexical)
      lexicalHandler.startEntity(name)

  @throws[SAXException]
  override def endEntity(name: String): Unit =
    if (forwardLexical)
      lexicalHandler.endEntity(name)

  @throws[SAXException]
  override def startCDATA(): Unit =
    if (forwardLexical)
      lexicalHandler.startCDATA()

  @throws[SAXException]
  override def endCDATA(): Unit =
    if (forwardLexical)
      lexicalHandler.endCDATA()

  @throws[SAXException]
  override def comment(ch: Array[Char], start: Int, length: Int): Unit =
    if (forwardLexical)
      lexicalHandler.comment(ch, start, length)
}