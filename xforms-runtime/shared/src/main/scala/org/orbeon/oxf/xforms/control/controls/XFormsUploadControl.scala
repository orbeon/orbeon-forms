/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import cats.syntax.option.*
import org.orbeon.datatypes.MaximumCurrentFiles
import org.orbeon.dom.{Element, QName}
import org.orbeon.io.FileUtils
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent.*
import org.orbeon.oxf.xforms.event.events.*
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.upload.UploadCheckerLogic
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLConstants.*
import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.saxon.om
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.orbeon.xforms.XFormsNames.*
import org.xml.sax.helpers.AttributesImpl

import java.net.URI
import scala.util.control.NonFatal


/**
 * Represents an xf:upload control.
 */
class XFormsUploadControl(container: XBLContainer, parent: XFormsControl, element: Element, _effectiveId: String)
    extends XFormsSingleNodeControl(container, parent, element, _effectiveId)
    with XFormsValueControl
    with SingleNodeFocusableTrait
    with FileMetadata {

  import XFormsUploadControl._

  def supportedFileMetadata: Seq[String] = FileMetadata.AllMetadataNames

  // NOTE: `mediatype` is deprecated as of XForms 2.0, use `accept` instead
  def acceptValue: Option[String] =
    extensionAttributeValue(ACCEPT_QNAME) orElse extensionAttributeValue(MEDIATYPE_QNAME)

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    markFileMetadataDirty()
  }

  override def computeValue(collector: ErrorEventCollector): String = {

    val result = super.computeValue(collector)

    // This is ugly, but `evaluateFileMetadata` require that the value is set. If not, there will be an infinite loop.
    // We need to find a better solution.
    setValue(result)
    evaluateFileMetadata(isRelevant, collector)

    result
  }

  // NOTE: Perform all actions at target, so that user event handlers are called after these operations.
  override def performTargetAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    super.performTargetAction(event, collector)
    event match {
      case _: XXFormsUploadStartEvent =>
        // Upload started
        containingDocument.startUpload(getUploadUniqueId)
      case _: XXFormsUploadProgressEvent =>
        // NOP: upload progress information will be sent through the diff process
      case _: XXFormsUploadCancelEvent =>
        // Upload canceled by the user
        containingDocument.endUpload(getUploadUniqueId)
        XFormsCrossPlatformSupport.removeUploadProgress(XFormsCrossPlatformSupport.externalContext.getRequest, this)
      case _: XXFormsUploadDoneEvent =>
        // 2024-11-04: Now just a notification event
        // https://github.com/orbeon/orbeon-forms/issues/6606
      case _: XXFormsUploadErrorEvent =>
        // Upload error: can be sent by the client in case of error, or as consequence of `xxforms-upload-store`
        containingDocument.endUpload(getUploadUniqueId)
        XFormsCrossPlatformSupport.removeUploadProgress(XFormsCrossPlatformSupport.externalContext.getRequest, this)
      case storeEvent: XXFormsUploadStoreEvent =>
        val (maxSize, maxCurrentFiles, _) = containingDocument.getUploadConstraintsForControl(effectiveId)
        maxCurrentFiles match {
          case MaximumCurrentFiles.LimitedFiles(current, max) if current >= max =>
            XFormsCrossPlatformSupport.removeUploadProgress(XFormsCrossPlatformSupport.externalContext.getRequest, this)
            Dispatch.dispatchEvent(
              new XXFormsUploadErrorEvent(
                this,
                Map(
                  "error-type" -> Some("max-files-per-control"),
                  "permitted"  -> Some(max)
                )
              ),
              collector
            )
          case _ =>
            // https://github.com/orbeon/orbeon-forms/issues/6606
            val size = storeEvent.contentLength.toLong
            UploadCheckerLogic.checkSizeLimitExceeded(
              maxSize     = maxSize,
              currentSize = size
            ) match {
              case Some(maxSize) =>
                XFormsCrossPlatformSupport.removeUploadProgress(XFormsCrossPlatformSupport.externalContext.getRequest, this)
                Dispatch.dispatchEvent(
                  new XXFormsUploadErrorEvent(
                    this,
                    Map(
                      "error-type" -> Some("size-error"),
                      "permitted"  -> Some(ByteSizeUtils.byteCountToDisplaySize(maxSize)),
                      "actual"     -> Some(ByteSizeUtils.byteCountToDisplaySize(size))
                    )
                  ),
                  collector
                )
              case None =>
                containingDocument.endUpload(getUploadUniqueId)
                XFormsCrossPlatformSupport.removeUploadProgress(XFormsCrossPlatformSupport.externalContext.getRequest, this)
                handleUploadedFile(
                  storeEvent.file,
                  Option(storeEvent.filename).map(PathUtils.filenameFromPath), // in case the filename contains a path
                  Option(storeEvent.contentType),
                  Option(storeEvent.contentLength),
                  collector
                )
                visitWithAncestors()
                Dispatch.dispatchEvent(
                  new XXFormsUploadDoneEvent(
                    this,
                    Map(
                      "filename"       -> Option(storeEvent.filename),
                      "content-type"   -> Option(storeEvent.contentType),
                      "content-length" -> Option(storeEvent.contentLength)
                    )
                  ),
                  collector
                )
            }
        }

      case _ =>
    }
  }

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    super.performDefaultAction(event, collector)
    event match {
      case _: XXFormsUploadErrorEvent =>
        // Upload error: sent by the client in case of error
        // It would be good to support i18n at the XForms engine level, but form authors can handle
        // `xxforms-upload-error` in a custom way if needed. This is what Form Runner does.
        containingDocument.addMessageToRun("There was an error during the upload.", "modal")
      case _ =>
    }
  }

  override def onDestroy(update: Boolean): Unit = {
    super.onDestroy(update)
    // Make sure to consider any upload associated with this control as ended
    containingDocument.endUpload(getUploadUniqueId)
  }

  // TODO: Need to move to using actual unique ids here, see:
  // http://wiki.orbeon.com/forms/projects/core-xforms-engine-improvements#TOC-Improvement-to-client-side-server-s
  def getUploadUniqueId: String = effectiveId

  private def handleUploadedFile(
    value    : String,
    filename : Option[String],
    mediatype: Option[String],
    size     : Option[String],
    collector: ErrorEventCollector
  ): Unit =
    if (size.exists(_ != "0") || filename.exists(_ != ""))
      storeExternalValueAndMetadata(value, filename, mediatype, size, collector)

  // This can only be called from the client to clear the value
  override def storeExternalValue(value: String, collector: ErrorEventCollector): Unit = {
    assert(value == "")
    storeExternalValueAndMetadata(value, None, None, None, collector)
  }

  private def storeExternalValueAndMetadata(
    rawNewValue : String,
    filename    : Option[String],
    mediatype   : Option[String],
    size        : Option[String],
    collector   : ErrorEventCollector
  ): Unit =
    try {

      val oldValueOpt = getValue(collector).trimAllToOpt

      // Attempt to delete the temporary file both when:
      //
      // - replacing an existing value
      // - clearing an existing value
      //
      // Note that the value might not point to a temporary file. In particular, it can
      // point to the persistence layer, in which case it will contain a path. When that's
      // the case, `deleteFileIfPossible()` will not attempt to delete the file.
      //
      oldValueOpt foreach XFormsCrossPlatformSupport.deleteFileIfPossible

      val valueToStore =
        normalizeAndCheckRawValue(rawNewValue) match {
          case Some(newValueUri) =>
            prepareValueAsBase64OrUri(
              newValueUri,
              filename,
              mediatype,
              size,
              valueType
            )
          case None =>

            // TODO: This should probably take place during refresh instead.
            if (oldValueOpt.nonEmpty)
              Dispatch.dispatchEvent(new XFormsDeselectEvent(this, EmptyGetter), collector)

            ""
        }

      // Store the value
      doStoreExternalValue(valueToStore, collector)

      // NOTE: We used to call markFileMetadataDirty() here, but it was wrong, because getBackCopy would then
      // obtain the new data, and control diffs wouldn't work properly. This was done for XFormsSubmissionUtils,
      // which is now modified to use boundFileMediatype/boundFilename instead.

      // Filename, mediatype and size
      setFilename(filename.getOrElse(""), collector)
      setFileMediatype(mediatype.getOrElse(""), collector)
      setFileSize(size.getOrElse(""), collector)

    } catch {
      case NonFatal(t) => throw new ValidationException(t, getLocationData)
  }

  // Don't expose an external value
  override def evaluateExternalValue(collector: ErrorEventCollector): Unit = setExternalValue(null)

  override def compareExternalUseExternalValue(
    previousExternalValue: Option[String],
    previousControl      : Option[XFormsControl],
    collector            : ErrorEventCollector
  ): Boolean =
    previousControl match {
      case Some(other: XFormsUploadControl) =>
        compareFileMetadata(other, collector) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl, collector)
      case _ => false
    }

  override def addAjaxExtensionAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {
    var added = super.addAjaxExtensionAttributes(attributesImpl, previousControlOpt, collector)
    added |= addFileMetadataAttributes(attributesImpl, previousControlOpt.asInstanceOf[Option[FileMetadata]], collector)
    added
  }

  override def getBackCopy(collector: ErrorEventCollector): AnyRef = {
    val cloned = super.getBackCopy(collector).asInstanceOf[XFormsUploadControl]
    updateFileMetadataCopy(cloned, collector)
    cloned
  }
}

