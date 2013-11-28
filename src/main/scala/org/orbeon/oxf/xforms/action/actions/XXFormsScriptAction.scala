/**
 *  Copyright (C) 2011 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.xforms._
import action.{DynamicActionContext, XFormsAction}
import org.orbeon.oxf.common.OXFException
import script.ServerScript

/**
 * Extension xxf:script action.
 */
class XXFormsScriptAction extends XFormsAction {

    override def execute(actionContext: DynamicActionContext) {

        val actionInterpreter = actionContext.interpreter
        val actionElement = actionContext.element

        val mediatype = actionElement.attributeValue(XFormsConstants.TYPE_QNAME)
        mediatype match {
            case "javascript" | "text/javascript" | "application/javascript" | null ⇒
                // Get script based on id
                val script = {
                    val partAnalysis = actionInterpreter.actionXPathContext.container.getPartAnalysis
                    partAnalysis.scripts(actionInterpreter.getActionPrefixedId(actionElement))
                }

                // Run Script on server or client
                script match {
                    case serverScript: ServerScript ⇒
                        actionInterpreter.containingDocument.getScriptInterpreter.runScript(serverScript)
                    case clientScript ⇒
                        actionInterpreter.containingDocument.addScriptToRun(clientScript, actionContext.interpreter.event, actionContext.interpreter.eventObserver)
                }
            case "xpath" | "text/xpath" | "application/xpath" ⇒ // "unofficial" type
                // Evaluate XPath expression for its side effects only
                val bindingContext = actionInterpreter.actionXPathContext.getCurrentBindingContext
                actionInterpreter.evaluateKeepItems(actionElement, bindingContext.nodeset, bindingContext.position, actionElement.getText)
            case other ⇒
                throw new OXFException("Unsupported script type: " + other)
        }
    }
}
