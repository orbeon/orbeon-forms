/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.xforms

import java.io.{InputStream, StringReader}
import java.net.{URI, URISyntaxException}

import cats.syntax.option._
import javax.xml.transform.Result
import javax.xml.transform.dom.{DOMResult, DOMSource}
import org.ccil.cowan.tagsoup.HTMLSchema
import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.util.{Connection, CoreCrossPlatformSupport, IndentedLogger, NetUtils, URLRewriterUtils, UploadProgress}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.processor.XFormsAssetServer
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xml.{HTMLBodyXMLReceiver, TransformerUtils, XMLParsing, XMLReceiver}
import org.xml.sax.InputSource

import scala.util.control.NonFatal


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext = NetUtils.getExternalContext

  def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress[CoreCrossPlatformSupport.FileItemType]] =
    UploaderServer.getUploadProgress(request, uuid, fieldName)

  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: Int): String = {
    val resolvedURI = containingDocument.resolveXMLBase(element, url)
    URLRewriterUtils.rewriteServiceURL(externalContext.getRequest, resolvedURI.toString, rewriteMode)
  }

  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: Int): String = {
    val resolvedURI = containingDocument.resolveXMLBase(element, url)
    externalContext.getResponse.rewriteResourceURL(resolvedURI.toString, rewriteMode)
  }

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : dom.Element,
    url                : String,
    skipRewrite        : Boolean
  ): String = {
    val resolvedURI = containingDocument.resolveXMLBase(currentElement, url)
    val resolvedURIStringNoPortletFragment = uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument, resolvedURI)
    if (skipRewrite)
      resolvedURIStringNoPortletFragment
    else
      externalContext.getResponse.rewriteRenderURL(resolvedURIStringNoPortletFragment, null, null)
  }

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: dom.Element, url: String): String = {
    val resolvedURI = containingDocument.resolveXMLBase(currentElement, url)
    val resolvedURIStringNoPortletFragment = uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument, resolvedURI)
    externalContext.getResponse.rewriteActionURL(resolvedURIStringNoPortletFragment, null, null)
  }

  private def uriToStringRemoveFragmentForPortletAndEmbedded(containingDocument: XFormsContainingDocument, resolvedURI: URI): String =
    if ((containingDocument.isPortletContainer || containingDocument.isEmbedded) && resolvedURI.getFragment != null) {
      // Page was loaded from a portlet or embedding API and there is a fragment, remove it
      try new URI(resolvedURI.getScheme, resolvedURI.getRawAuthority, resolvedURI.getRawPath, resolvedURI.getRawQuery, null).toString
      catch {
        case e: URISyntaxException =>
          throw new OXFException(e)
      }
    } else
      resolvedURI.toString

  private val TagSoupHtmlSchema = new HTMLSchema

  private def htmlStringToResult(value: String, locationData: LocationData, result: Result): Unit = {
    try {
      val xmlReader = new org.ccil.cowan.tagsoup.Parser
      xmlReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, TagSoupHtmlSchema)
      xmlReader.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true)
      val identity = TransformerUtils.getIdentityTransformerHandler
      identity.setResult(result)
      xmlReader.setContentHandler(identity)
      val inputSource = new InputSource
      inputSource.setCharacterStream(new StringReader(value))
      xmlReader.parse(inputSource)
    } catch {
      case NonFatal(_) =>
        throw new ValidationException(s"Cannot parse value as text/html for value: `$value`", locationData)
    }
    //			r.setFeature(Parser.CDATAElementsFeature, false);
    //			r.setFeature(Parser.namespacesFeature, false);
    //			r.setFeature(Parser.ignoreBogonsFeature, true);
    //			r.setFeature(Parser.bogonsEmptyFeature, false);
    //			r.setFeature(Parser.defaultAttributesFeature, false);
    //			r.setFeature(Parser.translateColonsFeature, true);
    //			r.setFeature(Parser.restartElementsFeature, false);
    //			r.setFeature(Parser.ignorableWhitespaceFeature, true);
    //			r.setProperty(Parser.scannerProperty, new PYXScanner());
    //          r.setProperty(Parser.lexicalHandlerProperty, h);
  }

  def htmlStringToDocumentTagSoup(value: String, locationData: LocationData): org.w3c.dom.Document = {
    val document = XMLParsing.createDocument
    val domResult = new DOMResult(document)
    htmlStringToResult(value, locationData, domResult)
    document
  }

  // TODO: implement server-side plain text output with <br> insertion
  //    public static void streamPlainText(final ContentHandler contentHandler, String value, LocationData locationData, final String xhtmlPrefix) {
  //        // 1: Split string along 0x0a and remove 0x0d (?)
  //        // 2: Output string parts, and between them, output <xhtml:br> element
  //        try {
  //            contentHandler.characters(filteredValue.toCharArray(), 0, filteredValue.length());
  //        } catch (SAXException e) {
  //            throw new OXFException(e);
  //        }
  //    }
  def streamHTMLFragment(xmlReceiver: XMLReceiver, value: String, locationData: LocationData, xhtmlPrefix: String): Unit = {
    if (value.nonAllBlank) {
      // don't parse blank values
      val htmlDocument = htmlStringToDocumentTagSoup(value, locationData)
      // Stream fragment to the output
      if (htmlDocument != null)
        TransformerUtils.sourceToSAX(new DOMSource(htmlDocument), new HTMLBodyXMLReceiver(xmlReceiver, xhtmlPrefix))
    }
  }

  def proxyURI(
    uri              : String,
    filename         : Option[String],
    contentType      : Option[String],
    lastModified     : Long,
    customHeaders    : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    XFormsAssetServer.proxyURI(
        uri              = uri,
        filename         = filename,
        contentType      = contentType,
        lastModified     = lastModified,
        customHeaders    = customHeaders,
        headersToForward = Connection.headersToForwardFromProperty,
        getHeader        = getHeader
    )

  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String =
    proxyURI(
      uri              = NetUtils.base64BinaryToAnyURI(value, NetUtils.SESSION_SCOPE, logger.logger.logger),
      filename         = filename,
      contentType      = mediatype,
      lastModified     = -1,
      customHeaders    = evaluatedHeaders,
      getHeader        = getHeader
    )

  def renameAndExpireWithSession(
    existingFileURI  : String)(implicit
    logger           : IndentedLogger
  ): URI =
    NetUtils.renameAndExpireWithSession(existingFileURI, logger.logger.logger).toURI

  def inputStreamToRequestUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    useAndClose(inputStream) { is =>
      NetUtils.inputStreamToAnyURI(is, NetUtils.REQUEST_SCOPE, logger.logger.logger).some
    }

  def inputStreamToSessionUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[String] =
    useAndClose(inputStream) { is =>
      NetUtils.inputStreamToAnyURI(is, NetUtils.SESSION_SCOPE, logger.logger.logger).some
    }

  def getLastModifiedIfFast(absoluteURL: String): Long =
    NetUtils.getLastModifiedIfFast(absoluteURL)
}
