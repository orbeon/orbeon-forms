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

import java.{util ⇒ ju}
import org.dom4j.Element
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.{ControlLocalSupport, XFormsControl, XFormsSingleNodeContainerControl}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.{Dom4j, XMLReceiverHelper}
import org.xml.sax.helpers.AttributesImpl

/**
 * Represents an xf:switch container control.
 *
 * NOTE: This keep the "currently selected flag" for all children xf:case.
 */
class XFormsSwitchControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
        extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId) {

    // Initial local state
    setLocal(new XFormsSwitchControlLocal)

    // NOTE: state deserialized -> state previously serialized -> control was relevant -> onCreate() called
    override def onCreate(state: Option[ControlState]): Unit = {
        super.onCreate(state)

        // Ensure that the initial state is set, either from default value, or for state deserialization.
        state match {
            case Some(state) ⇒
                // NOTE: Don't use getLocalForUpdate() as we don't want to cause initialLocal != currentLocal
                val local = getCurrentLocal.asInstanceOf[XFormsSwitchControlLocal]
                local.selectedCaseControlId = state.keyValues("case-id")
            case None ⇒
                val local = getLocalForUpdate.asInstanceOf[XFormsSwitchControlLocal]
                local.selectedCaseControlId = findDefaultSelectedCaseId
        }
    }

    private def findDefaultSelectedCaseId: String = {

        // At this point, the children cases are not created yet

        // TODO: Use ElementAnalysis instead when possible
        val caseElements = Dom4j.elements(element, XFORMS_CASE_QNAME)

        def isDefaultSelected(element: Element) =
            Option(element.attributeValue("selected")) exists evaluateBooleanAvt

        caseElements find
            isDefaultSelected map
            XFormsUtils.getElementId getOrElse
            XFormsUtils.getElementId(caseElements(0))
    }

    // Filter because XXFormsVariableControl can also be a child
    def getChildrenCases =
        children collect { case c: XFormsCaseControl ⇒ c }

    // Set the currently selected case.
    def setSelectedCase(caseControlToSelect: XFormsCaseControl): Unit = {

        require(caseControlToSelect.parent eq this, s"xf:case '${caseControlToSelect.effectiveId}' is not child of current xf:switch")

        val localForUpdate = getLocalForUpdate.asInstanceOf[XFormsSwitchControlLocal]
        val previouslySelectedCaseControl = selectedCase.get
        val isChanging = previouslySelectedCaseControl.getId != caseControlToSelect.getId

        localForUpdate.selectedCaseControlId = caseControlToSelect.getId
        if (isChanging) {
            // "This action adjusts all selected attributes on the affected cases to reflect the new state, and then
            // performs the following:"

            // "1. Dispatching an xforms-deselect event to the currently selected case."
            Dispatch.dispatchEvent(new XFormsDeselectEvent(previouslySelectedCaseControl))

            if (isXForms11Switch) {
                // Partial refresh on the case that is being deselected
                // Do this after xforms-deselect is dispatched
                containingDocument.getControls.doPartialRefresh(previouslySelectedCaseControl)

                // Partial refresh on the case that is being selected
                // Do this before xforms-select is dispatched
                containingDocument.getControls.doPartialRefresh(caseControlToSelect)
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
            XFormsUtils.getRelatedEffectiveId(getEffectiveId, local.selectedCaseControlId)
        } else
            null

    def selectedCase =
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
        ju.Collections.singletonMap("case-id", XFormsUtils.getStaticIdFromId(getSelectedCaseEffectiveId))

    override def focusableControls =
        if (isRelevant)
            selectedCase.iterator flatMap (_.focusableControls)
        else
            Iterator.empty

    override def equalsExternal(other: XFormsControl): Boolean = {
        if (! other.isInstanceOf[XFormsSwitchControl])
            return false

        // NOTE: don't give up on "this == other" because there can be a difference just in XFormsControlLocal

        val otherSwitchControl = other.asInstanceOf[XFormsSwitchControl]

        // Check whether selected case has changed
        if (getSelectedCaseEffectiveId != getOtherSelectedCaseEffectiveId(otherSwitchControl))
            return false

        super.equalsExternal(other)
    }

    override def outputAjaxDiff(ch: XMLReceiverHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean): Unit = {

        // Output regular diff
        super.outputAjaxDiff(ch, other, attributesImpl, isNewlyVisibleSubtree)

        val otherSwitchControl = other.asInstanceOf[XFormsSwitchControl]
        if (isRelevant && getSelectedCaseEffectiveId != getOtherSelectedCaseEffectiveId(otherSwitchControl)) {

            // Output newly selected case id
            val selectedCaseEffectiveId = getSelectedCaseEffectiveId ensuring (_ ne null)

            ch.element("xxf", XXFORMS_NAMESPACE_URI, "div", Array(
                "id", XFormsUtils.namespaceId(containingDocument, selectedCaseEffectiveId),
                "visibility", "visible")
            )

            if ((otherSwitchControl ne null) && otherSwitchControl.isRelevant) {
                // Used to be relevant, simply output deselected case ids
                val previousSelectedCaseId = getOtherSelectedCaseEffectiveId(otherSwitchControl) ensuring (_ ne null)

                ch.element("xxf", XXFORMS_NAMESPACE_URI, "div", Array(
                    "id", XFormsUtils.namespaceId(containingDocument, previousSelectedCaseId),
                    "visibility", "hidden")
                )
            } else {
                // Control was not relevant, send all deselected to be sure
                // TODO: This should not be needed because the repeat template should have a reasonable default.
                getChildrenCases filter (_.getEffectiveId != selectedCaseEffectiveId) foreach { caseControl ⇒
                    ch.element("xxf", XXFORMS_NAMESPACE_URI, "div", Array(
                        "id", XFormsUtils.namespaceId(containingDocument, caseControl.getEffectiveId),
                        "visibility", "hidden")
                    )
                }
            }
        }
    }

    private def getOtherSelectedCaseEffectiveId(switchControl1: XFormsSwitchControl): String =
        if ((switchControl1 ne null) && switchControl1.isRelevant) {
            val selectedCaseId = switchControl1.getInitialLocal.asInstanceOf[XFormsSwitchControlLocal].selectedCaseControlId
            XFormsUtils.getRelatedEffectiveId(switchControl1.getEffectiveId, selectedCaseId)
        } else
            null

    def isXForms11Switch: Boolean = {
        val localXForms11Switch = element.attributeValue(XXFORMS_XFORMS11_SWITCH_QNAME)
        if (localXForms11Switch ne null)
            localXForms11Switch.toBoolean
        else
            containingDocument.isXForms11Switch
    }

    override def valueType = null
}

private class XFormsSwitchControlLocal extends ControlLocalSupport.XFormsControlLocal {
    var selectedCaseControlId: String = null
}
