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

import org.apache.commons.fileupload.disk.DiskFileItem
import org.apache.commons.fileupload.{FileItem, FileItemHeaders, UploadContext}
import org.orbeon.datatypes.MaximumSize
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.io.IOUtils.{runQuietly, useAndClose}
import org.orbeon.io.LimiterInputStream
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.{Request, Session}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.FileItemSupport.*
import org.orbeon.oxf.util.Multipart.UploadItem
import org.orbeon.oxf.util.SLF4JLogging.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport.*
import org.orbeon.oxf.xforms.XFormsGlobalProperties
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.upload.api.java.{FileScan2, FileScanProvider2, FileScanResult as JFileScanResult}
import org.orbeon.oxf.xforms.upload.api.{FileScan, FileScanProvider, FileScanStatus}
import org.orbeon.xforms.Constants
import org.slf4j
import shapeless.syntax.typeable.*

import java.net.URI
import scala.collection.mutable as m
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}


object UploaderServer extends UploaderServer {

  case class UploadResponse(
    fieldName   : String,
    messageOpt  : Option[String],
    mediatypeOpt: Option[String],
    filenameOpt : Option[String],
    tmpFileUri  : URI,
    actualSize  : Long
  )

  protected def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[((MaximumSize, AllowedMediatypes), URI)] =
    withDocumentAcquireLock(
      uuid    = uuid,
      timeout = XFormsGlobalProperties.uploadXFormsAccessTimeout
    ) { doc =>
      doc.getUploadConstraintsForControl(controlEffectiveId) -> doc.getRequestUri
    }

  private def checkVersion(): Unit =
    Version.instance.requirePEFeature("File scan API")

  protected lazy val fileScanProviderOpt: Option[Either[FileScanProvider2, FileScanProvider]] =
      ServiceProviderSupport.loadProvider[FileScanProvider2]("file scan", checkVersion).kestrel(_.init()).map(Left.apply)
        .orElse(ServiceProviderSupport.loadProvider[FileScanProvider]("file scan", checkVersion).kestrel(_.init()).map(Right.apply))
}

trait UploaderServer {

  selfUploaderServer =>

  import Private.*
  import UploaderServer.*

  protected implicit val logger: slf4j.Logger = LoggerFactory.createLogger("org.orbeon.xforms.upload").logger

  protected def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[((MaximumSize, AllowedMediatypes), URI)]
  protected def fileScanProviderOpt: Option[Either[FileScanProvider2, FileScanProvider]]

  def processUpload(request: Request): (List[UploadResponse], Option[Throwable]) = {

    // Session is required to communicate with the XForms document
    val session = request.sessionOpt.getOrElse(throw new IllegalStateException("upload requires a session"))

    val trustedUploadContext = new UploadContext {

      private val outerLimiterInputStream = new LimiterInputStream(
        request.getInputStream,
        LimitedSize(Multipart.DefaultBufferSize), // should be enough to read headers and `$uuid` (this is updated once first file is detected)
        Multipart.throwSizeLimitExceeded
      )

      val getContentType       = request.getContentType
      val getCharacterEncoding = request.getCharacterEncoding
      val getInputStream       = outerLimiterInputStream

      def getMaxBytes                              = outerLimiterInputStream.maxBytes
      def setMaxBytes(maxBytes: MaximumSize): Unit = outerLimiterInputStream.maxBytes = maxBytes

      // Set to -1 because we want to be able to read at least `$uuid`
      val contentLength        = -1L
      def getContentLength     = -1 // this won't be used anyway
    }

    val result: (List[(String, UploadItem, Option[(FileScanAcceptResult, AllowedMediatypes)])], Option[Throwable]) =
      Multipart.parseMultipartRequest(
        uploadContext  = trustedUploadContext,
        lifecycleOpt   = Some(
          new UploadProgressMultipartLifecycle(
            requestContentLengthOpt  = request.contentLengthOpt,
            requestAcceptLanguageOpt = request.getFirstHeaderIgnoreCase(Headers.AcceptLanguage),
            getMaxBytes              = trustedUploadContext.getMaxBytes,
            setMaxBytes              = trustedUploadContext.setMaxBytes,
            session                  = session
          ) {
            def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[((MaximumSize, AllowedMediatypes), URI)] =
              selfUploaderServer.getUploadConstraintsForControl(uuid, controlEffectiveId)
          }
        ),
        maxSize        = MaximumSize.UnlimitedSize, // because we use our own limiter
        maxFiles       = Some(RequestGenerator.getMaxFilesProperty.toLong).filter(_ >= 0), // probably not really needed
        headerEncoding = ExternalContext.StandardHeaderCharacterEncoding,
        maxMemorySize  = -1 // make sure that the `FileItem`s returned always have an associated file
      )

    def quietlyDeleteFileItems(itemsToDelete: List[(String, UploadItem, Option[(FileScanAcceptResult, AllowedMediatypes)])]): Unit =
      itemsToDelete
        .collect { case (_, Right(fileItem: FileItem), _) => fileItem }
        .foreach(FileItemSupport.deleteFileItem(_, None))

    // Check post-file scan provider constraints
    result match {
      case (items, t @ Some(_)) =>
        // If there was already an error, don't bother checking for further errors
        quietlyDeleteFileItems(items)
        Nil -> t // currently, the caller does not need the failed uploads
      case (items, None) =>
        // No error so far, check mediatypes possibly updated by the file scan provider
        // https://github.com/orbeon/orbeon-forms/issues/6738
        val itemsWithThrowableOpt =
          items.collect { case (fieldName, Right(diskFileItem: DiskFileItem), fileScanAcceptResultTupleOpt) =>
            (fieldName, diskFileItem, fileScanAcceptResultTupleOpt.map(_._1)) ->
              Try(checkFileScanResultMediatypeThrowIfDisallowed(diskFileItem, fileScanAcceptResultTupleOpt)).toEither.left.toOption
          }

        itemsWithThrowableOpt
          .collectFirst { case (_, Some(t)) => t }
          match {
            case t @ Some(_) =>
              quietlyDeleteFileItems(items)
              Nil -> t // currently, the caller does not need the failed uploads
            case None =>
              itemsWithThrowableOpt.takeWhile(_._2.isEmpty).collect { case ((fieldName, diskFileItem, fileScanAcceptResultTupleOpt), _) =>
                uploadResponseFromFileScanResultOrDiskItem(fieldName, fileScanAcceptResultTupleOpt, diskFileItem)
              } -> None
          }
    }
  }

