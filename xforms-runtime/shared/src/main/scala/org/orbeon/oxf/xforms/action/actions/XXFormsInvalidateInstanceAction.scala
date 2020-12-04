package org.orbeon.oxf.xforms.action.actions

import org.orbeon.dom.Element
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction, XFormsActionInterpreter}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.saxon.om

/**
 * Extension xxf:invalidate-instance action.
 */
class XXFormsInvalidateInstanceAction extends XFormsAction {

  override def execute(
    actionContext : DynamicActionContext)(implicit
    logger        : IndentedLogger
  ): Unit = {

    val interpreter = actionContext.interpreter

    // Evaluate AVTs
    val resourceURI = interpreter.resolveAVT(actionContext.analysis, "resource")
    val handleXIncludeString = interpreter.resolveAVT(actionContext.analysis, "xinclude")
    // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
    val indentedLogger = interpreter.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
    if (handleXIncludeString == null) {
      // No @xinclude attribute specified so remove all instances matching @resource
      // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
      XFormsServerSharedInstancesCache.remove(resourceURI, null, true)(indentedLogger)
      XFormsServerSharedInstancesCache.remove(resourceURI, null, false)(indentedLogger)
    } else {
      // Just remove instances matching both @resource and @xinclude
      val handleXInclude = handleXIncludeString.toBoolean
      XFormsServerSharedInstancesCache.remove(resourceURI, null, handleXInclude)(indentedLogger)
    }
  }
}