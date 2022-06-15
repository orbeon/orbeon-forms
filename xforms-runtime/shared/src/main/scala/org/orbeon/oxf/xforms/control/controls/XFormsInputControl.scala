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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.analysis.controls.InputControl
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl._
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsInputHandler
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsNames._
import org.xml.sax.helpers.AttributesImpl

/**
 * xf:input control
 */
class XFormsInputControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsSingleNodeControl(
  container,
  parent,
  element,
  effectiveId
) with XFormsValueControl
  with ReadonlySingleNodeFocusableTrait {

  override type Control <: InputControl

  private def format   = staticControlOpt flatMap (_.format)
  private def unformat = staticControlOpt flatMap (_.unformat)

  private def unformatTransform(v: String) = unformat match {
    case Some(expr) => evaluateAsString(expr, Seq(stringToStringValue(v)), 1) getOrElse ""
    case None       => v
  }

  override def performDefaultAction(event: XFormsEvent): Unit = {
    // The boolean input is rendered as a checkbox, so consider it visited on selection/deselection, to be consistent
    // with what are doing for other selection controls
    if (getBuiltinTypeName == "boolean" && event.isInstanceOf[XXFormsValueEvent])
      visited = true
    super.performDefaultAction(event)
  }

  override def evaluateExternalValue() : Unit = {
    assert(isRelevant)

    val internalValue = getValue
    assert(internalValue ne null)

    // TODO: format must take place between instance and internal value instead

    val typeName = getBuiltinTypeName
    val updatedValue =
      if (typeName == "boolean")
        // xs:boolean

        // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also
        // poses a problem since the server does not send an itemset for new booleans, therefore the client
        // cannot know the encrypted value of "true". So we do not encrypt values.
        normalizeBooleanString(internalValue)
      else
        // Other types or no type
        // Format only if the format attribute is present. We don't use the default formats, because we don't
        // yet have default "unformats".
        format flatMap (valueWithSpecifiedFormat(_)) getOrElse internalValue

    setExternalValue(updatedValue)
  }

  override def translateExternalValue(boundItem: om.Item, externalValue: String): Option[String] = {

    // Tricky: mark the external value as dirty if there is a format, as the client will expect an up to date
    // formatted value
    format foreach { _ =>
      markExternalValueDirty()
      containingDocument.controls.markDirtySinceLastRequest(false)
    }

    // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
    // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
    // the encrypted value of "true". So we do not encrypt values.

    Option(
      getBuiltinTypeName match {
        case "boolean"       => normalizeBooleanString(externalValue)
        case "string" | null =>
          // Replacement-based input sanitation for string type only
          containingDocument.staticState.sanitizeInput(unformatTransform(externalValue))
        case _ =>
          unformatTransform(externalValue)
      }
    )
  }

  // Convenience method for handler: return the value of the first input field.
  def getFirstValueUseFormat: String = {
    val result =
      if (isRelevant)
        externalValueOpt
      else
        None

    result getOrElse ""
  }

  override def getFormattedValue: Option[String] =
    getValueUseFormat(format) orElse Option(getExternalValue)

  def getFirstValueType: String = getBuiltinTypeName

  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControl       : Option[XFormsControl]
  ): Boolean =
    previousControl match {
      case Some(other: XFormsInputControl) =>
        valueType == other.valueType &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl)
      case _ => false
    }

  // Add type attribute if needed
  override def addAjaxAttributes(attributesImpl: AttributesImpl, previousControlOpt: Option[XFormsControl]): Boolean = {

    var added = super.addAjaxAttributes(attributesImpl, previousControlOpt)

    val previousSingleNodeControlOpt = previousControlOpt.asInstanceOf[Option[XFormsSingleNodeControl]]

    val typeValue2 = typeClarkNameOpt
    if (previousControlOpt.isEmpty || previousSingleNodeControlOpt.exists(_.typeClarkNameOpt != typeValue2)) {
      val attributeValue = typeValue2 getOrElse ""
      added |= ControlAjaxSupport.addOrAppendToAttributeIfNeeded(
        attributesImpl,
        "type",
        attributeValue,
        previousControlOpt.isEmpty,
        attributeValue == "" || StringQNames(attributeValue)
      )
    }

    added
  }

  // Input needs to point to another element
  override def findAriaByControlEffectiveIdWithNs: Option[String] =
    getBuiltinTypeName != "boolean" option XFormsInputHandler.firstInputEffectiveIdWithNs(effectiveId)(containingDocument)
}

object XFormsInputControl {

  val StringQNames = Set(XS_STRING_EXPLODED_QNAME, XFORMS_STRING_EXPLODED_QNAME)

  // Anything but "true" is "false"
  private def normalizeBooleanString(s: String) = (s == "true").toString
}