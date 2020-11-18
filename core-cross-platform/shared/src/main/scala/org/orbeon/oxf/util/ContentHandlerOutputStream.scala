package org.orbeon.oxf.util

import java.io.{IOException, OutputStream}

import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.xml.XMLConstants
import org.xml.sax.ContentHandler
import org.xml.sax.helpers.AttributesImpl


private object ContentHandlerOutputStream {
  val DefaultBinaryDocumentElement = "document"
}

/**
 * An OutputStream that converts the bytes written into it into Base64-encoded characters written to
 * a ContentHandler.
 */
class ContentHandlerOutputStream(
  val contentHandler     : ContentHandler,
  val doStartEndDocument : Boolean
) extends OutputStream {

  private val byteBuffer            = new Array[Byte](76 * 3 / 4) // maximum bytes that, once decoded, can fit in a line of 76 characters
  private var currentBufferSize     = 0
  private val resultingLine         = new Array[Char](76 + 1)
  private val singleByte            = new Array[Byte](1)
  private var contentType : String  = null
  private var statusCode  : String  = null
  private var documentStarted       = false
  private var closed                = false

  def setContentType(contentType: String): Unit =
    this.contentType = contentType

  def setStatusCode(statusCode: String): Unit =
    this.statusCode = statusCode

  private def outputStartIfNeeded(): Unit =
    if (doStartEndDocument && ! documentStarted) {

      val attributes = new AttributesImpl
      attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", XMLConstants.XS_BASE64BINARY_QNAME.qualifiedName)
      if (contentType != null)
        attributes.addAttribute("", Headers.ContentTypeLower, Headers.ContentTypeLower, "CDATA", contentType)
      if (statusCode != null)
        attributes.addAttribute("", "status-code", "status-code", "CDATA", statusCode)

      contentHandler.startDocument()
      contentHandler.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI)
      contentHandler.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI)
      contentHandler.startElement("", ContentHandlerOutputStream.DefaultBinaryDocumentElement, ContentHandlerOutputStream.DefaultBinaryDocumentElement, attributes)

      documentStarted = true
    }

  private def outputEndIfNeeded(): Unit =
    if (doStartEndDocument && documentStarted) {
      contentHandler.endElement("", ContentHandlerOutputStream.DefaultBinaryDocumentElement, ContentHandlerOutputStream.DefaultBinaryDocumentElement)
      contentHandler.endPrefixMapping(XMLConstants.XSI_PREFIX)
      contentHandler.endPrefixMapping(XMLConstants.XSD_PREFIX)
      contentHandler.endDocument()
    }

  /**
   * Close this output stream. This must be called in the end if `startDocument()` was called, otherwise the document
   * won't be properly produced.
   */
  @throws[IOException]
  override def close(): Unit =
    if (!closed) {
      // Always flush
      flushBuffer()
      // Only close element and document if startDocument was called
      outputEndIfNeeded()
      closed = true
    }

  // NOTE: This will only flush on Base64 line boundaries. Is that what we want? Or
  // should we just ignore? We can't output an incomplete encoded line unless we have a
  // number of bytes in the buffer multiple of 3. Otherwise, we would have to output '='
  // characters, which do signal an end of transmission.
  @throws[IOException]
  override def flush(): Unit =
    if (! closed)
      contentHandler.processingInstruction("orbeon-serializer", "flush")

  @throws[IOException]
  override def write(b: Array[Byte]): Unit =
    addBytes(b, 0, b.length)

  @throws[IOException]
  override def write(b: Array[Byte], off: Int, len: Int): Unit =
    addBytes(b, off, len)

  @throws[IOException]
  def write(b: Int): Unit = {
    singleByte(0) = b.toByte
    addBytes(singleByte, 0, 1)
  }

  private def addBytes(b: Array[Byte], _off: Int, _len: Int): Unit = {

    if (closed)
      throw new IOException("ContentHandlerOutputStream already closed")

    var off = _off
    var len = _len

    // Check bounds
    if ((off < 0) || (len < 0) || (off > b.length) || ((off + len) > b.length))
      throw new IndexOutOfBoundsException
    else if (len == 0)
      return

    outputStartIfNeeded()

    while (len > 0) {
      // Fill buffer as much as possible
      val lenToCopy = Math.min(len, byteBuffer.length - currentBufferSize)
      System.arraycopy(b, off, byteBuffer, currentBufferSize, lenToCopy)
      off += lenToCopy
      len -= lenToCopy
      currentBufferSize += lenToCopy
      // If buffer is full, write it out
      if (currentBufferSize == byteBuffer.length) {
        val encoded = Base64.encode(byteBuffer, useLineBreaks = true) + "\n"
        // The terminating LF is already added by encode()
        encoded.getChars(0, encoded.length, resultingLine, 0)
        // Output characters
        contentHandler.characters(resultingLine, 0, encoded.length)
        // Reset counter
        currentBufferSize = 0
      }
    }
  }

  private def flushBuffer(): Unit =
    if (currentBufferSize > 0) {
      outputStartIfNeeded()
      val tempBuf = new Array[Byte](currentBufferSize)
      System.arraycopy(byteBuffer, 0, tempBuf, 0, currentBufferSize)
      val encoded = Base64.encode(tempBuf, useLineBreaks = true)
      encoded.getChars(0, encoded.length, resultingLine, 0)
      contentHandler.characters(resultingLine, 0, encoded.length)
      currentBufferSize = 0
    }
}