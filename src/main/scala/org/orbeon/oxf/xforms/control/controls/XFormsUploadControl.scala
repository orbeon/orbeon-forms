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

import org.apache.commons.lang3.StringUtils
import org.orbeon.dom.Element
import org.orbeon.oxf.common.{OXFException, ValidationException}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.Multipart._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{NetUtils, SecureUtils}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.XMLConstants._
import org.xml.sax.helpers.AttributesImpl

import scala.util.control.NonFatal

/**
 * Represents an xf:upload control.
 */
class XFormsUploadControl(container: XBLContainer, parent: XFormsControl, element: Element, id: String)
    extends XFormsSingleNodeControl(container, parent, element, id)
    with XFormsValueControl
    with FocusableTrait
    with FileMetadata {

  def supportedFileMetadata = FileMetadata.AllMetadataNames

  // NOTE: `mediatype` is deprecated as of XForms 2.0, use `accept` instead
  def acceptValue = extensionAttributeValue(ACCEPT_QNAME) orElse extensionAttributeValue(MEDIATYPE_QNAME)

  override def evaluateImpl(relevant: Boolean, parentRelevant: Boolean): Unit = {
    super.evaluateImpl(relevant, parentRelevant)
    evaluateFileMetadata(relevant)
  }

  override def markDirtyImpl(): Unit = {
    super.markDirtyImpl()
    markFileMetadataDirty()
  }

  // NOTE: Perform all actions at target, so that user event handlers are called after these operations.
  override def performTargetAction(event: XFormsEvent): Unit = {
    super.performTargetAction(event)
    event match {
      case startEvent: XXFormsUploadStartEvent ⇒
        // Upload started
        containingDocument.startUpload(getUploadUniqueId)
      case progressEvent: XXFormsUploadProgressEvent ⇒
        // NOP: upload progress information will be sent through the diff process
      case cancelEvent: XXFormsUploadCancelEvent ⇒
        // Upload canceled by the user
        containingDocument.endUpload(getUploadUniqueId)
        removeUploadProgress(NetUtils.getExternalContext.getRequest, this)
      case doneEvent: XXFormsUploadDoneEvent ⇒
        // Upload done: process upload to this control
        // Notify that the upload has ended
        containingDocument.endUpload(getUploadUniqueId)
        removeUploadProgress(NetUtils.getExternalContext.getRequest, this)
        handleUploadedFile(doneEvent.file, doneEvent.filename, doneEvent.mediatype, doneEvent.size)

        visited = true

      case errorEvent: XXFormsUploadErrorEvent ⇒
        // Upload error: sent by the client in case of error
        containingDocument.endUpload(getUploadUniqueId)
        removeUploadProgress(NetUtils.getExternalContext.getRequest, this)
      case _ ⇒
    }
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    super.performDefaultAction(event)
    event match {
      case errorEvent: XXFormsUploadErrorEvent ⇒
        // Upload error: sent by the client in case of error
        // It would be good to support i18n at the XForms engine level, but form authors can handle
        // xxforms-upload-error in a custom way if needed. This is what Form Runner does.
        containingDocument.addMessageToRun("There was an error during the upload.", "modal")
      case _ ⇒
    }
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    // Make sure to consider any upload associated with this control as ended
    containingDocument.endUpload(getUploadUniqueId)
  }

  // TODO: Need to move to using actual unique ids here, see:
  // http://wiki.orbeon.com/forms/projects/core-xforms-engine-improvements#TOC-Improvement-to-client-side-server-s
  def getUploadUniqueId = getEffectiveId

  // Called either upon Ajax xxforms-upload-done or upon client form POST (noscript, replace="all")
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
          val file = new File(new URI(splitQuery(url)._1))
          if (file.exists) {
            if (file.delete())
              debug("deleted temporary file upon upload", Seq("path" → file.getCanonicalPath))
            else
              warn("could not delete temporary file upon upload", Seq("path" → file.getCanonicalPath))
          }
        } catch {
          case NonFatal(_) ⇒
            error("could not delete temporary file upon upload", Seq("path" → url))
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

        } else if (StringUtils.isEmpty(newValue)) {
          // Setting blank value

          if (StringUtils.isNotEmpty(oldValue))
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
      case NonFatal(t) ⇒ throw new ValidationException(t, getLocationData)
    }
  }

  // Don't expose an external value
  override def evaluateExternalValue(): Unit = setExternalValue(null)

  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControl       : Option[XFormsControl]
  ): Boolean =
    previousControl match {
      case Some(other: XFormsUploadControl) ⇒
        compareFileMetadata(other) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl)
      case _ ⇒ false
    }

  override def addAjaxExtensionAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]) = {
    var added = super.addAjaxExtensionAttributes(attributesImpl, previousControlOpt)
    added |= addFileMetadataAttributes(attributesImpl, previousControlOpt.asInstanceOf[Option[FileMetadata]])
    added
  }

  override def getBackCopy: AnyRef = {
    val cloned = super.getBackCopy.asInstanceOf[XFormsUploadControl]
    updateFileMetadataCopy(cloned)
    cloned
  }
}

