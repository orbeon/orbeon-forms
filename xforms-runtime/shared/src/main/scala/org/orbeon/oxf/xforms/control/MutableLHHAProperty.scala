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

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xforms.control.LHHASupport.LHHAProperty
import org.orbeon.oxf.xforms.control.XFormsControl.MutableControlProperty
import org.orbeon.oxf.xforms.control.controls.XFormsLHHAControl
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.{XFormsContextStack, XFormsContextStackSupport}
import org.orbeon.xforms.XFormsId


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
