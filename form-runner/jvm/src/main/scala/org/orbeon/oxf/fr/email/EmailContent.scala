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
import org.orbeon.connection.{Content, StreamedContent}
import org.orbeon.dom.QName
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.email.EmailContent.URIOps
import org.orbeon.oxf.fr.email.EmailMetadata.FilesToAttach.All
import org.orbeon.oxf.fr.email.EmailMetadata.{FilesToAttach, HeaderName}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.processor.XPLConstants.OXF_PROCESSORS_NAMESPACE
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StaticXPath.tinyTreeToOrbeonDom
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger, NetUtils, URLRewriterUtils}
import org.orbeon.saxon.function.ProcessTemplateSupport
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.asScalaIterator
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps, *}
import org.orbeon.xforms.XFormsNames

import java.net.URI


case class EmailContent(
  headers       : List[(HeaderName, String)],
  subject       : String,
  messageContent: MessageContent,
  attachments   : List[Attachment]
)

case class MessageContent(content: String, html: Boolean)

case object MessageContent {
  def apply(nodeInfo: NodeInfo): MessageContent =
    MessageContent(
      content = nodeInfo.stringValue,
      html    = nodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType)
    )
}

case class Attachment(filename: String, contentType: String, data: AttachmentData)

case class AttachmentData(content: () => Content)

object AttachmentData {
  def fromURI(
    uri                     : URI
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
  ): AttachmentData =
    AttachmentData(() => {
      val connectionResult = PersistenceApi.connectPersistence(
        method         = HttpMethod.GET,
        pathQuery      = uri.rewrittenAsAbsoluteURI.toString,
        formVersionOpt = Left(FormDefinitionVersion.Specific(formRunnerParams.formVersion)).some
      )

      if (! StatusCode.isSuccessCode(connectionResult.statusCode)) {
        throw HttpStatusCodeException(code = connectionResult.statusCode, resource = uri.toString.some)
      }

      connectionResult.content
    })

  def fromString(string: String, contentType: String): AttachmentData =
    AttachmentData(() => {
      StreamedContent.fromBytes(string.getBytes(EmailContent.Charset), contentType = contentType.some)
    })
}

object EmailContent {
  val Charset: String = CharsetNames.Utf8

  // Test properties interpreted by EmailProcessor
  val TestTo       = "test-to"
  val TestSMTPHost = "test-smtp-host"

  def apply(
    formDefinition          : NodeInfo,
    formData                : NodeInfo,
    emailDataFormatVersion  : DataFormatVersion,
    urisByRenderedFormat    : Map[RenderedFormat, URI],
    template                : EmailMetadata.Template,
    parameters              : List[EmailMetadata.Param]
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
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
      xmlAttachment(formData, formDataMaybeMigrated, template).toList ++
      urisByRenderedFormat.get(RenderedFormat.Pdf).flatMap(pdfAttachment(formData, _, template)).toList ++
      fileAttachments(formDefinition, formData, template)

    val interpretedParams = InterpretedParams.fromEmailMetadata(formDefinition, formData, template, parameters)

    EmailContent(
      headers        = headers       (template, formDefinition, formData),
      subject        = subject       (template, interpretedParams),
      messageContent = messageContent(template, interpretedParams),
      attachments    = attachments
    )
  }

  private def headers(
    template      : EmailMetadata.Template,
    formDefinition: NodeInfo,
    formData      : NodeInfo
  )(implicit
    formRunnerParams: FormRunnerParams
  ): List[(HeaderName, String)] = {

    def evaluatedHeaderValues(headerName: HeaderName): List[String] =
      template
        .headers
        .filter(_._1 == headerName)
        .map(_._2)
        .flatMap(evaluatedTemplateValue(formDefinition, formData, _))
        .map(_.getStringValue)

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
    template         : EmailMetadata.Template,
    interpretedParams: InterpretedParams
  ): String = {
    val subjectFromTemplateOpt    = template.subject.map(_.text)
    lazy val subjectFromResources = (frc.currentFRResources / "email" / "subject").stringValue

    interpretedParams.withParametersValues(html = false, subjectFromTemplateOpt.getOrElse(subjectFromResources))
  }

