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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.FunctionContext
import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.xforms.analysis.controls.{FormatTrait, StaticLHHASupport, ValueTrait, ViewTrait}
import org.orbeon.oxf.xforms.control.XFormsValueControl._
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.events.XXFormsValueEvent
import org.orbeon.oxf.xforms.model.{DataModel, XFormsModelBinds}
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler
import org.orbeon.oxf.xforms.processor.handlers.xhtml.XFormsBaseHandlerXHTML
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.{XMLReceiver, XMLReceiverHelper}
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xml.NamespaceMapping
import org.xml.sax.helpers.AttributesImpl

import java.{util => ju}


// Trait for for all controls that hold a value
trait XFormsValueControl extends XFormsSingleNodeControl {

  override type Control <: ViewTrait with StaticLHHASupport with ValueTrait with FormatTrait

  // Value
  private[XFormsValueControl] var _value: String = null // TODO: use ControlProperty<String>?

  // Previous value for refresh
  private[XFormsValueControl] var _previousValue: String = null

  // External value (evaluated lazily)
  private[XFormsValueControl] var isExternalValueEvaluated: Boolean = false
  private[XFormsValueControl] var externalValue: String = null

  def handleExternalValue = true

  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean, collector: ErrorEventCollector): Unit = {
    super.onCreate(restoreState, state, update, collector)

    _value = null
    _previousValue = null

    markExternalValueDirty()
  }

  final override def preEvaluateImpl(relevant: Boolean, parentRelevant: Boolean, collector: ErrorEventCollector): Unit = {

    super.preEvaluateImpl(relevant, parentRelevant, collector) // 2019-09-13: `super` is a NOP

    // Evaluate control values
    if (relevant) {
      // Control is relevant
      // NOTE: Ugly test on staticControl is to handle the case of `xf:output` within LHHA
      if ((_value eq null) || (staticControl eq null) || containingDocument.xpathDependencies.requireValueUpdate(staticControl, effectiveId)) {
        _value = null
        markExternalValueDirty()
      }
    } else {
      // Control is not relevant
      isExternalValueEvaluated = true
      externalValue = null
      _value = null
    }

    // NOTE: We no longer evaluate the external value here, instead we do lazy evaluation. This is good in particular when there
    // are multiple refreshes during an Ajax request, and LHHA values are only needed in the end.
  }

  def computeValue(collector: ErrorEventCollector): String =
    boundItemOpt map DataModel.getValue getOrElse (throw new IllegalStateException)

  // Lazily return the control's internal value. Laziness is only allowed during the refresh process, before
  // events are dispatched. See https://github.com/orbeon/orbeon-forms/issues/4117
  final def getValue(collector: ErrorEventCollector): String = {
    if (isRelevant && (_value eq null))
      _value = computeValue(collector)

    _value
  }

  final def valueOpt(collector: ErrorEventCollector): Option[String] =
    Option(getValue(collector))

  final def isEmptyValue(collector: ErrorEventCollector): Boolean = XFormsModelBinds.isEmptyValue(getValue(collector))

  def evaluateExternalValue(collector: ErrorEventCollector): Unit =
    setExternalValue(
      if (handleExternalValue)
        getValue(collector) // by default, same as value
      else
        null
    )

  final protected def markExternalValueDirty(): Unit = {
    isExternalValueEvaluated = false
    externalValue = null
  }

  final protected def isExternalValueDirty: Boolean =
    ! isExternalValueEvaluated

  override def isValueChangedCommit(): Boolean = {
    val result = _previousValue != _value
    _previousValue = _value
    result
  }

  // This usually doesn't need to be overridden (only XFormsUploadControl as of 2012-08-15; 2019-09-04)
  def storeExternalValue(externalValue: String, collector: ErrorEventCollector): Unit =
    if (handleExternalValue)
      doStoreExternalValue(externalValue, collector)
    else
      throw new OXFException("operation not allowed")

  // Subclasses can override this to translate the incoming external value
  def translateExternalValue(
    boundItem    : om.Item,
    externalValue: String,
    collector    : ErrorEventCollector
  ): Option[String] = Option(externalValue)

  // Set the external value into the instance
  final def doStoreExternalValue(externalValue: String, collector: ErrorEventCollector): Unit = {
    // NOTE: Standard value controls should be bound to simple content only. Is there anything we should / can do
    // about this? See: https://github.com/orbeon/orbeon-forms/issues/13

    boundNodeOpt match {
      case None =>
        // This should not happen
        // `collector()`?
        throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
      case Some(boundNode) =>
        translateExternalValue(boundNode, externalValue, collector) foreach { translatedValue =>
          DataModel.setValueIfChangedHandleErrors(
            eventTarget  = this,
            locationData = getLocationData,
            nodeInfo     = boundNode,
            valueToSet   = translatedValue,
            source       = "client",
            isCalculate  = false,
            collector    = collector
          )
        }
    }

    // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
    // the controls as dirty, and they will be evaluated when necessary later.
  }

  final protected def getValueUseFormat(
    format             : Option[String],
    collector          : ErrorEventCollector,
    namespaceMapping   : NamespaceMapping                        = getNamespaceMappings,
    variableToValueMap : ju.Map[String, ValueRepresentationType] = bindingContext.getInScopeVariables
  ): Option[String] =
    format
      .flatMap(valueWithSpecifiedFormat(_, collector, namespaceMapping, variableToValueMap))
      .orElse(valueWithDefaultFormat(collector))

  // Formatted value for read-only output
  def getFormattedValue(collector: ErrorEventCollector): Option[String] = Option(getExternalValue(collector))

  // Format value according to format attribute
  final protected def valueWithSpecifiedFormat(
    format             : String,
    collector          : ErrorEventCollector,
    namespaceMapping   : NamespaceMapping                        = getNamespaceMappings,
    variableToValueMap : ju.Map[String, ValueRepresentationType] = bindingContext.getInScopeVariables,
    functionContext    : FunctionContext                         = newFunctionContext
  ): Option[String] = {

    assert(isRelevant)
    assert(getValue(collector) ne null)

    evaluateAsString(
      xpathString        = format,
      contextItems       = List(stringToStringValue(getValue(collector))),
      contextPosition    = 1,
      collector          = collector,
      contextMessage     = "formatting value",
      namespaceMapping   = namespaceMapping,
      variableToValueMap = variableToValueMap,
      functionContext    = functionContext
    )
  }

  // Try default format for known types
  final protected def valueWithDefaultFormat(collector: ErrorEventCollector): Option[String] = {

    assert(isRelevant)
    assert(getValue(collector) ne null)

    def evaluateFormat(format: String): Option[String] =
      evaluateAsString(
        xpathString        = format,
        contextItems       = List(stringToStringValue(getValue(collector))),
        contextPosition    = 1,
        collector          = collector,
        contextMessage     = "formatting value",
        namespaceMapping   = FormatNamespaceMapping,
        variableToValueMap = bindingContext.getInScopeVariables
      )

    for {
      typeName <- getBuiltinTypeNameOpt
      format   <- containingDocument.getTypeOutputFormat(typeName)
      value    <- evaluateFormat(format)
    } yield
      value
  }

  /**
   * Return the control's external value is the value as exposed to the UI layer.
   */
  final def getExternalValue(collector: ErrorEventCollector): String = {
    if (! isExternalValueEvaluated) {
      if (isRelevant)
        evaluateExternalValue(collector)
      else
        // NOTE: if the control is not relevant, nobody should ask about this in the first place
        setExternalValue(null)

      isExternalValueEvaluated = true
    }
    externalValue
  }

  final def externalValueOpt(collector: ErrorEventCollector): Option[String] = Option(getExternalValue(collector))

  // Return the external value ready to be inserted into the client after an Ajax response.
  // 2019-09-05: Used only by `xf:output`, `xxf:attribute`, and external LHHA. Otherwise this is the
  // same as `getExternalValue`,
  protected def getRelevantEscapedExternalValue(collector: ErrorEventCollector): String = getExternalValue(collector)
  protected def getNonRelevantEscapedExternalValue = ""

  final def getEscapedExternalValue(collector: ErrorEventCollector): String =
    if (isRelevant)
      // NOTE: Not sure if it is still possible to have a null value when the control is relevant
      Option(getRelevantEscapedExternalValue(collector)) getOrElse ""
    else
      // Some controls don't have "" as non-relevant value
      getNonRelevantEscapedExternalValue

  protected final def setValue(value: String): Unit =
    this._value = value

  protected final def setExternalValue(externalValue: String): Unit =
    this.externalValue = externalValue

  override def getBackCopy(collector: ErrorEventCollector): AnyRef = {
    // Evaluate lazy values
    getExternalValue(collector)
    super.getBackCopy(collector)
  }

  final override def compareExternalMaybeClientValue(
    previousValueOpt   : Option[String],
    previousControlOpt : Option[XFormsControl],
    collector          : ErrorEventCollector
  ): Boolean =
    // NOTE: Call `compareExternalUseExternalValue` directly so as to avoid check on
    // `(previousControlOpt exists (_ eq this)) && (getInitialLocal eq getCurrentLocal)` which
    // causes part of https://github.com/orbeon/orbeon-forms/issues/2857.
    compareExternalUseExternalValue(previousValueOpt orElse (previousControlOpt flatMap (_.asInstanceOf[XFormsValueControl].externalValueOpt(collector))), previousControlOpt, collector)

  override def compareExternalUseExternalValue(
    previousExternalValue: Option[String],
    previousControl      : Option[XFormsControl],
    collector            : ErrorEventCollector
  ): Boolean =
    handleExternalValue && (
      previousControl match {
        case Some(other: XFormsValueControl) =>
          previousExternalValue == externalValueOpt(collector) &&
            super.compareExternalUseExternalValue(previousExternalValue, previousControl, collector)
        case _ => false
      }
    )

  final def outputAjaxDiffMaybeClientValue(
    clientValue    : Option[String],
    previousControl: Option[XFormsValueControl],
    collector      : ErrorEventCollector
  )(implicit
    receiver       : XMLReceiver
  ): Unit =
    outputAjaxDiffUseClientValue(
      if (handleExternalValue)
        clientValue orElse (previousControl map (_.getExternalValue(collector)))
      else
        None,
      previousControl,
      None,
      collector
    )(
      new XMLReceiverHelper(receiver)
    )

  def mustOutputAjaxValueChange(
    previousValue   : Option[String],
    previousControl : Option[XFormsValueControl],
    collector       : ErrorEventCollector
  ): Boolean =
    previousControl.isEmpty && getEscapedExternalValue(collector) != getNonRelevantEscapedExternalValue ||
    previousControl.nonEmpty && ! (previousValue contains getExternalValue(collector))                             ||
    (previousControl exists (! _.isReadonly)) && isReadonly // https://github.com/orbeon/orbeon-forms/issues/3130

  def outputAjaxDiffUseClientValue(
    previousValue   : Option[String],
    previousControl : Option[XFormsValueControl],
    content         : Option[XMLReceiverHelper => Unit],
    collector       : ErrorEventCollector
  )(implicit
    ch              : XMLReceiverHelper
  ): Unit = {

    val hasNestedValueContent =
      handleExternalValue && mustOutputAjaxValueChange(previousValue, previousControl, collector)

    val hasNestedContent =
      content.isDefined || hasNestedValueContent

    val outputNestedContent = (ch: XMLReceiverHelper) => {

      content foreach (_(ch))

      if (hasNestedValueContent)
        outputValueElement(
          attributesImpl = new AttributesImpl,
          elementName    = "value",
          value          = getEscapedExternalValue(collector)
        )(ch)
    }

    super.outputAjaxDiff(
      previousControl,
      hasNestedContent option outputNestedContent,
      collector
    )
  }

  // Can be overridden by subclasses
  // This is the effective id of the element which may have an `aria-labelledby`, etc. attribute. So an `<input>`, `<textarea>`,
  // etc. HTML element id. This is needed so that the Ajax update can know how to update the attribute.
  def findAriaByControlEffectiveIdWithNs: Option[String] =
    Some(XFormsBaseHandler.getLHHACIdWithNs(containingDocument, effectiveId, XFormsBaseHandlerXHTML.ControlCode))

  protected def outputValueElement(
    attributesImpl : AttributesImpl,
    elementName    : String,
    value          : String)(implicit
    ch             : XMLReceiverHelper
  ): Unit = {
    ch.startElement("xxf", XXFORMS_NAMESPACE_URI, elementName, attributesImpl)
    if (value.nonEmpty)
      ch.text(value)
    ch.endElement()
  }

  override def performDefaultAction(event: XFormsEvent, collector: ErrorEventCollector): Unit = event match {
    case xxformsValue: XXFormsValueEvent => storeExternalValue(xxformsValue.value, collector)
    case _ => super.performDefaultAction(event, collector)
  }

