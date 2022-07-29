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
import org.orbeon.oxf.xforms.processor.ScriptBuilder._
import org.orbeon.oxf.xforms.XFormsAssetPaths
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XHTMLOutput}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.xbl.XBLAssetsSupport
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.xforms.HeadElement
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

    val ops = containingDocument.staticOps

    val (baselineScripts, baselineStyles) = ops.baselineResources
    val (scripts, styles)                 = ops.bindingResources

    // Stylesheets

    outputCSSResources(xhtmlPrefix, isMinimal, containingDocument.staticState.assets, styles, baselineStyles)

    // Scripts
    locally {

      // Linked JavaScript resources
      outputJavaScriptResources(xhtmlPrefix, isMinimal, containingDocument.staticState.assets, scripts, baselineScripts)

      if (! containingDocument.isServeInlineResources) {

        // Static resources
        if (containingDocument.staticOps.uniqueJsScripts.nonEmpty)
          element(
            localName = "script",
            prefix    = xhtmlPrefix,
            uri       = XHTML_NAMESPACE_URI,
            atts      = ("src" -> (XFormsAssetPaths.FormStaticResourcesPath + containingDocument.staticState.digest + ".js") :: StandaloneScriptBaseAtts)
          )

        // Dynamic resources
        element(
          localName = "script",
          prefix    = xhtmlPrefix,
          uri       = XHTML_NAMESPACE_URI,
          atts      = ("src" -> (XFormsAssetPaths.FormDynamicResourcesPath + containingDocument.uuid + ".js") :: StandaloneScriptBaseAtts)
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

        findConfigurationProperties(
          containingDocument = containingDocument,
          versionedResources = isVersionedResources,
          heartbeatDelay     = XFormsStateManager.getHeartbeatDelay(containingDocument, handlerContext.externalContext)
        ) foreach
          writeContent

        findOtherScriptInvocations(containingDocument) foreach
          writeContent

        List(
          buildJavaScriptInitialData(
            containingDocument   = containingDocument,
            rewriteResource      = externalContext.getResponse.rewriteResourceURL(_: String, UrlRewriteMode.AbsolutePathOrRelative),
            rewriteAction        = externalContext.getResponse.rewriteActionURL,
            controlsToInitialize = containingDocument.controls.getCurrentControlTree.rootOpt map (gatherJavaScriptInitializations(_, includeValue = true)) getOrElse Nil
          )
        ) foreach
          writeContent
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

  val ScriptBaseAtts =
    List(
      "type"  -> "text/javascript"
    )

  val StandaloneScriptBaseAtts =
    List(
      "type"  -> "text/javascript",
      "class" -> "xforms-standalone-resource",
      "defer" -> "defer"
    )

  val StyleBaseAtts =
    List(
      "type"  -> "text/css",
      "media" -> "all"
    )

  val LinkBaseAtts =
    List(
      "rel"   -> "stylesheet",
      "type"  -> "text/css",
      "media" -> "all"
    )

  def valueOptToList(name: String, value: Option[String]): List[(String, String)] =
    value.toList map (name -> _)

  def outputJavaScriptResources(
    xhtmlPrefix       : String,
    minimal           : Boolean,
    assets            : XFormsAssets,
    headElements      : List[HeadElement],
    baselineResources : List[String])(implicit
    receiver          : XMLReceiver
  ): Unit = {

    def outputScriptElement(resource: Option[String], cssClass: Option[String], content: Option[String]): Unit =
      withElement(
        localName = "script",
        prefix    = xhtmlPrefix,
        uri       = XHTML_NAMESPACE_URI,
        atts      = ScriptBaseAtts ::: valueOptToList("src", resource) ::: valueOptToList("class", cssClass)
      ) {
        content foreach text
      }

    XBLAssetsSupport.outputResources(
      outputElement = outputScriptElement,
      builtin       = assets.js,
      headElements  = headElements,
      xblBaseline   = baselineResources,
      minimal       = minimal
    )
  }

  def outputCSSResources(
    xhtmlPrefix       : String,
    minimal           : Boolean,
    assets            : XFormsAssets,
    headElements      : List[HeadElement],
    baselineResources : List[String])(implicit
    receiver          : XMLReceiver
  ): Unit = {

    def outputLinkOrStyleElement(resource: Option[String], cssClass: Option[String], content: Option[String]): Unit =
      withElement(
        localName = resource match {
          case Some(_) => "link"
          case None    => "style"
        },
        prefix    = xhtmlPrefix,
        uri       = XHTML_NAMESPACE_URI,
        atts      = (resource match {
            case Some(resource) => ("href"  -> resource) :: LinkBaseAtts
            case None           => StyleBaseAtts
          }) ::: valueOptToList("class", cssClass)
      ) {
        content foreach text
      }

    XBLAssetsSupport.outputResources(
      outputElement = outputLinkOrStyleElement,
      builtin       = assets.css,
      headElements  = headElements,
      xblBaseline   = baselineResources,
      minimal       = minimal
    )
  }
}