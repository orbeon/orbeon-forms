/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.analysis.controls.{SelectionControlUtil, LHHA}
import org.orbeon.oxf.xforms.action.XFormsActions
import org.orbeon.xforms.XFormsConstants.FOR_QNAME

trait ChildrenLHHAItemsetsAndActionsTrait extends ChildrenBuilderTrait {

  // For leaf controls, keep nested LHHA and actions
  override def findRelevantChildrenElements =
    findAllChildrenElements collect
      { case (e, s) if LHHA.isLHHA(e) && (e.attribute(FOR_QNAME) eq null) || SelectionControlUtil.isTopLevelItemsetElement(e) || XFormsActions.isAction(e.getQName) => (e, s) }
}
