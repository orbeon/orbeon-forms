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

import org.dom4j.*;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.RepeatIterationControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.XFormsLinkErrorEvent;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.io.IOException;
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

    public XFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        this.containingDocument = containingDocument;
        this.parent = parent;
        this.controlElement = element;
        this.name = name;
        this.effectiveId = effectiveId;

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

    public String getHelp() {
        return help;
    }

    public String getHint() {
        return hint;
    }

    public String getLabel() {
        return label;
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

    public void evaluate(PipelineContext pipelineContext) {

        // Set context to this control
        final XFormsControls xformsControls = containingDocument.getXFormsControls();
        xformsControls.setBinding(pipelineContext, this);

        this.label = getChildElementValue(pipelineContext, controlElement.element(XFormsConstants.XFORMS_LABEL_QNAME), false);
        this.help = getChildElementValue(pipelineContext, controlElement.element(XFormsConstants.XFORMS_HELP_QNAME), true);
        this.hint = getChildElementValue(pipelineContext, controlElement.element(XFormsConstants.XFORMS_HINT_QNAME), true);
        this.alert = getChildElementValue(pipelineContext, controlElement.element(XFormsConstants.XFORMS_ALERT_QNAME), false);
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return parent;
    }

    public List getEventHandlers() {
        return eventHandlers;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_REPEAT_FOCUS.equals(event.getEventName()) || XFormsEvents.XFORMS_FOCUS.equals(event.getEventName())) {

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
                        xformsControls.visitAllControls(new XFormsControls.XFormsControlVisitorListener() {
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

    protected String getChildElementValue(final PipelineContext pipelineContext, Element childElement, final boolean acceptHTML) {

        // NOTE: This returns an HTML string.

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        // Check that there is a current child element
        if (childElement == null)
            return null;

        // Child element becomes the new binding
        String result = null;
        xformsControls.pushBinding(pipelineContext, childElement);
        {
            final XFormsControls.BindingContext currentBindingContext = xformsControls.getCurrentBindingContext();

            // "the order of precedence is: single node binding attributes, linking attributes, inline text."

            // Try to get single node binding
            if (currentBindingContext.isNewBind()) {
                final NodeInfo currentNode = currentBindingContext.getSingleNode();
                if (currentNode != null)
                    result = XFormsInstance.getValueForNodeInfo(currentNode);
            }

            // Try to get value attribute
            // NOTE: This is an extension attribute not standard in XForms 1.0 or 1.1
            if (result == null) {
                final String valueAttribute = childElement.attributeValue("value");
                if (valueAttribute != null) {
                    final List currentNodeset = currentBindingContext.getNodeset();
                    if (currentNodeset != null && currentNodeset.size() > 0) {
                        final String tempResult = containingDocument.getEvaluator().evaluateAsString(pipelineContext,
                                currentNodeset, currentBindingContext.getPosition(),
                                valueAttribute, Dom4jUtils.getNamespaceContextNoDefault(childElement), null, containingDocument.getXFormsControls().getFunctionLibrary(), null);

                        result = (acceptHTML) ? XMLUtils.escapeXMLMinimal(tempResult) : tempResult;
                    } else {
                        result = ""; 
                    }
                }
            }

            // Try to get linking attribute
            // NOTE: This is deprecated in XForms 1.1
            if (result == null) {
                final String srcAttributeValue = childElement.attributeValue("src");
                if (srcAttributeValue != null) {
                    try {
                        // TODO: should cache this?
                        final String tempResult  = XFormsUtils.retrieveSrcValue(srcAttributeValue);
                        result = (acceptHTML) ? XMLUtils.escapeXMLMinimal(tempResult) : tempResult;
                    } catch (IOException e) {
                        // Dispatch xforms-link-error to model
                        final XFormsModel currentModel = currentBindingContext.getModel();
                        containingDocument.dispatchEvent(pipelineContext, new XFormsLinkErrorEvent(currentModel, srcAttributeValue, childElement, e));
                    }
                }
            }

            // Try to get inline value
            if (result == null) {

                final StringBuffer sb = new StringBuffer();

                // Visit the subtree and serialize

                // NOTE: It is a litte funny to do our own serialization here, but the alternative is to build a DOM
                // and serialize it, which is not trivial because of the possible interleaved xforms:output's.
                // Furthermore, we perform a very simple serialization of elements and text to simple (X)HTML, not
                // full-fledged HTML or XML serialization.
                Dom4jUtils.visitSubtree(childElement, new Dom4jUtils.VisitorListener() {

                    public void startElement(Element element) {
                        if (element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME)) {
                            // This is an xforms:output

                            final XFormsOutputControl outputControl = new XFormsOutputControl(containingDocument, null, element, element.getName(), null);
                            xformsControls.pushBinding(pipelineContext, element);
                            {
                                outputControl.setBindingContext(xformsControls.getCurrentBindingContext());
                                outputControl.evaluate(pipelineContext);
                            }
                            xformsControls.popBinding();

                            // Escape only if the mediatype is not HTML
                            if (acceptHTML && !"text/html".equals(outputControl.getMediatype()))
                                sb.append(XMLUtils.escapeXMLMinimal(outputControl.getDisplayValueOrValue()));
                            else
                                sb.append(outputControl.getDisplayValueOrValue());
                        } else {
                            // This is a regular element, just serialize the start tag to no namespace

                            sb.append('<');
                            sb.append(element.getName());
                            final List attributes = element.attributes();
                            if (attributes.size() > 0) {
                                for (Iterator i = attributes.iterator(); i.hasNext();) {
                                    final Attribute currentAttribute = (Attribute) i.next();

                                    // Only consider attributes in no namespace
                                    if ("".equals(currentAttribute.getNamespaceURI())) {
                                        sb.append(' ');
                                        sb.append(currentAttribute.getName());
                                        sb.append("=\"");
                                        sb.append(XMLUtils.escapeXMLMinimal(currentAttribute.getValue()));
                                        sb.append('"');
                                    }
                                }
                            }
                            sb.append('>');
                        }
                    }

                    public void endElement(Element element) {
                        if (!element.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME)) {
                            // This is a regular element, just serialize the end tag to no namespace
                            sb.append("</");
                            sb.append(element.getName());
                            sb.append('>');
                        }
                    }

                    public void text(Text text) {
                        sb.append(acceptHTML ? XMLUtils.escapeXMLMinimal(text.getStringValue()) : text.getStringValue());
                    }
                });

                result = sb.toString();
            }
        }

        xformsControls.popBinding();
        return result;
    }
}
