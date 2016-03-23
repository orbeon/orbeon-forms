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
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.LHHAC
import org.orbeon.oxf.xforms.processor.handlers.{HandlerSupport, XFormsBaseHandler}
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xml.XMLConstants.{FORMATTING_URL_TYPE_QNAME, XHTML_NAMESPACE_URI}
import org.orbeon.oxf.xml.XMLReceiverHelper._
import org.orbeon.oxf.xml.{SAXUtils, XMLReceiverHelper, XMLUtils}
import org.xml.sax.Attributes

trait XFormsOutputHandler extends XFormsControlLifecyleHandler with HandlerSupport {

  protected def getContainerAttributes(
    uri           : String,
    localname     : String,
    attributes    : Attributes,
    effectiveId   : String,
    outputControl : XFormsSingleNodeControl
  ) = {
    // Add custom class
    val containerAttributes = super.getEmptyNestedControlAttributesMaybeWithId(uri, localname, attributes, effectiveId, outputControl, true)
    containerAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "xforms-output-output")
    containerAttributes
  }
}

// Default xf:output handler
class XFormsOutputDefaultHandler extends XFormsControlLifecyleHandler(false) with XFormsOutputHandler {

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    implicit val xmlReceiver = handlerContext.getController.getOutput

    val outputControl = control.asInstanceOf[XFormsOutputControl]
    val isConcreteControl = outputControl ne null
    val contentHandler = handlerContext.getController.getOutput

    val containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl)

    // Handle accessibility attributes on control element
    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)

    val elementName = if (getStaticLHHA(getPrefixedId, LHHAC.LABEL) ne null) "output" else "span"

    withElement(elementName, prefix = handlerContext.findXHTMLPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      if (isConcreteControl) {
        val mediatypeValue = attributes.getValue("mediatype")
        val textValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
        if ((textValue ne null) && textValue.nonEmpty)
          contentHandler.characters(textValue.toCharArray, 0, textValue.length)
      }
    }
  }
}

// xf:output[@mediatype = 'text/html']
class XFormsOutputHTMLHandler extends XFormsControlLifecyleHandler(false) with XFormsOutputHandler {

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    implicit val xmlReceiver = handlerContext.getController.getOutput

    val outputControl = control.asInstanceOf[XFormsOutputControl]
    val isConcreteControl = outputControl ne null
    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    val containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl)

    // Handle accessibility attributes on <div>
    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)

    withElement("div", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      if (isConcreteControl) {
        val mediatypeValue = attributes.getValue("mediatype")
        val htmlValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
        XFormsUtils.streamHTMLFragment(xmlReceiver, htmlValue, outputControl.getLocationData, xhtmlPrefix)
      }
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null

  protected override def getContainingElementName: String = "div"
}

// xf:output[starts-with(@appearance, 'image/')]
class XFormsOutputImageHandler extends XFormsControlLifecyleHandler(false) with XFormsOutputHandler {

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    implicit val xmlReceiver = handlerContext.getController.getOutput

    val outputControl = control.asInstanceOf[XFormsOutputControl]
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val mediatypeValue = attributes.getValue("mediatype")

    val containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl)

    // @src="..."
    // NOTE: If producing a template, or if the image URL is blank, we point to an existing dummy image
    val srcValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
    containerAttributes.addAttribute("", "src", "src", XMLReceiverHelper.CDATA, if (srcValue ne null) srcValue else XFormsConstants.DUMMY_IMAGE_URI)

    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)
    if (control != null)
      control.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

    element("img", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes)
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null
}

// xf:output[@appearance = 'xxf:text']
class XFormsOutputTextHandler extends XFormsControlLifecyleHandler(false) with XFormsOutputHandler {

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    val outputControl = control.asInstanceOf[XFormsOutputControl]
    val isConcreteControl = outputControl ne null
    val contentHandler = handlerContext.getController.getOutput

    if (isConcreteControl) {
      val externalValue = outputControl.getExternalValue
      if ((externalValue ne null) && externalValue.nonEmpty)
        contentHandler.characters(externalValue.toCharArray, 0, externalValue.length)
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null
}

// xf:output[@appearance = 'xxf:download']
class XFormsOutputDownloadHandler extends XFormsControlLifecyleHandler(false) with XFormsOutputHandler {

  // NOP because the label is output as the text within <a>
  protected override def handleLabel() = ()

  protected def handleControlStart(
    uri         : String,
    localname   : String,
    qName       : String,
    attributes  : Attributes,
    effectiveId : String,
    control     : XFormsControl
  ): Unit = {

    implicit val context     = handlerContext
    implicit val xmlReceiver = context.getController.getOutput

    val outputControl        = control.asInstanceOf[XFormsOutputControl]
    val containerAttributes  = getContainerAttributes(uri, localname, attributes, effectiveId, outputControl)
    val xhtmlPrefix          = context.findXHTMLPrefix

    // For f:url-type="resource"
    withFormattingPrefix { formattingPrefix ⇒

      def anchorAttributes = {

        val hrefValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, null)

        if (StringUtils.isBlank(hrefValue)) {
          // No URL so make sure a click doesn't cause navigation, and add class
          containerAttributes.addAttribute("", "href", "href", CDATA, "#")
          SAXUtils.addOrAppendToAttribute(containerAttributes, "class", "xforms-readonly")
        } else {
          // URL value
          containerAttributes.addAttribute("", "href", "href", CDATA, hrefValue)
        }

        // Specify resource URL type for proxy portlet
        containerAttributes.addAttribute(
          FORMATTING_URL_TYPE_QNAME.getNamespaceURI,
          FORMATTING_URL_TYPE_QNAME.getName,
          XMLUtils.buildQName(formattingPrefix, FORMATTING_URL_TYPE_QNAME.getName),
          CDATA, "resource")

        // Add _blank target in order to prevent:
        // 1. The browser replacing the current page, and
        // 2. The browser displaying the "Are you sure you want to navigate away from this page?" warning dialog
        // This, as of 2009-05, seems to be how most sites handle this
        containerAttributes.addAttribute("", "target", "target", CDATA, "_blank")

        // Output xxf:* extension attributes
        if (outputControl ne null)
          outputControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

        containerAttributes
      }

      val aAttributes = anchorAttributes
      XFormsBaseHandler.handleAccessibilityAttributes(attributes, aAttributes)

      withElement("a", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = aAttributes) {
        val labelValue             = Option(control) map (_.getLabel) orNull
        val mustOutputHTMLFragment = Option(control) exists (_.isHTMLLabel)
        XFormsBaseHandlerXHTML.outputLabelText(xmlReceiver, control, labelValue, xhtmlPrefix, mustOutputHTMLFragment)
      }
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null
}