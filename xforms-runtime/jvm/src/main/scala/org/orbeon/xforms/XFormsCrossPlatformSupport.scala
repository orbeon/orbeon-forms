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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream, StringReader, Writer}
import java.net.{URI, URISyntaxException}
import java.nio.charset.Charset

import cats.syntax.option._
import javax.xml.transform.Result
import javax.xml.transform.dom.{DOMResult, DOMSource}
import javax.xml.transform.stream.StreamResult
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.{InputStreamBody, StringBody}
import org.ccil.cowan.tagsoup.HTMLSchema
import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.dom.{Document, Element, QName, VisitorSupport}
import org.orbeon.dom.io.DocumentSource
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.processor.XPLConstants
import org.orbeon.oxf.processor.converter.{TextConverterBase, XMLConverter}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, SaxonConfiguration}
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.util.{Base64, Connection, CoreCrossPlatformSupport, IndentedLogger, NetUtils, PathUtils, SecureUtils, URLRewriterUtils, UploadProgress}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xforms.processor.XFormsAssetServer
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.oxf.xml.{HTMLBodyXMLReceiver, ParserConfiguration, PlainHTMLOrXHTMLReceiver, SkipRootElement, TransformerUtils, XMLConstants, XMLParsing, XMLReceiver}
import org.xml.sax.InputSource

import scala.util.control.NonFatal


object XFormsCrossPlatformSupport extends XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext = NetUtils.getExternalContext

  def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress[CoreCrossPlatformSupport.FileItemType]] =
    UploaderServer.getUploadProgress(request, uuid, fieldName)

  def removeUploadProgress(request: Request, control: XFormsValueControl): Unit =
    UploaderServer.removeUploadProgress(request, control)

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

  def createHTMLFragmentXmlReceiver(writer: Writer, skipRootElement: Boolean): XMLReceiver = {
    val identity = TransformerUtils.getIdentityTransformerHandler

    TransformerUtils.applyOutputProperties(
      identity.getTransformer,
      Properties.instance.getPropertySet(
        QName(
          "html-converter",
          XPLConstants.OXF_PROCESSORS_NAMESPACE
        )
      ).getQName(
        TextConverterBase.DEFAULT_METHOD_PROPERTY_NAME,
        XMLConverter.DEFAULT_METHOD
      ).clarkName,
      null,
      null,
      null,
      CharsetNames.Utf8,
      true,
      null,
      false,
      0
    )

    identity.setResult(new StreamResult(writer))

    val htmlReceiver = new PlainHTMLOrXHTMLReceiver("", identity)

    if (skipRootElement)
      new SkipRootElement(htmlReceiver)
    else
      htmlReceiver
  }

  def serializeToByteArray(
    document           : dom.Document,
    method             : String,
    encoding           : String,
    versionOpt         : Option[String],
    indent             : Boolean,
    omitXmlDeclaration : Boolean,
    standaloneOpt      : Option[Boolean],
  ): Array[Byte] = {
    val identity = TransformerUtils.getIdentityTransformer
    TransformerUtils.applyOutputProperties(
      identity,
      method,
      versionOpt.orNull,
      null,
      null,
      encoding,
      omitXmlDeclaration,
      standaloneOpt map java.lang.Boolean.valueOf orNull,
      indent,
      4
    )

    // TODO: use cdata-section-elements

    val os = new ByteArrayOutputStream
    identity.transform(new DocumentSource(document), new StreamResult(os))
    os.toByteArray
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

  def readTinyTree(
    configuration  : SaxonConfiguration,
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): DocumentNodeInfoType =
    TransformerUtils.readTinyTree(configuration, inputStream, systemId, handleXInclude, handleLexical)

  def stringToTinyTree(
    configuration  : SaxonConfiguration,
    string         : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): DocumentNodeInfoType =
    TransformerUtils.stringToTinyTree(configuration, string, handleXInclude, handleLexical)

  def readDom4j(xmlString: String): dom.Document =
    IOSupport.readDom4j(xmlString)

  def readDom4j(
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): dom.Document =
    TransformerUtils.readDom4j(inputStream, systemId, handleXInclude, handleLexical)

  def hmacString(text: String, encoding: String): String =
    SecureUtils.hmacString(text, encoding)

  def digestBytes(bytes: Array[Byte], encoding: String): String =
    SecureUtils.digestBytes(bytes, encoding)

  def openUrlStream(urlString: String): InputStream =
    URLFactory.createURL(urlString).openStream

  /**
   * Implement support for XForms 1.1 section "11.9.7 Serialization as multipart/form-data".
   *
   * @param document XML document to submit
   * @return MultipartRequestEntity
   */
  def writeMultipartFormData(document: Document, os: OutputStream): String = {
    // Visit document
    val multipartEntity = new MultipartEntity
    document.accept(
      new VisitorSupport {
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
                addPart(multipartEntity, XFormsCrossPlatformSupport.openUrlStream(value), element, value.some)
              } else {
                // Value is invalid as per xs:anyURI
                // Just use the value as is (could also ignore it)
                multipartEntity.addPart(localName, new StringBody(value, Charset.forName(CharsetNames.Utf8)))
              }
            } else if (XMLConstants.XS_BASE64BINARY_QNAME == nodeType) {
              // Interpret value as xs:base64Binary
              if (InstanceData.getValid(element) && value.trimAllToOpt.isDefined) {
                // Value is valid as per xs:base64Binary
                addPart(multipartEntity, new ByteArrayInputStream(Base64.decode(value)), element, None)
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
    multipartEntity.writeTo(os)
    multipartEntity.getContentType.getValue
  }

  private def addPart(
    multipartEntity : MultipartEntity,
    inputStream     : InputStream,
    element         : Element,
    url             : Option[String]
  ): Unit = {

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

    // Get mediatype, first via `xf:upload` control, or, if not found, try URL metadata
    val mediatype =
      (InstanceData.findTransientAnnotation(element, "xxforms-mediatype"), url) match {
        case (None, Some(url)) => PathUtils.getFirstQueryParameter(url, "mediatype")
        case (mediatypeOpt, _) => mediatypeOpt
      }

    // Get filename, first via xf:upload control, or, if not found, try URL metadata
    val filename =
      (InstanceData.findTransientAnnotation(element, "xxforms-filename"), url) match {
        case (None, Some(url)) => PathUtils.getFirstQueryParameter(url, "filename")
        case (filenameOpt, _)  => filenameOpt
      }

    val contentBody = new InputStreamBody(inputStream, mediatype.orNull, filename.orNull)
    multipartEntity.addPart(element.getName, contentBody)
  }
}
