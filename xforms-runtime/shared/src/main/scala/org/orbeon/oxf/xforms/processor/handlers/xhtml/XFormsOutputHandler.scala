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
import org.orbeon.oxf.xforms.control.ControlAjaxSupport.AriaReadonly
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML.withFormattingPrefix
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.XMLConstants.{FORMATTING_URL_TYPE_QNAME, XHTML_NAMESPACE_URI}
import org.orbeon.oxf.xml.XMLReceiverHelper._
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.{XMLReceiver, XMLUtils}
import org.orbeon.xforms.Constants.DUMMY_IMAGE_URI
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsNames}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


trait XFormsOutputHandler extends XFormsControlLifecycleHandler {

  protected def getContainerAttributes(
    effectiveId   : String,
    outputControl : XFormsSingleNodeControl,
    isField       : Boolean
  ): AttributesImpl = {
    // Add custom class
    val containerAttributes = super.getEmptyNestedControlAttributesMaybeWithId(effectiveId, outputControl, addId = true)
    val nestedCssClass = if (isField) "xforms-field" else "xforms-output-output"
    containerAttributes.addOrReplace(XFormsNames.CLASS_QNAME, nestedCssClass)
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
  XFormsControlLifecycleHandler(
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
    findStaticLhhaOrLhhaBy(LHHA.Label).isDefined

  protected def handleControlStart(): Unit = {

    implicit val xmlReceiver: XMLReceiver = handlerContext.controller.output

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]

    val isMinimal =
      XFormsControl.appearances(elementAnalysis)(XFORMS_MINIMAL_APPEARANCE_QNAME)

    val containerAttributes =
      getContainerAttributes(getEffectiveId, outputControl, isField = hasLabel && ! isMinimal)

    // Handle accessibility attributes on control element
    XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
    if (hasLabel) {

      // NOTE: No need to handle `LHHA.Alert` as that's not handled by `handleAriaByAtts`
      def ariaByCondition(lhhaAnalysis: LHHAAnalysis): Boolean =
        XFormsLHHAHandler.coreControlLhhaByCondition(lhhaAnalysis) && (
          lhhaAnalysis.lhhaType != LHHA.Hint                   ||
          ! XFormsBaseHandler.isStaticReadonly(currentControl) ||
          containingDocument.staticReadonlyHint
        )

      handleAriaByAtts(containerAttributes, ariaByCondition)
      // See https://github.com/orbeon/orbeon-forms/issues/3583
      // Also do static readonly, see:
      // - https://github.com/orbeon/orbeon-forms/issues/5525
      // - https://github.com/orbeon/orbeon-forms/issues/5367
      containerAttributes.addOrReplace(XFormsNames.TABINDEX_QNAME, "0")
      containerAttributes.addOrReplace(AriaReadonly, "true")
      containerAttributes.addOrReplace(XFormsNames.ROLE_QNAME, "textbox")
    }

    withElement(if (hasLabel) "output" else "span", prefix = handlerContext.findXHTMLPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val mediatypeValue = attributes.getValue("mediatype")
      val textValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue, handlerContext.collector)
      if ((textValue ne null) && textValue.nonEmpty)
        xmlReceiver.characters(textValue.toCharArray, 0, textValue.length)
    }
  }

  // Override as we want to use `<label>` even in static readonly
  // Alternatively, for static readonly, we could do the same we do for `<xf:input>` and other fields, for consistency
  protected override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    handleLabelHintHelpAlert(
      lhhaAnalysis            = lhhaAnalysis,
      controlEffectiveIdOpt   = None,
      forEffectiveIdWithNsOpt = getForEffectiveIdWithNs(lhhaAnalysis),
      requestedElementNameOpt = None,
      controlOrNull           = currentControl,
      isExternal              = false
    )

