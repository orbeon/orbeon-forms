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

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl
import org.orbeon.oxf.xforms.itemset.Item
import org.orbeon.oxf.xforms.itemset.Itemset
import org.orbeon.oxf.xml.{XMLUtils, XMLReceiverHelper}
import org.orbeon.oxf.xml.XMLConstants._
import org.xml.sax.Attributes
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler._
import XFormsBaseHandlerXHTML._
import XFormsConstants._
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait
import org.orbeon.oxf.xforms.processor.handlers.HandlerSupport

/**
 * Handle xf:input.
 *
 * TODO: Subclasses per appearance.
 */
class XFormsInputHandler extends XFormsControlLifecyleHandler(false) with HandlerSupport { // repeating = false

  private var placeHolderInfo: XFormsInputControl.PlaceHolderInfo = _

  override def init(uri: String, localname: String, qName: String, attributes: Attributes, matched: AnyRef): Unit = {
    super.init(uri, localname, qName, attributes, matched)
    this.placeHolderInfo = XFormsInputControl.getPlaceholderInfo(elementAnalysis, currentControlOrNull)
  }

  // TODO: Use type member instead
  protected override def currentControlOrNull = super.currentControlOrNull.asInstanceOf[XFormsInputControl]
  protected override def currentControlOpt    = super.currentControlOpt.asInstanceOf[Option[XFormsInputControl]]

  private def controlHas(predicate: XFormsInputControl ⇒ Boolean) = currentControlOpt exists predicate

  private def isDateTime    = controlHas(c ⇒ c.getBuiltinTypeName == "dateTime")
  private def isDateMinimal = controlHas(c ⇒ c.getBuiltinTypeName == "date" && c.appearances(XFORMS_MINIMAL_APPEARANCE_QNAME))
  private def isBoolean     = controlHas(c ⇒ c.getBuiltinTypeName == "boolean")