object XFormsUploadControl {

  private val MacParamName = "mac"

  // Delete the temporary file associated with the old value if possible

  // Here, "possible" means the value is:
  //
  // - non-blank
  // - contains a URI
  // - that URI is a `file:` URI
  // - that `file:` URI points to a temporary file
  // - that file exists and can be deleted
  //
  // The caller can pass other types of URIs. In such cases, no file will be deleted.
  //
  //@XPathFunction
  def deleteFileIfPossible(urlString: String): Unit =
    XFormsCrossPlatformSupport.deleteFileIfPossible(urlString)

  // XForms 1.1 mediatype is space-separated, XForms 2 accept is comma-separated like in HTML
  def mediatypeToAccept(s: String): String = s.splitTo() mkString ","

  // Append metadata and MAC to the URL
  // The idea is that whenever the upload control stores a local file: URL, that URL contains a MAC (message
  // authentication code). This allows:
  //
  // - making sure that the URL has not been tampered with (e.g. xf:output now uses this so that you can't point it to
  //   any file: URL)
  // - easily searching instance for uploaded resources
  //
  // The MAC includes the URL protocol, path and metadata
  def hmacURL(url: String, filename: Option[String], mediatype: Option[String], size: Option[String]): String = {

    val candidates = List(
      "filename"  -> filename,
      "mediatype" -> mediatype,
      "size"      -> size
    )

    val query = candidates collect {
      case (name, Some(value)) =>
        name + '=' + value.encode
    } mkString "&"

    val urlWithQuery = PathUtils.appendQueryString(url, query)

    PathUtils.appendQueryString(urlWithQuery, MacParamName + '=' + hmac(urlWithQuery))
  }

