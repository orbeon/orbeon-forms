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

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.analysis.controls.SingleNodeTrait
import org.orbeon.oxf.xforms.analysis.model.{ModelDefs, StaticBind}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.XFormsEvents.XXFORMS_ITERATION_MOVED
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.model.{BindNode, InstanceData}
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{BindingContext, _}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.xforms.analysis.model.ValidationLevel.ErrorLevel
import org.xml.sax.helpers.AttributesImpl

import scala.collection.{immutable => i}

/**
* Control with a single-node binding (possibly optional). Such controls can have MIPs (properties coming from a model).
*/
abstract class XFormsSingleNodeControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsControl(container, parent, element, effectiveId)
       with VisibilityTrait {

  import XFormsSingleNodeControl._

  override type Control <: SingleNodeTrait

  private var _boundItem: Item = null
  final def boundItemOpt: Option[Item] = Option(_boundItem)
  final def boundNodeOpt: Option[NodeInfo] = boundItemOpt collect { case node: NodeInfo => node }

  final override def bindingEvenIfNonRelevant: Seq[Item] =
    if (bindingContext.newBind)
      bindingContext.singleItemOpt.toList
    else
      Nil

  // Standard MIPs
  private var _readonly = ModelDefs.DEFAULT_READONLY
  final def isReadonly = _readonly

  private var _required = ModelDefs.DEFAULT_REQUIRED
  final def isRequired = _required

  // TODO: maybe represent as case class
  //case class ValidationStatus(valid: Boolean, alertLevel: Option[ValidationLevel], failedValidations: List[StaticBind.MIP])
  //private var _validationStatus: Option[ValidationStatus] = None

  private var _valid = ModelDefs.DEFAULT_VALID
  def isValid = _valid

  // NOTE: At this time, the control only stores the constraints for a single level (the "highest" level). There is no
  // mixing of constraints among levels, like error and warning.
  private var _alertLevel: Option[ValidationLevel] = None
  def alertLevel = _alertLevel

  private var _failedValidations: List[StaticBind.MIP] = Nil
  def failedValidations = _failedValidations

  // Previous values for refresh
  private var _wasReadonly = false
  private var _wasRequired = false
  private var _wasValid = true
  private var _wasAlertLevel: Option[ValidationLevel] = None
  private var _wasFailedValidations: List[StaticBind.MIP] = Nil

  // Type
  private var _valueType: QName = null
  def valueType: QName = _valueType
  def valueTypeOpt = Option(valueType)

  // Custom MIPs
  private var _customMIPs = Map.empty[String, String]
  def customMIPs: Map[String, String] = _customMIPs
  def customMIPsClasses: i.Iterable[String] = customMIPs map { case (k, v) => ModelDefs.buildExternalCustomMIPName(k) + '-' + v }

  override def onDestroy(update: Boolean): Unit = {
    super.onDestroy(update)
    // Set default MIPs so that diff picks up the right values
    setDefaultMIPs()
  }

  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean): Unit = {
    super.onCreate(restoreState, state, update)

    readBinding()

    _wasReadonly = false
    _wasRequired = false
    _wasValid = true
  }

  override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = {
    super.onBindingUpdate(oldBinding, newBinding)
    readBinding()
  }

  private def readBinding(): Unit = {
    // Set bound item, only considering actual bindings (with @bind, @ref or @nodeset)
    val bc = bindingContext
    if (bc.newBind)
      this._boundItem = bc.getSingleItemOrNull

    // Get MIPs
    this._boundItem match {
      case nodeInfo: NodeInfo =>
        // Control is bound to a node - get model item properties
        this._readonly  = InstanceData.getInheritedReadonly(nodeInfo)
        this._required  = InstanceData.getRequired(nodeInfo)
        this._valueType = InstanceData.getType(nodeInfo)

        // Validation
        if ((staticControl eq null) || ! staticControl.explicitValidation)
          readValidation foreach setValidation

        // Custom MIPs
        this._customMIPs = InstanceData.collectAllClientCustomMIPs(nodeInfo) map (_.toMap) getOrElse Map()

      case _: AtomicValue =>
        // Control is not bound to a node (i.e. bound to an atomic value)
        setAtomicValueMIPs()
      case _ =>
        // Control is not bound to a node because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
        setDefaultMIPs()
    }
  }

  def getValidation: Option[(Boolean, Option[ValidationLevel], List[StaticBind.MIP])] =
    this._boundItem match {
      case _: NodeInfo => Some((_valid, _alertLevel, _failedValidations))
      case _           => None
    }

  def setValidation(validation: (Boolean, Option[ValidationLevel], List[StaticBind.MIP])): Unit = {
    this._valid             = validation._1
    this._alertLevel        = validation._2
    this._failedValidations = validation._3
  }

  def readValidation: Option[(Boolean, Option[ValidationLevel], List[StaticBind.MIP])] =
    this._boundItem match {
      case nodeInfo: NodeInfo =>

        val nodeValid = InstanceData.getValid(nodeInfo)

        // Instance data stores all failed validations
        // If there is any type, required, or failed constraint, we have at least one failed failedValidation
        // returned. The only exception is schema type validation, which can cause the node to be invalid, yet
        // doesn't have an associated validation because it doesn't have a MIP. Conceivably, something could be
        // associated with a type validation error.
        val nodeFailedValidations = BindNode.failedValidationsForHighestLevelPrioritizeRequired(nodeInfo)

        Some(
          (
            nodeValid,
            nodeFailedValidations map (_._1) orElse (! nodeValid option ErrorLevel),
            nodeFailedValidations map (_._2) getOrElse Nil
          )
        )

      case _ =>
        None
    }

  private def setAtomicValueMIPs(): Unit = {
    setDefaultMIPs()
    this._readonly = true
  }

  private def setDefaultMIPs(): Unit = {
    this._readonly          = ModelDefs.DEFAULT_READONLY
    this._required          = ModelDefs.DEFAULT_REQUIRED
    this._valid             = ModelDefs.DEFAULT_VALID
    this._valueType         = null
    this._customMIPs        = Map.empty[String, String]

    this._alertLevel        = None
    this._failedValidations = Nil
  }

  override def commitCurrentUIState(): Unit = {
    super.commitCurrentUIState()

    isValueChangedCommit()
    wasRequiredCommit()
    wasReadonlyCommit()
    wasValidCommit()
    wasFailedValidationsCommit()
  }

  // Single-node controls support refresh events
  override def supportsRefreshEvents = true

  final def wasReadonlyCommit(): Boolean = {
    val result = _wasReadonly
    _wasReadonly = _readonly
    result
  }

  final def wasRequiredCommit(): Boolean = {
    val result = _wasRequired
    _wasRequired = _required
    result
  }

  final def wasValidCommit(): Boolean = {
    val result = _wasValid
    _wasValid = _valid
    result
  }

  final def wasAlertLevelCommit() = {
    val result = _wasAlertLevel
    _wasAlertLevel = _alertLevel
    result
  }

  final def wasFailedValidationsCommit() = {
    val result = _wasFailedValidations
    _wasFailedValidations = _failedValidations
    result
  }

  def isValueChangedCommit() = false // TODO: move this to trait shared by value control and variable
  def typeClarkNameOpt: Option[String] = valueTypeOpt map (_.clarkName)

  // Convenience method to return the local name of a built-in XML Schema or XForms type.
  def getBuiltinTypeNameOpt =
    Option(valueType) filter
    (valueType => Set(XSD_URI, XFORMS_NAMESPACE_URI)(valueType.namespace.uri)) map
    (_.localName)

  def getBuiltinTypeName = getBuiltinTypeNameOpt.orNull

  // Convenience method to return the local name of the XML Schema type.
  def getTypeLocalNameOpt = Option(valueType) map (_.localName)

  def getBuiltinOrCustomTypeCSSClassOpt =
    (getBuiltinTypeNameOpt map ("xforms-type-" +)) orElse (getTypeLocalNameOpt map ("xforms-type-custom-" +))
  override def computeRelevant: Boolean = {
    // If parent is not relevant then we are not relevant either
    if (! super.computeRelevant)
      return false

    val bc = bindingContext
    val currentItem = bc.getSingleItemOrNull
    if (bc.newBind) {
      // There is a binding

      isAllowedBoundItem(currentItem) && isRelevantItem(currentItem)
    } else {
      // Control is not bound because it doesn't have a binding
      // If the binding is optional (group, trigger, dialog, etc. without @ref), the control is relevant,
      // otherwise there is a binding error and the control is marked as not relevant.
      // If staticControl is missing, consider the control relevant too (we're not happy with this but we have to
      // deal with it for now).
      (staticControl eq null) || staticControl.isBindingOptional
    }
  }

  // Allow override only for dangling `XFormsOutputControl`
  def isAllowedBoundItem(item: Item): Boolean = staticControl.isAllowedBoundItem(item)

  // NOTE: We don't compare the type here because only some controls (xf:input) need to tell the client
  // about value type changes.
  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControlOpt    : Option[XFormsControl]
  ): Boolean =
    previousControlOpt match {
      case Some(other: XFormsSingleNodeControl) =>
        isReadonly == other.isReadonly &&
        isRequired == other.isRequired &&
        isValid    == other.isValid    &&
        alertLevel == other.alertLevel &&
        customMIPs == other.customMIPs &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControlOpt)
      case _ => false
    }

  // Static read-only if we are read-only and static (global or local setting)
  override def isStaticReadonly = isReadonly && hasStaticReadonlyAppearance

  def hasStaticReadonlyAppearance =
    containingDocument.staticReadonly ||
      XFormsProperties.ReadonlyAppearanceStaticValue == element.attributeValue(XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME)

  override def outputAjaxDiff(
    previousControlOpt    : Option[XFormsControl],
    content               : Option[XMLReceiverHelper => Unit])(implicit
    ch                    : XMLReceiverHelper
  ): Unit = {

    val atts = new AttributesImpl

    // Add attributes
    val doOutputElement = addAjaxAttributes(atts, previousControlOpt)

    if (doOutputElement || content.isDefined) {
      ch.startElement("xxf", XXFORMS_NAMESPACE_URI, "control", atts)
      content foreach (_(ch))
      ch.endElement()
    }

    addExtensionAttributesExceptClassAndAcceptForAjax(previousControlOpt, "")
  }

  override def addAjaxAttributes(attributesImpl: AttributesImpl, previousControl: Option[XFormsControl]) = {

    val control1Opt = previousControl.asInstanceOf[Option[XFormsSingleNodeControl]]
    val control2    = this

    var added = super.addAjaxAttributes(attributesImpl, previousControl)

    added |= addAjaxMIPs(attributesImpl, control1Opt, control2)

    added
  }

  private def addAjaxMIPs(
    attributesImpl : AttributesImpl,
    control1Opt    : Option[XFormsSingleNodeControl],
    control2       : XFormsSingleNodeControl
  ): Boolean = {

    var added = false

    def addAttribute(name: String, value: String) = {
      attributesImpl.addAttribute("", name, name, XMLReceiverHelper.CDATA, value)
      added = true
    }

    if (control1Opt.isEmpty && control2.isReadonly || control1Opt.exists(_.isReadonly != control2.isReadonly))
      addAttribute(READONLY_ATTRIBUTE_NAME, control2.isReadonly.toString)

    if (control1Opt.isEmpty && control2.isRequired || control1Opt.exists(_.isRequired != control2.isRequired))
      addAttribute(REQUIRED_ATTRIBUTE_NAME, control2.isRequired.toString)

    if (control1Opt.isEmpty && ! control2.isRelevant || control1Opt.exists(_.isRelevant != control2.isRelevant))
      addAttribute(RELEVANT_ATTRIBUTE_NAME, control2.isRelevant.toString)

    if (control1Opt.isEmpty && control2.alertLevel.isDefined || control1Opt.exists(_.alertLevel != control2.alertLevel))
      addAttribute(CONSTRAINT_LEVEL_ATTRIBUTE_NAME, control2.alertLevel map (_.entryName) getOrElse "")

    added |= addAjaxCustomMIPs(attributesImpl, control1Opt, control2)

    added
  }

  override def writeMIPs(write: (String, String) => Unit): Unit = {
    super.writeMIPs(write)

    write("valid",            isValid.toString)
    write("read-only",        isReadonly.toString)
    write("static-read-only", isStaticReadonly.toString)
    write("required",         isRequired.toString)

    // Output custom MIPs classes
    for ((name, value) <- customMIPs)
      write(ModelDefs.buildExternalCustomMIPName(name), value)

    // Output type class
    (getBuiltinTypeNameOpt map ("xforms-type" ->))        orElse
      (getTypeLocalNameOpt map ("xforms-type-custom" ->)) foreach
      write.tupled
  }

  // Dispatch creation events
  override def dispatchCreationEvents(): Unit = {
    super.dispatchCreationEvents()

    // MIP events
    if (isRequired)
      Dispatch.dispatchEvent(new XFormsRequiredEvent(this))

    if (isReadonly)
      Dispatch.dispatchEvent(new XFormsReadonlyEvent(this))

    if (! isValid)
      Dispatch.dispatchEvent(new XFormsInvalidEvent(this))
  }

  // NOTE: For the purpose of dispatching value change and MIP events, we used to make a
  // distinction between value controls and plain single-node controls. However it seems that it is
  // still reasonable to dispatch those events to xf:group, xf:switch, and even repeat
  // iterations if they are bound.
  override def dispatchChangeEvents(): Unit = {

    super.dispatchChangeEvents()

    // Gather changes
    // 2013-06-20: This is a change from what we were doing before, but it makes things clearer. Later we might
    // gather all events upon onCreate/onDestroy/onBindingUpdate. The behavior can change if a new refresh is
    // triggered when processing one of the events below. The order of events in that case is hard to predict.
    val valueChanged        = isValueChangedCommit()
    val iterationMoved      = previousEffectiveIdCommit() != getEffectiveId && part.observerHasHandlerForEvent(getPrefixedId, XXFORMS_ITERATION_MOVED)
    val validityChanged     = wasValidCommit()            != isValid
    val requiredChanged     = wasRequiredCommit()         != isRequired
    val readonlyChanged     = wasReadonlyCommit()         != isReadonly

    val previousValidations = wasFailedValidationsCommit()
    val validationsChanged  = previousValidations         != failedValidations

    // This is needed because, unlike the other LHH, the alert doesn't only depend on its expressions: it also depends
    // on the control's current validity and validations. Because we don't have yet a way of taking those in as
    // dependencies, we force dirty alerts whenever such validations change upon refresh.
    if (validityChanged || validationsChanged)
      forceDirtyAlert()

    // Value change
    if (isRelevant && valueChanged)
      Dispatch.dispatchEvent(new XFormsValueChangeEvent(this))

    // Iteration change
    if (isRelevant && iterationMoved)
      Dispatch.dispatchEvent(new XXFormsIterationMovedEvent(this)) // NOTE: should have context info

    // MIP change
    if (isRelevant && validityChanged)
      Dispatch.dispatchEvent(if (isValid) new XFormsValidEvent(this) else new XFormsInvalidEvent(this))

    if (isRelevant && requiredChanged)
      Dispatch.dispatchEvent(if (isRequired) new XFormsRequiredEvent(this) else new XFormsOptionalEvent(this))

    if (isRelevant && readonlyChanged)
      Dispatch.dispatchEvent(if (isReadonly) new XFormsReadonlyEvent(this) else new XFormsReadwriteEvent(this))

    if (isRelevant && validationsChanged)
      Dispatch.dispatchEvent(new XXFormsConstraintsChangedEvent(this, alertLevel, previousValidations, failedValidations))
  }
}

