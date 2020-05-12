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

import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.LHHASupport.LHHAProperty
import org.orbeon.oxf.xforms.control.XFormsControl.MutableControlProperty
import org.orbeon.oxf.xforms.control.controls.XFormsLHHAControl
import org.orbeon.xforms.XFormsId

class MutableLHHProperty(control: XFormsControl, lhhaType: LHHA, supportsHTML: Boolean)
  extends MutableLHHAProperty(control, lhhaType, supportsHTML) {

  protected def evaluateValueImpl =
    for {
      lhh   <- control.staticControl.asInstanceOf[StaticLHHASupport].lhh(lhhaType)
      value <- evaluateOne(lhh)
    } yield
      value -> lhh.containsHTML
}

class MutableAlertProperty(control: XFormsSingleNodeControl, lhhaType: LHHA, supportsHTML: Boolean)
  extends MutableLHHAProperty(control, lhhaType, supportsHTML) {

  protected def evaluateValueImpl = {

    val activeAlertsOpt = LHHASupport.gatherActiveAlerts(control)

    val valuesWithIsHtml =
      for {
        (_, activeAlerts) <- activeAlertsOpt.toList
        activeAlert       <- activeAlerts
        valueWithIsHTML   <- evaluateOne(activeAlert)
      } yield
        valueWithIsHTML -> activeAlert.containsHTML

    if (valuesWithIsHtml.size < 2)
      valuesWithIsHtml.headOption
    else {
      // Combine multiple values as a single HTML value using ul/li
      val combined = (
        valuesWithIsHtml
        map { case (value, isHTML) => if (! isHTML) value.escapeXmlMinimal else value }
        mkString ("<ul><li>", "</li><li>", "</li></ul>")
      )

      Some(combined -> true)
    }
  }
}

// Mutable LHHA property
abstract class MutableLHHAProperty(control: XFormsControl, lhhaType: LHHA, supportsHTML: Boolean)
  extends MutableControlProperty[String]
  with LHHAProperty {

  private var _isHTML = false

  protected def isRelevant = control.isRelevant
  protected def wasRelevant = control.wasRelevant

  // TODO: `isHTML` now uses the static `containsHTML` except for multiple alerts. Do this more statically?
  protected def evaluateValue() =
    evaluateValueImpl match {
      case Some((value: String, isHTML)) =>
        _isHTML = isHTML
        value
      case _ =>
        _isHTML = false
        null
    }

  def escapedValue() = {
    val rawValue = value()
    if (_isHTML)
      XFormsControl.getEscapedHTMLValue(control.getLocationData, rawValue)
    else
      rawValue.escapeXmlMinimal
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
    control.containingDocument.getXPathDependencies.requireLHHAUpdate(control.staticControl, lhhaType, control.effectiveId)

  protected def notifyCompute() =
    control.containingDocument.getXPathDependencies.notifyComputeLHHA()

  protected def notifyOptimized() =
    control.containingDocument.getXPathDependencies.notifyOptimizeLHHA()

  override def copy: MutableLHHAProperty =
    super.copy.asInstanceOf[MutableLHHAProperty]

  protected def evaluateValueImpl: Option[(String, Boolean)]

  // Evaluate the value of a LHHA related to this control
  // Can return null
  protected def evaluateOne(lhhaAnalysis: LHHAAnalysis): Option[String] = {
    val contextStack = control.getContextStack

    val lhhaElement = lhhaAnalysis.element

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
            Array[Boolean](false)
          )
        )
        contextStack.popBinding()
        result
      } else {
        // LHHA is somewhere else. We resolve the control and ask for its value.
        Controls.resolveControlsById(control.containingDocument, control.effectiveId, lhhaAnalysis.staticId, followIndexes = true).headOption collect {
          case control: XFormsLHHAControl => control.getValue
        }
      }

    result
  }

  private def findAncestorContextControl(contextStaticId: String, lhhaStaticId: String): XFormsControl = {

    // NOTE: LHHA element must be in the same resolution scope as the current control (since @for refers to @id)
    val lhhaScope = control.getResolutionScope
    val lhhaPrefixedId = lhhaScope.prefixedIdForStaticId(lhhaStaticId)

    // Assume that LHHA element is within same repeat iteration as its related control
    val contextPrefixedId = XFormsId.getRelatedEffectiveId(lhhaPrefixedId, contextStaticId)
    val contextEffectiveId = contextPrefixedId + XFormsId.getEffectiveIdSuffixWithSeparator(control.effectiveId)

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
