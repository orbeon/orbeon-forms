package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.Element
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.action.XFormsAction
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.xforms.xbl.Scope
import org.orbeon.saxon.om


/**
 * Extension xxf:invalidate-instances action.
 */
class XXFormsInvalidateInstancesAction extends XFormsAction {

  override def execute(
    actionInterpreter    : XFormsActionInterpreter,
    actionElement        : Element,
    actionScope          : Scope,
    hasOverriddenContext : Boolean,
    overriddenContext    : om.Item
  ): Unit = {
    // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
    val indentedLogger = actionInterpreter.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
    XFormsServerSharedInstancesCache.removeAll(indentedLogger)
  }
}