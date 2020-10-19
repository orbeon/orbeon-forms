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

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.XFormsElementValue
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, PartAnalysisImpl}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.model.StaticDataModel
import org.orbeon.saxon.om.Item
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.xbl.Scope

class OutputControl(
  part      : PartAnalysisImpl,
  index     : Int,
  element   : Element,
  parent    : Option[ElementAnalysis],
  preceding : Option[ElementAnalysis],
  scope     : Scope
) extends ValueControl(part, index, element, parent, preceding, scope)
     with OptionalSingleNode {

  // Unlike other value controls, don't restrict to simple content (even though the spec says it should!)

  val isImageMediatype    : Boolean = element.attributeValueOpt("mediatype") exists (_.startsWith("image/"))
  val isHtmlMediatype     : Boolean = element.attributeValueOpt("mediatype") contains "text/html"
  val isDownloadAppearance: Boolean = appearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME)
  override def isAllowedBoundItem(item: Item): Boolean = StaticDataModel.isAllowedBoundItem(item)

  override protected val allowedExtensionAttributes: Set[QName] =
    (isImageMediatype set XXFORMS_ALT_QNAME) ++ (isDownloadAppearance set XXFORMS_TARGET_QNAME)

  override protected def externalEventsDef: Set[String] = super.externalEventsDef ++ Set(XFORMS_HELP, DOM_ACTIVATE, XFORMS_FOCUS)
  override val externalEvents: Set[String] = externalEventsDef

  val staticValue: Option[String] =
    (! isImageMediatype && ! isDownloadAppearance && LHHAAnalysis.hasStaticValue(element)) option
      XFormsElementValue.getStaticChildElementValue(containerScope.fullPrefix, element, acceptHTML = true, null)
}
