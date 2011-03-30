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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.analysis.VariableAnalysis;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.saxon.om.Item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 10.1.1 The action Element
 */
public class XFormsActionAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        // Iterate over child actions
        int variablesCount = 0;
        final List<Element> currentVariableElements = new ArrayList<Element>();
        for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
            final Element currentActionElement = (Element) i.next();

            if (VariableAnalysis.isVariableElement(currentActionElement)) {
                // Remember variable element
                currentVariableElements.add(currentActionElement);
            } else {
                // NOTE: We execute children actions, even if they happen to have ev:observer or ev:target attributes

                // Push previous variables if any
                if (currentVariableElements.size() > 0) {
                    contextStack.addAndScopeVariables(propertyContext, actionInterpreter.getXBLContainer(), currentVariableElements, actionInterpreter.getSourceEffectiveId(actionElement));
                    variablesCount += currentVariableElements.size();
                }

                // Set context on action element
                final XBLBindings.Scope currentActionScope = actionInterpreter.getActionScope(currentActionElement);
                contextStack.pushBinding(propertyContext, currentActionElement, actionInterpreter.getSourceEffectiveId(actionElement), currentActionScope);

                // Run action
                actionInterpreter.runAction(propertyContext, event, eventObserver, currentActionElement);

                // Restore context
                contextStack.popBinding();


                // Clear variables
                currentVariableElements.clear();
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
