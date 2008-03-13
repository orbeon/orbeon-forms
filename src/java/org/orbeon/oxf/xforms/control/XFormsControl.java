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
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.*;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represents an XForms control.
 */
public abstract class XFormsControl implements XFormsEventTarget, XFormsEventHandlerContainer {

    protected XFormsContainingDocument containingDocument;

    // Static information (never changes for the lifetime of the containing document)
    private Element controlElement;
    //private NodeInfo controlNodeInfo; TODO
    private String id;
    private String name;
    private String appearance;// could become more dynamic in the future
    private String mediatype;// could become more dynamic in the future

    // Semi-dynamic information (depends on the tree of controls, but does not change over time)
    private XFormsControl parent;

    // Dynamic information (changes depending on the content of XForms instances)
    private String effectiveId;
    protected XFormsContextStack.BindingContext bindingContext;
    private NodeInfo boundNode;

    private boolean evaluated;
    private String label;
    private boolean isHTMLLabel;
    private String help;
    private boolean isHTMLHelp;
    private String hint;
    private boolean isHTMLHint;
    private String alert;
    private boolean isHTMLAlert;

    // TODO: this should be handled in a subclass (e.g. ContainingControl)
    private List children;

    public XFormsControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        this.containingDocument = containingDocument;
        this.parent = parent;
        this.controlElement = element;
        this.name = name;
        this.effectiveId = effectiveId;

        if (element != null) {
            id = element.attributeValue("id");
        }
    }

    protected XFormsContextStack getContextStack() {
        return containingDocument.getXFormsControls().getContextStack();
    }

//    protected XFormsControl(XFormsControl xformsControl) {
//        this.controlElement = xformsControl.controlElement;
//        this.originalId = xformsControl.originalId;
//        this.name = xformsControl.name;
//        this.appearance = xformsControl.appearance;
//        this.mediatype = xformsControl.mediatype;
//    }
//
//    public abstract XFormsControl getStaticControl();

