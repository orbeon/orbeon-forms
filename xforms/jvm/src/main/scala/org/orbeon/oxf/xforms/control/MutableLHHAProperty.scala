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

class MutableLHHProperty(control: XFormsControl, lhhaType: XFormsConstants.LHHA, supportsHTML: Boolean)
  extends MutableLHHAProperty(control, lhhaType, supportsHTML) {

  protected def evaluateValueImpl =
    evaluateOne(control.staticControl.asInstanceOf[StaticLHHASupport].lhh(lhhaType.name).get)
}

class MutableAlertProperty(control: XFormsSingleNodeControl, lhhaType: XFormsConstants.LHHA, supportsHTML: Boolean)
  extends MutableLHHAProperty(control, lhhaType, supportsHTML) {

  protected def evaluateValueImpl = {

    val activeAlertsOpt = LHHASupport.gatherActiveAlerts(control)

    val valuesWithIsHTML =
      for {
        (_, activeAlerts) ← activeAlertsOpt.toList
        activeAlert       ← activeAlerts
        valueWithIsHTML   ← evaluateOne(activeAlert)
      } yield
        valueWithIsHTML

    if (valuesWithIsHTML.size < 2)
      valuesWithIsHTML.headOption
    else {
      // Combine multiple values as a single HTML value using ul/li
      val combined = (
        valuesWithIsHTML
        map { case (value, isHTML) ⇒ if (! isHTML) XMLUtils.escapeXMLMinimal(value) else value }
        mkString ("<ul><li>", "</li><li>", "</li></ul>")
      )

      Some(combined, true)
    }
  }
}

// Mutable LHHA property
abstract class MutableLHHAProperty(control: XFormsControl, lhhaType: XFormsConstants.LHHA, supportsHTML: Boolean)
  extends MutableControlProperty[String]
  with LHHAProperty {

  private var _isHTML = false

  protected def isRelevant = control.isRelevant
  protected def wasRelevant = control.wasRelevant

  protected def evaluateValue() =
    evaluateValueImpl match {
      case Some((value: String, isHTML)) ⇒
        _isHTML = isHTML
        value
      case _ ⇒
        _isHTML = false
        null
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

  protected override def markDirty(): Unit = {
    super.markDirty()
    _isHTML = false
  }

  protected def requireUpdate =
    control.containingDocument.getXPathDependencies.requireLHHAUpdate(control.staticControl, lhhaType.name, control.effectiveId)

  protected def notifyCompute() =
    control.containingDocument.getXPathDependencies.notifyComputeLHHA()

  protected def notifyOptimized() =
    control.containingDocument.getXPathDependencies.notifyOptimizeLHHA()

  override def copy: MutableLHHAProperty =
    super.copy.asInstanceOf[MutableLHHAProperty]

  protected def evaluateValueImpl: Option[(String, Boolean)]

  // Evaluate the value of a LHHA related to this control
  // Can return null
  protected def evaluateOne(lhhaAnalysis: LHHAAnalysis) = {
    val contextStack = control.getContextStack

    val lhhaElement = lhhaAnalysis.element

    val tempContainsHTML = Array[Boolean](false)

    val result =
      if (lhhaAnalysis.isLocal) {
        // LHHA is direct child of control, evaluate within context
        contextStack.setBinding(control.bindingContext)
        contextStack.pushBinding(lhhaElement, control.effectiveId, lhhaAnalysis.scope)
        val result = Option(
          XFormsUtils.getElementValue(
            control.lhhaContainer,
            contextStack,
            control.effectiveId,
            lhhaElement,
            supportsHTML,
            lhhaAnalysis.defaultToHTML,
            tempContainsHTML)
        )
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
          if ((contextStaticId eq null) || contextStaticId == "#document") {
            // Assume we are at the top-level
            contextStack.resetBindingContext()
            control.container.getFirstControlEffectiveId
          } else {
            // Not at top-level, find containing object
            val ancestorContextControl =
              findAncestorContextControl(contextStaticId, XFormsUtils.getElementId(lhhaElement))
            if (ancestorContextControl ne null) {
              contextStack.setBinding(ancestorContextControl.bindingContext)
              ancestorContextControl.effectiveId
            } else
              null
          }

        if (contextEffectiveId ne null) {
          // Push binding relative to context established above and evaluate
          contextStack.pushBinding(lhhaElement, contextEffectiveId, lhhaAnalysis.scope)
          val result = Option(
            XFormsUtils.getElementValue(
              control.container,
              contextStack,
              control.effectiveId,
              lhhaElement,
              supportsHTML,
              lhhaAnalysis.defaultToHTML,
              tempContainsHTML
            )
          )
          contextStack.popBinding()
          result
        } else
          // Do as if there was no LHHA
          None
      }

    result map (_ → tempContainsHTML(0))
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
