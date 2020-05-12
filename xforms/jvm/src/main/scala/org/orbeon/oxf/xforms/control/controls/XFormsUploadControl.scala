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

import java.io.File
import java.net.{URI, URLEncoder}

import org.orbeon.dom.Element
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{NetUtils, PathUtils, SecureUtils}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsUtils}
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.xforms.XFormsId
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable._

import scala.util.control.NonFatal

/**
 * Represents an xf:upload control.
 */
class XFormsUploadControl(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
    extends XFormsSingleNodeControl(container, parent, element, id)
    with XFormsValueControl
    with FocusableTrait
    with FileMetadata {

  def supportedFileMetadata: Seq[String] = FileMetadata.AllMetadataNames

  // NOTE: `mediatype` is deprecated as of XForms 2.0, use `accept` instead
  def acceptValue: Option[String] =
    extensionAttributeValue(ACCEPT_QNAME) orElse extensionAttributeValue(MEDIATYPE_QNAME)

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    markFileMetadataDirty()
  }

  override def computeValue: String = {

    val result = super.computeValue

    // This is ugly, but `evaluateFileMetadata` require that the value is set. If not, there will be an infinite loop.
    // We need to find a better solution.
    setValue(result)
    evaluateFileMetadata(isRelevant)

    result
  }

  // NOTE: Perform all actions at target, so that user event handlers are called after these operations.
  override def performTargetAction(event: XFormsEvent): Unit = {
    super.performTargetAction(event)
    event match {
      case _: XXFormsUploadStartEvent =>
        // Upload started
        containingDocument.startUpload(getUploadUniqueId)
      case _: XXFormsUploadProgressEvent =>
        // NOP: upload progress information will be sent through the diff process
      case _: XXFormsUploadCancelEvent =>
        // Upload canceled by the user
        containingDocument.endUpload(getUploadUniqueId)
        UploaderServer.removeUploadProgress(NetUtils.getExternalContext.getRequest, this)
      case doneEvent: XXFormsUploadDoneEvent =>
        // Upload done: process upload to this control
        // Notify that the upload has ended
        containingDocument.endUpload(getUploadUniqueId)
        UploaderServer.removeUploadProgress(NetUtils.getExternalContext.getRequest, this)
        handleUploadedFile(doneEvent.file, doneEvent.filename, doneEvent.contentType, doneEvent.contentLength)
        visitWithAncestors()
      case _: XXFormsUploadErrorEvent =>
        // Upload error: sent by the client in case of error
        containingDocument.endUpload(getUploadUniqueId)
        UploaderServer.removeUploadProgress(NetUtils.getExternalContext.getRequest, this)
      case _ =>
    }
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    super.performDefaultAction(event)
    event match {
      case _: XXFormsUploadErrorEvent =>
        // Upload error: sent by the client in case of error
        // It would be good to support i18n at the XForms engine level, but form authors can handle
        // xxforms-upload-error in a custom way if needed. This is what Form Runner does.
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
  def getUploadUniqueId: String = getEffectiveId

  // Called either upon Ajax `xxforms-upload-done` or upon client form POST (`replace="all"`)
  def handleUploadedFile(value: String, filename: String, mediatype: String, size: String): Unit =
    if (size != "0" || filename != "") {
      // Set value of uploaded file into the instance (will be xs:anyURI or xs:base64Binary)
      storeExternalValueAndMetadata(value, filename, mediatype, size)
    }

  // This can only be called from the client to clear the value
  override def storeExternalValue(value: String): Unit = {
    assert(value == "")
    storeExternalValueAndMetadata(value, "", "", "")
  }

  private def storeExternalValueAndMetadata(rawNewValue: String, filename: String, mediatype: String, size: String): Unit = {

    def isFileURL(url: String) =
      NetUtils.getProtocol(url) == "file"

    def deleteFileIfPossible(url: String): Unit =
      if (isFileURL(url))
        try {
          val file = new File(new URI(PathUtils.splitQuery(url)._1))
          if (file.exists) {
            if (file.delete())
              debug("deleted temporary file upon upload", List("path" -> file.getCanonicalPath))
            else
              warn("could not delete temporary file upon upload", List("path" -> file.getCanonicalPath))
          }
        } catch {
          case NonFatal(_) =>
            error("could not delete temporary file upon upload", List("path" -> url))
        }

    // Clean values
    val newValue = rawNewValue.trimAllToEmpty
    val oldValue = getValue.trimAllToEmpty
    try {
      // Only process if the new value is different from the old one
      val valueToStore =
        if (isFileURL(newValue)) {
          // Setting new file
          val convertedValue = {
            val isTargetBase64 = Set(XS_BASE64BINARY_QNAME, XFORMS_BASE64BINARY_QNAME)(valueType)
            if (isTargetBase64) {
              // Convert value to Base64 and delete incoming file
              val converted = NetUtils.anyURIToBase64Binary(newValue)
              deleteFileIfPossible(newValue)
              converted
            } else {
              // Leave value as is and make file expire with session
              val newFile = NetUtils.renameAndExpireWithSession(newValue, logger.getLogger)
              val newFileURL = newFile.toURI.toString

              // The result is a file: append a MAC
              hmacURL(newFileURL, Option(filename), Option(mediatype), Option(size))
            }
          }
          // Store the converted value
          convertedValue

        } else if (newValue.isEmpty) {
          // Setting blank value

          if (oldValue.nonEmpty)
            // TODO: This should probably take place during refresh instead.
            Dispatch.dispatchEvent(new XFormsDeselectEvent(this, EmptyGetter))

          // Try to delete temporary file associated with old value if any
          deleteFileIfPossible(oldValue)

          // Store blank value
          ""

        } else
          // Only accept file or blank
          throw new OXFException("Unexpected incoming value for xf:upload: " + newValue)

      // Store the value
      doStoreExternalValue(valueToStore)

      // NOTE: We used to call markFileMetadataDirty() here, but it was wrong, because getBackCopy would then
      // obtain the new data, and control diffs wouldn't work properly. This was done for XFormsSubmissionUtils,
      // which is now modified to use boundFileMediatype/boundFilename instead.

      // Filename, mediatype and size
      setFilename(filename)
      setFileMediatype(mediatype)
      setFileSize(size)

    } catch {
      case NonFatal(t) => throw new ValidationException(t, getLocationData)
    }
  }

  // Don't expose an external value
  override def evaluateExternalValue(): Unit = setExternalValue(null)

  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControl       : Option[XFormsControl]
  ): Boolean =
    previousControl match {
      case Some(other: XFormsUploadControl) =>
        compareFileMetadata(other) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl)
      case _ => false
    }

  override def addAjaxExtensionAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]): Boolean = {
    var added = super.addAjaxExtensionAttributes(attributesImpl, previousControlOpt)
    added |= addFileMetadataAttributes(attributesImpl, previousControlOpt.asInstanceOf[Option[FileMetadata]])
    added
  }

  override def findAriaByControlEffectiveId =
    Some(
      XFormsUtils.namespaceId(
        containingDocument,
        XFormsId.appendToEffectiveId(getEffectiveId, COMPONENT_SEPARATOR + "xforms-input")
      )
    )

  override def getBackCopy: AnyRef = {
    val cloned = super.getBackCopy.asInstanceOf[XFormsUploadControl]
    updateFileMetadataCopy(cloned)
    cloned
  }
}

