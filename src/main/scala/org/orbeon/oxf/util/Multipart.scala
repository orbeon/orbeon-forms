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

import org.apache.commons.fileupload._
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import java.util.{Map ⇒ JMap}
import ScalaUtils._

import collection.JavaConverters._
import org.orbeon.oxf.pipeline.api.ExternalContext._
import org.orbeon.oxf.xforms.control.XFormsValueControl
import collection.{immutable ⇒ i, mutable ⇒ m}
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException
import org.orbeon.errorified.Exceptions
import scala.util.control.NonFatal


/**
 * Multipart decoding with support for progress indicator.
 */
object Multipart {

    private val UPLOAD_PROGRESS_SESSION_KEY = "orbeon.upload.progress."
    private val STANDARD_PARAMETER_ENCODING = "utf-8"

    // For Java callers
    // Return fully successful requests only
    def jGetParameterMapMultipart(pipelineContext: PipelineContext, request: Request, headerEncoding: String): JMap[String, Array[AnyRef]] =
        parseMultipartRequest(request, RequestGenerator.getMaxSizeProperty, headerEncoding) match {
            case (nameValues, None) ⇒

                // Add a listener to destroy file items when the pipeline context is destroyed
                pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter {
                    override def contextDestroyed(success: Boolean) = deleteFileItems(nameValues)
                })

