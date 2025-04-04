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

import org.orbeon.dom.QName
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.email.EmailMetadata.{HeaderName, TemplateMatch}
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.fr.s3.S3Config
import org.orbeon.oxf.processor.XPLConstants.OXF_PROCESSORS_NAMESPACE
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.util.{CoreCrossPlatformSupportTrait, IndentedLogger, NetUtils, TryUtils, URLRewriterUtils}
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps, *}
import software.amazon.awssdk.services.s3.S3Client

import java.net.URI
import scala.util.Try


case class EmailContent(
  headers       : List[(HeaderName, String)],
  subject       : String,
  messageContent: MessageContent,
  attachments   : List[Attachment]
) {
  def storeToS3(s3PathPrefix: String)(implicit s3Config: S3Config, s3Client: S3Client): Try[Unit] = {
    // TODO: store email body and headers as well
    TryUtils.sequenceLazily(attachments)(_.storeToS3(s3PathPrefix)).map(_ => ())
  }
}

object EmailContent {
  val Charset: String = CharsetNames.Utf8

  // Test properties interpreted by EmailProcessor
  val TestTo       = "test-to"
  val TestSMTPHost = "test-smtp-host"

  def apply(
    template                : EmailMetadata.Template,
    parameters              : List[EmailMetadata.Param],
    emailDataFormatVersion  : DataFormatVersion,
    urisByRenderedFormat    : Map[RenderedFormat, URI])(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams,
    ctx                     : InDocFormRunnerDocContext
  ): EmailContent = {

    val formDataMaybeMigrated = GridDataMigration.dataMaybeMigratedFromEdge(
      app                        = formRunnerParams.app,
      form                       = formRunnerParams.form,
      data                       = frc.formInstance.root,
      metadataOpt                = frc.metadataInstance.map(_.root),
      dstDataFormatVersionString = emailDataFormatVersion.entryName,
      pruneMetadata              = false,
      pruneTmpAttMetadata        = true
    )

    val attachments =
      Attachment.xmlAttachment(formDataMaybeMigrated, template).toList ++
      urisByRenderedFormat.get(RenderedFormat.Pdf).flatMap(Attachment.pdfAttachment(_, template)).toList ++
      Attachment.fileAttachments(template)

    val evaluatedParams = EvaluatedParams.fromEmailMetadata(template, parameters)

    EmailContent(
      headers        = headers       (template),
      subject        = subject       (template, evaluatedParams),
      messageContent = MessageContent(template, evaluatedParams),
      attachments    = attachments
    )
  }

  private def headers(
    template        : EmailMetadata.Template)(implicit
    formRunnerParams: FormRunnerParams,
    ctx             : InDocFormRunnerDocContext
  ): List[(HeaderName, String)] = {

    def evaluatedHeaderValues(headerName: HeaderName): List[String] =
      template.headers.filter(_._1 == headerName).map(_._2).flatMap(evaluatedTemplateValue)

    def emailAddressesFromProperties(headerName: HeaderName): List[String] =
      formRunnerProperty(s"oxf.fr.email.${headerName.entryName}").toList

    // 2025-03-31: keep supporting properties from the email processor as they're documented and possibly used by customers
    val emailProcessorProperties = Properties.instance.getPropertySet(QName("email", OXF_PROCESSORS_NAMESPACE))
    val testToOpt                = emailProcessorProperties.getNonBlankString(TestTo)

    // Email addresses: From, Reply-To, To, CC, BCC
    val emailAddresses = HeaderName.values.toList.flatMap { headerName =>
      // Retrieve values from template and properties; remove empty values and duplicates
      val allHeaderValues =
        (evaluatedHeaderValues(headerName) ++ emailAddressesFromProperties(headerName)).flatMap(_.trimAllToOpt).distinct

      val headerValues =
        if (headerName == HeaderName.From) {
          // Keep only one From email address; all other headers can have multiple values
          allHeaderValues.take(1)
        } else if (headerName == HeaderName.To) {
          // If test To email address specified, use that address instead
          if (testToOpt.isDefined) testToOpt.toList else allHeaderValues
        } else if (headerName == HeaderName.CC || headerName == HeaderName.BCC) {
          // If test To email address specified, do not set CC/BCC recipients
          if (testToOpt.isDefined) Nil else allHeaderValues
        } else {
          allHeaderValues
        }

      headerValues.map(headerName -> _)
    }

    // Custom headers: retrieve them from template and evaluate them, but do not read them from properties (TODO: should we?)
    val customHeaders = template.headers.collect { case (headerName @ HeaderName.Custom(_), _) =>
      evaluatedHeaderValues(headerName).map(headerName -> _)
    }.flatten

    emailAddresses ++ customHeaders
  }

  private def subject(
    template       : EmailMetadata.Template,
    evaluatedParams: EvaluatedParams
  ): String = {
    val subjectFromTemplateOpt    = template.subject.map(_.text)
    lazy val subjectFromResources = (frc.currentFRResources / "email" / "subject").stringValue

    evaluatedParams.processedTemplate(html = false, subjectFromTemplateOpt.getOrElse(subjectFromResources))
  }

  def emailContents(
    emailMetadata           : EmailMetadata.Metadata,
    urisByRenderedFormat    : Map[RenderedFormat, URI],
    emailDataFormatVersion  : DataFormatVersion,
    templateMatch           : TemplateMatch,
    language                : String,
    templateNameOpt         : Option[String])(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams,
    ctx                     : InDocFormRunnerDocContext
  ): List[EmailContent] = {

    // 1) Filter templates by language and name
    val templatesFilteredByLanguageAndName = emailMetadata.templates.filter { template =>
      (template.lang.isEmpty || template.lang.contains(language)) && templateNameOpt.forall(template.name == _)
    }

    // 2) Consider only templates with enableIfTrue expression, if any, evaluating to true
    val enabledTemplates = templatesFilteredByLanguageAndName.filter {
      _.enableIfTrue.forall(evaluatedExpressionAsBoolean)
    }

    // 3) Consider first template or all templates
    val firstOrAllTemplates = templateMatch match {
      case TemplateMatch.First => enabledTemplates.take(1)
      case TemplateMatch.All   => enabledTemplates
    }

    // For each email template, generate email content
    firstOrAllTemplates.map { template =>
      EmailContent(
        template               = template,
        parameters             = emailMetadata.params,
        emailDataFormatVersion = emailDataFormatVersion,
        urisByRenderedFormat   = urisByRenderedFormat
      )
    }
  }

  implicit class URIOps(uri: URI) {
    def rewrittenAsAbsoluteURI: URI = new URI(
      URLRewriterUtils.rewriteServiceURL(
        request     = NetUtils.getExternalContext.getRequest,
        urlString   = uri.toString,
        rewriteMode = UrlRewriteMode.Absolute
      )
    )
  }
}