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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventObserver
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms._
import action.{XFormsAction, XFormsActionInterpreter}
import org.orbeon.oxf.common.OXFException
import xbl.XBLBindingsBase

/**
 * Extension xxforms:script action.
 */
class XXFormsScriptAction extends XFormsAction {

    def execute(actionInterpreter: XFormsActionInterpreter, event: XFormsEvent,
                eventObserver: XFormsEventObserver, actionElement: Element,
                actionScope: XBLBindingsBase.Scope, hasOverriddenContext: Boolean, overriddenContext: Item) {

        val mediatype = actionElement.attributeValue(XFormsConstants.TYPE_QNAME)
        mediatype match {
            case "javascript" | "text/javascript" | "application/javascript" | null =>
                // Get prefixed id of the xxforms:script element based on its location
                val actionPrefixedId = actionInterpreter.getXBLContainer.getFullPrefix + actionElement.attributeValue(XFormsConstants.ID_QNAME)
                val containingDocument = actionInterpreter.getContainingDocument

                // Run Script on server or client
                actionElement.attributeValue("runat") match {
                    case "server" =>
                        containingDocument.getScriptInterpreter.runScript(actionPrefixedId)
                    case _ =>
                        containingDocument.addScriptToRun(actionPrefixedId, event, eventObserver)
                }
            case "xpath" | "text/xpath" | "application/xpath" => // "unofficial" type
                // Evaluate XPath expression for its side effects only
                val bindingContext = actionInterpreter.getContextStack.getCurrentBindingContext
                actionInterpreter.evaluateExpression(actionElement, bindingContext.getNodeset, bindingContext.getPosition, actionElement.getText)
            case other =>
                throw new OXFException("Unsupported script type: " + other)
        }
    }
}
