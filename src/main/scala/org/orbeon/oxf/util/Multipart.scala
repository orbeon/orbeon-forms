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

import java.io.OutputStream
import java.{util => ju}

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
import shapeless.syntax.typeable._

import scala.collection.JavaConverters._
import scala.collection.{mutable => m}
import scala.util.control.NonFatal

case class DisallowedMediatypeException(permitted: Set[MediatypeRange], actual: Option[Mediatype]) extends FileUploadException
case class FileScanException           (message: String)                                           extends FileUploadException

sealed trait Reason
object Reason {
  case class SizeReason     (permitted: Long,                actual: Long)              extends Reason
  case class MediatypeReason(permitted: Set[MediatypeRange], actual: Option[Mediatype]) extends Reason
  case class FileScanReason (message: String)                                           extends Reason
}

sealed trait UploadState { def name: String }
object UploadState {
  case object Started                             extends UploadState { val name = "started" }
  case class  Completed(fileItem: DiskFileItem)   extends UploadState { val name = "completed" }
  case class  Interrupted(reason: Option[Reason]) extends UploadState { val name = "interrupted" }
}

// NOTE: Fields don't need to be @volatile as they are accessed via the session, which provides synchronized access.
case class UploadProgress(
  fieldName        : String,
  expectedSize     : Option[Long],
  var receivedSize : Long        = 0L,
  var state        : UploadState = UploadState.Started
)

trait MultipartLifecycle {
  def fieldReceived(fieldName: String, value: String)         : Unit
  def fileItemStarting(fieldName: String, fileItem: FileItem) : Option[MaximumSize]
  def updateProgress(b: Array[Byte], off: Int, len: Int)      : Unit
  def fileItemState(state: UploadState)                       : Unit
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

    val maxSize = MaximumSize.unapply(RequestGenerator.getMaxSizeProperty.toString) getOrElse LimitedSize(0)

    // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
    parseMultipartRequest(uploadContext, None, maxSize, headerEncoding, RequestGenerator.getMaxMemorySizeProperty) match {
      case (nameValues, None) =>

        // Add a listener to destroy file items when the pipeline context is destroyed
        pipelineContext.addContextListener(new PipelineContext.ContextListener {
          def contextDestroyed(success: Boolean) = quietlyDeleteFileItems(nameValues)
        })

        val foldedValues =
          nameValues map { case (k, v) => k -> v.fold(identity, identity) }

        combineValues[String, AnyRef, Array](foldedValues).toMap.asJava
      case (nameValues, Some(t)) =>
        quietlyDeleteFileItems(nameValues)
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
    headerEncoding : String,
    maxMemorySize  : Int
  ): (List[(String, UploadItem)], Option[Throwable]) = {

    require(uploadContext ne null)
    require(headerEncoding ne null)

    val servletFileUpload = new ServletFileUpload(new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory))

    servletFileUpload.setHeaderEncoding(headerEncoding)

    // `MultipartStream` buffer size is 4096, and it will always attempt to read that much. So even if we are just
    // reading the first headers, which are smaller than that, but have say a limit of 2000, an exception will be
    // thrown. Because we want to be able to read the first headers for `$uuid` and the next item, we adjust the limit
    // to the size of the buffer. This means we can read those headers without an exception due to the limit.
    val adjustedMaxSize = adjustMaxSize(MaximumSize.convertToLong(maxSize))

    servletFileUpload.setSizeMax(adjustedMaxSize)
    servletFileUpload.setFileSizeMax(adjustedMaxSize)

    // Parse the request and add file information
    useAndClose(uploadContext.getInputStream) { _ =>
      // This contains all completed values up to the point of failure if any
      val result = m.ListBuffer[(String, UploadItem)]()
      try {
        // `getItemIterator` can throw a `SizeLimitExceededException` in particular
        val itemIterator = asScalaIterator(servletFileUpload.getItemIterator(uploadContext))
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
  def quietlyDeleteFileItems(nameValues: List[(String, UploadItem)]): Unit =
    nameValues collect {
      case (_, Right(fileItem)) => fileItem
    } foreach {
      fileItem => runQuietly(fileItem.delete())
    }

  private object Private {

    val StandardParameterEncoding = CharsetNames.Utf8

    def adjustMaxSize(maxSize: Long): Long =
      if (maxSize < 0L) -1L else maxSize max DefaultBufferSize

    def processSingleStreamItem(
      servletFileUpload : ServletFileUpload,
      fis               : FileItemStream,
      lifecycleOpt      : Option[MultipartLifecycle]
    ): (String, UploadItem) = {

      val fieldName = fis.getFieldName

      if (fis.isFormField) {
        // Simple form field
        // Assume that form fields are in UTF-8. Can they have another encoding? If so, how is it specified?

        // A value could be very large, especially if there is a malicious request. However, this will be caught
        // by the limiter on the incoming outer input stream.
        val value = Streams.asString(fis.openStream, StandardParameterEncoding)
        lifecycleOpt foreach (_.fieldReceived(fieldName, value))
        fieldName -> Left(value)
      } else {

        try {

          val fileItem = servletFileUpload.getFileItemFactory.createItem(fieldName, fis.getContentType, false, fis.getName).asInstanceOf[DiskFileItem]

          try {

            // Browsers (at least Chrome and Firefox) don't seem to want to put a `Content-Length` per part :(
            for {
              fisHeaders     <- Option(fis.getHeaders) // `getHeaders` can be null
              headersSupport <- fileItem.cast[FileItemHeadersSupport]
            } locally {
              headersSupport.setHeaders(fisHeaders)
            }

            val maxSizeForSpecificFileItemOpt =
              lifecycleOpt flatMap (_.fileItemStarting(fieldName, fileItem)) // can throw `FileScanException`

            copyStream(
              in  = maxSizeForSpecificFileItemOpt map (
                new LimiterInputStream(
                  fis.openStream,
                  _,
                  throwSizeLimitExceeded
                )
              ) getOrElse
                fis.openStream,
              out = new OutputStream {

                val fios = fileItem.getOutputStream

                // We know that this is not called by `copyStream`
                def write(b: Int) = throw new IllegalStateException

                // We know that this is the only `write` method called by `copyStream`
                override def write(b: Array[Byte], off: Int, len: Int) = {
                  lifecycleOpt foreach (_.updateProgress(b, off, len)) // can throw `FileScanException`
                  fios.write(b, off, len)
                }

                override def flush() = fios.flush()
                override def close() = fios.close()
              }
            )
          } catch {
            // Clean-up `FileItem` right away in case of failure
            case NonFatal(t) =>
              runQuietly(fileItem.delete())
              throw t
          }

          lifecycleOpt foreach (_.fileItemState(UploadState.Completed(fileItem))) // can throw `FileScanException`
          fieldName -> Right(fileItem)
        } catch {
          case NonFatal(t) =>
            lifecycleOpt foreach (_.fileItemState(
              UploadState.Interrupted(
               Option(Exceptions.getRootThrowable(t))
               collect {
                 case root: SizeLimitExceededException                => Reason.SizeReason(root.getPermittedSize, root.getActualSize)
                 case DisallowedMediatypeException(permitted, actual) => Reason.MediatypeReason(permitted, actual)
                 case FileScanException(message)                      => Reason.FileScanReason(message)
               }
              )
            ))
            throw t
        }
      }
    }

    def asScalaIterator(i: FileItemIterator) = new Iterator[FileItemStream] {
      def hasNext = i.hasNext
      def next()  = i.next()
      override def toString = "Iterator wrapping FileItemIterator" // super.toString is dangerous when running in a debugger
    }
  }
}
