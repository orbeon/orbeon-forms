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

import javax.servlet.http.HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE

import org.apache.commons.fileupload.FileItem
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{Multipart, NetUtils}
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xforms.{XFormsProperties, XFormsUtils}
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.scaxon.XML

class Upload extends ProcessorImpl {
  override def createOutput(name: String) =
    addOutput(name, new ProcessorOutputImpl(Upload.this, name) {
      override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) =
        Multipart.parseMultipartRequest(NetUtils.getExternalContext.getRequest, RequestGenerator.getMaxSizeProperty, ExternalContext.StandardHeaderCharacterEncoding) match {
          case (nameValues, None) ⇒

            // NOTE: As of 2013-05-09, the client only uploads one file per request. We are able to
            // handle more than one here.
            val files = nameValues collect {
              case (name, fileItem: FileItem) if StringUtils.isNotBlank(fileItem.getName) ⇒
                val size = fileItem.getSize
                val sessionURL = NetUtils.renameAndExpireWithSession(RequestGenerator.urlForFileItem(fileItem), XFormsServer.logger).toURI.toString
                (name, fileItem, sessionURL, size)
            }

            val serverEvents =
              <xxf:events xmlns:xxf="http://orbeon.org/oxf/xml/xforms">{
                for ((name, fileItem, sessionURL, size) ← files)
                yield
                  <xxf:event
                    name="xxforms-upload-done"
                    source-control-id={name}
                    file={sessionURL}
                    filename={fileItem.getName.trimAllToEmpty}
                    content-type={fileItem.getContentType.trimAllToEmpty}
                    content-length={size.toString}/>
              }</xxf:events>

            // Encode successful response
            val response =
              <xxf:event-response xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                <xxf:action>
                  <xxf:server-events delay="0">{XFormsUtils.encodeXML(XML.elemToDom4j(serverEvents), XFormsProperties.isGZIPState, true, false)}</xxf:server-events>
                </xxf:action>
              </xxf:event-response>

            XML.elemToSAX(response, xmlReceiver)

          case (nameValues, someThrowable @ Some(t)) ⇒
            // NOTE: There is no point sending a response, see:
            // https://github.com/orbeon/orbeon-forms/issues/985
            Multipart.deleteFileItems(nameValues)
            throw HttpStatusCodeException(SC_REQUEST_ENTITY_TOO_LARGE, throwable = someThrowable)
        }
    })
}
