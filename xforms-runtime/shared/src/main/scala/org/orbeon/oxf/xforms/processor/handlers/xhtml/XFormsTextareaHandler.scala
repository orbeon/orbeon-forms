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

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.controls.{PlaceHolderInfo, XFormsTextareaControl}
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler.forwardAutocompleteAttribute
import org.orbeon.oxf.xforms.processor.handlers.{HandlerContext, XFormsBaseHandler}
import org.orbeon.oxf.xml.SaxSupport._
import org.orbeon.oxf.xml.{XMLConstants, XMLUtils}
import org.orbeon.xforms.XFormsNames
import org.xml.sax.Attributes

/**
  * Handle xf:textarea.
  */
class XFormsTextareaHandler(
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
  ) {

  private lazy val placeHolderInfo: Option[PlaceHolderInfo] =
    PlaceHolderInfo.placeHolderValueOpt(elementAnalysis, currentControl)

  override protected def handleControlStart(): Unit = {

    val textareaControl        = currentControl.asInstanceOf[XFormsTextareaControl]
    implicit val xmlReceiver   = handlerContext.controller.output
    val htmlTextareaAttributes = getEmptyNestedControlAttributesMaybeWithId(getEffectiveId, textareaControl, addId = true)

    // Create xhtml:textarea
    val xhtmlPrefix = handlerContext.findXHTMLPrefix

    if (! XFormsBaseHandler.isStaticReadonly(textareaControl)) {

      val textareaQName = XMLUtils.buildQName(xhtmlPrefix, "textarea")
      htmlTextareaAttributes.addOrReplace("name", getEffectiveId)

      XFormsBaseHandler.forwardAccessibilityAttributes(attributes, htmlTextareaAttributes)
      handleAriaByAtts(htmlTextareaAttributes, XFormsLHHAHandler.coreControlLhhaByCondition)

      // Output all extension attributes
      // Output xxf:* extension attributes
      textareaControl.addExtensionAttributesExceptClassAndAcceptForHandler(htmlTextareaAttributes, XFormsNames.XXFORMS_NAMESPACE_URI)

      if (isXFormsReadonlyButNotStaticReadonly(textareaControl))
        XFormsBaseHandlerXHTML.outputReadonlyAttribute(htmlTextareaAttributes)

      XFormsBaseHandler.handleAriaAttributes(textareaControl.isRequired, textareaControl.isValid, textareaControl.visited, htmlTextareaAttributes)

      // Add attribute even if the control is not concrete
      placeHolderInfo foreach { placeHolderInfo =>
        if (placeHolderInfo.value ne null) // unclear whether this can ever be null
          htmlTextareaAttributes.addOrReplace("placeholder", placeHolderInfo.value)
      }

      forwardAutocompleteAttribute(attributes, "textarea", htmlTextareaAttributes)

      xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName, htmlTextareaAttributes)
      val value = textareaControl.getExternalValue
      if (value ne null)
        xmlReceiver.characters(value.toCharArray, 0, value.length)
      xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName)
    } else {
      // Static readonly
      if (! isNonRelevant(textareaControl))
        // Use <pre> in text/plain so that spaces are kept by the serializer
        // NOTE: Another option would be to transform the text to output &nbsp; and <br/> instead.
        outputStaticReadonlyField(xhtmlPrefix, localName = "pre") {
          // NOTE: Don't replace spaces with &nbsp;, as this is not the right algorithm for all cases
          textareaControl.externalValueOpt foreach { value =>
              xmlReceiver.characters(value.toCharArray, 0, value.length)
            }
        }
    }
  }

  protected override def handleLabel(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! (placeHolderInfo exists (_.isLabelPlaceholder)))
      super.handleLabel(lhhaAnalysis)

  protected override def handleHint(lhhaAnalysis: LHHAAnalysis): Unit =
    if (! (placeHolderInfo exists (! _.isLabelPlaceholder)))
      super.handleHint(lhhaAnalysis)
}