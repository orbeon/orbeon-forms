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

import java.{util => ju}

import org.orbeon.dom.Element
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis.controls.{CaseControl, SwitchControl}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.{XFormsDeselectEvent, XFormsSelectEvent}
import org.orbeon.oxf.xforms.model.{DataModel, StaticDataModel}
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsId

/**
 * Represents an xf:switch container control.
 *
 * NOTE: This keep the "currently selected flag" for all children xf:case.
 */
class XFormsSwitchControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId) {

  override type Control <: SwitchControl

  // Initial local state
  setLocal(new XFormsSwitchControlLocal)

  private var _caserefBinding: Option[om.Item] = None

  // NOTE: state deserialized -> state previously serialized -> control was relevant -> onCreate() called
  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean): Unit = {
    super.onCreate(restoreState, state, update)

    _caserefBinding = evaluateCaseRefBinding

    // Ensure that the initial state is set, either from default value, or for state deserialization.
    state match {
      case Some(state) =>
        setLocal(new XFormsSwitchControlLocal(state.keyValues("case-id")))
      case None if restoreState =>
        // This can happen with xxf:dynamic, which does not guarantee the stability of ids, therefore state for a
        // particular control might not be found.
        setLocal(new XFormsSwitchControlLocal(findInitialSelectedCaseId))
      case None =>
        val local = getLocalForUpdate.asInstanceOf[XFormsSwitchControlLocal]
        local.selectedCaseControlId = findInitialSelectedCaseId
        // TODO: Deferred event dispatch for xforms-select/deselect?
        // See https://github.com/orbeon/orbeon-forms/issues/3496
    }
  }

  override def onBindingUpdate(oldBinding: BindingContext, newBinding: BindingContext): Unit = {
    super.onBindingUpdate(oldBinding, newBinding)

    _caserefBinding = evaluateCaseRefBinding

    if (staticControl.caseref.isDefined) {
      val newCaseId = caseIdFromCaseRefBinding getOrElse firstCaseId
      val local     = getLocalForUpdate.asInstanceOf[XFormsSwitchControlLocal]

//            val previouslySelectedCaseControl = selectedCase.get
      local.selectedCaseControlId = newCaseId
    }

    // TODO: Deferred event dispatch for xforms-select/deselect and xxforms-visible/hidden.
    // See https://github.com/orbeon/orbeon-forms/issues/3496
  }

  private def evaluateCaseRefBinding: Option[om.Item] =
    staticControl.caseref flatMap { caseref =>

      val caserefItem =
        Option(
          XPathCache.evaluateSingleKeepItems(
            contextItems       = bindingContext.nodeset,
            contextPosition    = bindingContext.position,
            xpathString        = caseref,
            namespaceMapping   = staticControl.namespaceMapping,
            variableToValueMap = bindingContext.getInScopeVariables,
            functionLibrary    = containingDocument.functionLibrary,
            functionContext    = newFunctionContext,
            baseURI            = null,
            locationData       = staticControl.locationData,
            reporter           = containingDocument.getRequestStats.getReporter
          )
        )

      caserefItem collect {
        case item if StaticDataModel.isAllowedValueBoundItem(item) => item
      }

      // TODO: deferred event dispatch for xforms-binding-error EXCEPT upon restoring state???
      // See https://github.com/orbeon/orbeon-forms/issues/3496
    }

  // "If the caseref attribute is specified, then it takes precedence over the selected attributes of the case
  // elements"
  private def findInitialSelectedCaseId =
    if (staticControl.caseref.isDefined)
      caseIdFromCaseRefBinding getOrElse firstCaseId
    else
      caseIdFromSelected getOrElse firstCaseId

  private def caseIdFromCaseRefBinding: Option[String] =
    _caserefBinding flatMap { item =>
      Some(item.getStringValue) flatMap caseForValue map (_.staticId)
    }

  // The value associated with a given xf:case can come from:
  //
  // - a literal string specified with @value (this is an optimization)
  // - a dynamic expression specified with @value
  // - the case id
  //
  // NOTE: A nested xf:value element should also be supported for consistency with xf:item.
  //
  private def caseValue(c: CaseControl) = {

    def fromLiteral(c: CaseControl) =
      c.valueLiteral

    // FIXME: The expression is evaluated in the context of xf:switch, when in fact it should be evaluated in the
    // context of the xf:case, including variables and FunctionContext.
    def fromExpression(c: CaseControl) = c.valueExpression flatMap { expr =>
      XPathCache.evaluateAsStringOpt(
        contextItems       = bindingContext.nodeset,
        contextPosition    = bindingContext.position,
        xpathString        = expr,
        namespaceMapping   = c.namespaceMapping,
        variableToValueMap = bindingContext.getInScopeVariables,
        functionLibrary    = containingDocument.functionLibrary,
        functionContext    = newFunctionContext,
        baseURI            = null,
        locationData       = c.locationData,
        reporter           = containingDocument.getRequestStats.getReporter
      )
    }

    fromLiteral(c) orElse fromExpression(c) getOrElse c.staticId
  }

  private def caseForValue(value: String) =
    staticControl.caseControls find (caseValue(_) == value)

  private def caseIdFromSelected: Option[String] = {

    // FIXME: The AVT is evaluated in the context of xf:switch, when in fact it should be evaluated in the  context
    // of the xf:case, including namespaces, variables and FunctionContext.
    def isSelected(c: CaseControl) =
      c.selected exists evaluateBooleanAvt

    staticControl.caseControls find isSelected map (_.staticId)
  }

  // NOTE: This assumes there is at least one child case element.
  private def firstCaseId =
    staticControl.caseControls.head.staticId

  // Filter because XXFormsVariableControl can also be a child
  def getChildrenCases =
    children collect { case c: XFormsCaseControl => c }

  def setSelectedCase(caseControlToSelect: XFormsCaseControl): Unit = {

    require(caseControlToSelect.parent eq this, s"xf:case '${caseControlToSelect.effectiveId}' is not child of current xf:switch")

    val previouslySelectedCaseControl = selectedCaseIfRelevantOpt.get

    if (staticControl.caseref.isDefined) {
      // "by performing a setvalue action if the caseref attribute is specified and indicates a node. If the
      // node is readonly or if the toggle action does not indicate a case in the switch, then no value change
      // occurs and therefore no change of the selected case occurs"
      _caserefBinding flatMap (item => StaticDataModel.isWritableItem(item).toOption) foreach { writableNode =>

        val newValue = caseValue(caseControlToSelect.staticControl)

        DataModel.setValueIfChanged(
          nodeInfo  = writableNode,
          newValue  = newValue,
          onSuccess = oldValue => DataModel.logAndNotifyValueChange(
            source             = "toggle",
            nodeInfo           = writableNode,
            oldValue           = oldValue,
            newValue           = newValue,
            isCalculate        = false,
            collector          = Dispatch.dispatchEvent
          )
        )
      }
    } else if (previouslySelectedCaseControl.getId != caseControlToSelect.getId) {

      containingDocument.requireRefresh()

      val localForUpdate = getLocalForUpdate.asInstanceOf[XFormsSwitchControlLocal]
      localForUpdate.selectedCaseControlId = caseControlToSelect.getId

      // "This action adjusts all selected attributes on the affected cases to reflect the new state, and then
      // performs the following:"

      // "1. Dispatching an xforms-deselect event to the currently selected case."
      Dispatch.dispatchEvent(new XFormsDeselectEvent(previouslySelectedCaseControl))

      if (isXForms11Switch) {
        // Partial refresh on the case that is being deselected
        // Do this after xforms-deselect is dispatched
        containingDocument.controls.doPartialRefresh(previouslySelectedCaseControl)

        // Partial refresh on the case that is being selected
        // Do this before xforms-select is dispatched
        containingDocument.controls.doPartialRefresh(caseControlToSelect)
      }

      // "2. Dispatching an xforms-select event to the case to be selected."
      Dispatch.dispatchEvent(new XFormsSelectEvent(caseControlToSelect))
    }
  }

  // Get the effective id of the currently selected case.
  def getSelectedCaseEffectiveId: String =
    if (isRelevant) {
      val local = getCurrentLocal.asInstanceOf[XFormsSwitchControlLocal]
      require(local.selectedCaseControlId ne null, s"Selected case was not set for xf:switch: $effectiveId")
      XFormsId.getRelatedEffectiveId(getEffectiveId, local.selectedCaseControlId)
    } else
      null

  def selectedCaseIfRelevantOpt =
    isRelevant option containingDocument.getControlByEffectiveId(getSelectedCaseEffectiveId).asInstanceOf[XFormsCaseControl]

  override def getBackCopy: AnyRef = {
    var cloned: XFormsSwitchControl = null

    // We want the new one to point to the children of the cloned nodes, not the children

    // Get initial index as we copy "back" to an initial state
    val initialLocal = getInitialLocal.asInstanceOf[XFormsSwitchControlLocal]

    // Clone this and children
    cloned = super.getBackCopy.asInstanceOf[XFormsSwitchControl]

    // Update clone's selected case control to point to one of the cloned children
    val clonedLocal = cloned.getInitialLocal.asInstanceOf[XFormsSwitchControlLocal]

    // NOTE: we don't call getLocalForUpdate() because we know that XFormsSwitchControlLocal is safe to write
    // to (super.getBackCopy() ensures that we have a new copy)
    clonedLocal.selectedCaseControlId = initialLocal.selectedCaseControlId

    cloned
  }

  // Serialize case id
  override def serializeLocal =
    ju.Collections.singletonMap("case-id", XFormsId.getStaticIdFromId(getSelectedCaseEffectiveId))

  override def followDescendantsForFocus: Iterator[XFormsControl] =
    selectedCaseIfRelevantOpt.iterator

  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControlOpt    : Option[XFormsControl]
  ): Boolean =
    previousControlOpt match {
      case Some(previousSwitchControl: XFormsSwitchControl) =>
        getSelectedCaseEffectiveId == getOtherSelectedCaseEffectiveId(Some(previousSwitchControl)) &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControlOpt)
      case _ => false
    }

  final override def outputAjaxDiff(
    previousControlOpt : Option[XFormsControl],
    content            : Option[XMLReceiverHelper => Unit])(implicit
    ch                 : XMLReceiverHelper
  ): Unit = {

    val otherSwitchControl = previousControlOpt.asInstanceOf[Option[XFormsSwitchControl]]

    val hasNestedContent =
      isRelevant                    &&
      ! staticControl.hasFullUpdate &&
      getSelectedCaseEffectiveId != getOtherSelectedCaseEffectiveId(otherSwitchControl)

    val outputNestedContent = (ch: XMLReceiverHelper) => {
      // Output newly selected case id
      val selectedCaseEffectiveId = getSelectedCaseEffectiveId ensuring (_ ne null)

      ch.element("xxf", XXFORMS_NAMESPACE_URI, "case", Array(
        "id", containingDocument.namespaceId(selectedCaseEffectiveId),
        "visibility", "visible")
      )

      otherSwitchControl match {
        case Some(control) if control.isRelevant =>
          // Used to be relevant, simply output deselected case ids
          val previousSelectedCaseId = getOtherSelectedCaseEffectiveId(otherSwitchControl) ensuring (_ ne null)

          ch.element("xxf", XXFORMS_NAMESPACE_URI, "case", Array(
            "id", containingDocument.namespaceId(previousSelectedCaseId),
            "visibility", "hidden")
          )
        case _ =>
          // Control was not relevant, send all deselected to be sure
          // TODO: This should not be needed because the repeat template should have a reasonable default.
          // TODO: 2020-02-27: There are no more repeat templates. Check this.
          getChildrenCases filter (_.getEffectiveId != selectedCaseEffectiveId) foreach { caseControl =>
            ch.element("xxf", XXFORMS_NAMESPACE_URI, "case", Array(
              "id", containingDocument.namespaceId(caseControl.getEffectiveId ensuring (_ ne null)),
              "visibility", "hidden")
            )
          }
      }
    }

    super.outputAjaxDiff(
      previousControlOpt,
      hasNestedContent option outputNestedContent
    )
  }

  def getOtherSelectedCaseEffectiveId(previousSwitchControl: Option[XFormsSwitchControl]): String =
    previousSwitchControl match {
      case Some(control) if control.isRelevant =>
        val selectedCaseId = control.getInitialLocal.asInstanceOf[XFormsSwitchControlLocal].selectedCaseControlId
        assert(selectedCaseId ne null)
        XFormsId.getRelatedEffectiveId(control.getEffectiveId, selectedCaseId)
      case _ =>
        null
    }

  def isXForms11Switch: Boolean = {
    val localXForms11Switch = element.attributeValue(XXFORMS_XFORMS11_SWITCH_QNAME)
    if (localXForms11Switch ne null)
      localXForms11Switch.toBoolean
    else
      containingDocument.isXForms11Switch
  }

  override def valueType = null
}

private class XFormsSwitchControlLocal(var selectedCaseControlId: String = null)
  extends ControlLocalSupport.XFormsControlLocal
