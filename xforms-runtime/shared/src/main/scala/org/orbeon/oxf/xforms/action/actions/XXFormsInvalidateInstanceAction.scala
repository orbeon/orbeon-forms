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

    // Use `XFormsModel` logger because it's what's used by `XFormsServerSharedInstancesCache` in other places
    XXFormsInvalidateInstanceAction.doInvalidateInstance(
      resourceURI       = interpreter.resolveAVT(actionContext.analysis, "resource"),
      handleXInclude    = Option(interpreter.resolveAVT(actionContext.analysis, "xinclude")).map(_.toBoolean),
      ignoreQueryString = actionContext.element.attributeValueOpt(XFormsNames.IGNORE_QUERY_STRING).contains("true")
    )(interpreter.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory))
}
}

object XXFormsInvalidateInstanceAction {

  // If `handleXInclude.isEmpty`, remove all instances matching `resourceURI`.
  // NOTE: For now, we can't individually invalidate instances obtained through `POST` or `PUT`
  def doInvalidateInstance(
    resourceURI      : String,
    handleXInclude   : Option[Boolean],
    ignoreQueryString: Boolean
  )(implicit
    logger           : IndentedLogger
  ): Unit =
    XFormsServerSharedInstancesCache.remove(
      instanceSourceURI = resourceURI,
      handleXInclude    = handleXInclude,
      ignoreQueryString = ignoreQueryString
    )
}