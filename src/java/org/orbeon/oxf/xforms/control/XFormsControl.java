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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.RepeatIterationControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.*;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

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
    private boolean isLabelEvaluated;
    private boolean isHTMLLabel;

    private String help;
    private boolean isHelpEvaluated;
    private boolean isHTMLHelp;

    private String hint;
    private boolean isHintEvaluated;
    private boolean isHTMLHint;

    private String alert;
    private boolean isAlertEvaluated;
    private boolean isHTMLAlert;

    final boolean[] tempContainsHTML = new boolean[1];// temporary holder

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
        if (!isAlertEvaluated) {
            if (controlElement != null) {// protection for RepeatIterationControl
                getContextStack().setBinding(this);
                alert = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_ALERT_QNAME), true, tempContainsHTML);
                isHTMLAlert = alert != null && tempContainsHTML[0];
            }
            isAlertEvaluated = true;
        }
        return alert;
    }

    public String getEscapedAlert(PipelineContext pipelineContext) {
        getAlert(pipelineContext);
        return isHTMLAlert ? rewriteHTMLValue(pipelineContext, alert) : XMLUtils.escapeXMLMinimal(alert);
    }

    public boolean isHTMLAlert(PipelineContext pipelineContext) {
        getAlert(pipelineContext);
        return isHTMLAlert;
    }

    public String getHelp(PipelineContext pipelineContext) {
        if (!isHelpEvaluated) {
            if (controlElement != null) {// protection for RepeatIterationControl
                getContextStack().setBinding(this);
                help = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_HELP_QNAME), true, tempContainsHTML);
                isHTMLHelp = help != null && tempContainsHTML[0];
            }
            isHelpEvaluated = true;
        }
        return help;
    }

    public String getEscapedHelp(PipelineContext pipelineContext) {
        getHelp(pipelineContext);
        return isHTMLHelp ? rewriteHTMLValue(pipelineContext, help) : XMLUtils.escapeXMLMinimal(help);
    }

    public boolean isHTMLHelp(PipelineContext pipelineContext) {
        getHelp(pipelineContext);
        return isHTMLHelp;
    }

    public String getHint(PipelineContext pipelineContext) {
        if (!isHintEvaluated) {
            if (controlElement != null) {// protection for RepeatIterationControl
                getContextStack().setBinding(this);
                hint = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_HINT_QNAME), isSupportHTMLHints(), tempContainsHTML);
                isHTMLHint = hint != null && tempContainsHTML[0];
            }
            isHintEvaluated = true;
        }
        return hint;
    }

    public String getEscapedHint(PipelineContext pipelineContext) {
        getHint(pipelineContext);
        return isHTMLHint ? rewriteHTMLValue(pipelineContext, hint) : XMLUtils.escapeXMLMinimal(hint);
    }

    public boolean isHTMLHint(PipelineContext pipelineContext) {
        getHint(pipelineContext);
        return isHTMLHint;
    }

    public String getLabel(PipelineContext pipelineContext) {
        if (!isLabelEvaluated) {
            if (controlElement != null) {// protection for RepeatIterationControl
                getContextStack().setBinding(this);
                label = XFormsUtils.getChildElementValue(pipelineContext, containingDocument, controlElement.element(XFormsConstants.XFORMS_LABEL_QNAME), isSupportHTMLLabels(), tempContainsHTML);
                isHTMLLabel = label != null && tempContainsHTML[0];
            }
            isLabelEvaluated = true;
        }
        return label;
    }

    public String getEscapedLabel(PipelineContext pipelineContext) {
        getLabel(pipelineContext);
        return isHTMLLabel ? rewriteHTMLValue(pipelineContext, label) : XMLUtils.escapeXMLMinimal(label);
    }

    public boolean isHTMLLabel(PipelineContext pipelineContext) {
        getLabel(pipelineContext);
        return isHTMLLabel;
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

    /**
     * Compare this control with another control, as far as the comparison is relevant for the external world.
     *
     * @param pipelineContext   current PipelineContext
     * @param other               other control
     * @return                  true is the controls have identical external values, false otherwise
     */
    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl other) {

        if (other == null)
            return false;

        if (this == other)
            return true;

        // Compare only what matters
        if (!XFormsUtils.compareStrings(getLabel(pipelineContext), other.getLabel(pipelineContext)))
            return false;
        if (!XFormsUtils.compareStrings(getHelp(pipelineContext), other.getHelp(pipelineContext)))
            return false;
        if (!XFormsUtils.compareStrings(getHint(pipelineContext), other.getHint(pipelineContext)))
            return false;
        if (!XFormsUtils.compareStrings(getAlert(pipelineContext), other.getAlert(pipelineContext)))
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
        getLabel(pipelineContext);
        getHint(pipelineContext);
        getHelp(pipelineContext);
        getAlert(pipelineContext);
    }

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return parent;
    }

    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return containingDocument.getStaticState().getEventHandlers(getId());
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_FOCUS.equals(event.getEventName()) || XFormsEvents.XFORMS_FOCUS.equals(event.getEventName())) {

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

    /**
     * Rewrite an HTML value which may contain URLs, for example in @src or @href attributes.
     *
     * @param pipelineContext   current PipelineContext
     * @param rawValue          value to rewrite
     * @return                  rewritten value
     */
    public String rewriteHTMLValue(final PipelineContext pipelineContext, String rawValue) {
        // Quick check for the most common attributes, src and href. Ideally we should check more.
        final boolean needsRewrite = rawValue.indexOf("src=") != -1 || rawValue.indexOf("href=") != -1;
        final String result;
        if (needsRewrite) {
            // Rewrite URLs
            final FastStringBuffer sb = new FastStringBuffer(rawValue.length() * 2);// just an approx of the size it may take
            // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
            XFormsUtils.streamHTMLFragment(new ForwardingContentHandler() {

                private boolean isStartElement;
                private final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                public void characters(char[] chars, int start, int length) throws SAXException {
                    sb.append(XMLUtils.escapeXMLMinimal(new String(chars, start, length)));// NOTE: not efficient to create a new String here
                    isStartElement = false;
                }

                public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                    sb.append('<');
                    sb.append(localname);
                    final int attributeCount = attributes.getLength();
                    for (int i = 0; i < attributeCount; i++) {

                        final String currentName = attributes.getLocalName(i);
                        final String currentValue = attributes.getValue(i);

                        final String rewrittenValue;
                        if ("src".equals(currentName) || "href".equals(currentName)) {
                            // We should probably use xml:base, but AbstractRewrite doesn't use xml:base
                            rewrittenValue = externalContext.getResponse().rewriteResourceURL(currentValue, false);
                        } else {
                            rewrittenValue = currentValue;
                        }

                        sb.append(' ');
                        sb.append(currentName);
                        sb.append("=\"");
                        sb.append(XMLUtils.escapeXMLMinimal(rewrittenValue));
                        sb.append('"');
                    }
                    sb.append('>');
                    isStartElement = true;
                }

                public void endElement(String uri, String localname, String qName) throws SAXException {
                    if (!isStartElement) {
                        // We serialize to HTML: don't close elements that just opened (will cover <br>, <hr>, etc.)
                        sb.append("</");
                        sb.append(localname);
                        sb.append('>');
                    }
                    isStartElement = false;
                }
            }, rawValue, getLocationData(), "xhtml");
            result = sb.toString();
        } else {
            // No rewriting needed
            result = rawValue;
        }
        return result;
    }
}
