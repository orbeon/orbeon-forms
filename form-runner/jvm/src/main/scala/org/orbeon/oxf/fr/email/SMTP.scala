/**
 *  Copyright (C) 2025 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.email

import cats.implicits.catsSyntaxOptionId
import enumeratum.EnumEntry.Hyphencase
import enumeratum.{Enum, EnumEntry}
import jakarta.activation.{DataHandler, DataSource}
import jakarta.mail.*
import jakarta.mail.internet.*
import org.orbeon.dom.QName
import org.orbeon.io.IOUtils.*
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.email.EmailContent.TestSMTPHost
import org.orbeon.oxf.fr.email.EmailMetadata.HeaderName
import org.orbeon.oxf.processor.XPLConstants.OXF_PROCESSORS_NAMESPACE
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.StringUtils.*

import java.io.{InputStream, OutputStream}
import java.util.Properties as JProperties
import scala.collection.immutable
import scala.util.Try


case object SMTP {

  case class ServerConfig(
    host                     : String,
    port                     : Int,
    encryptionWithCredentials: EncryptionWithCredentials
  ) {

    def newSession: Session = {

      val properties = new JProperties

      properties.setProperty("mail.smtp.host", host)
      properties.setProperty("mail.smtp.port", port.toString)

      encryptionWithCredentials match {
        case EncryptionWithCredentials.SSL(_) =>
          // "SSL" / Implicit TLS
          // Use mail.smtp.ssl.enable instead of mail.smtp.socketFactory.* (legacy)
          properties.setProperty("mail.smtp.ssl.enable" , "true")

        case EncryptionWithCredentials.TLS(_) =>
          // "TLS" / STARTTLS (i.e. opportunistic TLS upgrade)
          properties.setProperty("mail.smtp.starttls.enable", "true")

        case EncryptionWithCredentials.NoEncryption(_) =>
      }

      encryptionWithCredentials.credentialsOpt match {
        case Some(credentials) =>
          properties.setProperty("mail.smtp.auth", "true")

          Session.getInstance(properties, new Authenticator {
            override def getPasswordAuthentication: PasswordAuthentication =
              new PasswordAuthentication(credentials.username, credentials.password)
          })

        case None =>
          Session.getInstance(properties)
      }
    }
  }

  object ServerConfig {
    def fromProperties()(implicit params: FormRunnerParams): ServerConfig = {

      // 2025-03-31: keep supporting properties from the email processor as they're documented and possibly used by customers
      val emailProcessorProperties = Properties.instance.getPropertySet(QName("email", OXF_PROCESSORS_NAMESPACE))
      val testHostOpt              = emailProcessorProperties.getNonBlankString(TestSMTPHost)

      def prop(name: String): Option[String] = formRunnerProperty(s"oxf.fr.email.smtp.$name").flatMap(trimAllToOpt)

      val host           = testHostOpt.orElse(prop("host")).getOrElse(throw new OXFException("Could not find SMTP host in properties"))
      val portOpt        = prop("port").map(p => Try(p.toInt).getOrElse(throw new OXFException(s"Invalid SMTP port $p")))
      val usernameOpt    = prop("username")
      val encryption     = prop("encryption").getOrElse("")
      val credentialsOpt = usernameOpt.map(SMTP.Credentials(_, prop("credentials").getOrElse("")))

      def credentials: Credentials =
        credentialsOpt.getOrElse(throw new OXFException(s"Credentials are required when using ${encryption.toUpperCase}"))

      val encryptionWithCredentials = encryption match {
        case ""    => EncryptionWithCredentials.NoEncryption(credentialsOpt)
        case "tls" => EncryptionWithCredentials.TLS(credentials)
        case "ssl" => EncryptionWithCredentials.SSL(credentials)
      }

      ServerConfig(
        host                      = host,
        port                      = portOpt.getOrElse(encryptionWithCredentials.defaultPort),
        encryptionWithCredentials = encryptionWithCredentials
      )
    }
  }

  sealed trait Encryption extends EnumEntry with Hyphencase

  object Encryption extends Enum[Encryption] {
    case object SSL extends Encryption
    case object TLS extends Encryption

    override def values: immutable.IndexedSeq[Encryption] = super.findValues
  }

  case class Credentials(username: String, password: String)

  // Enforce optional or mandatory credentials via type
  sealed trait EncryptionWithCredentials {
    def defaultPort   : Int
    def encryptionOpt : Option[Encryption]
    def credentialsOpt: Option[Credentials]
  }

  object EncryptionWithCredentials {
    case class NoEncryption(credentialsOpt: Option[Credentials]) extends EncryptionWithCredentials {
      override val defaultPort  : Int                = 25
      override val encryptionOpt: Option[Encryption] = None
    }

    case class SSL(credentials: Credentials) extends EncryptionWithCredentials {
      override val defaultPort   : Int                 = 465
      override val encryptionOpt : Option[Encryption]  = Encryption.SSL.some
      override val credentialsOpt: Option[Credentials] = credentials.some
    }

    case class TLS(credentials: Credentials) extends EncryptionWithCredentials {
      override val defaultPort   : Int                 = 587
      override val encryptionOpt : Option[Encryption]  = Encryption.TLS.some
      override val credentialsOpt: Option[Credentials] = credentials.some
    }
  }

  def send(serverConfig: ServerConfig, emailContent: EmailContent): Try[Unit] = {

    val session = serverConfig.newSession
    val message = new MimeMessage(session)

    def emailAddresses(headerName: HeaderName): Array[Address] =
      emailContent.headers.filter(_._1 == headerName).map(_._2).flatMap(InternetAddress.parse(_, false).toList).toArray

    // All methods to add email addresses can be called with empty arrays, so don't check for empty arrays

    message.addFrom   (emailAddresses(HeaderName.From).take(1)) // Silently ignore extra From addresses
    message.setReplyTo(emailAddresses(HeaderName.ReplyTo))

    List(
      Message.RecipientType.TO  -> HeaderName.To,
      Message.RecipientType.CC  -> HeaderName.CC,
      Message.RecipientType.BCC -> HeaderName.BCC,
    ).foreach { case (recipientType, headerName) =>
      message.addRecipients(recipientType, emailAddresses(headerName))
    }

    // Custom headers
    emailContent.headers.collect {
      case (HeaderName.Custom(headerName), headerValue) =>
        headerName -> headerValue
    }.foreach {
      case (headerName, headerValue) =>
        message.addHeader(headerName, encoded(headerValue))
    }

    // Set the email subject
    // The JavaMail spec is badly written and is not clear about whether this needs to be done here. But it
    // seems to use the platform's default charset, which we don't want to deal with. So we preemptively encode.
    // The result is pure ASCII so that setSubject() will not attempt to re-encode it.
    message.setSubject(encoded(emailContent.subject))

    val multipart = new MimeMultipart("mixed")

    if (emailContent.attachments.isEmpty) {
      // Single-part email with message content only
      setMessageContent(message, emailContent.messageContent)
    } else {
      // Multipart email with message content and attachments
      multipart.addBodyPart(bodyPartForMessageContent(emailContent.messageContent))
      emailContent.attachments.map(bodyPartForAttachment).foreach(multipart.addBodyPart)

      message.setContent(multipart)
    }

    Try {
      useAndClose(session.getTransport("smtp")) { _ =>
        Transport.send(message)
      }
    }
  }

  private def encoded(string: String): String =
    MimeUtility.encodeText(string, EmailContent.Charset, None.orNull)

  private def setMessageContent(mimePart: MimePart, messageContent: MessageContent): Unit = {
    val baseContentType = if (messageContent.html) ContentTypes.HtmlContentType else ContentTypes.PlainTextContentType
    val contentType     = ContentTypes.makeContentTypeCharset(baseContentType, Some(EmailContent.Charset))
    mimePart.setContent(messageContent.content, contentType)
  }

  private def bodyPartForMessageContent(messageContent: MessageContent): MimeBodyPart = {
    val bodyPart = new MimeBodyPart()
    setMessageContent(bodyPart, messageContent)
    bodyPart
  }

  private def bodyPartForAttachment(attachment: Attachment): MimeBodyPart = {
    val bodyPart = new MimeBodyPart()

    val dataSource = new DataSource {
      def getContentType: String        = attachment.contentType
      def getInputStream: InputStream   = attachment.contentFactory().stream
      def getName: String               = attachment.filename
      def getOutputStream: OutputStream = throw new UnsupportedOperationException("Not implemented")
    }

    bodyPart.setDataHandler(new DataHandler(dataSource))
    bodyPart.setFileName(attachment.filename)

    bodyPart
  }
}
