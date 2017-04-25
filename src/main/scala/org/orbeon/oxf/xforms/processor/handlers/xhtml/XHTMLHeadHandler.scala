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


import java.{lang ⇒ jl}

import org.orbeon.oxf.externalcontext.URLRewriter._
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.util.URLRewriterUtils._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms.XFormsUtils.{escapeJavaScript, namespaceId}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl, XFormsControl, XFormsValueComponentControl}
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xforms.xbl.XBLAssets
import org.orbeon.oxf.xforms.xbl.XBLAssets.HeadElement
import org.orbeon.oxf.xml.XMLConstants.XHTML_NAMESPACE_URI
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

// Handler for <xh:head>
class XHTMLHeadHandler extends XFormsBaseHandlerXHTML(false, true) {

  import XHTMLHeadHandler._

  private var formattingPrefix: String = null

  override def start(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

    val xmlReceiver = handlerContext.getController.getOutput

    // Register control handlers on controller
    handlerContext.getController.registerHandler(
      classOf[XXFormsTextHandler].getName,
      XFormsConstants.XXFORMS_NAMESPACE_URI,
      "text",
      XHTMLBodyHandler.ANY_MATCHER
    )

    // Declare xmlns:f
    formattingPrefix = handlerContext.findFormattingPrefixDeclare

    // Open head element
    xmlReceiver.startElement(uri, localname, qName, attributes)

    implicit val helper = new XMLReceiverHelper(xmlReceiver)
    val xhtmlPrefix = XMLUtils.prefixFromQName(qName)

    // Create prefix for combined resources if needed
    val isMinimal = XFormsProperties.isMinimalResources
    val isVersionedResources = URLRewriterUtils.isResourcesVersioned

    // Include static XForms CSS and JS
    val requestPath = handlerContext.getExternalContext.getRequest.getRequestPath

    helper.element("", XMLNames.XIncludeURI, "include",
      Array(
        "href", XHTMLBodyHandler.getIncludedResourceURL(requestPath, "static-xforms-css-js.xml"),
        "fixup-xml-base", "false"
      )
    )

    val ops = containingDocument.getStaticOps

    val (baselineScripts, baselineStyles) = ops.baselineResources
    val (scripts, styles)                 = ops.bindingResources

    // Stylesheets
    val attributesImpl = new AttributesImpl



    outputCSSResources(xhtmlPrefix, isMinimal, attributesImpl, containingDocument.getStaticState.assets, styles, baselineStyles)

    // Scripts
    if (! handlerContext.isNoScript) {

      // Main JavaScript resources
      outputJavaScriptResources(xhtmlPrefix, isMinimal, attributesImpl, containingDocument.getStaticState.assets, scripts, baselineScripts)

      // Configuration properties
      outputConfigurationProperties(xhtmlPrefix, isVersionedResources)

      outputScriptDeclarations(xhtmlPrefix)

      outputJavaScriptInitialData(
        xhtmlPrefix,
        containingDocument.getControls.getCurrentControlTree.rootOpt map gatherJavaScriptInitializations getOrElse (Nil, Nil)
      )
    }
  }

