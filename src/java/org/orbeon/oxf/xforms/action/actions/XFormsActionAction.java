/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.Variable;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;

import java.util.Iterator;

/**
 * 10.1.1 The action Element
 */
public class XFormsActionAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XBLContainer container = actionInterpreter.getXBLContainer();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        // Iterate over child actions
        int variablesCount = 0;
        for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
            final Element currentActionElement = (Element) i.next();
            final String currentActionName = currentActionElement.getName();

            if (currentActionName.equals("variable")) {
                // Create variable object
                final Variable variable = new Variable(container, contextStack, currentActionElement);

                // Push the variable on the context stack. Note that we do as if each variable was a "parent" of the following controls and variables.
                // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation in the future.
                contextStack.pushVariable(currentActionElement, variable.getVariableName(), variable.getVariableValue(propertyContext, true));

                variablesCount++;
            } else {
                // NOTE: We execute children actions, even if they happen to have ev:observer or ev:target attributes

                // Set context on action element
                contextStack.pushBinding(propertyContext, currentActionElement);

                // Run action
                actionInterpreter.runAction(propertyContext, targetId, eventObserver, currentActionElement);

                // Restore context
                contextStack.popBinding();
            }
        }

        final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
        if (variablesCount > 0 && indentedLogger.isDebugEnabled())
            indentedLogger.logDebug("xforms:action", "evaluated variables within action",
                    "count", Integer.toString(variablesCount));

        // Unscope all variables
        for (int i = 0; i < variablesCount; i++)
            contextStack.popBinding();
    }
}
