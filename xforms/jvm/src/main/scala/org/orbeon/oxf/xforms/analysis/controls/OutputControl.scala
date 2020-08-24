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
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ValueControl
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, StaticStateContext, XPathAnalysis}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.xforms.xbl.Scope
import org.orbeon.saxon.om.Item

class OutputControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
    extends ValueControl(staticStateContext, element, parent, preceding, scope)
    with OptionalSingleNode {

  // Unlike other value controls, don't restrict to simple content (even though the spec says it should!)
  override def isAllowedBoundItem(item: Item): Boolean = DataModel.isAllowedBoundItem(item)

  val isImageMediatype    : Boolean = element.attributeValueOpt("mediatype") exists (_.startsWith("image/"))
  val isHtmlMediatype     : Boolean = element.attributeValueOpt("mediatype") contains "text/html"
  val isDownloadAppearance: Boolean = appearances.contains(XXFORMS_DOWNLOAD_APPEARANCE_QNAME)

  override protected val allowedExtensionAttributes: Set[QName] =
    (isImageMediatype set XXFORMS_ALT_QNAME) ++ (isDownloadAppearance set XXFORMS_TARGET_QNAME)

  override protected def externalEventsDef: Set[String] = super.externalEventsDef ++ Set(XFORMS_HELP, DOM_ACTIVATE, XFORMS_FOCUS)
  override val externalEvents: Set[String] = externalEventsDef

  val staticValue: Option[String] =
    (! isImageMediatype && ! isDownloadAppearance && LHHAAnalysis.hasStaticValue(staticStateContext, element)) option
      XFormsUtils.getStaticChildElementValue(containerScope.fullPrefix, element, true, null)

  // Q: Do we need to handle the context anyway?
  override protected def computeContextAnalysis: Option[XPathAnalysis] = staticValue.isEmpty flatOption super.computeContextAnalysis
  override protected def computeBindingAnalysis: Option[XPathAnalysis] = staticValue.isEmpty flatOption super.computeBindingAnalysis
  override protected def computeValueAnalysis  : Option[XPathAnalysis] = staticValue.isEmpty flatOption super.computeValueAnalysis
}
