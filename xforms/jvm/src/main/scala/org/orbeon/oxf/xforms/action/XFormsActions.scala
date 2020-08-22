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
import org.orbeon.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.actions._
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.ControlFactory
import org.orbeon.oxf.xforms.analysis.controls.ActionTrait
import org.orbeon.oxf.xforms.analysis.{ChildrenActionsAndVariablesTrait, SimpleElementAnalysis}
import org.orbeon.oxf.xforms.event.EventHandlerImpl

// 2016-02-05: This object mixes runtime actions and compile-time actions. We should separate this properly.
object XFormsActions {
  val LOGGING_CATEGORY = "action"
  val logger = LoggerFactory.createLogger(XFormsActions.getClass)

  private def xformsQName(name: String)  = QName(name, XFORMS_NAMESPACE)
  private def xxformsQName(name: String) = QName(name, XXFORMS_NAMESPACE)

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

  // Return a factory for action analysis
  val ActionFactory: PartialFunction[Element, ControlFactory] = {

    def isEventHandler(e: Element) = EventHandlerImpl.isEventHandler(e)

    val actionFactory: PartialFunction[Element, ControlFactory] = {
      case e if isContainerAction(e.getQName) && isEventHandler(e) => new EventHandlerImpl(_, _, _, _, _)      with ActionTrait with ChildrenActionsAndVariablesTrait
      case e if isContainerAction(e.getQName)                      => new SimpleElementAnalysis(_, _, _, _, _) with ActionTrait with ChildrenActionsAndVariablesTrait
      case e if isAction(e.getQName) && isEventHandler(e)          => new EventHandlerImpl(_, _, _, _, _)      with ActionTrait
      case e if isAction(e.getQName)                               => new SimpleElementAnalysis(_, _, _, _, _) with ActionTrait
    }

    actionFactory
  }

  // Return the action with the QName, null if there is no such action
  def getAction(qName: QName) = Actions.get(qName) orNull

  // Whether the given action exists
  def isAction(qName: QName) = Actions.contains(qName)

  // Whether the QName is xf:action
  def isContainerAction(qName: QName) = Set(xformsQName("action"), XBL_HANDLER_QNAME)(qName)

  // Whether the element is xf:action
  def isDispatchAction(qName: QName) =
    qName.namespace.uri == XFORMS_NAMESPACE_URI && qName.localName == "dispatch"
}