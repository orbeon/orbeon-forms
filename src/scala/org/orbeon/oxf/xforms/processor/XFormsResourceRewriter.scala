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
import org.orbeon.oxf.pipeline.api.ExternalContext
import scala.collection.JavaConversions._

object XFormsResourceRewriter {
    /**
     * Generate the resources into the given OutputStream. The stream is flushed and closed when done.
     *
     * @param logger                logger
     * @param resources             list of XFormsFeatures.ResourceConfig to consider
     * @param propertyContext       current PipelineContext (used for rewriting and matchers)
     * @param os                    OutputStream to write to
     * @param isCSS                 whether to generate CSS or JavaScript resources
     * @param isMinimal             whether to use minimal resources
     */
    def generate(logger: IndentedLogger, resources: JList[XFormsFeatures.ResourceConfig], propertyContext: PropertyContext, os: OutputStream, isCSS: Boolean, isMinimal: Boolean): Unit = {
        if (isCSS)
            generateCSS(logger, resources, propertyContext, os, isMinimal)
        else
            generateJS(logger, resources, propertyContext, os, isMinimal)

        os.flush()
        os.close()
    }

    private def generateCSS(logger: IndentedLogger, resources: JList[XFormsFeatures.ResourceConfig], propertyContext: PropertyContext, os: OutputStream, isMinimal: Boolean): Unit = {
        val externalContext = NetUtils.getExternalContext
        val response = externalContext.getResponse
        val containerNamespace = externalContext.getRequest.getContainerNamespace

        val outputWriter = new OutputStreamWriter(os, "utf-8")

        // Create matcher that matches all paths in case resources are versioned
        if (propertyContext.getAttribute(PageFlowControllerProcessor.PATH_MATCHERS) eq null) {
            val matchAllPathMatcher = URLRewriterUtils.getMatchAllPathMatcher
            propertyContext.setAttribute(PageFlowControllerProcessor.PATH_MATCHERS, matchAllPathMatcher)
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
            outputWriter write rewriteCSS(originalCSS, containerNamespace, resourcePath, response, logger)
        }
        outputWriter.flush()
    }

    def rewriteCSS(css: String, namespace: String, resourcePath: String, response: ExternalContext.Response, logger: IndentedLogger) = {

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
                val rewrittenURI = response.rewriteResourceURL(resolvedURI, false)
                "url(" + rewrittenURI + ")"
            } catch {
                case e: Exception => {
                    logger.logWarning("resources", "found invalid URI in CSS file", "uri", url)
                    "url(" + url + ")"
                }
            }

        // Find approximately pairs of selectors/blocks and rewrite each part
        // Ids are rewritten only if the namespace is not empty
        val r = """([^\{]*\s*)(\{[^\}]*\})""".r
        r.replaceAllIn(css, e => (if (namespace isEmpty) e.group(1) else rewriteSelector(e.group(1))) + rewriteBlock(e.group(2)))
    }

    private def generateJS(indentedLogger: IndentedLogger, resources: JList[XFormsFeatures.ResourceConfig], propertyContext: PropertyContext, os: OutputStream, isMinimal: Boolean): Unit = {
        // Output Orbeon Forms version
        val outputWriter = new OutputStreamWriter(os, "utf-8")
        outputWriter.write("// This file was produced by " + Version.getVersionString + "\n")
        outputWriter.flush()

        var first = true
        for (resourceConfig <- resources) {
            if (!first)
                os.write('\n')
            ScalaUtils.useAndClose(ResourceManagerWrapper.instance.getContentAsStream(resourceConfig.getResourcePath(isMinimal))) { is =>
                NetUtils.copyStream(is, os)
            }
            first = false
        }
    }
}