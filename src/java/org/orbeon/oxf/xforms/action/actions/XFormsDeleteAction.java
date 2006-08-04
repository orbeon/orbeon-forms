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
import org.orbeon.oxf.xforms.event.events.XFormsDeleteEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 9.3.6 The delete Element
 */
public class XFormsDeleteAction extends XFormsAction {

    public static final String CANNOT_DELETE_READONLY_MESSAGE = "Cannot perform deletion in read-only instance.";

    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

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
        actionInterpreter.pushContextAttributeIfNeeded(pipelineContext, actionElement);

        // We are now in the insert context

        // "The delete action is terminated with no effect if the insert context is the empty node-set [...]."
        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode == null)
            return;

        {
            final NodeInfo nodeInfoToRemove;
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
                        final String insertionIndexString = containingDocument.getEvaluator().evaluateAsString(pipelineContext,
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
                    nodeInfoToRemove = (NodeInfo) collectionToUpdate.get(deleteIndex - 1);
                    nodeToRemove = XFormsUtils.getNodeFromNodeInfo(nodeInfoToRemove, CANNOT_DELETE_READONLY_MESSAGE);

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

            // Identify the instance that actually changes
            final XFormsInstance modifiedInstance = containingDocument.getInstanceForNode(nodeInfoToRemove);

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
            XFormsIndexUtils.adjustRepeatIndexes(pipelineContext, xformsControls, nestedRepeatIndexUpdates);

            // "4. If the delete is successful, the event xforms-delete is dispatched."
            containingDocument.dispatchEvent(pipelineContext, new XFormsDeleteEvent(modifiedInstance, atAttribute));

            // "XForms Actions that change the tree structure of instance data result in setting all four flags to true"
            modifiedInstance.getModel().setAllDeferredFlags(true);
            containingDocument.getXFormsControls().markDirty();
        }
    }
}