//  override def writeMIPs(write: (String, String) => Unit): Unit = {
//    super.writeMIPs(write)
//
//    if (isRequired)
//      write("required-and-empty", XFormsModelBinds.isEmptyValue(getValue(collector)).toString)
//  }

  override def addAjaxAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {

    var added = super.addAjaxAttributes(attributesImpl, previousControlOpt, collector)

    // NOTE: We should really have a general mechanism to add/remove/diff classes.
    if (isRequired) {

      val control1Opt = previousControlOpt.asInstanceOf[Option[XFormsValueControl]]
      val control2    = this

      val control2IsEmptyValue = control2.isEmptyValue(collector)

      if (control1Opt.isEmpty || control1Opt.exists(control1 => ! control1.isRequired || control1.isEmptyValue(collector) != control2IsEmptyValue)) {
        attributesImpl.addAttribute("", "empty", "empty", XMLReceiverHelper.CDATA, control2IsEmptyValue.toString)
        added = true
      }
    }

    added
  }
}

object XFormsValueControl {

  val FormatNamespaceMapping =
    NamespaceMapping(
      Map(
        XSD_PREFIX           -> XSD_URI,
        XFORMS_PREFIX        -> XFORMS_NAMESPACE_URI,
        XFORMS_SHORT_PREFIX  -> XFORMS_NAMESPACE_URI,
        XXFORMS_PREFIX       -> XXFORMS_NAMESPACE_URI,
        XXFORMS_SHORT_PREFIX -> XXFORMS_NAMESPACE_URI,
        EXFORMS_PREFIX       -> EXFORMS_NAMESPACE_URI
      )
    )
}