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

import org.apache.commons.fileupload.{FileItem, FileItemHeadersSupport, FileUploadException, UploadContext}
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.datatypes.MediatypeRange.WildcardMediatypeRange
import org.orbeon.datatypes.{MaximumSize, Mediatype, MediatypeRange}
import org.orbeon.io.LimiterInputStream
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CollectionUtils.collectByErasedType
import org.orbeon.oxf.util.IOUtils.runQuietly
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.control.XFormsValueControl

import scala.collection.{mutable ⇒ m}

// Separate checking logic for sanity and testing
trait UploadCheckerLogic {

  def findAttachmentMaxSizeValidationMipFor(controlEffectiveId: String): Option[String]

  def currentUploadSizeAggregate     : Option[Long]
  def uploadMaxSizeProperty          : MaximumSize
  def uploadMaxSizeAggregateProperty : MaximumSize

  def uploadMaxSizeForControl(controlEffectiveId: String): MaximumSize = {

    def maximumSizeForControlOrDefault =
      findAttachmentMaxSizeValidationMipFor(controlEffectiveId) flatMap
        MaximumSize.unapply                                     getOrElse
        uploadMaxSizeProperty

    uploadMaxSizeAggregateProperty match {
      case UnlimitedSize ⇒
        // Aggregate size is not a factor so just use what we got for the control
        maximumSizeForControlOrDefault
      case LimitedSize(maximumAggregateSize) ⇒
        // Aggregate size is a factor
        currentUploadSizeAggregate match {
          case Some(currentAggregateSize) ⇒

            val remainingByAggregation = (maximumAggregateSize - currentAggregateSize) max 0L

            if (remainingByAggregation == 0) {
              LimitedSize(0)
            } else {
              maximumSizeForControlOrDefault match {
                case UnlimitedSize                   ⇒ LimitedSize(remainingByAggregation)
                case LimitedSize(remainingByControl) ⇒ LimitedSize(remainingByControl min remainingByAggregation)
              }
            }
          case None ⇒
            throw new IllegalArgumentException(s"missing `upload.max-size-aggregate-expression` property")
        }
    }
  }
}

case class DisallowedMediatypeException(permitted: Set[MediatypeRange], actual: Option[Mediatype])
  extends FileUploadException

sealed trait AllowedMediatypes
object AllowedMediatypes {

  case object AllowedAnyMediatype                                    extends AllowedMediatypes
  case class  AllowedSomeMediatypes(mediatypes: Set[MediatypeRange]) extends AllowedMediatypes {
    require(! mediatypes(WildcardMediatypeRange))
  }

  def unapply(s: String): Option[AllowedMediatypes] = {

    val mediatypeRanges =
      s.splitTo[List](" ,") flatMap { token ⇒
        token.trimAllToOpt
      } flatMap { trimmed ⇒
          MediatypeRange.unapply(trimmed)
      }

    if (mediatypeRanges.isEmpty)
      None
    else if (mediatypeRanges contains WildcardMediatypeRange)
      Some(AllowedAnyMediatype)
    else
      Some(AllowedSomeMediatypes(mediatypeRanges.to[Set]))
  }
}

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
          def getUploadConstraintsForControl(uuid: String, controlName: String): (MaximumSize, AllowedMediatypes) =
            withDocumentAcquireLock(uuid, XFormsProperties.uploadXFormsAccessTimeout) {
              _.getUploadConstraintsForControl(controlName)
            }
        }
      ),
      maxSize        = MaximumSize.UnlimitedSize, // because we use our own limiter
      headerEncoding = ExternalContext.StandardHeaderCharacterEncoding
    )
  }

  def getUploadProgressFromSession(session: Option[Session], uuid: String, fieldName: String): Option[UploadProgress] =
    session flatMap (_.getAttribute(getProgressSessionKey(uuid, fieldName))) collect {
      case progress: UploadProgress ⇒ progress
    }

  def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress] =
    getUploadProgressFromSession(request.sessionOpt, uuid, fieldName)

  def removeUploadProgress(request: Request, control: XFormsValueControl): Unit =
    request.sessionOpt foreach {
      _.removeAttribute(getProgressSessionKey(control.containingDocument.getUUID, control.getEffectiveId))
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

    def fieldReceived(name: String, value: String): Unit =
      if (name == "$uuid") {
        require(value ne null)
        require(uuidOpt.isEmpty, "more than one document UUID provided")

        uuidOpt = Some(value)
      }

    // Session keys created, for cleanup
    val sessionKeys = m.ListBuffer[String]()

    def getUploadConstraintsForControl(uuid: String, controlName: String): (MaximumSize, AllowedMediatypes)

    def fileItemStarting(name: String, fileItem: FileItem): Option[MaximumSize] = {

      val uuid =
        uuidOpt getOrElse (throw new IllegalStateException("missing document UUID"))

      if (progressOpt.isDefined)
        throw new IllegalStateException("more than one file provided")

      val (maxUploadSizeForControl, allowedMediatypeRangesForControl) =
        getUploadConstraintsForControl(uuid, name)

      // Handle max size
      locally {
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
          session.setAttribute(newSessionKey, progress)

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
      }

      // Handle mediatypes
      locally {
        allowedMediatypeRangesForControl match {
          case AllowedMediatypes.AllowedAnyMediatype ⇒
          case AllowedMediatypes.AllowedSomeMediatypes(allowedMediatypeRanges) ⇒

            val untrustedPartMediatypeOpt =
              for {
                headersSupport    ← collectByErasedType[FileItemHeadersSupport](fileItem)
                headers           ← Option(headersSupport.getHeaders)
                contentTypeString ← Option(headers.getHeader(Headers.ContentType))
                mediatypeString   ← ContentTypes.getContentTypeMediaType(contentTypeString)
                mediatype         ← Mediatype.unapply(mediatypeString)
              } yield
                mediatype

            untrustedPartMediatypeOpt match {
              case None ⇒
                throw DisallowedMediatypeException(allowedMediatypeRanges, None)
              case Some(untrustedPartMediatype) ⇒
                if (! (allowedMediatypeRanges exists untrustedPartMediatype.is))
                  throw DisallowedMediatypeException(allowedMediatypeRanges, Some(untrustedPartMediatype))
            }
        }
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
          collect { case p @ UploadProgress(_, _, _, UploadState.Started | UploadState.Completed ) ⇒ p }
          foreach (_.state = UploadState.Interrupted(None))
        )
    }

    def getUploadProgress(sessionKey: String): Option[UploadProgress] =
      session.getAttribute(sessionKey) collect {
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
