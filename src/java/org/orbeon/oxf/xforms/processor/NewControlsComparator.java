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
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.xml.sax.helpers.AttributesImpl;

import java.util.List;
import java.util.Map;
import java.util.Iterator;

public class NewControlsComparator extends BaseControlsComparator {

    private PipelineContext pipelineContext;
    private ContentHandlerHelper ch;
    private XFormsContainingDocument containingDocument;
    private Map itemsetsFull1;
    private Map itemsetsFull2;
    private Map valueChangeControlIds;

    public NewControlsComparator(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {
        this.pipelineContext = pipelineContext;
        this.ch = ch;
        this.containingDocument = containingDocument;
        this.itemsetsFull1 = itemsetsFull1;
        this.itemsetsFull2 = itemsetsFull2;
        this.valueChangeControlIds = valueChangeControlIds;
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

        // Only state1 can be null
//        if (state2 == null) {
//            throw new IllegalStateException("Illegal state when comparing controls.");
//        }
        // Both lists should have the same size if present, except when grouping controls become relevant/non-relevant,
        // in which case one of the containing controls may contain 0 children
        if (state1 != null && state2 != null && state1.size() != state2.size()) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        {
            final boolean isStaticReadonly = containingDocument.getReadonlyAppearance().equals(XFormsConstants.XXFORMS_READONLY_APPEARANCE_STATIC_VALUE);
            final AttributesImpl attributesImpl = new AttributesImpl();

            final Iterator leftIterator = (state1 == null) ? null : state1.iterator();
            final Iterator rightIterator = (state2 == null) ? null : state2.iterator();
            final Iterator leadingIterator = (rightIterator != null) ? rightIterator : leftIterator;

            while (leadingIterator.hasNext()) {
                final XFormsControl xformsControl1 = (leftIterator == null) ? null : (XFormsControl) leftIterator.next();
                final XFormsControl xformsControl2 = (rightIterator == null) ? null : (XFormsControl) rightIterator.next();

                final XFormsControl leadingControl = (xformsControl2 != null) ? xformsControl2 : xformsControl1;

                // 1: Check current control
                if (leadingControl instanceof XFormsSingleNodeControl) {
                    // xforms:repeat doesn't need to be handled independently, iterations do it

                    final XFormsSingleNodeControl xformsSingleNodeControl1 = (XFormsSingleNodeControl) xformsControl1;
                    final XFormsSingleNodeControl xformsSingleNodeControl2 = (XFormsSingleNodeControl) xformsControl2;

                    // Output diffs between controlInfo1 and controlInfo2

                    // Control id
                    attributesImpl.clear();
                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, leadingControl.getEffectiveId());

                    final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.get(leadingControl.getEffectiveId()) != null;
                    if (xformsSingleNodeControl2 != null) {

                        if ((!xformsSingleNodeControl2.equals(xformsSingleNodeControl1) || isValueChangeControl)
                                && !(isStaticReadonly && xformsSingleNodeControl2.isReadonly() && xformsSingleNodeControl2 instanceof XFormsTriggerControl)
                                && !(xformsSingleNodeControl2 instanceof XFormsGroupControl && XFormsGroupControl.INTERNAL_APPEARANCE.equals(xformsSingleNodeControl2.getAppearance()))) {
                            // Don't send anything if nothing has changed
                            // But we force a change for controls whose values changed in the request
                            // Also, we don't output anything for triggers in static readonly mode

                            // Whether it is necessary to output information about this control
                            final boolean isNewRepeatIteration = xformsSingleNodeControl1 == null;

                            // Whether it is necessary to output information about this control
                            boolean doOutputElement = false;

                            if (!(xformsSingleNodeControl2 instanceof RepeatIterationControl)) {
                                // Anything but a repeat iteration

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
                                if (isNewRepeatIteration && xformsSingleNodeControl2.isRelevant() // NOTE: we output if we ARE relevant as the default is non-relevant
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

                                // Notify the client that this control must be static readonly in case it just appeared
                                if (isNewRepeatIteration && xformsSingleNodeControl2.isStaticReadonly() && xformsSingleNodeControl2.isRelevant())
                                    attributesImpl.addAttribute("", "static", "static", ContentHandlerHelper.CDATA, "true");

                                // Type attribute
                                final boolean isOutputControlWithValueAttribute = xformsSingleNodeControl2 instanceof XFormsOutputControl && ((XFormsOutputControl) xformsSingleNodeControl2).getValueAttribute() != null;
                                if (!isOutputControlWithValueAttribute) {

                                    final String typeValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getType();
                                    final String typeValue2 = xformsSingleNodeControl2.getType();

                                    if (!((typeValue1 == null && typeValue2 == null) || (typeValue1 != null && typeValue2 != null && typeValue1.equals(typeValue2)))) {
                                        final String attributeValue = typeValue2 != null ? typeValue2 : "";
                                        doOutputElement |= addAttributeIfNeeded(attributesImpl, "type", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                    }
                                }

                                // Label, help, hint, alert, etc.
                                {
                                    final String labelValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getLabel(pipelineContext);
                                    final String labelValue2 = xformsSingleNodeControl2.getLabel(pipelineContext);

                                    if (!((labelValue1 == null && labelValue2 == null) || (labelValue1 != null && labelValue2 != null && labelValue1.equals(labelValue2)))) {
                                        final String escapedLabelValue2 = xformsSingleNodeControl2.getEscapedLabel(pipelineContext);
                                        final String attributeValue = escapedLabelValue2 != null ? escapedLabelValue2 : "";
                                        doOutputElement |= addAttributeIfNeeded(attributesImpl, "label", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                    }
                                }

                                {
                                    final String helpValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getHelp(pipelineContext);
                                    final String helpValue2 = xformsSingleNodeControl2.getHelp(pipelineContext);

                                    if (!((helpValue1 == null && helpValue2 == null) || (helpValue1 != null && helpValue2 != null && helpValue1.equals(helpValue2)))) {
                                final String escapedHelpValue2 = xformsSingleNodeControl2.getEscapedHelp(pipelineContext);
                                final String attributeValue = escapedHelpValue2 != null ? escapedHelpValue2 : "";
                                        doOutputElement |= addAttributeIfNeeded(attributesImpl, "help", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                    }
                                }

                                {
                                    final String hintValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getHint(pipelineContext);
                                    final String hintValue2 = xformsSingleNodeControl2.getHint(pipelineContext);

                                    if (!((hintValue1 == null && hintValue2 == null) || (hintValue1 != null && hintValue2 != null && hintValue1.equals(hintValue2)))) {
                                        final String attributeValue = hintValue2 != null ? hintValue2 : "";
                                        doOutputElement |= addAttributeIfNeeded(attributesImpl, "hint", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                    }
                                }

                                {
                                    final String alertValue1 = isNewRepeatIteration ? null : xformsSingleNodeControl1.getAlert(pipelineContext);
                                    final String alertValue2 = xformsSingleNodeControl2.getAlert(pipelineContext);

                                    if (!((alertValue1 == null && alertValue2 == null) || (alertValue1 != null && alertValue2 != null && alertValue1.equals(alertValue2)))) {
                                        final String escapedAlertValue2 = xformsSingleNodeControl2.getEscapedAlert(pipelineContext);
                                        final String attributeValue = escapedAlertValue2 != null ? escapedAlertValue2 : "";
                                        doOutputElement |= addAttributeIfNeeded(attributesImpl, "alert", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                    }
                                }

                                if (xformsSingleNodeControl2 instanceof XFormsOutputControl) {
                                    // Output xforms:output-specific information
                                    final XFormsOutputControl outputControlInfo1 = (XFormsOutputControl) xformsSingleNodeControl1;
                                    final XFormsOutputControl outputControlInfo2 = (XFormsOutputControl) xformsSingleNodeControl2;

                                    final String mediaTypeValue1 = (outputControlInfo1 == null) ? null : outputControlInfo1.getMediatypeAttribute();
                                    final String mediaTypeValue2 = outputControlInfo2.getMediatypeAttribute();

                                    if (!((mediaTypeValue1 == null && mediaTypeValue2 == null) || (mediaTypeValue1 != null && mediaTypeValue2 != null && mediaTypeValue1.equals(mediaTypeValue2)))) {
                                        final String attributeValue = mediaTypeValue2 != null ? mediaTypeValue2 : "";
                                        doOutputElement |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                    }
                                } else if (xformsSingleNodeControl2 instanceof XFormsUploadControl) {
                                    // Output xforms:upload-specific information
                                    final XFormsUploadControl uploadControlInfo1 = (XFormsUploadControl) xformsSingleNodeControl1;
                                    final XFormsUploadControl uploadControlInfo2 = (XFormsUploadControl) xformsSingleNodeControl2;

                                    {
                                        // State
                                        final String stateValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getState(pipelineContext);
                                        final String stateValue2 = uploadControlInfo2.getState(pipelineContext);

                                        if (!((stateValue1 == null && stateValue2 == null) || (stateValue1 != null && stateValue2 != null && stateValue1.equals(stateValue2)))) {
                                            final String attributeValue = stateValue2 != null ? stateValue2 : "";
                                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "state", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                        }
                                    }
                                    {
                                        // Mediatype
                                        final String mediatypeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getMediatype();
                                        final String mediatypeValue2 = uploadControlInfo2.getMediatype();

                                        if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
                                            final String attributeValue = mediatypeValue2 != null ? mediatypeValue2 : "";
                                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "mediatype", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                        }
                                    }
                                    {
                                        // Filename
                                        final String filenameValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getFilename(pipelineContext);
                                        final String filenameValue2 = uploadControlInfo2.getFilename(pipelineContext);

                                        if (!((filenameValue1 == null && filenameValue2 == null) || (filenameValue1 != null && filenameValue2 != null && filenameValue1.equals(filenameValue2)))) {
                                            final String attributeValue = filenameValue2 != null ? filenameValue2 : "";
                                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "filename", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
                                        }
                                    }
                                    {
                                        // Size
                                        final String sizeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getSize(pipelineContext);
                                        final String sizeValue2 = uploadControlInfo2.getSize(pipelineContext);

                                        if (!((sizeValue1 == null && sizeValue2 == null) || (sizeValue1 != null && sizeValue2 != null && sizeValue1.equals(sizeValue2)))) {
                                            final String attributeValue = sizeValue2 != null ? sizeValue2 : "";
                                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "size", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
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
                                    if (!isOutputControlWithValueAttribute) {
                                        final String displayValue = xformsValueControl.getDisplayValue();
                                        if (displayValue != null) {
                                            doOutputElement |= addAttributeIfNeeded(attributesImpl, "display-value", displayValue, isNewRepeatIteration, displayValue.equals(""));
                                        }
                                    }

                                    // Create element with text value
                                    final String value;
                                    {
                                        // Value may become null when controls are unbound
                                        final String tempValue = xformsValueControl.getExternalValue();
                                        value = (tempValue == null) ? "" : tempValue;
                                    }
                                    if (doOutputElement || !isNewRepeatIteration || (isNewRepeatIteration && !value.equals(value))) {
                                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                        ch.text(value);
                                        ch.endElement();
                                    }
                                } else {
                                    // No value, just output element with no content
                                    if (doOutputElement)
                                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                }
                            } else {

                                // Repeat iteration only handles relevance
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
                            final XFormsSelect1Control xformsSelect1Control2 = (XFormsSelect1Control) xformsSingleNodeControl2;

                            if (itemsetsFull1 != null && xformsSelect1Control1 != null) {
                                final Object items = xformsSelect1Control1.getItemset(pipelineContext, true);
                                if (items != null)
                                    itemsetsFull1.put(xformsSelect1Control1.getEffectiveId(), items);
                            }

                            if (itemsetsFull2 != null && xformsSelect1Control2 != null) {
                                final Object items = xformsSelect1Control2.getItemset(pipelineContext, true);
                                if (items != null)
                                    itemsetsFull2.put(xformsSelect1Control2.getEffectiveId(), items);
                            }
                        }
                    } else {
                        // xformsControl2 == null (&& xformsControl1 != null)
                        // We went from an existing control to a non-relevant control

                        // The only information we send is the non-relevance of the control if needed
                        if (xformsSingleNodeControl1.isRelevant()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                        XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(false));

                            if (!(xformsSingleNodeControl1 instanceof RepeatIterationControl)) {
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                            } else {
                                final RepeatIterationControl repeatIterationInfo = (RepeatIterationControl) xformsSingleNodeControl1;
                                attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIteration()));
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                            }
                        }
                    }
                }

                // 2: Check children if any
                if (XFormsControls.isGroupingControl(leadingControl.getName()) || leadingControl instanceof RepeatIterationControl) {

                    final boolean isRepeatControl = leadingControl instanceof XFormsRepeatControl;

                    final List children1 = (xformsControl1 == null) ? null : (xformsControl1.getChildren() != null && xformsControl1.getChildren().size() == 0) ? null : xformsControl1.getChildren();
                    final List children2 = (xformsControl2 == null) ? null : (xformsControl2.getChildren() != null && xformsControl2.getChildren().size() == 0) ? null : xformsControl2.getChildren();

                    if (isRepeatControl) {

                        // Repeat update

                        final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) leadingControl;

                        final int size1 = (children1 == null) ? 0 : children1.size();
                        final int size2 = (children2 == null) ? 0 : children2.size();

                        if (size1 == size2) {
                            // No add or remove of children

                            // Delete first template if needed
                            if (size2 == 0 && xformsControl1 == null) {
                                outputDeleteRepeatTemplate(ch, xformsControl2, 1);
                            }

                            // Diff children
                            diff(children1, children2);
                        } else if (size2 > size1) {
                            // Size has grown

                            // Copy template instructions
                            for (int k = size1 + 1; k <= size2; k++) {
                                outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                            }

                            // Diff the common subset
                            diff(children1, children2.subList(0, size1));

                            // Issue new values for new iterations
                            diff(null, children2.subList(size1, size2));

                        } else if (size2 < size1) {
                            // Size has shrunk

                            outputDeleteRepeatTemplate(ch, leadingControl, size1 - size2);

                            // Diff the remaining subset
                            diff(children1.subList(0, size2), children2);
                        }
                    } else {
                        // Other grouping controls
                        diff(children1, children2);
                    }
                }
            }
        }
    }
}
