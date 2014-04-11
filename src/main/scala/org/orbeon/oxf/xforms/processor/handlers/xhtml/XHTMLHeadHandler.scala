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

import collection.immutable.Seq
import collection.mutable
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.util.URLRewriterUtils
import org.orbeon.oxf.util.URLRewriterUtils._
import org.orbeon.oxf.xforms.XFormsProperties._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.control.Controls
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.processor.XFormsFeatures
import org.orbeon.oxf.xforms.xbl.XBLResources
import org.orbeon.oxf.xml._
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl
import scala.collection.JavaConverters._
import state.XFormsStateManager

// Handler for <xh:head>
class XHTMLHeadHandler extends XFormsBaseHandlerXHTML(false, true) {

    import XHTMLHeadHandler._

    private var formattingPrefix: String = null

    override def start(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {

        val xmlReceiver = handlerContext.getController.getOutput

        // Register control handlers on controller
        locally {
            val controller = handlerContext.getController
            controller.registerHandler(classOf[XXFormsTextHandler].getName, XFormsConstants.XXFORMS_NAMESPACE_URI, "text", XHTMLBodyHandler.ANY_MATCHER)
        }

        // Declare xmlns:f
        formattingPrefix = handlerContext.findFormattingPrefixDeclare

        // Open head element
        xmlReceiver.startElement(uri, localname, qName, attributes)

        implicit val helper = new XMLReceiverHelper(xmlReceiver)
        val xhtmlPrefix = XMLUtils.prefixFromQName(qName)

        // Create prefix for combined resources if needed
        val isMinimal = XFormsProperties.isMinimalResources
        val isVersionedResources = URLRewriterUtils.isResourcesVersioned

        // Include static XForms CSS
        val requestPath = handlerContext.getExternalContext.getRequest.getRequestPath

        helper.element("", XMLConstants.XINCLUDE_URI, "include",
            Array(
                "href", XHTMLBodyHandler.getIncludedResourceURL(requestPath, "static-xforms-css.xml"),
                "fixup-xml-base", "false"
            )
        )

        // Stylesheets
        val attributesImpl = new AttributesImpl
        outputCSSResources(xhtmlPrefix, isMinimal, attributesImpl)

        // Scripts
        if (! handlerContext.isNoScript && ! XFormsProperties.isReadonly(containingDocument)) {

            // Main JavaScript resources
            outputJavaScriptResources(xhtmlPrefix, isMinimal, attributesImpl)

            // Configuration properties
            outputConfigurationProperties(xhtmlPrefix, isVersionedResources)

            outputScriptDeclarations(xhtmlPrefix)

            // Store information about "special" controls that need JavaScript initialization
            // Gather information about appearances of controls which use Script
            // Map<String controlName, Map<String appearanceOrMediatype, List<String effectiveId>>>
            outputJavaScriptInitialData(xhtmlPrefix, gatherJavascriptControls(containingDocument))
        }
    }

    override def end(uri: String, localname: String, qName: String): Unit = {
        val xmlReceiver = handlerContext.getController.getOutput
        xmlReceiver.endElement(uri, localname, qName)
        handlerContext.findFormattingPrefixUndeclare(formattingPrefix)
    }

    // Output an element
    private def outputElement(
            xhtmlPrefix: String,
            attributesImpl: AttributesImpl,
            getElementDetails: (Option[String], Option[String]) ⇒ (String, Array[String])
        )(
            resource: Option[String],
            cssClass: Option[String],
            content: Option[String]
        )(implicit helper: XMLReceiverHelper): Unit = {

        val (elementName, attributes) = getElementDetails(resource, cssClass)

        attributesImpl.clear()
        XMLReceiverHelper.populateAttributes(attributesImpl, attributes)
        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, elementName, attributesImpl)
        // output content only if present
        content foreach helper.text
        helper.endElement()
    }

