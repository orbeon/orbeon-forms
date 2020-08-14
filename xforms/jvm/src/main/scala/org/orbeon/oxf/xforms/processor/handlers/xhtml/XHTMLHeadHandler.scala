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


import org.orbeon.oxf.externalcontext.URLRewriter._
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.processor.ScriptBuilder._
import org.orbeon.oxf.xforms.processor.XFormsAssetServer
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.xbl.XBLAssets
import org.orbeon.oxf.xforms.xbl.XBLAssets.HeadElement
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes

// Handler for `<xh:head>`
class XHTMLHeadHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsBaseHandlerXHTML(uri, localname, qName, localAtts, matched, handlerContext, repeating = false, forwarding = true) {

  import XHTMLHeadHandler._

  private var formattingPrefix: String = null

  override def start(): Unit = {

    implicit val xmlReceiver: XMLReceiver = xformsHandlerContext.getController.getOutput

    // Register control handlers on controller
    xformsHandlerContext.getController.registerHandler(
      classOf[XXFormsTextHandler].getName,
      XFormsConstants.XXFORMS_NAMESPACE_URI,
      "text",
      XHTMLBodyHandler.ANY_MATCHER
    )

    // Declare `xmlns:f`
    formattingPrefix = xformsHandlerContext.findFormattingPrefixDeclare

    // Open head element
    xmlReceiver.startElement(uri, localname, qName, attributes)

    val xhtmlPrefix = XMLUtils.prefixFromQName(qName)

    // Create prefix for combined resources if needed
    val isMinimal = XFormsProperties.isMinimalResources
    val isVersionedResources = URLRewriterUtils.isResourcesVersioned

    // Include static XForms CSS and JS
    val externalContext = xformsHandlerContext.getExternalContext

    if (containingDocument.isServeInlineResources)
      withElement("style", xhtmlPrefix, XHTML_NAMESPACE_URI, List("type" -> "text/css", "media" -> "all")) {
        text(
          """html body form.xforms-initially-hidden, html body .xforms-form .xforms-initially-hidden {
              display: none
          }"""
        )
      }

    val ops = containingDocument.getStaticOps

    val (baselineScripts, baselineStyles) = ops.baselineResources
    val (scripts, styles)                 = ops.bindingResources

    // Stylesheets

    outputCSSResources(xhtmlPrefix, isMinimal, containingDocument.getStaticState.assets, styles, baselineStyles)

    // Scripts
    locally {

      // Linked JavaScript resources
      outputJavaScriptResources(xhtmlPrefix, isMinimal, containingDocument.getStaticState.assets, scripts, baselineScripts)

      if (! containingDocument.isServeInlineResources) {

        // Static resources
        if (containingDocument.getStaticOps.uniqueJsScripts.nonEmpty)
          element(
            localName = "script",
            prefix    = xhtmlPrefix,
            uri       = XHTML_NAMESPACE_URI,
            atts      = ("src" -> (XFormsAssetServer.FormStaticResourcesPath + containingDocument.getStaticState.digest + ".js") :: StandaloneScriptBaseAtts)
          )

        // Dynamic resources
        element(
          localName = "script",
          prefix    = xhtmlPrefix,
          uri       = XHTML_NAMESPACE_URI,
          atts      = ("src" -> (XFormsAssetServer.FormDynamicResourcesPath + containingDocument.getUUID + ".js") :: StandaloneScriptBaseAtts)
        )

      } else {

        def writeContent(content: String): Unit =
          withElement("script", xhtmlPrefix, XHTML_NAMESPACE_URI, List("type" -> "text/javascript")) {
            text(escapeJavaScriptInsideScript(content))
          }

        // Scripts known statically
        val uniqueClientScripts = containingDocument.getStaticOps.uniqueJsScripts

        if (uniqueClientScripts.nonEmpty) {
          val sb = new StringBuilder
          writeScripts(uniqueClientScripts, sb append _)
          writeContent(sb.toString)
        }

        // Scripts known dynamically

        findConfigurationProperties(
          containingDocument = containingDocument,
          versionedResources = isVersionedResources,
          heartbeatDelay     = XFormsStateManager.getHeartbeatDelay(containingDocument, xformsHandlerContext.getExternalContext)
        ) foreach
          writeContent

        findOtherScriptInvocations(containingDocument) foreach
          writeContent

        List(
          buildJavaScriptInitialData(
            containingDocument   = containingDocument,
            rewriteResource      = externalContext.getResponse.rewriteResourceURL(_: String, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE),
            controlsToInitialize = containingDocument.getControls.getCurrentControlTree.rootOpt map (gatherJavaScriptInitializations(_, includeValue = true)) getOrElse Nil
          )
        ) foreach
          writeContent
      }
    }
  }

  override def end(): Unit = {
    val xmlReceiver = xformsHandlerContext.getController.getOutput
    xmlReceiver.endElement(uri, localname, qName)
    xformsHandlerContext.findFormattingPrefixUndeclare(formattingPrefix)
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

    XBLAssets.outputResources(
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

    XBLAssets.outputResources(
      outputElement = outputLinkOrStyleElement,
      builtin       = assets.css,
      headElements  = headElements,
      xblBaseline   = baselineResources,
      minimal       = minimal
    )
  }
}