  override def end(uri: String, localname: String, qName: String): Unit = {
    val xmlReceiver = handlerContext.getController.getOutput
    xmlReceiver.endElement(uri, localname, qName)
    handlerContext.findFormattingPrefixUndeclare(formattingPrefix)
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

  private def outputConfigurationProperties(
    xhtmlPrefix        : String,
    versionedResources : Boolean)(implicit
    helper             : XMLReceiverHelper
  ): Unit = {

    // Gather all static properties that need to be sent to the client
    val staticProperties = containingDocument.getStaticState.clientNonDefaultProperties

    val dynamicProperties = {

      def dynamicProperty(p: ⇒ Boolean, name: String, value: Any) =
        if (p) Some(name → value) else None

      // Heartbeat delay is dynamic because it depends on session duration
      def heartbeat = {
        val propertyDefinition =
          getPropertyDefinition(SESSION_HEARTBEAT_DELAY_PROPERTY)

        val heartbeatDelay =
          XFormsStateManager.getHeartbeatDelay(containingDocument, handlerContext.getExternalContext)

        dynamicProperty(heartbeatDelay != propertyDefinition.defaultValue.asInstanceOf[jl.Integer],
          SESSION_HEARTBEAT_DELAY_PROPERTY, heartbeatDelay)
      }

      // Help events are dynamic because they depend on whether the xforms-help event is used
      // TODO: Better way to enable/disable xforms-help event support, maybe static analysis of event handlers?
      def help = dynamicProperty(
        containingDocument.getStaticOps.hasHandlerForEvent(XFormsEvents.XFORMS_HELP, includeAllEvents = false),
        HELP_HANDLER_PROPERTY,
        true
      )

      // Whether resources are versioned
      def resourcesVersioned = dynamicProperty(
        versionedResources != RESOURCES_VERSIONED_DEFAULT,
        RESOURCES_VERSIONED_PROPERTY,
        versionedResources
      )

      // Application version is not an XForms property but we want to expose it on the client
      def resourcesVersion = dynamicProperty(
        versionedResources && (getApplicationResourceVersion ne null),
        RESOURCES_VERSION_NUMBER_PROPERTY,
        getApplicationResourceVersion
      )

      // Gather all dynamic properties that are defined
      List(heartbeat, help, resourcesVersioned, resourcesVersion).flatten
    }

    // combine all static and dynamic properties
    val clientProperties = staticProperties ++ dynamicProperties

    if (clientProperties.nonEmpty) {

      val sb = new StringBuilder

      for ((propertyName, propertyValue) ← clientProperties) {
        if (sb.isEmpty) {
          // First iteration
          sb append "var opsXFormsProperties = {"
          helper.startElement(xhtmlPrefix, XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))
        } else
          sb append ','

        sb append '"'
        sb append propertyName
        sb append "\":"

        propertyValue match {
          case s: String ⇒
            sb append '"'
            sb append s
            sb append '"'
          case _ ⇒
            sb append propertyValue.toString
        }
      }

      sb append "};"
      helper.text(sb.toString)
      helper.endElement()
    }
  }