  private def messageContent(
    template         : EmailMetadata.Template,
    interpretedParams: InterpretedParams
  )(implicit
    formRunnerParams : FormRunnerParams
  ): MessageContent = {

    val messageContentFromTemplateOpt    = template.body.map(part =>  MessageContent(part.text, part.isHTML))
    lazy val messageContentFromResources = MessageContent((frc.currentFRResources / "email" / "body").head)
    val messageContent                   = messageContentFromTemplateOpt.getOrElse(messageContentFromResources)

    val contentWithParams = messageContent.copy(
      content = interpretedParams.withParametersValues(messageContent.html, messageContent.content)
    )

    if (contentWithParams.html) {
      // Wrap HTML content along with inline CSS, if any
      val inlineCssOpt   = formRunnerProperty("oxf.fr.email.css.custom.inline").flatMap(_.trimAllToOpt)
      val inlineCss      = inlineCssOpt.map(css => s"<head><style type=\"text/css\">$css</style></head>").getOrElse("")
      val wrappedContent = s"<html>$inlineCss<body>${contentWithParams.content}</body></html>"

      contentWithParams.copy(content = wrappedContent)
    } else {
      // Non-wrapped, non-HTML content
      contentWithParams
    }
  }

  private def xmlAttachment(
    formData             : NodeInfo,
    formDataMaybeMigrated: NodeInfo,
    template             : EmailMetadata.Template
  )(implicit
    formRunnerParams     : FormRunnerParams
  ): Option[Attachment] = {

    val contentType = ContentTypes.makeContentTypeCharset(ContentTypes.XmlContentType, Some(Charset))

    attachment(
      formData       = formData,
      attachOpt      = template.attachXml,
      attachmentType = "xml",
      contentType    = contentType,
      data           = AttachmentData.fromString(
        string      = tinyTreeToOrbeonDom(formDataMaybeMigrated).serializeToString(),
        contentType = contentType
      )
    )
  }

  private def pdfAttachment(
    formData                : NodeInfo,
    uri                     : URI,
    template                : EmailMetadata.Template
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
  ): Option[Attachment] =
    attachment(
      formData       = formData,
      attachOpt      = template.attachPdf,
      attachmentType = RenderedFormat.Pdf.entryName,
      contentType    = ContentTypes.PdfContentType,
      data           = AttachmentData.fromURI(uri)
    )

  private def attachment(
    formData        : NodeInfo,
    attachOpt       : Option[Boolean],
    attachmentType  : String,
    contentType     : String,
    data            : AttachmentData
  )(implicit
    formRunnerParams: FormRunnerParams
  ): Option[Attachment] = {

    // Will include attachment depending on: 1) template value, or 2) property value
    val attach = attachOpt.getOrElse(booleanFormRunnerProperty(s"oxf.fr.email.attach-$attachmentType"))

    attach.option {
      val filenameOpt = FormRunner.emailAttachmentFilename(
        formData,
        attachmentType,
        formRunnerParams.app,
        formRunnerParams.form
      )

      val filename = filenameOpt.getOrElse(s"form.$attachmentType")

      Attachment(filename, contentType, data)
    }
  }

