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

import org.orbeon.dom.{Element, Namespace, QName}
import org.orbeon.oxf.xforms.analysis.controls.InputControl
import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.control.controls.XFormsInputControl.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.om
import org.orbeon.xforms.Namespaces
import org.orbeon.xforms.XFormsNames.*
import org.xml.sax.helpers.AttributesImpl


/**
 * xf:input control
 */
class XFormsInputControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  _effectiveId: String
) extends XFormsSingleNodeControl(
  container,
  parent,
  element,
  _effectiveId
) with XFormsValueControl
  with ReadonlySingleNodeFocusableTrait
  with WithFormatTrait
  with WithUnformatTrait {

  override type Control <: InputControl

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = {
    // The boolean input is rendered as a checkbox, so consider it visited on selection/deselection, to be consistent
    // with what are doing for other selection controls
    if (getBuiltinTypeName == "boolean" && event.isInstanceOf[XXFormsValueEvent])
      visited = true
    super.performDefaultAction(event, collector)
  }

  override def evaluateExternalValue(collector: ErrorEventCollector): Unit = {
    assert(isRelevant)

    val internalValue = getValue(collector)
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
        maybeEvaluateWithFormat(collector).getOrElse(internalValue)

    setExternalValue(updatedValue)
  }

  override def translateExternalValue(
    boundItem    : om.Item,
    externalValue: String,
    collector    : ErrorEventCollector
  ): Option[String] = {

    markExternalValueDirtyIfHasFormat()

    // NOTE: We have decided that it did not make much sense to encrypt the value for boolean. This also poses
    // a problem since the server does not send an itemset for new booleans, therefore the client cannot know
    // the encrypted value of "true". So we do not encrypt values.

    Option(
      getBuiltinTypeName match {
        case "boolean"       => normalizeBooleanString(externalValue)
        case "string" | null =>
          // Replacement-based input sanitation for string type only
          containingDocument.staticState.sanitizeInput(unformatTransform(externalValue, collector))
        case _ =>
          unformatTransform(externalValue, collector)
      }
    )
  }

  // Convenience method for handler: return the value of the first input field.
  def getFirstValueUseFormat(collector: ErrorEventCollector): String = {
    val result =
      if (isRelevant)
        externalValueOpt(collector)
      else
        None

    result getOrElse ""
  }

  override def getFormattedValue(collector: ErrorEventCollector): Option[String] =
    maybeEvaluateWithFormatOrDefaultFormat(collector).orElse(Option(getExternalValue(collector)))

  def getFirstValueType: String = getBuiltinTypeName

  override def compareExternalUseExternalValue(
    previousExternalValue: Option[String],
    previousControl      : Option[XFormsControl],
    collector            : ErrorEventCollector
  ): Boolean =
    previousControl match {
      case Some(other: XFormsInputControl) =>
        valueType == other.valueType &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControl, collector)
      case _ => false
    }

  // Add type attribute if needed
  override def addAjaxAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {

    var added = super.addAjaxAttributes(attributesImpl, previousControlOpt, collector)

    val previousSingleNodeControlOpt = previousControlOpt.asInstanceOf[Option[XFormsSingleNodeControl]]

    val typeValue2 = typeClarkNameOpt
    if (previousControlOpt.isEmpty || previousSingleNodeControlOpt.exists(_.typeClarkNameOpt != typeValue2)) {
      val attributeValue = typeValue2 getOrElse ""
      added |= ControlAjaxSupport.addOrAppendToAttributeIfNeeded(
        attributesImpl,
        "type",
        attributeValue,
        previousControlOpt.isEmpty,
        attributeValue == "" || StringClarkNames(attributeValue)
      )
    }

    added
  }
}

private object XFormsInputControl {

  private val XsStringClarkName = {

    // NOTE: Copied from `XMLConstants` for now as we don't yet have a common place for it. Maybe `xml-common` subproject?
    val XSD_PREFIX = "xs"
    val XSD_NAMESPACE = Namespace(XSD_PREFIX, Namespaces.XS)
    val XS_STRING_QNAME = QName("string", XSD_NAMESPACE)

    XS_STRING_QNAME.clarkName
  }

  private val XFormsStringClarkName = XFORMS_STRING_QNAME.clarkName

  val StringClarkNames = Set(XsStringClarkName, XFormsStringClarkName)

  // Anything but "true" is "false"
  private def normalizeBooleanString(s: String) = (s == "true").toString
}