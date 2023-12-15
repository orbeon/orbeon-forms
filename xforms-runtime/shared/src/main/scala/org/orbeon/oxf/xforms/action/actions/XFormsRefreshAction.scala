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

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.XFormsRefreshEvent
import org.orbeon.oxf.xforms.model.XFormsModel

/**
 * 10.1.6 The refresh Element
 */
class XFormsRefreshAction extends XFormsAction {
  override def execute(context: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val modelOpt = context.interpreter.actionXPathContext.getCurrentBindingContext.modelOpt

    modelOpt match {
      case Some(model) =>
        XFormsRefreshAction.refresh(model, context.collector)
      case None =>
        val modelId = context.analysis.model.orNull
        throw new ValidationException(s"Invalid model id: `$modelId`", context.element.getData.asInstanceOf[LocationData])
    }
  }
}

object XFormsRefreshAction {
  def refresh(model: XFormsModel, collector: ErrorEventCollector): Unit = {
    // NOTE: We no longer need to force the refresh flag here because the refresh flag is global. If a change in any
    // model occurred, then the flag will be already set and we are safe. Otherwise, it is safe not to do anything.
    Dispatch.dispatchEvent(new XFormsRefreshEvent(model), collector)
  }
}