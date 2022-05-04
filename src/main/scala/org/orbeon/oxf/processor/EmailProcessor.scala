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
package org.orbeon.oxf.processor

import java.io._
import java.util.{Properties => JProperties}

import javax.activation.{DataHandler, DataSource}
import javax.mail.Message.RecipientType
import javax.mail._
import javax.mail.internet._
import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult
import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.dom.{Document, Element}
import org.orbeon.io.IOUtils._
import org.orbeon.io.{CharsetNames, StringBuilderWriter}
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.EmailProcessor._
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.LocationSAXWriter

import scala.jdk.CollectionConverters._

/**
 * This processor allows sending emails. It supports multipart messages and inline as well as out-of-line attachments.
 *
 * For some useful JavaMail information: http://java.sun.com/products/javamail/FAQ.html
 *
 * TODO:
 *
 * - built-in support for HTML could handle src="cid:*" with part/message ids
 * - build message with SAX, not DOM, so streaming of input is possible [not necessarily a big win]
 */
class EmailProcessor extends ProcessorImpl {

  import Private._

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA, ConfigNamespaceURI))

  override def start(pipelineContext: PipelineContext): Unit = {

    val dataDocument   = readInputAsOrbeonDom(pipelineContext, ProcessorImpl.INPUT_DATA)
    val messageElement = dataDocument.getRootElement

    // Get system id (will likely be null if document is generated dynamically)
    val dataInputSystemId = Option(messageElement.getData) map (_.asInstanceOf[LocationData].file) orNull

    implicit val propertySet = getPropertySet

    // Set SMTP host
    val properties = new JProperties
    val host =
      propertySet.getNonBlankString(TestSMTPHost) orElse
      valueFromElementOrProperty(messageElement, SMTPHost) getOrElse
      (throw new OXFException("Could not find SMTP host in configuration or in properties"))

    properties.setProperty("mail.smtp.host", host)

    // Create session
    val session = {

      // Get credentials if any
      val (usernameOption, passwordOption) = {
        messageElement.elementOpt("credentials") match {
          case Some(credentials) =>
            val usernameElement = credentials.elementOpt(Username)
            val passwordElement = credentials.elementOpt(Password)

            (optionalValueTrim(usernameElement), optionalValueTrim(passwordElement))
          case None =>
            (propertySet.getNonBlankString(Username), propertySet.getNonBlankString(Password))
        }
      }

      def ensureCredentials(encryption: String) =
        if (usernameOption.isEmpty)
          throw new OXFException("Credentials are required when using " + encryption.toUpperCase)

      val defaultUpdatePort: String => Unit =
        properties.setProperty("mail.smtp.port", _)

      // SSL and TLS
      val (defaultPort, updatePort) =
        valueFromElementOrProperty(messageElement, Encryption) match {
          case Some("ssl") =>
            ensureCredentials("ssl") // partly enforced by the schema, but could have been blank

            properties.setProperty("mail.smtp.auth", "true")
            properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")

            val updatePort: String => Unit = { port =>
              properties.setProperty("mail.smtp.socketFactory.port", port)
              defaultUpdatePort(port)
            }

            // Should we change the default to 587?
            // "Although some servers support port 465 for legacy secure SMTP in violation of the
            // specifications" http://en.wikipedia.org/wiki/Simple_Mail_Transfer_Protocol#Ports
            (Some("465"), updatePort)

          case Some("tls") =>
            ensureCredentials("tls") // partly enforced by the schema, but could have been blank

            properties.setProperty("mail.smtp.auth", "true")
            properties.setProperty("mail.smtp.starttls.enable", "true")

            (Some("587"), defaultUpdatePort)

          case _ =>
            (None, defaultUpdatePort)
        }

      // Set or override port depending on the encryption settings
      valueFromElementOrProperty(messageElement, SMTPPort) orElse defaultPort foreach updatePort

      usernameOption match {
        case Some(username) =>
          if (Logger.isInfoEnabled) Logger.info("Authentication")

          properties.setProperty("mail.smtp.auth", "true")

          if (Logger.isInfoEnabled) Logger.info("Username: " + usernameOption)

          Session.getInstance(properties, new Authenticator {
            override def getPasswordAuthentication: PasswordAuthentication = {
              new PasswordAuthentication(username, passwordOption getOrElse "")
            }
          })
        case None =>
          if (Logger.isInfoEnabled) Logger.info("No Authentication")
          Session.getInstance(properties)
      }
    }

    // Create message
    val message = new MimeMessage(session)

    def createAddresses(addressElement: Element): List[Address] = {
      val email = addressElement.element("email").getTextTrim // required

      addressElement.elementOpt("name") match {
        case Some(nameElement) => List(new InternetAddress(email, nameElement.getTextTrim))
        case None => InternetAddress.parse(email).toList
      }
    }

    def addRecipients(elementName: String, recipientType: RecipientType): Unit = {
      val elements  = messageElement.elements(elementName)
      val addresses = elements.flatMap(createAddresses).toArray
      message.addRecipients(recipientType, addresses)
    }

    // Set From
    message.addFrom(createAddresses(messageElement.element("from")).toArray)

    // Set Reply-To
    locally {

      messageElement.elementOpt("reply-to") foreach { replyToElem =>
        val replyTo = createAddresses(replyToElem)

        // We might be able to just call `setReplyTo` with the above, but it's unclear
        // what's the behavior in there is nothing set. Also, can there be a default?
        if (replyTo.nonEmpty)
          message.setReplyTo(replyTo.toArray)
      }
    }

    // Set To
    propertySet.getNonBlankString(TestTo) match {
      case Some(testTo) => message.addRecipient(Message.RecipientType.TO, new InternetAddress(testTo))
      case None         => addRecipients("to", Message.RecipientType.TO)
    }

    addRecipients("cc", Message.RecipientType.CC)
    addRecipients("bcc", Message.RecipientType.BCC)

    // Set headers if any
    for (headerElement <- messageElement.jElements("header").asScala) {
      val headerName  = headerElement.element("name").getTextTrim  // required
      val headerValue = headerElement.element("value").getTextTrim // required

      // NOTE: Use encodeText() in case there are non-ASCII characters
      message.addHeader(headerName, MimeUtility.encodeText(headerValue, DefaultCharacterEncoding, null))
    }

    // Set the email subject
    // The JavaMail spec is badly written and is not clear about whether this needs to be done here. But it
    // seems to use the platform's default charset, which we don't want to deal with. So we preemptively encode.
    // The result is pure ASCII so that setSubject() will not attempt to re-encode it.
    message.setSubject(MimeUtility.encodeText(messageElement.element("subject").getStringValue, DefaultCharacterEncoding, null))

    // Handle body
    val textElement = messageElement.element("text")
    val bodyElement = messageElement.element("body")
    if (textElement ne null)
      // Old deprecated mechanism (simple text body)
      message.setText(textElement.getStringValue)
    else if (bodyElement ne null)
      // New mechanism with body and parts
      handleBody(pipelineContext, dataInputSystemId, message, bodyElement)
    else
      throw new OXFException("Main text or body element not found")

    // Send message
    useAndClose(session.getTransport("smtp")) { _ =>
      Transport.send(message)
    }
  }

  private object Private {

    def handleBody(pipelineContext: PipelineContext, dataInputSystemId: String, parentPart: Part, bodyElement: Element): Unit = {

      // Find out if there are embedded parts
      val parts = bodyElement.jElementIterator("part")
      val multipartOption =
        if (bodyElement.getName == "body") {
          val bodyMultipart = bodyElement.attributeValueOpt("mime-multipart")

          if (parts.hasNext)
            bodyMultipart orElse Some("mixed")
          else if (bodyMultipart.isDefined)
            throw new OXFException("mime-multipart attribute on body element requires part children elements")
          else
            None
        } else {
          ContentTypes.getContentTypeMediaType(bodyElement.attributeValue(Headers.ContentTypeLower)) filter
          (_.startsWith("multipart/")) map
          (_.substring("multipart/".length))
        }

      multipartOption match {
        case Some(multipart) =>
          // Multipart content is requested
          val mimeMultipart = new MimeMultipart(multipart)
          while (parts.hasNext) {
            val partElement = parts.next()
            val mimeBodyPart = new MimeBodyPart
            handleBody(pipelineContext, dataInputSystemId, mimeBodyPart, partElement)
            mimeMultipart.addBodyPart(mimeBodyPart)
          }

          // Set content on parent part
          parentPart.setContent(mimeMultipart)
        case None =>
          // No multipart, just use the content of the element and add to the current part (which can be the main message)
          handlePart(pipelineContext, dataInputSystemId, parentPart, bodyElement)
      }
    }

    def handlePart(pipelineContext: PipelineContext, dataInputSystemId: String, parentPart: Part, partOrBodyElement: Element): Unit = {
      val name = partOrBodyElement.attributeValue("name")
      val contentTypeAttribute = partOrBodyElement.attributeValue(Headers.ContentTypeLower)
      val mediatype = ContentTypes.getContentTypeMediaType(contentTypeAttribute) getOrElse (throw new IllegalArgumentException)
      val charset   = ContentTypes.getContentTypeCharset(contentTypeAttribute) getOrElse DefaultCharacterEncoding

      val contentTypeWithCharset = mediatype + "; charset=" + charset

      // Either a String or a FileItem
      val content =
        partOrBodyElement.attributeValueOpt("src") match {
          case Some(src) =>
            // Content of the part is not inline

            // Generate a FileItem from the source
            val source = PartUtils.getSAXSource(EmailProcessor.this, pipelineContext, src, dataInputSystemId, mediatype)
            Left(PartUtils.handleStreamedPartContent(source)(EmailProcessor.Logger.logger))
          case None =>
            // Content of the part is inline

            // For HTML, we support inline HTML or inline XHTML for backward compatibility
            val needsRootElement   = mediatype == ContentTypes.XhtmlContentType
            val mayHaveRootElement = mediatype == ContentTypes.HtmlContentType

            if (needsRootElement && partOrBodyElement.jElements.size != 1)
              throw new ValidationException(
                s"The `<body>` or `<part>` element must contain exactly one element for ${ContentTypes.XhtmlContentType}",
                partOrBodyElement.getData.asInstanceOf[LocationData]
              )

            val hasRootElement = needsRootElement || mayHaveRootElement && ! partOrBodyElement.jElements.isEmpty

            // Create Document and convert it into a String
            val rootElement = if (hasRootElement) partOrBodyElement.jElements.get(0) else partOrBodyElement
            val partDocument = dom.Document(rootElement.deepCopy.asInstanceOf[Element])
            Right(handleInlinePartContent(partDocument, mediatype, hasRootElement))
        }

      if (! ContentTypes.isTextOrJSONContentType(mediatype)) {
        // This is binary content (including application/xml)
        content match {
          case Left(fileItem) =>
            parentPart.setDataHandler(new DataHandler(new ReadonlyDataSource {
              def getContentType = mediatype
              def getInputStream = fileItem.getInputStream
              def getName = name
            }))
          case Right(inline) =>
            val data = NetUtils.base64StringToByteArray(inline)
            parentPart.setDataHandler(new DataHandler(new SimpleBinaryDataSource(name, mediatype, data)))
        }
      } else {
        // This is text content (including text/xml)
        content match {
          case Left(fileItem) =>
            parentPart.setDataHandler(new DataHandler(new ReadonlyDataSource {
              // This always contains a charset
              def getContentType = contentTypeWithCharset
              // This is encoded with the appropriate charset (user-defined, or the default)
              def getInputStream = fileItem.getInputStream
              def getName = name
            }))
          case Right(inline) =>
            parentPart.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentTypeWithCharset, inline)))
        }
      }

      // Set content-disposition header
      partOrBodyElement.attributeValueOpt("content-disposition") foreach
        (contentDisposition => parentPart.setDisposition(contentDisposition))

      // Set content-id header
      partOrBodyElement.attributeValueOpt("content-id") foreach
        (contentId => parentPart.setHeader("content-id", "<" + contentId + ">"))
      //part.setContentID(contentId);
    }

    def handleInlinePartContent(document: Document, contentType: String, hasRootElement: Boolean) =
      if (hasRootElement) {
        // Convert nested XHTML into an HTML String
        val writer = new StringBuilderWriter
        val identity = TransformerUtils.getIdentityTransformerHandler
        identity.getTransformer.setOutputProperty(OutputKeys.METHOD, "html")
        identity.setResult(new StreamResult(writer))
        val locationSAXWriter = new LocationSAXWriter
        locationSAXWriter.setContentHandler(identity)
        locationSAXWriter.write(document)
        writer.result
      } else
        // For other types, just return the text nodes
        document.getStringValue
  }
}