  private def uploadResponseFromFileScanResultOrDiskItem(
    fieldName              : String,
    fileScanAcceptResultOpt: Option[FileScanAcceptResult],
    diskFileItem           : DiskFileItem
  ): UploadResponse = {

    val messageOpt   = fileScanAcceptResultOpt.flatMap(_.message)
    val mediatypeOpt = fileScanAcceptResultOpt.flatMap(_.mediatype) orElse diskFileItem.nonBlankContentTypeOpt
    val filenameOpt  = fileScanAcceptResultOpt.flatMap(_.filename ) orElse diskFileItem.nonBlankClientFilenameOpt

    val (tmpFileUri, actualSize) = contentFromFileScanResultOrDiskItem(fileScanAcceptResultOpt, diskFileItem)

    UploadResponse(
      fieldName    = fieldName,
      messageOpt   = messageOpt,
      mediatypeOpt = mediatypeOpt,
      filenameOpt  = filenameOpt,
      tmpFileUri   = tmpFileUri,
      actualSize   = actualSize
    )
  }

  private def contentFromFileScanResultOrDiskItem(
    fileScanAcceptResultOpt: Option[FileScanAcceptResult],
    diskFileItem           : DiskFileItem
  ): (URI, Long) = {

    def sessionUrlAndSizeFromFileScan: Option[(URI, Long)] =
      fileScanAcceptResultOpt.flatMap(_.content).map { is =>
        useAndClose(is) { _ =>
          FileItemSupport.inputStreamToAnyURI(is, ExpirationScope.Session)
        }
      }

    // If there is a `FileScanProvider`, a `File` is obtained from the `DiskFileItem` separately. If by
    // any chance a new file is created on disk at that time, it will be deleted separately as the request
    // completes. Here again we would create a new request-expired file, before renaming it and making
    // sure it expires with the session only.
    def sessionUrlAndSizeFromFileItem: (URI, Long) = {

      val newFile =
        FileItemSupport.renameAndExpireWithSession(
          FileItemSupport.urlForFileItemCreateIfNeeded(diskFileItem, ExpirationScope.Request)
        )

      (newFile.toURI, newFile.length())
    }

//    FileItemSupport.Logger.debug(
//      s"UploaderServer got `FileItem` (disk location: `${fileItem.debugFileLocation}`)"
//    )

    sessionUrlAndSizeFromFileScan getOrElse sessionUrlAndSizeFromFileItem
  }

