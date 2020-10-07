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

import java.util.ServiceLoader

import org.apache.commons.fileupload.disk.DiskFileItem
import org.apache.commons.fileupload.{FileItem, FileItemHeaders, FileItemHeadersSupport, UploadContext}
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.datatypes.MediatypeRange.WildcardMediatypeRange
import org.orbeon.datatypes.{MaximumSize, Mediatype, MediatypeRange}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.LimiterInputStream
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CollectionUtils.{collectByErasedType, _}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.io.IOUtils.runQuietly
import org.orbeon.oxf.util.Multipart.UploadItem
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.upload.api.{FileScan, FileScanProvider, FileScanStatus}
import org.orbeon.xforms.Constants
import org.slf4j.LoggerFactory

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.collection.{mutable => m}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.collection.compat._
import shapeless.syntax.typeable._

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
      case UnlimitedSize =>
        // Aggregate size is not a factor so just use what we got for the control
        maximumSizeForControlOrDefault
      case LimitedSize(maximumAggregateSize) =>
        // Aggregate size is a factor
        currentUploadSizeAggregate match {
          case Some(currentAggregateSize) =>

            val remainingByAggregation = (maximumAggregateSize - currentAggregateSize) max 0L

            if (remainingByAggregation == 0) {
              LimitedSize(0)
            } else {
              maximumSizeForControlOrDefault match {
                case UnlimitedSize                   => LimitedSize(remainingByAggregation)
                case LimitedSize(remainingByControl) => LimitedSize(remainingByControl min remainingByAggregation)
              }
            }
          case None =>
            throw new IllegalArgumentException(s"missing `upload.max-size-aggregate-expression` property")
        }
    }
  }
}

sealed trait AllowedMediatypes
object AllowedMediatypes {

  case object AllowedAnyMediatype                                    extends AllowedMediatypes
  case class  AllowedSomeMediatypes(mediatypes: Set[MediatypeRange]) extends AllowedMediatypes {
    require(! mediatypes(WildcardMediatypeRange))
  }

  def unapply(s: String): Option[AllowedMediatypes] = {

    val mediatypeRanges =
      s.splitTo[List](" ,") flatMap { token =>
        token.trimAllToOpt
      } flatMap { trimmed =>
          MediatypeRange.unapply(trimmed)
      }

    if (mediatypeRanges.isEmpty)
      None
    else if (mediatypeRanges contains WildcardMediatypeRange)
      Some(AllowedAnyMediatype)
    else
      Some(AllowedSomeMediatypes(mediatypeRanges.to(Set)))
  }
}

object UploaderServer {

  import Private._

