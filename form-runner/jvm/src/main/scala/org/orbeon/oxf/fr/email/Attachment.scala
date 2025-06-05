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
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.email.EmailContent.URIOps
import org.orbeon.oxf.fr.email.EmailMetadata.FilesToAttach
import org.orbeon.oxf.fr.email.EmailMetadata.FilesToAttach.All
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.fr.s3.{S3, S3Config}
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StaticXPath.tinyTreeToOrbeonDom
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps, *}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectResponse

import java.net.URI
import scala.util.Try


// We use the type () => Content here, as we might need to consume the stream multiple times (e.g. to send the
// attachment by email and then to store it in S3). We also store the content type separately, to generate Jakarta
// DataSources more easily (the interface has separate content type and input stream getter methods).

case class Attachment(filename: String, contentType: String, contentFactory: () => Content) {

  def storeToS3(s3PathPrefix: String)(implicit s3Config: S3Config, s3Client: S3Client): Try[PutObjectResponse] =
    S3.write(key = s3PathPrefix + filename, contentFactory())
}

object Attachment {

  def xmlAttachment(
    formDataMaybeMigrated: NodeInfo,
    template             : EmailMetadata.Template
  )(implicit
    formRunnerParams     : FormRunnerParams
  ): Option[Attachment] = {

    val contentType = ContentTypes.makeContentTypeCharset(ContentTypes.XmlContentType, Some(EmailContent.Charset))

    attachment(
      attachOpt      = template.attachXml,
      attachmentType = "xml",
      contentType    = contentType,
      contentFactory = xmlContentFactory(formDataMaybeMigrated, contentType)
    )
  }

  def pdfAttachment(
    uri                     : URI,
    template                : EmailMetadata.Template
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
  ): Option[Attachment] = {

    val contentType = ContentTypes.PdfContentType

    attachment(
      attachOpt      = template.attachPdf,
      attachmentType = RenderedFormat.Pdf.entryName,
      contentType    = contentType,
      contentFactory = uriContentFactory(uri, contentType)
    )
  }

  private def attachment(
    attachOpt       : Option[Boolean],
    attachmentType  : String,
    contentType     : String,
    contentFactory  : () => Content
  )(implicit
    formRunnerParams: FormRunnerParams
  ): Option[Attachment] = {

    // Will include attachment depending on: 1) template value, or 2) property value
    val attach = attachOpt.getOrElse(booleanFormRunnerProperty(s"oxf.fr.email.attach-$attachmentType"))

    attach.option {
      val filenameOpt = emailAttachmentFilename(
        data           = frc.formInstance.root,
        attachmentType = attachmentType,
        app            = formRunnerParams.app,
        form           = formRunnerParams.form
      )

      val filename = filenameOpt.getOrElse(s"form.$attachmentType")

      Attachment(filename, contentType, contentFactory)
    }
  }

  def fileAttachments(
    template                : EmailMetadata.Template
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams,
    ctx                     : InDocFormRunnerDocContext
  ): List[Attachment] = {

    // Determine which attachments to include from template or property
    val filesToAttach = template.filesToAttach
      .orElse(formRunnerProperty("oxf.fr.email.attach-files").map(FilesToAttach.withName))
      .getOrElse(All)

    val attachmentHolders = filesToAttach match {
      case FilesToAttach.All =>
        val body = (ctx.formDefinitionRootElem / "*:body").head
        val head = (ctx.formDefinitionRootElem / "*:head").head
        val data = frc.formInstance.root

        val AttachmentClass = "fr-attachment"

        searchHoldersForClassTopLevelOnly       (             body = body, data = data, classNames = AttachmentClass) ++
        searchHoldersForClassUseSectionTemplates(head = head, body = body, data = data, classNames = AttachmentClass)

      case FilesToAttach.Selected =>
        template.controlsToAttach.flatMap { control =>
          controlValueAsNodeInfos(control.controlName, control.sectionOpt)
        }

      case FilesToAttach.None =>
        Nil
    }

    val singleAttachments   = attachmentHolders      .filter(_.hasAtt("filename"))
    val multipleAttachments = (attachmentHolders / *).filter(_.hasAtt("filename"))

    (singleAttachments ++ multipleAttachments).flatMap { attachment =>
      // URL may be absolute or already point to persistence layer
      // Use `@fr:tmp-file` first if present, see https://github.com/orbeon/orbeon-forms/issues/5768
      val mediaTypeOpt = (attachment /@ "mediatype") .headOption.map(_.stringValue)
      val contentType  = mediaTypeOpt.getOrElse(ContentTypes.OctetStreamContentType)
      val filenameOpt  = (attachment /@ "filename")  .headOption.map(_.stringValue)
      val tmpFileOpt   = (attachment /@ "*:tmp-file").headOption.map(_.stringValue)
      val valueOpt     = tmpFileOpt.flatMap(_.trimAllToOpt).orElse(attachment.getStringValue.trimAllToOpt)
      valueOpt.map { value =>
        val uri = new URI(value)
        Attachment(
          filename       = filenameOpt.orElse(uri.getPath.split("/").last.trimAllToOpt).getOrElse("unknown"),
          contentType    = contentType,
          contentFactory = uriContentFactory(uri, contentType)
        )
      }
    }
  }

  private def uriContentFactory(
    uri                     : URI,
    contentType             : String
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
  ): () => Content =
    () => {
      val connectionResult = PersistenceApi.connectPersistence(
        method         = HttpMethod.GET,
        pathQuery      = uri.rewrittenAsAbsoluteURI.toString,
        formVersionOpt = Left(FormDefinitionVersion.Specific(formRunnerParams.formVersion)).some
      )

      if (! StatusCode.isSuccessCode(connectionResult.statusCode))
        throw HttpStatusCodeException(code = connectionResult.statusCode, resource = uri.toString.some)

      // Use the content type from the attachment control (more exact)
      connectionResult.content.copy(contentType = contentType.some)
    }

  private def xmlContentFactory(nodeInfo: NodeInfo, contentType: String): () => Content = {
    val bytes = tinyTreeToOrbeonDom(nodeInfo).serializeToString().getBytes(EmailContent.Charset)
    () => StreamedContent.fromBytes(bytes, contentType.some)
  }

  private def emailAttachmentFilename(
    data          : NodeInfo,
    attachmentType: String,
    app           : String,
    form          : String
  ): Option[String] = {

    // NOTE: We don't use `FormRunnerParams()` for that this works in tests.
    // Callees only require, as of 2018-05-31, `app` and `form`.
    implicit val params: FormRunnerParams = FormRunnerParams(AppForm(app, form), "email")

    for {
      (expr, mapping) <- formRunnerPropertyWithNs(s"oxf.fr.email.$attachmentType.filename")
      trimmedExpr     <- expr.trimAllToOpt
      name            = process.SimpleProcess.evaluateString(trimmedExpr, data, mapping)
    } yield {
      // This appears necessary for non-ASCII characters to make it through.
      // Verified that this works with GMail.
      jakarta.mail.internet.MimeUtility.encodeText(name, EmailContent.Charset, null)
    }
  }
}
