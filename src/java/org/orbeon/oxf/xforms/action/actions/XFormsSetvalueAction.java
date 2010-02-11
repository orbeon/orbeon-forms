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
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XXFormsValueChanged;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.List;

/**
 * 10.1.9 The setvalue Element
 */
public class XFormsSetvalueAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final IndentedLogger indentedLogger = actionInterpreter.getIndentedLogger();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        final String valueExpression = actionElement.attributeValue("value");
        final String content = actionElement.getStringValue();

        final String valueToSet;
        if (valueExpression != null) {
            // Value to set is computed with an XPath expression

            final List<Item> currentNodeset = contextStack.getCurrentNodeset();
            {
                if (currentNodeset != null && currentNodeset.size() > 0) {
                    // Evaluate
                    valueToSet = actionInterpreter.evaluateStringExpression(propertyContext, actionElement,
                            currentNodeset, contextStack.getCurrentPosition(), valueExpression);
                } else {
                    // If @ref doesn't point to anything, don't even try to compute the value
                    valueToSet = null;
                }
            }
        } else {
            // Value to set is static content
            valueToSet = content;
        }

        // Set value on current node
        final Item currentItem = contextStack.getCurrentSingleItem();
        if (currentItem instanceof NodeInfo) {
            // TODO: XForms 1.1: "Element nodes: If element child nodes are present, then an xforms-binding-exception
            // occurs. Otherwise, regardless of how many child nodes the element has, the result is that the string
            // becomes the new content of the element. In accord with the data model of [XPath 1.0], the element will
            // have either a single non-empty text node child, or no children string was empty.

            // Node exists, we can try to set the value
            doSetValue(propertyContext, containingDocument, indentedLogger, eventObserver, (NodeInfo) currentItem, valueToSet, null, false);
        } else {
            // Node doesn't exist, don't do anything
            // NOP
            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug("xforms:setvalue", "not setting instance value",
                        "reason", "destination node not found",
                        "value", valueToSet
                );
            }
        }
    }

    public static boolean doSetValue(PropertyContext propertyContext, XFormsContainingDocument containingDocument,
                                     IndentedLogger indentedLogger, XFormsEventTarget eventTarget, NodeInfo currentNode,
                                     String valueToSet, String type, boolean isCalculate) {

        final String currentValue = XFormsInstance.getValueForNodeInfo(currentNode);
        final boolean changed = !currentValue.equals(valueToSet);

        if (indentedLogger.isDebugEnabled()) {
            final XFormsInstance modifiedInstance = containingDocument.getInstanceForNode(currentNode);
            indentedLogger.logDebug("xforms:setvalue", "setting instance value", "value", valueToSet,
                    "changed", Boolean.toString(changed),
                    "instance", (modifiedInstance != null) ? modifiedInstance.getEffectiveId() : "N/A");
        }

        // We take the liberty of not requiring RRR and marking the instance dirty if the value hasn't actually changed
        if (changed) {

            // Actually set the value
            XFormsInstance.setValueForNodeInfo(propertyContext, containingDocument, eventTarget, currentNode, valueToSet, type);

            final XFormsInstance modifiedInstance = containingDocument.getInstanceForNode(currentNode);
            if (modifiedInstance != null) {// can be null if you set a value in a non-instance doc

                // Dispatch extension event to instance
                final XBLContainer modifiedContainer = modifiedInstance.getXBLContainer(containingDocument);
                modifiedContainer.dispatchEvent(propertyContext, new XXFormsValueChanged(containingDocument, modifiedInstance));

                if (!isCalculate) {
                    // When this is called from a calculate, we don't set the flags as revalidate and refresh will have been set already

                    // "XForms Actions that change only the value of an instance node results in setting the flags for
                    // recalculate, revalidate, and refresh to true and making no change to the flag for rebuild".
                    final XFormsModel.DeferredActionContext deferredActionContext = modifiedInstance.getModel(containingDocument).getDeferredActionContext();
                    deferredActionContext.recalculate = true;
                    deferredActionContext.revalidate = true;
                    modifiedContainer.requireRefresh();
                }
            } else {
                // NOTE: Is this the right thing to do if the value modified is not an instance value? Might not be needed!
                containingDocument.getControls().markDirtySinceLastRequest(true);
            }

            return true;
        } else {
            return false;
        }
    }
}
