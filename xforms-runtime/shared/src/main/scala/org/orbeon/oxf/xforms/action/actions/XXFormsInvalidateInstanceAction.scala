package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.xforms.XFormsNames


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
    val resourceURI             = interpreter.resolveAVT(actionContext.analysis, "resource")
    val handleXIncludeStringOpt = Option(interpreter.resolveAVT(actionContext.analysis, "xinclude"))

    val ignoreQueryString = actionContext.element.attributeValueOpt(XFormsNames.IGNORE_QUERY_STRING).contains("true")

    // Use `XFormsModel` logger because it's what's used by `XFormsServerSharedInstancesCache` in other places
    def removeImpl(handleXInclude: Boolean): Unit =
      XFormsServerSharedInstancesCache.remove(
        resourceURI,
        requestBodyHash   = None,
        handleXInclude    = handleXInclude,
        ignoreQueryString = ignoreQueryString
      )(interpreter.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory))

    handleXIncludeStringOpt match {
      case Some(handleXIncludeString) =>
        removeImpl(handleXInclude = handleXIncludeString.toBoolean)
      case None =>
        // No `@xinclude` attribute specified so remove all instances matching `@resource`
        // NOTE: For now, we can't individually invalidate instances obtained through `POST` or `PUT`
        removeImpl(handleXInclude = true)
        removeImpl(handleXInclude = false)
    }
  }
}