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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, StaticLHHASupport}
import org.orbeon.oxf.xforms.control.{SingleNodeFocusableTrait, XFormsControl, XFormsSingleNodeControl}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.xbl.XBLContainer


/**
 * Represents an xf:trigger control.
 *
 * TODO: Use inheritance/interface to make this a single-node control that doesn't hold a value.
 */
class XFormsTriggerControl(container: XBLContainer, parent: XFormsControl, _effectiveId: String)
    extends XFormsSingleNodeControl(container, parent, _effectiveId)
    with SingleNodeFocusableTrait {

  self =>

  import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl.*

  override def htmlLhhaSupport = TriggerLhhaHtmlSupport

  // NOTE: We used to make the trigger non-relevant if it was static-readonly. But this caused issues:
  //
  // - at the time computeRelevant() is called, MIPs haven't been read yet
  // - even if we specially read the readonly value from the binding here, then:
  //   - the static-readonly control becomes non-relevant
  //   - therefore its readonly value becomes false (the default)
  //   - therefore isStaticReadonly() returns false!
  //
  // So we keep the control relevant in this case.

  // Don't output anything for triggers in static readonly mode
  override def supportAjaxUpdates = ! isStaticReadonly

  override def compareLHHA(other: XFormsControl, collector: ErrorEventCollector): Boolean =
    ajaxLhhaSupport.forall {
      case hint @ LHHA.Hint => directOrByLocalLhhaValue(hint, collector) == other.directOrByLocalLhhaValue(hint, collector)
      case lha              => lhhaProperty(lha, local = true).value(collector) == other.lhhaProperty(lha, local = true).value(collector)
    }

  override def evaluateLhhaForAjax(
    staticLhhaSupport : StaticLHHASupport,
    lhha              : LHHA,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Option[String] =
    lhha match {
      case hint @ LHHA.Hint =>
        if (staticLhhaSupport.hasLocal(hint) || staticLhhaSupport.hasByLhha(hint)) {
          val value1 = previousControlOpt.map(_.directOrByLocalLhhaValue(hint, collector))
          val value2 = self.directOrByLocalLhhaValue(hint, collector)
          (! value1.contains(value2)).option(value2.getOrElse(""))
        } else
          None
      case _ =>
        super.evaluateLhhaForAjax(staticLhhaSupport, lhha, previousControlOpt, collector)
    }
}

private object XFormsTriggerControl {
  val TriggerLhhaHtmlSupport = LHHA.DefaultLHHAHTMLSupport - LHHA.Hint
}