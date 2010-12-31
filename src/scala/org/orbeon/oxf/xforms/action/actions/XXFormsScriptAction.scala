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
import org.orbeon.oxf.util.PropertyContext
import org.orbeon.oxf.xforms.action.XFormsAction
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventObserver
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms._
/**
 * Extension xxforms:script action.
 */
class XXFormsScriptAction extends XFormsAction {

    def execute(actionInterpreter: XFormsActionInterpreter, propertyContext: PropertyContext, event: XFormsEvent,
                eventObserver: XFormsEventObserver, actionElement: Element,
                actionScope: XBLBindings#Scope, hasOverriddenContext: Boolean, overriddenContext: Item) {

        val containingDocument = actionInterpreter.getContainingDocument

        // Get prefixed id of the xxforms:script element based on its location
        val actionPrefixedId = actionInterpreter.getXBLContainer.getFullPrefix + actionElement.attributeValue(XFormsConstants.ID_QNAME)

        if ("server" == actionElement.attributeValue("runat")) {
            // Run on server
            containingDocument.getScriptInterpreter.runScript(propertyContext, actionPrefixedId)
        } else {
            // Run on client
            containingDocument.addScriptToRun(actionPrefixedId, event, eventObserver)
        }
    }
}
