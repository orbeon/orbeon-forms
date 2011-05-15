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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.saxon.om.Item;

/**
 * 9.3.7 The setindex Element
 */
public class XFormsSetindexAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        // Check presence of attribute
        final String repeatAttribute = actionElement.attributeValue("repeat");
        if (repeatAttribute == null)
            throw new OXFException("Missing mandatory 'repeat' attribute on xforms:setindex element.");

        // Can't evaluate index XPath if no context
        final Item currentSingleNode = actionInterpreter.getContextStack().getCurrentSingleItem();
        if (currentSingleNode == null)
            return;

        // Get repeat static id
        final String repeatStaticId = actionInterpreter.resolveAVTProvideValue(actionElement, repeatAttribute);

        // Determine index
        final String indexXPath = actionElement.attributeValue("index");
        final String indexString = actionInterpreter.evaluateStringExpression(actionElement,
                contextStack.getCurrentNodeset(), contextStack.getCurrentPosition(), "number(" + indexXPath + ")");

        actionInterpreter.getIndentedLogger().logDebug("xforms:setindex", "setting index", "index", indexString);

        // Execute
        executeSetindexAction(actionInterpreter, actionElement, repeatStaticId, indexString);
    }

    private static void executeSetindexAction(XFormsActionInterpreter actionInterpreter, Element actionElement,
                                              String repeatStaticId, String indexString) {
        if ("NaN".equals(indexString)) {
            // "If the index evaluates to NaN the action has no effect."
            return;
        }

        final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();

        // "This XForms Action begins by invoking the deferred update behavior."
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        containingDocument.synchronizeAndRefresh();

        // Find repeat control
        final Object control = actionInterpreter.resolveEffectiveControl(actionElement, repeatStaticId);
        if (control instanceof XFormsRepeatControl) {
            // Set its new index
            final int newRepeatIndex = Integer.parseInt(indexString);
            final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
            if (repeatControl.getIndex() != newRepeatIndex) {

                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug("xforms:setindex", "setting index upon xforms:setindex",
                            "old index", Integer.toString(repeatControl.getIndex()), "new index", Integer.toString(newRepeatIndex));
                }

                // Set index on control
                repeatControl.setIndex(newRepeatIndex);
            }
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("xforms:setindex", "index does not refer to an existing xforms:repeat element, ignoring action",
                        "repeat id", repeatStaticId);
        }
    }
}
