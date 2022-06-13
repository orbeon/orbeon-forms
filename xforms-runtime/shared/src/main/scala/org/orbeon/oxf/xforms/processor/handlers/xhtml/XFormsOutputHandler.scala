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

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis}
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.isStaticReadonly
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, HandlerSupport, XFormsBaseHandler}
import org.orbeon.oxf.xml.XMLConstants.{FORMATTING_URL_TYPE_QNAME, XHTML_NAMESPACE_URI}
import org.orbeon.oxf.xml.XMLReceiverHelper._
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverHelper, XMLReceiverSupport, XMLUtils}
import org.orbeon.xforms.Constants.DUMMY_IMAGE_URI
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


trait XFormsOutputHandler extends XFormsControlLifecyleHandler with HandlerSupport {

  protected def getContainerAttributes(
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
  uri             : String,
  localname       : String,
  qName           : String,
  localAtts       : Attributes,
  elementAnalysis : ElementAnalysis,
  handlerContext  : HandlerContext
) extends
  XFormsControlLifecyleHandler(
    uri,
    localname,
    qName,
    localAtts,
    elementAnalysis,
    handlerContext,
    repeating  = false,
    forwarding = false
  ) with XFormsOutputHandler {

  private val hasLabel =
    getStaticLHHA(LHHA.Label).isDefined

  protected def handleControlStart(): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]

    val isMinimal =
      XFormsControl.appearances(elementAnalysis)(XFORMS_MINIMAL_APPEARANCE_QNAME)

    val containerAttributes =
      getContainerAttributes(getEffectiveId, outputControl, isField = hasLabel && ! isMinimal)

    // Handle accessibility attributes on control element
    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)
    if (hasLabel)
      handleAriaByAtts(containerAttributes)
    // See https://github.com/orbeon/orbeon-forms/issues/3583
    if (hasLabel && ! isStaticReadonly(outputControl)) {
      containerAttributes.addAttribute("", "tabindex", "tabindex", XMLReceiverHelper.CDATA, "0")
      containerAttributes.addAttribute("", "aria-readonly", "aria-readonly", XMLReceiverHelper.CDATA, "true")
      containerAttributes.addAttribute("", "role", "role", XMLReceiverHelper.CDATA, "textbox")
    }

    withElement(if (hasLabel) "output" else "span", prefix = handlerContext.findXHTMLPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val mediatypeValue = attributes.getValue("mediatype")
      val textValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
      if ((textValue ne null) && textValue.nonEmpty)
        xmlReceiver.characters(textValue.toCharArray, 0, textValue.length)
    }
  }

  protected override def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyHint)
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        elemEffectiveIdOpt      = hasLabel option getEffectiveId, // change from default
        forEffectiveIdWithNs    = None,
        requestedElementNameOpt = None,
        controlOrNull           = currentControl,
        isExternal              = false
      )

  protected override def handleHelp(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl))
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        elemEffectiveIdOpt      = hasLabel option getEffectiveId, // change from default
        forEffectiveIdWithNs    = None,
        requestedElementNameOpt = None,
        controlOrNull           = currentControl,
        isExternal              = false
      )
}

// xf:output[@mediatype = 'text/html']
class XFormsOutputHTMLHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecyleHandler(
  uri,
  localname,
  qName,
  localAtts,
  matched,
  handlerContext,
  repeating  = false,
  forwarding = false
) with XFormsOutputHandler {

  protected def handleControlStart(): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]
    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    val containerAttributes = getContainerAttributes(getEffectiveId, outputControl, isField = false)

    // Handle accessibility attributes on <div>
    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)

    withElement("div", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val mediatypeValue = attributes.getValue("mediatype")
      val htmlValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
      XFormsCrossPlatformSupport.streamHTMLFragment(htmlValue, outputControl.getLocationData, xhtmlPrefix)
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs: Option[String] = None

  override def getContainingElementName: String = "div"
}

// xf:output[starts-with(@appearance, 'image/')]
class XFormsOutputImageHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecyleHandler(
  uri,
  localname,
  qName,
  localAtts,
  matched,
  handlerContext,
  repeating = false,
  forwarding = false
) with XFormsOutputHandler {

  protected def handleControlStart(): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]
    val xhtmlPrefix = handlerContext.findXHTMLPrefix
    val mediatypeValue = attributes.getValue("mediatype")

    val containerAttributes = getContainerAttributes(getEffectiveId, outputControl, isField = false)

    // @src="..."
    // NOTE: If producing a template, or if the image URL is blank, we point to an existing dummy image
    val srcValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue)
    containerAttributes.addAttribute("", "src", "src", XMLReceiverHelper.CDATA, if (srcValue ne null) srcValue else DUMMY_IMAGE_URI)

    XFormsBaseHandler.handleAccessibilityAttributes(attributes, containerAttributes)
      currentControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

    element("img", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes)
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs: Option[String] = None
}

// xf:output[@appearance = 'xxf:text']
class XFormsOutputTextHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecyleHandler(
  uri,
  localname,
  qName,
  localAtts,
  matched,
  handlerContext,
  repeating  = false,
  forwarding = false
) with XFormsOutputHandler {

  protected def handleControlStart(): Unit = {

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]
    val xmlReceiver   = handlerContext.controller.output

    val externalValue = outputControl.getExternalValue()
    if ((externalValue ne null) && externalValue.nonEmpty)
      xmlReceiver.characters(externalValue.toCharArray, 0, externalValue.length)
  }

  // Don't use `for` as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs: Option[String] = None
}

// xf:output[@appearance = 'xxf:download']
class XFormsOutputDownloadHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecyleHandler(
  uri,
  localname,
  qName,
  localAtts,
  matched,
  handlerContext,
  repeating  = false,
  forwarding = false
) with XFormsOutputHandler {

  // NOP because the label is output as the text within <a>
  protected override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit = ()

  protected def handleControlStart(): Unit = {

    implicit val context    : HandlerContext = handlerContext
    implicit val xmlReceiver: XMLReceiver    = handlerContext.controller.output

    val outputControl        = currentControl.asInstanceOf[XFormsOutputControl]
    val containerAttributes  = getContainerAttributes(getEffectiveId, outputControl, isField = false)
    val xhtmlPrefix          = handlerContext.findXHTMLPrefix

    // For f:url-type="resource"
    withFormattingPrefix { formattingPrefix =>

      def anchorAttributes = {

        val hrefValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, null)

        if (hrefValue.isAllBlank) {
          // No URL so make sure a click doesn't cause navigation, and add class
          containerAttributes.addAttribute("", "href", "href", CDATA, "#")
          XMLReceiverSupport.addOrAppendToAttribute(containerAttributes, "class", "xforms-readonly")
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
  override def getForEffectiveIdWithNs: Option[String] = None
}