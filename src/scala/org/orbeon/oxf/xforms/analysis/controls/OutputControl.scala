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
package org.orbeon.oxf.xforms.analysis.controls

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.StaticStateContext
import org.orbeon.oxf.xforms.analysis.{ChildrenLHHAAndActionsTrait, ChildrenBuilderTrait, ElementAnalysis}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.saxon.om.Item

class OutputControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
        extends CoreControl(staticStateContext, element, parent, preceding, scope)
        with ValueTrait
        with ChildrenBuilderTrait
        with ChildrenLHHAAndActionsTrait {

    // Unlike other value controls, don't restrict to simple content (even though the spec says it should!)
    override def isAllowedBoundItem(item: Item) = DataModel.isAllowedBoundItem(item)

    override def externalEvents = super.externalEvents ++ Set(XFORMS_HELP, DOM_ACTIVATE)
}
