/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import org.apache.commons.fileupload.FileItem
import org.apache.commons.fileupload.FileUploadBase.{FileSizeLimitExceededException, SizeLimitExceededException}
import org.orbeon.datatypes.Mediatype
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xml.{EncodeDecode, XMLReceiver}
import org.orbeon.scaxon.NodeConversions

class UploaderProcessor extends ProcessorImpl {
  override def createOutput(name: String) =
    addOutput(
      name,
      new ProcessorOutputImpl(UploaderProcessor.this, name) {
        override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) = {

          UploaderServer.processUpload(NetUtils.getExternalContext.getRequest) match {
            case (nameValues, None) =>

              // NOTE: As of 2013-05-09, the client only uploads one file per request. We are able to
              // handle more than one here.
              val files = nameValues collect {
                case (name, fileItem: FileItem) if fileItem.getName.nonAllBlank =>

                  // Get size before renaming below
                  val size = fileItem.getSize

                  // If there is a `FileScanProvider`, a `File` is obtained from the `DiskFileItem` separately. If by
                  // any chance a new file is created on disk at that time, it will be deleted separately as the request
                  // completes. Here again we would create a new request-expired file, before renaming it and making
                  // sure it expires with the session only.
                  val sessionURL =
                    NetUtils.renameAndExpireWithSession(
                      RequestGenerator.urlForFileItemCreateIfNeeded(fileItem, NetUtils.REQUEST_SCOPE),
                      XFormsServer.logger
                    ).toURI.toString

                  (name, fileItem, sessionURL, size)
              }

              val serverEvents =
                <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">{
                  for {
                    (name, fileItem, sessionURL, size) <- files
                    headers      = Map(Headers.ContentType -> fileItem.getContentType.trimAllToOpt)
                    mediatypeOpt = Mediatypes.fromHeadersOrFilename(n => headers.get(n).flatten, fileItem.getName.trimAllToOpt)
                  } yield
                    <xxf:event
                      name="xxforms-upload-done"
                      source-control-id={name}
                      file={sessionURL}
                      filename={fileItem.getName.trimAllToEmpty}
                      content-type={mediatypeOpt map (_.toString) getOrElse ""}
                      content-length={size.toString}/>
                }</xxf:events>

              // Encode successful response
              val response =
                <xxf:event-response xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                  <xxf:action>
                    <xxf:server-events>{
                      EncodeDecode.encodeXML(NodeConversions.elemToDom4j(serverEvents), XFormsProperties.isGZIPState, true, false)
                    }</xxf:server-events>
                  </xxf:action>
                </xxf:event-response>

              NodeConversions.elemToSAX(response, xmlReceiver)

            case (nameValues, someThrowable @ Some(t)) =>
              // NOTE: There is no point sending a response, see:
              // https://github.com/orbeon/orbeon-forms/issues/985
              Multipart.quietlyDeleteFileItems(nameValues)

              t match {
                case _: FileScanException =>
                  throw HttpStatusCodeException(StatusCode.Conflict, throwable = someThrowable) // unclear which status code makes the most sense
                case _: SizeLimitExceededException | _: FileSizeLimitExceededException =>
                  throw HttpStatusCodeException(StatusCode.RequestEntityTooLarge, throwable = someThrowable)
                case _ =>
                  throw HttpStatusCodeException(StatusCode.InternalServerError, throwable = someThrowable)
              }
          }
        }
      }
    )
}
