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

import org.orbeon.dom.Element
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl
import org.orbeon.oxf.xforms.analysis.{ChildrenBuilderTrait, ChildrenLHHAAndActionsTrait, ElementAnalysis, StaticStateContext}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.saxon.om.Item

class OutputControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
    extends ValueControl(staticStateContext, element, parent, preceding, scope)
    with ValueTrait
    with OptionalSingleNode
    with ChildrenBuilderTrait
    with ChildrenLHHAAndActionsTrait
    with FormatTrait {

  // Unlike other value controls, don't restrict to simple content (even though the spec says it should!)
  override def isAllowedBoundItem(item: Item) = DataModel.isAllowedBoundItem(item)

  override protected val allowedExtensionAttributes = {

    val altSet =
      element.attributeValueOpt("mediatype") exists (_.startsWith("image/")) set XXFORMS_ALT_QNAME

    val targetSet =
      appearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME) set XXFORMS_TARGET_QNAME

    altSet ++ targetSet
  }

  override protected def externalEventsDef = super.externalEventsDef ++ Set(XFORMS_HELP, DOM_ACTIVATE, XFORMS_FOCUS)
  override val externalEvents              = externalEventsDef
}
