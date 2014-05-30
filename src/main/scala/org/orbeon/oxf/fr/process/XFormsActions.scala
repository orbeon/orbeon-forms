/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.process

import SimpleProcess._
import org.orbeon.oxf.fr.FormRunner.{splitQueryDecodeParams ⇒ _, recombineQuery ⇒ _, _}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import util.Try

trait XFormsActions {

    def AllowedXFormsActions = Map[String, Action](
        "xf:send"     → tryXFormsSend,
        "xf:dispatch" → tryXFormsDispatch,
        "xf:show"     → tryShowDialog
    )

    def tryXFormsSend(params: ActionParams): Try[Any] =
        Try {
            val submission = paramByNameOrDefault(params, "submission")
            submission foreach (sendThrowOnError(_))
        }

    def tryXFormsDispatch(params: ActionParams): Try[Any] =
        Try {
            val eventName = paramByNameOrDefault(params, "name")
            val eventTargetId = paramByName(params, "targetid") getOrElse FormModel
            eventName foreach (dispatch(_, eventTargetId))
        }

    def tryShowDialog(params: ActionParams): Try[Any] =
        Try {
            val dialogName = paramByNameOrDefault(params, "dialog")
            dialogName foreach (show(_))
        }
}
