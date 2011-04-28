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

package org.orbeon.oxf.xforms.processor.handlers
    
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.processor.XFormsFeatures
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.XMLConstants
import org.xml.sax.helpers.AttributesImpl

import scala.collection.JavaConversions._
import java.util.{List => JList, Collections => JCollections}
import org.dom4j.Element
import collection.mutable.LinkedHashSet
import org.apache.commons.lang.StringUtils
import xbl.XBLBindings
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.properties.PropertySet

/**
 * Handler for <xhtml:head>. Outputs CSS and JS.
 */
class XHTMLHeadHandler extends XHTMLHeadHandlerBase {

    // Output an element
    private def outputElement(helper: ContentHandlerHelper, xhtmlPrefix: String, attributesImpl: AttributesImpl,
                              getElementDetails: (Option[String], Option[String]) => (String, Array[String]))
                             (resource: Option[String], cssClass: Option[String], content: Option[String]) {

        val (elementName, attributes) = getElementDetails(resource, cssClass)

        attributesImpl.clear()
        ContentHandlerHelper.populateAttributes(attributesImpl, attributes)
        helper.startElement(xhtmlPrefix, XMLConstants.XHTML_NAMESPACE_URI, elementName, attributesImpl)
        // output content only if present
        content foreach (helper.text(_))
        helper.endElement()
    }

    // Output baseline, remaining, and inline resources
    private def outputResources(outputElement: (Option[String], Option[String], Option[String]) => Unit,
                                getBuiltin: XFormsStaticState => JList[XFormsFeatures.ResourceConfig],
                                getXBL: => JList[Element], xblBaseline: collection.Set[String], minimal: Boolean) {

        // For now, actual builtin resources always include the baseline builtin resources
        val builtinBaseline = LinkedHashSet(getBuiltin(null) map (_.getResourcePath(minimal)): _*)
        val allBaseline = builtinBaseline ++ xblBaseline

        // Output baseline resources with a CSS class
        allBaseline foreach (s => outputElement(Some(s), Some("xforms-baseline"), None))

        val builtinUsed = LinkedHashSet(getBuiltin(containingDocument.getStaticState) map (_.getResourcePath(minimal)): _*)
        val xblUsed = LinkedHashSet(XHTMLHeadHandler.xblResourcesToSeq(getXBL): _*)

        // Output remaining resources if any, with no CSS class
        builtinUsed ++ xblUsed -- allBaseline foreach (s => outputElement(Some(s), None, None))

        // Output inline XBL resources
        getXBL filter (_.attributeValue(XFormsConstants.SRC_QNAME) eq null) foreach
            { e => outputElement(None, None, Some(e.getText)) }
    }

    override def outputCSSResources(helper: ContentHandlerHelper, xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl) {

        // Function to output either a <link> or <style> element
        def outputCSSElement = outputElement(helper, xhtmlPrefix, attributesImpl,
            (resource, cssClass) => resource match {
                case Some(resource) => ("link", Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all", "class", cssClass.orNull))
                case None => ("style", Array("type", "text/css", "media", "all", "class", cssClass.orNull))
            }) _

        // Output all CSS
        outputResources(outputCSSElement, XFormsFeatures.getCSSResources,
            containingDocument.getStaticState.getXBLBindings.getXBLStyles,
            containingDocument.getStaticState.getXBLBindings.getBaselineResources._2, minimal)

    }

    override def outputJavaScriptResources(helper: ContentHandlerHelper, xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl) {

        // Function to output either a <script> element
        def outputJSElement = outputElement(helper, xhtmlPrefix, attributesImpl,
            (resource, cssClass) => ("script", Array("type", "text/javascript", "src", resource.orNull, "class", cssClass.orNull))) _

        // Output all JS
        outputResources(outputJSElement, XFormsFeatures.getJavaScriptResources,
            containingDocument.getStaticState.getXBLBindings.getXBLScripts,
            containingDocument.getStaticState.getXBLBindings.getBaselineResources._1, minimal)
    }
}

object XHTMLHeadHandler {

    def getBaselineResources(staticState: XFormsStaticState): (collection.Set[String], collection.Set[String]) = {

        val metadata = staticState.getMetadata
        val xblBindings = staticState.getXBLBindings

        // Register baseline includes
        XFormsProperties.getResourcesBaseline match {
            case baselineProperty: PropertySet.Property =>
                val tokens = LinkedHashSet(StringUtils.split(baselineProperty.value.toString): _*) toIterable

                val (scripts, styles) =
                    (for {
                        token <- tokens
                        qName = Dom4jUtils.extractTextValueQName(baselineProperty.namespaces, token, true)
                    } yield
                        if (staticState.getMetadata.isXBLBinding(qName.getNamespaceURI, qName.getName)) {
                            val binding = xblBindings.getComponentBindings.get(qName)
                            (binding.scripts, binding.styles)
                        } else {
                            // Load XBL document
                            val xblDocument = xblBindings.readXBLResource(metadata.getAutomaticXBLMappingPath(qName.getNamespaceURI, qName.getName))

                            // Extract xbl:xbl/xbl:script
                            val scripts = Dom4jUtils.elements(xblDocument.getRootElement, XFormsConstants.XBL_SCRIPT_QNAME)

                            // Try to find binding
                            (Dom4jUtils.elements(xblDocument.getRootElement, XFormsConstants.XBL_BINDING_QNAME) map
                                (e => new XBLBindings.AbstractBinding(e, staticState.getNamespaceMapping("", e), scripts, null)) find
                                    (_.qNameMatch == qName)) match {
                                case Some(binding) => (binding.scripts, binding.styles)
                                case None => (JCollections.emptyList[Element], JCollections.emptyList[Element])
                            }
                        }) unzip

                (LinkedHashSet(xblResourcesToSeq(scripts.flatten): _*),
                    LinkedHashSet(xblResourcesToSeq(styles.flatten): _*))
            case _ => (collection.Set.empty[String], collection.Set.empty[String])
        }
    }

    // All XBL resources use the @src attribute
    def xblResourcesToSeq(elements: Iterable[Element]) =
        elements flatMap (e => Option(e.attributeValue(XFormsConstants.SRC_QNAME))) toSeq
}