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
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Collections;

public class OldControlsComparator extends BaseControlsComparator {

    private PipelineContext pipelineContext;
    private ContentHandlerHelper ch;
    private XFormsContainingDocument containingDocument;
    private Map itemsetsFull1;
    private Map itemsetsFull2;
    private Map valueChangeControlIds;

    public OldControlsComparator(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {
        this.pipelineContext = pipelineContext;
        this.ch = ch;
        this.containingDocument = containingDocument;
        this.itemsetsFull1 = itemsetsFull1;
        this.itemsetsFull2 = itemsetsFull2;
        this.valueChangeControlIds = valueChangeControlIds;
    }

    public void diff(List state1, List state2) {

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
                // xforms:repeat doesn't need to be handled independently, iterations do it

                final XFormsSingleNodeControl xformsSingleNodeControl1 = (XFormsSingleNodeControl) xformsControl1;
                final XFormsSingleNodeControl xformsSingleNodeControl2 = (XFormsSingleNodeControl) xformsControl2;

                // Output diffs between controlInfo1 and controlInfo2
                final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.get(xformsSingleNodeControl2.getEffectiveId()) != null;
                if ((!xformsSingleNodeControl2.equalsExternal(pipelineContext, xformsSingleNodeControl1) || isValueChangeControl)
                        && !(isStaticReadonly && xformsSingleNodeControl2.isReadonly() && xformsSingleNodeControl2 instanceof XFormsTriggerControl)
                        && !(xformsSingleNodeControl2 instanceof XFormsGroupControl && XFormsGroupControl.INTERNAL_APPEARANCE.equals(xformsSingleNodeControl2.getAppearance()))) {
                    // Don't send anything if nothing has changed
                    // But we force a change for controls whose values changed in the request
                    // Also, we don't output anything for triggers in static readonly mode

                    attributesImpl.clear();

                    // Whether it is necessary to output information about this control
                    // TODO: distinction between new iteration AND control just becoming relevant
                    final boolean isNewRepeatIteration = xformsSingleNodeControl1 == null;

                    // Whether it is necessary to output information about this control
                    boolean doOutputElement = false;

                    // Control children values
                    final boolean isRepeatIterationControl = xformsSingleNodeControl2 instanceof RepeatIterationControl;
                    final boolean isAttributeControl = xformsSingleNodeControl2 instanceof XXFormsAttributeControl;
                    if (!(isRepeatIterationControl || isAttributeControl)) {
                        // Anything but a repeat iteration or an attribute

                        // Control id
                        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getEffectiveId());

                        // Model item properties
                        if (isNewRepeatIteration && xformsSingleNodeControl2.isReadonly()
                                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isReadonly() != xformsSingleNodeControl2.isReadonly()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                    XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isReadonly()));
                            doOutputElement = true;
                        }
                        if (isNewRepeatIteration && xformsSingleNodeControl2.isRequired()
                                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRequired() != xformsSingleNodeControl2.isRequired()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                    XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRequired()));
                            doOutputElement = true;
                        }


                        // Default for relevance
                        final boolean relevantDefault = isNewRepeatIteration;

                        if (isNewRepeatIteration && xformsSingleNodeControl2.isRelevant() != relevantDefault
                                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRelevant() != xformsSingleNodeControl2.isRelevant()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                    XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRelevant()));
                            doOutputElement = true;
                        }
                        if (isNewRepeatIteration && !xformsSingleNodeControl2.isValid()
                                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isValid() != xformsSingleNodeControl2.isValid()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                    XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isValid()));
                            doOutputElement = true;
                        }

                        // Type attribute
                        {

                            final String typeValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getType();
                            final String typeValue2 = xformsSingleNodeControl2.getType();

                            if (isNewRepeatIteration || !XFormsUtils.compareStrings(typeValue1, typeValue2)) {
                                final String attributeValue = typeValue2 != null ? typeValue2 : "";
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "type", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                            }
                        }

                        // Label, help, hint, alert, etc.
                        {
                            final String labelValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getLabel(pipelineContext);
                            final String labelValue2 = xformsSingleNodeControl2.getLabel(pipelineContext);

                            if (!XFormsUtils.compareStrings(labelValue1, labelValue2)) {
                                final String escapedLabelValue2 = xformsSingleNodeControl2.getEscapedLabel(pipelineContext);
                                final String attributeValue = escapedLabelValue2 != null ? escapedLabelValue2 : "";
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "label", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                            }
                        }

                        {
                            final String helpValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getHelp(pipelineContext);
                            final String helpValue2 = xformsSingleNodeControl2.getHelp(pipelineContext);

                            if (!XFormsUtils.compareStrings(helpValue1, helpValue2)) {
                                final String escapedHelpValue2 = xformsSingleNodeControl2.getEscapedHelp(pipelineContext);
                                final String attributeValue = escapedHelpValue2 != null ? escapedHelpValue2 : "";
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "help", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                            }
                        }

                        {
                            final String hintValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getHint(pipelineContext);
                            final String hintValue2 = xformsSingleNodeControl2.getHint(pipelineContext);

                            if (!XFormsUtils.compareStrings(hintValue1, hintValue2)) {
                                final String escapedHintValue2 = xformsSingleNodeControl2.getEscapedHint(pipelineContext);
                                final String attributeValue = escapedHintValue2 != null ? escapedHintValue2 : "";
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "hint", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                            }
                        }

                        {
                            final String alertValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getAlert(pipelineContext);
                            final String alertValue2 = xformsSingleNodeControl2.getAlert(pipelineContext);

                            if (!XFormsUtils.compareStrings(alertValue1, alertValue2)) {
                                final String escapedAlertValue2 = xformsSingleNodeControl2.getEscapedAlert(pipelineContext);
                                final String attributeValue = escapedAlertValue2 != null ? escapedAlertValue2 : "";
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "alert", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                            }
                        }

                        if (xformsSingleNodeControl2 instanceof XFormsOutputControl) {
                            // Output xforms:output-specific information
                            final XFormsOutputControl outputControlInfo1 = (XFormsOutputControl) xformsSingleNodeControl1;
                            final XFormsOutputControl outputControlInfo2 = (XFormsOutputControl) xformsSingleNodeControl2;

                            // Mediatype
                            final String mediatypeValue1 = (outputControlInfo1 == null) ? null : outputControlInfo1.getMediatypeAttribute();
                            final String mediatypeValue2 = outputControlInfo2.getMediatypeAttribute();

                            if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
                                final String attributeValue = mediatypeValue2 != null ? mediatypeValue2 : "";
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                            }
                        } else if (xformsSingleNodeControl2 instanceof XFormsUploadControl) {
                            // Output xforms:upload-specific information
                            final XFormsUploadControl uploadControlInfo1 = (XFormsUploadControl) xformsSingleNodeControl1;
                            final XFormsUploadControl uploadControlInfo2 = (XFormsUploadControl) xformsSingleNodeControl2;

                            {
                                // State
                                final String stateValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getState(pipelineContext);
                                final String stateValue2 = uploadControlInfo2.getState(pipelineContext);

                                if (!XFormsUtils.compareStrings(stateValue1, stateValue2)) {
                                    final String attributeValue = stateValue2 != null ? stateValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "state", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                                }
                            }
                            {
                                // Mediatype
                                final String mediatypeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getMediatype();
                                final String mediatypeValue2 = uploadControlInfo2.getMediatype();

                                if (!XFormsUtils.compareStrings(mediatypeValue1, mediatypeValue2)) {
                                    final String attributeValue = mediatypeValue2 != null ? mediatypeValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                                }
                            }
                            {
                                // Filename
                                final String filenameValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getFilename(pipelineContext);
                                final String filenameValue2 = uploadControlInfo2.getFilename(pipelineContext);

                                if (!XFormsUtils.compareStrings(filenameValue1, filenameValue2)) {
                                    final String attributeValue = filenameValue2 != null ? filenameValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "filename", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                                }
                            }
                            {
                                // Size
                                final String sizeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getSize(pipelineContext);
                                final String sizeValue2 = uploadControlInfo2.getSize(pipelineContext);

                                if (!XFormsUtils.compareStrings(sizeValue1, sizeValue2)) {
                                    final String attributeValue = sizeValue2 != null ? sizeValue2 : "";
                                    doOutputElement |= addAttributeIfNeeded(attributesImpl, "size", attributeValue,  isNewRepeatIteration, attributeValue.equals(""));
                                }
                            }
                        }

                        // Get current value if possible for this control
                        // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
                        // client not to update the value, unlike with attributes which can be omitted
                        if (xformsSingleNodeControl2 instanceof XFormsValueControl && !(xformsSingleNodeControl2 instanceof XFormsUploadControl)) {

                            // TODO: Output value only when changed

                            final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsSingleNodeControl2;

                            // Check if a "display-value" attribute must be added
                            final String displayValue = xformsValueControl.getDisplayValue(pipelineContext);
                            if (displayValue != null) {
                                doOutputElement |= addAttributeIfNeeded(attributesImpl, "display-value", displayValue,  isNewRepeatIteration, displayValue.equals(""));
                            }

                            // Create element with text value
                            final String value;
                            {
                                // Value may become null when controls are unbound
                                final String tempValue = xformsValueControl.getEscapedExternalValue(pipelineContext);
                                value = (tempValue == null) ? "" : tempValue;
                            }
                            if (doOutputElement || !isNewRepeatIteration || (isNewRepeatIteration && !value.equals(""))) {
                                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                ch.text(value);
                                ch.endElement();
                            }
                        } else {
                            // No value, just output element with no content (but there may be attributes)
                            if (doOutputElement)
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                        }
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
                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "for", effectiveFor2,  isNewRepeatIteration, false);
                        }

                        {
                            // Attribute name
                            final String name2 = attributeControlInfo2.getNameAttribute();
                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "name", name2,  isNewRepeatIteration, false);
                        }

                        final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsSingleNodeControl2;

                        // Create element with text value
                        final String value;
                        {
                            // Value may become null when controls are unbound
                            final String tempValue = xformsValueControl.getExternalValue(pipelineContext);
                            value = (tempValue == null) ? "" : tempValue;
                        }
                        if (doOutputElement || !isNewRepeatIteration || (isNewRepeatIteration && !value.equals(""))) {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", attributesImpl);
                            ch.text(value);
                            ch.endElement();
                        }
                    } else {
                        // Repeat iteration only handles relevance

                        // Use the effective id of the parent repeat
                        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getParent().getEffectiveId());

                        if (isNewRepeatIteration && !xformsSingleNodeControl2.isRelevant() // NOTE: we output if we are NOT relevant as the client must mark non-relevant elements
                                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRelevant() != xformsSingleNodeControl2.isRelevant()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                    XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRelevant()));
                            doOutputElement = true;
                        }

                        // Repeat iteration
                        if (doOutputElement) {
                            final RepeatIterationControl repeatIterationInfo = (RepeatIterationControl) xformsSingleNodeControl2;
                            attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIteration()));
    
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                        }
                    }
                }

                // Handle itemsets
                if (xformsSingleNodeControl2 instanceof XFormsSelect1Control) {
                    final XFormsSelect1Control xformsSelect1Control1 = (XFormsSelect1Control) xformsSingleNodeControl1;
                    final XFormsSelect1Control xformsSelect1Control2 = (XFormsSelect1Control) xformsSingleNodeControl2;// not null

                    // Try to get static itemset info
                    final XFormsStaticState.ItemsInfo itemsInfo = containingDocument.getStaticState().getItemsInfo(xformsSingleNodeControl2.getId());
                    if (xformsSelect1Control1 != null && itemsInfo != null && !itemsInfo.hasNonStaticItem() && xformsSelect1Control1.isRelevant() == xformsSelect1Control2.isRelevant()) {
                        // No update to send because the itemset can't change
                    } else {
                        // There is a possible change
                        if (itemsetsFull1 != null && xformsSelect1Control1 != null && xformsSelect1Control1.isRelevant()) {
                            final Object items = xformsSelect1Control1.getItemset(pipelineContext, true);
                            if (items != null)
                                itemsetsFull1.put(xformsSelect1Control1.getEffectiveId(), items);
                        }

                        if (itemsetsFull2 != null && xformsSelect1Control2.isRelevant()) {
                            final Object items = xformsSelect1Control2.getItemset(pipelineContext, true);
                            if (items != null)
                                itemsetsFull2.put(xformsSelect1Control2.getEffectiveId(), items);
                        }
                    }
                }
            }

            // 2: Check children if any
            if (XFormsControls.isGroupingControl(xformsControl2.getName()) || xformsControl2 instanceof RepeatIterationControl) {

                final List children1 = (xformsControl1 == null) ? null : xformsControl1.getChildren();
                final List children2 = (xformsControl2.getChildren() == null) ? Collections.EMPTY_LIST : xformsControl2.getChildren();

                // Repeat grouping control
                if (xformsControl2 instanceof XFormsRepeatControl && children1 != null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Special case of repeat update

                    final int size1 = children1.size();
                    final int size2 = children2.size();

                    if (size1 == size2) {
                        // No add or remove of children
                        diff(children1, xformsControl2.getChildren());
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