  // https://github.com/orbeon/orbeon-forms/issues/6738
  private def checkFileScanResultMediatypeThrowIfDisallowed(
    fileItem        : DiskFileItem,
    fileScanResulOpt: Option[(FileScanAcceptResult, AllowedMediatypes)]
  ): Unit =
    fileScanResulOpt match {
      // If the file scan provider returned a new and different mediatype, it will be used, so we need to revalidate
      // it.
      case
        Some(
          (
            FileScanAcceptResult(_, someMediatypeFromFileScan @ Some(mediatypeFromFileScan), filenameFromFileScanOpt, _, _),
            allowedMediatypes: AllowedMediatypes
          )
        )
        if ! fileItem.nonBlankContentTypeOpt.contains(mediatypeFromFileScan) => // small optimization: do only if file scan mediatype is different, as if same it has already been checked

        UploaderServer.checkMediatypesThrowIfDisallowed(
          allowedMediatypeRanges    = allowedMediatypes,
          nonBlankClientFilenameOpt = filenameFromFileScanOpt.flatMap(_.trimAllToOpt),
          header                    = _ => someMediatypeFromFileScan
        )
      // If the file scan provider:
      // - did not return a new mediatype
      // - but returned a new filename
      // - and there is no mediatype associated with the `FileItem`
      // Then the mediatype associated with the filename returned by the provider will be used. So check it here.
      // However, note that this probably will not happen, as there is always a mediatype sent with the multipart
      // request's file part
      case
        Some(
          (
            FileScanAcceptResult(_, None, someFilenameFromFileScan @ Some(_), _, _),
            allowedMediatypes: AllowedMediatypes
          )
        )
        if fileItem.nonBlankContentTypeOpt.isEmpty =>

        UploaderServer.checkMediatypesThrowIfDisallowed(
          allowedMediatypeRanges    = allowedMediatypes,
          nonBlankClientFilenameOpt = someFilenameFromFileScan.flatMap(_.trimAllToOpt),
          header                    = _ => None
        )
      case _ =>
    }

  def getUploadProgressForTests(
    session  : Session,
    uuid     : String,
    fieldName: String
  ): Option[UploadProgress[DiskFileItem]] =
    getUploadProgress(session, getProgressSessionKey(uuid, fieldName))

  private def getUploadProgress(
    session   : Session,
    sessionKey: String
  ): Option[UploadProgress[DiskFileItem]] =
    session.getAttribute(sessionKey)
      .flatMap(_.cast[UploadProgress[DiskFileItem]])

  private def setUploadProgress(
    session        : Session,
    uuid           : String,
    fieldName      : String,
    expectedSizeOpt: Option[Long],
    fileName       : Option[String]
  ): (String, UploadProgress[DiskFileItem]) = {
    val progress      = UploadProgress[DiskFileItem](fieldName, expectedSizeOpt)
    val newSessionKey = getProgressSessionKey(uuid, fieldName)
    session.setAttribute(newSessionKey, progress)
    newSessionKey -> progress
  }

  def getUploadProgress(
    request  : Request,
    uuid     : String,
    fieldName: String
  ): Option[UploadProgress[DiskFileItem]] =
    request.sessionOpt.flatMap(getUploadProgress(_, getProgressSessionKey(uuid, fieldName)))

  def removeUploadProgress(request: Request, control: XFormsValueControl): Unit = {
    request
      .sessionOpt
      .foreach(_.removeAttribute(getProgressSessionKey(control.containingDocument.uuid, control.effectiveId)))
  }

  def checkMediatypesThrowIfDisallowed(
    allowedMediatypeRanges   : AllowedMediatypes,
    nonBlankClientFilenameOpt: Option[String],
    header                   : String => Option[String],
  ): Unit =
    allowedMediatypeRanges match {
      case AllowedMediatypes.AllowedAnyMediatype =>
      case AllowedMediatypes.AllowedSomeMediatypes(allowedMediatypeRanges) =>
        Mediatypes.fromHeadersOrFilename(header, nonBlankClientFilenameOpt) match {
          case None =>
            throw DisallowedMediatypeException(nonBlankClientFilenameOpt, allowedMediatypeRanges, None)
          case Some(untrustedPartMediatype) =>
            if (! (allowedMediatypeRanges exists untrustedPartMediatype.is))
              throw DisallowedMediatypeException(nonBlankClientFilenameOpt, allowedMediatypeRanges, Some(untrustedPartMediatype))
        }
    }

