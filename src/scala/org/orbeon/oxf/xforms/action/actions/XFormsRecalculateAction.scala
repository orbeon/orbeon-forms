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

import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsRecalculateEvent

/**
 * 10.1.4 The recalculate Element
 */
class XFormsRecalculateAction extends XFormsAction {

    override def execute(context: DynamicActionContext): Unit = {

        val interpreter = context.interpreter
        val model       = interpreter.actionXPathContext.getCurrentModel

        val applyDefaults = (Option(interpreter.resolveAVT(context.element, XXFORMS_DEFAULTS_QNAME)) getOrElse  "false").toBoolean
        val deferred      = (Option(interpreter.resolveAVT(context.element, XXFORMS_DEFERRED_QNAME)) getOrElse  "false").toBoolean

        model.getDeferredActionContext.recalculate = true
        if (! deferred)
            Dispatch.dispatchEvent(new XFormsRecalculateEvent(model, applyDefaults))
    }
}