    private def outputCSSResources(xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl)(implicit helper: XMLReceiverHelper): Unit = {

        // Function to output either a <link> or <style> element
        def outputCSSElement = outputElement(xhtmlPrefix, attributesImpl,
            (resource, cssClass) ⇒ resource match {
                case Some(resource) ⇒ ("link", Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all", "class", cssClass.orNull))
                case None ⇒ ("style", Array("type", "text/css", "media", "all", "class", cssClass.orNull))
            })(_, _, _)

        def getCSSResources(useDoc: Boolean) = {
            val ops = if (useDoc) containingDocument.getStaticOps else null
            XFormsFeatures.getCSSResources(ops).asScala
        }

        // Output all CSS
        XBLResources.outputResources(outputCSSElement,
            getCSSResources,
            containingDocument.getStaticOps.getXBLStyles,
            XBLResources.baselineResources._2,
            minimal)
    }

    private def outputJavaScriptResources(xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl)(implicit helper: XMLReceiverHelper): Unit = {

        // Function to output either a <script> element
        def outputJSElement = outputElement(xhtmlPrefix, attributesImpl,
            (resource, cssClass) ⇒ ("script", Array("type", "text/javascript", "src", resource.orNull, "class", cssClass.orNull)))(_, _, _)

        def getJavaScriptResources(useDoc: Boolean) = {
            val ops = if (useDoc) containingDocument.getStaticOps else null
            XFormsFeatures.getJavaScriptResources(ops).asScala
        }

        // Output all JS
        XBLResources.outputResources(outputJSElement,
            getJavaScriptResources,
            containingDocument.getStaticOps.getXBLScripts,
            XBLResources.baselineResources._1,
            minimal)
    }

    private def outputConfigurationProperties(xhtmlPrefix: String, versionedResources: Boolean)(implicit helper: XMLReceiverHelper): Unit = {

        // Gather all static properties that need to be sent to the client
        val staticProperties = containingDocument.getStaticState.getNonDefaultProperties filter
            { case (propertyName, _) ⇒ getPropertyDefinition(propertyName).isPropagateToClient }

        val dynamicProperties = {

            def dynamicProperty(p: ⇒ Boolean, name: String, value: Any) =
                if (p) Some(name → value) else None

            // Heartbeat delay is dynamic because it depends on session duration
            def heartbeat = {
                val propertyDefinition = getPropertyDefinition(SESSION_HEARTBEAT_DELAY_PROPERTY)
                val heartbeatDelay = XFormsStateManager.getHeartbeatDelay(containingDocument, handlerContext.getExternalContext)

                dynamicProperty(heartbeatDelay != propertyDefinition.defaultValue.asInstanceOf[java.lang.Integer],
                    SESSION_HEARTBEAT_DELAY_PROPERTY, heartbeatDelay)
            }

            // Help events are dynamic because they depend on whether the xforms-help event is used
            // TODO: Need better way to enable/disable xforms-help event support, probably better static analysis of event handlers
            def help = dynamicProperty(
                containingDocument.getStaticOps.hasHandlerForEvent(XFormsEvents.XFORMS_HELP, includeAllEvents = false),
                HELP_HANDLER_PROPERTY,
                true)

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
            Seq(heartbeat, help, resourcesVersioned, resourcesVersion).flatten
        }

        // combine all static and dynamic properties
        val clientProperties = staticProperties ++ dynamicProperties

        if (clientProperties nonEmpty) {

            val sb = new StringBuilder

            for ((propertyName, propertyValue) ← clientProperties) {
                if (sb isEmpty) {
                    // First iteration
                    sb append "var opsXFormsProperties = {"
                    helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))
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

        val uniqueClientScripts = containingDocument.getStaticOps.uniqueClientScripts

        val scriptsToRun        = containingDocument.getScriptsToRun.asScala
        val focusElementIdOpt   = Option(containingDocument.getControls.getFocusedControl) map (_.getEffectiveId)
        val messagesToRun       = containingDocument.getMessagesToRun.asScala

        val dialogsToOpen  = {
            val dialogsMap =
                Option(containingDocument.getControls.getCurrentControlTree.getDialogControls) map (_.asScala) getOrElse Map()

            for {
                control       ← dialogsMap.values
                dialogControl = control.asInstanceOf[XXFormsDialogControl]
                if dialogControl.isVisible
            } yield
                dialogControl
        }

        val javascriptLoads =
            containingDocument.getLoadsToRun.asScala filter (_.getResource.startsWith("javascript:"))

        val hasScriptDefinitions =
            uniqueClientScripts.nonEmpty

        val mustRunAnyScripts =
            scriptsToRun.nonEmpty || focusElementIdOpt.isDefined || messagesToRun.nonEmpty || dialogsToOpen.nonEmpty || javascriptLoads.nonEmpty

        if (hasScriptDefinitions || mustRunAnyScripts) {
            helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))

            if (hasScriptDefinitions)
                outputScripts(uniqueClientScripts)

            if (mustRunAnyScripts) {
                val sb = new StringBuilder("\nfunction xformsPageLoadedServer() { ")

                // Initial xxf:script executions if present
                for (script ← scriptsToRun) {
                    sb.append("ORBEON.xforms.server.Server.callUserScript(\"")
                    sb.append(script.functionName)
                    sb.append("\",\"")
                    sb.append(XFormsUtils.namespaceId(containingDocument, script.targetEffectiveId))
                    sb.append("\",\"")
                    sb.append(XFormsUtils.namespaceId(containingDocument, script.observerEffectiveId))
                    sb.append("\");")
                }

                // javascript: loads
                for (load ← javascriptLoads) {
                    val body = XFormsUtils.escapeJavaScript(load.getResource.substring("javascript:".size))
                    sb.append(s"""(function(){$body})();""")
                }

                // Initial xf:message to run if present
                if (messagesToRun.nonEmpty) {
                    var foundModalMessage = false
                    for (message ← messagesToRun) {
                        if ("modal" == message.getLevel) {
                            if (foundModalMessage) {
                                sb.append(", ")
                            } else {
                                foundModalMessage = true
                                sb.append("ORBEON.xforms.action.Message.showMessages([")
                            }
                            sb.append("\"")
                            sb.append(XFormsUtils.escapeJavaScript(message.getMessage))
                            sb.append("\"")
                        }
                    }
                    if (foundModalMessage)
                        sb.append("]);")
                }

                // Initial dialogs to open
                for (dialogControl ← dialogsToOpen) {
                    sb.append("ORBEON.xforms.Controls.showDialog(\"")
                    sb.append(XFormsUtils.namespaceId(containingDocument, dialogControl.getEffectiveId))
                    sb.append("\", ")
                    if (dialogControl.getNeighborControlId ne null) {
                        sb.append('"')
                        sb.append(XFormsUtils.namespaceId(containingDocument, dialogControl.getNeighborControlId))
                        sb.append('"')
                    } else {
                        sb.append("null")
                    }
                    sb.append(");")
                }

                // Initial setfocus if present
                // Seems reasonable to do this after dialogs as focus might be within a dialog
                focusElementIdOpt foreach { focusElementId ⇒
                    sb.append("ORBEON.xforms.Controls.setFocus(\"")
                    sb.append(XFormsUtils.namespaceId(containingDocument, focusElementId))
                    sb.append("\");")
                }
                sb.append(" }")
                helper.text(sb.toString)
            }
            helper.endElement()
        }
    }

