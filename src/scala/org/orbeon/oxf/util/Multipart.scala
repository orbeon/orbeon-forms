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
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.RequestGenerator
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.{Map => JMap, HashMap => JHashMap}

import scala.collection.JavaConversions._
import org.orbeon.oxf.pipeline.api.ExternalContext.Session
import util.Streams

object Multipart
{
    private val UPLOAD_PROGRESS_SESSION_KEY: String = "orbeon.upload.progress."
    private val STANDARD_PARAMETER_ENCODING: String = "utf-8"
    private val COPY_BUFFER_SIZE = 8192

    /**
     * Utility method to decode a multipart/form-data stream and return a Map of parameters of type Object[], each of
     * which can be a String or FileData.
     */
    def getParameterMapMultipart(pipelineContext: PipelineContext, request: ExternalContext.Request, headerEncoding: String): JMap[String, Array[AnyRef]] = {
        val uploadParameterMap = new JHashMap[String, Array[AnyRef]]

        // Read properties
        // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
        val maxSize = RequestGenerator.getMaxSizeProperty
        val maxMemorySize = RequestGenerator.getMaxMemorySizeProperty

        val diskFileItemFactory = new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory)
        val upload = new ServletFileUpload(diskFileItemFactory)

        upload.setHeaderEncoding(headerEncoding)
        upload.setSizeMax(maxSize)

