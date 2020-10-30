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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.QName
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsFocusEvent
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames._

/**
 * 10.1.7 The setfocus Element
 */
class XFormsSetfocusAction extends XFormsAction {

  override def execute(context: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    // "This XForms Action begins by invoking the deferred update behavior."
    synchronizeAndRefreshIfNeeded(context)

    // Extension: whether to focus on input controls only
    def fromInputOnlyAttribute =
      resolveBooleanAVT("input-only", default = false)(context) option Set(XFORMS_INPUT_QNAME)

    def extractQNames(s: String) =
      s.splitTo[Set]() flatMap (context.element.resolveStringQName(_, unprefixedIsNoNamespace = true))

    val includesQNamesOpt = resolveStringAVT("includes")(context) map extractQNames orElse fromInputOnlyAttribute getOrElse Set.empty
    val excludesQNamesOpt = resolveStringAVT("excludes")(context) map extractQNames getOrElse Set.empty

    // Resolve and update control
    resolveControlAvt("control")(context) foreach
      (XFormsSetfocusAction.setfocus(_, includesQNamesOpt, excludesQNamesOpt))
  }
}

object XFormsSetfocusAction {
  def setfocus(control: XFormsControl, includes: Set[QName], excludes: Set[QName]): Unit =
    Dispatch.dispatchEvent(new XFormsFocusEvent(control, includes, excludes))
}