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

import BinaryTextXMLReceiver._
import java.io._
import org.apache.commons.lang3.StringUtils.isNotBlank
import org.dom4j.Namespace
import org.dom4j.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.api.ExternalContext.Response
import org.orbeon.oxf.pipeline.api.{XMLReceiver, ExternalContext}
import org.orbeon.oxf.util.Base64XMLReceiver
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.NetUtils.{getContentTypeMediaType, getContentTypeCharset}
import org.orbeon.oxf.util.ScalaUtils.nonEmptyOrNone
import org.orbeon.oxf.util.TextXMLReceiver
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.scaxon.XML
import org.xml.sax.Attributes
import scala.collection.mutable

/**
 * ContentHandler able to serialize text or binary documents to an output stream.
 */
class BinaryTextXMLReceiver(
        output: Either[Response, OutputStream], // one of those is required
        closeStream: Boolean,                   // whether to close the stream upon endDocument()
        forceContentType: Boolean,
        requestedContentType: Option[String],
        ignoreDocumentContentType: Boolean,
        forceEncoding: Boolean,
        requestedEncoding: Option[String],
        ignoreDocumentEncoding: Boolean)
    extends XMLReceiverAdapter {

    require(! forceContentType || isNotBlank(requestedContentType.get))
    require(! forceEncoding    || isNotBlank(requestedEncoding.get))

    val response     = output.left.toOption
    val outputStream = response map (_.getOutputStream) getOrElse output.right.get

    private val prefixMappings = new mutable.HashMap[String, String]

    private var elementLevel: Int = 0
    private var writer: Writer = null
    private var outputReceiver: XMLReceiver = null

    // For Java callers
    def this(response: ExternalContext.Response,
            outputStream: OutputStream,
            closeStream: Boolean,
            forceContentType: Boolean,
            requestedContentType: String,
            ignoreDocumentContentType: Boolean,
            forceEncoding: Boolean,
            requestedEncoding: String,
            ignoreDocumentEncoding: Boolean) =
        this(
            if (response ne null) Left(response) else Right(outputStream),
            closeStream,
            forceContentType,
            nonEmptyOrNone(requestedContentType),
            ignoreDocumentContentType,
            forceEncoding,
            nonEmptyOrNone(requestedEncoding),
            ignoreDocumentEncoding)


    // Simple constructor to write to a stream and close it
    def this(outputStream: OutputStream) =
        this(Right(outputStream), true, false, None, false, false, None, false)

    // Record definitions only before root element arrives
    override def startPrefixMapping(prefix: String, uri: String): Unit =
        if (elementLevel == 0)
            prefixMappings.put(prefix, uri)

    override def startElement(namespaceURI: String, localName: String, qName: String, attributes: Attributes): Unit = {
        elementLevel += 1

        if (elementLevel == 1) {
            // This is the root element

            // Get xsi:type attribute and determine whether the input is binary or text

            val xsiType = Option(attributes.getValue(XSI_TYPE_QNAME.getNamespaceURI, XSI_TYPE_QNAME.getName)) getOrElse
                (throw new OXFException("Root element must contain an xsi:type attribute"))

            val (typePrefix, typeLocalName) = XML.parseQName(xsiType)

            val typeNamespaceURI = prefixMappings.get(typePrefix) getOrElse
                (throw new OXFException("Undeclared prefix in xsi:type: " + typePrefix))

            val isBinaryInput = QName.get(typeLocalName, Namespace.get(typePrefix, typeNamespaceURI)) match {
                case XS_BASE64BINARY_QNAME ⇒ true
                case XS_STRING_QNAME ⇒ false
                case _ ⇒ throw new OXFException("Type xs:string or xs:base64Binary must be specified")
            }

            // Set last-modified, Content-Disposition and status code when available
            response foreach { response ⇒

                // This will override caching settings which may have taken place before
                nonEmptyOrNone(attributes.getValue("last-modified")) foreach
                    (validity ⇒ response.setPageCaching(DateUtils.parseRFC1123(validity)))

                nonEmptyOrNone(attributes.getValue("filename")) foreach
                    (fileName ⇒ response.setHeader("Content-Disposition", "attachment; filename=" + fileName))

                nonEmptyOrNone(attributes.getValue("status-code")) foreach
                    (statusCode ⇒ response.setStatus(statusCode.toInt))
            }

            // Set ContentHandler and headers depending on input type
            val contentTypeAttribute = Option(attributes.getValue("content-type"))
            if (isBinaryInput) {
                response foreach { response ⇒
                    // Get content-type and encoding

                    if (! forceContentType && ! ignoreDocumentContentType && ! forceEncoding && ! ignoreDocumentEncoding && contentTypeAttribute.isDefined) {
                        // Simple case where we just forward what's coming in
                        response.setContentType(contentTypeAttribute.get)
                    } else {
                        // Otherwise try some funky logic based on the configuration
                        val contentType = getContentType(contentTypeAttribute, DefaultBinaryContentType)
                        val encoding    = getEncoding(contentTypeAttribute, CachedSerializer.DEFAULT_ENCODING)

                        // Output encoding only for text content types, defaulting to utf-8
                        // NOTE: The "binary" mode doesn't mean the content is binary, it could be text as well. So we
                        // output a charset when possible.
                        if (XMLUtils.isTextOrJSONContentType(contentType))
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
        if (target == "oxf-serializer") {
            if ((data ne null) && data.startsWith("status-code=\"")) {
                val endIndex = data.indexOf('"', 13)
                if (endIndex != -1) {
                    val codeString = data.substring(13, endIndex)
                    response foreach (_.setStatus(codeString.toInt))
                }
            } else if (data == "flush") {
                if (writer ne null)
                    writer.flush()

                outputStream.flush()
            }
        } else
            super.processingInstruction(target, data)

    // Content type determination algorithm
    private def getContentType(contentTypeAttribute: Option[String], defaultContentType: String) =
        if (forceContentType)
            requestedContentType.get
        else if (ignoreDocumentContentType)
            requestedContentType getOrElse defaultContentType
        else
            contentTypeAttribute map getContentTypeMediaType getOrElse defaultContentType

    // Encoding determination algorithm
    private def getEncoding(contentTypeAttribute: Option[String], defaultEncoding: String) =
        if (forceEncoding)
            requestedEncoding.get
        else if (ignoreDocumentEncoding)
            requestedEncoding getOrElse defaultEncoding
        else
            contentTypeAttribute map getContentTypeCharset getOrElse defaultEncoding
}

object BinaryTextXMLReceiver {
    val DefaultBinaryContentType = "application/octet-stream"
    val DefaultTextContentType   = "text/plain"
}