  private def outputScriptDeclarations(xhtmlPrefix: String)(implicit helper: XMLReceiverHelper): Unit = {

    val uniqueClientScripts = containingDocument.getStaticOps.uniqueJsScripts

    val errorsToShow      = containingDocument.getServerErrors.asScala
    val scriptInvocations      = containingDocument.getScriptsToRun.asScala
    val focusElementIdOpt = Option(containingDocument.getControls.getFocusedControl) map (_.getEffectiveId)
    val messagesToRun     = containingDocument.getMessagesToRun.asScala filter (_.getLevel == "modal")

    val dialogsToOpen =
      for {
        dialogControl ← containingDocument.getControls.getCurrentControlTree.getDialogControls
        if dialogControl.isVisible
      } yield
        dialogControl

    val javascriptLoads =
      containingDocument.getLoadsToRun.asScala filter (_.resource.startsWith("javascript:"))

    val hasScriptDefinitions =
      uniqueClientScripts.nonEmpty

    val mustRunAnyScripts =
      errorsToShow.nonEmpty       ||
      scriptInvocations.nonEmpty  ||
      focusElementIdOpt.isDefined ||
      messagesToRun.nonEmpty      ||
      dialogsToOpen.nonEmpty      ||
      javascriptLoads.nonEmpty

    if (hasScriptDefinitions || mustRunAnyScripts) {
      helper.startElement(xhtmlPrefix, XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))

      if (hasScriptDefinitions)
        outputScripts(uniqueClientScripts)

      if (mustRunAnyScripts) {
        val sb = new StringBuilder("\nfunction xformsPageLoadedServer() { ")

        // NOTE: The order of script actions vs. `javascript:` loads should be preserved. It is not currently.

        // Initial script actions executions if present
        for (script ← scriptInvocations) {
          val name     = script.script.shared.clientName
          val target   = namespaceId(containingDocument, script.targetEffectiveId)
          val observer = namespaceId(containingDocument, script.observerEffectiveId)

          val paramsString =
          if (script.paramValues.nonEmpty)
            script.paramValues map ('"' + escapeJavaScript(_) + '"') mkString (",", ",", "")
          else
            ""

          sb append s"""ORBEON.xforms.server.Server.callUserScript("$name","$target","$observer"$paramsString);"""
        }

        // javascript: loads
        for (load ← javascriptLoads) {
          val body = escapeJavaScript(load.resource.substring("javascript:".size))
          sb append s"""(function(){$body})();"""
        }

        // Initial modal xf:message to run if present
        if (messagesToRun.nonEmpty) {
          val quotedMessages = messagesToRun map (m ⇒ s""""${escapeJavaScript(m.getMessage)}"""")
          quotedMessages.addString(sb, "ORBEON.xforms.action.Message.showMessages([", ",", "]);")
        }

        // Initial dialogs to open
        for (dialogControl ← dialogsToOpen) {
          val id       = namespaceId(containingDocument, dialogControl.getEffectiveId)
          val neighbor = (
            dialogControl.neighborControlId
            map (n ⇒ s""""${namespaceId(containingDocument, n)}"""")
            getOrElse "null"
          )

          sb append s"""ORBEON.xforms.Controls.showDialog("$id", $neighbor);"""
        }

        // Initial setfocus if present
        // Seems reasonable to do this after dialogs as focus might be within a dialog
        focusElementIdOpt foreach { id ⇒
          sb append s"""ORBEON.xforms.Controls.setFocus("${namespaceId(containingDocument, id)}");"""
        }

        // Initial errors
        if (errorsToShow.nonEmpty) {

          val title   = "Non-fatal error"
          val details = XFormsUtils.escapeJavaScript(ServerError.errorsAsHTMLElem(errorsToShow).toString)
          val formId  = XFormsUtils.getFormId(containingDocument)

          sb append s"""ORBEON.xforms.server.AjaxServer.showError("$title", "$details", "$formId");"""
        }

        sb append " }"

        helper.text(sb.toString)
      }
      helper.endElement()
    }
  }

  private def outputJavaScriptInitialData(
    xhtmlPrefix               : String,
    javaScriptInitializations : (List[String], List[(String, Option[String])]))(implicit
    helper                    : XMLReceiverHelper
  ): Unit = {

    helper.startElement(xhtmlPrefix, XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))

    val (placeholders, controlsToInitialize) = javaScriptInitializations

    // Produce JSON output
    val hasPaths                = true
    val hasPlaceholders         = placeholders.nonEmpty
    val hasControlsToInitialize = controlsToInitialize.nonEmpty
    val hasKeyListeners         = containingDocument.getStaticOps.keypressHandlers.nonEmpty
    val hasServerEvents         = containingDocument.delayedEvents.nonEmpty
    val outputInitData          = hasPaths || hasPlaceholders || hasControlsToInitialize || hasKeyListeners || hasServerEvents

    if (outputInitData) {
      val sb = new jl.StringBuilder("var orbeonInitData = orbeonInitData || {}; orbeonInitData[\"")
      sb.append(XFormsUtils.getFormId(containingDocument))
      sb.append("\"] = {")

      val rewriteResource =
        handlerContext.getExternalContext.getResponse.rewriteResourceURL(
          _: String,
          REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE
        )

      // Output path information
      if (hasPaths) {
        sb.append("\"paths\":{")
        sb.append("\"xforms-server\": \"")
        sb.append(rewriteResource("/xforms-server"))
        sb.append("\",\"xforms-server-upload\": \"")
        sb.append(rewriteResource("/xforms-server/upload"))

        // NOTE: This is to handle the minimal date picker. Because the client must re-generate markup when
        // the control becomes relevant or changes type, it needs the image URL, and that URL must be rewritten
        // for use in portlets. This should ideally be handled by a template and/or the server should provide
        // the markup directly when needed.
        val calendarImage = "/ops/images/xforms/calendar.png"
        val rewrittenCalendarImage = rewriteResource(calendarImage)
        sb.append("\",\"calendar-image\": \"")
        sb.append(rewrittenCalendarImage)
        sb.append('"')
        sb.append('}')
      }

      if (hasPlaceholders || hasControlsToInitialize)
        buildJavaScriptInitializations(containingDocument, hasPaths, javaScriptInitializations, sb)

      // Output key listener information
      if (hasKeyListeners) {
        if (hasPaths || hasPlaceholders || hasControlsToInitialize)
          sb.append(',')
        sb.append("\"keylisteners\":[")
        var first = true
        for (handler ← containingDocument.getStaticOps.keypressHandlers) {
          if (! first)
            sb.append(',')
          for (observer ← handler.observersPrefixedIds) {
            sb.append('{')
            sb.append("\"observer\":\"")
            sb.append(observer)
            sb.append('"')
            if (handler.getKeyModifiers ne null) {
              sb.append(",\"modifier\":\"")
              sb.append(handler.getKeyModifiers)
              sb.append('"')
            }
            if (handler.getKeyText ne null) {
              sb.append(",\"text\":\"")
              sb.append(handler.getKeyText)
              sb.append('"')
            }
            sb.append('}')
            first = false
          }
        }
        sb.append(']')
      }

      // Output server events
      if (hasServerEvents) {
        if (hasPaths || hasPlaceholders || hasControlsToInitialize || hasKeyListeners)
          sb.append(',')
        sb.append("\"server-events\":[")
        val currentTime = System.currentTimeMillis
        var first = true
        for (delayedEvent ← containingDocument.delayedEvents) {
          if (! first)
            sb.append(',')
          delayedEvent.writeAsJSON(sb, currentTime)
          first = false
        }
        sb.append(']')
      }
      sb.append("};")
      helper.text(sb.toString)
    }
    helper.endElement()
  }
}

