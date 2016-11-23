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

import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.events.XXFormsDialogOpenEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}

/**
 * Extension xxf:show action.
 */
class XXFormsShowAction extends XFormsAction {
  override def execute(actionContext: DynamicActionContext): Unit = {

    val interpreter   = actionContext.interpreter
    val actionElement = actionContext.element

    synchronizeAndRefreshIfNeeded(actionContext)

    resolveControlAvt("dialog")(actionContext) match {
      case Some(targetDialog: XFormsEventTarget) ⇒
        val constrainToViewport = interpreter.resolveAVT(actionElement, "constrain") != "false"
        val neighborEffectiveId = resolveControlAvt("neighbor", required = false)(actionContext) map (_.getEffectiveId)
        XXFormsShowAction.showDialog(
          targetDialog,
          neighborEffectiveId,
          constrainToViewport,
          XFormsAction.eventProperties(interpreter, actionElement)
        )
      case _ ⇒
        val indentedLogger = interpreter.indentedLogger
        if (indentedLogger.isDebugEnabled)
          indentedLogger.logDebug(
            "xxf:show",
            "dialog does not refer to an existing xxf:dialog element, ignoring action",
            "dialog id",
            actionContext.element.attributeValue("dialog")
          )
    }
  }
}

object XXFormsShowAction {

  import XFormsEvent._

  def showDialog(
    targetDialog        : XFormsEventTarget,
    neighborEffectiveId : Option[String] = None,
    constrainToViewport : Boolean        = true,
    properties          : PropertyGetter = EmptyGetter
  ): Unit =
    Dispatch.dispatchEvent(
      new XXFormsDialogOpenEvent(
        properties,
        targetDialog,
        neighborEffectiveId.orNull,
        constrainToViewport
      )
    )
}