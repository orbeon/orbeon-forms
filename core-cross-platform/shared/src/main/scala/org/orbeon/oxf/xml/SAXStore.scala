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
package org.orbeon.oxf.xml

import java.{lang => jl, util => ju}

import org.xml.sax.helpers.AttributesImpl
import org.xml.sax.{Attributes, Locator, SAXException}

import scala.annotation.tailrec
import scala.util.control.Breaks.{break, breakable}


/**
 * SAXStore keeps a compact representation of SAX events sent to the ContentHandler interface.
 *
 * As of June 2009, we increase the size of buffers by 50% instead of 100%. Still not the greatest way. Possibly,
 * passed a threshold, say 10 MB or 20 MB, we could use a linked list of such big blocks.
 *
 * TODO: Handling of system IDs is not optimal in memory as system IDs are unlikely to change much within a document.
 */
private object SAXStore {

  val StartDocument        : Byte = 0x00
  val EndDocument          : Byte = 0x01
  val StartElement         : Byte = 0x02
  val EndElement           : Byte = 0x03
  val Characters           : Byte = 0x04
  val EndPrefixMapping     : Byte = 0x05
  val IgnWhitespace        : Byte = 0x06
  val Pi                   : Byte = 0x07
  val SkippedEntity        : Byte = 0x09
  val StartPrefixMapping   : Byte = 0x0A
  val Comment              : Byte = 0x0B

  val InitialSize          : Int  = 10
}

final class SAXStore extends ForwardingXMLReceiver {

  thisSAXStore =>

  private[oxf] var eventBuffer                  : Array[Byte] = null
  private[oxf] var eventBufferPosition          : Int = 0

  private[oxf] var charBuffer                   : Array[Char] = null
  private[oxf] var charBufferPosition           : Int = 0

  private[oxf] var intBuffer                    : Array[Int] = null
  private[oxf] var intBufferPosition            : Int = 0

  private[oxf] var lineBuffer                   : Array[Int] = null
  private[oxf] var lineBufferPosition           : Int = 0

  private[oxf] var systemIdBuffer               : Array[String] = null
  private[oxf] var systemIdBufferPosition       : Int = 0

  private[oxf] var attributeCountBuffer         : Array[Int] = null
  private[oxf] var attributeCountBufferPosition : Int = 0
  private[oxf] var attributeCount               : Int = 0

  private[oxf] var stringBuilder                 = new ju.ArrayList[String]

  private[oxf] var hasDocumentLocator           : Boolean = false
  private[oxf] var publicId                     : String = null

  private[oxf] var locator                      : Locator = null // used only for recording events, MUST be cleared afterwards

  private[oxf] val StartMark = new Mark

  private[oxf] var marks: ju.ArrayList[Mark] = null

  def clear(): Unit = {
    eventBufferPosition = 0
    eventBuffer = new Array[Byte](SAXStore.InitialSize)

    charBufferPosition = 0
    charBuffer = new Array[Char](SAXStore.InitialSize * 4)

    intBufferPosition = 0
    intBuffer = new Array[Int](SAXStore.InitialSize)

    lineBufferPosition = 0
    lineBuffer = new Array[Int](SAXStore.InitialSize)

    systemIdBufferPosition = 0
    systemIdBuffer = new Array[String](SAXStore.InitialSize)

    attributeCountBufferPosition = 0
    attributeCountBuffer = new Array[Int](SAXStore.InitialSize)

    stringBuilder.clear()
    locator = null
  }

  // Main constructor
  locally {
    clear()
  }

  def this(xmlReceiver: XMLReceiver) = {
    this()
    super.setXMLReceiver(xmlReceiver)
  }

  class Mark private[SAXStore] () {

    private[oxf] var _id: String = null
    def id: String = _id

    private[oxf] var eventBufferPosition          = 0
    private[oxf] var charBufferPosition           = 0
    private[oxf] var intBufferPosition            = 0
    private[oxf] var lineBufferPosition           = 0
    private[oxf] var systemIdBufferPosition       = 0
    private[oxf] var attributeCountBufferPosition = 0
    private[oxf] var stringBuilderPosition        = 0

