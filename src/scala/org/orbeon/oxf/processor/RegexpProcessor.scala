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
package org.orbeon.oxf.processor

import ProcessorImpl._
import RegexpProcessor._
import java.util.regex.Pattern
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.pipeline.api.XMLReceiver
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.xml.XMLUtils

class RegexpProcessor extends ProcessorImpl {

    self ⇒

    addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG))
    addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
    addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

    override def createOutput(name: String) =
        addOutput(name, new CacheableTransformerOutputImpl(self, name) {
            def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver) {
                // Read inputs
                val data   = readInputAsDOM4J(pipelineContext, INPUT_DATA)
                val config = readInputAsDOM4J(pipelineContext, INPUT_CONFIG)

                val text   = data.selectObject("string(*)").asInstanceOf[String]
                val regexp = config.selectObject("string(*)").asInstanceOf[String]

                // Compute result
                val result = regexpMatch(regexp, text)

                // Output result
                writeXML(xmlReceiver, result.matches, result.groups)
            }
        })

    def regexpMatch(regexp: String, text: String): MatchResult = {
        val pattern = Pattern.compile(regexp)
        val matcher = pattern.matcher(text)

        val matches = matcher.matches
        MatchResult(matches, if (matches) (1 to matcher.groupCount) map matcher.group else Seq())
    }
}

object RegexpProcessor {

    def writeXML(xmlReceiver: XMLReceiver, matches: Boolean, groups: Seq[String]) {

        xmlReceiver.startDocument()
        xmlReceiver.startElement("", "result", "result", XMLUtils.EMPTY_ATTRIBUTES)

        // <matches>
        xmlReceiver.startElement("", "matches", "matches", XMLUtils.EMPTY_ATTRIBUTES)
        val matchesString = matches.toString
        xmlReceiver.characters(matchesString.toCharArray, 0, matchesString.length)
        xmlReceiver.endElement("", "matches", "matches")

        // <group>
        for (group ← groups) {
            xmlReceiver.startElement("", "group", "group", XMLUtils.EMPTY_ATTRIBUTES)
            if (group ne null)
                xmlReceiver.characters(group.toCharArray, 0, group.length)
            xmlReceiver.endElement("", "group", "group")
        }

        xmlReceiver.endElement("", "result", "result")
        xmlReceiver.endDocument()
    }

    case class MatchResult(matches: Boolean, groups: Seq[String]) {
        def group(i: Int) = groups(i)
    }
}