    private def outputJavaScriptInitialData(
            xhtmlPrefix: String,
            javaScriptControlsAppearancesMap: collection.Map[String, collection.Map[String, collection.Seq[String]]]
        )(implicit helper: XMLReceiverHelper): Unit = {

        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", Array("type", "text/javascript"))

        // Produce JSON output
        val hasPaths = true
        val hasInitControls = javaScriptControlsAppearancesMap.nonEmpty
        val hasKeyListeners = containingDocument.getStaticOps.keypressHandlers.nonEmpty
        val hasServerEvents = {
            val delayedEvents = containingDocument.getDelayedEvents
            delayedEvents.asScala.nonEmpty
        }
        val outputInitData = hasPaths || hasInitControls || hasKeyListeners || hasServerEvents

        if (outputInitData) {
            val sb = new java.lang.StringBuilder("var orbeonInitData = orbeonInitData || {}; orbeonInitData[\"")
            sb.append(XFormsUtils.getFormId(containingDocument))
            sb.append("\"] = {")

            // Output path information
            if (hasPaths) {
                sb.append("\"paths\":{")
                sb.append("\"xforms-server\": \"")
                sb.append(handlerContext.getExternalContext.getResponse.rewriteResourceURL("/xforms-server", URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE))

                // NOTE: This is to handle the minimal date picker. Because the client must re-generate markup when
                // the control becomes relevant or changes type, it needs the image URL, and that URL must be rewritten
                // for use in portlets. This should ideally be handled by a template and/or the server should provide
                // the markup directly when needed.
                val calendarImage = "/ops/images/xforms/calendar.png"
                val rewrittenCalendarImage = handlerContext.getExternalContext.getResponse.rewriteResourceURL(calendarImage, URLRewriter.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE)
                sb.append("\",\"calendar-image\": \"")
                sb.append(rewrittenCalendarImage)
                sb.append('"')
                sb.append('}')
            }

            // Output controls initialization
            if (hasInitControls) {
                if (hasPaths)
                    sb.append(',')
                sb.append("\"controls\":{")
                locally {
                    val i = javaScriptControlsAppearancesMap.iterator
                    while (i.hasNext) {
                        val (controlName, controlMap) = i.next()
                        sb.append("\"")
                        sb.append(controlName)
                        sb.append("\":{")
                        locally {
                            val j = controlMap.iterator
                            while (j.hasNext) {
                                val (controlAppearance, idsForAppearanceList) = j.next()
                                sb.append('"')
                                sb.append(if (controlAppearance ne null) controlAppearance else "")
                                sb.append("\":[")
                                locally {
                                    val k = idsForAppearanceList.iterator
                                    while (k.hasNext) {
                                        val controlId = k.next()
                                        sb.append('"')
                                        sb.append(controlId)
                                        sb.append('"')
                                        if (k.hasNext)
                                            sb.append(',')
                                    }
                                }
                                sb.append(']')
                                if (j.hasNext)
                                    sb.append(',')
                            }
                        }
                        sb.append("}")
                        if (i.hasNext)
                            sb.append(',')
                    }
                }
                sb.append('}')
            }

            // Output key listener information
            if (hasKeyListeners) {
                if (hasPaths || hasInitControls)
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
                if (hasPaths || hasInitControls || hasKeyListeners)
                    sb.append(',')
                sb.append("\"server-events\":[")
                val currentTime = System.currentTimeMillis
                var first = true
                for (delayedEvent ← containingDocument.getDelayedEvents.asScala) {
                    if (! first)
                        sb.append(',')
                    delayedEvent.toJSON(sb, currentTime)
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

    def outputScripts(scripts: Iterable[(String, String)])(implicit helper: XMLReceiverHelper) =
        for ((clientName, body) ← scripts) {
            helper.text("\nfunction " + clientName + "(event) {\n")
            helper.text(body)
            helper.text("}\n")
        }

    def gatherJavascriptControls(containingDocument: XFormsContainingDocument): collection.Map[String, collection.Map[String, collection.Seq[String]]] = {

        // control name → appearance or mediatype → ids
        val result = mutable.HashMap[String, mutable.HashMap[String, mutable.Buffer[String]]]()

        val rootControl = containingDocument.getControls.getCurrentControlTree.getRoot

        // NOTE: Don't run JavaScript initialization if the control is static readonly (could change in the
        // future if some static readonly controls require JS initialization)
        for {
            control ← Controls.ControlsIterator(rootControl, includeSelf = false)
            if ! control.isStaticReadonly
            (localName, appearanceOrMediatype, effectiveId) ← control.javaScriptInitialization
            appearanceToId = result.getOrElseUpdate(localName, mutable.HashMap[String, mutable.Buffer[String]]())
            ids = appearanceToId.getOrElseUpdate(appearanceOrMediatype, mutable.Buffer[String]())
        } locally {
            ids += XFormsUtils.namespaceId(containingDocument, effectiveId)
        }

        result // TODO: work w/ immutable stuff
    }
}