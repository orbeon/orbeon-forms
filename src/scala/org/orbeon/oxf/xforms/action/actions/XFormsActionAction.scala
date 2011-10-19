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

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis.VariableAnalysis
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventObserver
import org.orbeon.saxon.om.Item
import java.util.ArrayList
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import scala.collection.JavaConversions._
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.action._
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase

/**
 * 10.1.1 The action Element
 */
class XFormsActionAction extends XFormsAction {

    def execute(actionInterpreter: XFormsActionInterpreter, event: XFormsEvent, eventObserver: XFormsEventObserver,
                actionElement: Element, actionScope: XBLBindingsBase.Scope, hasOverriddenContext: Boolean, overriddenContext: Item) {

        val mediatype = actionElement.attributeValue(XFormsConstants.TYPE_QNAME)
        if (mediatype eq null) {
            // Standard XForms action
            val contextStack = actionInterpreter.getContextStack

            // Iterate over child actions
            var variablesCount = 0
            val currentVariableElements = new ArrayList[Element]
            for (currentActionElement <- Dom4jUtils.elements(actionElement)) {
                if (VariableAnalysis.isVariableElement(currentActionElement)) {
                    // Remember variable element
                    currentVariableElements.add(currentActionElement)
                } else {
                    // NOTE: We execute children actions, even if they happen to have ev:observer or ev:target attributes

                    // Push previous variables if any
                    if (currentVariableElements.nonEmpty) {
                        contextStack.addAndScopeVariables(actionInterpreter.getXBLContainer, currentVariableElements, actionInterpreter.getSourceEffectiveId(actionElement))
                        variablesCount += currentVariableElements.size
                    }

                    // Set context on action element
                    val currentActionScope = actionInterpreter.getActionScope(currentActionElement)
                    contextStack.pushBinding(currentActionElement, actionInterpreter.getSourceEffectiveId(actionElement), currentActionScope)

                    // Run action
                    actionInterpreter.runAction(event, eventObserver, currentActionElement)

                    // Restore context
                    contextStack.popBinding()

                    // Clear variables
                    currentVariableElements.clear()
                }
            }

            val indentedLogger = actionInterpreter.getIndentedLogger
            if (variablesCount > 0 && indentedLogger.isDebugEnabled)
                indentedLogger.logDebug("xforms:action", "evaluated variables within action", "count", variablesCount.toString)

            // Unscope all variables
            for (i <- 1 to variablesCount)
                contextStack.popBinding()
        } else {
            // Delegate to xxf:script
            XFormsActions.getScriptAction
                .execute(actionInterpreter, event, eventObserver, actionElement, actionScope, hasOverriddenContext, overriddenContext)
        }
    }
}

