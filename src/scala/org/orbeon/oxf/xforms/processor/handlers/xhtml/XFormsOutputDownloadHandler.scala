/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl
import org.orbeon.oxf.xforms.processor.handlers.HandlerHelper._
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLUtils
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

/**
 * Handler for xf:output[@appearance = 'xxf:download'].
 */
class XFormsOutputDownloadHandler extends XFormsOutputHandler {

    // // NOP because the label is output as the text within <a>
    protected override def handleLabel() = ()

    protected def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl) {

        implicit val context     = handlerContext
        implicit val xmlReceiver = context.getController.getOutput

        val outputControl        = control.asInstanceOf[XFormsOutputControl]
        val containerAttributes  = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl)
        val xhtmlPrefix          = context.findXHTMLPrefix

        // For f:url-type="resource"
        withFormattingPrefix { formattingPrefix ⇒

            val aAttributes = anchorAttributes(outputControl, containerAttributes, formattingPrefix)
            XFormsBaseHandler.handleAccessibilityAttributes(attributes, aAttributes)

            withElement(xhtmlPrefix, XHTML_NAMESPACE_URI, "a", aAttributes) {
                val labelValue             = Option(control) map (_.getLabel) orNull
                val mustOutputHTMLFragment = Option(control) exists (_.isHTMLLabel)
                XFormsBaseHandlerXHTML.outputLabelText(xmlReceiver, control, labelValue, xhtmlPrefix, mustOutputHTMLFragment)
            }
        }
    }

    private def anchorAttributes(outputControl: XFormsOutputControl, containerAttributes: AttributesImpl, formattingPrefix: String) = {

        val hrefValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, null)

        if (StringUtils.isBlank(hrefValue)) {
            // No URL so make sure a click doesn't cause navigation, and add class
            containerAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#")
            XMLUtils.addOrAppendToAttribute(containerAttributes, "class", "xforms-readonly")
        } else {
            // URL value
            containerAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, hrefValue)
        }

        // Specify resource URL type for proxy portlet
        containerAttributes.addAttribute(
            FORMATTING_URL_TYPE_QNAME.getNamespaceURI,
            FORMATTING_URL_TYPE_QNAME.getName,
            XMLUtils.buildQName(formattingPrefix, FORMATTING_URL_TYPE_QNAME.getName),
            ContentHandlerHelper.CDATA, "resource")

        // Add _blank target in order to prevent:
        // 1. The browser replacing the current page, and
        // 2. The browser displaying the "Are you sure you want to navigate away from this page?" warning dialog
        // This, as of 2009-05, seems to be how most sites handle this
        containerAttributes.addAttribute("", "target", "target", ContentHandlerHelper.CDATA, "_blank")

        // Output xxf:* extension attributes
        if (outputControl ne null)
            outputControl.addExtensionAttributes(containerAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI)

        containerAttributes
    }
}