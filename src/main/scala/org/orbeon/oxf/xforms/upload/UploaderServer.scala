/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.upload

import org.apache.commons.fileupload.{FileItem, FileItemHeadersSupport, UploadContext}
import org.orbeon.datatypes.MaximumSize
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.io.LimiterInputStream
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CollectionUtils.collectByErasedType
import org.orbeon.oxf.util.IOUtils.runQuietly
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsContainingDocumentBase._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.control.XFormsValueControl

import scala.collection.{mutable ⇒ m}

object UploaderServer {

  import Private._

  def processUpload(request: Request): (List[(String, AnyRef)], Option[Throwable]) = {

    // Session is required to communicate with the XForms document
    val session = request.sessionOpt getOrElse (throw new IllegalStateException("upload requires a session"))

    val trustedUploadContext = new UploadContext {

      private val outerLimiterInputStream = new LimiterInputStream(
        request.getInputStream,
        LimitedSize(Multipart.DefaultBufferSize), // should be enough to read headers and `$uuid` (this is updated once first file is detected)
        Multipart.throwSizeLimitExceeded
      )

      val getContentType       = request.getContentType
      val getCharacterEncoding = request.getCharacterEncoding
      val getInputStream       = outerLimiterInputStream

      // Set to -1 because we want to be able to read at least `$uuid`
      val contentLength        = -1L
      def getContentLength     = -1 // this won't be used anyway
    }

    Multipart.parseMultipartRequest(
      uploadContext  = trustedUploadContext,
      lifecycleOpt   = Some(
        new UploadProgressMultipartLifecycle(request.contentLengthOpt, trustedUploadContext.getInputStream, session) {
          def getMaxUploadSizeForControl(uuid: String, controlName: String): MaximumSize =
            withDocumentAcquireLock(uuid, XFormsProperties.uploadXFormsAccessTimeout) {
              _.uploadMaxSizeForControl(controlName)
            }
        }
      ),
      maxSize        = MaximumSize.UnlimitedSize, // because we use our own limiter
      headerEncoding = ExternalContext.StandardHeaderCharacterEncoding
    )
  }

  def getUploadProgressFromSession(session: Option[Session], uuid: String, fieldName: String): Option[UploadProgress] =
    session flatMap (s ⇒ Option(s.getAttributesMap.get(getProgressSessionKey(uuid, fieldName)))) collect {
      case progress: UploadProgress ⇒ progress
    }

  def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress] =
    getUploadProgressFromSession(request.sessionOpt, uuid, fieldName)

  def removeUploadProgress(request: Request, control: XFormsValueControl): Unit =
    request.sessionOpt foreach {
      _.getAttributesMap.remove(getProgressSessionKey(control.containingDocument.getUUID, control.getEffectiveId))
    }

  // Public for tests
  abstract class UploadProgressMultipartLifecycle(
    requestContentLengthOpt : Option[Long],
    outerLimiterInputStream : LimiterInputStream,
    session                 : Session
  ) extends MultipartLifecycle {

    // Mutable state
    private var uuidOpt     : Option[String]         = None
    private var progressOpt : Option[UploadProgress] = None

    def fieldReceived(name: String, value: String): Unit = {
      require(value ne null)
      if (uuidOpt.isDefined)
        throw new IllegalStateException("more than one document UUID provided")
      else if (name == "$uuid")
        uuidOpt = Some(value)
    }

    // Session keys created, for cleanup
    val sessionKeys = m.ListBuffer[String]()

    def getMaxUploadSizeForControl(uuid: String, controlName: String): MaximumSize

    def fileItemStarting(name: String, fileItem: FileItem): Option[MaximumSize] = {

      val uuid =
        uuidOpt getOrElse (throw new IllegalStateException("missing document UUID"))

      if (progressOpt.isDefined)
        throw new IllegalStateException("more than one file provided")

      val maxUploadSizeForControl = getMaxUploadSizeForControl(uuid, name)

      // This is `None` with Chrome and Firefox at least
      val untrustedPartContentLengthOpt =
        for {
          headersSupport      ← collectByErasedType[FileItemHeadersSupport](fileItem)
          headers             ← Option(headersSupport.getHeaders)
          contentLengthString ← Option(headers.getHeader(Headers.ContentLength))
          contentLengthLong   ← NumericUtils.parseLong(contentLengthString)
        } yield
          contentLengthLong

      val untrustedExpectedSizeOpt = untrustedPartContentLengthOpt orElse requestContentLengthOpt

      // So that the XFCD is aware of progress information
      // Do this before checking size so that we can report the interrupted upload
      locally {
        val progress = UploadProgress(name, untrustedExpectedSizeOpt)

        val newSessionKey = getProgressSessionKey(uuid, name)
        sessionKeys += newSessionKey
        session.getAttributesMap.put(newSessionKey, progress)

        progressOpt = Some(progress)
      }

      // As of 2017-03-22: part `Content-Length` takes precedence if provided (but browsers don't set it).
      // Browsers do set the outer `Content-Length` though. Again we assume that the overhead of the
      // entire request vs. the part is small so it's ok, for progress purposes, to use the outer size.
      untrustedExpectedSizeOpt foreach { untrustedExpectedSize ⇒
        checkSizeLimitExceeded(maxSize = maxUploadSizeForControl, currentSize = untrustedExpectedSize)
      }

      // Otherwise update the outer limiter to support enough additional bytes
      // This is an approximation as there is overhead for `$uuid` and the part's headers.
      // The assumption is that the content of the upload is typically much larger than
      // the overhead.
      (outerLimiterInputStream.maxBytes, maxUploadSizeForControl) match {
        case (_,                   UnlimitedSize)         ⇒ outerLimiterInputStream.maxBytes = UnlimitedSize
        case (UnlimitedSize, LimitedSize(control))        ⇒ throw new IllegalStateException
        case (LimitedSize(current), LimitedSize(control)) ⇒ outerLimiterInputStream.maxBytes = LimitedSize(current + control)
      }

      Some(maxUploadSizeForControl)
    }

    def updateProgress(current: Long): Unit =
      progressOpt foreach (_.receivedSize += current)

    def fileItemState(state: UploadState): Unit =
      progressOpt foreach (_.state = state)

    def interrupted(): Unit = {
      // - don't remove `UploadProgress` objects from the session
      // - instead mark all entries added so far as being in state `Interrupted` if not already the case
      for (sessionKey ← sessionKeys)
        runQuietly (
          getUploadProgress(sessionKey)
          collect { case p @ UploadProgress(_, _, _, Started | Completed ) ⇒ p }
          foreach (_.state = Interrupted(None))
        )
    }

    def getUploadProgress(sessionKey: String): Option[UploadProgress] =
      Option(session.getAttributesMap.get(sessionKey)) collect {
        case progress: UploadProgress ⇒ progress
      }
  }

  private object Private {

    val UploadProgressSessionKey = "orbeon.upload.progress."

    def getProgressSessionKey(uuid: String, fieldName: String) =
      UploadProgressSessionKey + uuid + "." + fieldName

    def checkSizeLimitExceeded(maxSize: MaximumSize, currentSize: Long) = maxSize match {
      case UnlimitedSize        ⇒
      case LimitedSize(maxSize) ⇒
        if (currentSize > maxSize)
          Multipart.throwSizeLimitExceeded(maxSize, currentSize)
    }
  }
}
