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

import org.orbeon.connection.{ConnectionResult, StreamedContent}
import org.orbeon.dom.{Document, Element, VisitorSupport}
import org.orbeon.io.{CharsetNames, IOUtils}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.model.{InstanceData, XFormsInstance}
import org.orbeon.oxf.xml.{SaxonUtils, XMLParsing}
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.URI
import scala.collection.mutable


object SubmissionUtils {

  def logRequestBody(mediatype: String, messageBody: Array[Byte])(implicit logger: IndentedLogger): Unit =
    if (ContentTypes.isXMLMediatype(mediatype) ||
      ContentTypes.isTextOrJSONContentType(mediatype) ||
      mediatype == "application/x-www-form-urlencoded")
      logger.logDebug("submission", "setting request body", "body", new String(messageBody, CharsetNames.Utf8))
    else
      logger.logDebug("submission", "setting binary request body")

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
    resolveAttributeValueTemplates(
      containingDocument,
      refContext.xpathContext,
      refContext.refNodeInfo,
      value
    ) flatMap
      (_.trimAllToOpt)

  /**
   * Resolve attribute value templates (AVTs).
   *
   * @param xpathContext   current XPath context
   * @param contextNode    context node for evaluation
   * @param attributeValue attribute value
   * @return resolved attribute value
   */
  private def resolveAttributeValueTemplates(
    containingDocument : XFormsContainingDocument,
    xpathContext       : XPathCache.XPathContext,
    contextNode        : om.NodeInfo,
    attributeValue     : String
  ): Option[String] =
    if (attributeValue eq null)
      None
    else
      Option(XPathCache.evaluateAsAvt(xpathContext, contextNode, attributeValue, containingDocument.getRequestStats.getReporter))

  def booleanAvtOpt(
    value              : String)(implicit
    refContext         : RefContext,
    containingDocument : XFormsContainingDocument
  ): Option[Boolean] =
    stringAvtTrimmedOpt(value) map (_.toBoolean)

  def dataNodeHash(node: om.NodeInfo): String =
    XFormsCrossPlatformSupport.hmacStringToHexShort(SaxonUtils.buildNodePath(node) mkString ("/", "/", ""))

  def readByteArray(
    headersGetter       : String => Option[List[String]],
    resolvedAbsoluteUrl : URI)(implicit
    logger              : IndentedLogger,
    externalContext     : ExternalContext
  ): Array[Byte] =
    processGETConnection(headersGetter, resolvedAbsoluteUrl) { case (is, _) =>
      inputStreamToByteArray(is)
    }

  // NOTE: Copied and adapted from `NetUtils` but we don't want the dependency.
  def inputStreamToByteArray(is: InputStream): Array[Byte] = {
    val os = new ByteArrayOutputStream
    IOUtils.copyStreamAndClose(is, os) // new ByteArrayOutputStream(is)
    os.toByteArray
  }

  def readTinyTree(
    headersGetter       : String => Option[List[String]],
    resolvedAbsoluteUrl : URI,
    handleXInclude      : Boolean)(implicit
    logger              : IndentedLogger,
    externalContext     : ExternalContext
  ): (DocumentNodeInfoType, Map[String, List[String]]) =
    processGETConnection(headersGetter, resolvedAbsoluteUrl) { case (is, headers) =>
      XFormsCrossPlatformSupport.readTinyTree(
        XPath.GlobalConfiguration,
        is,
        resolvedAbsoluteUrl.toString,
        handleXInclude,
        handleLexical = true
      ) -> headers
    }

  private def processGETConnection[T](
    headersGetter       : String => Option[List[String]],
    resolvedAbsoluteUrl : URI)(
    body                : (InputStream, Map[String, List[String]]) => T)(implicit
    logger              : IndentedLogger,
    externalContext     : ExternalContext
  ): T = {
    val cxr = openGETConnection(headersGetter, resolvedAbsoluteUrl)
    ConnectionResult.withSuccessConnection(cxr, closeOnSuccess = true)(body(_, cxr.headers))
  }

  private def openGETConnection(
    headersGetter       : String => Option[List[String]],
    resolvedAbsoluteUrl : URI)(implicit
    logger              : IndentedLogger,
    externalContext     : ExternalContext
  ): ConnectionResult = {

    implicit val _coreCrossPlatformSupport = CoreCrossPlatformSupport

    Connection.connectNow(
      method          = GET,
      url             = resolvedAbsoluteUrl,
      credentials     = None,
      content         = None,
      headers         = Connection.buildConnectionHeadersCapitalizedIfNeeded(
        url              = resolvedAbsoluteUrl,
        hasCredentials   = false,
        customHeaders    = Map.empty,
        headersToForward = Connection.headersToForwardFromProperty,
        cookiesToForward = Connection.cookiesToForwardFromProperty,
        getHeader        = headersGetter
      ) mapValues (_.toList),
      loadState       = true,
      saveState       = true,
      logBody         = BaseSubmission.isLogBody
    )
  }

  def evaluateHeaders(
    submission           : XFormsModelSubmission,
    forwardClientHeaders : Boolean
  ): Map[String, List[String]] = {
    try {
      val headersToForward =
        clientHeadersToForward(submission.containingDocument.getRequestHeaders, forwardClientHeaders)

      SubmissionHeaders.evaluateHeaders(
        submission.getEffectiveId,
        submission.staticSubmission,
        headersToForward
      )(submission.model.getContextStack)

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

  def clientHeadersToForward(
    allHeaders           : Map[String, List[String]],
    forwardClientHeaders : Boolean
  ): Map[String, List[String]] =
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
