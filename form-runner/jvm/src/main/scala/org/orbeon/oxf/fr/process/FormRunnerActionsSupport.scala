/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr.process

import cats.data.NonEmptyList
import org.apache.commons.fileupload.disk.DiskFileItem
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content.ByteArrayBody
import org.apache.http.entity.mime.{FormBodyPartBuilder, HttpMultipartMode, MultipartEntityBuilder}
import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.common.Defaults
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.fr.FormRunner.FormVersionParam
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.process.RenderedFormat.SupportedRenderFormatsMediatypes
import org.orbeon.oxf.fr.{FormRunner, FormRunnerMetadata, Names, XMLNames}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.PathUtils.{recombineQuery, splitQueryDecodeParams}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ConnectionResult, ContentTypes, CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, ExpirationScope, FileItemSupport, IndentedLogger, Mediatypes, SecureUtils, StaticXPath, URLRewriterUtils}
import org.orbeon.oxf.xforms.action.XFormsAPI.{inScopeContainingDocument, setvalue}
import org.orbeon.oxf.xforms.submission.{SubmissionUtils, XFormsModelSubmissionSupport}
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsCrossPlatformSupport.externalContext
import org.orbeon.xforms.{RelevanceHandling, XFormsCrossPlatformSupport}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.util.Random


object FormRunnerActionsSupport {

  private def paramsToAppend(processParams: ProcessParams, paramNames: List[String]): List[(String, String)] =
    paramNames collect {
      case name @ "process"             => name -> processParams.runningProcessId
      case name @ "app"                 => name -> processParams.app
      case name @ "form"                => name -> processParams.form
      case name @ FormVersionParam      => name -> processParams.formVersion.toString
      case name @ "document"            => name -> processParams.document
      case name @ "valid"               => name -> processParams.valid.toString
      case name @ "language"            => name -> processParams.language
      case name @ DataFormatVersionName => name -> processParams.dataFormatVersion
      case name @ Names.WorkflowStage   => name -> processParams.workflowStage
    }

  def updateUriWithParams(processParams: ProcessParams, uri: String, requestedParamNames: List[String]): String = {

    val (path, params) = splitQueryDecodeParams(uri)

    val incomingParamNames = (params map (_._1)).toSet

    // Give priority to parameters on the URI, see:
    // https://github.com/orbeon/orbeon-forms/issues/3861
    recombineQuery(path, paramsToAppend(processParams, requestedParamNames filterNot incomingParamNames) ::: params)
  }