  // Public for tests
  abstract class UploadProgressMultipartLifecycle(
    requestContentLengthOpt : Option[Long],
    requestAcceptLanguageOpt: Option[String],
    getMaxBytes             : MaximumSize,
    setMaxBytes             : MaximumSize => Unit,
    session                 : Session
  ) extends MultipartLifecycle[AllowedMediatypes] {

    import Private.*

    // Mutable state
    private var uuidOpt     : Option[String]                       = None
    private var progressOpt : Option[UploadProgress[DiskFileItem]] = None
    private var fileScanOpt : Option[Either[FileScan2, FileScan]]  = None

    def fieldReceived(fieldName: String, value: String): Unit =
      if (fieldName == Constants.UuidFieldName) {
        require(value ne null)
        require(uuidOpt.isEmpty, "more than one document UUID provided")

        uuidOpt = Some(value)
      }

    // Session keys created, for cleanup
    private val sessionKeys = m.ListBuffer[String]()

    def getUploadConstraintsForControl(uuid: String, controlEffectiveId: String): Try[((MaximumSize, AllowedMediatypes), URI)]

    // Can throw
    def fileItemStarting(fieldName: String, fileItem: DiskFileItem): (Option[MaximumSize], AllowedMediatypes) = {

      val uuid =
        uuidOpt getOrElse (throw new IllegalStateException("missing document UUID"))

      if (progressOpt.isDefined)
        throw new IllegalStateException("more than one file provided")

      val ((maxUploadSizeForControl, allowedMediatypeRangesForControl), requestUri) =
        getUploadConstraintsForControl(uuid, fieldName).get // TODO: will throw if this is a `Failure`

      val fileItemHeadersOpt = Option(fileItem.getHeaders)

      def findHeaderValue(name: String): Option[String] =
        for {
          headers <- fileItemHeadersOpt
          value   <- Option(headers.getHeader(name))
        } yield
          value

      // Handle max size
      locally {
        // This is `None` with Chrome and Firefox at least (2019-10-18: confirmed at least for Chrome)
        val untrustedPartContentLengthOpt: Option[Long] =
          findHeaderValue(Headers.ContentLength) flatMap NumericUtils.parseLong

        val untrustedExpectedSizeOpt = untrustedPartContentLengthOpt orElse requestContentLengthOpt

        // So that the XFCD is aware of progress information
        // Do this before checking size so that we can report the interrupted upload
        locally {
          val (newSessionKey, progress) = selfUploaderServer.setUploadProgress(session, uuid, fieldName, untrustedExpectedSizeOpt, fileItem.nonBlankClientFilenameOpt)
          sessionKeys += newSessionKey
          progressOpt = Some(progress)
        }

        // As of 2017-03-22: part `Content-Length` takes precedence if provided (but browsers don't set it).
        // Browsers do set the outer `Content-Length` though. Again we assume that the overhead of the
        // entire request vs. the part is small so it's ok, for progress purposes, to use the outer size.
        untrustedExpectedSizeOpt.foreach { untrustedExpectedSize =>
          UploadCheckerLogic.checkSizeLimitExceeded(
            maxSize     = maxUploadSizeForControl,
            currentSize = untrustedExpectedSize
          ).foreach(Multipart.throwSizeLimitExceeded(_, untrustedExpectedSize))
        }

        // Otherwise update the outer limiter to support enough additional bytes
        // This is an approximation as there is overhead for `$uuid` and the part's headers.
        // The assumption is that the content of the upload is typically much larger than
        // the overhead.
        (getMaxBytes, maxUploadSizeForControl) match {
          case (_,                    UnlimitedSize)        => setMaxBytes(UnlimitedSize)
          case (UnlimitedSize,        LimitedSize(_))       => throw new IllegalStateException
          case (LimitedSize(current), LimitedSize(control)) => setMaxBytes(LimitedSize(current + control))
        }
      }

      // Handle mediatypes
      checkMediatypesThrowIfDisallowed(
        allowedMediatypeRanges    = allowedMediatypeRangesForControl,
        nonBlankClientFilenameOpt = fileItem.nonBlankClientFilenameOpt,
        header                    = findHeaderValue
      )

      // https://github.com/orbeon/orbeon-forms/issues/5516
      if (Version.isPE)
        fileScanOpt =
          fileScanProviderOpt.flatMap {
            case Left(fileScanProviderV2) =>
              Try(
                fileScanProviderV2.startStream(
                  filename  = fileItem.getName,
                  headers   = FileScanProvider.convertHeadersToJava(fileItemHeadersOpt map convertFileItemHeaders getOrElse Nil),
                  language  = requestAcceptLanguageOpt.getOrElse("en"),
                  extension = Map[String, Any]("request.uri" -> requestUri).asJava
                )
              ) match {
                case Success(fs) => Some(Left(fs))
                case Failure(t)  => throw FileScanException(fieldName, FileScanErrorResult(Option(t.getMessage), Option(t)))
              }
            case Right(fileScanProvider) =>
              fileScanProvider.startStream(fileItem.getName, fileItemHeadersOpt map convertFileItemHeaders getOrElse Nil) match {
                case Success(fs) => Some(Right(fs))
                case Failure(t)  => throw FileScanException(fieldName, FileScanErrorResult(Option(t.getMessage), Option(t)))
              }
          }

      (Some(maxUploadSizeForControl), allowedMediatypeRangesForControl)
    }

    def updateProgress(b: Array[Byte], off: Int, len: Int): Option[FileScanResult] = {

      progressOpt foreach (_.receivedSize += len)

      fileScanOpt map {
        case Left(fileScan2) => withFileScanCall2(fileScan2.bytesReceived(b, off, len))
        case Right(fileScan) => withFileScanCall(fileScan.bytesReceived(b, off, len))
      }
    }

    // May return `Some[FileScanResult]` only if passed `UploadState.Completed`, `None` otherwise.
    def fileItemState(state: UploadState[DiskFileItem]): Option[FileScanResult] = {

      progressOpt foreach (_.state = state)

      state match {
        case UploadState.Completed(fileItem) =>

          val file = FileItemSupport.fileFromFileItemCreateIfNeeded(fileItem)

          fileScanOpt map {
            case Left(fileScan2) => withFileScanCall2(fileScan2.complete(file))
            case Right(fileScan) => withFileScanCall(fileScan.complete(file))
          }
        case UploadState.Interrupted(_) =>
          try
            fileScanOpt foreach {
              case Left(fileScan2) => fileScan2.abort()
              case Right(fileScan) => fileScan.abort()
            }
          catch {
            case NonFatal(t) =>
              info(s"error thrown by file scan provider while calling `abort()`: ${OrbeonFormatter.getThrowableMessage(t)}")
          }
          None
        case UploadState.Started =>
          None
      }
    }

    def interrupted(): Unit =
      // - don't remove `UploadProgress` objects from the session
      // - instead mark all entries added so far as being in state `Interrupted` if not already the case
      for (sessionKey <- sessionKeys)
        runQuietly {
          selfUploaderServer.getUploadProgress(session, sessionKey)
          .collect { case p @ UploadProgress(_, _, _, UploadState.Started | UploadState.Completed(_) ) => p }
          .foreach (_.state = UploadState.Interrupted(None))
        }
  }

