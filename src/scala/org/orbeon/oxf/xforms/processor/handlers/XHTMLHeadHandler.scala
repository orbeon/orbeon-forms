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
import java.util.{List => JList}
import org.dom4j.Element
import collection.mutable.LinkedHashSet

/**
 * Handler for <xhtml:head>. Outputs CSS and JS.
 */
class XHTMLHeadHandler extends XHTMLHeadHandlerBase {

    // Output an element
    private def outputElement(helper: ContentHandlerHelper, xhtmlPrefix: String, attributesImpl: AttributesImpl,
                              getElementDetails: (Option[String], Option[String]) => (String, Array[String]))
                             (resource: Option[String], cssClass: Option[String], content: Option[String]) = {

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
                                getXBL: => JList[Element], baselineXBL: Set[String], minimal: Boolean) {

        // For now, actual builtin resources always include the baseline builtin resources
        val builtin = LinkedHashSet(getBuiltin(containingDocument.getStaticState) map (_.getResourcePath(minimal)): _*)
        val allBaseline = builtin ++ baselineXBL

        val usedXBL = LinkedHashSet(Option(getXBL).flatten flatMap (e => Option(e.attributeValue(XFormsConstants.SRC_QNAME))) toSeq: _*)

        // Output baseline resources with a CSS class
        allBaseline foreach (s => outputElement(Some(s), Some("xforms-baseline"), None))
        // Output remaining resources if any, with no CSS class
        usedXBL -- allBaseline foreach (s => outputElement(Some(s), None, None))

        // Output inline XBL resources
        Option(getXBL).flatten filter (_.attributeValue(XFormsConstants.SRC_QNAME) eq null) foreach
            { e => outputElement(None, None, Some(e.getText)) }
    }

    override def outputCSSResources(helper: ContentHandlerHelper, xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl) {

        val baselineXBLCSS = Set.empty[String] // TODO

        // Function to output either a <link> or <style> element
        def outputCSSElement = outputElement(helper, xhtmlPrefix, attributesImpl,
            (resource, cssClass) => resource match {
                case Some(resource) => ("link", Array("rel", "stylesheet", "href", resource, "type", "text/css", "media", "all", "class", cssClass.orNull))
                case None => ("style", Array("type", "text/css", "media", "all", "class", cssClass.orNull))
            }) _

        // Output all CSS
        outputResources(outputCSSElement, XFormsFeatures.getCSSResources, containingDocument.getStaticState.getXBLBindings.getXBLStyles,
            baselineXBLCSS, minimal)

    }

    override def outputJavaScriptResources(helper: ContentHandlerHelper, xhtmlPrefix: String, minimal: Boolean, attributesImpl: AttributesImpl) {

        val baselineXBLJS = Set.empty[String] // TODO

        // Function to output either a <script> element
        def outputJSElement = outputElement(helper, xhtmlPrefix, attributesImpl,
            (resource, cssClass) => ("script", Array("type", "text/javascript", "src", resource.orNull, "class", cssClass.orNull))) _

        // Output all JS
        outputResources(outputJSElement, XFormsFeatures.getJavaScriptResources, containingDocument.getStaticState.getXBLBindings.getXBLScripts,
            baselineXBLJS, minimal)
    }
}