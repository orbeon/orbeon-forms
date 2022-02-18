/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xml

import javax.xml.transform.sax.SAXSource
import org.apache.commons.fileupload.FileItem
import org.log4s.Logger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.generator.URLGenerator
import org.orbeon.oxf.processor.serializer.BinaryTextXMLReceiver
import org.orbeon.oxf.processor.{Processor, ProcessorImpl}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.{FileItemSupport, NetUtils}
import org.xml.sax.InputSource

object PartUtils {

  // Read a text or binary document and return it as a FileItem
  def handleStreamedPartContent(source: SAXSource)(logger: Logger): FileItem = {
    val fileItem = FileItemSupport.prepareFileItem(NetUtils.REQUEST_SCOPE, logger.logger)
    TransformerUtils.sourceToSAX(source, new BinaryTextXMLReceiver(fileItem.getOutputStream))
    fileItem
  }

  def getSAXSource(processor: Processor, pipelineContext: PipelineContext, href: String, base: String, contentType: String): SAXSource = {
    val processorOutput =
      Option(ProcessorImpl.getProcessorInputSchemeInputName(href)) match {
        case Some(inputName) =>
          processor.getInputByName(inputName).getOutput
        case None =>
          val urlGenerator =
            Option(contentType) map
            (new URLGenerator(URLFactory.createURL(base, href), _, true)) getOrElse
             new URLGenerator(URLFactory.createURL(base, href))

          urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA)
      }

    val saxSource = new SAXSource(new ProcessorOutputXMLReader(pipelineContext, processorOutput), new InputSource)
    saxSource.setSystemId(href)
    saxSource
  }
}
