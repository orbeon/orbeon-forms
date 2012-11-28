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
import org.orbeon.oxf.xforms.XFormsModel
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.{XFormsEvent, Dispatch}
import org.orbeon.oxf.xforms.event.events.{XFormsRevalidateEvent, XFormsRecalculateEvent, XFormsRebuildEvent}

class XFormsRebuildAction extends RRRAction {
    def setFlag(model: XFormsModel) = model.getDeferredActionContext.rebuild = true
    def createEvent(model: XFormsModel, applyDefaults: Boolean) = new XFormsRebuildEvent(model)
}

class XFormsRecalculateAction extends RRRAction {
    def setFlag(model: XFormsModel) = model.getDeferredActionContext.recalculate = true
    def createEvent(model: XFormsModel, applyDefaults: Boolean) = new XFormsRecalculateEvent(model, applyDefaults)
}

class XFormsRevalidateAction extends RRRAction {
    def setFlag(model: XFormsModel) = model.getDeferredActionContext.revalidate = true
    def createEvent(model: XFormsModel, applyDefaults: Boolean) = new XFormsRevalidateEvent(model)
}

// Common functionality
trait RRRAction extends XFormsAction {

    override def execute(context: DynamicActionContext): Unit = {

        val interpreter = context.interpreter
        val model       = interpreter.actionXPathContext.getCurrentModel

        val deferred      = (Option(interpreter.resolveAVT(context.element, XXFORMS_DEFERRED_QNAME)) getOrElse  "false").toBoolean
        val applyDefaults = (Option(interpreter.resolveAVT(context.element, XXFORMS_DEFAULTS_QNAME)) getOrElse  "false").toBoolean

        // Set the flag in any case
        setFlag(model)

        // Perform the action immediately if needed
        // NOTE: XForms 1.1 and 2.0 say that no event should be dispatched in this case. It's a bit unclear what the
        // purpose of these events is anyway.
        if (! deferred)
            Dispatch.dispatchEvent(createEvent(model, applyDefaults))
    }

    def setFlag(model: XFormsModel)
    def createEvent(model: XFormsModel, applyDefaults: Boolean): XFormsEvent
}