private object EmailProcessor {

  val Logger = LoggerFactory.createLogger(classOf[EmailProcessor])

  val SMTPHost     = "smtp-host"
  val SMTPPort     = "smtp-port"
  val Username     = "username"
  val Password     = "password"
  val Encryption   = "encryption"

  val TestTo       = "test-to"
  val TestSMTPHost = "test-smtp-host"

  val ConfigNamespaceURI = "http://www.orbeon.com/oxf/email"

  // Use utf-8 as most email clients support it. This allows us not to have to pick an inferior encoding.
  val DefaultCharacterEncoding = CharsetNames.Utf8

  // Get Some(trimmed value of the element) or None if the element is null
  def optionalValueTrim(e: Option[Element]): Option[String] =
    e map (_.getStringValue) flatMap (_.trimAllToOpt)

  // First try to get the value from a child element, then from the properties
  def valueFromElementOrProperty(e: Element, name: String)(implicit propertySet: PropertySet): Option[String] =
    optionalValueTrim(e.elementOpt(name)) orElse propertySet.getNonBlankString(name)

  trait ReadonlyDataSource extends DataSource {
    def getOutputStream = throw new IOException("Write operation not supported")
  }

  class SimpleTextDataSource(val getName: String, val getContentType: String, text: String) extends ReadonlyDataSource {
    def getInputStream  = new ByteArrayInputStream(text.getBytes(CharsetNames.Utf8))
  }

  class SimpleBinaryDataSource(val getName: String, val getContentType: String, data: Array[Byte]) extends ReadonlyDataSource {
    def getInputStream  = new ByteArrayInputStream(data)
  }
}