  private object Private {

    private val UploadProgressSessionKey = "orbeon.upload.progress."

    def getProgressSessionKey(uuid: String, fieldName: String): String =
      UploadProgressSessionKey + uuid + "." + fieldName

    def convertFileItemHeaders(headers: FileItemHeaders): List[(String, List[String])] =
      for (name <- headers.getHeaderNames.asScala.toList)
        yield name -> headers.getHeaders(name).asScala.toList

    def withFileScanCall(block: => FileScanStatus): FileScanResult =
      Try(block) match {
        case Success(FileScanStatus.Accept) => FileScanAcceptResult(None)
        case Success(FileScanStatus.Reject) => FileScanRejectResult(Some("File scan rejected uploaded content"))
        case Success(FileScanStatus.Error)  => FileScanErrorResult(Some("File scan returned an error"), None)
        case Failure(t)                     => FileScanErrorResult(Option(t.getMessage), Option(t))
      }

    def withFileScanCall2(block: => JFileScanResult): FileScanResult =
      Try(block) match {
        case Success(fileScanResponse) => fileScanResultFromJavaApi(fileScanResponse)
        case Failure(t)                => FileScanErrorResult(message = Option(t.getMessage), throwable = Option(t))
      }

    // The Java API uses its own ADT. Here we convert from that to our native Scala ADT.
    private def fileScanResultFromJavaApi(jfsr: JFileScanResult): FileScanResult =
      jfsr match {
        case r: JFileScanResult.FileScanAcceptResult =>
          FileScanAcceptResult(
            message   = Option(r.message)  .flatMap(_.trimAllToOpt),
            mediatype = Option(r.mediatype).flatMap(_.trimAllToOpt),
            filename  = Option(r.filename) .flatMap(_.trimAllToOpt),
            content   = Option(r.content),
            extension = Option(r.extension).map(_.asScala.toMap)
          )
        case r: JFileScanResult.FileScanRejectResult =>
          FileScanRejectResult(
            message   = Option(r.message).flatMap(_.trimAllToOpt)
          )
        case r: JFileScanResult.FileScanErrorResult  =>
          FileScanErrorResult(
            message   = Option(r.message).flatMap(_.trimAllToOpt),
            throwable = Option(r.throwable)
          )
      }
  }
}
