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
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.events.XFormsDeleteEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * 9.3.6 The delete Element
 */
public class XFormsDeleteAction extends XFormsAction {

    public static final String CANNOT_DELETE_READONLY_MESSAGE = "Cannot perform deletion in read-only instance.";

    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        final String atAttribute = actionElement.attributeValue("at");
        final String contextAttribute = actionElement.attributeValue("context");

        final List collectionToUpdate = contextStack.getCurrentNodeset();
        final boolean isEmptyNodesetBinding = collectionToUpdate == null || collectionToUpdate.size() == 0;

        // "The delete action is terminated with no effect if [...] the context attribute is not given and the Node
        // Set Binding node-set is empty."
        if (contextAttribute == null && isEmptyNodesetBinding) {
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:delete", "context is empty, terminating");
            return;
        }

        // Handle insert context (with @context attribute)
        // "The delete action is terminated with no effect if the insert context is the empty node-set [...]."
        if (hasOverriddenContext && (overriddenContext == null || !(overriddenContext instanceof NodeInfo))) {
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:delete", "overridden context is an empty nodeset or not a nodeset, terminating");
            return;
        }

        {
            int deleteIndex;
            {
                if (isEmptyNodesetBinding) {
                    // "If the Node Set Binding node-set empty, then this attribute is ignored"
                    deleteIndex = 0;
                } else if (atAttribute == null) {
                    // "If there is no delete location, each node in the Node Set Binding node-set is deleted, except
                    // if the node is a readonly node or the root document element of an instance then that particular
                    // node is not deleted."

                    deleteIndex = -1;
                } else {
                    // "1. The evaluation context node is the first node in document order from the Node Set Binding
                    // node-set, the context size is the size of the Node Set Binding node-set, and the context
                    // position is 1."

                    // "2. The return value is processed according to the rules of the XPath function round()"
                    final String insertionIndexString = XPathCache.evaluateAsString(pipelineContext,
                            collectionToUpdate, 1,
                            "round(" + atAttribute + ")",
                            containingDocument.getNamespaceMappings(actionElement), contextStack.getCurrentVariables(),
                            XFormsContainingDocument.getFunctionLibrary(), contextStack.getFunctionContext(), null,
                            (LocationData) actionElement.getData());

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

            doDelete(pipelineContext, containingDocument, collectionToUpdate, deleteIndex);
        }
    }

    public static List doDelete(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, List collectionToUpdate, int deleteIndex) {

        final boolean isEmptyNodesetBinding = collectionToUpdate == null || collectionToUpdate.size() == 0;

        final List deletedNodeInfos;
        if (isEmptyNodesetBinding) {
            deletedNodeInfos = Collections.EMPTY_LIST;
        } else if (deleteIndex == -1) {
            // Delete the entire collection

            deletedNodeInfos = new ArrayList(collectionToUpdate.size());
            for (int i = 1; i <= collectionToUpdate.size(); i++) {
                final NodeInfo deletedNodeInfo = doDeleteOne(containingDocument, collectionToUpdate, i);
                if (deletedNodeInfo != null) {
                    deletedNodeInfos.add(deletedNodeInfo);
                }
            }
        } else {
            // Find actual deletion point

            final NodeInfo deletedNodeInfo = doDeleteOne(containingDocument, collectionToUpdate, deleteIndex);
            if (deletedNodeInfo != null) {
                deletedNodeInfos = Collections.singletonList(deletedNodeInfo);
            } else {
                deletedNodeInfos = Collections.EMPTY_LIST;
            }
        }

        if (deletedNodeInfos.size() == 0) {
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:delete", "empty collection, terminating");
        } else {
            // Identify the instance that actually changes
            // NOTE: More than one instance may be modified. For now we look at the first one.
            final XFormsInstance modifiedInstance = containingDocument.getInstanceForNode((NodeInfo) deletedNodeInfos.get(0));

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:delete", "removed nodes",
                        new String[] { "count", Integer.toString(deletedNodeInfos.size()), "instance", modifiedInstance.getEffectiveId() });

            // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
            modifiedInstance.getModel(containingDocument).setAllDeferredFlags(true);
            containingDocument.getControls().markDirtySinceLastRequest(true);

            // "4. If the delete is successful, the event xforms-delete is dispatched."
            modifiedInstance.getContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsDeleteEvent(modifiedInstance, deletedNodeInfos, deleteIndex));
        }

        return deletedNodeInfos;
    }

    private static NodeInfo doDeleteOne(XFormsContainingDocument containingDocument, List collectionToUpdate, int deleteIndex) {
        final NodeInfo nodeInfoToRemove = (NodeInfo) collectionToUpdate.get(deleteIndex - 1);
        final Node nodeToRemove = XFormsUtils.getNodeFromNodeInfo(nodeInfoToRemove, CANNOT_DELETE_READONLY_MESSAGE);

        final List parentContent;
        final int actualIndexInParentContentCollection;
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

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:delete", "attempt to delete document node, terminating");

            return null;
        } else {
            throw new OXFException("Node to delete doesn't have a parent.");
        }

        // Actually perform the deletion
        // "The node at the delete location in the Node Set Binding node-set is deleted"
        parentContent.remove(actualIndexInParentContentCollection);

        return nodeInfoToRemove;
    }
}
