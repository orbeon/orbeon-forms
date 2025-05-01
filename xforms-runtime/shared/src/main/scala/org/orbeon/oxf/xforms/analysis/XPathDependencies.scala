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
import org.orbeon.oxf.xforms.analysis.model.{MipName, Model, StaticBind}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.saxon.om
import org.orbeon.xforms.analysis.model.ValidationLevel


trait XPathDependencies {

  def markValueChanged     (model: XFormsModel, nodeInfo: om.NodeInfo): Unit
  def markStructuralChange (model: XFormsModel, instanceOpt: Option[XFormsInstance]): Unit

  def rebuildDone          (model: XFormsModel): Unit // called even if no work was done during `doRebuild()`
  def recalculateDone      (model: XFormsModel): Unit
  def revalidateDone       (model: XFormsModel): Unit
  def modelDestruct        (model: XFormsModel): Unit

  def refreshStart         ()                  : Unit
  def refreshDone          ()                  : Unit

  def bindingUpdateStart   ()                  : Unit
  def bindingUpdateDone    ()                  : Unit

  def afterInitialResponse (propertiesSequence: Int): Unit
  def beforeUpdateResponse (propertiesSequence: Int): Unit
  def afterUpdateResponse  ()                  : Unit

  def notifyComputeLHHA    ()                  : Unit
  def notifyOptimizeLHHA   ()                  : Unit

  def notifyComputeItemset ()                  : Unit
  def notifyOptimizeItemset()                  : Unit

  def requireBindingUpdate (control: ElementAnalysis,                   controlIndexes: Array[Int]): Boolean
  def requireValueUpdate   (control: ElementAnalysis,                   controlIndexes: Array[Int]): Boolean
  def requireLHHAUpdate    (control: ElementAnalysis,       lhha: LHHA, controlIndexes: Array[Int]): Boolean
  def requireItemsetUpdate (control: SelectionControlTrait,             controlIndexes: Array[Int]): Boolean

  def requireModelMIPUpdate(model: XFormsModel, bind: StaticBind, mip: MipName, level: ValidationLevel): Boolean

  def hasAnyCalculationBind(model: Model, instancePrefixedId: String): Boolean
  def hasAnyValidationBind (model: Model, instancePrefixedId: String): Boolean
}