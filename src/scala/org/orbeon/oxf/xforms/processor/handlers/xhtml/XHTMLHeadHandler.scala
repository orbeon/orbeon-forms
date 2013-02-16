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
    
import org.orbeon.oxf.xforms._
import control.{Controls, XFormsControl}
import org.orbeon.oxf.xforms.XFormsProperties._
import event.XFormsEvents
import org.orbeon.oxf.xforms.processor.XFormsFeatures
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.XMLConstants
import org.xml.sax.helpers.AttributesImpl

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import java.util.{List ⇒ JList, Map ⇒ JMap}
import org.dom4j.Element
import collection.mutable.{Buffer, HashMap, LinkedHashSet}
import state.XFormsStateManager
import org.orbeon.oxf.util.URLRewriterUtils._

/**
 * Handler for <xh:head>. Outputs CSS and JS.
 */
class XHTMLHeadHandler extends XHTMLHeadHandlerBase {

    // Output an element
    private def outputElement(
            helper: ContentHandlerHelper,
            xhtmlPrefix: String,
            attributesImpl: AttributesImpl,
            getElementDetails: (Option[String], Option[String]) ⇒ (String, Array[String])
        )(
            resource: Option[String],
            cssClass: Option[String],
            content: Option[String]
        ) {

        val (elementName, attributes) = getElementDetails(resource, cssClass)

        attributesImpl.clear()
        ContentHandlerHelper.populateAttributes(attributesImpl, attributes)
        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, elementName, attributesImpl)
        // output content only if present
        content foreach helper.text
        helper.endElement()
    }

    // Output baseline, remaining, and inline resources
    private def outputResources(
            outputElement: (Option[String], Option[String], Option[String]) ⇒ Unit,
            getBuiltin: StaticStateGlobalOps ⇒ JList[XFormsFeatures.ResourceConfig],
            getXBL: ⇒ Seq[Element],
            xblBaseline: collection.Set[String],
            minimal: Boolean) {

        // For now, actual builtin resources always include the baseline builtin resources
        val builtinBaseline = LinkedHashSet(getBuiltin(null) map (_.getResourcePath(minimal)): _*)
        val allBaseline = builtinBaseline ++ xblBaseline

        // Output baseline resources with a CSS class
        allBaseline foreach (s ⇒ outputElement(Some(s), Some("xforms-baseline"), None))

        val builtinUsed = LinkedHashSet(getBuiltin(containingDocument.getStaticOps) map (_.getResourcePath(minimal)): _*)
        val xblUsed = LinkedHashSet(XHTMLHeadHandler.xblResourcesToSeq(getXBL): _*)

        // Output remaining resources if any, with no CSS class
        builtinUsed ++ xblUsed -- allBaseline foreach (s ⇒ outputElement(Some(s), None, None))

        // Output inline XBL resources
        getXBL filter (_.attributeValue(XFormsConstants.SRC_QNAME) eq null) foreach
            { e ⇒ outputElement(None, None, Some(e.getText)) }
    }

    override def outputCSSResources(helper: ContentHandlerHelper, xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl) {

        // Function to output either a <link> or <style> element
        def outputCSSElement = outputElement(helper, xhtmlPrefix, attributesImpl,
            (resource, cssClass) ⇒ resource match {
                case Some(resource) ⇒ ("link", Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all", "class", cssClass.orNull))
                case None ⇒ ("style", Array("type", "text/css", "media", "all", "class", cssClass.orNull))
            })(_, _, _)

        // Output all CSS
        outputResources(outputCSSElement, XFormsFeatures.getCSSResources,
            containingDocument.getStaticOps.getXBLStyles,
            containingDocument.getStaticOps.baselineResources._2, minimal)
    }

    override def outputJavaScriptResources(helper: ContentHandlerHelper, xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl) {

        // Function to output either a <script> element
        def outputJSElement = outputElement(helper, xhtmlPrefix, attributesImpl,
            (resource, cssClass) ⇒ ("script", Array("type", "text/javascript", "src", resource.orNull, "class", cssClass.orNull)))(_, _, _)

        // Output all JS
        outputResources(outputJSElement, XFormsFeatures.getJavaScriptResources,
            containingDocument.getStaticOps.getXBLScripts,
            containingDocument.getStaticOps.baselineResources._1, minimal)
    }

    override def outputConfigurationProperties(helper: ContentHandlerHelper, xhtmlPrefix: String, versionedResources: Boolean) {

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
                containingDocument.getStaticOps.hasHandlerForEvent(XFormsEvents.XFORMS_HELP, false),
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
                    helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, "script", Array[String]("type", "text/javascript"))
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
}

object XHTMLHeadHandler {

    // All XBL resources use the @src attribute
    def xblResourcesToSeq(elements: Iterable[Element]) =
        elements flatMap (e ⇒ Option(e.attributeValue(XFormsConstants.SRC_QNAME))) toSeq

    def outputScripts(helper: ContentHandlerHelper, scripts: Iterable[(String, String)]) =
        for ((clientName, body) ← scripts) {
            helper.text("\nfunction " + clientName + "(event) {\n")
            helper.text(body)
            helper.text("}\n")
        }

    def gatherJavascriptControls(containingDocument: XFormsContainingDocument): JMap[String, JMap[String, JList[String]]] = {

        // control name → appearance or mediatype → ids
        val javaScriptControlsAppearancesMap = HashMap[String, HashMap[String, Buffer[String]]]()

        val rootControl = containingDocument.getControls.getCurrentControlTree.getRoot

        // NOTE: Don't run JavaScript initialization if the control is static readonly (could change in the
        // future if some static readonly controls require JS initialization)
        for {
            control ← Controls.ControlsIterator(rootControl, includeSelf = false)
            if ! control.isStaticReadonly
            (localName, appearanceOrMediatype, effectiveId) ← Option(control.getJavaScriptInitialization)
            appearanceToId = javaScriptControlsAppearancesMap.getOrElseUpdate(localName, HashMap[String, Buffer[String]]())
            ids = appearanceToId.getOrElseUpdate(appearanceOrMediatype, Buffer[String]())
        }
            ids += XFormsUtils.namespaceId(containingDocument, effectiveId)

        // FIXME: Get rid of this series of conversions as soon as possible!
        javaScriptControlsAppearancesMap mapValues (_ mapValues (_.asJava) asJava) asJava
    }
}