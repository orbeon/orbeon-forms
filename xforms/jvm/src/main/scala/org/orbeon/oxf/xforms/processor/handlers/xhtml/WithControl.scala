/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis}
import org.orbeon.oxf.xforms.control.{ControlAjaxSupport, XFormsControl}
import org.orbeon.oxf.xml.XMLReceiverHelper
import org.xml.sax.helpers.AttributesImpl


trait WithControl extends XFormsBaseHandlerXHTML {

  final def staticControlOpt: Option[ElementAnalysis] =
    getXFormsHandlerContext.getPartAnalysis.findControlAnalysis(getPrefixedId)

  final lazy val currentControl: XFormsControl =
    getContainingDocument.getControlByEffectiveId(getEffectiveId) ensuring (_ ne null)

  final protected def handleAriaByAttForSelect1Full(atts: AttributesImpl): Unit =
    for {
      staticControl <- staticControlOpt
      attValue      <- ControlAjaxSupport.findAriaBy(staticControl, currentControl, LHHA.Label, condition = _ => true)(getContainingDocument)
      attName       = ControlAjaxSupport.AriaLabelledby
    } locally {
      atts.addAttribute("", attName, attName, XMLReceiverHelper.CDATA, attValue)
    }

  final protected def handleAriaByAtts(atts: AttributesImpl): Unit =
    for {
      staticControl   <- staticControlOpt
      (lhha, attName) <- ControlAjaxSupport.LhhaWithAriaAttName
      attValue        <- ControlAjaxSupport.findAriaBy(staticControl, currentControl, lhha, condition = _.isForRepeat)(getContainingDocument)
    } locally {
      atts.addAttribute("", attName, attName, XMLReceiverHelper.CDATA, attValue)
    }

  final protected def getStaticLHHA(controlPrefixedId: String, lhha: LHHA): LHHAAnalysis = {
    val globalOps = getXFormsHandlerContext.getPartAnalysis
    if (lhha == LHHA.Alert)
      globalOps.getAlerts(controlPrefixedId).head
    else // for alerts, take the first one, but does this make sense?
      globalOps.getLHH(controlPrefixedId, lhha)
  }
}
