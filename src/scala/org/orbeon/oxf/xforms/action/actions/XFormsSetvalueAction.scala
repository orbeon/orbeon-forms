/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.action.{XFormsActionInterpreter, XFormsAction}
import org.orbeon.oxf.xforms.event.{XFormsEventObserver, XFormsEvent}
import org.dom4j.Element
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase
import org.orbeon.saxon.om.{NodeInfo, Item}
import org.orbeon.oxf.xforms.model.DataModel

/**
 * 10.1.9 The setvalue Element
 */
class XFormsSetvalueAction extends XFormsAction {
    def execute(actionInterpreter: XFormsActionInterpreter, event: XFormsEvent, eventObserver: XFormsEventObserver,
                actionElement: Element, actionScope: XBLBindingsBase.Scope, hasOverriddenContext: Boolean, overriddenContext: Item): Unit = {
        
        val indentedLogger = actionInterpreter.getIndentedLogger
        val containingDocument = actionInterpreter.getContainingDocument
        val contextStack = actionInterpreter.getContextStack
        
        val valueExpression = Option(actionElement.attributeValue(XFormsConstants.VALUE_QNAME))

        // Determine value to set
        val valueToSet =
            valueExpression match {
                case Some(valueExpression) ⇒
                    // Value to set is computed with an XPath expression
                    actionInterpreter.evaluateStringExpression(actionElement, contextStack.getCurrentNodeset, contextStack.getCurrentPosition, valueExpression)
                case None ⇒
                    // Value to set is static content
                    actionElement.getStringValue
            }
        
        assert(valueToSet ne null)

        // Set the value on target node if possible
        contextStack.getCurrentSingleItem match {
            case nodeInfo: NodeInfo ⇒
                // NOTE: XForms 1.1 seems to require dispatching xforms-binding-exception in case the target node cannot be
                // written to. But because of the way we now handle errors in actions, we throw an exception instead and
                // action processing is interrupted.
                DataModel.setValueIfChanged(nodeInfo, valueToSet,
                    DataModel.logAndNotifyValueChange(containingDocument, indentedLogger, "setvalue", nodeInfo, _, valueToSet, false),
                    reason => throw new OXFException(reason.message))
            case _ ⇒
                // Node doesn't exist: NOP
                if (indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug("xforms:setvalue", "not setting instance value", "reason", "destination node not found", "value", valueToSet)
        }
    }
}

