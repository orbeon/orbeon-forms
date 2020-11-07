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
 * Extension xxf:invalidate-instance action.
 */
class XXFormsInvalidateInstanceAction extends XFormsAction {

  override def execute(
    actionInterpreter    : XFormsActionInterpreter,
    actionElement        : Element,
    actionScope          : Scope,
    hasOverriddenContext : Boolean,
    overriddenContext    : om.Item
  ): Unit = {
    // Evaluate AVTs
    val resourceURI = actionInterpreter.resolveAVT(actionElement, "resource")
    val handleXIncludeString = actionInterpreter.resolveAVT(actionElement, "xinclude")
    // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
    implicit val indentedLogger: IndentedLogger = actionInterpreter.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
    if (handleXIncludeString == null) {
      // No @xinclude attribute specified so remove all instances matching @resource
      // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
      XFormsServerSharedInstancesCache.remove(resourceURI, null, true)
      XFormsServerSharedInstancesCache.remove(resourceURI, null, false)
    } else {
      // Just remove instances matching both @resource and @xinclude
      val handleXInclude = handleXIncludeString.toBoolean
      XFormsServerSharedInstancesCache.remove(resourceURI, null, handleXInclude)
    }
  }
}