//    private String getChildElementId(Element element, QName qName) {
//        // Check that there is a current child element
//        Element childElement = element.element(qName);
//        if (childElement == null)
//            return null;
//
//        return childElement.attributeValue("id");
//    }

    public void addChild(XFormsControl XFormsControl) {
        if (children == null)
            children = new ArrayList();
        children.add(XFormsControl);
    }

    public String getId() {
        return id;
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    public LocationData getLocationData() {
        return (controlElement != null) ? (LocationData) controlElement.getData() : null;
    }

    public List getChildren() {
        return children;
    }

    public String getAlert(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return alert;
    }

    public String getEscapedAlert(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLAlert ? alert : XMLUtils.escapeXMLMinimal(alert);
    }

    public boolean isHTMLAlert(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLAlert;
    }

    public String getHelp(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return help;
    }

    public String getEscapedHelp(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLHelp ? help : XMLUtils.escapeXMLMinimal(help);
    }

    public boolean isHTMLHelp(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLHelp;
    }

    public String getHint(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return hint;
    }

    public boolean isHTMLHint(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLHint;
    }

    public String getLabel(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return label;
    }

    public String getEscapedLabel(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLLabel ? label : XMLUtils.escapeXMLMinimal(label);
    }

    public boolean isHTMLLabel(PipelineContext pipelineContext) {
        evaluateIfNeeded(pipelineContext);
        return isHTMLLabel;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean hasLabel() {
        return controlElement.element(XFormsConstants.XFORMS_LABEL_QNAME) != null;
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

    public boolean equalsExternal(PipelineContext pipelineContext, Object obj) {

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

        return true;
    }

    /**
     * Set this control's binding context.
     */
    public void setBindingContext(XFormsContextStack.BindingContext bindingContext) {
        this.bindingContext = bindingContext;
        // Set the bound node at this time as well. This won't change until next refresh.
        setBoundNode();
    }

    /**
     * Return the binding context for this control.
     */
    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    /**
     * Set the node to which the control is bound, if any. If the control is not bound to any node, the bound node is
     * null. If the node to which the control no longer exists, return null.
     */
    private void setBoundNode() {
        final NodeInfo boundSingleNode = bindingContext.getSingleNode();
        if (boundSingleNode == null) {
            this.boundNode = null;
            return;
        }

        // Is this needed now that we set the bound node upon setBindingContext()?
        final XFormsInstance boundInstance = containingDocument.getInstanceForNode(boundSingleNode);
        if (boundInstance == null) {
            this.boundNode = null;
            return;
        }

        this.boundNode = boundSingleNode;
    }

    /**
     * Return the node to which the control is bound, if any. If the control is not bound to any node, return null. If
     * the node to which the control no longer exists, return null.
     *
     * @return bound node or null
     */
    public NodeInfo getBoundNode() {
        return boundNode;
    }

    public final void evaluateIfNeeded(PipelineContext pipelineContext) {
        if (!evaluated) {
            evaluated = true;// be careful with this flag, you can get into a recursion if you don't set it before calling evaluate()
            evaluate(pipelineContext);
        }
    }

    protected void evaluate(PipelineContext pipelineContext) {

        // Set context to this control
        getContextStack().setBinding(this);

//        if (false) {
//            // XXX TEST don't run expressions to compute labels and helps assuming they will be
//            this.label="foobarlabel";
//            this.help="foobarhelp";
//            this.hint="";
//            this.alert="";
//        } else {
            final boolean[] containsHTML = new boolean[1];
            this.label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_LABEL_QNAME), isSupportHTMLLabels(), containsHTML);
            this.isHTMLLabel = containsHTML[0];

            this.help = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_HELP_QNAME), true, containsHTML);
            this.isHTMLHelp = containsHTML[0];

            this.hint = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_HINT_QNAME), isSupportHTMLHints(), containsHTML);
            this.isHTMLHint = containsHTML[0];

            this.alert = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_ALERT_QNAME), true, containsHTML);
            this.isHTMLAlert = containsHTML[0];
//        }
    }

    /**
     * Whether the control supports labels containing HTML. The default is true as most controls do support it.
     *
     * @return  true if HTML labels are supported, false otherwise
     */
    protected boolean isSupportHTMLLabels() {
        return true;
    }

    /**
     * Whether the control supports hints containing HTML. The default is true as most controls do support it.
     *
     * @return  true if HTML hints are supported, false otherwise
     */
    protected boolean isSupportHTMLHints() {
        return true;
    }

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return parent;
    }

    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return containingDocument.getStaticState().getEventHandlers(getId());
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
                    XFormsControl currentXFormsControl = XFormsControl.this; // start with this, as we can be a RepeatIteration
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
                            public void startVisitControl(XFormsControl control) {
                                if (control instanceof XFormsRepeatControl) {
                                    // Found child repeat
                                    xformsControls.getCurrentControlsState().updateRepeatIndex(((XFormsRepeatControl) control).getRepeatId(), 1);
                                }
                            }

                            public void endVisitControl(XFormsControl XFormsControl) {
                            }
                        }, firstAncestorRepeatControlInfo);

                        // Adjust controls ids that could have gone out of bounds
                        XFormsIndexUtils.adjustRepeatIndexes(xformsControls, null);

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

                        containingDocument.getXFormsControls().markDirtySinceLastRequest();
                    }
                }

                // Store new focus information for client
                if (XFormsEvents.XFORMS_FOCUS.equals(event.getEventName())) {
                    containingDocument.setClientFocusEffectiveControlId(getEffectiveId());
                }
            }
        } else if (XFormsEvents.XFORMS_HELP.equals(event.getEventName())) {
            containingDocument.setClientHelpEffectiveControlId(getEffectiveId());
        }
    }

    public boolean isStaticReadonly() {
        return false;
    }
}
