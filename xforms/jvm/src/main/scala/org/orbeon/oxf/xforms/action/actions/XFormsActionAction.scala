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
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.controls.ActionTrait
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsVariableControl
import org.orbeon.oxf.xforms.event.XFormsEventTarget
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.xforms.XFormsNames

/**
 * 10.1.1 The action Element
 */
class XFormsActionAction extends XFormsAction {

  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val actionInterpreter = actionContext.interpreter
    val actionElement     = actionContext.element
    val bindingContext    = actionContext.bindingContext

    def observerAncestorsOrSelfIterator(observer: XFormsEventTarget) =
      Iterator.iterate(observer)(_.parentEventObserver) takeWhile (_ ne null)

    def hasClientRepresentation(observer: XFormsEventTarget) =
      observer match {
        case _: XFormsVariableControl => false
        case c: XFormsControl if c.appearances(XFormsNames.XXFORMS_INTERNAL_APPEARANCE_QNAME) => false
        case _ => true
      }

    def firstClientRepresentation(observer: XFormsEventTarget) =
      observerAncestorsOrSelfIterator(observer) find hasClientRepresentation

    actionContext.partAnalysis.scriptsByPrefixedId.get(actionInterpreter.getActionPrefixedId(actionElement)) match {
      case Some(script @ StaticScript(_, JavaScriptScriptType, paramExpressions, _)) =>
        // Evaluate script parameters if any and schedule the script to run
        actionInterpreter.containingDocument.addScriptToRun(
          ScriptInvocation(
            script              = script,
            targetEffectiveId   = actionContext.interpreter.event.targetObject.getEffectiveId,
            observerEffectiveId = firstClientRepresentation(actionContext.interpreter.eventObserver) map (_.getEffectiveId) getOrElse "",
            paramValues         = paramExpressions map { expr =>
              // https://github.com/orbeon/orbeon-forms/issues/2499
              actionInterpreter.evaluateAsString(
                actionElement,
                bindingContext.nodeset,
                bindingContext.position,
                expr
              )
            }
          )
        )
      case Some(StaticScript(_, XPathScriptType, _, ShareableScript(_, _, body, _))) =>
        // Evaluate XPath expression for its side effects only
        actionInterpreter.evaluateKeepItems(
          actionElement,
          bindingContext.nodeset,
          bindingContext.position,
          body
        )
      case None =>
        // Grouping XForms action which executes its children actions

        val contextStack = actionInterpreter.actionXPathContext
        val partAnalysis = actionContext.partAnalysis

        // Iterate over child actions
        var variablesCount = 0
        for (childActionElement <- Dom4j.elements(actionElement)) {

          val childPrefixedId = actionInterpreter.getActionPrefixedId(childActionElement)

          partAnalysis.findControlAnalysis(childPrefixedId) match {
            case Some(variable: VariableAnalysisTrait) =>
              // Scope variable
              contextStack.scopeVariable(variable, actionInterpreter.getSourceEffectiveId(actionElement), false)
              variablesCount += 1
            case Some(action: ActionTrait) =>
              // Run child action
              // NOTE: We execute children actions even if they happen to have `observer` or `target` attributes.
              actionInterpreter.runAction(action)
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