  def hmac(value: String): String =
    XFormsCrossPlatformSupport.hmacStringForUpload(value, ByteEncoding.Hex)

  // Remove the MAC from the URL
  def removeMAC(url: String): String = {

    val uri = URI.create(url)

    // NOTE: Use getRawQuery, as the query might encode & and =, and we should not decode them before decoding the query
    val query = Option(uri.getRawQuery) map PathUtils.decodeSimpleQuery getOrElse Nil

    val filteredQuery =
      query filterNot (_._1 == MacParamName) map {
        case (name, value) =>
          name + '=' + value.encode
      } mkString "&"

    PathUtils.appendQueryString(url.substring(0, url.indexOf('?')), filteredQuery)
  }

  // Get the MAC from the URL
  def getMAC(url: String): Option[String] = PathUtils.getFirstQueryParameter(url, "mac")

  // Check that the given URL as a correct MAC
  def verifyMAC(url: String): Boolean =
    getMAC(url) match {
      case Some(mac) => hmac(removeMAC(url)) == mac
      case None      => false
    }

  /**
   * Convert a String in xs:anyURI to an xs:base64Binary.
   *
   * The URI has to be a URL. It is read entirely
   */
  private def anyURIToBase64Binary(value: URI): String = {

    val sb = new StringBuilder

    ContentHandlerOutputStream.copyStreamAndClose(
      XFormsCrossPlatformSupport.openUrlStream(value),
      new XMLReceiverAdapter {
        override def characters(ch: Array[Char], start: Int, length: Int): Unit =
          sb.appendAll(ch, start, length)
      }
    )

    sb.toString
  }

  private def normalizeAndCheckRawValue(rawNewValue: String): Option[URI] =
    rawNewValue.trimAllToOpt map (URI.create(_).normalize()) match {
      case someNewValueUri @ Some(newValueUri) if FileUtils.isTemporaryFileUri(newValueUri) =>
        someNewValueUri
      case Some(newValueUri) =>
        throw new OXFException(s"Unexpected incoming value for `xf:upload`: `$newValueUri`")
      case None =>
        None
    }

  private val Base64BinaryQNames = Set(XS_BASE64BINARY_QNAME, XFORMS_BASE64BINARY_QNAME)

  private def prepareValueAsBase64OrUri(
    newValueUri : URI,
    filename    : Option[String],
    mediatype   : Option[String],
    size        : Option[String],
    valueType   : QName)(implicit
    logger      : IndentedLogger
  ): String = {

    val newValueUriString = newValueUri.toString

    if (Base64BinaryQNames(valueType)) {
      val converted = anyURIToBase64Binary(newValueUri)
      deleteFileIfPossible(newValueUriString)
      converted
    } else {
      val newFileURL = XFormsCrossPlatformSupport.renameAndExpireWithSession(newValueUri).toString
      hmacURL(newFileURL, filename, mediatype, size)
    }
  }

  // NOTE: This is very similar to `storeExternalValueAndMetadata` but this doesn't depend on the
  // actual control.
  def updateExternalValueAndMetadata(
    boundNode   : om.NodeInfo,
    rawNewValue : String,
    filename    : Option[String],
    mediatype   : Option[String],
    size        : Long)(implicit
    logger      : IndentedLogger
  ): Unit = {

    val sizeStringOpt = size.toString.some

    val newTmpFileUriOpt = normalizeAndCheckRawValue(rawNewValue)

    boundNode.getStringValue.trimAllToOpt foreach
      deleteFileIfPossible

    val valueToStore =
      newTmpFileUriOpt match {
        case Some(newValueUri) =>
          prepareValueAsBase64OrUri(
            newValueUri,
            filename,
            mediatype,
            sizeStringOpt,
            XS_ANYURI_QNAME
          )
        case None =>
          ""
      }

    XFormsAPI.setvalue(
      List(boundNode),
      valueToStore
    )

    List(
      "filename"  -> filename,
      "mediatype" -> mediatype,
      "size"      -> sizeStringOpt
    ) collect { case (name, Some(value)) =>
      XFormsAPI.setvalue(
        boundNode /@ name,
        value
      )
    }
  }
}