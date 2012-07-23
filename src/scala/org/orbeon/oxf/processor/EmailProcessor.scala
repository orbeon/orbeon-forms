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

import EmailProcessor._
import org.orbeon.oxf.util.ScalaUtils._
import collection.JavaConverters._
import java.io._
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.mail.Message.RecipientType
import javax.mail._
import javax.mail.internet._
import javax.xml.transform.OutputKeys
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.stream.StreamResult
import org.apache.commons.fileupload.FileItem
import org.dom4j.{Node, Document, Element}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.URLGenerator
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j._
import org.xml.sax._

/**
 * This processor allows sending emails. It supports multipart messages and inline as well as out-of-line attachments.
 *
 * For some useful JavaMail information: http://java.sun.com/products/javamail/FAQ.html
 *
 * TODO:
 *
 * o revise support of text/html
 * o built-in support for HTML could handle src="cid:*" with part/message ids
 * o support text/xml? or just XHTML?
 * o build message with SAX, not DOM, so streaming of input is possible [not necessarily a big win]
 */
class EmailProcessor extends ProcessorImpl {
    
    
    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA, ConfigNamespaceURI))

    override def start(pipelineContext: PipelineContext) {

        val dataDocument   = readInputAsDOM4J(pipelineContext, ProcessorImpl.INPUT_DATA)
        val messageElement = dataDocument.getRootElement

        // Get system id (will likely be null if document is generated dynamically)
        val dataInputSystemId = messageElement.getData.asInstanceOf[LocationData].getSystemID

        // Set SMTP host
        val properties = new Properties
        val host =
            nonEmptyOrNone(getPropertySet.getString(TestSMTPHost)) orElse
            optionalValueTrim(messageElement.element("smtp-host")) orElse
            nonEmptyOrNone(getPropertySet.getString(SMTPHost))     getOrElse
            (throw new OXFException("Could not find SMTP host in configuration or in properties"))

        properties.setProperty("mail.smtp.host", host)

        // Create session
        val session = {

            // Get credentials if any
            val (usernameOption, passwordOption) = {
                val credentials = messageElement.element("credentials")
                if (credentials ne null) {
                    val usernameElement = credentials.element("username")
                    val passwordElement = credentials.element("password")

                    (optionalValueTrim(usernameElement), optionalValueTrim(passwordElement))
                } else
                    (None, None)
            }

            def ensureCredentials(transport: String) =
                if (usernameOption.isEmpty)
                    throw new OXFException("Credentails are required when using SSL")

            // SSL and TLS
            optionalValueTrim(messageElement.element("encryption")) match {
                case Some("ssl") ⇒
                    ensureCredentials("ssl") // partly enforced by the schema, but could have been blank

                    properties.setProperty("mail.smtp.auth", "true")
                    properties.setProperty("mail.smtp.socketFactory.port", "465")
                    properties.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    properties.setProperty("mail.smtp.port", "465")
                case Some("tls") ⇒
                    ensureCredentials("tls") // partly enforced by the schema, but could have been blank

                    properties.setProperty("mail.smtp.auth", "true")
                    properties.setProperty("mail.smtp.starttls.enable", "true")
                    properties.setProperty("mail.smtp.port", "587")

                case _ ⇒
            }

            usernameOption match {
                case Some(username) ⇒
                    if (Logger.isInfoEnabled) Logger.info("Authentication")

                    properties.setProperty("mail.smtp.auth", "true")

                    if (Logger.isInfoEnabled) Logger.info("Username: " + usernameOption)

                    Session.getInstance(properties, new Authenticator {
                        override def getPasswordAuthentication: PasswordAuthentication = {
                            new PasswordAuthentication(username, passwordOption getOrElse "")
                        }
                    })
                case None ⇒
                    if (Logger.isInfoEnabled) Logger.info("No Authentication")
                    Session.getInstance(properties)
            }
        }

        // Override port is requested
        optionalValueTrim(messageElement.element("smtp-port")) foreach
            (properties.setProperty("mail.smtp.port", _))

        // Create message
        val message = new MimeMessage(session)

        def createAddresses(addressElement: Element): Array[Address] = {
            val email = addressElement.element("email").getTextTrim // required

            val result = addressElement.element("name") match {
                case nameElement: Element ⇒ Seq(new InternetAddress(email, nameElement.getTextTrim))
                case null                 ⇒ InternetAddress.parse(email).toList
            }

            result.toArray
        }

        def addRecipients(elementName: String, recipientType: RecipientType) =
            for (element ← Dom4jUtils.elements(messageElement, elementName).asScala) {
                val addresses = createAddresses(element)
                message.addRecipients(recipientType, addresses)
            }

        // Set From
        message.addFrom(createAddresses(messageElement.element("from")))

        // Set To
        nonEmptyOrNone(getPropertySet.getString(TestTo)) match {
            case Some(testTo) ⇒
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(testTo))
            case None ⇒
                addRecipients("to", Message.RecipientType.TO)
        }

        addRecipients("cc", Message.RecipientType.CC)
        addRecipients("bcc", Message.RecipientType.BCC)

        // Set headers if any
        for (headerElement ← Dom4jUtils.elements(messageElement, "header").asScala) {
            val headerName  = headerElement.element("name").getTextTrim  // required
            val headerValue = headerElement.element("value").getTextTrim // required

            // NOTE: Use encodeText() in case there are non-ASCII characters
            message.addHeader(headerName, MimeUtility.encodeText(headerValue, DEFAULT_CHARACTER_ENCODING, null))
        }

        // Set the email subject
        // The JavaMail spec is badly written and is not clear about whether this needs to be done here. But it
        // seems to use the platform's default charset, which we don't want to deal with. So we preemptively encode.
        // The result is pure ASCII so that setSubject() will not attempt to re-encode it.
        message.setSubject(MimeUtility.encodeText(messageElement.element("subject").getStringValue, DEFAULT_CHARACTER_ENCODING, null))

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
        useAndClose(session.getTransport("smtp")) { transport ⇒
            Transport.send(message)
        }
    }

    private def handleBody(pipelineContext: PipelineContext, dataInputSystemId: String, parentPart: Part, bodyElement: Element) {

        // Find out if there are embedded parts
        val parts = bodyElement.elementIterator("part")
        var multipart: String = null
        if ("body" == bodyElement.getName) {
            multipart = bodyElement.attributeValue("mime-multipart")
            if (multipart != null && ! parts.hasNext)
                throw new OXFException("mime-multipart attribute on body element requires part children elements")
            // TODO: Check following lines, which were doing nothing!
//            final String contentTypeFromAttribute = NetUtils.getContentTypeMediaType(bodyElement.attributeValue("content-type"));
//            if (contentTypeFromAttribute != null && contentTypeFromAttribute.startsWith("multipart/"))
//                contentTypeFromAttribute.substring("multipart/".length());

            if (parts.hasNext && multipart == null)
                multipart = DefaultMultipart
        } else {
            val contentTypeAttribute = NetUtils.getContentTypeMediaType(bodyElement.attributeValue("content-type"))
            multipart =
                if (contentTypeAttribute != null && contentTypeAttribute.startsWith("multipart/"))
                    contentTypeAttribute.substring("multipart/".length)
                else
                    null
        }
        if (multipart ne null) {
            // Multipart content is requested
            val mimeMultipart = new MimeMultipart(multipart)
            while (parts.hasNext) {
                val partElement: Element = parts.next.asInstanceOf[Element]
                val mimeBodyPart: MimeBodyPart = new MimeBodyPart
                handleBody(pipelineContext, dataInputSystemId, mimeBodyPart, partElement)
                mimeMultipart.addBodyPart(mimeBodyPart)
            }

            // Set content on parent part
            parentPart.setContent(mimeMultipart)
        } else
            // No multipart, just use the content of the element and add to the current part (which can be the main message)
            handlePart(pipelineContext, dataInputSystemId, parentPart, bodyElement)
    }

    private def handlePart(pipelineContext: PipelineContext, dataInputSystemId: String, parentPart: Part, partOrBodyElement: Element) {
        val name = partOrBodyElement.attributeValue("name")
        val contentTypeAttribute = partOrBodyElement.attributeValue("content-type")
        val contentType = NetUtils.getContentTypeMediaType(contentTypeAttribute)
        val charset = Option(NetUtils.getContentTypeCharset(contentTypeAttribute)) getOrElse DEFAULT_CHARACTER_ENCODING

        val contentTypeWithCharset = contentType + "; charset=" + charset
        val src = partOrBodyElement.attributeValue("src")

        // Either a String or a FileItem
        val content =
            if (src ne null) {
                // Content of the part is not inline

                // Generate a FileItem from the source
                val source = getSAXSource(EmailProcessor.this, pipelineContext, src, dataInputSystemId, contentType)
                Left(handleStreamedPartContent(pipelineContext, source))
            } else {
                // Content of the part is inline

                // In the cases of text/html and XML, there must be exactly one root element
                val needsRootElement = contentType == "text/html"// || ProcessorUtils.isXMLContentType(contentType);
                if (needsRootElement && partOrBodyElement.elements.size != 1)
                    throw new ValidationException("The <body> or <part> element must contain exactly one element for text/html", partOrBodyElement.getData.asInstanceOf[LocationData])

                // Create Document and convert it into a String
                val rootElement = (if (needsRootElement) partOrBodyElement.elements.get(0) else partOrBodyElement).asInstanceOf[Element]
                val partDocument = new NonLazyUserDataDocument
                partDocument.setRootElement(rootElement.asInstanceOf[NonLazyUserDataElement].clone.asInstanceOf[Element])
                Right(handleInlinePartContent(partDocument, contentType))
            }

        if (! XMLUtils.isTextOrJSONContentType(contentType)) {
            // This is binary content (including application/xml)
            content match {
                case Left(fileItem) ⇒
                    parentPart.setDataHandler(new DataHandler(new ReadonlyDataSource {
                        def getContentType = contentType
                        def getInputStream = fileItem.getInputStream
                        def getName = name
                    }))
                case Right(inline) ⇒
                    val data = NetUtils.base64StringToByteArray(inline)
                    parentPart.setDataHandler(new DataHandler(new SimpleBinaryDataSource(name, contentType, data)))
            }
        } else {
            // This is text content (including text/xml)
            content match {
                case Left(fileItem) ⇒
                    parentPart.setDataHandler(new DataHandler(new ReadonlyDataSource {
                        // This always contains a charset
                        def getContentType = contentTypeWithCharset
                        // This is encoded with the appropriate charset (user-defined, or the default)
                        def getInputStream = fileItem.getInputStream
                        def getName = name
                    }))
                case Right(inline) ⇒
                    parentPart.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentTypeWithCharset, inline)))
            }
        }

        // Set content-disposition header
        Option(partOrBodyElement.attributeValue("content-disposition")) foreach
            (contentDisposition ⇒ parentPart.setDisposition(contentDisposition))

        // Set content-id header
        Option(partOrBodyElement.attributeValue("content-id")) foreach
            (contentId ⇒ parentPart.setHeader("content-id", "<" + contentId + ">"))
        //part.setContentID(contentId);
    }

    private def handleInlinePartContent(document: Document, contentType: String) =
        if (contentType == "text/html") {
            // Convert XHTML into an HTML String
            val writer = new StringBuilderWriter
            val identity = TransformerUtils.getIdentityTransformerHandler
            identity.getTransformer.setOutputProperty(OutputKeys.METHOD, "html")
            identity.setResult(new StreamResult(writer))
            val locationSAXWriter = new LocationSAXWriter
            locationSAXWriter.setContentHandler(identity)
            locationSAXWriter.write(document.asInstanceOf[Node])
            writer.toString
        } else
            // For other types, just return the text nodes
            document.getStringValue
}

