/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException
import org.apache.commons.fileupload._
import org.apache.commons.fileupload.disk.{DiskFileItem, DiskFileItemFactory}
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.fileupload.util.Streams
import org.orbeon.datatypes.MaximumSize.LimitedSize
import org.orbeon.datatypes.{MaximumSize, Mediatype, MediatypeRange}
import org.orbeon.errorified.Exceptions
import org.orbeon.io.IOUtils._
import org.orbeon.io.{CharsetNames, LimiterInputStream}
import org.orbeon.oxf.externalcontext.ExternalContext._
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreCrossPlatformSupport.properties
import shapeless.syntax.typeable._

import java.io.OutputStream
import java.{util => ju}
import scala.collection.{mutable => m}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal


case class DisallowedMediatypeException(
  filename  : String,
  permitted : Set[MediatypeRange],
  actual    : Option[Mediatype]
) extends FileUploadException

case class EmptyFileException() extends FileUploadException

case class FileScanException(
  fieldName      : String,
  fileScanResult : FileScanResult
) extends FileUploadException

sealed trait FileScanResult {
  val message: Option[String]
}

case class FileScanAcceptResult(
  message    : Option[String],
  mediatype  : Option[String]              = None,
  filename   : Option[String]              = None,
  content    : Option[java.io.InputStream] = None,
  extension  : Option[Map[String, Any]]    = None
) extends FileScanResult

case class FileScanRejectResult(
  message    : Option[String]
) extends FileScanResult

case class FileScanErrorResult(
  message   : Option[String],
  throwable : Option[Throwable]
) extends FileScanResult

trait MultipartLifecycle {
  def fieldReceived(fieldName: String, value: String)         : Unit
  def fileItemStarting(fieldName: String, fileItem: FileItem) : Option[MaximumSize]
  def updateProgress(b: Array[Byte], off: Int, len: Int)      : Option[FileScanResult]
  def fileItemState(state: UploadState[DiskFileItem])         : Option[FileScanResult]
  def interrupted()                                           : Unit
}

object Multipart {

  import Private._

  type UploadItem = String Either FileItem

  // Initially we set this to `MultipartStream.DEFAULT_BUFSIZE`.
  // But one user had an issue with a very large header causing errors. So we are making this larger now.
  // https://github.com/orbeon/orbeon-forms/issues/4579
  val DefaultBufferSize = 3 * 4096

  // Return fully successful requests only
  def getParameterMapMultipartJava(
    pipelineContext : PipelineContext,
    request         : Request,
    headerEncoding  : String
  ): ju.Map[String, Array[AnyRef]] = {

    val uploadContext = new UploadContext {
      val getContentType       = request.getContentType
      val getCharacterEncoding = request.getCharacterEncoding
      val getInputStream       = request.getInputStream

      val contentLength        = request.contentLengthOpt getOrElse -1L
      def getContentLength     = if (contentLength > Int.MaxValue) -1 else contentLength.toInt // this won't be used anyway
    }

    val maxSize  = MaximumSize.unapply(RequestGenerator.getMaxSizeProperty.toString) getOrElse LimitedSize(0)
    val maxFiles = Some(RequestGenerator.getMaxFilesProperty.toLong).filter(_ >= 0)

    // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
    parseMultipartRequest(uploadContext, None, maxSize, maxFiles, headerEncoding, RequestGenerator.getMaxMemorySizeProperty) match {
      case (nameValuesFileScan, None) =>

        // Add a listener to destroy file items when the pipeline context is destroyed
        pipelineContext.addContextListener((_: Boolean) => quietlyDeleteFileItems(nameValuesFileScan))

        val foldedValues =
          nameValuesFileScan map { case (k, v, _) => (k,  v.fold(identity, identity)) }

        combineValues[String, AnyRef, Array](foldedValues).toMap.asJava
      case (nameValuesFileScan, Some(t)) =>
        quietlyDeleteFileItems(nameValuesFileScan)
        throw t
    }
  }

