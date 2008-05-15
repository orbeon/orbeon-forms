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
package org.orbeon.oxf.xforms.control.controls;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction;
import org.orbeon.oxf.xforms.action.actions.XFormsInsertAction;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XXFormsDndEvent;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.saxon.om.NodeInfo;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Iterator;

/**
 * Represents an xforms:repeat container control.
 */
public class XFormsRepeatControl extends XFormsControl implements XFormsContainerControl {

    private int startIndex;

    public XFormsRepeatControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);

        // Store initial repeat index information
        final String startIndexString = element.attributeValue("startindex");
        this.startIndex = (startIndexString != null) ? Integer.parseInt(startIndexString) : 1;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public String getRepeatId() {
        return getId();
    }

    protected void evaluate(PipelineContext pipelineContext) {
        
        // For now, repeat does not support label, help, etc. so don't call super.evaluate()

        // Evaluate iterations
        final List children = getChildren();
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final RepeatIterationControl currentRepeatIteration = (RepeatIterationControl) i.next();
                currentRepeatIteration.evaluate(pipelineContext);
            }
        }
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_DND.equals(event.getEventName())) {

            // Only support this on DnD-enabled controls
            if (!isDnD())
                throw new ValidationException("Attempt to process xxforms-dnd event on non-DnD-enabled control: " + getEffectiveId(), getLocationData());

            // Perform DnD operation on node data
            final XXFormsDndEvent dndEvent = (XXFormsDndEvent) event;

            // Get all repeat iteration details
            final String[] dndStart = StringUtils.split(dndEvent.getDndStart(), '-');
            final String[] dndEnd = StringUtils.split(dndEvent.getDndEnd(), '-');

            // Find source information
            final List sourceNodeset;
            final int requestedSourceIndex;
            {
                sourceNodeset = getBindingContext().getNodeset();
                requestedSourceIndex = Integer.parseInt(dndStart[dndStart.length - 1]);

                if (requestedSourceIndex < 1 || requestedSourceIndex > sourceNodeset.size())
                    throw new ValidationException("Out of range Dnd start iteration: " + requestedSourceIndex, getLocationData());
            }

            // Find destination
            final List destinationNodeset;
            final int requestedDestinationIndex;
            {
                final String containingRepeatEffectiveId;
                if (dndEnd.length > 1) {
                    containingRepeatEffectiveId
                            = getId() + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1
                                + StringUtils.join(dndEnd, XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2, 0, dndEnd.length - 1);
                } else {
                    containingRepeatEffectiveId = getId();
                }


                final XFormsRepeatControl destinationControl = (XFormsRepeatControl) containingDocument.getObjectById(containingRepeatEffectiveId);
                destinationNodeset = destinationControl.getBindingContext().getNodeset();
                requestedDestinationIndex = Integer.parseInt(dndEnd[dndEnd.length - 1]);
            }

            // TODO: Detect DnD over repeat boundaries, and throw if not explicitly enabled

            // Delete node from source
            final List deletedNodes = XFormsDeleteAction.doDelete(pipelineContext, containingDocument, sourceNodeset, requestedSourceIndex);

            // Adjust destination collection to reflect new state
            final int deletedNodePosition = destinationNodeset.indexOf(deletedNodes.get(0));
            final int actualDestinationIndex;
            final String destinationPosition;
            if (deletedNodePosition != -1) {
                // Deleted node was part of the destination nodeset
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
            final NodeInfo insertContextNodeInfo = ((NodeInfo) deletedNodes.get(0)).getParent();
            XFormsInsertAction.doInsert(pipelineContext, containingDocument, destinationPosition, destinationNodeset, insertContextNodeInfo, deletedNodes, actualDestinationIndex);

        }
        super.performDefaultAction(pipelineContext, event);
    }

    public boolean isDnD() {
        final String dndAttribute = getControlElement().attributeValue(XFormsConstants.XXFORMS_DND_QNAME);
        return "true".equals(dndAttribute);
    }
}
