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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XXFormsValueChanged;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

/**
 * 10.1.9 The setvalue Element
 */
public class XFormsSetvalueAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        final String valueExpression = actionElement.attributeValue(XFormsConstants.VALUE_QNAME);

        final String valueToSet;
        if (valueExpression != null) {
            // Value to set is computed with an XPath expression
            valueToSet = actionInterpreter.evaluateStringExpression(actionElement,
                    contextStack.getCurrentNodeset(), contextStack.getCurrentPosition(), valueExpression);
        } else {
            // Value to set is static content
            valueToSet = actionElement.getStringValue();
        }

        // Set value on current node
        final Item currentItem = contextStack.getCurrentSingleItem();
        if ((currentItem instanceof NodeInfo) && valueToSet != null) {
            // TODO: XForms 1.1: "Element nodes: If element child nodes are present, then an xforms-binding-exception
            // occurs. Otherwise, regardless of how many child nodes the element has, the result is that the string
            // becomes the new content of the element. In accord with the data model of [XPath 1.0], the element will
            // have either a single non-empty text node child, or no children string was empty.

            // Node exists and value is not null, we can try to set the value
            doSetValue(containingDocument, indentedLogger, eventObserver, (NodeInfo) currentItem, valueToSet, null, "setvalue", false);
        } else {
            // Node doesn't exist or value is null: NOP
            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug("xforms:setvalue", "not setting instance value",
                        "reason", "destination node not found",
                        "value", valueToSet
                );
            }
        }
    }

    public static boolean doSetValue(XFormsContainingDocument containingDocument,
                                     IndentedLogger indentedLogger, XFormsEventTarget eventTarget, NodeInfo currentNode,
                                     String valueToSet, String type, String source, boolean isCalculate) {

        assert valueToSet != null;

        final String currentValue = XFormsInstance.getValueForNodeInfo(currentNode);
        final boolean changed = !currentValue.equals(valueToSet);

        if (indentedLogger != null && indentedLogger.isDebugEnabled()) {
            final XFormsInstance modifiedInstance = (containingDocument != null) ? containingDocument.getInstanceForNode(currentNode) : null;
            indentedLogger.logDebug("xforms:setvalue", "setting instance value", "source", source, "value", valueToSet,
                    "changed", Boolean.toString(changed),
                    "instance", (modifiedInstance != null) ? modifiedInstance.getEffectiveId() : "N/A");
        }

        // We take the liberty of not requiring RRR and marking the instance dirty if the value hasn't actually changed
        if (changed) {

            // Actually set the value
            XFormsInstance.setValueForNodeInfo(containingDocument, eventTarget, currentNode, valueToSet, type);

            if (containingDocument != null) {
                final XFormsInstance modifiedInstance = containingDocument.getInstanceForNode(currentNode);
                if (modifiedInstance != null) {// can be null if you set a value in a non-instance doc

                    // Tell the model about the value change
                    modifiedInstance.getModel(containingDocument).markValueChange(currentNode, isCalculate);

                    // Dispatch extension event to instance
                    final XBLContainer modifiedContainer = modifiedInstance.getXBLContainer(containingDocument);
                    modifiedContainer.dispatchEvent(new XXFormsValueChanged(containingDocument, modifiedInstance, currentNode, currentValue, valueToSet));
                } else {
                    // NOTE: Is this the right thing to do if the value modified is not an instance value? Might not be needed!
                    containingDocument.getControls().markDirtySinceLastRequest(true);
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
