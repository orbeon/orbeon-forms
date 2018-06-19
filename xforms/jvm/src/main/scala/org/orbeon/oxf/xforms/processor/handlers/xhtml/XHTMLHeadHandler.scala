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
import org.orbeon.oxf.xforms.processor.XFormsResourceServer
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.xbl.XBLAssets
import org.orbeon.oxf.xforms.xbl.XBLAssets.HeadElement
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

// Handler for <xh:head>
class XHTMLHeadHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsBaseHandlerXHTML(uri, localname, qName, attributes, matched, handlerContext, false, true) {

  private var formattingPrefix: String = null

  override def start(): Unit = {

    val xmlReceiver = xformsHandlerContext.getController.getOutput

    // Register control handlers on controller
    xformsHandlerContext.getController.registerHandler(
      classOf[XXFormsTextHandler].getName,
      XFormsConstants.XXFORMS_NAMESPACE_URI,
      "text",
      XHTMLBodyHandler.ANY_MATCHER
    )

    // Declare xmlns:f
    formattingPrefix = xformsHandlerContext.findFormattingPrefixDeclare

    // Open head element
    xmlReceiver.startElement(uri, localname, qName, attributes)

    implicit val helper = new XMLReceiverHelper(xmlReceiver)
    val xhtmlPrefix = XMLUtils.prefixFromQName(qName)

    // Create prefix for combined resources if needed
    val isMinimal = XFormsProperties.isMinimalResources
    val isVersionedResources = URLRewriterUtils.isResourcesVersioned

    // Include static XForms CSS and JS
    val externalContext = xformsHandlerContext.getExternalContext

    if (containingDocument.isServeInlineResources) {
      helper.startElement(xhtmlPrefix, XHTML_NAMESPACE_URI, "style", Array("type", "text/css", "media", "all"))

      val content =
        """html body form.xforms-initially-hidden, html body .xforms-form .xforms-initially-hidden {
          |    display: none
          |}"""

      helper.text(content)
      helper.endElement()
    }

    val ops = containingDocument.getStaticOps

    val (baselineScripts, baselineStyles) = ops.baselineResources
    val (scripts, styles)                 = ops.bindingResources

    // Stylesheets
    val attributesImpl = new AttributesImpl

    outputCSSResources(xhtmlPrefix, isMinimal, attributesImpl, containingDocument.getStaticState.assets, styles, baselineStyles)

    // Scripts
    locally {

      // Linked JavaScript resources
      outputJavaScriptResources(xhtmlPrefix, isMinimal, attributesImpl, containingDocument.getStaticState.assets, scripts, baselineScripts)

      if (! containingDocument.isServeInlineResources) {

        // Static resources
        if (containingDocument.getStaticOps.uniqueJsScripts.nonEmpty)
          helper.element(
            xhtmlPrefix,
            XHTML_NAMESPACE_URI,
            "script",
            Array(
              "type", "text/javascript",
              "class", "xforms-standalone-resource",
              "defer", "defer",
              "src", XFormsResourceServer.FormStaticResourcesPath + containingDocument.getStaticState.digest + ".js"
            )
          )

        // Dynamic resources
        helper.element(
          xhtmlPrefix,
          XHTML_NAMESPACE_URI,
          "script",
          Array(
            "type", "text/javascript",
            "class", "xforms-standalone-resource",
            "defer", "defer",
            "src", XFormsResourceServer.FormDynamicResourcesPath + containingDocument.getUUID + ".js"
          )
        )

      } else {

        def writeContent(content: String): Unit = {
          helper.startElement(xhtmlPrefix, XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))
          helper.text(escapeJavaScriptInsideScript(content))
          helper.endElement()
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

        findScriptInvocations(containingDocument) foreach
          writeContent

        findJavaScriptInitialData(
          containingDocument   = containingDocument,
          rewriteResource      = externalContext.getResponse.rewriteResourceURL(_: String, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE),
          controlsToInitialize = containingDocument.getControls.getCurrentControlTree.rootOpt map gatherJavaScriptInitializations getOrElse Nil
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

  // Output an element
  private def outputElement(
    xhtmlPrefix       : String,
    attributesImpl    : AttributesImpl,
    getElementDetails : (Option[String], Option[String]) ⇒ (String, Array[String]))(
    resource          : Option[String],
    cssClass          : Option[String],
    content           : Option[String])(implicit
    helper            : XMLReceiverHelper
  ): Unit = {

    val (elementName, attributes) = getElementDetails(resource, cssClass)

    attributesImpl.clear()
    XMLReceiverHelper.populateAttributes(attributesImpl, attributes)
    helper.startElement(xhtmlPrefix, XHTML_NAMESPACE_URI, elementName, attributesImpl)
    // Output content only if present
    content foreach helper.text
    helper.endElement()
  }

  private def outputCSSResources(
    xhtmlPrefix       : String,
    minimal           : Boolean,
    attributesImpl    : AttributesImpl,
    assets            : XFormsAssets,
    headElements      : List[HeadElement],
    baselineResources : List[String])(implicit
    helper            : XMLReceiverHelper
  ): Unit = {

    // Function to output either a <link> or <style> element
    def outputCSSElement =
      outputElement(
        xhtmlPrefix,
        attributesImpl,
        (resource, cssClass) ⇒ resource match {
          case Some(resource) ⇒
            ("link", Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all", "class", cssClass.orNull))
          case None ⇒
            ("style", Array("type", "text/css", "media", "all", "class", cssClass.orNull))
        }
      )(_, _, _)

    // Output all CSS
    XBLAssets.outputResources(
      outputCSSElement,
      assets.css,
      headElements,
      baselineResources,
      minimal
    )
  }

  private def outputJavaScriptResources(
    xhtmlPrefix       : String,
    minimal           : Boolean,
    attributesImpl    : AttributesImpl,
    assets            : XFormsAssets,
    headElements      : List[HeadElement],
    baselineResources : List[String])(implicit
    helper            : XMLReceiverHelper
  ): Unit = {

    // Function to output a <script> element
    def outputJSElement =
      outputElement(
        xhtmlPrefix,
        attributesImpl,
        (resource, cssClass) ⇒ ("script", Array("type", "text/javascript", "src", resource.orNull, "class", cssClass.orNull))
      )(_, _, _)

    // Output all JS
    XBLAssets.outputResources(
      outputJSElement,
      assets.js,
      headElements,
      baselineResources,
      minimal
    )
  }
}
