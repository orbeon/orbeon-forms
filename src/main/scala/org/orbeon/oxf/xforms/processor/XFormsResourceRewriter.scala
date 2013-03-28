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

import org.orbeon.oxf.util._
import ScalaUtils._
import java.io._
import java.util.regex.Matcher
import java.util.{List ⇒ JList}
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.processor.XFormsFeatures.ResourceConfig
import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

object XFormsResourceRewriter extends Logging {
    /**
     * Generate the resources into the given OutputStream. The stream is flushed and closed when done.
     *
     * @param logger                logger
     * @param resources             list of ResourceConfig to consider
     * @param os                    OutputStream to write to
     * @param isCSS                 whether to generate CSS or JavaScript resources
     * @param isMinimal             whether to use minimal resources
     */
    def generate(resources: Seq[ResourceConfig], namespaceOpt: Option[String], os: OutputStream, isCSS: Boolean, isMinimal: Boolean)(implicit logger: IndentedLogger): Unit =
        useAndClose(os) { _ ⇒
            if (isCSS)
                generateCSS(resources, namespaceOpt, os, isMinimal)
            else
                generateJS(resources, os, isMinimal)

            os.flush()
        }

    private def logFailure[T](path: String)(implicit logger: IndentedLogger): PartialFunction[Throwable, Try[T]] = {
        case e: Exception ⇒
            error("could not read resource to aggregate", Seq("resource" → path))
            new Failure(e)
    }

    private def generateCSS(resources: Seq[ResourceConfig], namespaceOpt: Option[String], os: OutputStream, isMinimal: Boolean)(implicit logger: IndentedLogger): Unit = {

        val response = NetUtils.getExternalContext.getResponse

        val pipelineContext = PipelineContext.get

        // Create matcher that matches all paths in case resources are versioned
        if (pipelineContext.getAttribute(PageFlowControllerProcessor.PathMatchers) eq null) {
            val matchAllPathMatcher = URLRewriterUtils.getMatchAllPathMatcher
            pipelineContext.setAttribute(PageFlowControllerProcessor.PathMatchers, matchAllPathMatcher)
        }

        val rm = ResourceManagerWrapper.instance

        // NOTE: The idea is that:
        // - we recover and log resource read errors (a file can be missing for example during development)
        // - we don't recover when writing (writing the resources will be interupted)
        def tryInputStream(path: String) =
            Try(rm.getContentAsStream(path)) recoverWith logFailure(path)

        // Use iterators so that we don't open all input streams at once
        def inputStreamIterator =
            for {
                resource ← resources.iterator
                path     = resource.getResourcePath(isMinimal)
                is       ← tryInputStream(path)
            } yield
                path → is

        def tryReadCSS(path: String, is: InputStream) =
            Try {
                val sbw = new StringBuilderWriter
                copyReader(new InputStreamReader(is, "utf-8"), sbw)
                sbw.toString
            } recoverWith
                logFailure(path)

        val readCSSIterator =
            for {
                (path, is)  ← inputStreamIterator
                originalCSS ← tryReadCSS(path, is)
            } yield
                path → originalCSS

        val outputWriter = new OutputStreamWriter(os, "utf-8")

        // Output Orbeon Forms version if allowed
        if (! XFormsProperties.isEncodeVersion)
            outputWriter.write("/* This file was produced by " + Version.VersionString + " */\n")

        // Write and rewrite all resources one after the other
        readCSSIterator foreach {
            case (path, originalCSS) ⇒
                if (! isMinimal)
                    outputWriter.write("/* Original CSS path: " + path + " */\n")

                outputWriter.write(rewriteCSS(originalCSS, path, namespaceOpt, response))
        }

        outputWriter.flush()
    }

    private val MatchSelectorAndBlock = """([^\{]*\s*)(\{[^\}]*\})""".r
    private val MatchId               = """#([\w]+)""".r
    private val MatchURL              = """url\(("|')?([^"^'^\)]*)("|')?\)""".r