    def this(store: SAXStore, id: String) = {
      this()
      this._id = id
      this.eventBufferPosition          = store.eventBufferPosition
      this.charBufferPosition           = store.charBufferPosition
      this.intBufferPosition            = store.intBufferPosition
      this.lineBufferPosition           = store.lineBufferPosition
      this.systemIdBufferPosition       = store.systemIdBufferPosition
      this.attributeCountBufferPosition = store.attributeCountBufferPosition
      this.stringBuilderPosition        = store.stringBuilder.size
      rememberMark()
    }

    def this(values: Array[Int], id: String) = {
      this()
      this._id = id

      var i = 0
      this.eventBufferPosition = values(i)
      i+=1
      this.charBufferPosition = values(i)
      i+=1
      this.intBufferPosition = values(i)
      i+=1
      this.lineBufferPosition = values(i)
      i+=1
      this.systemIdBufferPosition = values(i)
      i+=1
      this.attributeCountBufferPosition = values(i)
      i+=1
      this.stringBuilderPosition = values(i)
      i+=1

      rememberMark()
    }

    private def rememberMark(): Unit = {
      // Keep a reference to marks, so that they can be serialized/deserialized along with the `SAXStore`
      if (marks eq null)
        marks = new ju.ArrayList[Mark]
      marks.add(this)
    }

    @throws[SAXException]
    def replay(xmlReceiver: XMLReceiver): Unit =
      thisSAXStore.replay(xmlReceiver, this)

    def saxStore: SAXStore = thisSAXStore
  }

  def newMark(values: Array[Int], id: String): Mark =
    new Mark(values, id)

  // For debugging only
  def getApproximateSize: Int = {

    var size = eventBufferPosition * 4

    size += charBufferPosition
    size += intBufferPosition * 4
    size += lineBufferPosition * 4

    var previousId: String = null

    for (i <- systemIdBuffer.indices) {
      val currentId = systemIdBuffer(i)
      // This is rough, but entries in the list could point to the same string, so we try to detect this case.
      if (currentId != null && (currentId ne previousId))
        size += currentId.length * 2
      previousId = currentId
    }
    size += attributeCountBufferPosition * 4
    var previousString: String = null
    val i = stringBuilder.iterator
    while (i.hasNext) {
      val currentString = i.next
      if (currentString != null && (currentString ne previousString))
        size += currentString.length * 2
      previousString = currentString
    }
    size
  }

  def getAttributesCount: Int = attributeCount

  def getValidity: AnyRef = jl.Long.valueOf(eventBuffer.hashCode * charBuffer.hashCode * intBuffer.hashCode)

  @throws[SAXException]
  def replay(xmlReceiver: XMLReceiver): Unit =
    replay(xmlReceiver, StartMark)

