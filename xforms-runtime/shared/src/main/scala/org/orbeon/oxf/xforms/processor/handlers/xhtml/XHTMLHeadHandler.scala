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
package org.orbeon.oxf.xforms.processor.handlers.xhtml


import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.processor.ScriptBuilder
import org.orbeon.oxf.xforms.processor.ScriptBuilder._
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XHTMLOutput}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.xbl.{AssetsSupport, XBLAssets}
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.xforms.Constants
import org.xml.sax.Attributes


// Handler for `<xh:head>`
class XHTMLHeadHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  handlerContext : HandlerContext
) extends
  XFormsBaseHandlerXHTML(
    uri,
    localname,
    qName,
    localAtts,
    handlerContext,
    repeating  = false,
    forwarding = true
  ) {

  import XHTMLHeadHandler._

  private var formattingPrefix: String = null

  override def start(): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    handlerContext.controller.combinePf(XHTMLOutput.headPf)

    // Declare `xmlns:f`
    formattingPrefix = handlerContext.findFormattingPrefixDeclare

    // Open head element
    xmlReceiver.startElement(uri, localname, qName, attributes)

    val xhtmlPrefix = XMLUtils.prefixFromQName(qName)

    // Create prefix for combined resources if needed
    val isMinimal = XFormsGlobalProperties.isMinimalResources
    val isVersionedResources = URLRewriterUtils.isResourcesVersioned

    // Include static XForms CSS and JS
    val externalContext = handlerContext.externalContext

    if (containingDocument.isServeInlineResources)
      withElement("style", xhtmlPrefix, XHTML_NAMESPACE_URI, List("type" -> "text/css", "media" -> "all")) {
        text(
          """html body form.xforms-initially-hidden, html body .xforms-form .xforms-initially-hidden {
              display: none
          }"""
        )
      }

    val XFormsAssets(baselineCss, baselineJs) = containingDocument.staticState.baselineAssets
    val XBLAssets(css, js)                    = containingDocument.staticState.bindingAssets

    // Stylesheets
    AssetsSupport.outputCssAssets(xhtmlPrefix, baselineCss, css, isMinimal)

    // Scripts
    locally {

      // Linked JavaScript resources
      AssetsSupport.outputJsAssets(xhtmlPrefix, baselineJs, js, isMinimal)

      if (! containingDocument.isServeInlineResources) {

        // Static resources
        if (containingDocument.staticOps.uniqueJsScripts.nonEmpty)
          element(
            localName = "script",
            prefix    = xhtmlPrefix,
            uri       = XHTML_NAMESPACE_URI,
            atts      = "src" -> (XFormsAssetPaths.FormStaticResourcesPath + containingDocument.staticState.digest + ".js") :: StandaloneScriptBaseAtts
          )

        // Dynamic resources
        element(
          localName = "script",
          prefix    = xhtmlPrefix,
          uri       = XHTML_NAMESPACE_URI,
          atts      = "src" -> (Constants.FormDynamicResourcesPath + containingDocument.uuid + ".js") :: StandaloneScriptBaseAtts
        )

      } else {

        def writeContent(content: String): Unit =
          withElement("script", xhtmlPrefix, XHTML_NAMESPACE_URI, List("type" -> "text/javascript")) {
            text(escapeJavaScriptInsideScript(content))
          }

        // Scripts known statically
        val uniqueClientScripts = containingDocument.staticOps.uniqueJsScripts

        if (uniqueClientScripts.nonEmpty) {
          val sb = new StringBuilder
          writeScripts(uniqueClientScripts, sb append _)
          writeContent(sb.toString)
        }

        // Scripts known dynamically
        ScriptBuilder.findOtherScriptInvocations(containingDocument) foreach { initializationScripts =>
          writeContent(
            ScriptBuilder.buildXFormsPageLoadedServer(
              body         = initializationScripts,
              namespaceOpt = None
            )
          )
        }
        writeContent(
          ScriptBuilder.buildInitializationCall(
            jsonInitialization = buildJsonInitializationData(
              containingDocument        = containingDocument,
              rewriteResource           = externalContext.getResponse.rewriteResourceURL(_: String, UrlRewriteMode.AbsolutePathOrRelative),
              rewriteAction             = externalContext.getResponse.rewriteActionURL,
              controlsToInitialize      = containingDocument.controls.getCurrentControlTree.rootOpt map (gatherJavaScriptInitializations(_, includeValue = true)) getOrElse Nil,
              versionedResources        = isVersionedResources,
              maxInactiveIntervalMillis = XFormsStateManager.getMaxInactiveIntervalMillis(containingDocument, handlerContext.externalContext),
              sessionId                 = externalContext.getSessionOpt(create = false).map(_.getId).getOrElse("")
            ),
            contextPathOpt = externalContext.getRequest.getFirstParamAsString(Constants.EmbeddingContextParameter),
            namespaceOpt   = externalContext.getRequest.getFirstParamAsString(Constants.EmbeddingNamespaceParameter)
          )
        )
      }
    }
  }

  override def end(): Unit = {
    val xmlReceiver = handlerContext.controller.output
    xmlReceiver.endElement(uri, localname, qName)
    handlerContext.findFormattingPrefixUndeclare(formattingPrefix)
  }
}

private object XHTMLHeadHandler {

  val StandaloneScriptBaseAtts =
    List(
      "type"  -> "text/javascript",
      "class" -> "xforms-standalone-resource",
      "defer" -> "defer"
    )
}