object XHTMLHeadHandler {

  def quoteString(s: String) =
    s""""${escapeJavaScript(s)}""""

  def outputScripts(shareableScripts: Iterable[ShareableScript])(implicit helper: XMLReceiverHelper) =
    for (shareableScript ← shareableScripts) {

      val paramsString =
        if (shareableScript.paramNames.nonEmpty)
          shareableScript.paramNames mkString (",", ",", "")
        else
          ""

      helper.text(s"\nfunction ${shareableScript.clientName}(event$paramsString) {\n")
      helper.text(shareableScript.body)
      helper.text("}\n")
    }

  def buildJavaScriptInitializations(
    containingDocument        : XFormsContainingDocument,
    prependComma              : Boolean,
    javaScriptInitializations : (List[String], List[(String, Option[String])]),
    sb                        : jl.StringBuilder
  ): Unit = {

    val (placeholders, controlsToInitialize) = javaScriptInitializations

    if (placeholders.nonEmpty) {
      if (prependComma)
        sb.append(',')
      sb.append("\"placeholders\":[")
      val i = placeholders.iterator
      while (i.hasNext) {
        val controlId = i.next()
        sb.append(quoteString(controlId))
        if (i.hasNext)
          sb.append(',')
      }
      sb.append(']')
    }

    if (controlsToInitialize.nonEmpty) {
      if (prependComma || placeholders.nonEmpty)
        sb.append(',')
      sb.append("\"controls\":[")
      val it = controlsToInitialize.iterator
      while (it.hasNext) {
        val (controlId, valueOpt) = it.next()

        val namespacedId = namespaceId(containingDocument, controlId)

        valueOpt match {
          case Some(value) ⇒ sb.append(s"""{"id":${quoteString(namespacedId)},"value":${quoteString(value)}}""")
          case None        ⇒ sb.append(s"""{"id":${quoteString(namespacedId)}}""")
        }

        if (it.hasNext)
          sb.append(',')
      }
      sb.append(']')
    }
  }

  def gatherJavaScriptInitializations(startControl: XFormsControl): (List[String], List[(String, Option[String])]) = {

    val placeholders         = ListBuffer[String]()
    val controlsToInitialize = ListBuffer[(String, Option[String])]()

    def addControlToInitialize(effectiveId: String, value: Option[String]) =
      controlsToInitialize += effectiveId → value

    Controls.ControlsIterator(startControl, includeSelf = false) foreach {
      case c: XFormsInputControl ⇒
        // Special case of placeholders (useful only for IE8 and IE9)
        XFormsInputControl.placeholderInfo(c.containingDocument, c.staticControl, c) foreach { placeHolderInfo ⇒
          placeholders += c.getEffectiveId
        }
      case c: XFormsValueComponentControl ⇒
        if (c.isRelevant) {
          val abstractBinding = c.staticControl.binding.abstractBinding
          if (abstractBinding.modeJavaScriptLifecycle)
            addControlToInitialize(
              c.getEffectiveId,
              if (abstractBinding.modeExternalValue)
                c.externalValueOpt
              else
                None
            )
        }
      case c: XFormsComponentControl ⇒
        if (c.isRelevant && c.staticControl.binding.abstractBinding.modeJavaScriptLifecycle)
          addControlToInitialize(c.getEffectiveId, None)
      case c ⇒
        // Legacy JavaScript initialization
        // As of 2016-08-04: xxf:dialog, xf:select1[appearance = compact], xf:range
        if (c.hasJavaScriptInitialization && ! c.isStaticReadonly)
          addControlToInitialize(c.getEffectiveId, None)
    }

    placeholders.result → controlsToInitialize.result
  }
}