  // Decode a multipart/form-data request and return all the successful parameters.
  // This function returns as many name values pairs as possible. If a failure occurs midway, the values collected
  // until that point are returned. Only completely read values are returned. The caller can know that a failure
  // occurred by looking at the Throwable returned. If the caller wants to discard the partial request, it is the
  // responsibility of the caller to discard returned FileItem if the caller doesn't want to process the partial
  // results further.
  def parseMultipartRequest(
    uploadContext  : UploadContext,
    lifecycleOpt   : Option[MultipartLifecycle],
    maxSize        : MaximumSize,
    maxFiles       : Option[Long],
    headerEncoding : String,
    maxMemorySize  : Int
  ): (List[(String, UploadItem, Option[FileScanAcceptResult])], Option[Throwable]) = {

    require(uploadContext ne null)
    require(headerEncoding ne null)

    val servletFileUpload = new ServletFileUpload(new DiskFileItemFactory(maxMemorySize, NetUtils.getTemporaryDirectory))

    servletFileUpload.setHeaderEncoding(headerEncoding)

    // `MultipartStream` buffer size is 4096, and it will always attempt to read that much. So even if we are just
    // reading the first headers, which are smaller than that, but have say a limit of 2000, an exception will be
    // thrown. Because we want to be able to read the first headers for `$uuid` and the next item, we adjust the limit
    // to the size of the buffer. This means we can read those headers without an exception due to the limit.
    val adjustedMaxSize = adjustMaxSize(MaximumSize.convertToLong(maxSize))

    servletFileUpload.setSizeMax(adjustedMaxSize)
    servletFileUpload.setFileSizeMax(adjustedMaxSize)
    servletFileUpload.setFileCountMax(maxFiles.getOrElse(-1))

    // Parse the request and add file information
    useAndClose(uploadContext.getInputStream) { _ =>
      // This contains all completed values up to the point of failure if any
      val result = m.ListBuffer[(String, UploadItem, Option[FileScanAcceptResult])]()
      try {
        // `getItemIterator` can throw a `SizeLimitExceededException` in particular
        val itemIterator = FileItemSupport.asScalaIterator(servletFileUpload.getItemIterator(uploadContext))
        for (fis <- itemIterator)
          result += processSingleStreamItem(servletFileUpload, fis, lifecycleOpt)

        (result.toList, None)
      } catch {
        case NonFatal(t) =>
          lifecycleOpt foreach (_.interrupted())
          // Return all completed values up to the point of failure alongside the `Throwable`
          (result.toList, Some(t))
      }
    }
  }

  def throwSizeLimitExceeded(maxSize: Long, currentSize: Long): Unit =
    throw new SizeLimitExceededException(
      f"the request was rejected because its size ($currentSize%d) exceeds the configured maximum ($maxSize%d)",
      currentSize,
      maxSize
    )

  // Delete all items which are of type `FileItem`
  def quietlyDeleteFileItems[T, U](nameValues: List[(T, UploadItem, U)]): Unit =
    nameValues collect {
      case (_, Right(fileItem), _) => fileItem
    } foreach {
      fileItem =>
        FileItemSupport.deleteFileItem(fileItem, None)
    }

  def rejectEmptyFiles: Boolean =
    properties.getBoolean("oxf.fr.upload.reject-empty-files", default = true)

  private object Private {

    val StandardParameterEncoding = CharsetNames.Utf8

    def adjustMaxSize(maxSize: Long): Long =
      if (maxSize < 0L) -1L else maxSize max DefaultBufferSize

