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
package org.orbeon.oxf.processor.serializer

import java.io._

import org.apache.commons.lang3.StringUtils.isNotBlank
import org.orbeon.dom.{Namespace, QName}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Response
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver._
import org.orbeon.oxf.util.ContentTypes.{getContentTypeCharset, getContentTypeMediaType}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Base64XMLReceiver, ContentTypes, DateUtils, TextXMLReceiver}
import org.orbeon.oxf.xml.SaxonUtils.parseQName
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverAdapter}
import org.xml.sax.Attributes

import scala.collection.mutable

/**
 * ContentHandler able to serialize text or binary documents to an output stream.
 */
class BinaryTextXMLReceiver(
  output                    : Either[Response, OutputStream], // one of those is required
  closeStream               : Boolean,                        // whether to close the stream upon endDocument()
  forceContentType          : Boolean,
  requestedContentType      : Option[String],
  ignoreDocumentContentType : Boolean,
  forceEncoding             : Boolean,
  requestedEncoding         : Option[String],
  ignoreDocumentEncoding    : Boolean,
  headersToForward          : List[String]

) extends XMLReceiverAdapter {

  require(! forceContentType || isNotBlank(requestedContentType.get))
  require(! forceEncoding    || isNotBlank(requestedEncoding.get))

  val response     = output.left.toOption
  val outputStream = response map (_.getOutputStream) getOrElse output.right.get

  private val prefixMappings = new mutable.HashMap[String, String]

  private var elementLevel: Int = 0
  private var writer: Writer = null
  private var outputReceiver: XMLReceiver = null

  // For Java callers
  def this(
    response                  : ExternalContext.Response,
    outputStream              : OutputStream,
    closeStream               : Boolean,
    forceContentType          : Boolean,
    requestedContentType      : String,
    ignoreDocumentContentType : Boolean,
    forceEncoding             : Boolean,
    requestedEncoding         : String,
    ignoreDocumentEncoding    : Boolean,
    headersToForward          : String
  ) =
    this(
      if (response ne null) Left(response) else Right(outputStream),
      closeStream,
      forceContentType,
      requestedContentType.trimAllToOpt,
      ignoreDocumentContentType,
      forceEncoding,
      requestedEncoding.trimAllToOpt,
      ignoreDocumentEncoding,
      Option(headersToForward) map (_.splitTo[List]()) getOrElse Nil
    )

  // Simple constructor to write to a stream and close it
  def this(outputStream: OutputStream) =
    this(Right(outputStream), true, false, None, false, false, None, false, Nil)

  // Record definitions only before root element arrives
  override def startPrefixMapping(prefix: String, uri: String): Unit =
    if (elementLevel == 0)
      prefixMappings.put(prefix, uri)

  override def startElement(namespaceURI: String, localName: String, qName: String, attributes: Attributes): Unit = {
    elementLevel += 1

    if (elementLevel == 1) {
      // This is the root element

      // Get xsi:type attribute and determine whether the input is binary or text

      val xsiType = Option(attributes.getValue(XSI_TYPE_QNAME.namespace.uri, XSI_TYPE_QNAME.localName)) getOrElse
        (throw new OXFException("Root element must contain an xsi:type attribute"))

      val (typePrefix, typeLocalName) = parseQName(xsiType)

      val typeNamespaceURI =
        prefixMappings.getOrElse(typePrefix, throw new OXFException(s"Undeclared prefix in xsi:type: $typePrefix"))

      val isBinaryInput = QName(typeLocalName, Namespace(typePrefix, typeNamespaceURI)) match {
        case XS_BASE64BINARY_QNAME => true
        case XS_STRING_QNAME       => false
        case _                     => throw new OXFException("Type xs:string or xs:base64Binary must be specified")
      }

      // Set Last-Modified, Content-Disposition and status code when available
      response foreach { response =>

        // This will override caching settings which may have taken place before
        attributes.getValue(Headers.LastModifiedLower).trimAllToOpt foreach
          (validity => response.setPageCaching(DateUtils.parseRFC1123(validity)))

        attributes.getValue("filename").trimAllToOpt.foreach { fileName =>
          val isInline        = attributes.getValue("disposition-type").trimAllToOpt.contains("inline")
          val dispositionType = if (isInline) "inline" else "attachment"
          response.setHeader("Content-Disposition", s"$dispositionType; filename=$fileName")
        }

        attributes.getValue("status-code").trimAllToOpt foreach
          (statusCode => response.setStatus(statusCode.toInt))

        // Forward headers if any
        headersToForward foreach { headerName =>
          attributes.getValue(headerName).trimAllToOpt foreach { headerValue =>
            response.setHeader(headerName, headerValue)
          }
        }
      }

      // Set ContentHandler and headers depending on input type
      val contentTypeAttribute = Option(attributes.getValue(Headers.ContentTypeLower))
      if (isBinaryInput) {
        response foreach { response =>
          // Get content-type and encoding

          if (! forceContentType          &&
            ! ignoreDocumentContentType &&
            ! forceEncoding             &&
            ! ignoreDocumentEncoding    &&
            contentTypeAttribute.isDefined) {
            // Simple case where we just forward what's coming in
            response.setContentType(contentTypeAttribute.get)
          } else {
            // Otherwise try some funky logic based on the configuration
            val contentType = getContentType(contentTypeAttribute, DefaultBinaryContentType)
            val encoding    = getEncoding(contentTypeAttribute, CachedSerializer.DEFAULT_ENCODING)

            // Output encoding only for text content types, defaulting to utf-8
            // NOTE: The "binary" mode doesn't mean the content is binary, it could be text as well. So we
            // output a charset when possible.
            if (ContentTypes.isTextOrJSONContentType(contentType))
              response.setContentType(contentType + "; charset=" + encoding)
            else
              response.setContentType(contentType)
          }
        }
        outputReceiver = new Base64XMLReceiver(outputStream)
      } else {
        // Get content-type and encoding
        val contentType = getContentType(contentTypeAttribute, DefaultTextContentType)
        val encoding    = getEncoding(contentTypeAttribute, CachedSerializer.DEFAULT_ENCODING)

        // Always set the content type with a charset attribute, defaulting to utf-8
        response foreach (_.setContentType(contentType + "; charset=" + encoding))

        writer = new OutputStreamWriter(outputStream, encoding)
        outputReceiver = new TextXMLReceiver(writer)
      }
    }
  }

  override def endElement(namespaceURI: String, localName: String, qName: String): Unit =
    elementLevel -= 1

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    outputReceiver.characters(ch, start, length)

  override def endDocument(): Unit = {
    if (writer ne null)
      writer.flush()
    outputStream.flush()
    if (closeStream)
      outputStream.close()
  }

  override def processingInstruction(target: String, data: String): Unit =
    parseSerializerPI(target, data) match {
      case Some(("status-code", Some(code))) =>
        response foreach (_.setStatus(code.toInt))
      case Some(("flush", _)) =>
        if (writer ne null)
          writer.flush()

        outputStream.flush()
      case _ =>
        super.processingInstruction(target, data)
    }

  // Content type determination algorithm
  private def getContentType(contentTypeAttribute: Option[String], defaultContentType: String): String =
    if (forceContentType)
      requestedContentType.get
    else if (ignoreDocumentContentType)
      requestedContentType getOrElse defaultContentType
    else
      contentTypeAttribute flatMap getContentTypeMediaType getOrElse defaultContentType

  // Encoding determination algorithm
  private def getEncoding(contentTypeAttribute: Option[String], defaultEncoding: String): String =
    if (forceEncoding)
      requestedEncoding.get
    else if (ignoreDocumentEncoding)
      requestedEncoding getOrElse defaultEncoding
    else
      contentTypeAttribute flatMap getContentTypeCharset getOrElse defaultEncoding
}

object BinaryTextXMLReceiver {

  val DefaultBinaryContentType = "application/octet-stream"
  val DefaultTextContentType   = "text/plain"

  val PITargets = Set("orbeon-serializer", "oxf-serializer")

  private val StatusCodeRE = """status-code="([^"]*)"""".r

  def parseSerializerPI(target: String, data: String): Option[(String, Option[String])] = {
    if (PITargets(target)) {
      Option(data) collect {
        case StatusCodeRE(code) => "status-code" -> Some(code)
        case "flush"            => "flush" -> None
      }
    } else
      None
  }

  def isSerializerPI(target: String, data: String) =
    parseSerializerPI(target, data).isDefined
}