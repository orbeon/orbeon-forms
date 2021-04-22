/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.saxon.om.Item
import org.orbeon.xforms.xbl.Scope

class VariableControl(
  part      : PartAnalysisImpl,
  index     : Int,
  element   : Element,
  parent    : Option[ElementAnalysis],
  preceding : Option[ElementAnalysis],
  scope     : Scope
) extends ElementAnalysis(part, index, element, parent, preceding, scope)
  with ViewTrait
  with OptionalSingleNode
  with WithChildrenTrait
  with VariableAnalysisTrait {

  override def isAllowedBoundItem(item: Item): Boolean = DataModel.isAllowedValueBoundItem(item)
}