        // Add a listener to destroy file items when the pipeline context is destroyed
        pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter {
            override def contextDestroyed(success: Boolean) {
                for (name <- uploadParameterMap.keySet) {
                    for (currentValue <- uploadParameterMap.get(name))
                        if (currentValue.isInstanceOf[FileItem])
                            currentValue.asInstanceOf[FileItem].delete()
                }
            }
        })

        // Implement the required methods for the upload code
        val inputStream = request.getInputStream

        val requestContext = new RequestContext {
            def getContentType = request.getContentType
            def getContentLength = request.getContentLength
            def getCharacterEncoding = request.getCharacterEncoding

            // Q: Is this note still up to date?
            // NOTE: The upload code does not actually check that it doesn't read more than the content-length
            // sent by the client! Maybe here would be a good place to put an interceptor and make sure we
            // don't read too much.
            def getInputStream = inputStream
        }

        // Parse the request and add file information
        try {
            parseRequest(requestContext, diskFileItemFactory, upload, request, uploadParameterMap)
        } finally {
            if (inputStream ne null)
                try {
                    inputStream.close()
                } catch {
                    case _: IOException => // NOP
                }
        }

        uploadParameterMap
    }

    def getUploadProgressJava(request: ExternalContext.Request, uuid: String) =
        getUploadProgress(request, uuid) match {
            case Some(progress) => progress
            case _ => null
        }

    def getUploadProgress(request: ExternalContext.Request, uuid: String): Option[UploadProgress] =
        request.getSession(false) match {
            case session: Session => session.getAttributesMap.get(UPLOAD_PROGRESS_SESSION_KEY + uuid) match {
                case progress: UploadProgress => Some(progress)
                case _ => None
            }
            case _ => None
        }

    def removeUploadProgress(request: ExternalContext.Request, uuid: String): Unit =
        request.getSession(false) match {
            case session: Session => session.getAttributesMap.remove(UPLOAD_PROGRESS_SESSION_KEY + uuid)
            case _ =>
        }

    class UploadProgress(val fieldName: String, val expectedSize: Option[Long]) {
        var receivedSize = 0L
        var completed = false
    }

    private def parseRequest(requestContext: RequestContext, factory: DiskFileItemFactory, upload: ServletFileUpload,
                             request: ExternalContext.Request, uploadParameterMap: JMap[String, Array[AnyRef]]): Unit = {

        require(requestContext ne null)
        require(factory ne null)
        require(upload ne null)

        // Don't create session if missing
        val session = request.getSession(false)

        var progressUUID: String = null

        val items = collection.mutable.Seq[FileItem]()
        var successful = false

        try {
            for (item <- upload.getItemIterator(requestContext)) {
                val fileItem = factory.createItem(item.getFieldName, item.getContentType, item.isFormField, item.getName)

                items :+ fileItem

                // Make sure openStream is opened otherwise other methods fail later due to NPE
                val inputStream = item.openStream

                if (fileItem.isFormField) {
                    // Simple form field
                    // Assume that form fields are in UTF-8. Can they have another encoding? If so, how is it specified?
                    val formFieldValue = Streams.asString(inputStream, STANDARD_PARAMETER_ENCODING)
                    StringConversions.addValueToObjectArrayMap(uploadParameterMap, fileItem.getFieldName, formFieldValue)
                    if ((progressUUID eq null) && (session ne null) && item.getFieldName == "$uuid") {
                        // NOTE: The test on $uuid is XForms-engine specific
                        progressUUID = formFieldValue
                    }
                } else if (progressUUID ne null) {
                    // File upload with progress notification

                    // Get expected size first from part then from request
                    val expectedLength = item.getHeaders match {
                        case headers: FileItemHeaders if headers.getHeader("Content-Length") ne null => Some(headers.getHeader("Content-Length").toLong)
                        case _ => request.getHeaderMap.get("Content-Length") match {
                            case requestLength: String => Some(requestLength.toLong)
                            case _ => None
                        }
                    }

                    // Store into session with start value
                    val uploadStatus = new UploadProgress(item.getFieldName, expectedLength);
                    session.getAttributesMap.put(UPLOAD_PROGRESS_SESSION_KEY + progressUUID, uploadStatus);

                    copyStream(inputStream, fileItem.getOutputStream, true, (read: Long) => {
                        // Update session value
                        uploadStatus.receivedSize += read
                    })

                    uploadStatus.completed = true

                } else {
                    // File upload without progress notification -> just copy the stream
                    copyStream(item.openStream, fileItem.getOutputStream, true)
                }

                if (fileItem.isInstanceOf[FileItemHeadersSupport])
                    (fileItem.asInstanceOf[FileItemHeadersSupport]).setHeaders(item.getHeaders)
            }
            successful = true
        } finally {
            if (!successful) {
                // Remove session value
                if (progressUUID ne null)
                    session.getAttributesMap.remove(UPLOAD_PROGRESS_SESSION_KEY + progressUUID);

                // Free underlying storage for all (disk) items gathered so far
                for (fileItem <- items)
                    try {
                        fileItem.delete()
                    } catch {
                        case _: Throwable => // NOP
                    }
            }
        }
    }

    def copyStream(in: InputStream, out: OutputStream, closeOut: Boolean, progress: (Long) => Unit = null) = {

        require(in ne null)
        require(out ne null)

        var outClosed = false
        var inClosed = false
        try {
            val buffer = new Array[Byte](COPY_BUFFER_SIZE)

            var read = 0
            do {
                read = in.read(buffer)
                if (read > 0) {
                    if (progress ne null)
                        progress(read)

                    out.write(buffer, 0, read)
                }
            } while (read != -1)

            if (closeOut) {
                out.close()
                outClosed = true
            } else
                out.flush()

            in.close()
            inClosed = true
        } finally {
            if (!inClosed)
                try {
                    in.close()
                } catch {
                    case _: Throwable => // NOP
                }
            if (closeOut && !outClosed)
                try {
                    out.close()
                } catch {
                    case _: Throwable => // NOP
                }
        }
    }

    private case class FileItemIteratorWrapper(underlying : FileItemIterator) extends Iterator[FileItemStream] {
        def hasNext = underlying.hasNext
        def next = underlying.next
    }

    // Implicit conversion from FileItemIterator -> Iterator
    private implicit def asScalaIterator(i : FileItemIterator): Iterator[FileItemStream] = FileItemIteratorWrapper(i)
}