/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.nio.charset.Charset

import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.{InputStreamBody, StringBody}
import org.orbeon.dom.{Document, Element, VisitorSupport}
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.http.StreamedContent
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xforms.model.{InstanceData, XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsUtils}
import org.orbeon.oxf.xml.{SaxonUtils, TransformerUtils, XMLConstants, XMLParsing}
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.xforms.CrossPlatformSupport

import scala.collection.mutable

object SubmissionUtils {

  // Create an `application/x-www-form-urlencoded` string, encoded in UTF-8, based on the elements and text content
  // present in an XML document. This assumes that non-relevant elements are already pruned or blanked if needed.
  def createWwwFormUrlEncoded(document: Document, separator: String): String = {

    val builder = mutable.ListBuffer[(String, String)]()

    document.accept(
      new VisitorSupport {
        override def visit(element: Element): Unit =
          if (element.jElements.isEmpty)
            builder += element.getName -> element.getText
      }
    )

    PathUtils.encodeSimpleQuery(builder, separator)
  }

  // Result of `resolveAttributeValueTemplates` can be `None` if, e.g. you have an AVT like `resource="{()}"`!
  def stringAvtTrimmedOpt(
    value              : String)(implicit
    refContext         : RefContext,
    containingDocument : XFormsContainingDocument
  ): Option[String] =
    Option(
      XFormsUtils.resolveAttributeValueTemplates(
        containingDocument,
        refContext.xpathContext,
        refContext.refNodeInfo,
        value
      )
    ) flatMap (_.trimAllToOpt)

  def booleanAvtOpt(
    value              : String)(implicit
    refContext         : RefContext,
    containingDocument : XFormsContainingDocument
  ): Option[Boolean] =
    stringAvtTrimmedOpt(value) map (_.toBoolean)

  def dataNodeHash(node: NodeInfo): String =
    SecureUtils.hmacString(SaxonUtils.buildNodePath(node) mkString ("/", "/", ""), "hex")

  def readByteArray(model: XFormsModel, resolvedAbsoluteUrl: URI): Array[Byte] =
    processGETConnection(model, resolvedAbsoluteUrl) { is =>
      NetUtils.inputStreamToByteArray(is)
    }

  def readTinyTree(model: XFormsModel, resolvedAbsoluteUrl: URI, handleXInclude: Boolean): DocumentInfo =
    processGETConnection(model, resolvedAbsoluteUrl) { is =>
      TransformerUtils.readTinyTree(
        XPath.GlobalConfiguration,
        is,
        resolvedAbsoluteUrl.toString,
        handleXInclude,
        true
      )
    }

  private def processGETConnection[T](model: XFormsModel, resolvedAbsoluteUrl: URI)(body: InputStream => T): T =
    ConnectionResult.withSuccessConnection(openGETConnection(model, resolvedAbsoluteUrl), closeOnSuccess = true)(body)

