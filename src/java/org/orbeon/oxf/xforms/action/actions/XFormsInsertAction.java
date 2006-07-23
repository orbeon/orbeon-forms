/**
 *  Copyright (C) 2006 Orbeon, Inc.
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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.events.XFormsInsertEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.List;

/**
 * 9.3.5 The insert Element
 */
public class XFormsInsertAction extends XFormsAction {

    public static final String CANNOT_INSERT_READONLY_MESSAGE = "Cannot perform insertion into read-only instance.";

    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

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
        actionInterpreter.pushContextAttributeIfNeeded(pipelineContext, actionElement);

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
                    final String insertionIndexString = containingDocument.getEvaluator().evaluateAsString(pipelineContext,
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

            // Identify the instance that actually changes
            final XFormsInstance modifiedInstance;

            // Find actual insertion point and insert
            if (isEmptyNodesetBinding) {
                // "1. If the Node Set Binding node-set is empty, then the target location is before the first
                // child or attribute of the insert context node, based on the node type of the cloned node."

                final NodeInfo insertContextNode = xformsControls.getCurrentSingleNode();
                modifiedInstance = containingDocument.getInstanceForNode(insertContextNode);
                doInsert(XFormsUtils.getNodeFromNodeInfo(insertContextNode, CANNOT_INSERT_READONLY_MESSAGE), clonedNode);
            } else {
                final NodeInfo insertLocationNodeInfo = (NodeInfo) collectionToBeUpdated.get(insertionIndex - 1);
                final Node insertLocationNode = XFormsUtils.getNodeFromNodeInfo(insertLocationNodeInfo, CANNOT_INSERT_READONLY_MESSAGE);
                modifiedInstance = containingDocument.getInstanceForNode(insertLocationNodeInfo);
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
            containingDocument.dispatchEvent(pipelineContext, new XFormsInsertEvent(modifiedInstance, atAttribute));

            // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
            modifiedInstance.getModel().getDeferredActionContext().setAllDeferredFlags(true);
            containingDocument.getXFormsControls().markDirty();
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
}