object XFormsUploadControl {

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
  def hmacURL(url: String, filename: Option[String], mediatype: Option[String], size: Option[String]) = {

    val candidates = Seq(
      "filename"  → filename,
      "mediatype" → mediatype,
      "size"      → size
    )

    val query = candidates collect { case (name, Some(value)) ⇒ name + '=' + URLEncoder.encode(value, "utf-8") } mkString "&"
    val urlWithQuery = NetUtils.appendQueryString(url, query)

    NetUtils.appendQueryString(urlWithQuery, "mac=" + hmac(urlWithQuery))
  }

  // Get the MAC for a given string
  def hmac(value: String) =
    SecureUtils.hmacString(value, "hex")

  // Remove the MAC from the URL
  def removeMAC(url: String) = {
    val uri = new URI(url)
    // NOTE: Use getRawQuery, as the query might encode & and =, and we should not decode them before decoding the query
    val query = Option(uri.getRawQuery) map decodeSimpleQuery getOrElse Seq()
    val filteredQuery = query filterNot (_._1 == "mac") map { case (name, value) ⇒ name + '=' + URLEncoder.encode(value, "utf-8") } mkString "&"

    NetUtils.appendQueryString(url.substring(0, url.indexOf('?')), filteredQuery)
  }

  // For Java callers
  def getParameterOrNull(url: String, name: String) = getFirstQueryParameter(url, name).orNull

  // Get the MAC from the URL
  def getMAC(url: String) = getFirstQueryParameter(url, "mac")

  // Check that the given URL as a correct MAC
  def verifyMAC(url: String) =
    getMAC(url) match {
      case Some(mac) ⇒ hmac(removeMAC(url)) == mac
      case None      ⇒ false
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
      (name, value, filename, mediatype, size) ← iterateFileElement(filesElement)
      // In case of xf:repeat, the name of the template will not match an existing control
      // In addition, only set value on forControl control if specified
      uploadControl ← Option(containingDocument.getControlByEffectiveId(name).asInstanceOf[XFormsUploadControl])
    } uploadControl.handleUploadedFile(value, filename, mediatype, size)

  // Check if an <xxf:files> element actually contains file uploads to process
  def hasSubmittedFiles(filesElement: Element) =
    iterateFileElement(filesElement).nonEmpty

  private def iterateFileElement(filesElement: Element) =
    for {
      parameterElement ← Option(filesElement).toIterator flatMap Dom4j.elements

      // Extract all parameters
      name = parameterElement.element("name").getTextTrim
      value = parameterElement.element("value").getTextTrim
      filename = Option(parameterElement.element("filename")) map (_.getTextTrim) getOrElse ""
      mediatype = Option(parameterElement.element(Headers.ContentTypeLower)) map (_.getTextTrim) getOrElse ""
      size = parameterElement.element(Headers.ContentLengthLower).getTextTrim

      // A file was selected in the UI (the file may be empty)
      if size != "0" || filename != ""
    } yield
      (name, value, filename, mediatype, size)
}