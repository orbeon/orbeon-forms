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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.xforms.analysis.controls.{LHHA, SelectionControlTrait}
import org.orbeon.oxf.xforms.analysis.model.ModelDefs.MIP
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.orbeon.oxf.xforms.analysis.model.{Model, ModelDefs, StaticBind}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.saxon.om.NodeInfo

// Interface to dependencies implementation.
trait XPathDependencies {

  def markValueChanged(model: XFormsModel, nodeInfo: NodeInfo)
  def markStructuralChange(model: XFormsModel, instanceOpt: Option[XFormsInstance])

  def rebuildDone(model: XFormsModel) // called even if no work was done during `doRebuild()`
  def recalculateDone(model: XFormsModel)
  def revalidateDone(model: XFormsModel)
  def modelDestruct(model: XFormsModel)

  def refreshStart()
  def refreshDone()

  def bindingUpdateStart()
  def bindingUpdateDone()

  def afterInitialResponse()
  def beforeUpdateResponse()
  def afterUpdateResponse()

  def notifyComputeLHHA()
  def notifyOptimizeLHHA()

  def notifyComputeItemset()
  def notifyOptimizeItemset()

  def requireBindingUpdate(control: ElementAnalysis, controlEffectiveId: String): Boolean
  def requireValueUpdate(control: ElementAnalysis, controlEffectiveId: String): Boolean
  def requireLHHAUpdate(control: ElementAnalysis, lhha: LHHA, controlEffectiveId: String): Boolean
  def requireItemsetUpdate(control: SelectionControlTrait, controlEffectiveId: String): Boolean

  def requireModelMIPUpdate(model: XFormsModel, bind: StaticBind, mip: MIP, level: ValidationLevel): Boolean

  def hasAnyCalculationBind(model: Model, instancePrefixedId: String): Boolean
  def hasAnyValidationBind(model: Model, instancePrefixedId: String): Boolean
}