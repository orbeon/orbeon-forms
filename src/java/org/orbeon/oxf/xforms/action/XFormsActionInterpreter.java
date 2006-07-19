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

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xforms.controls.RepeatControlInfo;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of all the XForms actions.
 *
 * TODO: Separate actions into different classes for more modularity.
 */
public class XFormsActionInterpreter {

    public static final String CANNOT_INSERT_READONLY_MESSAGE = "Cannot perform insertion into read-only instance.";
    public static final String CANNOT_DELETE_READONLY_MESSAGE = "Cannot perform deletion in read-only instance.";

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
        final String actionName = actionElement.getName();
        if (!XFormsActions.isActionName(actionNamespaceURI, actionName)) {
            throw new OXFException("Invalid action name: " + XMLUtils.buildExplodedQName(actionNamespaceURI, actionName));
        }

        // Set binding context
        final String eventHandlerContainerId = eventHandlerContainer.getId();
        setBindingContext(pipelineContext, containingDocument, eventHandlerContainerId, actionElement);

        // Handle conditional action (@if / @exf:if)
        final String conditionAttribute;
        {
            final String ifAttribute = actionElement.attributeValue("if");
            if (ifAttribute != null)
                conditionAttribute = ifAttribute;
            else
                conditionAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_IF_ATTRIBUTE_QNAME);
        }
        if (conditionAttribute != null) {
            // Don't evaluate the condition if the context has gone missing
            final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
            if (currentSingleNode == null) {
                if (XFormsServer.logger.isDebugEnabled())
                    XFormsServer.logger.debug("XForms - not executing conditional action (missing context): " + actionName);

                return;
            }

            final XFormsInstance currentInstance = xformsControls.getInstanceForNode(currentSingleNode);

            final List conditionResult = currentInstance.getEvaluator().evaluate(pipelineContext,
                currentSingleNode, "boolean(" + conditionAttribute + ")",
                Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

            if (!((Boolean) conditionResult.get(0)).booleanValue()) {
                // Don't execute action

                if (XFormsServer.logger.isDebugEnabled())
                    XFormsServer.logger.debug("XForms - not executing conditional action (condition evaluated to 'false'). Action name: " + actionName + ", condition: " + conditionAttribute);

                return;
            }
        }

        // We are executing the action
        if (XFormsServer.logger.isDebugEnabled())
            XFormsServer.logger.debug("XForms - executing action: " + actionName);

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            if (XFormsActions.XFORMS_SETVALUE_ACTION.equals(actionName)) {
                executeSetvalue(pipelineContext, actionElement, actionContext);
            } else if (XFormsActions.XFORMS_RESET_ACTION.equals(actionName)) {
                executeReset(pipelineContext, actionElement, actionContext);
            } else if (XFormsActions.XFORMS_ACTION_ACTION.equals(actionName)) {
                // 10.1.1 The action Element

                final ActionContext newActionContext = (actionContext == null) ? new ActionContext() : null;
                for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
                    final Element embeddedActionElement = (Element) i.next();
                    runAction(pipelineContext, targetId, eventHandlerContainer, embeddedActionElement, (newActionContext == null) ? actionContext : newActionContext );
                }
                if (newActionContext != null) {
                    // Binding context has to be reset as it may have been modified by sub-actions
                    setBindingContext(pipelineContext, containingDocument, eventHandlerContainer.getId(), actionElement);
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

            } else if (XFormsActions.XFORMS_REBUILD_ACTION.equals(actionName)) {
                executeRebuild(pipelineContext, actionElement, actionContext);
            } else if (XFormsActions.XFORMS_RECALCULATE_ACTION.equals(actionName)) {
                executeRecalculate(pipelineContext, actionElement, actionContext);
            } else if (XFormsActions.XFORMS_REVALIDATE_ACTION.equals(actionName)) {
                executeRevalidate(pipelineContext, actionElement, actionContext);
            } else if (XFormsActions.XFORMS_REFRESH_ACTION.equals(actionName)) {
                executeRefresh(pipelineContext, actionElement, actionContext);
            } else if (XFormsActions.XFORMS_TOGGLE_ACTION.equals(actionName)) {
                executeToggle(pipelineContext, actionElement);
            } else if (XFormsActions.XFORMS_INSERT_ACTION.equals(actionName)) {
                executeInsert(pipelineContext, actionElement, actionContext, eventHandlerContainer);
            } else if (XFormsActions.XFORMS_DELETE_ACTION.equals(actionName)) {
                executeDelete(pipelineContext, actionElement, actionContext, eventHandlerContainer);
            } else if (XFormsActions.XFORMS_SETINDEX_ACTION.equals(actionName)) {
                executeSetindex(pipelineContext, actionElement);
            } else if (XFormsActions.XFORMS_SEND_ACTION.equals(actionName)) {
                executeSend(pipelineContext, actionElement);
            } else if (XFormsActions.XFORMS_DISPATCH_ACTION.equals(actionName)) {
                executeDispatch(pipelineContext, actionElement);
            } else if (XFormsActions.XFORMS_MESSAGE_ACTION.equals(actionName)) {
                executeMessage(pipelineContext, actionElement);
            } else if (XFormsActions.XFORMS_SETFOCUS_ACTION.equals(actionName)) {
                executeSetfocus(pipelineContext, actionElement);
            } else if (XFormsActions.XFORMS_LOAD_ACTION.equals(actionName)) {
                executeLoad(pipelineContext, actionElement);
            } else {
                throw new OXFException("Invalid action requested: " + actionName);
            }
        } else {
            if (XFormsActions.XXFORMS_SCRIPT_ACTION.equals(actionName)) {
                executeScript(pipelineContext, targetId, eventHandlerContainerId, actionElement);
            } else {
                throw new OXFException("Invalid action requested: " + actionName);
            }
        }
    }

    private void executeScript(PipelineContext pipelineContext, String targetId, String eventHandlerContainerId, Element actionElement) {
        containingDocument.addScriptToRun(actionElement.attributeValue("id"), targetId, eventHandlerContainerId);
    }

    private void executeLoad(PipelineContext pipelineContext, Element actionElement) {
        // 10.1.8 The load Element

        final String ref = actionElement.attributeValue("ref");
        final String resource = actionElement.attributeValue("resource");
        final String showAttribute;
        {
            final String rawShowAttribute = actionElement.attributeValue("show");
            showAttribute = (rawShowAttribute == null) ? "replace" : rawShowAttribute;
            if (!("replace".equals(showAttribute) || "new".equals(showAttribute)))
                throw new OXFException("Invalid value for 'show' attribute on xforms:load element: " + showAttribute);
        }
        final boolean doReplace = "replace".equals(showAttribute);
        final String target = actionElement.attributeValue(XFormsConstants.XXFORMS_TARGET_QNAME);
        final String urlType = actionElement.attributeValue(new QName("url-type", new Namespace("f", XMLConstants.OPS_FORMATTING_URI)));

        // "If both are present, the action has no effect."
        if (ref != null && resource != null)
            return;

        if (ref != null) {
            // Use single-node binding
            final NodeInfo currentNode = xformsControls.getCurrentSingleNode();
            if (currentNode != null) {
                final String value = XFormsInstance.getValueForNode(currentNode);
                resolveLoadValue(containingDocument, pipelineContext, actionElement, doReplace, value, target, urlType);
            } else {
                // The action is a NOP if it's not bound to a node
                return;
            }
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else if (resource != null) {
            // Use linking attribute
            resolveLoadValue(containingDocument, pipelineContext, actionElement, doReplace, resource, target, urlType);
            // NOTE: We are supposed to throw an xforms-link-error in case of failure. Can we do it?
        } else {
            // "Either the single node binding attributes, pointing to a URI in the instance
            // data, or the linking attributes are required."
            throw new OXFException("Missing 'resource' or 'ref' attribute on xforms:load element.");
        }
    }

    private void executeSetfocus(PipelineContext pipelineContext, Element actionElement) {
        // 10.1.7 The setfocus Element

        final String controlId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("control"));
        if (controlId == null)
            throw new OXFException("Missing mandatory 'control' attribute on xforms:control element.");
        final String effectiveControlId = xformsControls.getCurrentControlsState().findEffectiveControlId(controlId);
        if (effectiveControlId == null)
            throw new OXFException("Could not find actual control on xforms:setfocus element for control: " + controlId);

        final Object controlObject = containingDocument.getObjectById(pipelineContext, effectiveControlId);

        if (!(controlObject instanceof ControlInfo))
            throw new OXFException("xforms:setfocus attribute 'control' must refer to a control: " + controlId);

        containingDocument.dispatchEvent(pipelineContext, new XFormsFocusEvent((XFormsEventTarget) controlObject));
    }

    private void executeMessage(PipelineContext pipelineContext, Element actionElement) {
        // 10.1.12 The message Element

        final String level;
        {
            final String levelAttribute = actionElement.attributeValue("level");;
            if (levelAttribute == null)
                throw new OXFException("xforms:message element is missing mandatory 'level' attribute.");
            final QName levelQName = Dom4jUtils.extractAttributeValueQName(actionElement, "level");
            if (levelQName.getNamespacePrefix().equals("")) {
                if (!("ephemeral".equals(levelAttribute) || "modeless".equals(levelAttribute) || "modal".equals(levelAttribute))) {
                    throw new OXFException("xforms:message element's 'level' attribute must have value: 'ephemeral'|'modeless'|'modal'|QName-but-not-NCName.");
                }
                level = levelAttribute;
            } else {
                level = "{" + levelQName.getNamespaceURI() + "}" + levelQName.getName();
            }
        }

        final String src = actionElement.attributeValue("src");
        final String ref = actionElement.attributeValue("ref");

        String message = null;

        // Try to get message from single-node binding if any
        if (ref != null) {
            final NodeInfo currentNode = xformsControls.getCurrentSingleNode();
            if (currentNode != null)
                message = XFormsInstance.getValueForNode(currentNode);
        }

        // Try to get message from linking attribute
        boolean linkException = false;
        if (message == null && src != null) {
            try {
                message = XFormsUtils.retrieveSrcValue(src);
            } catch (IOException e) {
                containingDocument.dispatchEvent(pipelineContext, new XFormsLinkErrorEvent(xformsControls.getCurrentModel(), src, null, e));
                linkException = true;
            }
        }

        if (!linkException) {
            // Try to get inline message
            if (message == null) {
                message = actionElement.getStringValue();
            }

            if (message != null) {
                // Store message for sending to client
                containingDocument.addMessageToRun(message, level);

                // NOTE: In the future, we *may* want to save and resume the event state before and
                // after displaying a message, in order to possibly provide a behavior which is more
                // consistent with what users may expect regarding actions executed after
                // xforms:message.
            }
        }
    }

    private void executeDispatch(PipelineContext pipelineContext, Element actionElement) {
        // 10.1.2 The dispatch Element

        // Mandatory attributes
        final String newEventName = actionElement.attributeValue("name");
        if (newEventName == null)
            throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");
        final String newEventTargetId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("target"));
        if (newEventTargetId == null)
            throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

        // Optional attributes
        final boolean newEventBubbles;
        {
                        final String newEventBubblesString = actionElement.attributeValue("bubbles");
                        // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                        // The event factory makes sure that those values are ignored for predefined events
                        newEventBubbles = Boolean.valueOf((newEventBubblesString == null) ? "true" : newEventBubblesString).booleanValue();
                    }
        final boolean newEventCancelable;
        {
                        // "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                        // The event factory makes sure that those values are ignored for predefined events
                        final String newEventCancelableString = actionElement.attributeValue("cancelable");
                        newEventCancelable = Boolean.valueOf((newEventCancelableString == null) ? "true" : newEventCancelableString).booleanValue();
                    }

        // Find actual target
        final Object xformsEventTarget;
        {
            final Object tempXFormsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(pipelineContext, newEventTargetId);
            if (tempXFormsEventTarget != null) {
                // Object with this id exists
                xformsEventTarget = tempXFormsEventTarget;
            } else {
                // Otherwise, try effective id
                final String newEventTargetEffectiveId = xformsControls.getCurrentControlsState().findEffectiveControlId(newEventTargetId);
                xformsEventTarget = (XFormsEventTarget) containingDocument.getObjectById(pipelineContext, newEventTargetEffectiveId);
            }
        }

        if (xformsEventTarget == null)
            throw new OXFException("Could not find actual event target on xforms:dispatch element for id: " + newEventTargetId);

        if (xformsEventTarget instanceof XFormsEventTarget) {
            // This can be anything
            containingDocument.dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(newEventName, (XFormsEventTarget) xformsEventTarget, newEventBubbles, newEventCancelable));
        } else {
            throw new OXFException("Invalid event target for id: " + newEventTargetId);
        }
    }

    private void executeSend(PipelineContext pipelineContext, Element actionElement) {
        // 10.1.10 The send Element

        // Find submission object
        final String submissionId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("submission"));
        if (submissionId == null)
            throw new OXFException("Missing mandatory submission attribute on xforms:send element.");
        final Object submission = containingDocument.getObjectById(pipelineContext, submissionId);
        if (submission == null || !(submission instanceof XFormsModelSubmission))
            throw new OXFException("Submission attribute on xforms:send element does not refer to existing xforms:submission element: " + submissionId);

        // Dispatch event to submission object
        containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitEvent((XFormsEventTarget) submission));
    }

    private void executeSetindex(PipelineContext pipelineContext, Element actionElement) {
        // 9.3.7 The setindex Element

        final String repeatId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("repeat"));
        final String indexXPath = actionElement.attributeValue("index");

        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode == null)
            return;

        final XFormsInstance currentInstance = xformsControls.getInstanceForNode(currentSingleNode);
        final String indexString = currentInstance.getEvaluator().evaluateAsString(pipelineContext,
                xformsControls.getCurrentNodeset(), xformsControls.getCurrentPosition(),
                "number(" + indexXPath + ")", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

        executeSetindexAction(pipelineContext, containingDocument, repeatId, indexString);
    }

    private void executeDelete(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext, XFormsEventHandlerContainer eventHandlerContainer) {
        // 9.3.6 The delete Element

        final String atAttribute = actionElement.attributeValue("at");
        final String contextAttribute = actionElement.attributeValue("context");

        final List collectionToUpdate = xformsControls.getCurrentNodeset();
        final boolean isEmptyNodesetBinding = collectionToUpdate == null || collectionToUpdate.size() == 0;

        // "The delete action is terminated with no effect if [...] the context attribute is not given and the Node
        // Set Binding node-set is empty."
        if (contextAttribute == null && isEmptyNodesetBinding)
            return;

        // Now that we have evaluated the nodeset, restore context to in-scope evaluation context
        xformsControls.popBinding();

        // Handle @context attribute
        if (contextAttribute != null) {
            xformsControls.pushBinding(pipelineContext, null, null, contextAttribute, null, null, null, Dom4jUtils.getNamespaceContextNoDefault(actionElement));
        }
        // We are now in the insert context

        // "The delete action is terminated with no effect if the insert context is the empty node-set [...]."
        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode == null)
            return;

        {
            final XFormsInstance currentInstance = xformsControls.getInstanceForNode(currentSingleNode);
            final Node nodeToRemove;
            final List parentContent;
            final int actualIndexInParentContentCollection;
            {
                int deleteIndex;
                {
                    if (isEmptyNodesetBinding) {
                        // "If the Node Set Binding node-set empty, then this attribute is ignored"
                        deleteIndex = 0;
                    } else if (atAttribute == null) {
                        // "If the attribute is not given, then the default is the size of the Node Set Binding node-set"
                        deleteIndex = collectionToUpdate.size();
                    } else {
                        // "1. The evaluation context node is the first node in document order from the Node Set Binding
                        // node-set, the context size is the size of the Node Set Binding node-set, and the context
                        // position is 1."

                        // "2. The return value is processed according to the rules of the XPath function round()"
                        final String insertionIndexString = currentInstance.getEvaluator().evaluateAsString(pipelineContext,
                            collectionToUpdate, 1,
                            "round(" + atAttribute + ")", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

                        // "3. If the result is in the range 1 to the Node Set Binding node-set size, then the insert
                        // location is equal to the result. If the result is non-positive, then the insert location is
                        // 1. Otherwise, the result is NaN or exceeds the Node Set Binding node-set size, so the insert
                        // location is the Node Set Binding node-set size."

                        // Don't think we will get NaN with XPath 2.0...
                        deleteIndex = "NaN".equals(insertionIndexString) ? collectionToUpdate.size() : Integer.parseInt(insertionIndexString) ;

                        // Adjust index to be in range
                        if (deleteIndex > collectionToUpdate.size())
                            deleteIndex = collectionToUpdate.size();

                        if (deleteIndex < 1)
                            deleteIndex = 1;
                    }
                }

                if (isEmptyNodesetBinding) {
                    // TODO: not specified by spec
                    return;
                } else {
                    // Find actual deletion point
                    nodeToRemove = XFormsUtils.getNodeFromNodeInfo((NodeInfo) collectionToUpdate.get(deleteIndex - 1), CANNOT_DELETE_READONLY_MESSAGE);

                    final Element parentElement = nodeToRemove.getParent();
                    if (parentElement != null) {
                        // Regular case
                        if (nodeToRemove instanceof Attribute) {
                            parentContent = parentElement.attributes();
                        } else {
                            parentContent = parentElement.content();
                        }
                        actualIndexInParentContentCollection = parentContent.indexOf(nodeToRemove);
                    } else if (nodeToRemove == nodeToRemove.getDocument().getRootElement()) {
                        // Case of root element where parent is Document
                        parentContent = nodeToRemove.getDocument().content();
                        actualIndexInParentContentCollection = parentContent.indexOf(nodeToRemove);
                    } else if (nodeToRemove instanceof Document) {
                        // Case where node to remove is Document

                        // "except if the node is the root document element of an instance then the delete action
                        // is terminated with no effect."
                        return;
                    } else {
                        throw new OXFException("Node to delete doesn't have a parent.");
                    }
                }
            }

            // Get current repeat indexes
            final Map previousRepeatIdToIndex = xformsControls.getCurrentControlsState().getRepeatIdToIndex();

            // Find updates to repeat indexes
            final Map repeatIndexUpdates = new HashMap();
            final Map nestedRepeatIndexUpdates = new HashMap();
            XFormsIndexUtils.adjustIndexesForDelete(pipelineContext, xformsControls, previousRepeatIdToIndex,
                    repeatIndexUpdates, nestedRepeatIndexUpdates, nodeToRemove);

            // Prepare switches
            XFormsSwitchUtils.prepareSwitches(pipelineContext, xformsControls);

            // Then only perform the deletion
            // "The node at the delete location in the Node Set Binding node-set is deleted"
            parentContent.remove(actualIndexInParentContentCollection);

            // Rebuild ControlsState
            xformsControls.rebuildCurrentControlsState(pipelineContext);

            // Update switches
            XFormsSwitchUtils.updateSwitches(pipelineContext, xformsControls);

            // Update affected repeat index information
            if (repeatIndexUpdates.size() > 0) {
                for (Iterator i = repeatIndexUpdates.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    xformsControls.getCurrentControlsState().updateRepeatIndex((String) currentEntry.getKey(), ((Integer) currentEntry.getValue()).intValue());
                }
            }

            // Adjust indexes that could have gone out of bounds
            adjustRepeatIndexes(pipelineContext, xformsControls, nestedRepeatIndexUpdates);

            // "4. If the delete is successful, the event xforms-delete is dispatched."
            containingDocument.dispatchEvent(pipelineContext, new XFormsDeleteEvent(currentInstance, atAttribute));

            if (actionContext != null) {
                // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
                actionContext.setAll(true);
            } else {
                // Binding context has to be reset as the controls have been updated
                setBindingContext(pipelineContext, containingDocument, eventHandlerContainer.getId(), actionElement);
                final XFormsModel model = xformsControls.getCurrentModel();
                // Send events directly
                containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));
            }
        }
    }

    private void executeInsert(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext, XFormsEventHandlerContainer eventHandlerContainer) {
        // 9.3.5 The insert Element
        final String atAttribute = actionElement.attributeValue("at");
        final String positionAttribute = actionElement.attributeValue("position");
        final String originAttribute = actionElement.attributeValue("origin");
        final String contextAttribute = actionElement.attributeValue("context");

        final List collectionToBeUpdated = xformsControls.getCurrentNodeset();
        final boolean isEmptyNodesetBinding = collectionToBeUpdated == null || collectionToBeUpdated.size() == 0;

        // "The insert action is terminated with no effect if [...] the context attribute is not given and the Node
        // Set Binding node-set is empty."
        if (contextAttribute == null && isEmptyNodesetBinding)
            return;

        // Now that we have evaluated the nodeset, restore context to in-scope evaluation context
        xformsControls.popBinding();

        // Handle @context attribute
        if (contextAttribute != null) {
            xformsControls.pushBinding(pipelineContext, null, null, contextAttribute, null, null, null, Dom4jUtils.getNamespaceContextNoDefault(actionElement));
        }
        // We are now in the insert context

        // "The insert action is terminated with no effect if the insert context is the empty node-set [...]."
        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode == null)
            return;

        {
            final Node sourceNode;
            final Node clonedNode;
            {
                final Node clonedNodeTemp;
                if (originAttribute == null) {
                    // "If the attribute is not given and the Node Set Binding node-set is empty, then the insert
                    // action is terminated with no effect."
                    if (isEmptyNodesetBinding)
                        return;

                    // "if this attribute is not given, then the last node of the Node Set Binding node-set is
                    // cloned"
                    sourceNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) collectionToBeUpdated.get(collectionToBeUpdated.size() - 1), CANNOT_INSERT_READONLY_MESSAGE);
                    clonedNodeTemp = (sourceNode instanceof Element) ? ((Node) ((Element) sourceNode).createCopy()) : (Node) sourceNode.clone();
                } else {
                    // "If the attribute is given, it is evaluated in the insert context using the first node rule.
                    // If the result is a node, then it is cloned, and otherwise the insert action is terminated
                    // with no effect."
                    xformsControls.pushBinding(pipelineContext, null, null, originAttribute, null, null, null, Dom4jUtils.getNamespaceContextNoDefault(actionElement));
                    final Object originObject = xformsControls.getCurrentSingleNode();
                    if (!(originObject instanceof NodeInfo))
                        return;

                    sourceNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) originObject, CANNOT_INSERT_READONLY_MESSAGE);
                    clonedNodeTemp = (sourceNode instanceof Element) ? ((Node) ((Element) sourceNode).createCopy()) : (Node) sourceNode.clone();
                    xformsControls.popBinding();
                }

                // We can never really insert a document into anything, but we assume that this means the root element
                if (clonedNodeTemp instanceof Document)
                    clonedNode = clonedNodeTemp.getDocument().getRootElement().detach();
                else
                    clonedNode = clonedNodeTemp;

                if (clonedNode instanceof Element)
                    XFormsUtils.setInitialDecoration((Element) clonedNode);
                else if (clonedNode instanceof Attribute)
                    XFormsUtils.setInitialDecoration((Attribute) clonedNode);
                else if (clonedNode instanceof Document)
                    XFormsUtils.setInitialDecoration(clonedNode.getDocument().getRootElement());
                // TODO: we incorrectly don't handle instance data on text nodes and other nodes
            }

            // "Finally, this newly created node is inserted into the instance data at the location
            // specified by attributes position and at."

            final XFormsInstance currentInstance = xformsControls.getInstanceForNode(currentSingleNode);
            int insertionIndex;
            {
                if (isEmptyNodesetBinding) {
                    // "If the Node Set Binding node-set empty, then this attribute is ignored"
                    insertionIndex = 0;
                } else if (atAttribute == null) {
                    // "If the attribute is not given, then the default is the size of the Node Set Binding node-set"
                    insertionIndex = collectionToBeUpdated.size();
                } else {
                    // "1. The evaluation context node is the first node in document order from the Node Set Binding
                    // node-set, the context size is the size of the Node Set Binding node-set, and the context
                    // position is 1."

                    // "2. The return value is processed according to the rules of the XPath function round()"
                    final String insertionIndexString = currentInstance.getEvaluator().evaluateAsString(pipelineContext,
                        collectionToBeUpdated, 1,
                        "round(" + atAttribute + ")", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

                    // "3. If the result is in the range 1 to the Node Set Binding node-set size, then the insert
                    // location is equal to the result. If the result is non-positive, then the insert location is
                    // 1. Otherwise, the result is NaN or exceeds the Node Set Binding node-set size, so the insert
                    // location is the Node Set Binding node-set size."

                    // Don't think we will get NaN with XPath 2.0...
                    insertionIndex = "NaN".equals(insertionIndexString) ? collectionToBeUpdated.size() : Integer.parseInt(insertionIndexString) ;

                    // Adjust index to be in range
                    if (insertionIndex > collectionToBeUpdated.size())
                        insertionIndex = collectionToBeUpdated.size();

                    if (insertionIndex < 1)
                        insertionIndex = 1;
                }
            }

            // Prepare switches
            XFormsSwitchUtils.prepareSwitches(pipelineContext, xformsControls, sourceNode, clonedNode);

            // Find actual insertion point and insert
            if (isEmptyNodesetBinding) {
                // "1. If the Node Set Binding node-set is empty, then the target location is before the first
                // child or attribute of the insert context node, based on the node type of the cloned node."

                final NodeInfo insertContextNode = xformsControls.getCurrentSingleNode();
                doInsert(XFormsUtils.getNodeFromNodeInfo(insertContextNode, CANNOT_INSERT_READONLY_MESSAGE), clonedNode);
            } else {
                final Node insertLocationNode = XFormsUtils.getNodeFromNodeInfo((NodeInfo) collectionToBeUpdated.get(insertionIndex - 1), CANNOT_INSERT_READONLY_MESSAGE);
                if (insertLocationNode.getNodeType() != clonedNode.getNodeType()) {
                    // "2. If the node type of the cloned node does not match the node type of the insert location
                    // node, then the target location is before the first child or attribute of the insert location
                    // node, based on the node type of the cloned node."

                    doInsert(insertLocationNode, clonedNode);
                } else {

                    if (insertLocationNode.getDocument().getRootElement() == insertLocationNode) {
                        // "3. If the Node Set Binding node-set and insert location indicate the root element of an
                        // instance, then that instance root element location is the target location."

                        doInsert(insertLocationNode.getDocument(), clonedNode);
                    } else {
                        // "4. Otherwise, the target location is immediately before or after the insert location
                        // node, based on the position attribute setting or its default."

                        final Element parentNode = insertLocationNode.getParent();
                        final List siblingElements = parentNode.content();
                        final int actualIndex = siblingElements.indexOf(insertLocationNode);

                        // Prepare insertion of new element
                        final int actualInsertionIndex;
                        if (positionAttribute == null || "after".equals(positionAttribute)) { // "after" is the default
                            actualInsertionIndex = actualIndex + 1;
                        } else if ("before".equals(positionAttribute)) {
                            actualInsertionIndex = actualIndex;
                        } else {
                            throw new OXFException("Invalid 'position' attribute: " + positionAttribute + ". Must be either 'before' or 'after'.");
                        }

                        // "3. The index for any repeating sequence that is bound to the homogeneous
                        // collection where the node was added is updated to point to the newly added node.
                        // The indexes for inner nested repeat collections are re-initialized to
                        // startindex."

                        // Perform the insertion
                        siblingElements.add(actualInsertionIndex, clonedNode);
                    }
                }
            }

            // Rebuild ControlsState
            xformsControls.rebuildCurrentControlsState(pipelineContext);
            final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

            // Update repeat indexes
            XFormsIndexUtils.ajustIndexesAfterInsert(pipelineContext, xformsControls, currentControlsState, clonedNode);

            // Update switches
            XFormsSwitchUtils.updateSwitches(pipelineContext, xformsControls);

            // "4. If the insert is successful, the event xforms-insert is dispatched."
            containingDocument.dispatchEvent(pipelineContext, new XFormsInsertEvent(currentInstance, atAttribute));

            if (actionContext != null) {
                // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
                actionContext.setAll(true);
            } else {
                // Binding context has to be reset as the controls have been updated
                setBindingContext(pipelineContext, containingDocument, eventHandlerContainer.getId(), actionElement);
                final XFormsModel model = xformsControls.getCurrentModel();
                // Send events directly
                containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));
                containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));
            }
        }
    }

    private void executeToggle(PipelineContext pipelineContext, Element actionElement) {
        // 9.2.3 The toggle Element

        final String caseId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("case"));
        final String effectiveCaseId = xformsControls.findEffectiveCaseId(caseId);

        // Update xforms:switch info and dispatch events
        xformsControls.updateSwitchInfo(pipelineContext, effectiveCaseId);
    }

    private void executeRefresh(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext) {
        // 10.1.6 The refresh Element

        final String modelId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("model"));
        final XFormsModel model = (modelId != null) ? containingDocument.getModel(modelId) : xformsControls.getCurrentModel();
        containingDocument.dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (actionContext != null)
            actionContext.refresh = false;
    }

    private void executeRevalidate(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext) {
        // 10.1.5 The revalidate Element

        final String modelId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("model"));
        final XFormsModel model = (modelId != null) ? containingDocument.getModel(modelId) : xformsControls.getCurrentModel();
        containingDocument.dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (actionContext != null)
            actionContext.revalidate = false;
    }

    private void executeRecalculate(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext) {
        // 10.1.4 The recalculate Element

        final String modelId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("model"));
        final XFormsModel model = (modelId != null) ? containingDocument.getModel(modelId) : xformsControls.getCurrentModel();
        containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (actionContext != null)
            actionContext.recalculate = false;
    }

    private void executeRebuild(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext) {
        // 10.1.3 The rebuild Element

        final String modelId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("model"));
        final XFormsModel model = (modelId != null) ? containingDocument.getModel(modelId) : xformsControls.getCurrentModel();
        containingDocument.dispatchEvent(pipelineContext, new XFormsRebuildEvent(model));

        // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always
        // have an immediate effect, and clear the corresponding flag."
        if (actionContext != null)
            actionContext.rebuild = false;
    }

    private void executeReset(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext) {
        // 10.1.11 The reset Element

        final String modelId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("model"));

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
    }

    private void executeSetvalue(PipelineContext pipelineContext, Element actionElement, ActionContext actionContext) {
        // 10.1.9 The setvalue Element
        // xforms:setvalue

        final String value = actionElement.attributeValue("value");
        final String content = actionElement.getStringValue();

        final String valueToSet;
        if (value != null) {
            // Value to set is computed with an XPath expression
            final Map namespaceContext = Dom4jUtils.getNamespaceContextNoDefault(actionElement);

            final List currentNodeset;
            {
                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();// TODO: we should not use this
                currentNodeset = (xformsControls.getCurrentNodeset() != null && xformsControls.getCurrentNodeset().size() > 0)
                        ? xformsControls.getCurrentNodeset()
                        : Collections.singletonList(currentInstance.getInstanceDocumentInfo());

                // NOTE: The above is actually not correct: the context should not become null or empty. This is
                // therefore just a workaround for a bug we hit:

                // o Do 2 setvalue in sequence
                // o The first one changes the context around the control containing the actions
                // o When the second one runs, context is empty, and setvalue either crashes or does nothing
                //
                // The correct solution is probably to NOT reevaluate the context of actions unless a rebuild is done.
                // This would require an update to the way we impelement the processing model.
            }

            final XFormsInstance currentInstance = xformsControls.getInstanceForNode((NodeInfo) currentNodeset.get(0));
            valueToSet = currentInstance.getEvaluator().evaluateAsString(pipelineContext,
                    currentNodeset, xformsControls.getCurrentPosition(),
                    value, namespaceContext, null, xformsControls.getFunctionLibrary(), null);
        } else {
            // Value to set is static content
            valueToSet = content;
        }

        // Set value on current node
        final NodeInfo currentNode = xformsControls.getCurrentSingleNode();
        if (currentNode != null) {
            // Node exists, we can try to set the value
            XFormsInstance.setValueForNodeInfo(pipelineContext, currentNode, valueToSet, null);

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
        } else {
            // Node doesn't exist, don't do anything
            // NOP
        }
    }

    private void doInsert(Node insertionNode, Node clonedNode) {
        if (insertionNode instanceof Element) {
            final Element insertContextElement = (Element) insertionNode;
            if (clonedNode instanceof Attribute) {
                final Attribute clonedAttribute = (Attribute) clonedNode;
                final Attribute existingAttribute = insertContextElement.attribute(clonedAttribute.getQName());
                if (existingAttribute != null)
                    insertContextElement.attributes().remove(existingAttribute);
                insertContextElement.attributes().add(0, clonedNode);
            } else if (!(clonedNode instanceof Document)) {
                insertContextElement.content().add(0, clonedNode);
            }
        } else if (insertionNode instanceof Document) {
            final Document insertContextDocument = (Document) insertionNode;

            if (!(clonedNode instanceof Element))
                return; // TODO: can we insert comments and PIs?

            insertContextDocument.setRootElement((Element) clonedNode);
        } else {
            throw new OXFException("Unsupported insertion node type: " + insertionNode.getClass().getName());
        }
    }

    /**
     * Adjust controls ids that could have gone out of bounds.
     *
     * What we do here is that we bring back the index within bounds. The spec does not cover this
     * scenario.
     */
    public static void adjustRepeatIndexes(PipelineContext pipelineContext, final XFormsControls xformsControls) {
        adjustRepeatIndexes(pipelineContext, xformsControls, null);
    }

    public static void adjustRepeatIndexes(PipelineContext pipelineContext, final XFormsControls xformsControls, final Map forceUpdate) {

        // Rebuild before iterating
        xformsControls.rebuildCurrentControlsState(pipelineContext);
        xformsControls.getCurrentControlsState().visitControlInfoFollowRepeats(pipelineContext, xformsControls, new XFormsControls.ControlInfoVisitorListener() {

            public void startVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    // Found an xforms:repeat
                    final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) controlInfo;
                    final String repeatId = repeatControlInfo.getOriginalId();

                    final List repeatNodeSet = xformsControls.getCurrentNodeset();

                    if (repeatNodeSet != null && repeatNodeSet.size() > 0) {
                        // Node-set is non-empty

                        final int adjustedNewIndex;
                        {
                            final int newIndex;
                            if (forceUpdate != null && forceUpdate.get(repeatId) != null) {
                                // Force update of index to start index
                                newIndex = repeatControlInfo.getStartIndex();

                                // NOTE: XForms 1.0 2nd edition actually says "To re-initialize
                                // a repeat means to change the index to 0 if it is empty,
                                // otherwise 1." However, for, xforms:insert, we are supposed to
                                // update to startindex. Here, for now, we decide to use
                                // startindex for consistency.

                            } else {
                                // Just use current index
                                newIndex = ((Integer) xformsControls.getCurrentControlsState().getRepeatIdToIndex().get(repeatId)).intValue();
                            }

                            // Adjust bounds if necessary
                            if (newIndex < 1)
                                adjustedNewIndex = 1;
                            else if (newIndex > repeatNodeSet.size())
                                adjustedNewIndex = repeatNodeSet.size();
                            else
                                adjustedNewIndex = newIndex;
                        }

                        // Set index
                        xformsControls.getCurrentControlsState().updateRepeatIndex(repeatId, adjustedNewIndex);

                    } else {
                        // Node-set is empty, make sure index is set to 0
                        xformsControls.getCurrentControlsState().updateRepeatIndex(repeatId, 0);
                    }
                }
            }

            public void endVisitControl(ControlInfo controlInfo) {
            }
        });
    }

    public static String resolveLoadValue(XFormsContainingDocument containingDocument, PipelineContext pipelineContext, Element currentElement, boolean doReplace, String value, String target, String urlType) {

        final boolean isPortletLoad = "portlet".equals(containingDocument.getContainerType());
        final String externalURL = XFormsUtils.resolveURL(containingDocument, pipelineContext, currentElement, (!isPortletLoad) ? doReplace : (doReplace && !"resource".equals(urlType)), value);
        containingDocument.addLoadToRun(externalURL, target, urlType, doReplace, isPortletLoad);
        return externalURL;
    }

    public static void executeSetindexAction(final PipelineContext pipelineContext, final XFormsContainingDocument containingDocument, final String repeatId, final String indexString) {
        if ("NaN".equals(indexString)) {
            // "If the index evaluates to NaN the action has no effect."
        } else {

            final XFormsControls xformsControls = containingDocument.getXFormsControls();
            final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

            final int index = Integer.parseInt(indexString);

            final Map repeatIdToRepeatControlInfo = currentControlsState.getRepeatIdToRepeatControlInfo();
            final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) repeatIdToRepeatControlInfo.get(repeatId);

            if (repeatControlInfo == null)
                throw new OXFException("Invalid repeat id: " + repeatId);

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

            // "The indexes for inner nested repeat collections are re-initialized to startindex."
            {
                // First step: set all children indexes to 0
                final List nestedRepeatIds = currentControlsState.getNestedRepeatIds(xformsControls, repeatId);
                final Map nestedRepeatIdsMap = new HashMap();
                if (nestedRepeatIds != null) {
                    for (Iterator i = nestedRepeatIds.iterator(); i.hasNext();) {
                        final String currentRepeatId = (String) i.next();
                        nestedRepeatIdsMap.put(currentRepeatId, "");
                        currentControlsState.updateRepeatIndex(currentRepeatId, 0);
                    }
                }

                // Adjust controls ids that could have gone out of bounds
                adjustRepeatIndexes(pipelineContext, xformsControls, nestedRepeatIdsMap);
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

    private static void setBindingContext(final PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String eventHandlerContainerId, Element actionElement) {

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        // Rebuild controls state before using setBinding()
//        xformsControls.rebuildCurrentControlsState(pipelineContext);// TODO: why do we need to do this here?

        // Get "fresh" event handler containier
        final XFormsEventHandlerContainer eventHandlerContainer = (XFormsEventHandlerContainer) containingDocument.getObjectById(pipelineContext, eventHandlerContainerId);

        if (eventHandlerContainer instanceof ControlInfo) {
            // The event handler is contained within a control. Bindings are relative to that control
            xformsControls.setBinding(pipelineContext, (ControlInfo) eventHandlerContainer);
        } else {
            // The event handler is not contained within a control (e.g. model or submission)
            xformsControls.resetBindingContext();
        }
        xformsControls.pushBinding(pipelineContext, actionElement);
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