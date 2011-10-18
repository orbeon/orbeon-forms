/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import java.io._
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.processor.PageFlowControllerProcessor
import java.util.{List => JList}
import org.orbeon.oxf.util._
import org.orbeon.oxf.common.Version
import scala.collection.JavaConversions._
import org.orbeon.oxf.pipeline.api.{PipelineContext, ExternalContext}
import org.orbeon.oxf.externalcontext.URLRewriter

object XFormsResourceRewriter {
    /**
     * Generate the resources into the given OutputStream. The stream is flushed and closed when done.
     *
     * @param logger                logger
     * @param resources             list of XFormsFeatures.ResourceConfig to consider
     * @param os                    OutputStream to write to
     * @param isCSS                 whether to generate CSS or JavaScript resources
     * @param isMinimal             whether to use minimal resources
     */
    def generate(logger: IndentedLogger, resources: JList[XFormsFeatures.ResourceConfig], os: OutputStream, isCSS: Boolean, isMinimal: Boolean): Unit = {
        if (isCSS)
            generateCSS(logger, resources, os, isMinimal)
        else
            generateJS(logger, resources, os, isMinimal)

        os.flush()
        os.close()
    }

    private def generateCSS(logger: IndentedLogger, resources: JList[XFormsFeatures.ResourceConfig], os: OutputStream, isMinimal: Boolean): Unit = {

        val response = NetUtils.getExternalContext.getResponse
        val outputWriter = new OutputStreamWriter(os, "utf-8")

        val pipelineContext = PipelineContext.get

        // Create matcher that matches all paths in case resources are versioned
        if (pipelineContext.getAttribute(PageFlowControllerProcessor.PATH_MATCHERS) eq null) {
            val matchAllPathMatcher = URLRewriterUtils.getMatchAllPathMatcher
            pipelineContext.setAttribute(PageFlowControllerProcessor.PATH_MATCHERS, matchAllPathMatcher)
        }

        // Output Orbeon Forms version
        outputWriter.write("/* This file was produced by " + Version.getVersionString + " */\n")

        for (resource <- resources) {
            val resourcePath = resource.getResourcePath(isMinimal)

            // Read CSS into a string
            val originalCSS = {
                val sbw = new StringBuilderWriter
                val is = ResourceManagerWrapper.instance.getContentAsStream(resourcePath)
                ScalaUtils.copyReader(new InputStreamReader(is, "utf-8"), sbw)
                sbw.toString
            }

            // Rewrite it all
            outputWriter write rewriteCSS(originalCSS, resourcePath, response, logger)
        }
        outputWriter.flush()
    }

    def rewriteCSS(css: String, resourcePath: String, response: ExternalContext.Response, logger: IndentedLogger) = {

        val namespace = response.getNamespacePrefix

        // Match and rewrite an id within a selector
        val matchId = """#([\w]+)""".r
        def rewriteSelector(s: String) = matchId.replaceAllIn(s, e => "#" + namespace + e.group(1))

        // Match and rewrite a URL within a block
        val matchURL = """url\(("|')?([^"^'^\)]*)("|')?\)""".r
        def rewriteBlock(s: String) = matchURL.replaceAllIn(s, e => rewriteURL(e.group(2)))

        // Rewrite an individual URL
        def rewriteURL(url: String) =
            try {
                val resolvedURI = NetUtils.resolveURI(url, resourcePath)
                val rewrittenURI = response.rewriteResourceURL(resolvedURI, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)
                "url(" + rewrittenURI + ")"
            } catch {
                case _ =>
                    logger.logWarning("resources", "found invalid URI in CSS file", "uri", url)
                    "url(" + url + ")"
            }

        // Find approximately pairs of selectors/blocks and rewrite each part
        // Ids are rewritten only if the namespace is not empty
        val r = """([^\{]*\s*)(\{[^\}]*\})""".r
        r.replaceAllIn(css, e => (if (namespace.size == 0) e.group(1) else rewriteSelector(e.group(1))) + rewriteBlock(e.group(2)))
    }

    private def generateJS(logger: IndentedLogger, resources: JList[XFormsFeatures.ResourceConfig], os: OutputStream, isMinimal: Boolean): Unit = {
        // Output Orbeon Forms version
        val outputWriter = new OutputStreamWriter(os, "utf-8")
        outputWriter.write("// This file was produced by " + Version.getVersionString + "\n")
        outputWriter.flush()

        for (resourceConfig <- resources) {
            ScalaUtils.useAndClose(ResourceManagerWrapper.instance.getContentAsStream(resourceConfig.getResourcePath(isMinimal))) { is =>
                NetUtils.copyStream(is, os)
            }
            os.write('\n')
        }
    }
}
