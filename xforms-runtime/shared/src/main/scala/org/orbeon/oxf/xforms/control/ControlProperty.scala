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

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.LHHASupport.LHHAProperty
import org.orbeon.oxf.xforms.control.controls.XFormsLHHAControl
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.{XFormsContextStack, XFormsContextStackSupport}
import org.orbeon.xforms.XFormsId


// Base trait for a control property (label, itemset, etc.)
trait ControlProperty[T >: Null] {
  def value(collector: ErrorEventCollector): T
  def handleMarkDirty(force: Boolean = false): Unit

  def valueOpt(collector: ErrorEventCollector): Option[T] = Option(value(collector))
}

// Immutable control property
class ImmutableControlProperty[T >: Null](private val _value: T) extends ControlProperty[T] {
  def value(collector: ErrorEventCollector): T = _value
  def handleMarkDirty(force: Boolean): Unit = ()
}

// Mutable control property supporting optimization
trait MutableControlProperty[T >: Null] extends ControlProperty[T] with Cloneable {

  private var _value: T = null
  private var isEvaluated = false
  private var isOptimized = false

  protected def isRelevant: Boolean
  protected def wasRelevant: Boolean
  protected def requireUpdate: Boolean
  protected def notifyCompute(): Unit
  protected def notifyOptimized(): Unit

  protected def evaluateValue(collector: ErrorEventCollector): T
  protected def nonRelevantValue: T = null

  final def value(collector: ErrorEventCollector): T = {
    if (! isEvaluated) {
      _value =
        if (isRelevant) {
          notifyCompute()
          evaluateValue(collector)
        } else {
          // NOTE: if the control is not relevant, nobody should ask about this in the first place
          // In practice, this can be called as of 2012-06-20
          nonRelevantValue
        }
      isEvaluated = true
    } else if (isOptimized) {
      // This is only for statistics: if the value was not re-evaluated because of the dependency engine
      // giving us the green light, the first time the value is asked we notify the dependency engine of that
      // situation.
      notifyOptimized()
      isOptimized = false
    }

    _value
  }

  def handleMarkDirty(force: Boolean): Unit = {

    val isDirty = ! isEvaluated
    def markOptimized(): Unit = isOptimized = true

    if (! isDirty) {
      // don't do anything if we are already dirty
      if (force || isRelevant != wasRelevant) {
        // Control becomes relevant or non-relevant
        markDirty()
      } else if (isRelevant) {
        // Control remains relevant
        if (requireUpdate)
          markDirty()
        else
          markOptimized() // for statistics only
      }
    }
  }

  protected def markDirty(): Unit = {
    _value = null
    isEvaluated = false
    isOptimized = false
  }
}

class MutableLHHAProperty(control: XFormsControl, val lhhaAnalysis: LHHAAnalysis)
extends MutableControlProperty[String]
   with LHHAProperty {

def isHTML: Boolean =
  lhhaAnalysis.containsHTML

def locationData: LocationData =
  control.getLocationData

protected def isRelevant: Boolean = control.isRelevant
protected def wasRelevant: Boolean = control.wasRelevant

protected def evaluateValue(collector: ErrorEventCollector): String =
  evaluateOne(lhhaAnalysis, collector) match {
    case Some(value: String) =>
      value
    case _ =>
      null
  }

protected def requireUpdate: Boolean =
  control.containingDocument.xpathDependencies.requireLHHAUpdate(
    control.staticControl,
    lhhaAnalysis.lhhaType,
    XFormsId.getEffectiveIdSuffixParts(control.effectiveId)
  )

protected def notifyCompute(): Unit =
  control.containingDocument.xpathDependencies.notifyComputeLHHA()

protected def notifyOptimized(): Unit =
  control.containingDocument.xpathDependencies.notifyOptimizeLHHA()

// Evaluate the value of a LHHA related to this control
private def evaluateOne(lhhaAnalysis: LHHAAnalysis, collector: ErrorEventCollector): Option[String] =
  if (lhhaAnalysis.isLocal) {

    implicit val contextStack: XFormsContextStack = control.getContextStack
    contextStack.setBinding(control.bindingContext)

    XFormsContextStackSupport.evaluateExpressionOrConstant(
      childElem           = lhhaAnalysis,
      parentEffectiveId   = control.effectiveId,
      pushContextAndModel = true,
      eventTarget         = control,
      collector           = collector
    )
  } else {

    // LHHA is somewhere else. We resolve the control and ask for its value.
    Controls.resolveControlsById(
      control.containingDocument,
      control.effectiveId,
      lhhaAnalysis.staticId,
      followIndexes = true
    )
    .headOption
    .collect { case control: XFormsLHHAControl =>
      control.getValue(collector)
    }
  }
}