  @throws[SAXException]
  def replay(xmlReceiver: XMLReceiver, mark: Mark): Unit = {

    var intBufferPos            = mark.intBufferPosition
    var charBufferPos           = mark.charBufferPosition
    var stringBuilderPos        = mark.stringBuilderPosition
    var attributeCountBufferPos = mark.attributeCountBufferPosition
    val lineBufferPos           = Array(mark.lineBufferPosition)
    val systemIdBufferPos       = Array(mark.systemIdBufferPosition)
    val attributes              = new AttributesImpl
    var currentEventPosition    = mark.eventBufferPosition

    val outputLocator =
      if (! hasDocumentLocator)
        null
      else
        new Locator {

          def getPublicId: String = publicId

          def getSystemId: String =
            try systemIdBuffer(systemIdBufferPos(0))
            catch {
              case _: ArrayIndexOutOfBoundsException => null
            }

          def getLineNumber: Int =
            try lineBuffer(lineBufferPos(0))
            catch {
              case _: ArrayIndexOutOfBoundsException => -1
            }

          def getColumnNumber: Int =
            try lineBuffer(lineBufferPos(0) + 1)
            catch {
              case _: ArrayIndexOutOfBoundsException => -1
            }
        }

    if (hasDocumentLocator)
      xmlReceiver.setDocumentLocator(outputLocator)

    // Handle element marks
    val handleElementMark = (mark ne StartMark) && (eventBuffer(currentEventPosition) == SAXStore.StartElement)

    var elementLevel = 0
    breakable {
      while (currentEventPosition < eventBufferPosition) {
        val eventType = eventBuffer(currentEventPosition)
        val eventHasLocation = hasDocumentLocator && eventType != SAXStore.EndPrefixMapping && eventType != SAXStore.StartPrefixMapping
        eventType match {
          case SAXStore.StartDocument =>
            xmlReceiver.startDocument()
          case SAXStore.StartElement =>

            val elemNamespaceURI = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            val elemLocalName = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            val elemQualifiedName = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1

            attributes.clear()
            val attributeCount = attributeCountBuffer(attributeCountBufferPos)
            attributeCountBufferPos += 1
            for (_ <- 0 until attributeCount) {
              val uri = stringBuilder.get(stringBuilderPos)
              stringBuilderPos += 1
              val attLocalName = stringBuilder.get(stringBuilderPos)
              stringBuilderPos += 1
              val attQualifiedName = stringBuilder.get(stringBuilderPos)
              stringBuilderPos += 1
              val attType = stringBuilder.get(stringBuilderPos)
              stringBuilderPos += 1
              val attValue = stringBuilder.get(stringBuilderPos)
              stringBuilderPos += 1

              attributes.addAttribute(uri, attLocalName, attQualifiedName, attType, attValue)
            }

            xmlReceiver.startElement(elemNamespaceURI, elemLocalName, elemQualifiedName, attributes)
            elementLevel += 1
          case SAXStore.Characters =>
            val length = intBuffer(intBufferPos)
            intBufferPos += 1
            xmlReceiver.characters(charBuffer, charBufferPos, length)
            charBufferPos += length
          case SAXStore.EndElement =>
            elementLevel -= 1

            val elemNamespaceURI = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            val elemLocalName = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            val elemQualifiedName = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1

            xmlReceiver.endElement(elemNamespaceURI, elemLocalName, elemQualifiedName)

            if (handleElementMark && elementLevel == 0) // back to ground level, we are done!
              break()

          case SAXStore.EndDocument =>
            xmlReceiver.endDocument()
          case SAXStore.EndPrefixMapping =>
            xmlReceiver.endPrefixMapping(stringBuilder.get(stringBuilderPos))
            stringBuilderPos += 1
          case SAXStore.IgnWhitespace =>
            val length = intBuffer(intBufferPos)
            intBufferPos += 1
            xmlReceiver.ignorableWhitespace(charBuffer, charBufferPos, length)
            charBufferPos += length
          case SAXStore.Pi =>
            val target = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            val data = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            xmlReceiver.processingInstruction(target, data)
          case SAXStore.SkippedEntity =>
            xmlReceiver.skippedEntity(stringBuilder.get(stringBuilderPos))
            stringBuilderPos += 1
          case SAXStore.StartPrefixMapping =>
            val prefix = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            val uri = stringBuilder.get(stringBuilderPos)
            stringBuilderPos += 1
            xmlReceiver.startPrefixMapping(prefix, uri)
          case SAXStore.Comment =>
            val length = intBuffer(intBufferPos)
            intBufferPos += 1
            xmlReceiver.comment(charBuffer, charBufferPos, length)
            charBufferPos += length
        }
        currentEventPosition += 1
        if (eventHasLocation) {
          lineBufferPos(0) += 2
          systemIdBufferPos(0) += 1
        }
      }
    }
  }

  // NOTE: This must be called *before* the `startElement()` event that will be the first element associated with the mark.
  def createAndRememberMark(id: String): Mark =
    new Mark(this, id)