  def processUpload(request: Request): (List[(String, UploadItem)], Option[Throwable]) = {

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
          def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[(MaximumSize, AllowedMediatypes)] =
            withDocumentAcquireLock(
              uuid    = uuid,
              timeout = XFormsProperties.uploadXFormsAccessTimeout)(
              block   = _.getUploadConstraintsForControl(controlEffectiveId)
            ).flatten
        }
      ),
      maxSize        = MaximumSize.UnlimitedSize, // because we use our own limiter
      headerEncoding = ExternalContext.StandardHeaderCharacterEncoding,
      maxMemorySize  = -1 // make sure that the `FileItem`s returned always have an associated file
    )
  }

  def getUploadProgressFromSession(session: Option[Session], uuid: String, fieldName: String): Option[UploadProgress] =
    session flatMap (_.getAttribute(getProgressSessionKey(uuid, fieldName))) collect {
      case progress: UploadProgress => progress
    }

  def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress] =
    getUploadProgressFromSession(request.sessionOpt, uuid, fieldName)

  def removeUploadProgress(request: Request, control: XFormsValueControl): Unit =
    request.sessionOpt foreach {
      _.removeAttribute(getProgressSessionKey(control.containingDocument.uuid, control.getEffectiveId))
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
    private var fileScanOpt : Option[FileScan]       = None

    def fieldReceived(fieldName: String, value: String): Unit =
      if (fieldName == Constants.UuidFieldName) {
        require(value ne null)
        require(uuidOpt.isEmpty, "more than one document UUID provided")

        uuidOpt = Some(value)
      }

    // Session keys created, for cleanup
    val sessionKeys = m.ListBuffer[String]()

    def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[(MaximumSize, AllowedMediatypes)]

    // Can throw
    def fileItemStarting(fieldName: String, fileItem: FileItem): Option[MaximumSize] = {

      val uuid =
        uuidOpt getOrElse (throw new IllegalStateException("missing document UUID"))

      if (progressOpt.isDefined)
        throw new IllegalStateException("more than one file provided")

      val (maxUploadSizeForControl, allowedMediatypeRangesForControl) =
        getUploadConstraintsForControl(uuid, fieldName).get // TODO: will throw if this is a `Failure`

      val headersOpt =
        for {
          headersSupport <- collectByErasedType[FileItemHeadersSupport](fileItem)
          headers        <- Option(headersSupport.getHeaders)
        } yield
          headers


      // Handle max size
      locally {
        // This is `None` with Chrome and Firefox at least (2019-10-18: confirmed at least for Chrome)
        val untrustedPartContentLengthOpt =
          for {
            headers             <- headersOpt
            contentLengthString <- Option(headers.getHeader(Headers.ContentLength))
            contentLengthLong   <- NumericUtils.parseLong(contentLengthString)
          } yield
            contentLengthLong

        val untrustedExpectedSizeOpt = untrustedPartContentLengthOpt orElse requestContentLengthOpt

        // So that the XFCD is aware of progress information
        // Do this before checking size so that we can report the interrupted upload
        locally {
          val progress = UploadProgress(fieldName, untrustedExpectedSizeOpt)

          val newSessionKey = getProgressSessionKey(uuid, fieldName)
          sessionKeys += newSessionKey
          session.setAttribute(newSessionKey, progress)

          progressOpt = Some(progress)
        }

        // As of 2017-03-22: part `Content-Length` takes precedence if provided (but browsers don't set it).
        // Browsers do set the outer `Content-Length` though. Again we assume that the overhead of the
        // entire request vs. the part is small so it's ok, for progress purposes, to use the outer size.
        untrustedExpectedSizeOpt foreach { untrustedExpectedSize =>
          checkSizeLimitExceeded(maxSize = maxUploadSizeForControl, currentSize = untrustedExpectedSize)
        }

        // Otherwise update the outer limiter to support enough additional bytes
        // This is an approximation as there is overhead for `$uuid` and the part's headers.
        // The assumption is that the content of the upload is typically much larger than
        // the overhead.
        (outerLimiterInputStream.maxBytes, maxUploadSizeForControl) match {
          case (_,                   UnlimitedSize)         => outerLimiterInputStream.maxBytes = UnlimitedSize
          case (UnlimitedSize, LimitedSize(control))        => throw new IllegalStateException
          case (LimitedSize(current), LimitedSize(control)) => outerLimiterInputStream.maxBytes = LimitedSize(current + control)
        }
      }

      // Handle mediatypes
      locally {
        allowedMediatypeRangesForControl match {
          case AllowedMediatypes.AllowedAnyMediatype =>
          case AllowedMediatypes.AllowedSomeMediatypes(allowedMediatypeRanges) =>

            def findHeaderValue(name: String) =
              for {
                support <- fileItem.cast[FileItemHeadersSupport]
                headers <- Option(support.getHeaders)
                value   <- Option(headers.getHeader(name))
              } yield
                value

            Mediatypes.fromHeadersOrFilename(findHeaderValue, fileItem.getName.trimAllToOpt) match {
              case None =>
                throw DisallowedMediatypeException(allowedMediatypeRanges, None)
              case Some(untrustedPartMediatype) =>
                if (! (allowedMediatypeRanges exists untrustedPartMediatype.is))
                  throw DisallowedMediatypeException(allowedMediatypeRanges, Some(untrustedPartMediatype))
            }
        }
      }

      fileScanProviderOpt foreach { fileScanProvider =>
        fileScanOpt = fileScanProvider.startStream(fileItem.getName, headersOpt map convertFileItemHeaders getOrElse Nil) match {
          case Success(fs) => Some(fs)
          case Failure(t)  => throw FileScanException(t.getMessage)
        }
      }

      Some(maxUploadSizeForControl)
    }

    // Can throw
    def updateProgress(b: Array[Byte], off: Int, len: Int): Unit = {

      progressOpt foreach (_.receivedSize += len)

      fileScanOpt foreach { fileScan =>
        withFileScanCall(fileScan.bytesReceived(b, off, len))
      }
    }

    // Can throw
    def fileItemState(state: UploadState): Unit = {

      state match {
        case UploadState.Completed(fileItem) => fileScanOpt foreach (fileScan => withFileScanCall(fileScan.complete(fileFromFileItemCreateIfNeeded(fileItem))))
        case UploadState.Interrupted(_)      => runQuietly(fileScanOpt foreach (_.abort())) // TODO: maybe log?
        case _ =>
      }

      progressOpt foreach (_.state = state)
    }

    def interrupted(): Unit = {
      // - don't remove `UploadProgress` objects from the session
      // - instead mark all entries added so far as being in state `Interrupted` if not already the case
      for (sessionKey <- sessionKeys)
        runQuietly (
          getUploadProgress(sessionKey)
          collect { case p @ UploadProgress(_, _, _, UploadState.Started | UploadState.Completed(_) ) => p }
          foreach (_.state = UploadState.Interrupted(None))
        )
    }

    private def getUploadProgress(sessionKey: String): Option[UploadProgress] =
      session.getAttribute(sessionKey) collect {
        case progress: UploadProgress => progress
      }
  }

  private object Private {

    val Logger = LoggerFactory.getLogger("org.orbeon.xforms.upload")

    val UploadProgressSessionKey = "orbeon.upload.progress."

    def getProgressSessionKey(uuid: String, fieldName: String) =
      UploadProgressSessionKey + uuid + "." + fieldName

    def checkSizeLimitExceeded(maxSize: MaximumSize, currentSize: Long) = maxSize match {
      case UnlimitedSize        =>
      case LimitedSize(maxSize) =>
        if (currentSize > maxSize)
          Multipart.throwSizeLimitExceeded(maxSize, currentSize)
    }

    def convertFileItemHeaders(headers: FileItemHeaders) =
      for (name <- headers.getHeaderNames.asScala.to(List))
        yield name -> headers.getHeaders(name).asScala.to(List)

    // The file will expire with the request
    // We now set the threshold of `DiskFileItem` to `-1` so that a file is already created in the first
    // place, so this should never create a file but just use the one from the `DiskItem`. One unclear
    // case is that of a zero-length file, which will probably not be created by `DiskFileItem` as nothing
    // is written.
    def fileFromFileItemCreateIfNeeded(fileItem: DiskFileItem): java.io.File =
      new java.io.File(new URI(RequestGenerator.urlForFileItemCreateIfNeeded(fileItem, NetUtils.REQUEST_SCOPE)))

    def withFileScanCall(block: => FileScanStatus): FileScanStatus =
      Try(block) match {
        case Failure(t)                     => throw FileScanException(t.getMessage)
        case Success(FileScanStatus.Reject) => throw FileScanException("File scan rejected uploaded content")
        case Success(FileScanStatus.Error)  => throw FileScanException("File scan returned an error")
        case Success(FileScanStatus.Accept) => FileScanStatus.Accept
      }

    lazy val fileScanProviderOpt: Option[FileScanProvider] =
      try {
        Version.instance.requirePEFeature("File scan API")

        Option(ServiceLoader.load(classOf[FileScanProvider])) flatMap { serviceLoader =>
          serviceLoader.iterator.asScala.nextOption() |!> { provider =>
            Logger.info(s"Initializing file scan provider `${provider.getClass.getName}`")
            provider.init()
          }
        }
      } catch {
        case NonFatal(t) =>
          Logger.error(s"Failed to obtain file scan provider:\n${OrbeonFormatter.format(t)}")
          None
      }
  }
}
