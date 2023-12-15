package org.orbeon.oxf.xforms.action.actions

import org.log4s
import org.log4s.LogLevel
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContextStackSupport
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.analysis.controls.WithExpressionOrConstantTrait


class XXFormsLogAction extends XFormsAction {
  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit =
    actionContext.interpreter.containingDocument.logMessage(
      name    = actionContext.element.attributeValueOpt("name").getOrElse("orbeon"),
      level   = actionContext.element.attributeValueOpt("level").map(LogLevel.forName).getOrElse(log4s.Info),
      message =
        XFormsContextStackSupport.evaluateExpressionOrConstant(
          childElem           = actionContext.analysis.asInstanceOf[WithExpressionOrConstantTrait],
          parentEffectiveId   = actionContext.interpreter.getSourceEffectiveId(actionContext.analysis),
          pushContextAndModel = false, // `XFormsActionInterpreter` already handles that
          eventTarget         = actionContext.interpreter.eventObserver,
          collector           = actionContext.collector
        )(actionContext.interpreter.actionXPathContext)
          .getOrElse(""),
      collector = actionContext.collector
    )
}