                combineValues[String, AnyRef, Array](nameValues).toMap.asJava
            case (nameValues, Some(t)) ⇒
                deleteFileItems(nameValues)
                throw t
        }

    // Delete all items which are of type FileItem
    def deleteFileItems(nameValues: i.Seq[(String, AnyRef)]) = (
        nameValues
        collect { case (_, fileItem: FileItem) ⇒ fileItem }
        foreach (fileItem ⇒ runQuietly(fileItem.delete()))
    )

    // MultipartStream buffer size is 4096, and it will always attempt to read that much. So even if we are just
    // reading the first headers, which are smaller than that, but have say a limit of 2000, an exception will be
    // thrown. Because we want to be able to read the first headers for $uuid and the next item, we adjust the limit
    // to the size of the buffer. This means we can read those headers without an exception due to the limit.
    def adjustMaxSize(maxSize: Long) =
        if (maxSize < 0) -1L else Math.max(maxSize, 4096) // MultipartStream.DEFAULT_BUFSIZE

    // Decode a multipart/form-data request and return all the successful parameters.
    def parseMultipartRequest(request: Request, maxSize: Long, headerEncoding: String): (i.Seq[(String, AnyRef)], Option[Throwable]) = {

        require(request ne null)
        require(headerEncoding ne null)

        // Read properties
        // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
        val maxMemorySize = RequestGenerator.getMaxMemorySizeProperty

        val upload = new ServletFileUpload(new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory))

        upload.setHeaderEncoding(headerEncoding)

        // NOTE: FileUploadBase handles sizeMax and fileSizeMax differently:
        //
        // - sizeMax: trusts Content-Length (which it shouldn't), and doesn't wrap in LimitedInputStream if specified!
        // - fileSizeMax: checks Content-Length, but wraps LimitedInputStream in all cases (which is safer)
        //
        // It is arguably a bug in FileUploadBase to handle sizeMax this way.
        //
        // We have getContentLength return -1 so that:
        //
        // - even if the request is too large, we still try to read $uuid as first item
        // - FileUploadBase wraps with LimitedInputStream
        //
        val adjustedMaxSize = adjustMaxSize(maxSize)

        upload.setSizeMax(adjustedMaxSize)
        upload.setFileSizeMax(adjustedMaxSize)

        // Parse the request and add file information
        useAndClose(request.getInputStream) { inputStream ⇒

            val requestContext = new RequestContext {
                def getContentType       = request.getContentType
                def getContentLength     = request.getContentLength
                def getCharacterEncoding = request.getCharacterEncoding
                def getInputStream       = inputStream
            }

            parseRequest(requestContext, upload, Option(request.getSession(false))) // don't create session if missing
        }
    }

    private def getProgressSessionKey(uuid: String, fieldName: String) =
        UPLOAD_PROGRESS_SESSION_KEY + uuid + "." + fieldName

    def getUploadProgress(session: Option[Session], sessionKey: String): Option[UploadProgress] =
        session flatMap (s ⇒ Option(s.getAttributesMap.get(sessionKey))) collect {
            case progress: UploadProgress ⇒ progress
        }

    def getUploadProgress(request: Request, uuid: String, fieldName: String): Option[UploadProgress] =
        getUploadProgress(Option(request.getSession(false)), getProgressSessionKey(uuid, fieldName))

    def removeUploadProgress(request: Request, control: XFormsValueControl): Unit =
        Option(request.getSession(false)) foreach {
            _.getAttributesMap.remove(getProgressSessionKey(control.containingDocument.getUUID, control.getEffectiveId))
        }

    sealed trait Reason
    case class SizeReason(permitted: Long, actual: Long) extends Reason

    sealed trait UploadState { def name: String }
    case object Started                             extends UploadState { val name = "started" }
    case object Completed                           extends UploadState { val name = "completed" }
    case class  Interrupted(reason: Option[Reason]) extends UploadState { val name = "interrupted" }

    // NOTE: Fields don't need to be @volatile as they are accessed via the session, which provides synchronized access.
    case class UploadProgress(fieldName: String, expectedSize: Option[Long], var receivedSize: Long = 0L, var state: UploadState = Started)

    // Parse a multipart request
    //
    // This function returns as many name values pairs as possible. If a failure occurs midway, the values collected
    // until that point are returned. Only completely read values are returned. The caller can know that a failure
    // occurred by looking at the Throwable returned. If the caller wants to discard the partial request, it is the
    // responsibility of the caller to discard returned FileItem if the caller doesn't want to process the partial
    // results further.
    private def parseRequest(
            requestContext: RequestContext,
            upload: ServletFileUpload,
            sessionOpt: Option[Session]): (i.Seq[(String, AnyRef)], Option[Throwable]) = {

        val requestUntrustedSize = requestContext.getContentLength

        val trustedRequestContext = new RequestContext {
            def getCharacterEncoding = requestContext.getCharacterEncoding
            def getContentType       = requestContext.getContentType
            def getContentLength     = -1 // so that we fail lazily AND so that we get FileUploadBase to wrap with LimitedInputStream
            def getInputStream       = requestContext.getInputStream
        }

        // Session keys created, for cleanup
        val sessionKeys = m.ListBuffer[String]()

        // This contains all completed values up to the point of failure if any
        val result = m.ListBuffer[(String, AnyRef)]()

        try {

            // Create new progress indicator
            def newUploadProgress(progressUUID: String, fis: FileItemStream) = {
                // Browsers don't seem to want to put a Content-Length per part, how dumb
                def expectedSizeFromPart =
                    for {
                        headers ← Option(fis.getHeaders)
                        header  ← Option(headers.getHeader(Headers.ContentLengthLower))
                    } yield
                        header.toLong

                def expectedSizeFromRequest =
                    requestUntrustedSize > 0 option requestUntrustedSize.toLong

                // Try size first from part then from request
                val expectedLength = expectedSizeFromPart orElse expectedSizeFromRequest

                val progress = UploadProgress(fis.getFieldName, expectedLength)

                // Store into session with start value
                val newSessionKey = getProgressSessionKey(progressUUID, fis.getFieldName)
                sessionKeys += newSessionKey
                sessionOpt.get.getAttributesMap.put(newSessionKey, progress)

                progress
            }

            // Process a single item
            def processStreamItem(fis: FileItemStream, uploadProgressOpt: Option[UploadProgress], checkSize: () ⇒ Unit = () ⇒ ()): Unit = {
                val fieldName = fis.getFieldName

                if (fis.isFormField) {
                    // Simple form field
                    // Assume that form fields are in UTF-8. Can they have another encoding? If so, how is it specified?

                    checkSize()

                    result += fieldName → Streams.asString(fis.openStream, STANDARD_PARAMETER_ENCODING)
                } else {

                    def createFileItem = {
                        val fileItem = upload.getFileItemFactory.createItem(fieldName, fis.getContentType, false, fis.getName)
                        collectByErasedType[FileItemHeadersSupport](fileItem) foreach (_.setHeaders(fis.getHeaders))
                        fileItem
                    }

                    def copyAndAdd(progress: Long ⇒ Unit): Unit = {
                        val fileItem = createFileItem
                        try {
                            // Attempt copy
                            copyStream(fis.openStream, fileItem.getOutputStream, progress)
                            // Accumulate in any case if copy was successful
                            result += fileItem.getFieldName → fileItem
                        } catch {
                            // Clean-up FileItem right away in case of failure
                            case NonFatal(t) ⇒
                                runQuietly(fileItem.delete())
                                throw t
                        }
                    }

                    uploadProgressOpt match {
                        case Some(uploadProgress) ⇒
                            // File upload with progress notification
                            try {
                                checkSize()
                                copyAndAdd(uploadProgress.receivedSize += _)
                                uploadProgress.state = Completed
                            } catch {
                                case NonFatal(t) ⇒
                                    uploadProgress.state = Interrupted(
                                       Option(Exceptions.getRootThrowable(t))
                                       collect { case root: SizeLimitExceededException ⇒ SizeReason(root.getPermittedSize, root.getActualSize) }
                                    )
                                    throw t
                            }

                        case None ⇒
                            // File upload without progress notification → just copy the stream
                            copyAndAdd(_ ⇒ ())
                    }
                }
            }

            def checkTotalSize() =
                if (upload.getSizeMax >=0 && requestUntrustedSize > upload.getSizeMax)
                    throw new SizeLimitExceededException(
                        f"the request was rejected because its size ($requestUntrustedSize%d) exceeds the configured maximum (${upload.getSizeMax}%d)",
                        requestUntrustedSize,
                        upload.getSizeMax)

            val itemIterator = asScalaIterator(upload.getItemIterator(trustedRequestContext))

            sessionOpt match {
                case Some(session) ⇒
                    // Processing with session and progress

                    // Process the first item specially to catch $uuid (XForms-engine specific)
                    val progressUUIDOpt =
                        itemIterator.nextOption() flatMap { fis ⇒
                            processStreamItem(fis, None)
                            result.headOption collect {
                                case ("$uuid", value: String) ⇒ value
                            }
                        }

                    // No need delaying checking the size further if we don't have a uuid
                    if (progressUUIDOpt.isEmpty)
                        checkTotalSize()

                    // Process the rest of the stream
                    var uploadProgressOpt: Option[UploadProgress] = None
                    for (fis ← itemIterator) {

                        // Try to create the progress indicator if not present
                        if (uploadProgressOpt.isEmpty)
                            uploadProgressOpt = progressUUIDOpt map (newUploadProgress(_, fis))

                        processStreamItem(fis, uploadProgressOpt, () ⇒ checkTotalSize())
                    }
                case None ⇒
                    // Simplified processing
                    checkTotalSize()
                    for (fis ← itemIterator)
                        processStreamItem(fis, None)
            }

            (result.toList, None)
        } catch {
            case NonFatal(t) ⇒
                // - don't remove UploadProgress objects from the session
                // - instead mark all entries added so far as being in state Interrupted if not already the case
                // - return all completed values up to the point of failure alongside the throwable
                for (sessionKey ← sessionKeys)
                    runQuietly (
                        getUploadProgress(sessionOpt, sessionKey)
                        collect { case p @ UploadProgress(_, _, _, Started | Completed ) ⇒ p }
                        foreach (_.state = Interrupted(None))
                    )

                (result.toList, Some(t))
        }
    }

    private def asScalaIterator(i: FileItemIterator) = new Iterator[FileItemStream] {
        def hasNext = i.hasNext
        def next()  = i.next()
        override def toString = "Iterator wrapping FileItemIterator" // super.toString is dangerous when running in a debugger
    }
}
