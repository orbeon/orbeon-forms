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

import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.action._
import collection.JavaConverters._
import org.orbeon.oxf.xforms.analysis._

/**
 * 10.1.1 The action Element
 */
class XFormsActionAction extends XFormsAction {

    override def execute(actionContext: DynamicActionContext) {

        val actionInterpreter = actionContext.interpreter
        val actionElement = actionContext.element

        val mediatype = actionElement.attributeValue(XFormsConstants.TYPE_QNAME)
        if (mediatype eq null) {
            // Standard XForms action
            val contextStack = actionInterpreter.actionXPathContext
            val partAnalysis = contextStack.container.getPartAnalysis

            // Iterate over child actions
            var variablesCount = 0
            for (childActionElement ← Dom4jUtils.elements(actionElement).asScala) {

                val childPrefixedId = actionInterpreter.getActionPrefixedId(childActionElement)

                Option(partAnalysis.getControlAnalysis(childPrefixedId)) match {
                    case Some(variable: VariableAnalysisTrait) ⇒
                        // Scope variable
                        contextStack.scopeVariable(variable, actionInterpreter.getSourceEffectiveId(actionElement), false)
                        variablesCount += 1
                    case Some(action) ⇒
                        // Run child action
                        // NOTE: We execute children actions, even if they happen to have ev:observer or ev:target attributes
                        actionInterpreter.runAction(action)
                    case None ⇒
                        throw new IllegalStateException
                }
            }

            // Unscope all variables
            for (_ ← 1 to variablesCount)
                contextStack.popBinding()
        } else {
            // Delegate to xxf:script
            XFormsActions.getScriptAction.execute(actionContext)
        }
    }
}