  protected override def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl) || containingDocument.staticReadonlyHint)
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = hasLabel option getEffectiveId, // change from default
        forEffectiveIdWithNsOpt = None,
        requestedElementNameOpt = None,
        controlOrNull           = currentControl,
        isExternal              = false
      )

  protected override def handleHelp(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! XFormsBaseHandler.isStaticReadonly(currentControl))
      handleLabelHintHelpAlert(
        lhhaAnalysis            = lhhaAnalysis,
        controlEffectiveIdOpt   = hasLabel option getEffectiveId, // change from default
        forEffectiveIdWithNsOpt = None,
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
) extends XFormsControlLifecycleHandler(
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
    XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)

    withElement("div", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val mediatypeValue = attributes.getValue("mediatype")
      val htmlValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue, handlerContext.collector)
      XFormsCrossPlatformSupport.streamHTMLFragment(htmlValue, outputControl.getLocationData, xhtmlPrefix)
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] = None

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
) extends XFormsControlLifecycleHandler(
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
    val srcValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, mediatypeValue, handlerContext.collector)
    containerAttributes.addOrReplace("src", if (srcValue ne null) srcValue else DUMMY_IMAGE_URI)

    XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
    currentControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

    element("img", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes)
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] = None
}

// xf:output[starts-with(@appearance, 'video/')]
class XFormsOutputVideoHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecycleHandler(
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
    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    val outputControl = currentControl.asInstanceOf[XFormsOutputControl]
    val generalMediaType = attributes.getValue("mediatype")
    val specificMediaType = outputControl.fileMediatype(handlerContext.collector)

    val srcValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, generalMediaType, handlerContext.collector)

    val containerAttributes = getContainerAttributes(getEffectiveId, outputControl, isField = false)
    containerAttributes.addOrReplace("controls", "")
    if (srcValue.isEmpty) {
      containerAttributes.appendToClassAttribute("empty-source")
    }

    XFormsBaseHandler.forwardAccessibilityAttributes(attributes, containerAttributes)
    currentControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

    withElement("video", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = containerAttributes) {
      val sourceAttributes = new AttributesImpl()
      sourceAttributes.addOrReplace("src", srcValue)
      specificMediaType.foreach(sourceAttributes.addOrReplace("type", _))

      element("source", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = sourceAttributes)
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] = None
}

// xf:output[@appearance = 'xxf:text']
class XFormsOutputTextHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecycleHandler(
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

    val externalValue = outputControl.getExternalValue(handlerContext.collector)
    if ((externalValue ne null) && externalValue.nonEmpty)
      xmlReceiver.characters(externalValue.toCharArray, 0, externalValue.length)
  }

  // Don't use `for` as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] = None
}

// xf:output[@appearance = 'xxf:download']
class XFormsOutputDownloadHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  localAtts      : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends XFormsControlLifecycleHandler(
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

        val hrefValue = XFormsOutputControl.getExternalValueOrDefault(outputControl, null, handlerContext.collector)

        if (hrefValue.isAllBlank) {
          // No URL so make sure a click doesn't cause navigation, and add class
          containerAttributes.addOrReplace("href", "#")
          containerAttributes.addOrReplace("class", "xforms-readonly")
        } else {
          // URL value
          containerAttributes.addOrReplace("href", hrefValue)
        }

        // Specify resource URL type for proxy portlet
        containerAttributes.addAttribute(
          FORMATTING_URL_TYPE_QNAME.namespace.uri,
          FORMATTING_URL_TYPE_QNAME.localName,
          XMLUtils.buildQName(formattingPrefix, FORMATTING_URL_TYPE_QNAME.localName),
          CDATA, "resource")

        // https://developer.mozilla.org/en-US/docs/Web/HTML/Element/a#download
        containerAttributes.addOrReplace("download", "download")

        // Output xxf:* extension attributes
        if (outputControl ne null)
          outputControl.addExtensionAttributesExceptClassAndAcceptForHandler(containerAttributes, XXFORMS_NAMESPACE_URI)

        containerAttributes
      }

      val aAttributes = anchorAttributes
      XFormsBaseHandler.forwardAccessibilityAttributes(attributes, aAttributes)

      withElement(localName = "a", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = aAttributes) {
        val labelValue             = currentControl.getLabel(handlerContext.collector)
        val mustOutputHTMLFragment = currentControl.isHTMLLabel(handlerContext.collector)
        XFormsBaseHandlerXHTML.outputLabelTextIfNotEmpty(labelValue, xhtmlPrefix, mustOutputHTMLFragment, Option(currentControl.getLocationData))
      }
    }
  }

  // Don't use @for as we are not pointing to an HTML control
  override def getForEffectiveIdWithNs(lhhaAnalysis: LHHAAnalysis): Option[String] = None
}