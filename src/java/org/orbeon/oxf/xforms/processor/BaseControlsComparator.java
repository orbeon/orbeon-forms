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
package org.orbeon.oxf.xforms.processor;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.control.controls.XFormsSwitchControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

public abstract class BaseControlsComparator {

    protected final PipelineContext pipelineContext;
    protected final ContentHandlerHelper ch;
    protected final XFormsContainingDocument containingDocument;
    protected final Map<String, Itemset> itemsetsFull1;
    protected final Map<String, Itemset> itemsetsFull2;
    protected final Set<String> valueChangeControlIds;
    protected final boolean isTestMode;

    protected final boolean isStaticReadonly;

    public BaseControlsComparator(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument,
                                  Map<String, Itemset> itemsetsFull1, Map<String, Itemset> itemsetsFull2,
                                  Set<String> valueChangeControlIds, boolean isTestMode) {
        this.pipelineContext = pipelineContext;
        this.ch = ch;
        this.containingDocument = containingDocument;
        this.itemsetsFull1 = itemsetsFull1;
        this.itemsetsFull2 = itemsetsFull2;
        this.valueChangeControlIds = valueChangeControlIds;
        this.isStaticReadonly = XFormsProperties.isStaticReadonlyAppearance(containingDocument);
        this.isTestMode = isTestMode;
    }

    protected static boolean addOrAppendToAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            XMLUtils.addOrAppendToAttribute(attributesImpl, name, value);
            return true;
        }
    }

    protected void outputDeleteRepeatTemplate(ContentHandlerHelper ch, XFormsControl xformsControl2, int count) {
        if (!isTestMode) {
            final String repeatControlId = xformsControl2.getEffectiveId();
            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
            final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                    new String[] { "id", templateId, "parent-indexes", parentIndexes, "count", "" + count });
        }
    }

    protected void outputCopyRepeatTemplate(ContentHandlerHelper ch, XFormsRepeatControl repeatControlInfo, int startSuffix, int endSuffix) {
        if (!isTestMode) {
            final String repeatControlId = repeatControlInfo.getEffectiveId();
            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                    new String[] {
                            // Get prefixed id without suffix as templates are global
                            "id", repeatControlInfo.getPrefixedId(),
                            "parent-indexes", parentIndexes,
                            "start-suffix", Integer.toString(startSuffix), "end-suffix", Integer.toString(endSuffix)
                    });
        }
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
                    final Itemset itemset = xformsSelect1Control1.getItemset(pipelineContext, true);
                    if (itemset != null)
                        itemsetsFull1.put(xformsSelect1Control1.getEffectiveId(), itemset);
                }

                if (itemsetsFull2 != null && XFormsSingleNodeControl.isRelevant(xformsSelect1Control2)) {
                    final Itemset itemset = xformsSelect1Control2.getItemset(pipelineContext, true);
                    if (itemset != null)
                        itemsetsFull2.put(xformsSelect1Control2.getEffectiveId(), itemset);
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
                final List<XFormsControl> children = switchControl2.getChildren();
                if (children != null && children.size() > 0) {
                    for (final XFormsControl caseControl: children) {
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

        final boolean previousVisible = (dialogControl1 != null) && dialogControl1.wasVisible();
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

    // public for unit tests
    public static boolean diffCustomMIPs(AttributesImpl attributesImpl, XFormsSingleNodeControl xformsSingleNodeControl1,
                                         XFormsSingleNodeControl xformsSingleNodeControl2, boolean newlyVisibleSubtree, boolean doOutputElement) {
        final Map<String, String> customMIPs1 = (xformsSingleNodeControl1 == null) ? null : xformsSingleNodeControl1.getCustomMIPs();
        final Map<String, String> customMIPs2 = xformsSingleNodeControl2.getCustomMIPs();

        if (newlyVisibleSubtree || !XFormsSingleNodeControl.compareCustomMIPs(customMIPs1, customMIPs2)) {
            // Custom MIPs changed

            final String attributeValue;
            if (customMIPs1 == null) {
                attributeValue = xformsSingleNodeControl2.getCustomMIPsClasses();
            } else {
                final StringBuilder sb = new StringBuilder(100);

                // Classes to remove
                for (final Map.Entry<String, String> entry: customMIPs1.entrySet()) {
                    final String name = entry.getKey();
                    final String value = entry.getValue();

                    // customMIPs2 may be null if the control becomes no longer bound
                    final String newValue = (customMIPs2 == null) ? null : customMIPs2.get(name);
                    if (newValue == null || !value.equals(newValue)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('-');
                        // TODO: encode so that there are no spaces
                        sb.append(name);
                        sb.append('-');
                        sb.append(value);
                    }
                }

                // Classes to add
                // customMIPs2 may be null if the control becomes no longer bound
                if (customMIPs2 != null) {
                    for (final Map.Entry<String, String> entry: customMIPs2.entrySet()) {
                        final String name = entry.getKey();
                        final String value = entry.getValue();

                        final String oldValue = customMIPs1.get(name);
                        if (oldValue == null || !value.equals(oldValue)) {

                            if (sb.length() > 0)
                                sb.append(' ');

                            sb.append('+');
                            // TODO: encode so that there are no spaces
                            sb.append(name);
                            sb.append('-');
                            sb.append(value);
                        }
                    }
                }

                attributeValue = sb.toString();
            }
            // This attribute is a space-separate list of class names prefixed with either '-' or '+'
            if (attributeValue != null)
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue.equals(""));
        }
        return doOutputElement;
    }

    // public for unit tests
    public static boolean diffClassAVT(AttributesImpl attributesImpl, XFormsControl control1, XFormsControl control2,
                                       boolean newlyVisibleSubtree, boolean doOutputElement) {

        final String class1 = (control1 == null) ? null : control1.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);
        final String class2 = control2.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);

        if (newlyVisibleSubtree || !XFormsUtils.compareStrings(class1, class2)) {
            // Custom MIPs changed

            final String attributeValue;
            if (class1 == null) {
                attributeValue = class2;
            } else {
                final StringBuilder sb = new StringBuilder(100);

                final Set<String> classes1 = tokenize(class1);
                final Set<String> classes2 = tokenize(class2);

                // Classes to remove
                for (final String currentClass: classes1) {
                    if (!classes2.contains(currentClass)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('-');
                        sb.append(currentClass);
                    }
                }

                // Classes to add
                for (final String currentClass: classes2) {
                    if (!classes1.contains(currentClass)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('+');
                        sb.append(currentClass);
                    }
                }

                attributeValue = sb.toString();
            }
            // This attribute is a space-separate list of class names prefixed with either '-' or '+'
            if (attributeValue != null)
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue.equals(""));
        }
        return doOutputElement;
    }

    private static Set<String> tokenize(String value) {
        final Set<String> result;
        if (value != null) {
            result = new LinkedHashSet<String>();
            for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
                result.add(st.nextToken());
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }
}
