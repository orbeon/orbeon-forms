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
import java.util.{Map => JMap, HashMap => JHashMap}
import ScalaUtils._

import scala.collection.JavaConversions._
import org.orbeon.oxf.pipeline.api.ExternalContext.Session
import util.Streams
import org.orbeon.oxf.xforms.control.XFormsValueControl
/**
 * Multipart decoding with support for progress indicator.
 */
object Multipart {

    private val UPLOAD_PROGRESS_SESSION_KEY = "orbeon.upload.progress."
    private val STANDARD_PARAMETER_ENCODING = "utf-8"

    /**
     * Decode a multipart/form-data stream and return a Map of parameters of type Object[], each of
     * which can be a String or FileItem.
     */
    def getParameterMapMultipart(pipelineContext: PipelineContext, request: ExternalContext.Request, headerEncoding: String): JMap[String, Array[AnyRef]] = {

        require(pipelineContext ne null)
        require(request ne null)
        require(headerEncoding ne null)

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
            override def contextDestroyed(success: Boolean) =
                uploadParameterMap.values.flatten foreach
                    { case value: FileItem => runQuietly(value.delete()); case _ => }
        })

        // Parse the request and add file information
        useAndClose(request.getInputStream) { inputStream =>
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

            parseRequest(requestContext, diskFileItemFactory, upload, request, uploadParameterMap)
        }

        uploadParameterMap
    }

    private def getProgressSessionKey(uuid: String, fieldName: String) = UPLOAD_PROGRESS_SESSION_KEY + uuid + "." + fieldName

    def getUploadProgress(request: ExternalContext.Request, sessionKey: String): Option[UploadProgress] =
        request.getSession(false) match {
            case session: Session => session.getAttributesMap.get(sessionKey) match {
                case progress: UploadProgress => Some(progress)
                case _ => None
            }
            case _ => None
        }

    def getUploadProgress(request: ExternalContext.Request, uuid: String, fieldName: String): Option[UploadProgress] =
        getUploadProgress(request, getProgressSessionKey(uuid, fieldName))

    def removeUploadProgress(request: ExternalContext.Request, control: XFormsValueControl): Unit =
        request.getSession(false) match {
            case session: Session => session.getAttributesMap.remove(getProgressSessionKey(control.getContainingDocument.getUUID, control.getEffectiveId))
            case _ =>
        }

    object UploadState extends Enumeration {
        val Started = Value
        val Completed = Value
        val Interrupted = Value
    }

    class UploadProgress(val fieldName: String, val expectedSize: Option[Long]) {
        var receivedSize = 0L
        var state = UploadState.Started
    }

    private def parseRequest(requestContext: RequestContext, factory: DiskFileItemFactory, upload: ServletFileUpload,
                             request: ExternalContext.Request, uploadParameterMap: JMap[String, Array[AnyRef]]): Unit = {

        require(requestContext ne null)
        require(factory ne null)
        require(upload ne null)
        require(request ne null)
        require(uploadParameterMap ne null)

        // Don't create session if missing
        val session = request.getSession(false)

        var sessionKeys: List[String] = Nil

        val items = collection.mutable.Seq[FileItem]()
        var successful = false

        try {
            var progressUUID: String = null

            for (item <- upload.getItemIterator(requestContext)) {
                val fileItem = factory.createItem(item.getFieldName, item.getContentType, item.isFormField, item.getName)

                // Make sure openStream is opened otherwise other methods fail later due to NPE
                val inputStream = item.openStream

                fileItem match {
                    case headersSupport: FileItemHeadersSupport => headersSupport.setHeaders(item.getHeaders)
                }

                items :+ fileItem

                val fieldValue = // either a String or a FileItem (we don't use Either as this is used from Java)
                    if (fileItem.isFormField) {
                        // Simple form field
                        // Assume that form fields are in UTF-8. Can they have another encoding? If so, how is it specified?
                        val formFieldValue = Streams.asString(inputStream, STANDARD_PARAMETER_ENCODING)
                        if ((progressUUID eq null) && (session ne null) && item.getFieldName == "$uuid") {
                            // NOTE: The test on $uuid is XForms-engine specific
                            progressUUID = formFieldValue
                        }

                        formFieldValue
                    } else if (progressUUID ne null) {
                        // File upload with progress notification

                        // Get expected size first from part then from request
                        val expectedLength = item.getHeaders match {
                            case headers: FileItemHeaders if headers.getHeader("content-length") ne null =>
                                Some(headers.getHeader("content-length").toLong)
                            case _ =>
                                request.getHeaderValuesMap.get("content-length") match {
                                    case Array(requestLength: String) => Some(requestLength.toLong)
                                    case _ => None
                                }
                        }

                        // Store into session with start value
                        val newSessionKey = getProgressSessionKey(progressUUID, item.getFieldName)
                        sessionKeys = newSessionKey :: sessionKeys

                        val uploadProgress = new UploadProgress(item.getFieldName, expectedLength)
                        session.getAttributesMap.put(newSessionKey, uploadProgress)

                        // Copy stream and update progress information
                        copyStream(inputStream, fileItem.getOutputStream, uploadProgress.receivedSize += _)
                        uploadProgress.state = UploadState.Completed

                        fileItem
                    } else {
                        // File upload without progress notification -> just copy the stream
                        copyStream(item.openStream, fileItem.getOutputStream)

                        fileItem
                    }

                StringConversions.addValueToObjectArrayMap(uploadParameterMap, fileItem.getFieldName, fieldValue)
            }
            successful = true
        } finally {
            if (!successful) {
                // In case of exception:
                // o don't remove UploadProgress objects from the session
                // o instead mark all entries added so far as being in state Interrupted
                // o free underlying FileItem storage if any as those won't be used
                for (sessionKey <- sessionKeys)
                    runQuietly {
                        getUploadProgress(request, sessionKey) match {
                            case Some(progress) => progress.state = UploadState.Interrupted
                            case _ =>
                        }
                    }

                // Free underlying storage for all (disk) items gathered so far
                for (fileItem <- items)
                    runQuietly(fileItem.delete())
            }
        }
    }

    // Implicit conversion from FileItemIterator -> Iterator
    private implicit def asScalaIterator(i : FileItemIterator): Iterator[FileItemStream] = new Iterator[FileItemStream] {
        def hasNext = i.hasNext
        def next = i.next
    }
}
