package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CoreCrossPlatformSupport
import org.orbeon.oxf.xforms.action.{XFormsAction, XFormsActionInterpreter}
import org.orbeon.saxon.om
import org.orbeon.xforms.xbl.Scope


class XXFormsJoinSubmissions extends XFormsAction {

  override def execute(
    actionInterpreter    : XFormsActionInterpreter,
    actionElement        : Element,
    actionScope          : Scope,
    hasOverriddenContext : Boolean,
    overriddenContext    : om.Item
  ): Unit = {
    // Process all pending async submissions. The action will block until the method returns.
    if (CoreCrossPlatformSupport.isPE) {
      // Only supported in PE version
      actionInterpreter.containingDocument
        .getAsynchronousSubmissionManager(create = false)
        .filter(_.hasPendingAsynchronousSubmissions)
        .foreach(_.processAllAsynchronousSubmissionsForJoin(actionInterpreter.containingDocument))
    } else {
      // It's better to throw an exception since this action can have an impact on application behavior, not only performance
      throw new OXFException("xxf:join-submissions extension action is only supported in Orbeon Forms PE.")
    }
  }
}