/**
 * Copyright (C) 2015 Orbeon, Inc.
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

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.XXFormsDialogCloseEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}

/**
 * Extension xxf:hide action.
 */
class XXFormsHideAction extends XFormsAction {
  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val interpreter = actionContext.interpreter

    synchronizeAndRefreshIfNeeded(actionContext)

    resolveControlAvt("dialog")(actionContext) match {
      case Some(targetDialog: XFormsEventTarget) =>
        XXFormsHideAction.hideDialog(
          targetDialog,
          XFormsAction.eventProperties(
            interpreter,
            actionContext.analysis,
            interpreter.eventObserver,
            actionContext.collector
          ),
          actionContext.collector
        )
      case _ =>
        debug(
          "xxf:hide: dialog does not refer to an existing xxf:dialog element, ignoring action",
          List("dialog id" -> actionContext.element.attributeValue("dialog"))
        )
    }
  }
}

object XXFormsHideAction {

  import XFormsEvent._

  def hideDialog(
    targetDialog : XFormsEventTarget,
    properties   : PropertyGetter = EmptyGetter,
    collector    : ErrorEventCollector
  ): Unit =
    Dispatch.dispatchEvent(
      new XXFormsDialogCloseEvent(
        targetDialog,
        properties
      ),
      collector
    )
}