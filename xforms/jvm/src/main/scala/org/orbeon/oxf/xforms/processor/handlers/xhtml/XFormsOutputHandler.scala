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
import org.orbeon.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.isStaticReadonly
import org.orbeon.oxf.xforms.processor.handlers.{HandlerSupport, XFormsBaseHandler}
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xml.XMLConstants.{FORMATTING_URL_TYPE_QNAME, XHTML_NAMESPACE_URI}
import org.orbeon.oxf.xml.XMLReceiverHelper._
import org.orbeon.oxf.xml.{SAXUtils, XMLReceiver, XMLReceiverHelper, XMLUtils}
import org.orbeon.xforms.XFormsConstants
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl

trait XFormsOutputHandler extends XFormsControlLifecyleHandler with HandlerSupport {

  protected def getContainerAttributes(
    uri           : String,
    localname     : String,
    localAtts     : Attributes,
    effectiveId   : String,
    outputControl : XFormsSingleNodeControl,
    isField       : Boolean
  ): AttributesImpl = {
    // Add custom class
    val containerAttributes = super.getEmptyNestedControlAttributesMaybeWithId(effectiveId, outputControl, addId = true)
    val nestedCssClass = if (isField) "xforms-field" else "xforms-output-output"
    containerAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, nestedCssClass)
    containerAttributes
  }
}

// Default xf:output handler
class XFormsOutputDefaultHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, localAtts, matched, handlerContext, repeating = false, forwarding = false)
     with XFormsOutputHandler {

  override protected def handleControlStart(): Unit = {

    implicit val xmlReceiver: XMLReceiver = xformsHandlerContext.getController.getOutput

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]

    val hasLabel =
      staticControlOpt exists (_.asInstanceOf[StaticLHHASupport].hasLHHA(LHHA.Label))

    val isMinimal =
      staticControlOpt exists (XFormsControl.appearances(_)(XFORMS_MINIMAL_APPEARANCE_QNAME))

    val containerAttributes =
      getContainerAttributes(uri, localname, attributes, getEffectiveId, outputControl, isField = hasLabel && ! isMinimal)

    // Handle accessibility attributes on control element
    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)
    handleAriaByAtts(containerAttributes)
    // See https://github.com/orbeon/orbeon-forms/issues/3583
    if (hasLabel && ! isStaticReadonly(outputControl)) {
      containerAttributes.addAttribute("", "tabindex", "tabindex", XMLReceiverHelper.CDATA, "0")
      containerAttributes.addAttribute("", "aria-readonly", "aria-readonly", XMLReceiverHelper.CDATA, "true")
      containerAttributes.addAttribute("", "role", "role", XMLReceiverHelper.CDATA, "textbox")
    }

    val elementName = if (getStaticLHHA(getPrefixedId, LHHA.Label) ne null) "output" else "span"

    withElement(elementName, prefix = xformsHandlerContext.findXHTMLPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val mediatypeValue = attributes.getValue("mediatype")
      val textValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
      if ((textValue ne null) && textValue.nonEmpty)
        xmlReceiver.characters(textValue.toCharArray, 0, textValue.length)
    }
  }
}

// xf:output[@mediatype = 'text/html']
class XFormsOutputHTMLHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, localAtts, matched, handlerContext, repeating = false, forwarding = false)
     with XFormsOutputHandler {

  override protected def handleControlStart(): Unit = {

    implicit val xmlReceiver = xformsHandlerContext.getController.getOutput

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]
    val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix

    val containerAttributes = getContainerAttributes(uri, localname, attributes, getEffectiveId, outputControl, isField = false)

    // Handle accessibility attributes on <div>
    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)

    withElement("div", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val mediatypeValue = attributes.getValue("mediatype")
      val htmlValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
      XFormsUtils.streamHTMLFragment(xmlReceiver, htmlValue, outputControl.getLocationData, xhtmlPrefix)
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null

  override def getContainingElementName: String = "div"
}

// xf:output[starts-with(@appearance, 'image/')]
class XFormsOutputImageHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, localAtts, matched, handlerContext, repeating = false, forwarding = false)
     with XFormsOutputHandler {

  override protected def handleControlStart(): Unit = {

    implicit val xmlReceiver = xformsHandlerContext.getController.getOutput

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]
    val xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix
    val mediatypeValue = attributes.getValue("mediatype")

    val containerAttributes = getContainerAttributes(uri, localname, attributes, getEffectiveId, outputControl, isField = false)

    // @src="..."
    // NOTE: If producing a template, or if the image URL is blank, we point to an existing dummy image
    val srcValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
    containerAttributes.addAttribute("", "src", "src", XMLReceiverHelper.CDATA, if (srcValue ne null) srcValue else XFormsConstants.DUMMY_IMAGE_URI)

    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)
      currentControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

    element("img", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes)
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null
}

// xf:output[@appearance = 'xxf:text']
class XFormsOutputTextHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, localAtts, matched, handlerContext, repeating = false, forwarding = false)
     with XFormsOutputHandler {

  override protected def handleControlStart(): Unit = {

    val outputControl     = currentControl.asInstanceOf[XFormsOutputControl]
    val xmlReceiver       = xformsHandlerContext.getController.getOutput

    val externalValue = outputControl.getExternalValue
    if ((externalValue ne null) && externalValue.nonEmpty)
      xmlReceiver.characters(externalValue.toCharArray, 0, externalValue.length)
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String) = null
}

// xf:output[@appearance = 'xxf:download']
class XFormsOutputDownloadHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : AnyRef,
  handlerContext : AnyRef
) extends XFormsControlLifecyleHandler(uri, localname, qName, localAtts, matched, handlerContext, repeating = false, forwarding = false)
     with XFormsOutputHandler {

  // NOP because the label is output as the text within <a>
  protected override def handleLabel() = ()

  override protected def handleControlStart(): Unit = {

    implicit val context     = xformsHandlerContext
    implicit val xmlReceiver = xformsHandlerContext.getController.getOutput

    val outputControl        = currentControl.asInstanceOf[XFormsOutputControl]
    val containerAttributes  = getContainerAttributes(uri, localname, attributes, getEffectiveId, outputControl, isField = false)
    val xhtmlPrefix          = xformsHandlerContext.findXHTMLPrefix

    // For f:url-type="resource"
    withFormattingPrefix { formattingPrefix =>

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
          FORMATTING_URL_TYPE_QNAME.namespace.uri,
          FORMATTING_URL_TYPE_QNAME.localName,
          XMLUtils.buildQName(formattingPrefix, FORMATTING_URL_TYPE_QNAME.localName),
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

      withElement(localName = "a", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = aAttributes) {
        val labelValue             = currentControl.getLabel
        val mustOutputHTMLFragment = currentControl.isHTMLLabel
        XFormsBaseHandlerXHTML.outputLabelTextIfNotEmpty(labelValue, xhtmlPrefix, mustOutputHTMLFragment, Option(currentControl.getLocationData))
      }
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveId(effectiveId: String): String = null
}