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
import org.orbeon.oxf.xforms.analysis.model.{Model, StaticBind}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.xforms.analysis.model.ValidationLevel

// This implementation of dependencies simply says that everything must be updated all the time.
class DumbXPathDependencies extends XPathDependencies {
  def markValueChanged(model: XFormsModel, nodeInfo: NodeInfo) = ()
  def markStructuralChange(model: XFormsModel, instanceOpt: Option[XFormsInstance]) = ()
  def rebuildDone(model: XFormsModel) = ()
  def recalculateDone(model: XFormsModel) = ()
  def revalidateDone(model: XFormsModel) = ()
  def modelDestruct(model: XFormsModel) = ()
  def refreshStart() = ()
  def refreshDone() = ()
  def bindingUpdateStart() = ()
  def bindingUpdateDone() = ()
  def afterInitialResponse() = ()
  def beforeUpdateResponse() = ()
  def afterUpdateResponse() = ()
  def notifyComputeLHHA() = ()
  def notifyOptimizeLHHA() = ()
  def notifyComputeItemset() = ()
  def notifyOptimizeItemset() = ()
  def requireBindingUpdate(control: ElementAnalysis, controlEffectiveId: String) = true
  def requireValueUpdate(control: ElementAnalysis, controlEffectiveId: String) = true
  def requireLHHAUpdate(control: ElementAnalysis, lhha: LHHA, controlEffectiveId: String) = true
  def requireItemsetUpdate(control: SelectionControlTrait, controlPrefixedId: String) = true
  def requireModelMIPUpdate(model: XFormsModel, bind: StaticBind, mip: MIP, level: ValidationLevel) = true
  def hasAnyCalculationBind(model: Model, instancePrefixedId: String) = true
  def hasAnyValidationBind(model: Model, instancePrefixedId: String) = true
}