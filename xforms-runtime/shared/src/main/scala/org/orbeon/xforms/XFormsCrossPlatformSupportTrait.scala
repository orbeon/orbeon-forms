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

import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.dom.QName
import org.orbeon.dom.io.DocumentSource
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.processor.XPLConstants
import org.orbeon.oxf.util.CoreCrossPlatformSupport.FileItemType
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ByteEncoding, CoreCrossPlatformSupport, IndentedLogger, UploadProgress}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, PlainHTMLOrXHTMLReceiver, SkipRootElement, XMLReceiver}

import java.io.{ByteArrayOutputStream, InputStream, OutputStream, Writer}
import java.net.URI
import javax.xml.transform.sax.TransformerHandler
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{OutputKeys, Transformer}


//
// This object contains functions that are required both in the JVM and the JavaScript environment, but which
// must have separate implementations on each side.
//
trait XFormsCrossPlatformSupportTrait {

  def externalContext: ExternalContext

  def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress[FileItemType]]

  def removeUploadProgress(request: Request, control: XFormsValueControl): Unit

  def attachmentFileExists(holderValue: String): Boolean

  def resolveServiceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: UrlRewriteMode): String

  def resolveResourceURL(containingDocument: XFormsContainingDocument, element: dom.Element, url: String, rewriteMode: UrlRewriteMode): String

  def resolveRenderURL(
    containingDocument : XFormsContainingDocument,
    currentElement     : dom.Element,
    url                : String,
    skipRewrite        : Boolean
  ): String

  def rewriteURL(request: ExternalContext.Request, urlString: String, rewriteMode: UrlRewriteMode): String

  def streamHTMLFragment(
    value        : String,
    locationData : LocationData,
    xhtmlPrefix  : String)(implicit
    xmlReceiver  : XMLReceiver
  ): Unit

  private val DEFAULT_METHOD_PROPERTY_NAME = "default-method"
  private val DEFAULT_METHOD               = QName("html")

  def createHTMLFragmentXmlReceiver(writer: Writer, skipRootElement: Boolean): XMLReceiver = {

    val identity = getIdentityTransformerHandler

    applyOutputProperties(
      identity.getTransformer,
      method             = CoreCrossPlatformSupport.getPropertySet(
                             QName(
                               "html-converter",
                               XPLConstants.OXF_PROCESSORS_NAMESPACE
                             )
                           ).getQName(
                             DEFAULT_METHOD_PROPERTY_NAME,
                             DEFAULT_METHOD
                           ).clarkName,
      encoding           = CharsetNames.Utf8,
      omitXmlDeclaration = true
    )

    identity.setResult(new StreamResult(writer))

    val htmlReceiver = new PlainHTMLOrXHTMLReceiver("", new ForwardingXMLReceiver(identity, identity))

    if (skipRootElement)
      new SkipRootElement(htmlReceiver)
    else
      htmlReceiver
  }

  def resolveActionURL(containingDocument: XFormsContainingDocument, currentElement: dom.Element, url: String): String

  def htmlStringToDocumentTagSoup(value: String, locationData: LocationData): org.w3c.dom.Document

  def serializeToByteArray(
    document           : dom.Document,
    method             : String,
    encoding           : String,
    versionOpt         : Option[String],
    indent             : Boolean,
    omitXmlDeclaration : Boolean,
    standaloneOpt      : Option[Boolean],
  ): Array[Byte] = {

    val identity = getIdentityTransformer

    applyOutputProperties(
      identity,
      method             = method,
      encoding           = encoding,
      indentAmountOpt    = indent option 4,
      omitXmlDeclaration = omitXmlDeclaration,
      versionOpt         = versionOpt,
      standaloneOpt      = standaloneOpt
    )

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
  ): String

  def proxyBase64Binary(
    value            : String,
    filename         : Option[String],
    mediatype        : Option[String],
    evaluatedHeaders : Map[String, List[String]],
    getHeader        : String => Option[List[String]])(implicit
    logger           : IndentedLogger
  ): String

  def renameAndExpireWithSession(
    existingFileURI  : URI)(implicit
    logger           : IndentedLogger
  ): URI

  def inputStreamToRequestUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[URI]

  def inputStreamToSessionUri(
    inputStream      : InputStream)(implicit
    logger           : IndentedLogger
  ): Option[URI]

  def getLastModifiedIfFast(absoluteURL: String): Long

  // NOTE: Only referred to indirectly by Form Builder Summary page in order to load
  // external resources. This will not be called when offline!
  def readTinyTreeFromUrl(url: URI): DocumentNodeInfoType

  def readTinyTree(
    configuration  : SaxonConfiguration,
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): DocumentNodeInfoType

  def stringToTinyTree(
    configuration  : SaxonConfiguration,
    string         : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): DocumentNodeInfoType

  def readOrbeonDom(xmlString: String): dom.Document

  def readOrbeonDom(
    inputStream    : InputStream,
    systemId       : String,
    handleXInclude : Boolean,
    handleLexical  : Boolean
  ): dom.Document

  def hmacStringToHexShort(text: String): String
  def hmacString(text: String, encoding: ByteEncoding): String
  def digestBytes(bytes: Array[Byte], encoding: ByteEncoding): String

  def openUrlStream(url: URI): InputStream

  def writeMultipartFormData(document: dom.Document, os: OutputStream): String

  def getRootThrowable(t : Throwable) : Throwable

  def causesIterator(t : Throwable) : Iterator[Throwable]

  def tempFileSize(filePath: String): Long

  def deleteFileIfPossible(urlString: String): Unit

  protected def getIdentityTransformer: Transformer
  protected def getIdentityTransformerHandler: TransformerHandler

  def applyOutputProperties(
    transformer        : Transformer,
    method             : String,
    encoding           : String,
    indentAmountOpt    : Option[Int]     = None,
    omitXmlDeclaration : Boolean         = false,
    versionOpt         : Option[String]  = None,
    publicDoctypeOpt   : Option[String]  = None,
    systemDoctypeOpt   : Option[String]  = None,
    standaloneOpt      : Option[Boolean] = None
  ): Unit = {

    if (method.nonAllBlank)
      transformer.setOutputProperty(OutputKeys.METHOD, method)

    if (encoding.nonAllBlank)
      transformer.setOutputProperty(OutputKeys.ENCODING, encoding)

    transformer.setOutputProperty(OutputKeys.INDENT, if (indentAmountOpt.isDefined) "yes" else "no")

    indentAmountOpt foreach { indentAmount =>
      transformer.setOutputProperty(IndentAmountProperty, indentAmount.toString)
    }

    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (omitXmlDeclaration) "yes" else "no")

    versionOpt flatMap (_.trimAllToOpt) foreach { version =>
      transformer.setOutputProperty(OutputKeys.VERSION, version)
    }

    publicDoctypeOpt flatMap (_.trimAllToOpt) foreach { publicDoctype =>
      transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, publicDoctype)
    }

    systemDoctypeOpt flatMap (_.trimAllToOpt) foreach { systemDoctype =>
      transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemDoctype)
    }

    standaloneOpt foreach { standalone =>
      transformer.setOutputProperty(OutputKeys.STANDALONE, if (standalone) "yes" else "no")
    }
  }

  private val IndentAmountProperty = "{http://orbeon.org/oxf/}indent-spaces"
}
