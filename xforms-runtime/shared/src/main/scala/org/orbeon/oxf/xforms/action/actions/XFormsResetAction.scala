package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.action.XFormsAction
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsResetEvent
import org.orbeon.xforms.xbl.Scope
import org.orbeon.saxon.om


/**
 * 10.1.11 The reset Element
 *
 * TODO: Processing xforms-reset is not actually implemented yet in the model.
 */
class XFormsResetAction extends XFormsAction {

  override def execute(
    actionInterpreter    : XFormsActionInterpreter,
    actionElement        : Element,
    actionScope          : Scope,
    hasOverriddenContext : Boolean,
    overriddenContext    : om.Item
  ): Unit = {
    val modelOpt = actionInterpreter.actionXPathContext.getCurrentBindingContext.modelOpt
    // "This action initiates reset processing by dispatching an xforms-reset event to the specified model."
    modelOpt foreach { model =>
      Dispatch.dispatchEvent(new XFormsResetEvent(model))
    }
  }
}