object XFormsSingleNodeControl {

  // Item relevance (atomic values are considered relevant)
  def isRelevantItem(item: Item): Boolean =
    item match {
      case info: NodeInfo => InstanceData.getInheritedRelevant(info)
      case _              => true
    }

  // Convenience method to figure out when a control is relevant, assuming a "null" control is non-relevant.
  def isRelevant(control: XFormsSingleNodeControl): Boolean = Option(control) exists (_.isRelevant)

  // NOTE: Similar to AjaxSupport.addAjaxClasses. Should unify handling of classes.
  def addAjaxCustomMIPs(
    attributesImpl : AttributesImpl,
    control1Opt    : Option[XFormsSingleNodeControl],
    control2       : XFormsSingleNodeControl
  ): Boolean = {

    val customMIPs2 = control2.customMIPs

    def addOrAppend(s: String) =
      ControlAjaxSupport.addOrAppendToAttributeIfNeeded(attributesImpl, "class", s, control1Opt.isEmpty, s == "")

    // This attribute is a space-separate list of class names prefixed with either '-' or '+'
    control1Opt match {
      case None =>
        // Add all classes
        addOrAppend(control2.customMIPsClasses map ('+' + _) mkString " ")
      case Some(control1) if control1.customMIPs != customMIPs2 =>
        // Custom MIPs changed
        val customMIPs1 = control1.customMIPs

        def diff(mips1: Map[String, String], mips2: Map[String, String], plusOrMinusPrefix: Char) =
          for {
            (name, value1) <- mips1
            value2         = mips2.get(name)
            if Option(value1) != value2
          } yield
            plusOrMinusPrefix + ModelDefs.buildExternalCustomMIPName(name) + '-' + value1 // TODO: encode so that there are no spaces

        val classesToRemove = diff(customMIPs1, customMIPs2, '-')
        val classesToAdd    = diff(customMIPs2, customMIPs1, '+')

        addOrAppend(classesToRemove ++ classesToAdd mkString " ")
      case _ =>
        false
    }
  }
}
