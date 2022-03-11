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

import org.orbeon.io.IOUtils._
import org.orbeon.io.{CharsetNames, IOUtils, StringBuilderWriter}
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.{ExternalContext, UrlRewriteMode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.resources.ResourceManagerWrapper
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.AssetPath
import org.orbeon.xforms.XFormsCrossPlatformSupport

import java.io._
import java.util.regex.Matcher
import scala.util.Try
import scala.util.control.NonFatal


// NOTE: Should rename to XFormsAssetRewriter?
object XFormsResourceRewriter extends Logging {

  // Generate the resources into the given OutputStream. The stream is flushed and closed when done.
  def generateAndClose(
    assetPaths   : List[AssetPath],
    namespaceOpt : Option[String],
    os           : OutputStream,
    isCSS        : Boolean,
    isMinimal    : Boolean)(implicit
    logger       : IndentedLogger
  ): Unit =
    useAndClose(os) { _ =>
      if (isCSS)
        generateCSS(assetPaths, namespaceOpt, os, isMinimal)
      else
        generateJS(assetPaths, os, isMinimal)

      os.flush()
    }

  private def logFailure[T](path: String)(implicit logger: IndentedLogger): PartialFunction[Throwable, Any] = {
    case NonFatal(_) =>
      error("could not read asset to aggregate", List("asset" -> path))
  }

  private def generateCSS(
    assetPaths   : List[AssetPath],
    namespaceOpt : Option[String],
    os           : OutputStream,
    isMinimal    : Boolean)(implicit
    logger       : IndentedLogger
  ): Unit = {

    val response = XFormsCrossPlatformSupport.externalContext.getResponse

    val pipelineContext = PipelineContext.get

    // Create matcher that matches all paths in case resources are versioned
    if (pipelineContext.getAttribute(PageFlowControllerProcessor.PathMatchers) eq null) {
      val matchAllPathMatcher = URLRewriterUtils.getMatchAllPathMatcher
      pipelineContext.setAttribute(PageFlowControllerProcessor.PathMatchers, matchAllPathMatcher)
    }

    val rm = ResourceManagerWrapper.instance

    // NOTE: The idea is that:
    // - we recover and log resource read errors (a file can be missing for example during development)
    // - we don't recover when writing (writing the resources will be interrupted)
    def tryInputStream(path: String) =
      Try(rm.getContentAsStream(path)) onFailure logFailure(path)

    // Use iterators so that we don't open all input streams at once
    def inputStreamIterator =
      for {
        asset <- assetPaths.iterator
        path  = asset.assetPath(isMinimal)
        is    <- tryInputStream(path).iterator
      } yield
        path -> is

    def tryReadCSS(path: String, is: InputStream) =
      Try {
        val sbw = new StringBuilderWriter
        copyReaderAndClose(new InputStreamReader(is, CharsetNames.Utf8), sbw)
        sbw.result
      } onFailure
        logFailure(path)

    val readCSSIterator =
      for {
        (path, is)  <- inputStreamIterator
        originalCSS <- tryReadCSS(path, is).iterator
      } yield
        path -> originalCSS

    val outputWriter = new OutputStreamWriter(os, CharsetNames.Utf8)

    // Output Orbeon Forms version if allowed
    Version.versionStringIfAllowed foreach { version =>
      outputWriter.write(s"/* This file was produced by $version */\n")
    }

    // Write and rewrite all resources one after the other
    readCSSIterator foreach {
      case (path, originalCSS) =>
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
  def rewriteCSS(
    css          : String,
    resourcePath : String,
    namespaceOpt : Option[String],
    response     : ExternalContext.Response)(implicit
    logger       : IndentedLogger
  ): String = {

    // Match and rewrite an id within a selector
    def rewriteSelector(s: String) = namespaceOpt match {
      case Some(namespace) => MatchId.replaceAllIn(s, e => Matcher.quoteReplacement("#" + namespace + e.group(1)))
      case None            => s
    }

    // Rewrite an individual URL
    def tryRewriteURL(url: String) =
      Try {
        val resolvedURI = NetUtils.resolveURI(url, resourcePath)
        val rewrittenURI = response.rewriteResourceURL(resolvedURI, UrlRewriteMode.AbsolutePathOrRelative)
        "url(" + rewrittenURI + ")"
      } recover {
        case NonFatal(_) =>
          warn("found invalid URI in CSS file", Seq("uri" -> url))
          "url(" + url + ")"
      }

    // Match and rewrite a URL within a block
    def rewriteBlock(s: String) =
      MatchURL.replaceAllIn(s, e => Matcher.quoteReplacement(tryRewriteURL(e.group(2)).get))

    // Find approximately pairs of selectors/blocks and rewrite each part
    // Ids are rewritten only if the namespace is not empty
    MatchSelectorAndBlock.replaceAllIn(css, e => Matcher.quoteReplacement(rewriteSelector(e.group(1)) + rewriteBlock(e.group(2))))
  }

  private def generateJS(
    assetPaths : List[AssetPath],
    os         : OutputStream,
    isMinimal  : Boolean)(implicit
    logger     : IndentedLogger
  ): Unit = {

    val outputWriter = new OutputStreamWriter(os, CharsetNames.Utf8)

    // Output Orbeon Forms version if allowed
    Version.versionStringIfAllowed foreach { version =>
      outputWriter.write(s"// This file was produced by $version\n")
      outputWriter.flush()
    }

    val rm = ResourceManagerWrapper.instance

    def tryInputStream(path: String) =
      Try(rm.getContentAsStream(path)) onFailure logFailure(path)

    // Use iterators so that we don't open all input streams at once
    def inputStreamIterator =
      assetPaths.iterator flatMap (r => tryInputStream(r.assetPath(isMinimal)).iterator)

    // Write all resources one after the other

    outputWriter.write(
      """
        |(function() {
        |    if (window.define || window.exports) {
        |        window.ORBEON = window.ORBEON || {};
        |        if (window.define) {
        |            window.ORBEON.define = window.define;
        |            window.define = null;
        |        }
        |        if (window.exports) {
        |            window.ORBEON.exports = window.exports;
        |            window.exports = null;
        |        }
        |    }
        |})();
      """.stripMargin)

    outputWriter.flush()

    inputStreamIterator foreach { is =>
      IOUtils.copyStreamAndClose(is, os, doCloseOut = false)
      os.write('\n')
    }

    outputWriter.write(
      """
        |(function() {
        |    if (window.ORBEON.define) {
        |        window.define = window.ORBEON.define;
        |        window.ORBEON.define = null;
        |    }
        |    if (window.ORBEON.exports) {
        |        window.exports = window.ORBEON.exports;
        |        window.ORBEON.exports = null;
        |    }
        |})();
      """.stripMargin)

    outputWriter.flush()
  }

  // Compute the last modification date of the given resources.
  def computeCombinedLastModified(assetPaths: List[AssetPath], isMinimal: Boolean): Long = {

    val rm = ResourceManagerWrapper.instance

    // NOTE: Actual aggregation will log missing files so we ignore them here
    def lastModified(r: AssetPath) =
      Try(rm.lastModified(r.assetPath(isMinimal), false)) getOrElse 0L

    if (assetPaths.isEmpty) 0L else assetPaths map lastModified max
  }
}
