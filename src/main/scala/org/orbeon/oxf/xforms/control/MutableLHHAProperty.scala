/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.XFormsControl.MutableControlProperty
import org.orbeon.oxf.xforms.analysis.controls.{StaticLHHASupport, LHHAAnalysis}
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.oxf.xforms.control.LHHASupport.LHHAProperty
import org.apache.commons.lang3.StringUtils

class MutableLHHProperty(control: XFormsControl, lhhaType: XFormsConstants.LHHA, supportsHTML: Boolean)
    extends MutableLHHAProperty(control, lhhaType, supportsHTML) {

    protected def evaluateValueImpl(tempContainsHTML: Array[Boolean]) =
        evaluateOne(control.staticControl.asInstanceOf[StaticLHHASupport].lhh(lhhaType.name).get, tempContainsHTML)
}

class MutableAlertProperty(control: XFormsSingleNodeControl, lhhaType: XFormsConstants.LHHA, supportsHTML: Boolean)
    extends MutableLHHAProperty(control, lhhaType, supportsHTML) {

    protected def evaluateValueImpl(tempContainsHTML: Array[Boolean]) = {

        val activeAlertsOpt = LHHASupport.gatherActiveAlerts(control)

        val values =
            for {
                (_, activeAlerts) ← activeAlertsOpt.toList
                activeAlert       ← activeAlerts
                value             ← Option(evaluateOne(activeAlert, tempContainsHTML))
            } yield
                value

        // TODO: separator??? use HTML?
        if (values.nonEmpty) values filter StringUtils.isNotBlank mkString " - " else null
    }
}

// Mutable LHHA property
abstract class MutableLHHAProperty(control: XFormsControl, lhhaType: XFormsConstants.LHHA, supportsHTML: Boolean)
    extends MutableControlProperty[String]
    with LHHAProperty {

    private var _isHTML = false

    protected def isRelevant = control.isRelevant
    protected def wasRelevant = control.wasRelevant

    protected def evaluateValue() = {
        val tempContainsHTML = new Array[Boolean](1)
        val result = evaluateValueImpl(tempContainsHTML)
        _isHTML = result != null && tempContainsHTML(0)
        result
    }

    def escapedValue() = {
        val rawValue = value()
        if (_isHTML)
            XFormsControl.getEscapedHTMLValue(control.getLocationData, rawValue)
        else
            XMLUtils.escapeXMLMinimal(rawValue)
    }

    def isHTML = {
        value()
        _isHTML
    }

    protected override def markDirty() {
        super.markDirty()
        _isHTML = false
    }

    protected def requireUpdate =
        control.containingDocument.getXPathDependencies.requireLHHAUpdate(lhhaType.name, control.getPrefixedId)

    protected def notifyCompute() =
        control.containingDocument.getXPathDependencies.notifyComputeLHHA()

    protected def notifyOptimized() =
        control.containingDocument.getXPathDependencies.notifyOptimizeLHHA()

    override def copy: MutableLHHAProperty =
        super.copy.asInstanceOf[MutableLHHAProperty]

    protected def evaluateValueImpl(tempContainsHTML: Array[Boolean]): String

    // Evaluate the value of a LHHA related to this control
    // Can return null
    protected def evaluateOne(lhhaAnalysis: LHHAAnalysis, tempContainsHTML: Array[Boolean]) = {
        val contextStack = control.getContextStack

        val lhhaElement = lhhaAnalysis.element

        if (lhhaAnalysis.isLocal) {
            // LHHA is direct child of control, evaluate within context
            contextStack.setBinding(control.bindingContext)
            contextStack.pushBinding(lhhaElement, control.effectiveId, lhhaAnalysis.scope)
            val result = XFormsUtils.getElementValue(control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, lhhaAnalysis.defaultToHTML, tempContainsHTML)
            contextStack.popBinding()
            result
        } else {
            // LHHA is somewhere else, assumed as a child of xf:* or xxf:*

            // TODO: This whole code sounds insanely complicated.
            // LHHA elements should be present in the tree and we should 1) resolve them and 2) obtain their context.

            // Find context object for XPath evaluation
            val contextElement = lhhaElement.getParent
            val contextStaticId = XFormsUtils.getElementId(contextElement)
            val contextEffectiveId =
                if (contextStaticId == null || contextStaticId == "#document") {
                    // Assume we are at the top-level
                    contextStack.resetBindingContext()
                    control.container.getFirstControlEffectiveId
                } else {
                    // Not at top-level, find containing object
                    val ancestorContextControl = findAncestorContextControl(contextStaticId, XFormsUtils.getElementId(lhhaElement))
                    if (ancestorContextControl != null) {
                        contextStack.setBinding(ancestorContextControl.bindingContext)
                        ancestorContextControl.effectiveId
                    } else
                        null
                }

            if (contextEffectiveId != null) {
                // Push binding relative to context established above and evaluate
                contextStack.pushBinding(lhhaElement, contextEffectiveId, lhhaAnalysis.scope)
                val result = XFormsUtils.getElementValue(control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, lhhaAnalysis.defaultToHTML, tempContainsHTML)
                contextStack.popBinding()
                result
            } else
                // Do as if there was no LHHA
                null
        }
    }

    private def findAncestorContextControl(contextStaticId: String, lhhaStaticId: String): XFormsControl = {

        // NOTE: LHHA element must be in the same resolution scope as the current control (since @for refers to @id)
        val lhhaScope = control.getResolutionScope
        val lhhaPrefixedId = lhhaScope.prefixedIdForStaticId(lhhaStaticId)

        // Assume that LHHA element is within same repeat iteration as its related control
        val contextPrefixedId = XFormsUtils.getRelatedEffectiveId(lhhaPrefixedId, contextStaticId)
        val contextEffectiveId = contextPrefixedId + XFormsUtils.getEffectiveIdSuffixWithSeparator(control.effectiveId)

        var ancestorObject = control.container.getContainingDocument.getObjectByEffectiveId(contextEffectiveId)
        while (ancestorObject.isInstanceOf[XFormsControl]) {
            val ancestorControl = ancestorObject.asInstanceOf[XFormsControl]
            if (ancestorControl.getResolutionScope == lhhaScope) {
                // Found ancestor in right scope
                return ancestorControl
            }
            ancestorObject = ancestorControl.parent
        }

        null
    }
}
