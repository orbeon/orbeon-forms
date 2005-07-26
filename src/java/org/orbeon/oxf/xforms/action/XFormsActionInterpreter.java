/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action;

import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of all the XForms actions.
 */
public class XFormsActionInterpreter {

    private XFormsContainingDocument containingDocument;
    private XFormsControls xformsControls;

    public XFormsActionInterpreter(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
        this.xformsControls = containingDocument.getXFormsControls();
    }

    /**
     * Execute an XForms action.
     *
     * @param pipelineContext       current PipelineContext
     * @param targetId              id of the target control
     * @param eventHandlerContainer event handler containe this action is running in
     * @param actionElement         Element specifying the action to execute
     * @param actionContext         ActionContext instance for deferred updates, or null
     */
    public void runAction(final PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement, ActionContext actionContext) {

        // Check that we understand the action element
        final String actionNamespaceURI = actionElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        // Set binding context
        setBindingContext(pipelineContext, eventHandlerContainer.getId(), actionElement);

        final String actionEventName = actionElement.getName();

        if (XFormsActions.XFORMS_SETVALUE_ACTION.equals(actionEventName)) {
            // 10.1.9 The setvalue Element
            // xforms:setvalue

            final String value = actionElement.attributeValue("value");
            final String content = actionElement.getStringValue();

            final XFormsInstance currentInstance = xformsControls.getCurrentInstance();
            final String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                Map namespaceContext = Dom4jUtils.getNamespaceContextNoDefault(actionElement);
                valueToSet = currentInstance.evaluateXPathAsString(pipelineContext, xformsControls.getCurrentSingleNode(), value, namespaceContext, null, xformsControls.getFunctionLibrary(), null);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            // Set value on current node
            final Node currentNode = xformsControls.getCurrentSingleNode();
            XFormsInstance.setValueForNode(pipelineContext, currentNode, valueToSet);

            if (actionContext != null) {
                // "XForms Actions that change only the value of an instance node results in setting
                // the flags for recalculate, revalidate, and refresh to true and making no change to
                // the flag for rebuild".
                actionContext.recalculate = true;
                actionContext.revalidate = true;
                actionContext.refresh = true;
            } else {
                // Send events directly
                final XFormsModel model = xformsControls.getCurrentModel();
                containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));
            }

        } else if (XFormsActions.XFORMS_RESET_ACTION.equals(actionEventName)) {
            // 10.1.11 The reset Element

            final String modelId = actionElement.attributeValue("model");

            final Object modelObject = containingDocument.getObjectById(pipelineContext, modelId);
            if (modelObject instanceof XFormsModel) {
                final XFormsModel model = (XFormsModel) modelObject;
                containingDocument.dispatchEvent(pipelineContext, new XFormsResetEvent(model));
            } else {
                throw new OXFException("xforms:reset model attribute must point to an xforms:model element.");
            }

            // "the reset action takes effect immediately and clears all of the flags."
            if (actionContext != null)
                actionContext.setAll(false);

        } else if (XFormsActions.XFORMS_ACTION_ACTION.equals(actionEventName)) {
            // 10.1.1 The action Element

            final ActionContext newActionContext = (actionContext == null) ? new ActionContext() : null;
            for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
                final Element embeddedActionElement = (Element) i.next();
                runAction(pipelineContext, targetId, eventHandlerContainer, embeddedActionElement, (newActionContext == null) ? actionContext : newActionContext );
            }
            if (newActionContext != null) {
                // Binding context has to be reset as it may have been modified by sub-actions
                setBindingContext(pipelineContext, eventHandlerContainer.getId(), actionElement);
                final XFormsModel model = xformsControls.getCurrentModel();

                // Process deferred behavior
                if (newActionContext.rebuild)
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));
                if (newActionContext.recalculate)
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
                if (newActionContext.revalidate)
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));
                if (newActionContext.refresh)
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));
            }

        } else if (XFormsActions.XFORMS_REBUILD_ACTION.equals(actionEventName)) {
            // 10.1.3 The rebuild Element

            final XFormsModel model = xformsControls.getCurrentModel();
            containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            if (actionContext != null)
                actionContext.rebuild = false;

        } else if (XFormsActions.XFORMS_RECALCULATE_ACTION.equals(actionEventName)) {
            // 10.1.4 The recalculate Element

            final XFormsModel model = xformsControls.getCurrentModel();
            containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            if (actionContext != null)
                actionContext.recalculate = false;

        } else if (XFormsActions.XFORMS_REVALIDATE_ACTION.equals(actionEventName)) {
            // 10.1.5 The revalidate Element

            final XFormsModel model = xformsControls.getCurrentModel();
            containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            if (actionContext != null)
                actionContext.revalidate = false;

        } else if (XFormsActions.XFORMS_REFRESH_ACTION.equals(actionEventName)) {
            // 10.1.6 The refresh Element

            final XFormsModel model = xformsControls.getCurrentModel();
            containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));

            // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
            // have an immediate effect, and clear the corresponding flag."
            if (actionContext != null)
                actionContext.refresh = false;

        } else if (XFormsActions.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element

            final String caseId = actionElement.attributeValue("case");

            // Update xforms:switch info and dispatch events
            xformsControls.updateSwitchInfo(pipelineContext, caseId);

        } else if (XFormsActions.XFORMS_INSERT_ACTION.equals(actionEventName)) {
            // 9.3.5 The insert Element
            final String atAttribute = actionElement.attributeValue("at");
            final String positionAttribute = actionElement.attributeValue("position");

            // Set current binding in order to evaluate the current nodeset
            // "1. The homogeneous collection to be updated is determined by evaluating the Node Set Binding."

            final List collectionToBeUpdated = xformsControls.getCurrentNodeset();

            if (collectionToBeUpdated.size() > 0) {
                // "If the collection is empty, the insert action has no effect."

                // "2. The node-set binding identifies a homogeneous collection in the instance
                // data. The final member of this collection is cloned to produce the node that will
                // be inserted."
                final Element clonedElement;
                {
                    final Element lastElement = (Element) collectionToBeUpdated.get(collectionToBeUpdated.size() - 1);
                    clonedElement = (Element) lastElement.createCopy();
                    XFormsUtils.setInitialDecoration(clonedElement);
                }

                // "Finally, this newly created node is inserted into the instance data at the location
                // specified by attributes position and at."

                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();
                final String insertionIndexString = currentInstance.evaluateXPathAsString(pipelineContext, xformsControls.getCurrentSingleNode(),
                        "round(" + atAttribute + ")", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

                // Don't think we will get NaN with XPath 2.0...
                int insertionIndex = "NaN".equals(insertionIndexString) ? collectionToBeUpdated.size() : Integer.parseInt(insertionIndexString) ;

                // Adjust index to be in range
                if (insertionIndex > collectionToBeUpdated.size())
                    insertionIndex = collectionToBeUpdated.size();

                if (insertionIndex < 1)
                    insertionIndex = 1;

                // Find actual insertion point and insert
                final Element indexElement = (Element) collectionToBeUpdated.get(insertionIndex - 1);

                final Element parentElement = indexElement.getParent();
                final List siblingElements = parentElement.elements();
                final int actualIndex = siblingElements.indexOf(indexElement);

                // Insert new element (changes to the list are reflected in the document)
                final int newNodeIndex;
                if ("after".equals(positionAttribute) || "NaN".equals(insertionIndexString)) {
                    siblingElements.add(actualIndex + 1, clonedElement);
                    newNodeIndex = insertionIndex + 1;
                } else if ("before".equals(positionAttribute)) {
                    siblingElements.add(actualIndex, clonedElement);
                    newNodeIndex = insertionIndex;
                } else {
                    throw new OXFException("Invalid 'position' attribute: " + positionAttribute + ". Must be either 'before' or 'after'.");
                }

                // "3. The index for any repeating sequence that is bound to the homogeneous
                // collection where the node was added is updated to point to the newly added node.
                // The indexes for inner nested repeat collections are re-initialized to
                // startindex."

                // Find list of affected repeat ids
                final Map boundRepeatIds = new HashMap();
                final Map childrenRepeatIds = new HashMap();
                findAffectedRepeatIds(pipelineContext, parentElement, boundRepeatIds, childrenRepeatIds);

                // Rebuild ControlsState
                xformsControls.rebuildCurrentControlsState(pipelineContext);
                final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

                // Update repeat information for the ids found
                if (boundRepeatIds.size() != 0 || childrenRepeatIds.size() != 0) {

                    for (Iterator i = boundRepeatIds.keySet().iterator(); i.hasNext();) {
                        final String repeatId = (String) i.next();
                        currentControlsState.updateRepeatIndex(repeatId, newNodeIndex);
                    }
                    for (Iterator i = childrenRepeatIds.keySet().iterator(); i.hasNext();) {
                        final String repeatId = (String) i.next();
                        //final int newIndex = ((Integer) currentControlsState.getInitialRepeatIdToIndex().get(repeatId)).intValue();
                        final int newIndex = 1;
                        currentControlsState.updateRepeatIndex(repeatId, newIndex);
                    }
                }

                // "4. If the insert is successful, the event xforms-insert is dispatched."
                containingDocument.dispatchEvent(pipelineContext, new XFormsInsertEvent(currentInstance, atAttribute));

                if (actionContext != null) {
                    // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
                    actionContext.setAll(true);
                } else {
                    // Binding context has to be reset as the controls have been updated
                    setBindingContext(pipelineContext, eventHandlerContainer.getId(), actionElement);
                    final XFormsModel model = xformsControls.getCurrentModel();
                    // Send events directly
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));
                }
            }

        } else if (XFormsActions.XFORMS_DELETE_ACTION.equals(actionEventName)) {
            // 9.3.6 The delete Element

            final String atAttribute = actionElement.attributeValue("at");

            // Set current binding in order to evaluate the current nodeset
            // "1. The homogeneous collection to be updated is determined by evaluating the Node Set Binding."

            final List collectionToBeUpdated = xformsControls.getCurrentNodeset();

            if (collectionToBeUpdated.size() > 0) {
                // "If the collection is empty, the delete action has no effect."

                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();
                final Element parentElement;
                int deletionIndex;
                final List siblingElements;
                final int actualIndex;
                {
                    final String deletionIndexString = currentInstance.evaluateXPathAsString(pipelineContext, xformsControls.getCurrentSingleNode(),
                            "round(" + atAttribute + ")", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

                    // Don't think we will get NaN with XPath 2.0...
                    deletionIndex = "NaN".equals(deletionIndexString) ? collectionToBeUpdated.size() : Integer.parseInt(deletionIndexString) ;

                    // Adjust index to be in range
                    if (deletionIndex > collectionToBeUpdated.size())
                        deletionIndex = collectionToBeUpdated.size();

                    if (deletionIndex < 1)
                        deletionIndex = 1;

                    // Find actual deletion point
                    final Element indexElement = (Element) collectionToBeUpdated.get(deletionIndex - 1);

                    parentElement = indexElement.getParent();
                    siblingElements = parentElement.elements();
                    actualIndex = siblingElements.indexOf(indexElement);
                }

                // Find list of affected repeat ids
                final Map boundRepeatIds = new HashMap();
                final Map childrenRepeatIds = new HashMap();
                findAffectedRepeatIds(pipelineContext, parentElement, boundRepeatIds, childrenRepeatIds);

                // Then only perform the deletion (so that the list above is correct even when the last node is deleted)
                siblingElements.remove(actualIndex);

                // Rebuild ControlsState
                final Map previousRepeatIdToIndex = xformsControls.getCurrentControlsState().getRepeatIdToIndex();
                xformsControls.rebuildCurrentControlsState(pipelineContext);
                final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

                // Update repeat information for the ids found
                if (boundRepeatIds.size() != 0 || childrenRepeatIds.size() != 0) {
                    boolean updateInnerRepeats = false;
                    // Iterate over bound repeat ids
                    for (Iterator i = boundRepeatIds.keySet().iterator(); i.hasNext();) {
                        final String repeatId = (String) i.next();

                        if (collectionToBeUpdated.size() == 1) {
                            // Delete the last element of the collection: the index must be set to 0
                            currentControlsState.updateRepeatIndex(repeatId, 0);
                            updateInnerRepeats = true;
                        } else {
                            final int currentlySelected = ((Integer) previousRepeatIdToIndex.get(repeatId)).intValue();
                            if (currentlySelected == deletionIndex) {
                                if (deletionIndex == collectionToBeUpdated.size()) {

                                    // o "When the last remaining item in the collection is removed,
                                    // the index position becomes 0."

                                    // o "When the index was pointing to the deleted node, which was
                                    // the last item in the collection, the index will point to the new
                                    // last node of the collection and the index of inner repeats is
                                    // reinitialized."

                                    currentControlsState.updateRepeatIndex(repeatId, currentlySelected - 1);
                                    updateInnerRepeats = true;
                                } else {
                                    // o "When the index was pointing to the deleted node, which was
                                    // not the last item in the collection, the index position is not
                                    // changed and the index of inner repeats is re-initialized."

                                    updateInnerRepeats = true;
                                }
                            }
                        }
                    }

                    if (updateInnerRepeats) {
                        for (Iterator i = childrenRepeatIds.keySet().iterator(); i.hasNext();) {
                            final String repeatId = (String) i.next();
                            final int newIndex = (collectionToBeUpdated.size() == 1) ? 0 : 1;
                            currentControlsState.updateRepeatIndex(repeatId, newIndex);
                        }
                    }
                }

                // "4. If the delete is successful, the event xforms-delete is dispatched."
                containingDocument.dispatchEvent(pipelineContext, new org.orbeon.oxf.xforms.event.events.XFormsDeleteEvent(currentInstance, atAttribute));

                if (actionContext != null) {
                    // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
                    actionContext.setAll(true);
                } else {
                    // Binding context has to be reset as the controls have been updated
                    setBindingContext(pipelineContext, eventHandlerContainer.getId(), actionElement);
                    final XFormsModel model = xformsControls.getCurrentModel();
                    // Send events directly
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));
                    containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));
                }
            }

        } else if (XFormsActions.XFORMS_SETINDEX_ACTION.equals(actionEventName)) {
            // 9.3.7 The setindex Element

            final String repeatId = actionElement.attributeValue("repeat");
            final String indexXPath = actionElement.attributeValue("index");

            final XFormsInstance currentInstance = xformsControls.getCurrentInstance();
            final String indexString = currentInstance.evaluateXPathAsString(pipelineContext, xformsControls.getCurrentSingleNode(),
                    "string(number(" + indexXPath + "))", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

            executeSetindexAction(pipelineContext, containingDocument, repeatId, indexString);

        } else if (XFormsActions.XFORMS_SEND_ACTION.equals(actionEventName)) {
            // 10.1.10 The send Element

            // Find submission object
            final String submissionId = actionElement.attributeValue("submission");
            if (submissionId == null)
                throw new OXFException("Missing mandatory submission attribute on xforms:send element.");
            final Object submission = containingDocument.getObjectById(pipelineContext, submissionId);
            if (submission == null || !(submission instanceof XFormsModelSubmission))
                throw new OXFException("submission attribute on xforms:send element does not refer to existing xforms:submission element.");

            // Dispatch event to submission object
            containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitEvent((XFormsEventTarget) submission));

        } else if (XFormsActions.XFORMS_DISPATCH_ACTION.equals(actionEventName)) {
            // 10.1.2 The dispatch Element

            // Mandatory attributes
            final String newEventName = actionElement.attributeValue("name");
            if (newEventName == null)
                throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");
            final String newEventTargetId = actionElement.attributeValue("target");
            if (newEventTargetId == null)
                throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

            // Optional attributes
            final boolean newEventBubbles; {
                final String newEventBubblesString = actionElement.attributeValue("bubbles");
                // FIXME: "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                newEventBubbles = Boolean.getBoolean((newEventBubblesString == null) ? "true" : newEventBubblesString);
            }
            final boolean newEventCancelable; {
                // FIXME: "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                final String newEventCancelableString = actionElement.attributeValue("cancelable");
                newEventCancelable = Boolean.getBoolean((newEventCancelableString == null) ? "true" : newEventCancelableString);
            }

            final Object newTargetObject = containingDocument.getObjectById(pipelineContext, newEventTargetId);

            if (newTargetObject instanceof XFormsEventTarget) {
                // This can be anything
                containingDocument.dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(newEventName, (XFormsEventTarget) newTargetObject, newEventBubbles, newEventCancelable));
            } else {
                throw new OXFException("Invalid event target for id: " + newEventTargetId);
            }

        } else {
            throw new OXFException("Invalid action requested: " + actionEventName);
        }
    }

    public static void executeSetindexAction(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument, final String repeatId, final String indexString) {
        if ("NaN".equals(indexString)) {
            // "If the index evaluates to NaN the action has no effect."
        } else {

            final XFormsControls xformsControls = containingDocument.getXFormsControls();

            // Rebuild ControlsState if needed as we are going to make some changes
            if (xformsControls.getCurrentControlsState() == xformsControls.getInitialControlsState())
                xformsControls.rebuildCurrentControlsState(pipelineContext);

            final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

            final int index = Integer.parseInt(indexString);

            final Map repeatIdToRepeatControlInfo = currentControlsState.getRepeatIdToRepeatControlInfo();
            final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) repeatIdToRepeatControlInfo.get(repeatId);

            if (index <= 0) {
                // "If the selected index is 0 or less, an xforms-scroll-first event is dispatched
                // and the index is set to 1."
                containingDocument.dispatchEvent(pipelineContext, new XFormsScrollFirstEvent(repeatControlInfo));
                currentControlsState.updateRepeatIndex(repeatId, 1);
            } else {
                final List children = repeatControlInfo.getChildren();

                if (children != null && index > children.size()) {
                    // "If the selected index is greater than the index of the last repeat
                    // item, an xforms-scroll-last event is dispatched and the index is set to
                    // that of the last item."

                    containingDocument.dispatchEvent(pipelineContext, new XFormsScrollLastEvent(repeatControlInfo));
                    currentControlsState.updateRepeatIndex(repeatId, children.size());
                } else {
                    // Otherwise just set the index
                    currentControlsState.updateRepeatIndex(repeatId, index);
                }
            }

            // "The indexes for inner nested repeat collections are re-initialized to 1."
            final List nestedRepeatIds = currentControlsState.getNestedRepeatIds(repeatId);
            if (nestedRepeatIds != null) {
                for (Iterator i = nestedRepeatIds.iterator(); i.hasNext();) {
                    final String currentRepeatId = (String) i.next();
                    currentControlsState.updateRepeatIndex(currentRepeatId, 1);
                }
            }
        }

        // TODO: "The implementation data structures for tracking computational dependencies are
        // rebuilt or updated as a result of this action."
        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
            XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.applyComputedExpressionBinds(pipelineContext);
            //containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(currentModel, true));
        }
    }

    private void setBindingContext(final PipelineContext pipelineContext, String eventHandlerContainerId, Element actionElement) {

        final XFormsEventHandlerContainer eventHandlerContainer = (XFormsEventHandlerContainer) containingDocument.getObjectById(pipelineContext, eventHandlerContainerId);

        if (eventHandlerContainer instanceof XFormsControls.ControlInfo) {
            // The event handler is contained within a control. Bindings are relative to that control
            xformsControls.setBinding(pipelineContext, (XFormsControls.ControlInfo) eventHandlerContainer);
        } else {
            // The event handler is not contained within a control (e.g. model or submission)
            xformsControls.resetBindingContext();
        }
        xformsControls.pushBinding(pipelineContext, actionElement);
    }

    private void findAffectedRepeatIds(final PipelineContext pipelineContext, final Element parentElement, final Map setRepeatIds, final Map resetRepeatIds) {
        xformsControls.visitAllControlsHandleRepeat(pipelineContext, new XFormsControls.ControlElementVisitorListener() {
            private Element foundControlElement = null;
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                if (controlElement.getName().equals("repeat")) {
                    if (foundControlElement == null) {
                        // We are not yet inside a matching xforms:repeat
                        final List currentNodeset = xformsControls.getCurrentNodeset();
                        if (currentNodeset.size() > 0) {
                            final Element currentNode = (Element) xformsControls.getCurrentSingleNode();
                            final Element currentParent = currentNode.getParent();
                            if (currentParent == parentElement) {
                                // Found xforms:repeat affected by the change
                                setRepeatIds.put(controlElement.attributeValue("id"), "");
                                foundControlElement = controlElement;
                            }
                        }
                    } else {
                        // This xforms:repeat is inside a matching xforms:repeat
                        resetRepeatIds.put(controlElement.attributeValue("id"), "");
                    }
                }
                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                if (foundControlElement == controlElement)
                    foundControlElement = null;
                return true;
            }

            public void startRepeatIteration(int iteration) {
            }

            public void endRepeatIteration(int iteration) {
            }
        });
    }

    private static class ActionContext {
        public boolean rebuild;
        public boolean recalculate;
        public boolean revalidate;
        public boolean refresh;

        public void setAll(boolean value) {
            rebuild = value;
            recalculate = value;
            revalidate = value;
            refresh = value;
        }
    }
}
