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
package org.orbeon.oxf.processor.serializer.legacy

import org.orbeon.io.IOUtils
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.processor.serializer.HttpSerializerBase
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInput, ProcessorOutput}
import org.orbeon.oxf.util.ContentHandlerOutputStream
import org.orbeon.oxf.xml.XMLReceiver

import java.io.OutputStream


/**
 * Legacy HTTP binary serializer. This is deprecated by HttpSerializer.
 */
abstract class HttpBinarySerializer extends HttpSerializerBase {

  self =>

  override final protected def readInput(
    pipelineContext: PipelineContext,
    response       : ExternalContext.Response,
    input          : ProcessorInput,
    config         : HttpSerializerBase.Config
  ): Unit = {
    response.setContentType(config.contentTypeOrDefault(getDefaultContentType))
    readInput(pipelineContext, input, config, response.getOutputStream)
  }

  /**
   * This must be overridden by subclasses.
   */
//  protected
  def readInput(
    context     : PipelineContext,
    input       : ProcessorInput,
    config      : HttpSerializerBase.Config,
    outputStream: OutputStream
  ): Unit

  /**
   * This method is use when the legacy serializer is used in the new converter mode. In this
   * case, the converter exposes a "data" output, and the processor's start() method is not
   * called.
   */
  override def createOutput(name: String): ProcessorOutput = {
    require(name == ProcessorImpl.OUTPUT_DATA, s"Invalid output name: `$name`")
    addOutput(
      name,
      new CacheableTransformerOutputImpl(self, name) {
        def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

          val config = readConfig(pipelineContext)

          IOUtils.useAndClose(new ContentHandlerOutputStream(xmlReceiver, doStartEndDocument = true)) { outputStream =>
            outputStream.setContentType(config.contentTypeOrDefault(getDefaultContentType))
            readInput(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA), config, outputStream)
          }
        }
      }
    )
  }
}