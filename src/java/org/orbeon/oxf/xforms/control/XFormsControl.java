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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.RepeatIterationControl;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represents an XForms control.
 */
public abstract class XFormsControl implements XFormsEventTarget, XFormsEventHandlerContainer {

    protected XFormsContainingDocument containingDocument;

    private XFormsControl parent;
    private String name;

    private Element controlElement;

    private String originalId;
    private String appearance;
    private String mediatype;

    private String effectiveId;
    private String label;
    private String help;
    private String hint;
    private String alert;
    private String value;
    private String displayValue;

    private String labelId;
    private String helpId;
    private String hintId;
    private String alertId;

    private boolean readonly;
    private boolean required;
    private boolean relevant;
    private boolean valid;
    private String type;

    private List children;
    private List eventHandlers;

    protected XFormsControls.BindingContext bindingContext;

    public XFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        this.containingDocument = containingDocument;
        this.parent = parent;
        this.controlElement = element;
        this.name = name;
        this.effectiveId = id;

        // Extract event handlers
        if (element != null) {
            originalId = element.attributeValue("id");
            eventHandlers = XFormsEventHandlerImpl.extractEventHandlers(containingDocument, this, element);

            labelId = getChildElementId(element, XFormsConstants.XFORMS_LABEL_QNAME);
            helpId = getChildElementId(element, XFormsConstants.XFORMS_HELP_QNAME);
            hintId = getChildElementId(element, XFormsConstants.XFORMS_HINT_QNAME);
            alertId = getChildElementId(element, XFormsConstants.XFORMS_ALERT_QNAME);
        }
    }

    private String getChildElementId(Element element, QName qName) {
        // Check that there is a current child element
        Element childElement = element.element(qName);
        if (childElement == null)
            return null;

        return childElement.attributeValue("id");
    }

    public void addChild(XFormsControl XFormsControl) {
        if (children == null)
            children = new ArrayList();
        children.add(XFormsControl);
    }

    public String getOriginalId() {
        return originalId;
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    public LocationData getLocationData() {
        return (LocationData) controlElement.getData();
    }

    public List getChildren() {
        return children;
    }

    public String getAlert() {
        return alert;
    }

    public void setAlert(String alert) {
        this.alert = alert;
    }

    public String getHelp() {
        return help;
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabelId() {
        return labelId;
    }

    public String getHintId() {
        return hintId;
    }

    public String getHelpId() {
        return helpId;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public boolean isRelevant() {
        return relevant;
    }

    public void setRelevant(boolean relevant) {
        this.relevant = relevant;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getValue() {
        return value;
    }

    /**
     * Return a formatted display value of the control value, null if there is no such display value.
     */
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Return a formatted display value of the control value, or the raw control value if there is no such display value.
     */
    public String getDisplayValueOrValue() {
        return displayValue != null ? displayValue : value;
    }

    protected void setValue(String value) {
        this.value = value;
    }

    protected void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    public XFormsControl getParent() {
        return parent;
    }

    public void detach() {
        this.parent = null;
    }

    public Element getControlElement() {
        return controlElement;
    }

    /**
     * Return the control's appearance as an exploded QName.
     */
    public String getAppearance() {
        if (appearance == null)
            appearance = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(controlElement, "appearance"));
        return appearance;
    }

    /**
     * Return the control's mediatype.
     */
    public String getMediatype() {
        if (mediatype == null)
            mediatype = controlElement.attributeValue("mediatype");
        return mediatype;
    }

    /**
     * Return true if the control, with its current appearance, requires JavaScript initialization.
     */
    public boolean hasJavaScriptInitialization() {
        return false;
    }

    public boolean equals(Object obj) {

        if (obj == null || !(obj instanceof XFormsControl))
            return false;

        if (this == obj)
            return true;

        final XFormsControl other = (XFormsControl) obj;

        if (!((name == null && other.name == null) || (name != null && other.name != null && name.equals(other.name))))
            return false;
        if (!((effectiveId == null && other.effectiveId == null) || (effectiveId != null && other.effectiveId != null && effectiveId.equals(other.effectiveId))))
            return false;
        if (!((label == null && other.label == null) || (label != null && other.label != null && label.equals(other.label))))
            return false;
        if (!((help == null && other.help == null) || (help != null && other.help != null && help.equals(other.help))))
            return false;
        if (!((hint == null && other.hint == null) || (hint != null && other.hint != null && hint.equals(other.hint))))
            return false;
        if (!((alert == null && other.alert == null) || (alert != null && other.alert != null && alert.equals(other.alert))))
            return false;
        if (!((value == null && other.value == null) || (value != null && other.value != null && value.equals(other.value))))
            return false;

        if (readonly != other.readonly)
            return false;
        if (required != other.required)
            return false;
        if (relevant != other.relevant)
            return false;
        if (valid != other.valid)
            return false;

        if (!((type == null && other.type == null) || (type != null && other.type != null && type.equals(other.type))))
            return false;

        return true;
    }

    public boolean isValueControl() {
        return XFormsControls.isValueControl(getName());
    }

    /**
     * Set this control's binding context.
     */
    public void setBindingContext(XFormsControls.BindingContext bindingContext) {
        this.bindingContext = bindingContext;
    }

    /**
     * Return the binding context for this control.
     */
    public XFormsControls.BindingContext getBindingContext() {
        return bindingContext;
    }

    /**
     * Return the node to which the control is bound, if any. If the control is not bound to any node, return null. If
     * the node to which the control no longer exists, return null.
     */
    public NodeInfo getBoundNode() {
        final NodeInfo boundSingleNode = bindingContext.getSingleNode();
        if (boundSingleNode == null)
            return null;

        final XFormsInstance boundInstance = containingDocument.getInstanceForNode(boundSingleNode);
        if (boundInstance == null)
            return null;

        return boundSingleNode;
    }

    /**
     * Notify the control that its value has changed due to external user interaction.
     *
     * @param value the new value
     */
    public void setExternalValue(PipelineContext pipelineContext, String value) {
        // Set value into the instance

        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.");
        final boolean changed = XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, boundNode, value);

        if (changed) {
            // Update this particular control's value
            evaluateValue(pipelineContext);
            evaluateDisplayValue(pipelineContext);
        }
    }

    /**
     *
     *
     * @param pipelineContext
     */
    public void evaluateValue(PipelineContext pipelineContext) {
        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot evaluate control value.");
        setValue(XFormsInstance.getValueForNode(boundNode));
    }

    public void evaluateDisplayValue(PipelineContext pipelineContext) {
        // NOP for most controls
    }

    public String convertToExternalValue(String internalValue) {
        return internalValue;
    }

    protected void evaluateDisplayValueUseFormat(PipelineContext pipelineContext, String format) {
        final String result;
        if (format == null) {
            // Try default format for known types

            final Map prefixToURIMap = new HashMap();
            prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

            final OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();

            if ("{http://www.w3.org/2001/XMLSchema}date".equals(type)) {
                // Format a date
                final String DEFAULT_FORMAT = "if (. castable as xs:date) then format-date(xs:date(.), '[MNn] [D], [Y]', 'en', (), ()) else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DATE_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}dateTime".equals(type)) {
                // Format a dateTime
                final String DEFAULT_FORMAT = "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ()) else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DATETIME_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}time".equals(type)) {
                // Format a time
                final String DEFAULT_FORMAT = "if (. castable as xs:time) then format-time(xs:time(.), '[H01]:[m01]:[s01] UTC', 'en', (), ()) else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_TIME_FORMAT_PROPERTY, DEFAULT_FORMAT);
            }

            if (format != null) {
                final NodeInfo boundNode = getBoundNode();
                if (boundNode == null) // this should not happen
                    throw new OXFException("Control is no longer bound to a node. Cannot evaluate control display value.");
                result = containingDocument.getEvaluator().evaluateAsString(pipelineContext, boundNode,
                            format, prefixToURIMap, null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
            } else {
                result = null;
            }

        } else {
            // Format value according to format attribute
            final Map prefixToURIMap = Dom4jUtils.getNamespaceContextNoDefault(getControlElement());

            final NodeInfo boundNode = getBoundNode();
            if (boundNode == null) // this should not happen
                throw new OXFException("Control is no longer bound to a node. Cannot evaluate control display value.");

            result = containingDocument.getEvaluator().evaluateAsString(pipelineContext, boundNode,
                        format, prefixToURIMap, null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
        }
        setDisplayValue(result);
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return parent;
    }

    public List getEventHandlers() {
        return eventHandlers;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        if (XFormsEvents.XFORMS_DOM_FOCUS_IN.equals(event.getEventName()) || XFormsEvents.XFORMS_FOCUS.equals(event.getEventName())) {

            // Try to update xforms:repeat indices based on this
            {
                XFormsRepeatControl firstAncestorRepeatControlInfo = null;
                final List ancestorRepeatsIds = new ArrayList();
                final Map ancestorRepeatsIterationMap = new HashMap();

                // Find current path through ancestor xforms:repeat elements, if any
                {
                    XFormsControl currentXFormsControl = getParent();
                    while (currentXFormsControl != null) {

                        if (currentXFormsControl instanceof RepeatIterationControl) {
                            final RepeatIterationControl repeatIterationInfo = (RepeatIterationControl) currentXFormsControl;
                            final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) repeatIterationInfo.getParent();
                            final int iteration = repeatIterationInfo.getIteration();
                            final String repeatId = repeatControlInfo.getRepeatId();

                            if (firstAncestorRepeatControlInfo == null)
                                firstAncestorRepeatControlInfo = repeatControlInfo;
                            ancestorRepeatsIds.add(repeatId);
                            ancestorRepeatsIterationMap.put(repeatId,  new Integer(iteration));
                        }

                        currentXFormsControl = currentXFormsControl.getParent();
                    }
                }

                if (ancestorRepeatsIds.size() > 0) {

                    final XFormsControls xformsControls = containingDocument.getXFormsControls();

                    // Check whether this changes the index selection
                    boolean doUpdate = false;
                    {
                        final Map currentRepeatIdToIndex = xformsControls.getCurrentControlsState().getRepeatIdToIndex();
                        for (Iterator i = ancestorRepeatsIds.iterator(); i.hasNext();) {
                            final String repeatId = (String) i.next();
                            final Integer newIteration = (Integer) ancestorRepeatsIterationMap.get(repeatId);
                            final Integer currentIteration = (Integer) currentRepeatIdToIndex.get(repeatId);
                            if (!newIteration.equals(currentIteration)) {
                                doUpdate = true;
                                break;
                            }
                        }
                    }

                    // Only update if the index selection has changed
                    if (doUpdate) {

                        // Update ControlsState if needed as we are going to make some changes
                        xformsControls.rebuildCurrentControlsState(pipelineContext);

                        // Iterate from root to leaf
                        Collections.reverse(ancestorRepeatsIds);
                        for (Iterator i = ancestorRepeatsIds.iterator(); i.hasNext();) {
                            final String repeatId = (String) i.next();
                            final Integer iteration = (Integer) ancestorRepeatsIterationMap.get(repeatId);

    //                            XFormsActionInterpreter.executeSetindexAction(pipelineContext, containingDocument, repeatId, iteration.toString());
                            xformsControls.getCurrentControlsState().updateRepeatIndex(repeatId, iteration.intValue());
                        }

                        // Update children xforms:repeat indexes if any
                        xformsControls.visitAllControlInfo(new XFormsControls.XFormsControlVisitorListener() {
                            public void startVisitControl(XFormsControl XFormsControl) {
                                if (XFormsControl instanceof XFormsRepeatControl) {
                                    // Found child repeat
                                    xformsControls.getCurrentControlsState().updateRepeatIndex(((XFormsRepeatControl) XFormsControl).getRepeatId(), 1);
                                }
                            }

                            public void endVisitControl(XFormsControl XFormsControl) {
                            }
                        }, firstAncestorRepeatControlInfo);

                        // Adjust controls ids that could have gone out of bounds
                        XFormsIndexUtils.adjustRepeatIndexes(pipelineContext, xformsControls, null);

                        // After changing indexes, must recalculate
                        // NOTE: The <setindex> action is supposed to "The implementation data
                        // structures for tracking computational dependencies are rebuilt or
                        // updated as a result of this action."
                        // What should we do here? We run the computed expression binds so that
                        // those are updated, but is that what we should do?
                        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
                            XFormsModel currentModel = (XFormsModel) i.next();
                            currentModel.applyComputedExpressionBinds(pipelineContext);
                            //containingDocument.dispatchEvent(pipelineContext, new XFormsRecalculateEvent(currentModel, true));
                        }
                        // TODO: Should try to use the code of the <setindex> action

                        containingDocument.getXFormsControls().markDirty();
                    }
                }

                // Store new focus information for client
                if (XFormsEvents.XFORMS_FOCUS.equals(event.getEventName())) {
                    containingDocument.setClientFocusEffectiveControlId(getEffectiveId());
                }
            }
        }
    }
}