  private def openGETConnection(model: XFormsModel, resolvedAbsoluteUrl: URI): ConnectionResult = {

    implicit val _logger          = model.indentedLogger
    implicit val _externalContext = CrossPlatformSupport.externalContext

    Connection(
      method          = GET,
      url             = resolvedAbsoluteUrl,
      credentials     = None,
      content         = None,
      headers         = Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedAbsoluteUrl,
        hasCredentials   = false,
        customHeaders    = Map(),
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = model.containingDocument.headersGetter
      ) mapValues (_.toList),
      loadState       = true,
      logBody         = BaseSubmission.isLogBody
    ).connect(
      saveState = true
    )
  }

  def evaluateHeaders(submission: XFormsModelSubmission, forwardClientHeaders: Boolean): Map[String, List[String]] = {
    try {
      val headersToForward =
        clientHeadersToForward(submission.containingDocument.getRequestHeaders, forwardClientHeaders)

      SubmissionHeaders.evaluateHeaders(
        submission.container,
        submission.model.getContextStack,
        submission.getEffectiveId,
        submission.staticSubmission.element,
        headersToForward
      )

    } catch {
      case e: OXFException =>
        throw new XFormsSubmissionException(
          submission  = submission,
          message     = e.getMessage,
          description = "processing <header> elements",
          throwable = e
        )
    }
  }

  def clientHeadersToForward(allHeaders: Map[String, List[String]], forwardClientHeaders: Boolean): Map[String, List[String]] = {
    if (forwardClientHeaders) {
      // Forwarding the user agent and accept headers makes sense when dealing with resources that
      // typically would come from the client browser, including:
      //
      // - submission with replace="all"
      // - dynamic resources loaded by xf:output
      //
      val toForward =
        for {
          name   <- List("user-agent", "accept")
          values <- allHeaders.get(name)
        } yield
          name -> values

      // Give priority to explicit headers
      toForward.toMap
    } else
      Map.empty[String, List[String]]
  }

  def forwardResponseHeaders(cxr: ConnectionResult, response: ExternalContext.Response): Unit =
    for {
      (headerName, headerValues) <- http.Headers.proxyHeaders(cxr.headers, request = false)
      headerValue                <- headerValues
    } locally {
      response.addHeader(headerName, headerValue)
    }

  // Whether there is at least one relevant upload control with pending upload bound to any node of the given instance
  def hasBoundRelevantPendingUploadControls(
    doc         : XFormsContainingDocument,
    instanceOpt : Option[XFormsInstance]
  ): Boolean =
    instanceOpt match {
      case Some(instance) if doc.countPendingUploads > 0 =>

        val boundRelevantPendingUploadControlsIt =
          for {
            uploadControl <- doc.controls.getCurrentControlTree.getUploadControls.iterator
            if uploadControl.isRelevant && doc.isUploadPendingFor(uploadControl)
            node          <- uploadControl.boundNodeOpt
            if instance.model.findInstanceForNode(node) exists (_ eq instance)
          } yield
            uploadControl

        boundRelevantPendingUploadControlsIt.nonEmpty
      case _ =>
        false
    }

  /**
   * Implement support for XForms 1.1 section "11.9.7 Serialization as multipart/form-data".
   *
   * @param document XML document to submit
   * @return MultipartRequestEntity
   */
  def createMultipartFormData(document: Document): MultipartEntity = {
    // Visit document
    val multipartEntity = new MultipartEntity
    document.accept(
      new VisitorSupport() {
        override final def visit(element: Element): Unit = {
          // Only care about elements
          // Only consider leaves i.e. elements without children elements
          if (element.elements.isEmpty) {
            val value = element.getText
            // Got one!
            val localName = element.getName
            val nodeType = InstanceData.getType(element)
            if (XMLConstants.XS_ANYURI_QNAME == nodeType) { // Interpret value as xs:anyURI
              if (InstanceData.getValid(element) && value.trimAllToOpt.isDefined) {
                // Value is valid as per xs:anyURI
                // Don't close the stream here, as it will get read later when the MultipartEntity
                // we create here is written to an output stream
                addPart(multipartEntity, URLFactory.createURL(value).openStream, element, value)
              } else {
                // Value is invalid as per xs:anyURI
                // Just use the value as is (could also ignore it)
                multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8)))
              }
            } else if (XMLConstants.XS_BASE64BINARY_QNAME == nodeType) {
              // Interpret value as xs:base64Binary
              if (InstanceData.getValid(element) && value.trimAllToOpt.isDefined) {
                // Value is valid as per xs:base64Binary
                addPart(multipartEntity, new ByteArrayInputStream(NetUtils.base64StringToByteArray(value)), element, null)
              } else {
                // Value is invalid as per xs:base64Binary
                multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8)))
              }
            } else {
              // Just use the value as is
              multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8)))
            }
          }
        }
      }
    )
    multipartEntity
  }

  private def addPart(multipartEntity: MultipartEntity, inputStream: InputStream, element: Element, url: String): Unit = {
    // Gather mediatype and filename if known
    // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those will be
    // removed during the next recalculate.
    // See this WG action item (which was decided but not carried out): "Clarify that upload activation produces
    // content and possibly filename and mediatype info as metadata. If available, filename and mediatype are copied
    // to instance data if upload filename and mediatype elements are specified. At serialization, filename and
    // mediatype from instance data are used if upload filename and mediatype are specified; otherwise, filename and
    // mediatype are drawn from upload metadata, if they were available at time of upload activation"
    //
    // See:
    // http://lists.w3.org/Archives/Public/public-forms/2009May/0052.html
    // http://lists.w3.org/Archives/Public/public-forms/2009Apr/att-0010/2009-04-22.html#ACTION2
    // See also this clarification:
    // http://lists.w3.org/Archives/Public/public-forms/2009May/0053.html
    // http://lists.w3.org/Archives/Public/public-forms/2009Apr/att-0003/2009-04-01.html#ACTION1
    // The bottom line is that if we can find the xf:upload control bound to a node to submit, we try to get
    // metadata from that control. If that fails (which can be because the control is non-relevant, bound to another
    // control, or never had nested xf:filename/xf:mediatype elements), we try URL metadata. URL metadata is only
    // present on nodes written by xf:upload as temporary file: URLs. It is not present if the data is stored as
    // xs:base64Binary. In any case, metadata can be absent.
    // If an xf:upload control saved data to a node as xs:anyURI, has xf:filename/xf:mediatype elements, is still
    // relevant and bound to the original node (as well as its children elements), and if the nodes pointed to by
    // the children elements have not been modified (e.g. by xf:setvalue), then retrieving the metadata via
    // xf:upload should be equivalent to retrieving it via the URL metadata.
    // Benefits of URL metadata: a single xf:upload can be used to save data to multiple nodes over time, and it
    // doesn't have to be relevant and bound upon submission.
    // Benefits of using xf:upload metadata: it is possible to modify the filename and mediatype subsequently.
    // URL metadata was added 2012-05-29.
    // Get mediatype, first via xf:upload control, or, if not found, try URL metadata
    var mediatype = InstanceData.getTransientAnnotation(element, "xxforms-mediatype")
    if (mediatype == null && url != null)
      mediatype = XFormsUploadControl.getParameterOrNull(url, "mediatype")
    // Get filename, first via xf:upload control, or, if not found, try URL metadata
    var filename = InstanceData.getTransientAnnotation(element, "xxforms-filename")
    if (filename == null && url != null)
      filename = XFormsUploadControl.getParameterOrNull(url, "filename")
    val contentBody = new InputStreamBody(inputStream, mediatype, filename)
    multipartEntity.addPart(element.getName, contentBody)
  }

  /**
   * Annotate the DOM with information about file name and mediatype provided by uploads if available.
   *
   * @param containingDocument current XFormsContainingDocument
   * @param currentInstance    instance containing the nodes to check
   */
  def annotateBoundRelevantUploadControls(containingDocument: XFormsContainingDocument, currentInstance: XFormsInstance): Unit =
    for {
      currentUploadControl <- containingDocument.controls.getCurrentControlTree.getUploadControls
      if currentUploadControl.isRelevant
      controlBoundNodeInfo <- currentUploadControl.boundNodeOpt
      if currentInstance eq currentInstance.model.getInstanceForNode(controlBoundNodeInfo)
    } locally {
      // Found one relevant upload control bound to the instance we are submitting
      // NOTE: special MIP-like annotations were added just before re-rooting/pruning element. Those
      // will be removed during the next recalculate.
      Option(currentUploadControl.boundFilename) foreach { fileName =>
        InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-filename", fileName)
      }
      Option(currentUploadControl.boundFileMediatype) foreach { mediatype =>
        InstanceData.setTransientAnnotation(controlBoundNodeInfo, "xxforms-mediatype", mediatype)
      }
    }

  def readTextContent(content: StreamedContent): Option[String] = {

    val mediatype = content.contentType flatMap ContentTypes.getContentTypeMediaType

    mediatype collect {
      case mediatype if ContentTypes.isXMLMediatype(mediatype) =>
        // TODO: RFC 7303 says that content type charset must take precedence with any XML mediatype.
        //
        // http://tools.ietf.org/html/rfc7303:
        //
        //  The former confusion
        //  around the question of default character sets for the two text/ types
        //  no longer arises because
        //
        //     [RFC7231] changes [RFC2616] by removing the ISO-8859-1 default and
        //     not defining any default at all;
        //
        //     [RFC6657] updates [RFC2046] to remove the US-ASCII [ASCII]
        //
        // [...]
        //
        // this specification sets the priority as follows:
        //
        //    A BOM (Section 3.3) is authoritative if it is present in an XML
        //    MIME entity;
        //
        //    In the absence of a BOM (Section 3.3), the charset parameter is
        //    authoritative if it is present
        //
        IOUtils.readStreamAsStringAndClose(XMLParsing.getReaderFromXMLInputStream(content.inputStream))
      case mediatype if ContentTypes.isTextOrJSONContentType(mediatype) =>
        val charset = content.contentType flatMap ContentTypes.getContentTypeCharset
        IOUtils.readStreamAsStringAndClose(content.inputStream, charset)
    }
  }
}
