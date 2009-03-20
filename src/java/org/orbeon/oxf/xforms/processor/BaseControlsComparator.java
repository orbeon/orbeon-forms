/**
 *  Copyright (C) 2005-2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSwitchControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;
import java.util.Iterator;
import java.util.Map;

public abstract class BaseControlsComparator implements ControlsComparator {

    public static final boolean DEFAULT_RELEVANCE_FOR_NEW_ITERATION = true;

    protected final PipelineContext pipelineContext;
    protected final ContentHandlerHelper ch;
    protected final XFormsContainingDocument containingDocument;
    protected final Map itemsetsFull1;
    protected final Map itemsetsFull2;
    protected final Map valueChangeControlIds;

    protected final boolean isStaticReadonly;

    public BaseControlsComparator(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {
        this.pipelineContext = pipelineContext;
        this.ch = ch;
        this.containingDocument = containingDocument;
        this.itemsetsFull1 = itemsetsFull1;
        this.itemsetsFull2 = itemsetsFull2;
        this.valueChangeControlIds = valueChangeControlIds;
        this.isStaticReadonly = XFormsProperties.isStaticReadonlyAppearance(containingDocument);
    }

    protected static boolean addAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            attributesImpl.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
            return true;
        }
    }

    protected static void outputDeleteRepeatTemplate(ContentHandlerHelper ch, XFormsControl xformsControl2, int count) {
        final String repeatControlId = xformsControl2.getEffectiveId();
        final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
        final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                new String[] { "id", templateId, "parent-indexes", parentIndexes, "count", "" + count });
    }

    protected static void outputCopyRepeatTemplate(ContentHandlerHelper ch, XFormsRepeatControl repeatControlInfo, int startSuffix, int endSuffix) {
        final String repeatControlId = repeatControlInfo.getEffectiveId();
        final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                new String[] {
                        // Get prefixed id without suffix as templates are global 
                        "id", XFormsUtils.getEffectiveIdNoSuffix(repeatControlInfo.getEffectiveId()),
                        "parent-indexes", parentIndexes,
                        "start-suffix", Integer.toString(startSuffix), "end-suffix", Integer.toString(endSuffix)
                });
    }

    protected void diffOutOfBand(XFormsControl xformsControl1, XFormsControl xformsControl2) {
        if (xformsControl2 instanceof XFormsSwitchControl) {
            // xforms:switch

            final XFormsSwitchControl switchControl1 = (XFormsSwitchControl) xformsControl1;
            final XFormsSwitchControl switchControl2 = (XFormsSwitchControl) xformsControl2;

            diffSwitches(switchControl1, switchControl2);

        } else if (xformsControl2 instanceof XFormsSelect1Control) {
            // xforms:select/xforms:select1

            final XFormsSelect1Control xformsSelect1Control1 = (XFormsSelect1Control) xformsControl1;
            final XFormsSelect1Control xformsSelect1Control2 = (XFormsSelect1Control) xformsControl2;// not null

            // Try to get static itemset info
            final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(xformsControl2.getPrefixedId());
            if (itemsInfo != null && !itemsInfo.hasNonStaticItem()) {
                // There is no need to send an update:
                //
                // 1. Items are static...
                // 2. ...and they have been outputted statically in the HTML page, directly or in repeat template
            } else if (!xformsControl2.isStaticReadonly()) {
                // There is a possible change
                // Don't update itemset for static readonly controls

                if (itemsetsFull1 != null && XFormsSingleNodeControl.isRelevant(xformsSelect1Control1)) {
                    final Object items = xformsSelect1Control1.getItemset(pipelineContext, true);
                    if (items != null)
                        itemsetsFull1.put(xformsSelect1Control1.getEffectiveId(), items);
                }

                if (itemsetsFull2 != null && XFormsSingleNodeControl.isRelevant(xformsSelect1Control2)) {
                    final Object items = xformsSelect1Control2.getItemset(pipelineContext, true);
                    if (items != null)
                        itemsetsFull2.put(xformsSelect1Control2.getEffectiveId(), items);
                }
            }
        }
    }

    protected void diffSwitches(XFormsSwitchControl switchControl1, XFormsSwitchControl switchControl2) {

        final String selectedCaseEffectiveId = switchControl2.getSelectedCase().getEffectiveId();

        // Only output the information if it has changed
        final String previousSelectedCaseId
                = (switchControl1 != null)
                    ? ((XFormsSwitchControl.XFormsSwitchControlLocal) switchControl1.getInitialLocal()).getSelectedCaseControl().getEffectiveId() : null;
        if (!selectedCaseEffectiveId.equals(previousSelectedCaseId)) {

            // Output selected case id
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                    "id", selectedCaseEffectiveId,
                    "visibility", "visible"
            });

            if (previousSelectedCaseId != null) {
                // Output deselected case ids
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                        "id", previousSelectedCaseId,
                        "visibility", "hidden"}
                );
            } else {
                // This is a new switch (can happen with repeat), send all deselected to be sure
                final List children = switchControl2.getChildren();
                if (children != null && children.size() > 0) {
                    for (Iterator j = children.iterator(); j.hasNext();) {
                        final XFormsControl caseControl = (XFormsControl) j.next();

                        if (!caseControl.getEffectiveId().equals(selectedCaseEffectiveId)) {
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                                    "id", caseControl.getEffectiveId(),
                                    "visibility", "hidden"
                            });
                        }
                    }
                }
            }
        }
    }

    protected void diffDialogs(XXFormsDialogControl dialogControl1, XXFormsDialogControl dialogControl2) {

        final String effectiveDialogId = dialogControl2.getEffectiveId();

        final boolean previousVisible = (dialogControl1 != null) ? dialogControl1.wasVisible() : false;
        // NOTE: We only compare on isVisible as we don't support just changing the neighbor for now
        if (dialogControl1 == null || previousVisible != dialogControl2.isVisible()) {

            final String neighbor = dialogControl2.getNeighborControlId();
            final boolean constrainToViewport = dialogControl2.isConstrainToViewport();

            // Output element
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] {
                    "id", effectiveDialogId,
                    "visibility", dialogControl2.isVisible() ? "visible" : "hidden",
                    (neighbor != null && dialogControl2.isVisible()) ? "neighbor" : null, neighbor,
                    dialogControl2.isVisible() ? "constrain" : null, Boolean.toString(constrainToViewport)
            });
        }
    }
}