  private def fileAttachments(
    formDefinition          : NodeInfo,
    formData                : NodeInfo,
    template                : EmailMetadata.Template
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
  ): List[Attachment] = {

    // Determine which attachments to include from template or property
    val filesToAttach = template.filesToAttach
      .orElse(formRunnerProperty("oxf.fr.email.attach-files").map(FilesToAttach.withName))
      .getOrElse(All)

    val attachmentHolders = filesToAttach match {
      case FilesToAttach.All =>
        val body = (formDefinition / "*:body").head
        val head = (formDefinition / "*:head").head

        val AttachmentClass = "fr-attachment"

        searchHoldersForClassTopLevelOnly       (             body = body, data = formData, classNames = AttachmentClass).toList ++
        searchHoldersForClassUseSectionTemplates(head = head, body = body, data = formData, classNames = AttachmentClass).toList

      case FilesToAttach.Selected =>
        evaluatedTemplateValues(formDefinition, formData, template.controlsToAttach).toList

      case FilesToAttach.None =>
        Nil
    }

    val attachmentHolderNodes = attachmentHolders.collect { case nodeInfo: NodeInfo => nodeInfo }

    val singleAttachments   = attachmentHolderNodes.filter(_.hasAtt("filename"))
    val multipleAttachments = (attachmentHolderNodes / *).filter(_.hasAtt("filename"))

    for {
      attachment <- singleAttachments ++ multipleAttachments
    } yield {
      // URL may be absolute or already point to persistence layer
      // Use `@fr:tmp-file` first if present, see https://github.com/orbeon/orbeon-forms/issues/5768

      val mediaTypeOpt = (attachment /@ "mediatype") .headOption.map(_.stringValue)
      val filenameOpt  = (attachment /@ "filename")  .headOption.map(_.stringValue)
      val tmpFileOpt   = (attachment /@ "*:tmp-file").headOption.map(_.stringValue)
      val uri          = new URI(tmpFileOpt.flatMap(_.trimAllToOpt).getOrElse(attachment.getStringValue))

      Attachment(
        filename    = filenameOpt.orElse(uri.getPath.split("/").last.trimAllToOpt).getOrElse("unknown"),
        contentType = mediaTypeOpt.getOrElse(ContentTypes.OctetStreamContentType),
        data        = AttachmentData.fromURI(uri)
      )
    }
  }

  sealed trait InterpretedParam {
    def value(html: Boolean): String
  }

  object InterpretedParam {
    // Once interpreted, the parameter value is static
    case class Static(staticValue: String) extends InterpretedParam {
      override def value(html: Boolean): String = staticValue
    }

    // "All Control Values" parameters can generate HTML or not
    case class AllControls(controlsToExclude: List[String]) extends InterpretedParam {
      override def value(html: Boolean): String =
        FormRunnerMetadata.findAllControlsWithValuesExcludingNamedControls(html, controlsToExclude)
    }

    def apply(staticValue: String): InterpretedParam =
      Static(staticValue)

    def apply(
      formDefinition: NodeInfo,
      formData      : NodeInfo,
      template      : EmailMetadata.Template,
      param         : EmailMetadata.Param
    ): InterpretedParam =
      param match {
        case EmailMetadata.Param.ExpressionParam(_, expression) =>
          InterpretedParam(
            FormRunner.expressionValue(formDefinition, expression).map(_.getStringValue).headOption.getOrElse("")
          )

        case EmailMetadata.Param.ControlValueParam(_, controlName) =>
          // Value is not formatted at all. Would need to be formatted properly like we should do with #3627.
          InterpretedParam(
            FormRunner
              .controlValue(formDefinition, formData, controlName, sectionOpt = None)
              .map(_.getStringValue)
              .mkString(", ") // TODO: add a way to configure the values separator
          )

        case EmailMetadata.Param.AllControlValuesParam(_) =>
          AllControls(
            // TODO: add support for section templates
            controlsToExclude = template.controlsToExcludeFromAllControlValues.map(_.controlName)
          )

        case param @ _ =>
          InterpretedParam(
            // TODO: includeToken
            buildLinkBackToFormRunner(param.entryName, includeToken = false)
          )
      }
  }

  case class InterpretedParams(parameters: List[(String, InterpretedParam)]) {
    def withParametersValues(html: Boolean, contentTemplate: String): String =
      ProcessTemplateSupport.processTemplateWithNames(
        contentTemplate,
        parameters.map { case (name, value) => name -> value.value(html)}
      )
  }

  object InterpretedParams {
    def fromEmailMetadata(
      formDefinition: NodeInfo,
      formData      : NodeInfo,
      template      : EmailMetadata.Template,
      parameters    : List[EmailMetadata.Param]
    ): InterpretedParams =
      InterpretedParams(
        parameters = parameters.map(param => (param.name, InterpretedParam(formDefinition, formData, template, param)))
      )
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