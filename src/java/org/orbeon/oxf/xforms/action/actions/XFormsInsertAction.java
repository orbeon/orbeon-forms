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

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.events.XFormsInsertEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.dom4j.DocumentWrapper;

import java.util.List;
import java.util.Iterator;
import java.util.Collections;
import java.util.ArrayList;

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

        final String binding;
        {
            final String nodesetAttribute = actionElement.attributeValue("nodeset");
            final String bindAttribute = actionElement.attributeValue("bind");
            binding = (bindAttribute != null) ? bindAttribute : nodesetAttribute;
        }

        final XFormsControls.BindingContext curBindingContext = xformsControls.getCurrentBindingContext();

        // "2. The Node Set Binding node-set is determined."
        final List collectionToBeUpdated = curBindingContext.isNewBind() ? curBindingContext.getNodeset() : Collections.EMPTY_LIST;
        final boolean isEmptyNodesetBinding = collectionToBeUpdated == null || collectionToBeUpdated.size() == 0;

        // "1. The insert context is determined."

        // "The insert action is terminated with no effect if [...] a. The context attribute is not given and the Node
        // Set Binding node-set is the empty node-set."
        if (contextAttribute == null && isEmptyNodesetBinding)
            return;

        // Now that we have evaluated the nodeset, restore context to in-scope evaluation context
        xformsControls.popBinding();

        // Handle @context attribute
        actionInterpreter.pushContextAttributeIfNeeded(pipelineContext, actionElement);

        // We are now in the insert context
        final NodeInfo insertContextNodeInfo;
        {
            final List insertContextNodeset = xformsControls.getCurrentNodeset();

            // "If the result is an empty nodeset or not a nodeset, then the insert action is terminated with no effect. "
            if (insertContextNodeset == null || insertContextNodeset.size() == 0 || !(insertContextNodeset.get(0) instanceof NodeInfo))
                return;

            insertContextNodeInfo = xformsControls.getCurrentSingleNode();
        }

        // "The insert action is terminated with no effect if [...] b. The context attribute is given, the insert
        // context does not evaluate to an element node and the Node Set Binding node-set is the empty node-set."
        if (contextAttribute != null && insertContextNodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE && isEmptyNodesetBinding)
            return;

        final List originObjects;
        {
            // "3. The origin node-set is determined."
            // "5. Each node in the origin node-set is cloned in the order it appears in the origin node-set."
            final List sourceNodes;
            final List clonedNodes;
            {
                final List clonedNodesTemp;
                if (originAttribute == null) {
                    // There is no @origin attribute, use node from Node Set Binding node-set

                    // "If the origin attribute is not given and the Node Set Binding node-set is empty, then the origin
                    // node-set is the empty node-set. [...] The insert action is terminated with no effect if the
                    // origin node-set is the empty node-set."

                    if (isEmptyNodesetBinding)
                        return;

                    // "Otherwise, if the origin attribute is not given, then the origin node-set consists of the last
                    // node of the Node Set Binding node-set."
                    final Node singleSourceNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) collectionToBeUpdated.get(collectionToBeUpdated.size() - 1), CANNOT_INSERT_READONLY_MESSAGE);
                    final Node singleClonedNode = (singleSourceNode instanceof Element) ? ((Node) ((Element) singleSourceNode).createCopy()) : (Node) singleSourceNode.clone();

                    sourceNodes = Collections.singletonList(singleSourceNode);
                    clonedNodesTemp = Collections.singletonList(singleClonedNode);

                    originObjects = null;
                } else {
                    // There is an @origin attribute

                    // "If the origin attribute is given, the origin node-set is the result of the evaluation of the
                    // origin attribute in the insert context."

                    originObjects = XPathCache.evaluate(pipelineContext, insertContextNodeInfo,
                        originAttribute, Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, XFormsContainingDocument.getFunctionLibrary(), xformsControls,
                        null, (LocationData) actionElement.getData());
                    //XFormsUtils.resolveXMLBase(actionElement, ".").toString()

                    // "The insert action is terminated with no effect if the origin node-set is the empty node-set."
                    if (originObjects.size() == 0)
                        return;

                    // "Each node in the origin node-set is cloned in the order it appears in the origin node-set."

                    sourceNodes = new ArrayList(originObjects.size()); // set to max possible size
                    clonedNodesTemp = new ArrayList(originObjects.size());

                    for (Iterator i = originObjects.iterator(); i.hasNext();) {
                        final Object currentObject = i.next();

                        if (currentObject instanceof NodeInfo) {
                            // This is the regular case covered by XForms 1.1 / XPath 1.0

                            final Node sourceNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) currentObject, CANNOT_INSERT_READONLY_MESSAGE);
                            final Node clonedNode = (sourceNode instanceof Element) ? ((Node) ((Element) sourceNode).createCopy()) : (Node) sourceNode.clone();

                            sourceNodes.add(sourceNode);
                            clonedNodesTemp.add(clonedNode);

                        } else {
                            // This is an extension: support sequences containing other items

                            // Convert the result to a text node
//                            final String stringValue = ((Item) currentObject).getStringValue();
                            final String stringValue = currentObject.toString(); // we get String, Long, etc.
                            final Text textNode = Dom4jUtils.createText(stringValue);

                            sourceNodes.add(null); // there is no source node for this cloned node, it's a source item
                            clonedNodesTemp.add(textNode);
                        }
                    }
                }

                for (int i = 0; i < clonedNodesTemp.size(); i++) {
                    final Node clonedNodeTemp = (Node) clonedNodesTemp.get(i);

                    if (clonedNodeTemp instanceof Element)
                        InstanceData.remove(clonedNodeTemp);
                    else if (clonedNodeTemp instanceof Attribute)
                        InstanceData.remove(clonedNodeTemp);
                    else if (clonedNodeTemp instanceof Document) {
                        final Element clonedNodeTempRootElement = clonedNodeTemp.getDocument().getRootElement();
                        InstanceData.remove(clonedNodeTempRootElement);
                        // We can never really insert a document into anything, but we assume that this means the root element
                        clonedNodesTemp.set(i, clonedNodeTempRootElement.detach());
                    }
                }
                clonedNodes = clonedNodesTemp;
            }

            // "4. The insert location node is determined."
            int insertionIndex;
            {
                if (isEmptyNodesetBinding) {
                    // "If the Node Set Binding node-set empty, then this attribute is ignored"
                    insertionIndex = 0;
                } else if (atAttribute == null) {
                    // "If the attribute is not given, then the default is the size of the Node Set Binding node-set"
                    insertionIndex = collectionToBeUpdated.size();
                } else {
                    // "a. The evaluation context node is the first node in document order from the Node Set Binding
                    // node-set, the context size is the size of the Node Set Binding node-set, and the context
                    // position is 1."

                    // "b. The return value is processed according to the rules of the XPath function round()"
                    final String insertionIndexString = XPathCache.evaluateAsString(pipelineContext,
                        collectionToBeUpdated, 1,
                        "round(" + atAttribute + ")", Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, XFormsContainingDocument.getFunctionLibrary(), xformsControls, null,
                            (LocationData) actionElement.getData());

                    // "c. If the result is in the range 1 to the Node Set Binding node-set size, then the insert
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
            for (int i = 0; i < sourceNodes.size(); i++) {
                final Node sourceNode = (Node) sourceNodes.get(i);
                // 1) may be null when source is an item 2) may be null when source is from a read-only instance
                //  && InstanceData.getLocalInstanceData(sourceNode) != null
                // TODO: How could this come from a read-only instance since we have a Node? Remove this and the above comment when we have made sure this is fine.
                if (sourceNode != null) {
                    final Node clonedNode = (Node) clonedNodes.get(i);
                    XFormsSwitchUtils.prepareSwitches(xformsControls, sourceNode, clonedNode);
                }
            }

            // "6. The target location of each cloned node or nodes is determined"
            // "7. The cloned node or nodes are inserted in the order they were cloned at their target location
            // depending on their node type."

            // Identify the instance that actually changes
            final XFormsInstance modifiedInstance;
            // Find actual insertion point and insert
            final NodeInfo insertLocationNodeInfo;
            final List insertedNodes;
            if (isEmptyNodesetBinding) {

                // "If the Node Set Binding node-set is not specified or empty, the insert location node is the insert
                // context node."

                // "a. If the Node Set Binding node-set is not specified or empty, the target location depends on the
                // node type of the cloned node. If the cloned node is an attribute, then the target location is before
                // the first attribute of the insert location node. If the cloned node is not an attribute, then the
                // target location is before the first child of the insert location node."

                modifiedInstance = containingDocument.getInstanceForNode(insertContextNodeInfo);
                insertLocationNodeInfo = insertContextNodeInfo;
                final Node insertLocationNode = XFormsUtils.getNodeFromNodeInfo(insertContextNodeInfo, CANNOT_INSERT_READONLY_MESSAGE);
                insertedNodes = doInsert(insertLocationNode, clonedNodes);

                // Normalize text nodes if needed to respect XPath 1.0 constraint
                {
                    boolean hasTextNode = false;
                    for (int i = 0; i < clonedNodes.size(); i++) {
                        final Node clonedNode = (Node) clonedNodes.get(i);
                        hasTextNode |= clonedNode.getNodeType() == org.dom4j.Node.TEXT_NODE;
                    }
                    if (hasTextNode)
                        Dom4jUtils.normalizeTextNodes(insertLocationNode);
                }
            } else {
                insertLocationNodeInfo = (NodeInfo) collectionToBeUpdated.get(insertionIndex - 1);
                final Node insertLocationNode = XFormsUtils.getNodeFromNodeInfo(insertLocationNodeInfo, CANNOT_INSERT_READONLY_MESSAGE);
                modifiedInstance = containingDocument.getInstanceForNode(insertLocationNodeInfo);

//                if (insertLocationNode.getNodeType() != clonedNode.getNodeType()) {
//                    // "2. If the node type of the cloned node does not match the node type of the insert location
//                    // node, then the target location is before the first child or attribute of the insert location
//                    // node, based on the node type of the cloned node."
//
//                    doInsert(insertLocationNode, clonedNode);
//                } else {

                    if (insertLocationNode.getDocument().getRootElement() == insertLocationNode) {
                        
                        // "c. if insert location node is the root element of an instance, then that instance root element
                        // location is the target location. If there is more than one cloned node to insert, only the
                        // first node that does not cause a conflict is considered."

                        insertedNodes = doInsert(insertLocationNode.getDocument(), clonedNodes);

                        // NOTE: Don't need to normalize text nodes in this case, as no new text node is inserted
                    } else {
                        // "d. Otherwise, the target location is immediately before or after the insert location
                        // node, based on the position attribute setting or its default."

                        if (insertLocationNode.getNodeType() == org.dom4j.Node.ATTRIBUTE_NODE) {
                            // Special case for attributes

                            // NOTE: In XML, attributes are unordered. dom4j handles them as a list so has order, but
                            // the XForms spec shouldn't rely on attribute order. We could try to keep the order, but it
                            // is harder as we have to deal with removing duplicate attributes and find a reasonable
                            // insertion strategy.

                            insertedNodes = doInsert(insertLocationNode.getParent(), clonedNodes);

                        } else {
                            // Other node types
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
                                throw new OXFException("Invalid 'position' attribute: " + positionAttribute + ". Must be either 'before' or 'after' if present.");
                            }

                            // "7. The cloned node or nodes are inserted in the order they were cloned at their target
                            // location depending on their node type."

                            boolean hasTextNode = false;
                            for (int i = 0; i < clonedNodes.size(); i++) {
                                final Node clonedNode = (Node) clonedNodes.get(i);
                                hasTextNode |= clonedNode.getNodeType() == org.dom4j.Node.TEXT_NODE;
                                siblingElements.add(actualInsertionIndex + i, clonedNode);
                            }
                            insertedNodes = clonedNodes;

                            // Normalize text nodes if needed to respect XPath 1.0 constraint
                            if (hasTextNode)
                                Dom4jUtils.normalizeTextNodes(parentNode);
                        }
                    }
//                }
            }

            // Rebuild ControlsState
            xformsControls.rebuildCurrentControlsState(pipelineContext);
            final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

            // Update repeat indexes
            XFormsIndexUtils.adjustIndexesAfterInsert(pipelineContext, xformsControls, currentControlsState, clonedNodes);

            // Update switches
            XFormsSwitchUtils.updateSwitches(xformsControls);

            // "4. If the insert is successful, the event xforms-insert is dispatched."
            {
                final List insertedNodeInfos;
                if (insertedNodes == null || insertedNodes.size() == 0) {
                    insertedNodeInfos = null;
                } else {
                    final DocumentWrapper documentWrapper = (DocumentWrapper) modifiedInstance.getDocumentInfo();
                    insertedNodeInfos = new ArrayList(insertedNodes.size());
                    for (Iterator i = insertedNodes.iterator(); i.hasNext();)
                        insertedNodeInfos.add(documentWrapper.wrap(i.next()));
                }


                containingDocument.dispatchEvent(pipelineContext,
                        new XFormsInsertEvent(modifiedInstance, binding, insertedNodeInfos, originObjects, insertLocationNodeInfo, positionAttribute == null ? "after" : positionAttribute));
            }

            // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
            modifiedInstance.getModel(containingDocument).setAllDeferredFlags(true);
            containingDocument.getXFormsControls().markDirty();
        }
    }

    private List doInsert(Node insertionNode, List clonedNodes) {
        final List insertedNodes = new ArrayList(clonedNodes.size());
        if (insertionNode instanceof Element) {
            // Insert inside an element
            final Element insertContextElement = (Element) insertionNode;

//            int attributeIndex = 0;
            int otherNodeIndex = 0;
            for (int i = 0; i < clonedNodes.size(); i++) {
                final Node clonedNode = (Node) clonedNodes.get(i);

                if (clonedNode instanceof Attribute) {
                    // Add attribute to element
                    final Attribute clonedAttribute = (Attribute) clonedNode;
                    final Attribute existingAttribute = insertContextElement.attribute(clonedAttribute.getQName());
                    if (existingAttribute != null)
                        insertContextElement.remove(existingAttribute);
//                    // TODO: If we try to insert several attributes with the same name, we may get an OutOfBounds exception below. Must check and ajust.
//                    insertContextElement.attributes().add(attributeIndex++, clonedNode);

                    // NOTE: In XML, attributes are unordered. dom4j handles them as a list so has order, but the
                    // XForms spec shouldn't rely on attribute order. We could try to keep the order, but it is harder
                    // as we have to deal with removing duplicate attributes and find a reasonable insertion strategy.

                    insertContextElement.add(clonedAttribute);
                    insertedNodes.add(clonedAttribute);

                } else if (!(clonedNode instanceof Document)) {
                    // Add other node to element
                    insertContextElement.content().add(otherNodeIndex++, clonedNode);
                    insertedNodes.add(clonedNode);
                } else {
                    // "If a cloned node cannot be placed at the target location due to a node type conflict, then the
                    // insertion for that particular clone node is ignored."
                }
            }
            return insertedNodes;
        } else if (insertionNode instanceof Document) {
            final Document insertContextDocument = (Document) insertionNode;

            // "If there is more than one cloned node to insert, only the first node that does not cause a conflict is
            // considered."
            for (int i = 0; i < clonedNodes.size(); i++) {
                final Node clonedNode = (Node) clonedNodes.get(i);

                // Only an element can be inserted at the root of an instance
                if (clonedNode instanceof Element) {
                    insertContextDocument.setRootElement((Element) clonedNode);
                    insertedNodes.add(clonedNode);
                    return insertedNodes;
                }
            }

            // NOTE: The spec does not allow inserting comments and PIs at the root of an instance document at this
            // point.

            return insertedNodes;
        } else {
            throw new OXFException("Unsupported insertion node type: " + insertionNode.getClass().getName());
        }
    }
}
