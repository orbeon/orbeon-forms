package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.model.XFormsModel


/**
 * Extension xxf:invalidate-instances action.
 */
class XXFormsInvalidateInstancesAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {
    // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
    val indentedLogger = actionContext.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
    XFormsServerSharedInstancesCache.removeAll()(indentedLogger)
  }
}