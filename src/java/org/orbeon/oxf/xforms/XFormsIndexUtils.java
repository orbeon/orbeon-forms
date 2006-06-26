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
package org.orbeon.oxf.xforms;

import org.dom4j.Node;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xforms.controls.RepeatControlInfo;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Useful functions for handling repeat indexes.
 */
public class XFormsIndexUtils {

    /**
     * Ajust repeat indexes so that they are put back within bounds.
     *
     * @param pipelineContext
     * @param xformsControls
     * @param currentControlsState
     */
    public static void adjustIndexes(PipelineContext pipelineContext, final XFormsControls xformsControls,
                                     final XFormsControls.ControlsState currentControlsState) {

        // NOTE: You can imagine really complicated stuff related to index
        // updates. Here, we assume that repeat iterations do
        // *not* depend on instance values that themselves depend on the index()
        // function. This scenario is not impossible, but fairly far-fetched I think, and we haven't seen it yet. So
        // once an instance structure and content is determined, we assume that it won't change in a significant way
        // with index updates performed below.

        // However, one scenario we want to allow is a repeat "detail" on the same level as a repeat "master", where
        // the repeat detail iteration depends on index('master').

        // TODO: detect use of index() function
        final Map updatedIndexesIds = new HashMap();
        currentControlsState.visitControlInfoFollowRepeats(pipelineContext, xformsControls, new XFormsControls.ControlInfoVisitorListener() {

            private int level = 0;

            public void startVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    // Found an xforms:repeat
                    final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) controlInfo;
                    final String repeatId = repeatControlInfo.getOriginalId();
                    final List repeatNodeSet = xformsControls.getCurrentNodeset();

                    // Make sure the bounds of this xforms:repeat are correct
                    // for the rest of the visit.

                    final int adjustedNewIndex;
                    {
                        final int newIndex = ((Integer) currentControlsState.getRepeatIdToIndex().get(repeatId)).intValue();

                        // Adjust bounds if necessary
                        if (repeatNodeSet == null || repeatNodeSet.size() == 0)
                            adjustedNewIndex = 0;
                        else if (newIndex < 1)
                            adjustedNewIndex = 1;
                        else if (newIndex > repeatNodeSet.size())
                            adjustedNewIndex = repeatNodeSet.size();
                        else
                            adjustedNewIndex = newIndex;
                    }

                    // Set index
                    currentControlsState.updateRepeatIndex(repeatId, adjustedNewIndex);
                    updatedIndexesIds.put(repeatId, "");

                    level++;
                }
            }