  // Return all the marks created
  def getMarks: ju.List[Mark] =
    if (marks ne null)
      marks
    else
      ju.Collections.emptyList[Mark]

//  // Print to System.out. For debug only.
//  def printOut() {
//    try {
//      val th = TransformerUtils.getIdentityTransformerHandler
//      th.setResult(new StreamResult(System.out))
//      th.startDocument()
//      replay(th)
//      th.endDocument()
//    } catch {
//      case e: SAXException =>
//        throw new OXFException(e)
//    }
//  }

//  // This outputs the content to the SAXLoggerProcessor logger. For debug only.
//  def logContents() {
//    try replay(new SAXLoggerProcessor.DebugXMLReceiver)
//    catch {
//      case e: SAXException =>
//        throw new OXFException(e)
//    }
//  }

//  def getDocument = try {
//    val ch = new LocationSAXContentHandler
//    replay(ch)
//    ch.getDocument
//  } catch {
//    case e: SAXException =>
//      throw new OXFException(e)
//  }

  @throws[SAXException]
  override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
    addToEventBuffer(SAXStore.Characters)
    addToCharBuffer(chars, start, length)
    addToIntBuffer(length)
    addLocation()
    super.characters(chars, start, length)
  }

  @throws[SAXException]
  override def endDocument(): Unit = {
    addToEventBuffer(SAXStore.EndDocument)
    addLocation()
    super.endDocument()
    // The resulting `SAXStore` must never keep references to whoever filled it
    locator = null
  }

  @throws[SAXException]
  override def endElement(uri: String, localname: String, qName: String): Unit = {
    addToEventBuffer(SAXStore.EndElement)
    addLocation()
    stringBuilder.add(uri)
    stringBuilder.add(localname)
    stringBuilder.add(qName)
    super.endElement(uri, localname, qName)
  }

  @throws[SAXException]
  override def endPrefixMapping(s: String): Unit = {
    addToEventBuffer(SAXStore.EndPrefixMapping)
    // NOTE: We don't keep location data for this event as it is very unlikely to be used
    stringBuilder.add(s)
    super.endPrefixMapping(s)
  }

  @throws[SAXException]
  override def ignorableWhitespace(chars: Array[Char], start: Int, length: Int): Unit = {
    addToEventBuffer(SAXStore.IgnWhitespace)
    addToCharBuffer(chars, start, length)
    addToIntBuffer(length)
    addLocation()
    super.ignorableWhitespace(chars, start, length)
  }

  @throws[SAXException]
  override def processingInstruction(s: String, s1: String): Unit = {
    addToEventBuffer(SAXStore.Pi)
    addLocation()
    stringBuilder.add(s)
    stringBuilder.add(s1)
    super.processingInstruction(s, s1)
  }

  override def setDocumentLocator(locator: Locator): Unit = {
    this.hasDocumentLocator = locator != null
    this.locator = locator
    super.setDocumentLocator(locator)
  }

  @throws[SAXException]
  override def skippedEntity(s: String): Unit = {
    addToEventBuffer(SAXStore.SkippedEntity)
    addLocation()
    stringBuilder.add(s)
    super.skippedEntity(s)
  }

  @throws[SAXException]
  override def startDocument(): Unit = {
    addToEventBuffer(SAXStore.StartDocument)
    addLocation()
    super.startDocument()
  }

  @throws[SAXException]
  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    addToEventBuffer(SAXStore.StartElement)
    if (locator ne null) {
      addToLineBuffer(locator.getLineNumber)
      addToLineBuffer(locator.getColumnNumber)
      addToSystemIdBuffer(locator.getSystemId)
      if ((publicId eq null) && (locator.getPublicId ne null))
        publicId = locator.getPublicId
    }
    stringBuilder.add(uri)
    stringBuilder.add(localname)
    stringBuilder.add(qName)
    addToAttributeBuffer(attributes)
    super.startElement(uri, localname, qName, attributes)
  }

  @throws[SAXException]
  override def startPrefixMapping(s: String, s1: String): Unit = {
    addToEventBuffer(SAXStore.StartPrefixMapping)
    stringBuilder.add(s)
    stringBuilder.add(s1)
    super.startPrefixMapping(s, s1)
  }

  @throws[SAXException]
  override def comment(ch: Array[Char], start: Int, length: Int): Unit = {
    addToEventBuffer(SAXStore.Comment)
    addToCharBuffer(ch, start, length)
    addToIntBuffer(length)
    addLocation()
    super.comment(ch, start, length)
  }

  private def addLocation(): Unit =
    if (locator ne null) {
      addToLineBuffer(locator.getLineNumber)
      addToLineBuffer(locator.getColumnNumber)
      addToSystemIdBuffer(locator.getSystemId)
    }

  @tailrec
  private def addToCharBuffer(chars: Array[Char], start: Int, length: Int): Unit =
    if (charBuffer.length - charBufferPosition <= length) {
      val old = charBuffer
      charBuffer = new Array[Char](old.length * 3 / 2 + 1)
      System.arraycopy(old, 0, charBuffer, 0, charBufferPosition)
      addToCharBuffer(chars, start, length)
    } else {
      System.arraycopy(chars, start, charBuffer, charBufferPosition, length)
      charBufferPosition += length
    }

  @tailrec
  private def addToIntBuffer(i: Int): Unit =
    if (intBuffer.length - intBufferPosition == 1) {
      val old = intBuffer
      intBuffer = new Array[Int](old.length * 3 / 2 + 1)
      System.arraycopy(old, 0, intBuffer, 0, intBufferPosition)
      addToIntBuffer(i)
    } else {
      intBuffer(intBufferPosition) = i
      intBufferPosition += 1
    }

  @tailrec
  private def addToLineBuffer(i: Int): Unit =
    if (lineBuffer.length - lineBufferPosition == 1) {
      val old = lineBuffer
      lineBuffer = new Array[Int](old.length * 3 / 2 + 1)
      System.arraycopy(old, 0, lineBuffer, 0, lineBufferPosition)
      addToLineBuffer(i)
    } else {
      lineBuffer(lineBufferPosition) = i
      lineBufferPosition += 1
    }

  @tailrec
  private def addToSystemIdBuffer(systemId: String): Unit =
    // Try to detect contiguous system ids
    //
    // NOTE: This native method won't work during replay, will need to store number of contiguous identical strings
    // as well, and/or use intern().
    //        if (systemIdBufferPosition > 0 && systemIdBuffer[systemIdBufferPosition] == systemId) {
    //            return;
    //        }
    if (systemIdBuffer.length - systemIdBufferPosition == 1) {
      val old = systemIdBuffer
      systemIdBuffer = new Array[String](old.length * 3 / 2 + 1)
      System.arraycopy(old, 0, systemIdBuffer, 0, systemIdBufferPosition)
      addToSystemIdBuffer(systemId)
    } else {
      systemIdBuffer(systemIdBufferPosition) = systemId
      systemIdBufferPosition += 1
    }

  @tailrec
  private def addToEventBuffer(b: Byte): Unit =
    if (eventBuffer.length - eventBufferPosition == 1) {
      val old = eventBuffer
      eventBuffer = new Array[Byte](old.length * 3 / 2 + 1)
      System.arraycopy(old, 0, eventBuffer, 0, eventBufferPosition)
      addToEventBuffer(b)
    } else {
      eventBuffer(eventBufferPosition) = b
      eventBufferPosition += 1
    }

  @tailrec
  private def addToAttributeBuffer(attributes: Attributes): Unit =
    if (attributeCountBuffer.length - attributeCountBufferPosition == 1) {
      val old = attributeCountBuffer
      attributeCountBuffer = new Array[Int](old.length * 3 / 2 + 1)
      System.arraycopy(old, 0, attributeCountBuffer, 0, attributeCountBufferPosition)
      addToAttributeBuffer(attributes)
    } else {
      val count = attributes.getLength
      attributeCountBuffer(attributeCountBufferPosition) = count
      attributeCountBufferPosition += 1
      attributeCount += count
      for (i <- 0 until attributes.getLength) {
        stringBuilder.add(attributes.getURI(i))
        stringBuilder.add(attributes.getLocalName(i))
        stringBuilder.add(attributes.getQName(i))
        stringBuilder.add(attributes.getType(i))
        stringBuilder.add(attributes.getValue(i))
      }
    }
}