object EmailProcessor {

    val Logger = LoggerFactory.createLogger(classOf[EmailProcessor])
    val SMTPHost = "smtp-host"
    val TestTo = "test-to"
    val TestSMTPHost = "test-smtp-host"
    val ConfigNamespaceURI = "http://www.orbeon.com/oxf/email"
    val DefaultMultipart = "mixed"

    // Use utf-8 as most email clients support it. This allows us not to have to pick an inferior encoding.
    val DEFAULT_CHARACTER_ENCODING = "utf-8"

    // Get Some(trimmed value of the element) or None if the element is null
    def optionalValueTrim(e: Element) = nonEmptyOrNone(Option(e) map(_.getStringValue) orNull)

    // Read a text or binary document and return it as a FileItem
    def handleStreamedPartContent(pipelineContext: PipelineContext, source: SAXSource): FileItem = {
        val fileItem = NetUtils.prepareFileItem(NetUtils.REQUEST_SCOPE)
        TransformerUtils.sourceToSAX(source, new BinaryTextXMLReceiver(fileItem.getOutputStream))
        fileItem
    }

    def getSAXSource(processor: Processor, pipelineContext: PipelineContext, href: String, base: String, contentType: String): SAXSource = {
        val xmlReader = {
            val inputName = ProcessorImpl.getProcessorInputSchemeInputName(href)
            if (inputName ne null)
                new ProcessorOutputXMLReader(pipelineContext, processor.getInputByName(inputName).getOutput)
            else {
                val urlGenerator =
                    if (contentType eq null)
                        new URLGenerator(URLFactory.createURL(base, href))
                    else
                        new URLGenerator(URLFactory.createURL(base, href), contentType, true)
                new ProcessorOutputXMLReader(pipelineContext, urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA))
            }
        }
        val saxSource = new SAXSource(xmlReader, new InputSource)
        saxSource.setSystemId(href)
        saxSource
    }

    private abstract class ReadonlyDataSource extends DataSource {
        def getOutputStream = throw new IOException("Write operation not supported")
    }

    private class SimpleTextDataSource(val getName: String, val getContentType: String, text: String) extends ReadonlyDataSource {
        def getInputStream  = new ByteArrayInputStream(text.getBytes("utf-8"))
    }

    private class SimpleBinaryDataSource(val getName: String, val getContentType: String, data: Array[Byte]) extends ReadonlyDataSource {
        def getInputStream  = new ByteArrayInputStream(data)
    }
}

// Set content-transfer-encoding header
//                    final String contentTransferEncoding = partElement.attributeValue("content-transfer-encoding");
//
//                    MimeBodyPart part = new MimeBodyPart() {
//                        protected void updateHeaders() throws MessagingException {
//                            super.updateHeaders();
//                            if (contentTransferEncoding != null)
//                                setHeader("Content-Transfer-Encoding", contentTransferEncoding);
//                        }
//                    };
//                    // Set content-disposition header
//                    String contentDisposition = partElement.attributeValue("content-disposition");
//                    if (contentDisposition != null)
//                        part.setDisposition(contentDisposition);
//
//                    part.setDataHandler(new DataHandler(new SimpleTextDataSource(name, contentType, content)));
//                    mimeMultipart.addBodyPart(part);