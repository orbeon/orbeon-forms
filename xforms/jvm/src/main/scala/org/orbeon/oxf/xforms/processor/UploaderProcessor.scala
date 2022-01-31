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

import org.apache.commons.fileupload.FileUploadBase.{FileSizeLimitExceededException, SizeLimitExceededException}
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.http.{Headers, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorOutput}
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.{Loggers, XFormsGlobalProperties}
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xml.{EncodeDecode, XMLReceiver}
import org.orbeon.scaxon.NodeConversions
import org.orbeon.xforms.XFormsCrossPlatformSupport


class UploaderProcessor extends ProcessorImpl {
  override def createOutput(name: String): ProcessorOutput =
    addOutput(
      name,
      new ProcessorOutputImpl(UploaderProcessor.this, name) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          def outputResponse(serverEvents: scala.xml.Elem): Unit =
              NodeConversions.elemToSAX(
                <xxf:event-response xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                  <xxf:action>
                    <xxf:server-events>{
                      EncodeDecode.encodeXML(
                        NodeConversions.elemToOrbeonDom(serverEvents),
                        XFormsGlobalProperties.isGZIPState,
                        encrypt = true,
                        location = false
                      )
                    }</xxf:server-events>
                  </xxf:action>
                </xxf:event-response>,
                xmlReceiver
              )

          UploaderServer.processUpload(XFormsCrossPlatformSupport.externalContext.getRequest) match {
            case (nameValuesFileScan, None) =>

              // NOTE: As of 2013-05-09, the client only uploads one file per request. We are able to
              // handle more than one here.
              val files =
                nameValuesFileScan collect { // Q: Why do we test on fileItem.getName? We call the file scan API even is not blank, by the way!
                  case (name, Right(fileItem), fileScanAcceptResultOpt) if fileItem.getName.nonAllBlank => // XXX TODO use fileScanAcceptResultOpt

                    // Get size before renaming below
                    val message   = fileScanAcceptResultOpt.flatMap(_.message)
                    val mediatype = fileScanAcceptResultOpt.flatMap(_.mediatype) orElse fileItem.getContentType.trimAllToOpt
                    val filename  = fileScanAcceptResultOpt.flatMap(_.filename ) orElse fileItem.getName.trimAllToOpt

                    // Not used yet
                    //val extension = fileScanAcceptResultOpt.flatMap(_.extension)

                    def sessionUrlAndSizeFromFileScan =
                      fileScanAcceptResultOpt.flatMap(_.content) map { is =>
                        useAndClose(is) { _ =>
                          FileItemSupport.inputStreamToAnyURI(is, NetUtils.SESSION_SCOPE, Loggers.logger.logger)
                        }
                      }

                    // If there is a `FileScanProvider`, a `File` is obtained from the `DiskFileItem` separately. If by
                    // any chance a new file is created on disk at that time, it will be deleted separately as the request
                    // completes. Here again we would create a new request-expired file, before renaming it and making
                    // sure it expires with the session only.
                    def sessionUrlAndSizeFromFileItem = {

                      val newFile =
                        NetUtils.renameAndExpireWithSession(
                          RequestGenerator.urlForFileItemCreateIfNeeded(fileItem, NetUtils.REQUEST_SCOPE))(
                          Loggers.logger.logger
                        )

                      (newFile.toURI.toString, newFile.length())
                    }

                    (name, filename, mediatype, sessionUrlAndSizeFromFileScan getOrElse sessionUrlAndSizeFromFileItem)
                }

              outputResponse(
                <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">{
                  for {
                    (fieldName, filename, mediatype, (sessionURL, size)) <- files
                    headers      = Map(Headers.ContentType -> mediatype)
                    mediatypeOpt = Mediatypes.fromHeadersOrFilename(n => headers.get(n).flatten, filename)
                  } yield
                    <xxf:event
                      name="xxforms-upload-done"
                      source-control-id={fieldName}
                      file={sessionURL}
                      filename={filename.getOrElse("")}
                      content-type={mediatypeOpt map (_.toString) getOrElse ""}
                      content-length={size.toString}/>
                }</xxf:events>
              )

            case (nameValues, someThrowable @ Some(t)) =>
              // NOTE: There is no point sending a response, see:
              //   https://github.com/orbeon/orbeon-forms/issues/985
              // 2022-02-18: However we do send a response for a `FileScanException` so we can pass a custom error
              // message.
              Multipart.quietlyDeleteFileItems(nameValues)

              t match {
                case FileScanException(fieldName, fileScanResult) =>
                  outputResponse(
                    <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                      <xxf:event name="xxforms-upload-error" source-control-id={fieldName}>
                        <xxf:property name="error-type">file-scan-error</xxf:property>
                        <xxf:property name="message">{fileScanResult.message.getOrElse("")}</xxf:property>
                      </xxf:event>
                    </xxf:events>
                  )
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