            public void endVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    level--;
                }
            }
        });

        // Repeats that haven't been reached are set to 0
        for (Iterator i = currentControlsState.getRepeatIdToIndex().entrySet().iterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final String repeatId = (String) currentEntry.getKey();

            if (updatedIndexesIds.get(repeatId) == null) {
                currentControlsState.updateRepeatIndex(repeatId, 0);
            }
        }
    }

    /**
     * Adjust repeat indexes after an insertion.
     *
     * @param pipelineContext
     * @param xformsControls
     * @param currentControlsState
     * @param clonedNode
     */
    public static void ajustIndexesAfterInsert(PipelineContext pipelineContext, final XFormsControls xformsControls,
                                               final XFormsControls.ControlsState currentControlsState, final Node clonedNode) {

        // NOTE: The code below assumes that there are no nested repeats bound to node-sets that intersect
        currentControlsState.visitControlInfoFollowRepeats(pipelineContext, xformsControls, new XFormsControls.ControlInfoVisitorListener() {

            private ControlInfo foundControl;

            public void startVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    // Found an xforms:repeat
                    final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) controlInfo;
                    final String repeatId = repeatControlInfo.getOriginalId();
                    final List repeatNodeSet = xformsControls.getCurrentNodeset();

                    if (foundControl == null) {
                        // We are not yet inside a matching xforms:repeat

                        if (repeatNodeSet != null && repeatNodeSet.size() > 0) {
                            // Find whether one node of the repeat node-set contains the inserted node
                            int index = 1;
                            for (Iterator i = repeatNodeSet.iterator(); i.hasNext(); index++) {
                                final NodeInfo currentNode = (NodeInfo) i.next();
                                if (((NodeWrapper) currentNode).getUnderlyingNode() == clonedNode) {
                                    // Found xforms:repeat affected by the change

                                    // "The index for any repeating sequence that is bound
                                    // to the homogeneous collection where the node was
                                    // added is updated to point to the newly added node."
                                    currentControlsState.updateRepeatIndex(repeatId, index);

                                    // First step: set all children indexes to 0
                                    final List nestedRepeatIds = currentControlsState.getNestedRepeatIds(xformsControls, repeatId);
                                    if (nestedRepeatIds != null) {
                                        for (Iterator j = nestedRepeatIds.iterator(); j.hasNext();) {
                                            final String nestedRepeatId = (String) j.next();
                                            currentControlsState.updateRepeatIndex(nestedRepeatId, 0);
                                        }
                                    }

                                    foundControl = controlInfo;
                                    break;
                                }
                            }

                            if (foundControl == null) {
                                // Still not found a control. Make sure the bounds of this
                                // xforms:repeat are correct for the rest of the visit.

                                final int adjustedNewIndex;
                                {
                                    final int newIndex = ((Integer) currentControlsState.getRepeatIdToIndex().get(repeatId)).intValue();

                                    // Adjust bounds if necessary
                                    if (newIndex < 1)
                                        adjustedNewIndex = 1;
                                    else if (newIndex > repeatNodeSet.size())
                                        adjustedNewIndex = repeatNodeSet.size();
                                    else
                                        adjustedNewIndex = newIndex;
                                }

                                // Set index
                                currentControlsState.updateRepeatIndex(repeatId, adjustedNewIndex);
                            }

                        } else {
                            // Make sure the index is set to zero when the node-set is empty
                            currentControlsState.updateRepeatIndex(repeatId, 0);
                        }
                    } else {
                        // This is a child xforms:repeat of a matching xforms:repeat
                        // Second step: update non-empty repeat indexes to the appropriate value

                        // "The indexes for inner nested repeat collections are re-initialized to startindex."

                        // NOTE: We do this, but we also adjust the index:
                        // "The index for this repeating structure is initialized to the
                        // value of startindex. If the initial startindex is less than 1 it
                        // defaults to 1. If the index is greater than the initial node-set
                        // then it defaults to the size of the node-set."

                        if (repeatNodeSet != null && repeatNodeSet.size() > 0) {
                            int newIndex = repeatControlInfo.getStartIndex();

                            if (newIndex < 1)
                                newIndex = 1;
                            if (newIndex > repeatNodeSet.size())
                                newIndex = repeatNodeSet.size();

                            currentControlsState.updateRepeatIndex(repeatId, newIndex);
                        } else {
                            // Make sure the index is set to zero when the node-set is empty
                            // (although this should already have been done above by the
                            // enclosing xforms:repeat)
                            currentControlsState.updateRepeatIndex(repeatId, 0);
                        }
                    }
                }
            }

            public void endVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    if (foundControl == controlInfo)
                        foundControl = null;
                }
            }
        });
    }

    /**
     * Adjust indexes after a deletion.
     *
     * @param pipelineContext
     * @param xformsControls
     * @param previousRepeatIdToIndex
     * @param repeatIndexUpdates
     * @param nestedRepeatIndexUpdates
     * @param nodeToRemove
     */
    public static void adjustIndexesForDelete(PipelineContext pipelineContext, final XFormsControls xformsControls,
                                              final Map previousRepeatIdToIndex, final Map repeatIndexUpdates,
                                              final Map nestedRepeatIndexUpdates, final Node nodeToRemove) {

        // NOTE: The code below assumes that there are no nested repeats bound to node-sets that intersect
        xformsControls.getCurrentControlsState().visitControlInfoFollowRepeats(pipelineContext, xformsControls, new XFormsControls.ControlInfoVisitorListener() {

            private ControlInfo foundControl;
            private boolean reinitializeInner;

            public void startVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    // Found an xforms:repeat
                    final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) controlInfo;
                    final String repeatId = repeatControlInfo.getOriginalId();

                    final List repeatNodeSet = xformsControls.getCurrentNodeset();
                    if (foundControl == null) {
                        // We are not yet inside a matching xforms:repeat

                        if (repeatNodeSet != null && repeatNodeSet.size() > 0) {
                            // Find whether one node of the repeat node-set contains the inserted node
                            for (Iterator i = repeatNodeSet.iterator(); i.hasNext();) {
                                final NodeInfo currentNode = (NodeInfo) i.next();
                                if (((NodeWrapper) currentNode).getUnderlyingNode() == nodeToRemove) {
                                    // Found xforms:repeat affected by the change

                                    final int newIndex;
                                    if (repeatNodeSet.size() == 1) {
                                        // Delete the last element of the collection: the index must be set to 0
                                        newIndex = 0;
                                        reinitializeInner = false;
                                    } else {
                                        // Current index for this repeat
                                        final int currentIndex = ((Integer) previousRepeatIdToIndex.get(repeatId)).intValue();

                                        // Index of deleted element for this repeat
                                        final int deletionIndexInRepeat = repeatNodeSet.indexOf(nodeToRemove) + 1;

                                        if (currentIndex == deletionIndexInRepeat) {
                                            if (deletionIndexInRepeat == repeatNodeSet.size()) {

                                                // o "When the last remaining item in the collection is removed,
                                                // the index position becomes 0."

                                                // o "When the index was pointing to the deleted node, which was
                                                // the last item in the collection, the index will point to the new
                                                // last node of the collection and the index of inner repeats is
                                                // reinitialized."

                                                newIndex = currentIndex - 1;
                                                reinitializeInner = true;
                                            } else {
                                                // o "When the index was pointing to the deleted node, which was
                                                // not the last item in the collection, the index position is not
                                                // changed and the index of inner repeats is re-initialized."

                                                newIndex = currentIndex;
                                                reinitializeInner = true;
                                            }
                                        } else {
                                            // "The index should point to the same node
                                            // after a delete as it did before the delete"

                                            if (currentIndex < deletionIndexInRepeat) {
                                                newIndex = currentIndex;
                                            } else {
                                                newIndex = currentIndex - 1;
                                            }
                                            reinitializeInner = false;
                                        }
                                    }

                                    repeatIndexUpdates.put(repeatId, new Integer(newIndex));

                                    // Handle children
                                    if (reinitializeInner) {
                                        // First step: set all children indexes to 0
                                        final List nestedRepeatIds = xformsControls.getCurrentControlsState().getNestedRepeatIds(xformsControls, repeatId);
                                        if (nestedRepeatIds != null) {
                                            for (Iterator j = nestedRepeatIds.iterator(); j.hasNext();) {
                                                final String nestedRepeatId = (String) j.next();
                                                repeatIndexUpdates.put(nestedRepeatId, new Integer(0));
                                                nestedRepeatIndexUpdates.put(nestedRepeatId, "");
                                            }
                                        }
                                    }

                                    foundControl = controlInfo;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            public void endVisitControl(ControlInfo controlInfo) {
                if (controlInfo instanceof RepeatControlInfo) {
                    if (foundControl == controlInfo)
                        foundControl = null;
                }
            }
        });
    }
}
