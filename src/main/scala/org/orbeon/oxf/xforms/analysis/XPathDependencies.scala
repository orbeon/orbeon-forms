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

import org.orbeon.oxf.xforms.XFormsInstance
import org.orbeon.oxf.xforms.XFormsModel
import org.orbeon.oxf.xforms.analysis.model.{StaticBind, Model}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._

/**
 * Interface to dependencies implementation.
 */
trait XPathDependencies {
  def markValueChanged(model: XFormsModel, nodeInfo: NodeInfo)
  def markStructuralChange(model: XFormsModel, instanceOpt: Option[XFormsInstance])

  def rebuildDone(model: Model)
  def recalculateDone(model: Model)
  def revalidateDone(model: Model)

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

  def requireBindingUpdate(controlPrefixedId: String): Boolean
  def requireValueUpdate(controlPrefixedId: String): Boolean
  def requireLHHAUpdate(lhhaName: String, controlPrefixedId: String): Boolean
  def requireItemsetUpdate(controlPrefixedId: String): Boolean

  def hasAnyCalculationBind(model: Model, instancePrefixedId: String): Boolean
  def hasAnyValidationBind(model: Model, instancePrefixedId: String): Boolean

  def requireModelMIPUpdate(model: Model, bind: StaticBind, mipName: String, level: ValidationLevel): Boolean
}