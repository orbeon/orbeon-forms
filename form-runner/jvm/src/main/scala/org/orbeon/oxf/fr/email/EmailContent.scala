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

import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunner, FormRunnerParams, GridDataMigration}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StaticXPath.tinyTreeToOrbeonDom
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.oxf.util.{ContentTypes, NetUtils, URLRewriterUtils}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.asScalaIterator
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps, *}

import java.net.URI


case class EmailContent(
  headers         : List[(String, String)],
  body            : String,
  htmlBody        : Boolean,
  xmlAttachmentOpt: Option[Attachment],
  pdfAttachmentOpt: Option[Attachment],
  fileAttachments : List[Attachment]
) {
  def allAttachments: List[Attachment] =
    xmlAttachmentOpt.toList ++ pdfAttachmentOpt.toList ++ fileAttachments
}

case class Attachment(filename: String, contentType: String, data: Either[URI, Array[Byte]])

object EmailContent {
  private val Charset = CharsetNames.Utf8

  def apply(
    formDefinition        : NodeInfo,
    formData              : NodeInfo,
    emailDataFormatVersion: DataFormatVersion,
    urisByRenderedFormat  : Map[RenderedFormat, URI],
    template              : EmailMetadata.Template,
    parameters            : List[EmailMetadata.Param]
  )(implicit
    formRunnerParams      : FormRunnerParams
  ): EmailContent = {
    // TODO: implement non-attachment parts

    val formDataMaybeMigrated = GridDataMigration.dataMaybeMigratedFromEdge(
      app                        = formRunnerParams.app,
      form                       = formRunnerParams.form,
      data                       = frc.formInstance.root,
      metadataOpt                = frc.metadataInstance.map(_.root),
      dstDataFormatVersionString = emailDataFormatVersion.entryName,
      // email-form.xpl reads the prune-metadata param, but the email action doesn't set it, so use the default value (i.e. false)
      pruneMetadata              = false,
      pruneTmpAttMetadata        = true
    )

    EmailContent(
      headers          = Nil, // TODO: headers
      body             = "",  // TODO: body
      htmlBody         = false,
      xmlAttachmentOpt = xmlAttachment(formData, formDataMaybeMigrated, template),
      pdfAttachmentOpt = urisByRenderedFormat.get(RenderedFormat.Pdf).flatMap(pdfAttachment(formData, _, template)),
      fileAttachments  = fileAttachments(formDefinition, formData, template)
    )
  }

  private def xmlAttachment(
    formData             : NodeInfo,
    formDataMaybeMigrated: NodeInfo,
    template             : EmailMetadata.Template
  )(implicit
    formRunnerParams     : FormRunnerParams
  ): Option[Attachment] =
    attachment(
      formData       = formData,
      attachOpt      = template.attachXml,
      attachmentType = "xml",
      contentType    = ContentTypes.makeContentTypeCharset(ContentTypes.XmlContentType, Some(Charset)),
      data           = Right(tinyTreeToOrbeonDom(formDataMaybeMigrated).serializeToString().getBytes(Charset))
    )

  private def pdfAttachment(
    formData        : NodeInfo,
    uri             : URI,
    template        : EmailMetadata.Template
  )(implicit
    formRunnerParams: FormRunnerParams
  ): Option[Attachment] =
    attachment(formData, template.attachPdf, RenderedFormat.Pdf.entryName, ContentTypes.PdfContentType, Left(uri))

  private def attachment(
    formData        : NodeInfo,
    attachOpt       : Option[Boolean],
    attachmentType  : String,
    contentType     : String,
    data            : Either[URI, Array[Byte]]
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

      val filename             = filenameOpt.getOrElse(s"form.$attachmentType")
      val dataWithRewrittenUri = data.left.map(_.rewrittenAsAbsoluteURI)

      Attachment(filename, contentType, dataWithRewrittenUri)
    }
  }

  private def fileAttachments(
    formDefinition  : NodeInfo,
    formData        : NodeInfo,
    template        : EmailMetadata.Template
  )(implicit
    formRunnerParams: FormRunnerParams
  ): List[Attachment] = {

    // TODO: use case objects
    val attachFiles = template.attachFiles.orElse(formRunnerProperty("oxf.fr.email.attach-files")).getOrElse("all")

    val body = (formDefinition / "*:body").head
    val head = (formDefinition / "*:head").head

    val AttachmentClass = "fr-attachment"

    val attachmentHolders =
      if (attachFiles == "all")
        searchHoldersForClassTopLevelOnly       (             body = body, data = formData, classNames = AttachmentClass).toList ++
        searchHoldersForClassUseSectionTemplates(head = head, body = body, data = formData, classNames = AttachmentClass).toList
      else if (attachFiles == "selected")
        values(formDefinition, formData, template.attachControls).toList
      else
        Nil

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
      val uriString    = tmpFileOpt.flatMap(_.trimAllToOpt).getOrElse(attachment.getStringValue)
      val rewrittenURI = new URI(uriString).rewrittenAsAbsoluteURI

      Attachment(
        filename    = filenameOpt.orElse(rewrittenURI.getPath.split("/").last.trimAllToOpt).getOrElse("unknown"),
        contentType = mediaTypeOpt.getOrElse(ContentTypes.OctetStreamContentType),
        data        = Left(rewrittenURI)
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