object XFormsUploadControl {

  private val MacParamName = "mac"

  // XForms 1.1 mediatype is space-separated, XForms 2 accept is comma-separated like in HTML
  def mediatypeToAccept(s: String): String = s.splitTo() mkString ","

  // Append metadata and MAC to the URl
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
        name + '=' + URLEncoder.encode(value, CharsetNames.Utf8)
    } mkString "&"

    val urlWithQuery = PathUtils.appendQueryString(url, query)

    PathUtils.appendQueryString(urlWithQuery, MacParamName + '=' + hmac(urlWithQuery))
  }

  // Get the MAC for a given string
  def hmac(value: String): String =
    SecureUtils.hmacString(value, "hex")

  // Remove the MAC from the URL
  def removeMAC(url: String): String = {

    val uri = new URI(url)

    // NOTE: Use getRawQuery, as the query might encode & and =, and we should not decode them before decoding the query
    val query = Option(uri.getRawQuery) map PathUtils.decodeSimpleQuery getOrElse Nil

    val filteredQuery =
      query filterNot (_._1 == MacParamName) map {
        case (name, value) =>
          name + '=' + URLEncoder.encode(value, CharsetNames.Utf8)
      } mkString "&"

    PathUtils.appendQueryString(url.substring(0, url.indexOf('?')), filteredQuery)
  }

  // For Java callers
  def getParameterOrNull(url: String, name: String): String = PathUtils.getFirstQueryParameter(url, name).orNull

  // Get the MAC from the URL
  def getMAC(url: String): Option[String] = PathUtils.getFirstQueryParameter(url, "mac")

  // Check that the given URL as a correct MAC
  def verifyMAC(url: String): Boolean =
    getMAC(url) match {
      case Some(mac) => hmac(removeMAC(url)) == mac
      case None      => false
    }

  /**
   * Handle a construct of the form:
   *
   * <xxf:files>
   *   <parameter>
   *     <name>xforms-element-27</name>
   *     <filename>my-filename.jpg</filename>
   *     <content-type>image/jpeg</content-type>
   *     <content-length>33204</content-length>
   *     <value xmlns:request="http://orbeon.org/oxf/xml/request-private" xsi:type="xs:anyURI">file:/temp/upload_432dfead_11f1a9836128000_00000107.tmp</value>
   *   </parameter>
   *   <parameter>
   *     ...
   *   </parameter>
   * </xxf:files>
   */
  def handleSubmittedFiles(containingDocument: XFormsContainingDocument, filesElement: Element): Unit =
    for {
      (name, value, filename, mediatype, size) <- iterateFileElement(filesElement)
      // In case of `xf:repeat`, the name of the template will not match an existing control.
      // In addition, only set value on forControl control if specified.
      control       <- containingDocument.findControlByEffectiveId(name)
      uploadControl <- control.narrowTo[XFormsUploadControl]
    } locally {
      uploadControl.handleUploadedFile(value, filename, mediatype, size)
    }

  // Check if an <xxf:files> element actually contains file uploads to process
  def hasSubmittedFiles(filesElement: Element): Boolean =
    iterateFileElement(filesElement).nonEmpty

  private def iterateFileElement(filesElement: Element) =
    for {
      parameterElement <- Option(filesElement).toIterator flatMap Dom4j.elements

      // Extract all parameters
      name      <- parameterElement.elementOpt("name")                     flatMap (_.getText.trimAllToOpt)
      value     <- parameterElement.elementOpt("value")                    flatMap (_.getText.trimAllToOpt)

      // TODO: Ideally: `Option[Filename]`, `Option[ContentType]`, `Option[FileSize]`.
      filename  = parameterElement.elementOpt("filename")                 flatMap (_.getText.trimAllToOpt) getOrElse ""
      mediatype = parameterElement.elementOpt(Headers.ContentTypeLower)   flatMap (_.getText.trimAllToOpt) getOrElse ""
      size      = parameterElement.elementOpt(Headers.ContentLengthLower) flatMap (_.getText.trimAllToOpt) getOrElse ""

      // A file was selected in the UI (the file may be empty)
      if size != "0" || filename != ""
    } yield
      (name, value, filename, mediatype, size)
}