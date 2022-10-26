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
package org.orbeon.oxf.xforms.processor.handlers

import org.orbeon.oxf.xml._
import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.{Attributes, ContentHandler}
import java.{lang => jl}


// Intercept SAX output and annotate resulting elements and/or text with classes and spans.
class OutputInterceptor(
  output                     : XMLReceiver,
  spanQName                  : String,
  beginDelimiterListener     : OutputInterceptor => Unit,
  isAroundTableOrListElement : Boolean
) extends ForwardingXMLReceiver(output) {

  private var gotElements = false

  // Default to <xhtml:span>
  private var delimiterNamespaceURI = XMLConstants.XHTML_NAMESPACE_URI
  private var delimiterPrefix       = XMLUtils.prefixFromQName(spanQName)
  private var delimiterLocalName    = XMLUtils.localNameFromQName(spanQName)

  private var addedClasses: String = null
  private var mustGenerateFirstDelimiters = true
  private var level = 0
  private val currentCharacters = new jl.StringBuilder

  private val reusableAttributes = new AttributesImpl

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

    level += 1

    val topLevelElement = level == 1

    if (! gotElements) { // Override default as we just go an element
      assert(topLevelElement)
      delimiterNamespaceURI = uri
      delimiterPrefix       = XMLUtils.prefixFromQName(qName)
      delimiterLocalName    = XMLUtils.localNameFromQName(qName)

      gotElements = true
    }

    flushCharacters(finalFlush = false, topLevelCharacters = topLevelElement)
    generateFirstDelimitersIfNeeded()

    // Add or update classes on element if needed
    super.startElement(uri, localname, qName, if (topLevelElement) getAttributesWithClass(attributes) else attributes)
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    flushCharacters(finalFlush = false, topLevelCharacters = false)
    super.endElement(uri, localname, qName)

    level -= 1
  }

  override def characters(chars: Array[Char], start: Int, length: Int): Unit =
    currentCharacters.append(chars, start, length)

  def flushCharacters(finalFlush: Boolean, topLevelCharacters: Boolean): Unit = {

    val currentString = currentCharacters.toString

    if (topLevelCharacters && ! isAroundTableOrListElement) {
      // We handle top-level characters specially and wrap them in a span so we can hide them
      generateTopLevelSpanWithCharacters(currentCharacters.toString)
    } else if (currentString.nonEmpty) {
      // Just output characters as is in deeper levels, or when around at table or list element
      val chars = currentString.toCharArray
      super.characters(chars, 0, chars.length)
    }
    currentCharacters.setLength(0)

    if (finalFlush)
      generateFirstDelimitersIfNeeded()
  }

  def generateFirstDelimitersIfNeeded(): Unit = {
    if (mustGenerateFirstDelimiters) {
      beginDelimiterListener(this)
      mustGenerateFirstDelimiters = false
    }
  }

  def setAddedClasses(addedClasses: String): Unit =
    this.addedClasses = addedClasses

  def outputDelimiter(contentHandler: ContentHandler, classes: String, id: String): Unit = {

    reusableAttributes.clear()

    if (id ne null)
      reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, id)

    if (classes ne null)
      reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, classes)

    val delimiterQName = XMLUtils.buildQName(delimiterPrefix, delimiterLocalName)
    contentHandler.startElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName, reusableAttributes)
    contentHandler.endElement(delimiterNamespaceURI, delimiterLocalName, delimiterQName)
  }

  private def generateTopLevelSpanWithCharacters(characters: String): Unit = {

    // The first element received determines the type of separator
    generateFirstDelimitersIfNeeded()

    // Wrap any other text within an xhtml:span
    super.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, getAttributesWithClass(SaxSupport.EmptyAttributes))

    if (characters.nonEmpty) {
      val chars = characters.toCharArray
      super.characters(chars, 0, chars.length)
    }
    super.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName)
  }

  private def getAttributesWithClass(originalAttributes: Attributes): Attributes = {

    var newClassAttribute = originalAttributes.getValue("class")

    if (addedClasses != null && addedClasses.length > 0) {
      if (newClassAttribute == null || newClassAttribute.length == 0)
        newClassAttribute = addedClasses
      else
        newClassAttribute += " " + addedClasses
    }

    if (newClassAttribute != null)
      XMLReceiverSupport.addOrReplaceAttribute(originalAttributes, "", "", "class", newClassAttribute)
    else
      originalAttributes
  }
}