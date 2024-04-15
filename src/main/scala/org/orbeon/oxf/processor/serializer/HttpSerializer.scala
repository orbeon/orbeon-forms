/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.processor.serializer

import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.PathType
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput}


/**
 * This serializer is a generic HTTP serializer able to serialize text as well as binary.
 */
object HttpSerializer {
  val HttpSerializerConfigNamespaceUri = "http://www.orbeon.com/oxf/http-serializer"
}

class HttpSerializer extends HttpSerializerBase {

  //    protected
  override def getDefaultContentType: String = BinaryTextXMLReceiver.DefaultBinaryContentType

  //    protected
  override def getConfigSchemaNamespaceURI: String = HttpSerializer.HttpSerializerConfigNamespaceUri

  //    protected
  override def readInput(
    context : PipelineContext,
    response: ExternalContext.Response,
    input   : ProcessorInput,
    config  : HttpSerializerBase.Config
  ): Unit = {
    val pathType = context.getAttribute(PageFlowControllerProcessor.PathTypeKey).asInstanceOf[PathType]
    ProcessorImpl.readInputAsSAX(
      context,
      input,
      new BinaryTextXMLReceiver(
        response,
        if (pathType != null) pathType else PathType.Page,
        null,
        true,
        config.forceContentType,
        config.contentTypeOrNull,
        config.ignoreDocumentContentType,
        config.forceEncoding,
        config.encodingOrNull,
        config.ignoreDocumentEncoding,
        config.headersToForward
      )
    )
  }
}