  protected def handleControlStart(uri: String, localname: String, qName: String, attributes: Attributes, effectiveId: String, control: XFormsControl): Unit = {
    val inputControl = control.asInstanceOf[XFormsInputControl]
    implicit val xmlReceiver = handlerContext.getController.getOutput
    val isRelevantControl = ! isNonRelevant(inputControl)
    val isConcreteControl = inputControl ne null
    if (isBoolean) {
      // Produce a boolean output

      val isMultiple = true
      val itemset = new Itemset(isMultiple)
      // NOTE: We have decided that it did not make much sense to encode the value for boolean. This also poses
      // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
      // the encrypted value of "true". So we do not encrypt values.
      // NOTE: Put null label so that it is not output at all
      // encode = false,
      itemset.addChildItem(
        Item(
          position   = 0,
          isMultiple = isMultiple,
          attributes = Nil,
          label      = null,
          help       = None,
          hint       = None,
          value      = "true"
        )
      )

      // TODO: This delegation to xf:select1 handler is error-prone, is there a better way?
      val select1Handler = new XFormsSelect1Handler {
        override def getPrefixedId        = XFormsInputHandler.this.getPrefixedId
        override def getEffectiveId       = XFormsInputHandler.this.getEffectiveId
        override def currentControlOrNull = XFormsInputHandler.this.currentControlOrNull
        override def currentControlOpt    = XFormsInputHandler.this.currentControlOpt
      }
      select1Handler.setContext(getContext)
      select1Handler.init(uri, localname, qName, attributes, elementAnalysis)
      select1Handler.outputContent(uri, localname, attributes, effectiveId, inputControl, itemset, isMultiple, true, true)
    } else {

      val xhtmlPrefix = handlerContext.findXHTMLPrefix

      // Create xh:input
      if (! isStaticReadonly(inputControl)) {
        // Regular read-write mode

        val inputQName = XMLUtils.buildQName(xhtmlPrefix, "input")

        // Main input field
        {
          val inputIdName = getFirstInputEffectiveId(effectiveId)
          reusableAttributes.clear()
          reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, inputIdName)
          if (! isDateMinimal)
            reusableAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, "text")
          // Use effective id for name of first field
          reusableAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, inputIdName)
          val inputClasses = new java.lang.StringBuilder("xforms-input-input")
          if (isRelevantControl) {
            // Output value only for concrete control
            val formattedValue = inputControl.getFirstValueUseFormat
            if (!isDateMinimal) {
              // Regular case, value goes to input control
              reusableAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, Option(formattedValue) getOrElse "")
            } else {
              // "Minimal date", value goes to @alt attribute on image
              reusableAttributes.addAttribute("", "alt", "alt", XMLReceiverHelper.CDATA, Option(formattedValue) getOrElse "")
            }
            val firstType = inputControl.getFirstValueType
            if (firstType ne null) {
              inputClasses.append(" xforms-type-")
              inputClasses.append(firstType)
            }
            // Q: Not sure why we duplicate the appearances here. As of 2011-10-27, removing this
            // makes the minimal date picker fail on the client. We should be able to remove this.
            elementAnalysis match {
              case a: AppearanceTrait ⇒ a.encodeAndAppendAppearances(inputClasses)
              case _ ⇒
            }
          } else {
            reusableAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, "")
          }

          // Output xxf:* extension attributes
          if (isConcreteControl)
            inputControl.addExtensionAttributesExceptClassAndAcceptForHandler(reusableAttributes, XXFORMS_NAMESPACE_URI)

          // Add attribute even if the control is not concrete
          if ((placeHolderInfo ne null) && (placeHolderInfo.placeholder ne null))
            reusableAttributes.addAttribute("", "placeholder", "placeholder", XMLReceiverHelper.CDATA, placeHolderInfo.placeholder)

          reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, inputClasses.toString)

          handleAccessibilityAttributes(attributes, reusableAttributes)
          if (isDateMinimal) {
            val imgQName = XMLUtils.buildQName(xhtmlPrefix, "img")
            reusableAttributes.addAttribute("", "src", "src", XMLReceiverHelper.CDATA, CALENDAR_IMAGE_URI)
            reusableAttributes.addAttribute("", "title", "title", XMLReceiverHelper.CDATA, "")
            xmlReceiver.startElement(XHTML_NAMESPACE_URI, "img", imgQName, reusableAttributes)
            xmlReceiver.endElement(XHTML_NAMESPACE_URI, "img", imgQName)
          } else {
            if (isHTMLDisabled(inputControl))
              outputDisabledAttribute(reusableAttributes)

            if (isConcreteControl)
              handleAriaAttributes(inputControl.isRequired, inputControl.isValid, reusableAttributes)

            xmlReceiver.startElement(XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes)
            xmlReceiver.endElement(XHTML_NAMESPACE_URI, "input", inputQName)
          }
        }
        // Add second field for dateTime's time part
        // NOTE: In the future, we probably want to do this as an XBL component
        if (isDateTime) {
          val inputIdName = getSecondInputEffectiveId(effectiveId)
          reusableAttributes.clear()
          reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, inputIdName)
          reusableAttributes.addAttribute("", "type", "type", XMLReceiverHelper.CDATA, "text")
          reusableAttributes.addAttribute("", "src", "src", XMLReceiverHelper.CDATA, CALENDAR_IMAGE_URI)
          reusableAttributes.addAttribute("", "title", "title", XMLReceiverHelper.CDATA, "")
          reusableAttributes.addAttribute("", "alt", "alt", XMLReceiverHelper.CDATA, "")
          // TODO: Is this an appropriate name? Noscript must be able to find this
          reusableAttributes.addAttribute("", "name", "name", XMLReceiverHelper.CDATA, inputIdName)
          val inputClasses = new StringBuilder("xforms-input-input")
          if (isRelevantControl) {
            // Output value only for concrete control
            val inputValue = inputControl.getSecondValueUseFormat
            reusableAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, inputValue)
            val secondType = inputControl.getSecondValueType
            if (secondType ne null) {
              inputClasses.append(" xforms-type-")
              inputClasses.append(secondType)
            }
          }
          else {
            reusableAttributes.addAttribute("", "value", "value", XMLReceiverHelper.CDATA, "")
          }
          reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, inputClasses.toString)
          if (isHTMLDisabled(inputControl))
            outputDisabledAttribute(reusableAttributes)

          // TODO: set @size and @maxlength

          handleAccessibilityAttributes(attributes, reusableAttributes)
          xmlReceiver.startElement(XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes)
          xmlReceiver.endElement(XHTML_NAMESPACE_URI, "input", inputQName)
        }
      } else {
        // Output static read-only value
        if (isRelevantControl) {
          val atts = List("class" → "xforms-field")
          withElement("span", prefix = xhtmlPrefix, uri = XHTML_NAMESPACE_URI, atts = atts) {
            val outputValue = inputControl.getReadonlyValue
            if (outputValue ne null)
              xmlReceiver.characters(outputValue.toCharArray, 0, outputValue.length)
          }
        }
      }
    }
  }

  import XFormsUtils.namespaceId

  // Do as if this was in a component, noscript has to handle that
  private def getFirstInputEffectiveId(effectiveId: String): String =
    ! isBoolean option namespaceId(containingDocument, XFormsUtils.appendToEffectiveId(effectiveId, COMPONENT_SEPARATOR + "xforms-input-1")) orNull

  // Do as if this was in a component, noscript has to handle that
  private def getSecondInputEffectiveId(effectiveId: String): String =
    isDateTime option namespaceId(containingDocument, XFormsUtils.appendToEffectiveId(effectiveId, COMPONENT_SEPARATOR + "xforms-input-2")) orNull

  override def getForEffectiveId(effectiveId: String): String =
    isBoolean option XFormsSelect1Handler.getItemId(getEffectiveId, "0") getOrElse getFirstInputEffectiveId(getEffectiveId)

  protected override def handleLabel(): Unit =
    if (! ((placeHolderInfo ne null) && placeHolderInfo.isLabelPlaceholder))
      super.handleLabel()

  protected override def handleHint(): Unit =
    if (! ((placeHolderInfo ne null) && placeHolderInfo.isHintPlaceholder))
      super.handleHint()
}