  def buildMultipartEntity(
    dataMaybeLiveMaybeMigrated: DocumentNodeInfoType,
    parts                     : NonEmptyList[ContentToken],
    renderedFormatTmpFileUris : Set[(URI, RenderedFormat)],
    fromBasePaths             : Iterable[(String, Int)],
    toBasePath                : String,
    relevanceHandling         : RelevanceHandling,
    annotateWith              : Set[String],
    headersGetter             : String => Option[List[String]])(implicit
    logger                    : IndentedLogger
  ): (URI, String) = {

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport
    implicit val externalContext: ExternalContext = coreCrossPlatformSupport.externalContext

    // TODO: lazy if we don't need data or attachments
    val dataCopiedAndMaybeMigrated =
      XFormsModelSubmissionSupport.prepareXML(
        xfcd              = inScopeContainingDocument,
        ref               = dataMaybeLiveMaybeMigrated,
        relevanceHandling = relevanceHandling,
        namespaceContext  = ProcessInterpreter.StandardNamespaceMapping.mapping, // Q: other?
        annotateWith      = annotateWith,
        relevantAttOpt    = Some(XMLNames.FRRelevantQName)
      )

    // Find all instance nodes containing file URLs we need to upload
    val attachmentsWithHolder =
      FormRunner.collectAttachments(
        new DocumentWrapper(dataCopiedAndMaybeMigrated, null, StaticXPath.GlobalConfiguration),
        fromBasePaths.map(_._1),
        toBasePath,
        forceAttachments = true
      )

    def createCidForNode(node: NodeInfo): String =
      SecureUtils.hmacString(SaxonUtils.buildNodePath(node).mkString("/"), "hex")

    // Replace attachment paths/URLs in the (copied) data now. We don't need the URLs again since they have been
    // captured by `FormRunner.collectAttachments()`. We need to update the data before serializing attachments as
    // we want to serialize the XML before the attachments in the multipart body.
    attachmentsWithHolder foreach { case FormRunner.AttachmentWithHolder(_, _, holder) =>
      setvalue(holder, s"cid:${createCidForNode(holder)}")
    }

    def processXml(builder: MultipartEntityBuilder, xml: dom.Document): Unit = {

      val bytes =
        XFormsCrossPlatformSupport.serializeToByteArray(
          document           = xml,
          method             = "xml",
          encoding           = Defaults.DefaultEncodingForModernUse,
          versionOpt         = None,
          indent             = false,
          omitXmlDeclaration = false,
          standaloneOpt      = None
        )

      builder.addPart(
        FormBodyPartBuilder.create(
          "data.xml",
          new ByteArrayBody(
            bytes,
            ContentType.create(ContentTypes.XmlContentType, CharsetNames.Utf8),
            "data.xml"
          )
        )
        .setField(Headers.ContentDisposition, "form-data") // TODO: name?
        .build
      )
    }

    def processBinary(
      builder     : MultipartEntityBuilder,
      is          : InputStream,
      cid         : String,
      name        : String,
      mediatypeOpt: Option[String],
      filenameOpt : Option[String]
    ): Unit =
      builder.addPart(
        FormBodyPartBuilder.create(
          name,
          new ByteArrayBody(
            SubmissionUtils.inputStreamToByteArray(is),
            ContentType.create(mediatypeOpt.getOrElse(ContentTypes.OctetStreamContentType)),
            null
          )
        )
        .setField(
          Headers.ContentId,
          s"<$cid>"
        )
        .setField(
          Headers.ContentDisposition,
          Headers.buildContentDispositionHeader(filenameOpt.getOrElse(s"$name.bin"))._2
        )
        .build
      )

    def processAllAttachments(builder: MultipartEntityBuilder): Unit =
      attachmentsWithHolder foreach { case FormRunner.AttachmentWithHolder(beforeUrl, _, holder) =>

        def writeBody(is: InputStream): Unit =
          processBinary(
            builder,
            is,
            createCidForNode(holder),
            holder.localname,
            holder.attValueOpt("mediatype").flatMap(_.trimAllToOpt),
            holder.attValueOpt("filename").flatMap(_.trimAllToOpt)
          )

        for {
          successGetCxr <- ConnectionResult.trySuccessConnection(FormRunner.getAttachment(fromBasePaths, beforeUrl))
          _             <- ConnectionResult.tryBody(successGetCxr, closeOnSuccess = true)(writeBody)
        } yield
          ()
      }

    // Generate as we cannot get the one automatically generated by `MultipartEntityBuilder`
    val boundary = generateBoundary

    val builder =
      MultipartEntityBuilder.create()
        .setMode(HttpMultipartMode.STRICT)
        .setMimeSubtype("related")
        .setBoundary(boundary)

    // TODO: Pass specific order for parts.
    if (parts.exists(_ == ContentToken.Xml))
      processXml(builder, dataCopiedAndMaybeMigrated)

    if (parts.exists(_ == ContentToken.Metadata))
      processXml(builder, StaticXPath.tinyTreeToOrbeonDom(FormRunnerMetadata.createFormMetadataDocument))

    if (parts.exists(_ == ContentToken.Attachments))
      processAllAttachments(builder)

    parts collect {
      case ContentToken.Rendered(format, urlOnly) =>
        renderedFormatTmpFileUris collect {
          case (uri, `format`) if urlOnly =>
            ???
          case (uri, `format`) =>
            processBinary(
              builder,
              new ByteArrayInputStream(
                SubmissionUtils.readByteArray(
                  headersGetter,
                  URI.create(URLRewriterUtils.rewriteServiceURL(externalContext.getRequest, uri.toString, UrlRewriteMode.Absolute))
                )
              ), // TODO: stream?
              SecureUtils.hmacString(format.entryName, "hex"),
              format.entryName,
              SupportedRenderFormatsMediatypes.get(format),
              Some(s"file.${Mediatypes.findExtensionForMediatype(SupportedRenderFormatsMediatypes(format)).getOrElse(throw new IllegalStateException)}") // TODO: should use configured filename expression
            )
        }
    }

    val httpEntity = builder.build()

    val fileItem = FileItemSupport.prepareFileItem(ExpirationScope.Session)
    useAndClose(fileItem.getOutputStream)(httpEntity.writeTo)
    (fileItem.asInstanceOf[DiskFileItem].getStoreLocation.toURI, boundary)
  }

  private val MultipartChars = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray

  private def generateBoundary: String = {
    val buffer = new java.lang.StringBuilder
    val rand   = new Random
    val count  = rand.nextInt(11) + 30 // a random size from 30 to 40
    for (_ <- 0 until count)
      buffer.append(MultipartChars(rand.nextInt(MultipartChars.length)))
    buffer.toString
  }
}
