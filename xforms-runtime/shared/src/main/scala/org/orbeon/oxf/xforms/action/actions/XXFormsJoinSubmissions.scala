package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger}
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}


class XXFormsJoinSubmissions extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {
    // Process all pending async submissions. The action will block until the method returns.
    if (CoreCrossPlatformSupport.isPE) {
      // Only supported in PE version
      actionContext.containingDocument
        .findAsynchronousSubmissionManager
        .filter(_.hasPendingAsynchronousSubmissions)
        .foreach(_.awaitAllAsynchronousSubmissions(actionContext.containingDocument))
    } else {
      // It's better to throw an exception since this action can have an impact on application behavior, not only performance
      throw new OXFException("xxf:join-submissions extension action is only supported in Orbeon Forms PE.")
    }
  }
}