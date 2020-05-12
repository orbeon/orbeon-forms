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

import org.orbeon.dom.*;
import org.orbeon.dom.saxon.DocumentWrapper;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.events.XFormsInsertEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsReplaceEvent;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.model.FlaggedDefaultsStrategy$;
import org.orbeon.oxf.xforms.model.InstanceDataOps;
import org.orbeon.oxf.xforms.model.XFormsInstance;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Axis;
import org.orbeon.saxon.om.AxisIterator;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.value.AtomicValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 9.3.5 The insert Element
 */
public class XFormsInsertAction extends XFormsAction {

    private static final String CANNOT_INSERT_READONLY_MESSAGE = "Cannot perform insertion into read-only instance.";

    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final IndentedLogger indentedLogger = actionInterpreter.indentedLogger();
        final XFormsContainingDocument containingDocument = actionInterpreter.containingDocument();
        final XFormsContextStack contextStack = actionInterpreter.actionXPathContext();

        final String atAttribute = actionElement.attributeValue("at");
        final String originAttribute = actionElement.attributeValue("origin");
        final String contextAttribute = actionElement.attributeValue(XFormsConstants.CONTEXT_QNAME());

        // Extension: allow position to be an AVT
        final String resolvedPositionAttribute = actionInterpreter.resolveAVT(actionElement, "position");

        // Extension: xxf:default="true" AVT requires that recalculate apply default values on the inserted nodes.
        final boolean setRequireDefaultValues = "true".equals(actionInterpreter.resolveAVT(actionElement, XFormsConstants.XXFORMS_DEFAULTS_QNAME()));

        // "2. The Node Set Binding node-set is determined."
        final List<Item> collectionToBeUpdated; {
            final BindingContext currentBindingContext = contextStack.getCurrentBindingContext();
            collectionToBeUpdated = currentBindingContext.newBind() ? currentBindingContext.nodeset() : java.util.Collections.emptyList();
        }
        final boolean isEmptyNodesetBinding = collectionToBeUpdated == null || collectionToBeUpdated.size() == 0;

        // "1. The insert context is determined."

        // "The insert action is terminated with no effect if [...] a. The context attribute is not given and the Node
        // Set Binding node-set is the empty node-set."
        if (contextAttribute == null && isEmptyNodesetBinding) {
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("xf:insert", "context is empty, terminating");
            return;
        }

        // Handle insert context (with @context attribute)
        final Item insertContextItem;
        if (hasOverriddenContext) {
            // "If the result is an empty nodeset or not a nodeset, then the insert action is terminated with no effect. "
            if (overriddenContext == null || !(overriddenContext instanceof NodeInfo)) {
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("xf:insert", "overridden context is an empty nodeset or not a nodeset, terminating");
                return;
            } else {
                insertContextItem = overriddenContext;
            }
        } else {
            insertContextItem = contextStack.getCurrentBindingContext().getSingleItem();
        }

        // "The insert action is terminated with no effect if [...] b. The context attribute is given, the insert
        // context does not evaluate to an element node and the Node Set Binding node-set is the empty node-set."
        // NOTE: In addition we support inserting into a context which is a document node
        if (contextAttribute != null && isEmptyNodesetBinding && !DataModel.isElement(insertContextItem) && !DataModel.isDocument(insertContextItem)) {
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("xf:insert", "insert context is not an element node and binding node-set is empty, terminating");
            return;
        }

