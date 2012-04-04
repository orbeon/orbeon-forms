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

import org.orbeon.oxf.pipeline.api.{XMLReceiver, PipelineContext}
import org.ccil.cowan.tagsoup.HTMLSchema
import org.xml.sax.InputSource
import org.orbeon.oxf.util.TextXMLReceiver
import java.io.{StringWriter, StringReader}

class TagSoupProcessor extends ProcessorImpl {

    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.INPUT_DATA))
    addInputInfo(new ProcessorInputOutputInfo(ProcessorImpl.OUTPUT_DATA))

    val TAGSOUP_HTML_SCHEMA = new HTMLSchema()

    override def createOutput(name: String) =
        addOutput(name, new ProcessorOutputImpl(this, name) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) {

                // Read input as binary document in byte array
                val inputValue = {
                    val writer = new StringWriter
                    readInputAsSAX(pipelineContext, ProcessorImpl.INPUT_DATA, new TextXMLReceiver(writer))
                    writer.getBuffer.toString
                }

                // Create TagSoup reader
                val tagSoupReader = new org.ccil.cowan.tagsoup.Parser
                tagSoupReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, TAGSOUP_HTML_SCHEMA)
                tagSoupReader.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true)

                // Connect to input
                val inputSource = new InputSource
                inputSource.setCharacterStream(new StringReader(inputValue))
                // Connect to output
                tagSoupReader.setContentHandler(xmlReceiver)
                // Do the TagSoup parsing
                tagSoupReader.parse(inputSource)
            }
        })
}
