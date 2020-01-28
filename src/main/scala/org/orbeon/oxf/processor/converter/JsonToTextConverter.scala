/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.processor.converter

import org.orbeon.oxf.json
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{BinaryTextSupport, ProcessorImpl, ProcessorInputOutputInfo}
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xml.XMLReceiver

class JsonToTextConverter extends ProcessorImpl {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

  override def createOutput(outputName: String) =
    addOutput(outputName, new ProcessorOutputImpl(self, outputName) {
      def readImpl(context: PipelineContext, xmlReceiver: XMLReceiver): Unit = {

        val xmlDoc = readInputAsTinyTree(
          context,
          getInputByName(ProcessorImpl.INPUT_DATA),
          XPath.GlobalConfiguration
        )

        val jsonString = json.Converter.xmlToJsonString(xmlDoc, strict = false)

        BinaryTextSupport.readText(
          jsonString,
          xmlReceiver,
          "application/json",
          null
        )
      }
    })
}