        // "3. The origin node-set is determined."
        final List<Item> originObjects;
        {
            if (originAttribute == null) {
                originObjects = null;
            } else {
                // There is an @origin attribute

                // "If the origin attribute is given, the origin node-set is the result of the evaluation of the
                // origin attribute in the insert context."

                originObjects = actionInterpreter.evaluateKeepItemsJava(actionElement,
                        Collections.singletonList(insertContextItem), 1, originAttribute);

                // "The insert action is terminated with no effect if the origin node-set is the empty node-set."
                if (originObjects.size() == 0) {
                    if (indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("xf:insert", "origin node-set is empty, terminating");
                    return;
                }
            }
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
                final String insertionIndexString = actionInterpreter.evaluateAsString(
                        actionElement, collectionToBeUpdated, 1, "round(" + atAttribute + ")");

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

        final String normalizedPosition; {
            if (resolvedPositionAttribute == null) {
                // Default value
                normalizedPosition = "after";
            } else if ("after".equals(resolvedPositionAttribute) || "before".equals(resolvedPositionAttribute)) {
                // Specified value
                normalizedPosition = resolvedPositionAttribute;
            } else {
                // Invalid value
                if (indentedLogger.isInfoEnabled())
                    indentedLogger.logWarning("xf:insert", "invalid position attribute, defaulting to \"after\"", "value", resolvedPositionAttribute);

                normalizedPosition = "after";
            }
        }

        doInsert(
            containingDocument,
            indentedLogger,
            normalizedPosition,
            collectionToBeUpdated,
            (NodeInfo) insertContextItem,
            originObjects,
            insertionIndex,
            true,
            true,
            setRequireDefaultValues,
            true,
                true
        );
    }

    public static List<NodeInfo> doInsert(
        XFormsContainingDocument containingDocument,
        IndentedLogger indentedLogger,
        String positionAttribute,
        List collectionToBeUpdated,
        NodeInfo insertContextNodeInfo,
        List<Item> originItems,
        int insertionIndex,
        boolean doClone,
        boolean doDispatch,
        boolean requireDefaultValues,
        boolean searchForInstance,
        boolean removeInstanceDataFromClonedNodes
    ) {

        final boolean isEmptyNodesetBinding = collectionToBeUpdated == null || collectionToBeUpdated.size() == 0;

        final NodeInfo insertLocationNodeInfo;
        if (isEmptyNodesetBinding) {
            // Insert INTO a node

            // "If the Node Set Binding node-set is not specified or empty, the insert location node is the insert
            // context node."

            // "a. If the Node Set Binding node-set is not specified or empty, the target location depends on the
            // node type of the cloned node. If the cloned node is an attribute, then the target location is before
            // the first attribute of the insert location node. If the cloned node is not an attribute, then the
            // target location is before the first child of the insert location node."

            insertLocationNodeInfo = insertContextNodeInfo;
        } else {
            // Insert BEFORE or AFTER a node
            insertLocationNodeInfo = (NodeInfo) collectionToBeUpdated.get(insertionIndex - 1);
        }

        // Identify the instance that actually changes
        final XFormsInstance modifiedInstanceOrNull =
            (searchForInstance && containingDocument != null) ? containingDocument.getInstanceForNode(insertLocationNodeInfo) : null;

        // NOTE: The check on `hasAnyCalculationBind` is not optimal: we should check whether specifically there are any xxf:default which can touch this
        // instance, ideally.
        // NOTE: We do this test here so that we don't unnecessarily annotate nodes.
        final boolean applyDefaults =
            requireDefaultValues                                               &&
            modifiedInstanceOrNull != null                                     &&
            modifiedInstanceOrNull.model().staticModel().hasDefaultValueBind() &&
            containingDocument.getXPathDependencies().hasAnyCalculationBind(
                modifiedInstanceOrNull.model().staticModel(),
                modifiedInstanceOrNull.getPrefixedId()
            );

        // "3. The origin node-set is determined."
        // "5. Each node in the origin node-set is cloned in the order it appears in the origin node-set."
        final List<Node> sourceNodes;
        final List<Node> clonedNodes;
        {
            final List<Node> clonedNodesTemp;
            if (originItems == null) {
                // There are no explicitly specified origin objects, use node from Node Set Binding node-set

                // "If the origin attribute is not given and the Node Set Binding node-set is empty, then the origin
                // node-set is the empty node-set. [...] The insert action is terminated with no effect if the
                // origin node-set is the empty node-set."

                if (isEmptyNodesetBinding) {
                    if (indentedLogger != null && indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("xf:insert", "origin node-set from node-set binding is empty, terminating");
                    return Collections.emptyList();
                }

                // "Otherwise, if the origin attribute is not given, then the origin node-set consists of the last
                // node of the Node Set Binding node-set."
                final Node singleSourceNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) collectionToBeUpdated.get(collectionToBeUpdated.size() - 1));
                // TODO: check namespace handling might be incorrect. Should use copyElementCopyParentNamespaces() instead?
                final Node singleClonedNode = Dom4jUtils.createCopy(singleSourceNode);

                sourceNodes = Collections.singletonList(singleSourceNode);
                clonedNodesTemp = Collections.singletonList(singleClonedNode);
            } else {
                // There are explicitly specified origin objects

                // "The insert action is terminated with no effect if the origin node-set is the empty node-set."
                if (originItems.size() == 0) {
                    if (indentedLogger != null && indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("xf:insert", "origin node-set is empty, terminating");
                    return Collections.emptyList();
                }

                // "Each node in the origin node-set is cloned in the order it appears in the origin node-set."

                sourceNodes = new ArrayList<Node>(originItems.size()); // set to max possible size
                clonedNodesTemp = new ArrayList<Node>(originItems.size());

                for (final Object currentObject : originItems) {
                    if (currentObject instanceof NodeInfo) {
                        // This is the regular case covered by XForms 1.1 / XPath 1.0

                        // NOTE: Don't clone nodes if doClone == false
                        final Node sourceNode = XFormsUtils.getNodeFromNodeInfoConvert((NodeInfo) currentObject);
                        final Node clonedNode = doClone ? (sourceNode instanceof Element) ? ((Element) sourceNode).createCopy() : (Node) sourceNode.deepCopy() : sourceNode;

                        sourceNodes.add(sourceNode);
                        clonedNodesTemp.add(clonedNode);

                    } else if (currentObject instanceof AtomicValue){
                        // This is an extension: support sequences containing atomic values

                        // Convert the result to a text node
                        final String stringValue = ((Item) currentObject).getStringValue();
                        final Text textNode = Text.apply(stringValue);

                        sourceNodes.add(null); // there is no source node for this cloned node, it's a source item
                        clonedNodesTemp.add(textNode);
                    } else
                        throw new IllegalStateException();
                }
            }

            if (removeInstanceDataFromClonedNodes) {
                // Remove instance data from cloned nodes and perform Document node adjustment
                for (int i = 0; i < clonedNodesTemp.size(); i++) {
                    final Node clonedNodeTemp = clonedNodesTemp.get(i);

                    if (clonedNodeTemp instanceof Element) {
                        // Element node
                        if (applyDefaults)
                            InstanceDataOps.setRequireDefaultValueRecursively(clonedNodeTemp);
                        else
                            InstanceDataOps.removeRecursively(clonedNodeTemp);
                        clonedNodeTemp.detach();
                    } else if (clonedNodeTemp instanceof Attribute) {
                        // Attribute node
                        if (applyDefaults)
                            InstanceDataOps.setRequireDefaultValueRecursively(clonedNodeTemp);
                        else
                            InstanceDataOps.removeRecursively(clonedNodeTemp);
                        clonedNodeTemp.detach();
                    } else if (clonedNodeTemp instanceof Document) {
                        // Document node
                        final Element clonedNodeTempRootElement = clonedNodeTemp.getDocument().getRootElement();

                        if (clonedNodeTempRootElement == null) {
                            // Can be null in rare cases of documents without root element
                            clonedNodesTemp.set(i, null); // we support having a null node further below, so set this to null
                        } else {
                            if (applyDefaults)
                                InstanceDataOps.setRequireDefaultValueRecursively(clonedNodeTempRootElement);
                            else
                                InstanceDataOps.removeRecursively(clonedNodeTempRootElement);
                            // We can never really insert a document into anything at this point, but we assume that this means the root element
                            clonedNodesTemp.set(i, clonedNodeTempRootElement.detach());
                        }
                    } else {
                        // Other nodes
                        clonedNodeTemp.detach();
                    }
                }
            } else {
                // Just make sure the cloned nodes are detached
                for (int i = 0; i < clonedNodesTemp.size(); i++) {
                    clonedNodesTemp.get(i).detach();
                }
            }
            clonedNodes = clonedNodesTemp;
        }

        // "6. The target location of each cloned node or nodes is determined"
        // "7. The cloned node or nodes are inserted in the order they were cloned at their target location
        // depending on their node type."

        // Find actual insertion point and insert
        final int insertLocationIndexWithinParentBeforeUpdate;
        final List<Node> insertedNodes;
        final String beforeAfterInto;
        if (isEmptyNodesetBinding) {
            // Insert INTO a node

            insertLocationIndexWithinParentBeforeUpdate = findNodeIndex(insertLocationNodeInfo);

            final Node insertLocationNode = XFormsUtils.getNodeFromNodeInfo(insertContextNodeInfo, CANNOT_INSERT_READONLY_MESSAGE);
            insertedNodes = doInsert(insertLocationNode, clonedNodes, modifiedInstanceOrNull, doDispatch);
            beforeAfterInto = "into";

            // Normalize text nodes if needed to respect XPath 1.0 constraint
            {
                boolean hasTextNode = false;
                for (Node clonedNode: clonedNodes) {
                    hasTextNode |= clonedNode != null && (clonedNode instanceof Text);
                }
                if (hasTextNode)
                    Dom4jUtils.normalizeTextNodes(insertLocationNode);
            }
        } else {
            // Insert BEFORE or AFTER a node

            insertLocationIndexWithinParentBeforeUpdate = findNodeIndex(insertLocationNodeInfo);

            final Node insertLocationNode = XFormsUtils.getNodeFromNodeInfo(insertLocationNodeInfo, CANNOT_INSERT_READONLY_MESSAGE);
            final Document insertLocationNodeDocument = insertLocationNode.getDocument();
            if (insertLocationNodeDocument != null && insertLocationNodeDocument.getRootElement() == insertLocationNode) {

                // "c. if insert location node is the root element of an instance, then that instance root element
                // location is the target location. If there is more than one cloned node to insert, only the
                // first node that does not cause a conflict is considered."

                insertedNodes = doInsert(insertLocationNode.getDocument(), clonedNodes, modifiedInstanceOrNull, doDispatch);
                beforeAfterInto = positionAttribute; // TODO: ideally normalize to "into document node"?

                // NOTE: Don't need to normalize text nodes in this case, as no new text node is inserted
            } else {
                // "d. Otherwise, the target location is immediately before or after the insert location
                // node, based on the position attribute setting or its default."

                if (insertLocationNode instanceof Attribute) {
                    // Special case for "next to an attribute"

                    // NOTE: In XML, attributes are unordered. dom4j handles them as a list so has order, but
                    // the XForms spec shouldn't rely on attribute order. We could try to keep the order, but it
                    // is harder as we have to deal with removing duplicate attributes and find a reasonable
                    // insertion strategy.

                    // TODO: Don't think we should even do this now in XForms 1.1
                    insertedNodes = doInsert(insertLocationNode.getParent(), clonedNodes, modifiedInstanceOrNull, doDispatch);

                } else {
                    // Other node types
                    final Element parentNode = insertLocationNode.getParent();
                    final List<Node> siblingElements = parentNode.content();
                    final int actualIndex = siblingElements.indexOf(insertLocationNode);

                    // Prepare insertion of new element
                    final int actualInsertionIndex;
                    if ("before".equals(positionAttribute)) {
                        actualInsertionIndex = actualIndex;
                    } else {
                        // "after"
                        actualInsertionIndex = actualIndex + 1;
                    }

                    // "7. The cloned node or nodes are inserted in the order they were cloned at their target
                    // location depending on their node type."

                    boolean hasTextNode = false;
                    int addIndex = 0;
                    insertedNodes = new ArrayList<Node>(clonedNodes.size());
                    for (Node clonedNode: clonedNodes) {

                        if (clonedNode != null) {// NOTE: we allow passing some null nodes so we check on null
                            if (!(clonedNode instanceof Attribute || clonedNode instanceof Namespace)) {
                                // Element, text, comment, processing instruction node
                                siblingElements.add(actualInsertionIndex + addIndex, clonedNode);
                                insertedNodes.add(clonedNode);
                                hasTextNode |= clonedNode instanceof Text;
                                addIndex++;
                            } else {
                                // We never insert attributes or namespace nodes as siblings
                                if (indentedLogger != null && indentedLogger.isDebugEnabled())
                                    indentedLogger.logDebug("xf:insert", "skipping insertion of node as sibling in element content",
                                        "type", Node$.MODULE$.nodeTypeName(clonedNode),
                                        "node", clonedNode instanceof Attribute ? Dom4jUtils.attributeToDebugString((Attribute) clonedNode) : clonedNode.toString()
                                    );
                    }
                        }
                    }

                    // Normalize text nodes if needed to respect XPath 1.0 constraint
                    if (hasTextNode)
                        Dom4jUtils.normalizeTextNodes(parentNode);
                }

                beforeAfterInto = positionAttribute;
            }
        }

        // Whether some nodes were inserted
        final boolean didInsertNodes = insertedNodes != null && insertedNodes.size() > 0;

        // Log stuff
        if (indentedLogger != null && indentedLogger.isDebugEnabled()) {
            if (didInsertNodes)
                indentedLogger.logDebug("xf:insert", "inserted nodes",
                        "count", Integer.toString(insertedNodes.size()), "instance",
                                (modifiedInstanceOrNull != null) ? modifiedInstanceOrNull.getEffectiveId() : null);
            else
                indentedLogger.logDebug("xf:insert", "no node inserted");
        }

        // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
        if (didInsertNodes && modifiedInstanceOrNull != null) {
            // NOTE: Can be null if document into which delete is performed is not in an instance, e.g. in a variable
            modifiedInstanceOrNull.markModified();
            modifiedInstanceOrNull.model().markStructuralChange(
                scala.Option.<XFormsInstance>apply(modifiedInstanceOrNull),
                FlaggedDefaultsStrategy$.MODULE$
            );
        }

        // Gather list of modified nodes
        final List<NodeInfo> insertedNodeInfos;
        if (didInsertNodes && modifiedInstanceOrNull != null) {
            // Instance can be null if document into which delete is performed is not in an instance, e.g. in a variable
            final DocumentWrapper documentWrapper = (DocumentWrapper) modifiedInstanceOrNull.documentInfo();
            insertedNodeInfos = new ArrayList<NodeInfo>(insertedNodes.size());
            for (Node insertedNode : insertedNodes)
                insertedNodeInfos.add(documentWrapper.wrap(insertedNode));
        } else {
            insertedNodeInfos = Collections.emptyList();
        }

        // "4. If the insert is successful, the event xforms-insert is dispatched."
        // XFormsInstance handles index and repeat items updates
        if (doDispatch && didInsertNodes && modifiedInstanceOrNull != null) {

            // Adjust insert location node and before/after/into in case the root element was replaced
            final NodeInfo adjustedInsertLocationNodeInfo;
            final String adjustedBeforeAfterInto;

            final NodeInfo parent = insertedNodeInfos.get(0).getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE ? insertedNodeInfos.get(0).getParent() : null;
            if (parent != null && parent.equals(parent.getDocumentRoot())) {
                // Node was inserted under document node
                adjustedInsertLocationNodeInfo = parent.getDocumentRoot();
                adjustedBeforeAfterInto = "into";
            } else {
                adjustedInsertLocationNodeInfo = insertLocationNodeInfo;
                adjustedBeforeAfterInto = beforeAfterInto;
            }

            Dispatch.dispatchEvent(
                new XFormsInsertEvent(
                    modifiedInstanceOrNull,
                    insertedNodeInfos,
                    originItems,
                    adjustedInsertLocationNodeInfo,
                    adjustedBeforeAfterInto,
                    insertLocationIndexWithinParentBeforeUpdate
                )
            );
        }

        return insertedNodeInfos;
    }

    private static int findNodeIndex(NodeInfo node) {

        if (node.getParent() == null)
            return 0;

        final AxisIterator it = node.iterateAxis(Axis.PRECEDING_SIBLING);
        int result = 0;

        Item i = it.next();

        while (i != null) {
            result++;
            i = it.next();
        }

        return result;
    }

    private static List<Node> doInsert(Node insertionNode, List<Node> clonedNodes, XFormsInstance modifiedInstance, boolean doDispatch) {
        final List<Node> insertedNodes = new ArrayList<Node>(clonedNodes.size());
        if (insertionNode instanceof Element) {
            // Insert inside an element
            final Element insertContextElement = (Element) insertionNode;

            int otherNodeIndex = 0;
            for (Node clonedNode: clonedNodes) {

                if (clonedNode != null) {// NOTE: we allow passing some null nodes so we check on null
                    if (clonedNode instanceof Attribute) {
                        // Add attribute to element

                        // NOTE: In XML, attributes are unordered. dom4j handles them as a list so has order, but the
                        // XForms spec shouldn't rely on attribute order. We could try to keep the order, but it is harder
                        // as we have to deal with removing duplicate attributes and find a reasonable insertion strategy.
                        final Attribute clonedAttribute = (Attribute) clonedNode;
                        final Attribute existingAttribute = insertContextElement.attribute(clonedAttribute.getQName());

                        if (existingAttribute != null)
                            insertContextElement.remove(existingAttribute);

                        insertContextElement.add(clonedAttribute);

                        if (existingAttribute != null) {

                            // Dispatch xxforms-replace event if required and possible
                            // NOTE: For now, still dispatch xforms-insert for backward compatibility.
                            if (doDispatch && modifiedInstance != null) {
                                final DocumentWrapper documentWrapper = (DocumentWrapper) modifiedInstance.documentInfo();

                                Dispatch.dispatchEvent(
                                    new XXFormsReplaceEvent(
                                        modifiedInstance,
                                        documentWrapper.wrap(existingAttribute),
                                        documentWrapper.wrap(clonedAttribute)));
                            }
                        }

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
            }
            return insertedNodes;
        } else if (insertionNode instanceof Document) {
            final Document insertContextDocument = (Document) insertionNode;

            // "If there is more than one cloned node to insert, only the first node that does not cause a conflict is
            // considered."
            for (Node clonedNode: clonedNodes) {
                // Only an element can be inserted at the root of an instance
                if (clonedNode instanceof Element) {

                    final Element formerRootElement = insertContextDocument.getRootElement();
                    insertContextDocument.setRootElement((Element) clonedNode);

                    // Dispatch xxforms-replace event if required and possible
                    // NOTE: For now, still dispatch xforms-insert for backward compatibility.
                    if (doDispatch && modifiedInstance != null) {
                        final DocumentWrapper documentWrapper = (DocumentWrapper) modifiedInstance.documentInfo();

                        Dispatch.dispatchEvent(
                            new XXFormsReplaceEvent(
                                modifiedInstance,
                                documentWrapper.wrap(formerRootElement),
                                documentWrapper.wrap(insertContextDocument.getRootElement())));
                    }

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
