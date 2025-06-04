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

import org.apache.commons.fileupload.FileCountLimitExceededException
import org.apache.commons.fileupload.FileUploadBase.{FileSizeLimitExceededException, SizeLimitExceededException}
import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.RegexpMatcher.MatchResult
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.XFormsGlobalProperties
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xforms.upload.UploaderServer.UploadResponse
import org.orbeon.oxf.xml.EncodeDecode
import org.orbeon.scaxon.NodeConversions
import org.orbeon.xforms.EventNames


object XFormsUploadRoute extends XmlNativeRoute {

  def process(
    matchResult: MatchResult
  )(implicit
    pc         : PipelineContext,
    ec         : ExternalContext
  ): Unit = {

    val response = ec.getResponse

    def outputResponse(serverEvents: scala.xml.Elem): Unit = {

      response.setStatus(StatusCode.Ok)

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
        getResponseXmlReceiverSetContentType(ec)
      )
    }

    // NOTE: As of 2013-05-09, the client only uploads one file per request. We are able to
    // handle more than one here.
    UploaderServer.processUpload(ec.getRequest) match {
      case (uploadResponses, None) =>

        outputResponse(
          <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">{
            for {
              UploadResponse(fieldName, messageOpt, mediatypeOpt, filenameOpt, uploadId, sessionUrl, actualSize, hashAlgorithm, hashValue) <- uploadResponses
              effectiveMediatypeOpt = Mediatypes.fromHeadersOrFilename(_ => mediatypeOpt, filenameOpt)
            } yield
              <xxf:event
                name              = {EventNames.XXFormsUploadStore}
                source-control-id = {fieldName}
                file              = {sessionUrl.toString}
                upload-id         = {uploadId}
                filename          = {filenameOpt.getOrElse("")}
                content-type      = {effectiveMediatypeOpt.map(_.toString).getOrElse("")}
                content-length    = {actualSize.toString}
                hash-algorithm    = {hashAlgorithm}
                hash-value        = {hashValue}
                messsage          = {messageOpt.getOrElse("")}/>
          }</xxf:events>
        )

      case (_, Some(FileScanException(fieldName, fileScanResult))) =>
        // 2022-02-18: We do send a response for a `FileScanException` so we can pass a custom error message.
        outputResponse(
          <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xxf:event name={EventNames.XXFormsUploadError} source-control-id={fieldName}>
              <xxf:property name="error-type">file-scan-error</xxf:property>
              <xxf:property name="message">{fileScanResult.message.getOrElse("")}</xxf:property>
            </xxf:event>
          </xxf:events>
        )
      case (_, someThrowable @ Some(_: SizeLimitExceededException | _: FileSizeLimitExceededException | _: FileCountLimitExceededException)) =>
        // No point sending a response body: https://github.com/orbeon/orbeon-forms/issues/985
        throw HttpStatusCodeException(StatusCode.RequestEntityTooLarge, throwable = someThrowable)
      case (_, someThrowable) =>
        // No point sending a response body: https://github.com/orbeon/orbeon-forms/issues/985
        throw HttpStatusCodeException(StatusCode.InternalServerError, throwable = someThrowable)
    }
  }
}
