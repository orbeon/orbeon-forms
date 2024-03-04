/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action._
import org.orbeon.oxf.xforms.analysis.WithChildrenTrait
import org.orbeon.oxf.xforms.analysis.controls.{ActionTrait, VariableAnalysisTrait}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsVariableControl
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission
import org.orbeon.xforms.XFormsNames

/**
 * 10.1.1 The action Element
 */
class XFormsActionAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val actionInterpreter = actionContext.interpreter
    val actionElement     = actionContext.element
    val actionAnalysis    = actionContext.analysis
    val bindingContext    = actionContext.bindingContext

    actionContext.partAnalysis.scriptsByPrefixedId.get(actionAnalysis.prefixedId) match {
      case Some(script @ StaticScript(_, ScriptType.JavaScript, paramExpressions, _)) =>
        // Evaluate script parameters if any and schedule the script to run

        def firstClientRepresentation(eventTarget: XFormsEventTarget): Option[XFormsEventTarget] =
          eventTarget.iterateAncestorEventTargets(includeSelf = true, includeComponents = true).find {
            case _ @ (_: XFormsVariableControl | _: XFormsModelSubmission | _: XFormsModel | _: XFormsInstance) => false
            case c: XFormsControl if c.appearances(XFormsNames.XXFORMS_INTERNAL_APPEARANCE_QNAME)               => false
            case _                                                                                              => true
          }

        actionInterpreter.containingDocument.addScriptToRun(
          ScriptInvocation(
            script              = script,
            targetEffectiveId   = firstClientRepresentation(actionContext.interpreter.event.targetObject).map(_.getEffectiveId),
            observerEffectiveId = firstClientRepresentation(actionContext.interpreter.eventObserver).map(_.getEffectiveId),
            paramValues         = paramExpressions map { expr =>
              // https://github.com/orbeon/orbeon-forms/issues/2499
              actionInterpreter.evaluateAsString(
                actionAnalysis,
                actionElement,
                bindingContext.nodeset,
                bindingContext.position,
                expr
              )
            }
          )
        )
      case Some(StaticScript(_, ScriptType.XPath, _, ShareableScript(_, _, body, _))) =>
        // Evaluate XPath expression for its side-effects only
        actionInterpreter.evaluateKeepItems(
          actionAnalysis,
          bindingContext.nodeset,
          bindingContext.position,
          body
        )
      case None =>
        // Grouping XForms action which executes its children actions

        val contextStack = actionInterpreter.actionXPathContext

        // Iterate over child actions
        var variablesCount = 0

        for (childActionAnalysis <- actionAnalysis.asInstanceOf[WithChildrenTrait].children) {
          childActionAnalysis match {
            case variable: VariableAnalysisTrait =>
              // Scope variable
              contextStack.scopeVariable(
                variable,
                actionInterpreter.getSourceEffectiveId(actionAnalysis),
                actionContext.interpreter.eventObserver,
                actionContext.collector
              )
              variablesCount += 1
            case action: ActionTrait =>
              // Run child action
              // NOTE: We execute children actions even if they happen to have `observer` or `target` attributes.
              actionInterpreter.runAction(action, actionInterpreter.eventObserver, actionContext.collector)
            case _ =>
              throw new IllegalStateException
          }
        }

        // Unscope all variables
        for (_ <- 1 to variablesCount)
          contextStack.popBinding()
    }
  }
}