    // Public for unit tests
    def rewriteCSS(css: String, resourcePath: String, namespaceOpt: Option[String], response: ExternalContext.Response)(implicit logger: IndentedLogger) = {

        // Match and rewrite an id within a selector
        def rewriteSelector(s: String) = namespaceOpt match {
            case Some(namespace) ⇒ MatchId.replaceAllIn(s, e ⇒ Matcher.quoteReplacement("#" + namespace + e.group(1)))
            case None            ⇒ s
        }

        // Rewrite an individual URL
        def tryRewriteURL(url: String) =
            Try {
                val resolvedURI = NetUtils.resolveURI(url, resourcePath)
                val rewrittenURI = response.rewriteResourceURL(resolvedURI, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)
                "url(" + rewrittenURI + ")"
            } recover {
                case e: Exception ⇒
                    warn("found invalid URI in CSS file", Seq("uri" → url))
                    "url(" + url + ")"
            }

        // Match and rewrite a URL within a block
        def rewriteBlock(s: String) =
            MatchURL.replaceAllIn(s, e ⇒ Matcher.quoteReplacement(tryRewriteURL(e.group(2)).get))

        // Find approximately pairs of selectors/blocks and rewrite each part
        // Ids are rewritten only if the namespace is not empty
        MatchSelectorAndBlock.replaceAllIn(css, e ⇒ Matcher.quoteReplacement(rewriteSelector(e.group(1)) + rewriteBlock(e.group(2))))
    }

    private def generateJS(resources: Seq[ResourceConfig], os: OutputStream, isMinimal: Boolean)(implicit logger: IndentedLogger): Unit = {
        // Output Orbeon Forms version if allowed
        if (! XFormsProperties.isEncodeVersion) {
            val outputWriter = new OutputStreamWriter(os, "utf-8")
            outputWriter.write("// This file was produced by " + Version.VersionString + "\n")
            outputWriter.flush()
        }

        val rm = ResourceManagerWrapper.instance

        def tryInputStream(path: String) =
            Try(rm.getContentAsStream(path)) recoverWith logFailure(path)

        // Use iterators so that we don't open all input streams at once
        def inputStreamIterator =
            resources.iterator flatMap (r ⇒ tryInputStream(r.getResourcePath(isMinimal)))

        // Write all resources one after the other
        inputStreamIterator foreach { is ⇒
            useAndClose(is)(NetUtils.copyStream(_, os))
            os.write('\n')
        }
    }

    def jComputeCombinedLastModified(resources: JList[ResourceConfig], isMinimal: Boolean) =
        computeCombinedLastModified(resources.asScala, isMinimal)

    // Compute the last modification date of the given resources.
    def computeCombinedLastModified(resources: Seq[ResourceConfig], isMinimal: Boolean): Long = {

        val rm = ResourceManagerWrapper.instance

        // NOTE: Actual aggregation will log missing files so we ignore them here
        def lastModified(r: ResourceConfig) =
            Try(rm.lastModified(r.getResourcePath(isMinimal), false)) getOrElse 0L

        if (resources.isEmpty) 0L else resources map lastModified max
    }

    def cacheResources(resources: Seq[ResourceConfig], resourcePath: String, namespaceOpt: Option[String], combinedLastModified: Long, isCSS: Boolean, isMinimal: Boolean): File = {

        implicit val indentedLogger = XFormsResourceServer.indentedLogger
        val rm = ResourceManagerWrapper.instance
        
        Option(rm.getRealPath(resourcePath)) match {
            case Some(realPath) ⇒
                // We hope to be able to cache as a resource
                def logParameters = Seq("resource path" → resourcePath, "real path" → realPath)

                val resourceFile = new File(realPath)
                if (resourceFile.exists) {
                    // Resources exist, generate if needed
                    val resourceLastModified = resourceFile.lastModified
                    if (resourceLastModified < combinedLastModified) {
                        // Resource is out of date, generate
                        debug("cached combined resources out of date, saving", logParameters)
                        val fos = new FileOutputStream(resourceFile)
                        generate(resources, namespaceOpt, fos, isCSS, isMinimal)(indentedLogger)
                    } else
                        debug("cached combined resources exist and are up-to-date", logParameters)
                } else {
                    // Resource doesn't exist, generate
                    debug("cached combined resources don't exist, saving", logParameters)
                    resourceFile.getParentFile.mkdirs()
                    resourceFile.createNewFile()
                    val fos = new FileOutputStream(resourceFile)
                    generate(resources, namespaceOpt, fos, isCSS, isMinimal)(indentedLogger)
                }
                resourceFile
            case None ⇒
                debug("unable to locate real path for cached combined resources, not saving", Seq("resource path" → resourcePath))
                null
        }
    }
}