    def processSingleStreamItem(
      servletFileUpload : ServletFileUpload,
      fis               : FileItemStream,
      lifecycleOpt      : Option[MultipartLifecycle]
    ): (String, UploadItem, Option[FileScanAcceptResult]) = {

      val fieldName = fis.getFieldName

      if (fis.isFormField) {
        // Simple form field
        // Assume that form fields are in UTF-8. Can they have another encoding? If so, how is it specified?

        // A value could be very large, especially if there is a malicious request. However, this will be caught
        // by the limiter on the incoming outer input stream.
        val value = Streams.asString(fis.openStream, StandardParameterEncoding)
        lifecycleOpt foreach (_.fieldReceived(fieldName, value))
        (fieldName, Left(value), None)
      } else {

        var fileScanCompleteCalled = false

        try {

          val fileItem = servletFileUpload.getFileItemFactory.createItem(fieldName, fis.getContentType, false, fis.getName).asInstanceOf[DiskFileItem]

          var fileItemSize = 0L

          try {

            // Browsers (at least Chrome and Firefox) don't seem to want to put a `Content-Length` per part :(
            for {
              fisHeaders     <- Option(fis.getHeaders) // `getHeaders` can be null
              headersSupport <- fileItem.cast[FileItemHeadersSupport]
            } locally {
              headersSupport.setHeaders(fisHeaders)
            }

            val maxSizeForSpecificFileItemOpt =
              lifecycleOpt flatMap
                (_.fileItemStarting(fieldName, fileItem)) // can throw `FileScanException`, `IllegalStateException`, `DisallowedMediatypeException`

            copyStreamAndClose(
              in  = maxSizeForSpecificFileItemOpt map (
                new LimiterInputStream(
                  fis.openStream,
                  _,
                  throwSizeLimitExceeded
                )
              ) getOrElse
                fis.openStream,
              out = new OutputStream {

                private val fios = fileItem.getOutputStream

                // We know that this is not called by `copyStream`
                def write(b: Int): Unit = throw new IllegalStateException

                // We know that this is the only `write` method called by `copyStream`
                override def write(b: Array[Byte], off: Int, len: Int): Unit =
                  lifecycleOpt flatMap (_.updateProgress(b, off, len)) match {
                    case None | Some(_: FileScanAcceptResult) => fios.write(b, off, len)
                    case Some(r)                              => throw FileScanException(fieldName, r) // to the `catch` at the bottom
                  }

                override def flush(): Unit = fios.flush()
                override def close(): Unit = fios.close()
              },
              progress = fileItemSize += _
            )
          } catch {
            // Clean-up `FileItem` right away in case of failure
            case NonFatal(t) =>
              FileItemSupport.deleteFileItem(fileItem, None)
              throw t
          }

          if (lifecycleOpt.isDefined && fileItemSize == 0L && rejectEmptyFiles) {
            throw EmptyFileException()
          }

          lifecycleOpt flatMap (_.fileItemState(UploadState.Completed(fileItem))) match {
            case None                          =>
              // Means there was no file scan
              (fieldName, Right(fileItem), None)
            case Some(r: FileScanAcceptResult) =>
              fileScanCompleteCalled = true
              (fieldName, Right(fileItem), Some(r))
            case Some(r)                       =>
              // File scan with `FileScanErrorResult` or `FileScanRejectResult`
              fileScanCompleteCalled = true
              throw FileScanException(fieldName, r) // go to the `catch` below
          }
        } catch {
          case NonFatal(t) =>
            // Make sure we call `abort()` on the file scan provider but not if we have already called `complete()`
            if (! fileScanCompleteCalled)
              lifecycleOpt foreach (_.fileItemState( // returns `None` anyway for `Interrupted` so we ignore the result
                UploadState.Interrupted(
                 Option(Exceptions.getRootThrowable(t))
                 collect {
                   case _: EmptyFileException                                     => FileRejectionReason.EmptyFile
                   case root: SizeLimitExceededException                          => FileRejectionReason.SizeTooLarge(root.getPermittedSize, root.getActualSize)
                   case DisallowedMediatypeException(filename, permitted, actual) => FileRejectionReason.DisallowedMediatype(filename, permitted, actual)
                   case FileScanException(fieldName, fileScanResult)              => FileRejectionReason.FailedFileScan(fieldName, fileScanResult.message)
                 }
                )
              ))
            throw t
        } // catch
      } // ! fis.isFormField
    } // processSingleStreamItem
  }
}
