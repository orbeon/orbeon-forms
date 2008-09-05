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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.action.actions.XFormsSetindexAction;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.*;

/**
 * Represents an XForms control.
 */
public abstract class XFormsControl implements XFormsEventTarget, XFormsEventHandlerContainer, Cloneable {

    private XFormsContainer container;
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

    private boolean isEvaluated;

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

    private XFormsControlLocal initialLocal;
    private XFormsControlLocal currentLocal;

    public static class XFormsControlLocal implements Cloneable {
        protected Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                throw new OXFException(e);
            }
        }
    }

    public XFormsControl(XFormsContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        this.container = container;
        this.containingDocument = (container != null) ? container.getContainingDocument() : null;// some special cases pass null (bad, we know...)
        this.parent = parent;
        this.controlElement = element;
        this.name = name;
        this.effectiveId = effectiveId;

        if (element != null) {
            id = element.attributeValue("id");
        }
    }

    public XFormsContainer getContainer() {
        return container;
    }

    protected XFormsContextStack getContextStack() {
        return containingDocument.getControls().getContextStack();
    }

    public String getId() {
        return id;
    }

    /**
     * Update this control's effective id based on the parent's effective id.
     */
    public void updateEffectiveId() {
        final String parentEffectiveId = getParent().getEffectiveId();
        final String parentSuffix = XFormsUtils.getEffectiveIdSuffix(parentEffectiveId);

        if (!parentSuffix.equals("")) {
            // Update effective id
            effectiveId = XFormsUtils.getEffectiveIdNoSuffix(effectiveId) + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + parentSuffix;
        } else {
            // Nothing to do as we are not in repeated content
        }
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    protected void setEffectiveId(String effectiveId) {
        this.effectiveId = effectiveId;
    }

    public LocationData getLocationData() {
        return (controlElement != null) ? (LocationData) controlElement.getData() : null;
    }

    public String getAlert(PipelineContext pipelineContext) {
        if (!isAlertEvaluated) {
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
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
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
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
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
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
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
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

    protected void setParent(XFormsControl parent) {
        this.parent = parent;
    }

    public void detach() {
        this.parent = null;
    }

    public Element getControlElement() {
        return controlElement;
    }

    /**
     * Whether a given control has an xforms:label element.
     *
     * @param containingDocument    containing document (if control is null)
     * @param control               concrete control if available, or null
     * @param staticId              static control id (if control is null)
     * @return                      true iif there is an xforms:label element
     */
    public static boolean hasLabel(XFormsContainingDocument containingDocument, XFormsControl control, String staticId) {
        final Element controlElement = getControlElement(containingDocument, control, staticId);
        return controlElement.element(XFormsConstants.XFORMS_LABEL_QNAME) != null;
    }

    /**
     * Whether a given control has an xforms:alert element.
     *
     * @param containingDocument    containing document (if control is null)
     * @param control               concrete control if available, or null
     * @param staticId              static control id (if control is null)
     * @return                      true iif there is an xforms:alert element
     */
    public static boolean hasAlert(XFormsContainingDocument containingDocument, XFormsControl control, String staticId) {
        final Element controlElement = getControlElement(containingDocument, control, staticId);
        return controlElement.element(XFormsConstants.XFORMS_ALERT_QNAME) != null;
    }

    /**
     * Whether a given control has an xforms:hint element.
     *
     * @param containingDocument    containing document (if control is null)
     * @param control               concrete control if available, or null
     * @param staticId              static control id (if control is null)
     * @return                      true iif there is an xforms:hint element
     */
    public static boolean hasHint(XFormsContainingDocument containingDocument, XFormsControl control, String staticId) {
        final Element controlElement = getControlElement(containingDocument, control, staticId);
        return controlElement.element(XFormsConstants.XFORMS_HINT_QNAME) != null;
    }

    /**
     * Whether a given control has an xforms:help element.
     *
     * @param containingDocument    containing document (if control is null)
     * @param control               concrete control if available, or null
     * @param staticId              static control id (if control is null)
     * @return                      true iif there is an xforms:help element
     */
    public static boolean hasHelp(XFormsContainingDocument containingDocument, XFormsControl control, String staticId) {
        final Element controlElement = getControlElement(containingDocument, control, staticId);
        return controlElement.element(XFormsConstants.XFORMS_HELP_QNAME) != null;
    }

    private static Element getControlElement(XFormsContainingDocument containingDocument, XFormsControl control, String staticId) {
        final Element controlElement;
        if (control != null) {
            // Concrete control
            controlElement = control.getControlElement();
        } else {
            // Static control
            controlElement = ((XFormsStaticState.ControlInfo) containingDocument.getStaticState().getControlInfoMap().get(staticId)).getElement();
        }
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

        // Keep binding context
        this.bindingContext = bindingContext;

        // Set bound node, only considering actual bindings with @bind, @ref or @nodeset
        if (bindingContext.isNewBind())// TODO: this must be done in XFormsSingleNodeControl
            this.boundNode = bindingContext.getSingleNode();
    }

    /**
     * Return the binding context for this control.
     */
    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    /**
     * Return the node to which the control is bound, if any. If the control is not bound to any node, return null. If
     * the node to which the control no longer exists, return null.
     *
     * @return bound node or null
     */
    public NodeInfo getBoundNode() {
        // TODO: this must be done in XFormsSingleNodeControl
        return boundNode;
    }

    public final void evaluateIfNeeded(PipelineContext pipelineContext) {
        if (!isEvaluated) {
            isEvaluated = true;// be careful with this flag, you can get into a recursion if you don't set it before calling evaluate()
            evaluate(pipelineContext);
        }
    }

    public void markDirty() {
        isEvaluated = false;
        isLabelEvaluated = false;
        isHelpEvaluated = false;
        isHintEvaluated = false;
        isAlertEvaluated = false;
    }

    protected void evaluate(PipelineContext pipelineContext) {
        getLabel(pipelineContext);
        getHint(pipelineContext);
        getHelp(pipelineContext);
        getAlert(pipelineContext);
    }

    public XFormsEventHandlerContainer getParentEventHandlerContainer(XFormsContainer container) {
        return parent;
    }

    public List getEventHandlers(XFormsContainer container) {
        return containingDocument.getStaticState().getEventHandlers(XFormsUtils.getEffectiveIdNoSuffix(getEffectiveId()));
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_REPEAT_FOCUS.equals(event.getEventName()) || XFormsEvents.XFORMS_FOCUS.equals(event.getEventName())) {

            // Try to update xforms:repeat indices based on this
            {
                // Find current path through ancestor xforms:repeat elements, if any
                final List repeatIterationsToModify = new ArrayList();
                {
                    XFormsControl currentXFormsControl = XFormsControl.this; // start with this, as we can be a RepeatIteration
                    while (currentXFormsControl != null) {

                        if (currentXFormsControl instanceof XFormsRepeatIterationControl) {
                            final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl) currentXFormsControl;
                            final XFormsRepeatControl repeatControl = (XFormsRepeatControl) repeatIterationControl.getParent();

                            // Check whether the index selection changes
                            if (repeatControl.getIndex() != repeatIterationControl.getIterationIndex()) {
                                // Store the id because the controls may be cloned below
                                repeatIterationsToModify.add(repeatIterationControl.getEffectiveId());
                            }
                        }

                        currentXFormsControl = currentXFormsControl.getParent();
                    }
                }

                if (repeatIterationsToModify.size() > 0) {
                    final XFormsControls controls = containingDocument.getControls();

                    // Find all repeat iterations and controls again
                    for (Iterator i = repeatIterationsToModify.iterator(); i.hasNext();) {
                        final String repeatIterationEffectiveId = (String) i.next();
                        final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl) controls.getObjectByEffectiveId(repeatIterationEffectiveId);
                        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) repeatIterationControl.getParent();

                        final int newRepeatIndex = repeatIterationControl.getIterationIndex();

                        if (XFormsServer.logger.isDebugEnabled()) {
                            containingDocument.logDebug("repeat", "setting index upon focus change",
                                    new String[] { "new index", Integer.toString(newRepeatIndex)});
                        }

                        repeatControl.setIndex(newRepeatIndex);
                    }
                    
                    controls.markDirtySinceLastRequest(true);
                    XFormsSetindexAction.setDeferredFlagsForSetindex(containingDocument);
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

    public void performTargetAction(PipelineContext pipelineContext, XFormsContainer container, XFormsEvent event) {
        // NOP
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

    /**
     * Serialize this control's information which cannot be reconstructed from instances. The result is null if no
     * serialization is needed, or a map of name/value pairs otherwise.
     *
     * @return  map<String name, String value>
     */
    public Map serializeLocal() {
        return null;
    }

    /**
     * Deserialize this control's information which cannot be reconstructed from instances.
     *
     * @param element containing attributes which can be used by the control
     */
    public void deserializeLocal(Element element) {
        // NOP
    }

    /**
     * Clone a control. It is important to understand why this is implemented: to create a copy of a tree of controls
     * before updates that may change control bindings. Also, it is important to understand that we clone "back", that
     * is the new clone will be used as the reference copy for the difference engine.
     *
     * @return  new XFormsControl
     */
    public Object clone() {
        // NOTE: this.parent is handled by subclasses
        final XFormsControl cloned;
        try {
            cloned = (XFormsControl) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);
        }

        if (this.currentLocal != null) {
            // There is some local data
            if (this.currentLocal != this.initialLocal) {
                // The trees don't keep wastefull references
                cloned.currentLocal = cloned.initialLocal;
                this.initialLocal = this.currentLocal;
            } else {
                // The new tree must have its own copy
                // NOTE: We could implement a copy-on-write flag here
                cloned.initialLocal = cloned.currentLocal = (XFormsControlLocal) this.currentLocal.clone();
            }
        }

        return cloned;
    }

    protected void setLocal(XFormsControlLocal local) {
        this.initialLocal = this.currentLocal = local;
    }

    protected XFormsControlLocal getLocalForUpdate() {
        if (containingDocument.isHandleDifferences()) {
            // Happening during a client request where we need to handle diffs
            final XFormsControls controls =  containingDocument.getControls();
            if (controls.getInitialControlTree() != controls.getCurrentControlTree()) {
                if (currentLocal != initialLocal)
                    throw new OXFException("currentLocal != initialLocal");
            } else if (initialLocal == currentLocal) {
                currentLocal = (XFormsControlLocal) initialLocal.clone();
            }
        } else {
            // Happening during initialization
            // NOP: Don't modify currentLocal
        }
        return currentLocal;
    }

    public XFormsControlLocal getInitialLocal() {
        return initialLocal;
    }

    public XFormsControlLocal getCurrentLocal() {
        return currentLocal;
    }

    public void resetLocal() {
        initialLocal = currentLocal;
    }
}
