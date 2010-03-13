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
package org.orbeon.oxf.xforms.control.controls;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction;
import org.orbeon.oxf.xforms.action.actions.XFormsInsertAction;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsNoSingleNodeContainerControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XXFormsDndEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsIndexChangedEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsNodesetChangedEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.value.AtomicValue;
import org.orbeon.saxon.value.NumericValue;
import org.orbeon.saxon.value.StringValue;

import java.util.*;

/**
 * Represents an xforms:repeat container control.
 */
public class XFormsRepeatControl extends XFormsNoSingleNodeContainerControl {

    private int startIndex;
    private transient boolean restoredState;  // used by deserializeLocal() and childrenAdded()

    public static class XFormsRepeatControlLocal extends XFormsControlLocal {
        private int index = -1;

        private XFormsRepeatControlLocal() {
        }
        
        public int getIndex() {
            return index;
        }
    }

    public XFormsRepeatControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);

        // Initial local state
        setLocal(new XFormsRepeatControlLocal());

        // Store initial repeat index information
        final String startIndexString = element.attributeValue("startindex");
        this.startIndex = (startIndexString != null) ? Integer.parseInt(startIndexString) : 1;
    }

    @Override
    public boolean hasJavaScriptInitialization() {
        // If there is DnD, must tell the client to perform initialization
        return isDnD();
    }

    @Override
    public void childrenAdded(PropertyContext propertyContext) {
        // This is called once all children have been added

        // NOTE: We used to initialize the repeat index here, but this made the index() function non-functional during
        // repeat construction. Instead, we now initialize the index in setBindingContext(), when that method is called
        // during control creation.
    }

    @Override
    public void setBindingContext(PropertyContext propertyContext, XFormsContextStack.BindingContext bindingContext, boolean isCreate) {
        super.setBindingContext(propertyContext, bindingContext, isCreate);

        // Ensure that the initial index is set, unless the state was already restored. In that case, the index was
        // reset from serialized data and the initial index must not be used.
        if (isCreate && !restoredState) {
            final XFormsRepeatControlLocal local = (XFormsRepeatControlLocal) getCurrentLocal();
            local.index = ensureIndexBounds(getStartIndex());
        }

        // Reset refresh information
        refreshInfo = null;
    }

    public int getStartIndex() {
        return startIndex;
    }

    /**
     * Set the repeat index. The index is automatically adjusted to fall within bounds.
     *
     * @param propertyContext   current context
     * @param index             new repeat index
     */
    public void setIndex(PropertyContext propertyContext, int index) {

        final int oldRepeatIndex = getIndex();// 1-based

        // Set index
        setIndexInternal(index);

        if (oldRepeatIndex != getIndex()) {
            // Dispatch custom event to notify that the repeat index has changed
            getXBLContainer().dispatchEvent(propertyContext, new XXFormsIndexChangedEvent(containingDocument, this,
                    oldRepeatIndex, getIndex()));
        }

        // Handle rebuild flags for container affected by changes to this repeat
        final XBLContainer resolutionScopeContainer = getXBLContainer().findResolutionScope(getPrefixedId());
        resolutionScopeContainer.setDeferredFlagsForSetindex();
    }

    private void setIndexInternal(int index) {
        final XFormsRepeatControlLocal local = (XFormsRepeatControlLocal) getLocalForUpdate();
        local.index = ensureIndexBounds(index);
    }

    private int ensureIndexBounds(int index) {
        return Math.min(Math.max(index, (getSize() > 0) ? 1 : 0), getSize());
    }

    @Override
    public int getSize() {

        // Return the size based on the nodeset size, so we can call this before all iterations have been added.
        // Scenario:
        // o call index() or xxf:index() from within a variable within the iteration:
        // o not all iterations have been added, but the size must be known
        // NOTE: This raises an interesting question about the relevance of iterations. As of 2009-12-04, not sure
        // how we handle that!
        final XFormsContextStack.BindingContext bindingContext = getBindingContext();
        if (bindingContext == null)
            return 0;

        return bindingContext.nodeset.size();
    }

    public int getIndex() {
        final XFormsRepeatControlLocal local = (XFormsRepeatControlLocal) getCurrentLocal();
        if (local.index != -1) {
            return local.index;
        } else {
            throw new OXFException("Repeat index was not set for repeat id: " + getEffectiveId());
        }
    }

    @Override
    protected void evaluate(PropertyContext propertyContext, boolean isRefresh) {
        
        // This will evaluate relevance
        super.evaluate(propertyContext, isRefresh);

        // Evaluate iterations
        final List<XFormsControl> children = getChildren();
        if (children != null) {
            for (final XFormsControl child: children) {
                final XFormsRepeatIterationControl currentRepeatIteration = (XFormsRepeatIterationControl) child;
                currentRepeatIteration.evaluate(propertyContext, isRefresh);
            }
        }
    }
    
    @Override
    public String getLabel(PropertyContext propertyContext) {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public String getHelp(PropertyContext propertyContext) {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public String getHint(PropertyContext propertyContext) {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public String getAlert(PropertyContext propertyContext) {
        // Don't bother letting superclass handle this
        return null;
    }

    @Override
    public void performDefaultAction(PropertyContext propertyContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_DND.equals(event.getName())) {
            doDnD(propertyContext, event);
        }
        super.performDefaultAction(propertyContext, event);
    }

    private void doDnD(PropertyContext propertyContext, XFormsEvent event) {
        // Only support this on DnD-enabled controls
        if (!isDnD())
            throw new ValidationException("Attempt to process xxforms-dnd event on non-DnD-enabled control: " + getEffectiveId(), getLocationData());

        // Perform DnD operation on node data
        final XXFormsDndEvent dndEvent = (XXFormsDndEvent) event;

        // Get all repeat iteration details
        final String[] dndStart = StringUtils.split(dndEvent.getDndStart(), '-');
        final String[] dndEnd = StringUtils.split(dndEvent.getDndEnd(), '-');

        // Find source information
        final List<Item> sourceNodeset;
        final int requestedSourceIndex;
        {
            sourceNodeset = getBindingContext().getNodeset();
            requestedSourceIndex = Integer.parseInt(dndStart[dndStart.length - 1]);

            if (requestedSourceIndex < 1 || requestedSourceIndex > sourceNodeset.size())
                throw new ValidationException("Out of range Dnd start iteration: " + requestedSourceIndex, getLocationData());
        }

        // Find destination
        final List<Item> destinationNodeset;
        final int requestedDestinationIndex;
        {
            final XFormsRepeatControl destinationControl;
            if (dndEnd.length > 1) {
                // DnD destination is a different repeat control
                final String containingRepeatEffectiveId
                        = getPrefixedId() + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1
                            + StringUtils.join(dndEnd, XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2, 0, dndEnd.length - 1);

                destinationControl = (XFormsRepeatControl) containingDocument.getObjectByEffectiveId(containingRepeatEffectiveId);
            } else {
                // DnD destination is the current repeat control
                destinationControl = this;
            }

            destinationNodeset = new ArrayList<Item>(destinationControl.getBindingContext().getNodeset());
            requestedDestinationIndex = Integer.parseInt(dndEnd[dndEnd.length - 1]);
        }

        // TODO: Detect DnD over repeat boundaries, and throw if not explicitly enabled

        // Delete node from source
        // NOTE: don't dispatch event, because one call to updateRepeatNodeset() is enough
        final List deletedNodes = XFormsDeleteAction.doDelete(propertyContext, containingDocument,
                containingDocument.getControls().getIndentedLogger(), sourceNodeset, requestedSourceIndex, false);
        final NodeInfo deletedNodeInfo = (NodeInfo) deletedNodes.get(0);

        // Adjust destination collection to reflect new state
        final int deletedNodePosition = destinationNodeset.indexOf(deletedNodeInfo);
        final int actualDestinationIndex;
        final String destinationPosition;
        if (deletedNodePosition != -1) {
            // Deleted node was part of the destination nodeset
            // NOTE: This removes from our copy of the nodeset, not from the control's nodeset, which must not be touched until control bindings are updated
            destinationNodeset.remove(deletedNodePosition);
            // If the insertion position is after the delete node, must adjust it
            if (requestedDestinationIndex <= deletedNodePosition + 1) {
                // Insertion point is before or on (degenerate case) deleted node
                actualDestinationIndex = requestedDestinationIndex;
                destinationPosition = "before";
            } else {
                // Insertion point is after deleted node
                actualDestinationIndex = requestedDestinationIndex - 1;
                destinationPosition = "after";
            }
        } else {
            // Deleted node was not part of the destination nodeset
            if (requestedDestinationIndex <= destinationNodeset.size()) {
                // Position within nodeset
                actualDestinationIndex = requestedDestinationIndex;
                destinationPosition = "before";
            } else {
                // Position at the end of the nodeset
                actualDestinationIndex = requestedDestinationIndex - 1;
                destinationPosition = "after";
            }
        }

        // Insert nodes into destination
        final NodeInfo insertContextNodeInfo = deletedNodeInfo.getParent();
        // NOTE: Tell insert to not clone the node, as we know it is ready for insertion
        XFormsInsertAction.doInsert(propertyContext, containingDocument, containingDocument.getControls().getIndentedLogger(),
                destinationPosition, destinationNodeset, insertContextNodeInfo, deletedNodes, actualDestinationIndex, false, true);

        // TODO: should dispatch xxforms-move instead of xforms-insert?
    }

    public boolean isDnD() {
        final String dndAttribute = getControlElement().attributeValue(XFormsConstants.XXFORMS_DND_QNAME);
        return dndAttribute != null && !"none".equals(dndAttribute);
    }

    public void updateNodesetForInsertDelete(PropertyContext propertyContext, List<Item> insertedNodeInfos) {

        // Get old nodeset
        final List<Item> oldRepeatNodeset = getBindingContext().getNodeset();

        // Set binding context and get new nodeset
        final List<Item> newRepeatNodeset;
        {
            // Set new binding context on the repeat control
            // NOTE: here we just reevaluate against the parent; maybe we should reevaluate all the way down
            final XFormsContextStack contextStack = getXBLContainer().getContextStack();
            final XFormsControl parentControl = getParent();
            if (parentControl == null) {
                // TODO: does this prevent preceding top-level variables from working?
                contextStack.resetBindingContext(propertyContext);
            } else {
                // NOTE: Don't do setBinding(parent) as that would not keep the variables in scope
                contextStack.setBinding(this);
                contextStack.popBinding();
            }
            contextStack.pushBinding(propertyContext, getControlElement(), getEffectiveId(), getResolutionScope());
            setBindingContext(propertyContext, contextStack.getCurrentBindingContext(), false);

            newRepeatNodeset = getBindingContext().getNodeset();
        }

        // Move things around and create new iterations if needed
        if (!compareNodesets(oldRepeatNodeset, newRepeatNodeset)) {
            // Update iterations
            final List<XFormsRepeatIterationControl> newIterations
                    = updateIterations(propertyContext, oldRepeatNodeset, insertedNodeInfos);
            // Initialize all new iterations
            final ControlTree currentControlTree = containingDocument.getControls().getCurrentControlTree();
            for (final XFormsRepeatIterationControl newIteration: newIterations) {
                // This evaluates all controls and then dispatches creation events
                currentControlTree.initializeRepeatIterationTree(propertyContext, newIteration);
            }
            // Perform a very local refresh on the control
            // This will dispatch xforms-enabled/xforms-disabled/xxforms-nodeset-changed/xxforms-index-changed events if needed
            markDirty();
            evaluate(propertyContext, true);
            containingDocument.getControls().dispatchRefreshEvents(propertyContext, Collections.singletonList(getEffectiveId()));
        }
    }

    /**
     * Update this repeat's iterations given the old and new node-sets, and a list of inserted nodes if any (used for
     * index updates). This returns a list of entirely new repeat iterations added, if any. The repeat's index is
     * adjusted.
     *
     * NOTE: The new binding context must have been set on this control before calling.
     *
     * @param propertyContext       current context
     * @param oldRepeatNodeset      old node-set
     * @param insertedNodeInfos     nodes just inserted by xforms:insert if any, or null
     * @return                      new iterations if any, or an empty list
     */
    public List<XFormsRepeatIterationControl> updateIterations(PropertyContext propertyContext, List<Item> oldRepeatNodeset,
                                                               List<Item> insertedNodeInfos) {

        // NOTE: The following assumes the nodesets have changed

        // Get current (new) nodeset
        final List<Item> newRepeatNodeset = getBindingContext().getNodeset();

        final XFormsControls controls = containingDocument.getControls();
        controls.cloneInitialStateIfNeeded(propertyContext);

        final boolean isInsert = insertedNodeInfos != null;

        final ControlTree currentControlTree = controls.getCurrentControlTree();

        final IndentedLogger indentedLogger = containingDocument.getControls().getIndentedLogger();
        final boolean isDebugEnabled = indentedLogger.isDebugEnabled();

        final int oldRepeatIndex = getIndex();// 1-based
        boolean updated = false;
        final List<XFormsRepeatIterationControl> newIterations;
        final List<Integer> movedIterationsOldPositions;
        final List<Integer> movedIterationsNewPositions;

        if (newRepeatNodeset != null && newRepeatNodeset.size() > 0) {

            // For each new node, what its old index was, -1 if it was not there
            final int[] oldIndexes = findNodeIndexes(newRepeatNodeset, oldRepeatNodeset);

            // For each old node, what its new index is, -1 if it is no longer there
            final int[] newIndexes = findNodeIndexes(oldRepeatNodeset, newRepeatNodeset);

            // Remove control information for iterations that move or just disappear
            final List oldChildren = getChildren();

            for (int i = 0; i < newIndexes.length; i++) {
                final int currentNewIndex = newIndexes[i];

                if (currentNewIndex != i) {
                    // Node has moved or is removed

                    final boolean isRemoved = currentNewIndex == -1;
                    final XFormsRepeatIterationControl movedOrRemovedIteration = (XFormsRepeatIterationControl) oldChildren.get(i);

                    if (isRemoved) {
                        if (isDebugEnabled) {
                            indentedLogger.startHandleOperation("xforms:repeat", "removing iteration", "id", getEffectiveId(), "index", Integer.toString(i + 1));
                        }

                        // Dispatch destruction events
                        currentControlTree.dispatchDestructionEvents(propertyContext, movedOrRemovedIteration);

                        // Indicate to iteration that it is being removed
                        movedOrRemovedIteration.iterationRemoved(propertyContext);

                        if (isDebugEnabled) {
                            indentedLogger.endHandleOperation();
                        }
                    }

                    // Deindex old iteration
                    currentControlTree.deindexSubtree(movedOrRemovedIteration, true);
                    updated = true;
                }
            }

            // Set new repeat index (do this before creating new iterations so that index is available then)
            boolean didSetIndex = false;
            if (isInsert) {
                // Insert logic

                // We want to point to a new node (case of insert)

                // First, try to point to the last inserted node if found
                final int[] foobar = findNodeIndexes(insertedNodeInfos, newRepeatNodeset);

                for (int i = foobar.length - 1; i >= 0; i--) {
                    if (foobar[i] != -1) {
                        final int newRepeatIndex = foobar[i] + 1;

                        if (isDebugEnabled) {
                            indentedLogger.logDebug("xforms:repeat", "setting index to new node",
                                    "id", getEffectiveId(), "new index", Integer.toString(newRepeatIndex));
                        }

                        setIndexInternal(newRepeatIndex);
                        didSetIndex = true;
                        break;
                    }
                }
            }

            if (!didSetIndex) {
                // Delete logic

                // Try to point to the same node as before
                if (oldRepeatIndex > 0 && oldRepeatIndex <= newIndexes.length && newIndexes[oldRepeatIndex - 1] != -1) {
                    // The index was pointing to a node which is still there, so just move the index
                    final int newRepeatIndex = newIndexes[oldRepeatIndex - 1] + 1;

                    if (newRepeatIndex != oldRepeatIndex) {
                        if (isDebugEnabled) {
                            indentedLogger.logDebug("xforms:repeat", "adjusting index for existing node",
                                   "id", getEffectiveId(),
                                   "old index", Integer.toString(oldRepeatIndex),
                                   "new index", Integer.toString(newRepeatIndex));
                        }

                        setIndexInternal(newRepeatIndex);
                    }
                } else if (oldRepeatIndex > 0 && oldRepeatIndex <= newIndexes.length) {
                    // The index was pointing to a node which has been removed

                    if (oldRepeatIndex > newRepeatNodeset.size()) {
                        // "if the repeat index was pointing to one of the deleted repeat items, and if the new size of
                        // the collection is smaller than the index, the index is changed to the new size of the
                        // collection."

                        if (isDebugEnabled) {
                            indentedLogger.logDebug("xforms:repeat", "setting index to the size of the new nodeset",
                                    "id", getEffectiveId(), "new index", Integer.toString(newRepeatNodeset.size()));
                        }

                        setIndexInternal(newRepeatNodeset.size());
                    } else {
                        // "if the new size of the collection is equal to or greater than the index, the index is not
                        // changed"
                        // NOP
                    }
                } else {
                    // Old index was out of bounds?

                    setIndexInternal(getStartIndex());

                    if (isDebugEnabled) {
                        indentedLogger.logDebug("xforms:repeat", "resetting index",
                                "id", getEffectiveId(), "new index", Integer.toString(getIndex()));
                    }
                }
            }

            // Iterate over new nodeset to move or add iterations
            final int newSize = newRepeatNodeset.size();
            final List<XFormsControl> newChildren = new ArrayList<XFormsControl>(newSize);
            newIterations = new ArrayList<XFormsRepeatIterationControl>();
            movedIterationsOldPositions = new ArrayList<Integer>();
            movedIterationsNewPositions = new ArrayList<Integer>();
            final XFormsContextStack contextStack = getXBLContainer().getContextStack();
            contextStack.setBinding(this); // ensure we start with the correct binding
            for (int repeatIndex = 1; repeatIndex <= newSize; repeatIndex++) {// 1-based index

                final int currentOldIndex = oldIndexes[repeatIndex - 1];
                if (currentOldIndex == -1) {
                    // This new node was not in the old nodeset so create a new one

                    if (isDebugEnabled) {
                        indentedLogger.startHandleOperation("xforms:repeat", "creating new iteration", "id", getEffectiveId(), "index", Integer.toString(repeatIndex));
                    }

                    // Create repeat iteration with proper binding context
                    contextStack.pushIteration(repeatIndex);
                    final XFormsRepeatIterationControl newIteration
                            = controls.createRepeatIterationTree(propertyContext, contextStack.getCurrentBindingContext(), this, repeatIndex);
                    contextStack.popBinding();

                    updated = true;

                    newIterations.add(newIteration);

                    if (isDebugEnabled) {
                        indentedLogger.endHandleOperation();
                    }

                    // Add new iteration
                    newChildren.add(newIteration);
                } else {
                    // This new node was in the old nodeset so keep it
                    final XFormsRepeatIterationControl existingIteration = (XFormsRepeatIterationControl) oldChildren.get(currentOldIndex);
                    final int newIterationOldIndex = existingIteration.getIterationIndex();
                    if (newIterationOldIndex != repeatIndex) {
                        // Iteration index changed

                        if (isDebugEnabled) {
                            indentedLogger.logDebug("xforms:repeat", "moving iteration",
                                   "id", getEffectiveId(),
                                   "old index", Integer.toString(newIterationOldIndex),
                                   "new index", Integer.toString(repeatIndex));
                        }

                        // Set new index
                        existingIteration.setIterationIndex(repeatIndex);

                        // Update binding context on iteration for consistency (since binding context on xf:repeat control was updated by caller)

                        // TODO: Then should the bindings for the whole subtree of control be updated at this time? Probably!
                        contextStack.pushIteration(repeatIndex);
                        existingIteration.setBindingContext(propertyContext, contextStack.getCurrentBindingContext(), false);
                        contextStack.popBinding();

                        // Index new iteration
                        currentControlTree.indexSubtree(existingIteration, true);
                        updated = true;


                        // Add information for moved iterations
                        movedIterationsOldPositions.add(newIterationOldIndex);
                        movedIterationsNewPositions.add(repeatIndex);
                    }

                    // Add existing iteration
                    newChildren.add(existingIteration);
                }
            }
            // Set the new children iterations
            setChildren(newChildren);


        } else {
            // New repeat nodeset is now empty

            // Remove control information for iterations that disappear
            final List oldChildren = getChildren();
            if (oldChildren != null) {
                for (int i = 0; i < oldChildren.size(); i++) {

                    if (isDebugEnabled) {
                        indentedLogger.startHandleOperation("xforms:repeat", "removing iteration", "id", getEffectiveId(), "index", Integer.toString(i + 1));
                    }

                    final XFormsRepeatIterationControl removedIteration = (XFormsRepeatIterationControl) oldChildren.get(i);

                    // Dispatch destruction events
                    currentControlTree.dispatchDestructionEvents(propertyContext, removedIteration);

                    // Deindex old iteration
                    currentControlTree.deindexSubtree(removedIteration, true);
                    if (isDebugEnabled) {
                        indentedLogger.endHandleOperation();
                    }
                    updated = true;
                }
            }

            if (isDebugEnabled) {
                if (getIndex() != 0)
                    indentedLogger.logDebug("xforms:repeat", "setting index to 0", "id", getEffectiveId());
            }

            setChildren(null);
            setIndexInternal(0);

            newIterations = Collections.emptyList();
            movedIterationsOldPositions = Collections.emptyList();
            movedIterationsNewPositions = Collections.emptyList();
        }

        if (updated || oldRepeatIndex != getIndex()) {
            // Keep information available until refresh events are dispatched, which must happen soon after this method was called
            refreshInfo = new RefreshInfo();
            if (updated) {
                refreshInfo.isNodesetChanged = true;
                refreshInfo.newIterations = newIterations;
                refreshInfo.movedIterationsOldPositions = movedIterationsOldPositions;
                refreshInfo.movedIterationsNewPositions = movedIterationsNewPositions;
            }

            refreshInfo.oldRepeatIndex = oldRepeatIndex;
        } else {
            refreshInfo = null;
        }

        return newIterations;
    }

    public void dispatchRefreshEvents(PropertyContext propertyContext) {
        if (isRelevant() && refreshInfo != null) {
            final RefreshInfo refreshInfo = this.refreshInfo; // copy as dispatching events might clear this.refreshInfo
            if (refreshInfo.isNodesetChanged) {
                // Dispatch custom event to xforms:repeat to notify that the nodeset has changed
                getXBLContainer().dispatchEvent(propertyContext, new XXFormsNodesetChangedEvent(containingDocument, this,
                        refreshInfo.newIterations, refreshInfo.movedIterationsOldPositions, refreshInfo.movedIterationsNewPositions));
            }

            if (refreshInfo.oldRepeatIndex != getIndex()) {
                // Dispatch custom event to notify that the repeat index has changed
                getXBLContainer().dispatchEvent(propertyContext, new XXFormsIndexChangedEvent(containingDocument, this,
                        refreshInfo.oldRepeatIndex, getIndex()));
            }
            this.refreshInfo = null;
        }
    }

    private RefreshInfo refreshInfo;
    private static class RefreshInfo {
        public boolean isNodesetChanged;
        public List<XFormsRepeatIterationControl> newIterations;
        public List<Integer> movedIterationsOldPositions;
        public List<Integer> movedIterationsNewPositions;

        public int oldRepeatIndex;// 1-based
    }

    private int indexOfItem(List<Item> sequence, Item otherItem) {
        int index = 0;
        for (final Item currentItem: sequence) {
            if (currentItem instanceof StringValue) {
                // Saxon doesn't allow equals() on StringValue because it requires a collation and equals() might throw
                if (otherItem instanceof StringValue) {
                    final StringValue currentStringValue = (StringValue) currentItem;
                    if (currentStringValue.codepointEquals((StringValue) otherItem)) {
                        return index;
                    }
                }
            } else if (currentItem instanceof NumericValue) {
                // Saxon doesn't allow equals() between numeric and non-numeric values
                if (otherItem instanceof NumericValue) {
                    final NumericValue currentNumericValue = (NumericValue) currentItem;
                    if (currentNumericValue.equals((NumericValue) otherItem)) {
                        return index;
                    }
                }
            } else if (currentItem instanceof AtomicValue) {
                if (otherItem instanceof AtomicValue) {
                    final AtomicValue currentAtomicValue = (AtomicValue) currentItem;
                    if (currentAtomicValue.equals((AtomicValue) otherItem)) {
                        return index;
                    }
                }
            } else {
                if (currentItem.equals(otherItem)) {// equals() is the same as isSameNodeInfo() for NodeInfo, and compares the values for values
                    return index;
                }
            }
            index++;
        }
        return -1;
    }

    private int[] findNodeIndexes(List<Item> nodeset1, List<Item> nodeset2) {
        final int[] result = new int[nodeset1.size()];

        int index = 0;
        for (final Item currentItem: nodeset1) {
            result[index] = indexOfItem(nodeset2, currentItem);
            index++;
        }
        return result;
    }

    @Override
    public Map<String, String> serializeLocal() {
        // Serialize index
        return Collections.singletonMap("index", Integer.toString(getIndex()));
    }

    @Override
    public void deserializeLocal(Element element) {
        // Deserialize index

        // NOTE: Don't use setIndex() as we don't want to cause initialLocal != currentLocal
        final XFormsRepeatControlLocal local = (XFormsRepeatControlLocal) getCurrentLocal();
        local.index = Integer.parseInt(element.attributeValue("index"));

        // Indicate to childrenAdded() that default index must not be set
        restoredState = true;
    }

    @Override
    public boolean setFocus() {
        // "4.3.7 The xforms-focus Event [...] Setting focus to a repeat container form control sets the focus to the
        // repeat object  associated with the repeat index"
        if (getIndex() > 0) {
            return getChildren().get(getIndex() - 1).setFocus();
        } else {
            return false;
        }
    }

    @Override
    public boolean supportsRefreshEvents() {
        return true;
    }

    @Override
    protected boolean computeRelevant() {

        // If parent is not relevant then we are not relevant either
        if (!super.computeRelevant())
            return false;

        final List<Item> currentItems = bindingContext.getNodeset();
        for (Item currentItem: currentItems) {
            // If bound to non-node, consider as relevant (e.g. nodeset="(1 to 10)")
            if (!(currentItem instanceof NodeInfo))
                return true;

            // Bound to node and node is relevant
            final NodeInfo currentNodeInfo = (NodeInfo) currentItem;
            if (InstanceData.getInheritedRelevant(currentNodeInfo))
                return true;
        }

        // No item was relevant so we are not relevant either
        return false;
    }

    // Only allow xxforms-dnd from client
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_DND);
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }
}
