/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.xforms.action.actions._
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ControlFactory
import org.orbeon.oxf.xforms.analysis.EventHandler._
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, EventHandler, WithChildrenTrait}
import org.orbeon.oxf.xforms.analysis.controls.ActionTrait
import org.orbeon.xforms.XFormsNames._

object XFormsActions {

  val LoggingCategory = "action"

  val logger = LoggerFactory.createLogger(XFormsActions.getClass)

  private val Actions = Map(
    // Standard XForms actions
    xformsQName("action")                   -> new XFormsActionAction,
    xformsQName("dispatch")                 -> new XFormsDispatchAction,
    xformsQName("rebuild")                  -> new XFormsRebuildAction,
    xformsQName("recalculate")              -> new XFormsRecalculateAction,
    xformsQName("revalidate")               -> new XFormsRevalidateAction,
    xformsQName("refresh")                  -> new XFormsRefreshAction,
    xformsQName("setfocus")                 -> new XFormsSetfocusAction,
    xformsQName("load")                     -> new XFormsLoadAction,
    xformsQName("setvalue")                 -> new XFormsSetvalueAction,
    xformsQName("send")                     -> new XFormsSendAction,
    xformsQName("reset")                    -> new XFormsResetAction,
    xformsQName("message")                  -> new XFormsMessageAction,
    xformsQName("toggle")                   -> new XFormsToggleAction,
    xformsQName("insert")                   -> new XFormsInsertAction,
    xformsQName("delete")                   -> new XFormsDeleteAction,
    xformsQName("setindex")                 -> new XFormsSetindexAction,

    // Extension actions
    xxformsQName("script")                  -> new XFormsActionAction,
    xxformsQName("show")                    -> new XXFormsShowAction,
    xxformsQName("hide")                    -> new XXFormsHideAction,
    xxformsQName("invalidate-instance")     -> new XXFormsInvalidateInstanceAction,
    xxformsQName("invalidate-instances")    -> new XXFormsInvalidateInstancesAction,
    xxformsQName("join-submissions")        -> new XXFormsJoinSubmissions,
    xxformsQName("setvisited")              -> new XXFormsSetvisitedAction,
    xxformsQName("update-validity")         -> new XXFormsUpdateValidityAction,

    // xbl:handler as action container working like xf:action
    XBL_HANDLER_QNAME                       -> new XFormsActionAction
  )

  // Return the action with the QName, null if there is no such action
  def getAction(qName: QName): XFormsAction = Actions.get(qName) orNull
}