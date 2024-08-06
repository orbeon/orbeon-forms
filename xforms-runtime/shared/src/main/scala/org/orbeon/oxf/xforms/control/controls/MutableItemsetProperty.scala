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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.xforms.control.XFormsControl.MutableControlProperty
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.itemset.{Itemset, ItemsetSupport}
import org.orbeon.xforms.XFormsId

class MutableItemsetProperty(private val control: XFormsSelect1Control) extends MutableControlProperty[Itemset] {
  protected def isRelevant                                   : Boolean = control.isRelevant
  protected def wasRelevant                                  : Boolean = control.wasRelevant
  protected def requireUpdate                                : Boolean = control.containingDocument.xpathDependencies.requireItemsetUpdate(control.staticControl, XFormsId.getEffectiveIdSuffixParts(control.effectiveId))
  protected def notifyCompute()                              : Unit = control.containingDocument.xpathDependencies.notifyComputeItemset()
  protected def notifyOptimized()                            : Unit = control.containingDocument.xpathDependencies.notifyOptimizeItemset()
  protected def evaluateValue(collector: ErrorEventCollector): Itemset = ItemsetSupport.evaluateItemset(control, collector)
}
