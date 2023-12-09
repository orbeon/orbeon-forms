package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XFormsResetEvent


/**
 * 10.1.11 The reset Element
 *
 * TODO: Processing xforms-reset is not actually implemented yet in the model.
 */
class XFormsResetAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {
    val modelOpt = actionContext.bindingContext.modelOpt
    // "This action initiates reset processing by dispatching an xforms-reset event to the specified model."
    modelOpt foreach { model =>
      Dispatch.dispatchEvent(new XFormsResetEvent(model), actionContext.collector)
    }
  }
}