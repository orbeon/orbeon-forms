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
import RegexpMatcher._
import java.util.regex.Pattern
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.impl.CacheableTransformerOutputImpl
import org.orbeon.oxf.xml.{SAXUtils, XMLReceiver}
import org.orbeon.oxf.util.URLRewriterUtils.globToRegexp

class RegexpProcessor extends ProcessorImpl {

  self =>

  addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG))
  addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA))
  addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA))

  override def createOutput(name: String) =
    addOutput(name, new CacheableTransformerOutputImpl(self, name) {
      def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        // Read inputs
        val data   = readInputAsOrbeonDom(pipelineContext, INPUT_DATA)
        val config = readInputAsOrbeonDom(pipelineContext, INPUT_CONFIG)

        val text   = data.getRootElement.getStringValue
        val regexp = config.getRootElement.getStringValue

        // Compute and output result
        writeXML(xmlReceiver, regexpMatch(regexp, text))
      }
    })

  def regexpMatch(regexp: String, text: String): MatchResult =
    MatchResult(compilePattern(regexp, glob = false), text)
}

object RegexpMatcher {

  def writeXML(xmlReceiver: XMLReceiver, result: MatchResult): Unit = {

    xmlReceiver.startDocument()
    xmlReceiver.startElement("", "result", "result", SAXUtils.EMPTY_ATTRIBUTES)

    // <matches>
    xmlReceiver.startElement("", "matches", "matches", SAXUtils.EMPTY_ATTRIBUTES)
    val matchesString = result.matches.toString
    xmlReceiver.characters(matchesString.toCharArray, 0, matchesString.length)
    xmlReceiver.endElement("", "matches", "matches")

    // <group>
    for (group <- result.groupsWithNulls) {
      xmlReceiver.startElement("", "group", "group", SAXUtils.EMPTY_ATTRIBUTES)
      if (group ne null)
        xmlReceiver.characters(group.toCharArray, 0, group.length)
      xmlReceiver.endElement("", "group", "group")
    }

    xmlReceiver.endElement("", "result", "result")
    xmlReceiver.endDocument()
  }

  def compilePattern(path: String, glob: Boolean = false) =
    Pattern.compile(if (glob) globToRegexp(path.toCharArray) else path)

  case class MatchResult(matches: Boolean, groupsWithNulls: Seq[String] = Seq()) {
    def group(i: Int) = groupsWithNulls(i)
  }

  object MatchResult {
    def apply(pattern: Pattern, s: String): MatchResult = {
      val matcher = pattern.matcher(s)
      val matches = matcher.matches
      MatchResult(matches, if (matches) 1 to matcher.groupCount map matcher.group else Seq())
    }
  }

  // For Java callers
  def jMatchResult(pattern: Pattern, s: String) = MatchResult(pattern, s)
}