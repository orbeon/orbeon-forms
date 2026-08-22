/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.{JsoupSAX, TextXMLReceiver}
import org.orbeon.oxf.xml.XMLReceiver

import java.io.StringWriter

class TagSoupProcessor extends ProcessorImpl {

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(name: String) =
    addOutput(name, new ProcessorOutputImpl(this, name) {
      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        // Read input as binary document
        val inputValue = {
          val writer = new StringWriter
          readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA, new TextXMLReceiver(writer))
          writer.getBuffer.toString
        }

        JsoupSAX.parseHtmlToReceiver(inputValue, xmlReceiver)
      }
    })
}
