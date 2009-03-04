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

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class OldControlsComparator extends BaseControlsComparator {

    public OldControlsComparator(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {
        super(pipelineContext, ch, containingDocument, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
    }

    public void diff(List state1, List state2) {

        // Normalize
        if (state1 != null && state1.size() == 0)
            state1 = null;
        if (state2 != null && state2.size() == 0)
            state2 = null;

        // Trivial case
        if (state1 == null && state2 == null)
            return;

        // Both lists must have the same size if present; state1 can be null
        if ((state1 != null && state2 != null && state1.size() != state2.size()) || (state2 == null)) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        final boolean isStaticReadonly = XFormsProperties.isStaticReadonlyAppearance(containingDocument);
        final AttributesImpl attributesImpl = new AttributesImpl();
        final Iterator j = (state1 == null) ? null : state1.iterator();
        for (Iterator i = state2.iterator(); i.hasNext();) {
            final XFormsControl xformsControl1 = (state1 == null) ? null : (XFormsControl) j.next();
            final XFormsControl xformsControl2 = (XFormsControl) i.next();

            // 1: Check current control
            if (xformsControl2 instanceof XFormsSingleNodeControl) {
                // NOTE: xforms:repeat doesn't need to be handled independently, iterations do it

                final XFormsSingleNodeControl xformsSingleNodeControl1 = (XFormsSingleNodeControl) xformsControl1;
                final XFormsSingleNodeControl xformsSingleNodeControl2 = (XFormsSingleNodeControl) xformsControl2;

                if (!(isStaticReadonly && xformsSingleNodeControl2.isReadonly() && xformsSingleNodeControl2 instanceof XFormsTriggerControl)
                            && !(xformsSingleNodeControl2 instanceof XFormsGroupControl && XFormsGroupControl.INTERNAL_APPEARANCE.equals(xformsSingleNodeControl2.getAppearance()))) {
                    // Output diffs between controlInfo1 and controlInfo2
                    final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.get(xformsSingleNodeControl2.getEffectiveId()) != null;
                    if ((!xformsSingleNodeControl2.equalsExternal(pipelineContext, xformsSingleNodeControl1) || isValueChangeControl)) {
                        // Don't send anything if nothing has changed
                        // But we force a change for controls whose values changed in the request
                        // Also, we don't output anything for triggers in static readonly mode

                        attributesImpl.clear();

                        // Whether it is necessary to output information about this control because the control was previously non-existing
                        // TODO: distinction between new iteration AND control just becoming relevant
                        final boolean isNewlyVisibleSubtree = xformsSingleNodeControl1 == null;

                        // Whether it is necessary to output information about this control
                        boolean doOutputElement = false;

                        // Control children values
                        final boolean isRepeatIterationControl = xformsSingleNodeControl2 instanceof XFormsRepeatIterationControl;
                        final boolean isAttributeControl = xformsSingleNodeControl2 instanceof XXFormsAttributeControl;
                        final boolean isTextControl = xformsSingleNodeControl2 instanceof XXFormsTextControl;
                        if (!(isRepeatIterationControl || isAttributeControl || isTextControl)) {
                            // Anything but a repeat iteration, an attribute or a text

                            // Control id
                            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getEffectiveId());

                            // Model item properties
                            if (isNewlyVisibleSubtree && xformsSingleNodeControl2.isReadonly()
                                    || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isReadonly() != xformsSingleNodeControl2.isReadonly()) {
                                attributesImpl.addAttribute("", XFormsConstants.READONLY_ATTRIBUTE_NAME,
                                        XFormsConstants.READONLY_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isReadonly()));
                                doOutputElement = true;
                            }
                            if (isNewlyVisibleSubtree && xformsSingleNodeControl2.isRequired()
                                    || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRequired() != xformsSingleNodeControl2.isRequired()) {
                                attributesImpl.addAttribute("", XFormsConstants.REQUIRED_ATTRIBUTE_NAME,
                                        XFormsConstants.REQUIRED_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRequired()));
                                doOutputElement = true;
                            }


                            // Default for relevance
                            if (isNewlyVisibleSubtree && xformsSingleNodeControl2.isRelevant() != DEFAULT_RELEVANCE_FOR_NEW_ITERATION
                                    //|| XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl1) != XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl2)) {
                                    || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRelevant() != xformsSingleNodeControl2.isRelevant()) {//TODO: not sure why the above alternative fails tests. Which is more correct?
                                attributesImpl.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                                        XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRelevant()));
                                doOutputElement = true;
                            }
                            if (isNewlyVisibleSubtree && !xformsSingleNodeControl2.isValid()
                                    || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isValid() != xformsSingleNodeControl2.isValid()) {
                                attributesImpl.addAttribute("", XFormsConstants.VALID_ATTRIBUTE_NAME,
                                        XFormsConstants.VALID_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isValid()));
                                doOutputElement = true;
                            }

                            // Type attribute
                            {

                                final String typeValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getType();
                                final String typeValue2 = xformsSingleNodeControl2.getType();

                                if (isNewlyVisibleSubtree || !XFormsUtils.compareStrings(typeValue1, typeValue2)) {
                                    final String attributeValue = typeValue2 != null ? typeValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "type", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
                                }
                            }

                            // Label, help, hint, alert, etc.
                            {
                                final String labelValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getLabel(pipelineContext);
                                final String labelValue2 = xformsSingleNodeControl2.getLabel(pipelineContext);

                                if (!XFormsUtils.compareStrings(labelValue1, labelValue2)) {
                                    final String escapedLabelValue2 = xformsSingleNodeControl2.getEscapedLabel(pipelineContext);
                                    final String attributeValue = escapedLabelValue2 != null ? escapedLabelValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "label", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
                                }
                            }

                            {
                                final String helpValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getHelp(pipelineContext);
                                final String helpValue2 = xformsSingleNodeControl2.getHelp(pipelineContext);

                                if (!XFormsUtils.compareStrings(helpValue1, helpValue2)) {
                                    final String escapedHelpValue2 = xformsSingleNodeControl2.getEscapedHelp(pipelineContext);
                                    final String attributeValue = escapedHelpValue2 != null ? escapedHelpValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "help", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
                                }
                            }

                            {
                                final String hintValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getHint(pipelineContext);
                                final String hintValue2 = xformsSingleNodeControl2.getHint(pipelineContext);

                                if (!XFormsUtils.compareStrings(hintValue1, hintValue2)) {
                                    final String escapedHintValue2 = xformsSingleNodeControl2.getEscapedHint(pipelineContext);
                                    final String attributeValue = escapedHintValue2 != null ? escapedHintValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "hint", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
                                }
                            }

                            {
                                final String alertValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getAlert(pipelineContext);
                                final String alertValue2 = xformsSingleNodeControl2.getAlert(pipelineContext);

                                if (!XFormsUtils.compareStrings(alertValue1, alertValue2)) {
                                    final String escapedAlertValue2 = xformsSingleNodeControl2.getEscapedAlert(pipelineContext);
                                    final String attributeValue = escapedAlertValue2 != null ? escapedAlertValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "alert", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
                                }
                            }

                            // Output control-specific attributes
                            doOutputElement |= xformsSingleNodeControl2.addAttributesDiffs(pipelineContext, xformsSingleNodeControl1, attributesImpl, isNewlyVisibleSubtree);

                            // Get current value if possible for this control
                            // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
                            // client not to update the value, unlike with attributes which can be omitted
                            if (xformsSingleNodeControl2 instanceof XFormsValueControl && !(xformsSingleNodeControl2 instanceof XFormsUploadControl)) {

                                // TODO: Output value only when changed

                                final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsSingleNodeControl2;

                                // Check if a "display-value" attribute must be added
    //                            final String displayValue = xformsValueControl.getDisplayValue(pipelineContext);
    //                            if (displayValue != null) {
    //                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "display-value", displayValue, isNewRepeatIteration, displayValue.equals(""));
    //                            }

                                // Create element with text value
                                final String value;
                                {
                                    // Value may become null when controls are unbound
                                    final String tempValue = xformsValueControl.getEscapedExternalValue(pipelineContext);
                                    value = (tempValue == null) ? "" : tempValue;
                                }
                                if (doOutputElement || !isNewlyVisibleSubtree || (isNewlyVisibleSubtree && !value.equals(""))) {
                                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                    ch.text(value);
                                    ch.endElement();
                                }
                            } else {
                                // No value, just output element with no content (but there may be attributes)
                                if (doOutputElement)
                                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                            }

                            // Output extension attributes in no namespace
                            // TODO: If only some attributes changed, then we also output xxf:control above, which is unnecessary
                            xformsSingleNodeControl2.addAttributesDiffs(xformsSingleNodeControl1, ch, isNewlyVisibleSubtree);

                        } else if (isAttributeControl) {
                            // Attribute control
                            final XXFormsAttributeControl attributeControlInfo2 = (XXFormsAttributeControl) xformsSingleNodeControl2;

                            // Control id
                            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getEffectiveId());

                            // The client does not store an HTML representation of the xxforms:attribute control, so we
                            // have to output these attributes.
                            {
                                // HTML element id
                                final String effectiveFor2 = attributeControlInfo2.getEffectiveForAttribute();
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "for", effectiveFor2, isNewlyVisibleSubtree, false);
                            }

                            {
                                // Attribute name
                                final String name2 = attributeControlInfo2.getNameAttribute();
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "name", name2, isNewlyVisibleSubtree, false);
                            }

                            final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsSingleNodeControl2;

                            // Create element with text value
                            final String value;
                            {
                                // Value may become null when controls are unbound
                                final String tempValue = xformsValueControl.getEscapedExternalValue(pipelineContext);
                                value = (tempValue == null) ? "" : tempValue;
                            }
                            if (doOutputElement || !isNewlyVisibleSubtree || (isNewlyVisibleSubtree && !value.equals(""))) {
                                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", attributesImpl);
                                ch.text(value);
                                ch.endElement();
                            }
                        } else if (isTextControl) {
                            // Text control
                            final XXFormsTextControl txtControlInfo2 = (XXFormsTextControl) xformsSingleNodeControl2;

                            // Control id
                            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getEffectiveId());

                            // The client does not store an HTML representation of the xxforms:text control, so we
                            // have to output these attributes.
                            {
                                // HTML element id
                                final String effectiveFor2 = txtControlInfo2.getEffectiveForAttribute();
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "for", effectiveFor2, isNewlyVisibleSubtree, false);
                            }

                            final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsSingleNodeControl2;

                            // Create element with text value
                            final String value;
                            {
                                // Value may become null when controls are unbound
                                final String tempValue = xformsValueControl.getEscapedExternalValue(pipelineContext);
                                value = (tempValue == null) ? "" : tempValue;
                            }
                            if (doOutputElement || !isNewlyVisibleSubtree || (isNewlyVisibleSubtree && !value.equals(""))) {
                                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "text", attributesImpl);
                                ch.text(value);
                                ch.endElement();
                            }
                        } else {
                            // Repeat iteration only handles relevance

                            // Use the effective id of the parent repeat
                            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getParent().getEffectiveId());

                            if (isNewlyVisibleSubtree && !xformsSingleNodeControl2.isRelevant() // NOTE: we output if we are NOT relevant as the client must mark non-relevant elements
                                    //|| XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl1) != XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl2)) {
                                    || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRelevant() != xformsSingleNodeControl2.isRelevant()) {//TODO: not sure why the above alternative fails tests. Which is more correct?
                                attributesImpl.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                                        XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRelevant()));
                                doOutputElement = true;
                            }

                            // Repeat iteration
                            if (doOutputElement) {
                                final XFormsRepeatIterationControl repeatIterationInfo = (XFormsRepeatIterationControl) xformsSingleNodeControl2;
                                attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIterationIndex()));

                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                            }
                        }
                    }

                    // Handle out of band differences
                    diffOutOfBand(xformsControl1, xformsControl2);
                }
            } else if (xformsControl2 instanceof XXFormsDialogControl) {
                // Out of band xxforms:dialog differences

                final XXFormsDialogControl dialogControl1 = (XXFormsDialogControl) xformsControl1;
                final XXFormsDialogControl dialogControl2 = (XXFormsDialogControl) xformsControl2;

                diffDialogs(dialogControl1, dialogControl2);
            }

            // 2: Check children if any
            if (xformsControl2 instanceof XFormsContainerControl) {

                final XFormsContainerControl containerControl1 = (XFormsContainerControl) xformsControl1;
                final XFormsContainerControl containerControl2 = (XFormsContainerControl) xformsControl2;

                final List children1 = (containerControl1 == null) ? null : containerControl1.getChildren();
                final List children2 = (containerControl2.getChildren() == null) ? Collections.EMPTY_LIST : containerControl2.getChildren();

                // Repeat grouping control
                if (xformsControl2 instanceof XFormsRepeatControl && children1 != null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Special case of repeat update

                    final int size1 = children1.size();
                    final int size2 = children2.size();

                    if (size1 == size2) {
                        // No add or remove of children
                        diff(children1, children2);
                    } else if (size2 > size1) {
                        // Size has grown

                        // Copy template instructions
                        outputCopyRepeatTemplate(ch, repeatControlInfo, size1 + 1, size2);

                        // Diff the common subset
                        diff(children1, children2.subList(0, size1));

                        // Issue new values for new iterations
                        diff(null, children2.subList(size1, size2));

                    } else if (size2 < size1) {
                        // Size has shrunk
                        outputDeleteRepeatTemplate(ch, xformsControl2, size1 - size2);

                        // Diff the remaining subset
                        diff(children1.subList(0, size2), children2);
                    }

                } else if (xformsControl2 instanceof XFormsRepeatControl && xformsControl1 == null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Handle new sub-xforms:repeat

                    // Copy template instructions
                    final int size2 = children2.size();
                    if (size2 > 1) {
                        outputCopyRepeatTemplate(ch, repeatControlInfo, 2, size2);// don't copy the first template, which is already copied when the parent is copied
                    } else if (size2 == 1) {
                        // NOP, the client already has the template copied
                    } else if (size2 == 0) {
                        // Delete first template
                        outputDeleteRepeatTemplate(ch, xformsControl2, 1);
                    }

                    // Issue new values for the children
                    diff(null, children2);

                } else if (xformsControl2 instanceof XFormsRepeatControl && children1 == null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Handle repeat growing from size 0 (case of instance replacement, for example)

                    // Copy template instructions
                    final int size2 = children2.size();
                    if (size2 > 0) {
                        outputCopyRepeatTemplate(ch, repeatControlInfo, 1, size2);

                        // Issue new values for the children
                        diff(null, children2);
                    }
                } else {
                    // Other grouping controls
                    diff(children1, children2);
                }
            }
        }
    }
}
