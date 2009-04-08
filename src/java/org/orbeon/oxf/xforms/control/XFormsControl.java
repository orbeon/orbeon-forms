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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.actions.XFormsSetindexAction;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Represents an XForms control.
 */
public abstract class XFormsControl implements XFormsEventTarget, XFormsEventObserver, Cloneable {

    // List of standard extension attributes
    private static final QName[] EXTENSION_ATTRIBUTES = {
            XFormsConstants.STYLE_QNAME,
            //XFormsConstants.CLASS_QNAME, TODO: handle @class specially as it is now copied as is in XFormsbaseHandler
    };

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
    private String prefixedId;

    protected XFormsContextStack.BindingContext bindingContext;
    private NodeInfo boundNode;

    private boolean isEvaluated;

    // Optional extension attributes supported by the control
    private Map extensionAttributesValues;

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

    public XFormsContainer getContainer(XFormsContainingDocument containingDocument) {
        return getContainer();
    }

    protected XFormsContextStack getContextStack() {
        // TODO: Once each container has its own subtree of controls, use container's context stack
        return containingDocument.getControls().getContextStack();
    }

    public void iterationRemoved(PipelineContext pipelineContext) {
        // NOP, can be overridden
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

    public String getPrefixedId() {
        if (prefixedId == null) {
            prefixedId = XFormsUtils.getEffectiveIdNoSuffix(effectiveId);
        }
        return prefixedId;
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
                final Element lhhaElement = containingDocument.getStaticState().getAlertElement(getPrefixedId());
                alert = XFormsUtils.getLabelHelpHintAlertValue(pipelineContext, container, this, lhhaElement, true, tempContainsHTML);
                isHTMLAlert = alert != null && tempContainsHTML[0];
            }
            isAlertEvaluated = true;
        }
        return alert;
    }

    public String getEscapedAlert(PipelineContext pipelineContext) {
        getAlert(pipelineContext);
        return isHTMLAlert ? getEscapedHTMLValue(pipelineContext, alert) : XMLUtils.escapeXMLMinimal(alert);
    }

    public boolean isHTMLAlert(PipelineContext pipelineContext) {
        getAlert(pipelineContext);
        return isHTMLAlert;
    }

    public String getHelp(PipelineContext pipelineContext) {
        if (!isHelpEvaluated) {
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
                final Element lhhaElement = containingDocument.getStaticState().getHelpElement(getPrefixedId());
                help = XFormsUtils.getLabelHelpHintAlertValue(pipelineContext, container, this, lhhaElement, true, tempContainsHTML);
                isHTMLHelp = help != null && tempContainsHTML[0];
            }
            isHelpEvaluated = true;
        }
        return help;
    }

    public String getEscapedHelp(PipelineContext pipelineContext) {
        getHelp(pipelineContext);
        return isHTMLHelp ? getEscapedHTMLValue(pipelineContext, help) : XMLUtils.escapeXMLMinimal(help);
    }

    public boolean isHTMLHelp(PipelineContext pipelineContext) {
        getHelp(pipelineContext);
        return isHTMLHelp;
    }

    public String getHint(PipelineContext pipelineContext) {
        if (!isHintEvaluated) {
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
                final Element lhhaElement = containingDocument.getStaticState().getHintElement(getPrefixedId());
                hint = XFormsUtils.getLabelHelpHintAlertValue(pipelineContext, container, this, lhhaElement, isSupportHTMLHints(), tempContainsHTML);

                isHTMLHint = hint != null && tempContainsHTML[0];
            }
            isHintEvaluated = true;
        }
        return hint;
    }

    public String getEscapedHint(PipelineContext pipelineContext) {
        getHint(pipelineContext);
        return isHTMLHint ? getEscapedHTMLValue(pipelineContext, hint) : XMLUtils.escapeXMLMinimal(hint);
    }

    public boolean isHTMLHint(PipelineContext pipelineContext) {
        getHint(pipelineContext);
        return isHTMLHint;
    }

    public String getLabel(PipelineContext pipelineContext) {
        if (!isLabelEvaluated) {
            if (!(this instanceof XFormsPseudoControl)) {// protection for RepeatIterationControl
                final Element lhhaElement = containingDocument.getStaticState().getLabelElement(getPrefixedId());
                label = XFormsUtils.getLabelHelpHintAlertValue(pipelineContext, container, this, lhhaElement, isSupportHTMLLabels(), tempContainsHTML);

                isHTMLLabel = label != null && tempContainsHTML[0];
            }
            isLabelEvaluated = true;
        }
        return label;
    }

    public String getEscapedLabel(PipelineContext pipelineContext) {
        getLabel(pipelineContext);
        return isHTMLLabel ? getEscapedHTMLValue(pipelineContext, label) : XMLUtils.escapeXMLMinimal(label);
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
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:label element
     */
    public static boolean hasLabel(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticState().getLabelElement(prefixedId) != null;
    }

    /**
     * Whether a given control has an xforms:hint element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:hint element
     */
    public static boolean hasHint(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticState().getHintElement(prefixedId) != null;
    }

    /**
     * Whether a given control has an xforms:help element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:help element
     */
    public static boolean hasHelp(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticState().getHelpElement(prefixedId) != null;
    }

    /**
     * Whether a given control has an xforms:alert element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:alert element
     */
    public static boolean hasAlert(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticState().getAlertElement(prefixedId) != null;
    }

    /**
     * Return the control's appearance as an exploded QName.
     */
    public String getAppearance() {
        if (appearance == null)
            appearance = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractAttributeValueQName(controlElement, "appearance"));
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

        // Compare values of extension attributes if any
        if (extensionAttributesValues != null) {
            for (Iterator i = extensionAttributesValues.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final QName currentName = (QName) currentEntry.getKey();
                final String currentValue = (String) currentEntry.getValue();

                if (!XFormsUtils.compareStrings(currentValue, other.getExtensionAttributeValue(currentName)))
                    return false;
            }
        }

        return true;
    }

    /**
     * Set this control's binding context.
     */
    public void setBindingContext(PipelineContext pipelineContext, XFormsContextStack.BindingContext bindingContext) {

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

            // Evaluate standard extension attributes
            evaluateExtensionAttributes(pipelineContext, EXTENSION_ATTRIBUTES);
            // Evaluate custom extension attributes
            final QName[] extensionAttributes = getExtensionAttributes();
            if (extensionAttributes != null) {
                evaluateExtensionAttributes(pipelineContext, extensionAttributes);
            }
        }
    }

    private void evaluateExtensionAttributes(PipelineContext pipelineContext, QName[] attributeQNames) {
        final Element controlElement = getControlElement();
        for (int i = 0; i < attributeQNames.length; i++) {
            final QName avtAttributeQName = attributeQNames[i];
            final String attributeValue = controlElement.attributeValue(avtAttributeQName);

            if (attributeValue != null) {
                final String resolvedValue = evaluateAvt(pipelineContext, attributeValue);

                if (extensionAttributesValues == null)
                    extensionAttributesValues = new HashMap();

                extensionAttributesValues.put(avtAttributeQName, resolvedValue);
            }
        }
    }

    public void markDirty() {
        isEvaluated = false;
        isLabelEvaluated = false;
        isHelpEvaluated = false;
        isHintEvaluated = false;
        isAlertEvaluated = false;
        if (extensionAttributesValues != null)
            extensionAttributesValues.clear();
    }

    protected void evaluate(PipelineContext pipelineContext) {
        getLabel(pipelineContext);
        getHint(pipelineContext);
        getHelp(pipelineContext);
        getAlert(pipelineContext);
    }

    /**
     * Return an optional static list of extension attribute QNames provided by the control. If present these
     * attributes are evaluated as AVTs and copied over to the outer control element.
     */
    protected QName[] getExtensionAttributes() {
        return null;
    }

    public XFormsEventObserver getParentEventObserver(XFormsContainer container) {
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
    public String getEscapedHTMLValue(final PipelineContext pipelineContext, String rawValue) {
        // Quick check for the most common attributes, src and href. Ideally we should check more.
        final boolean needsRewrite = rawValue.indexOf("src=") != -1 || rawValue.indexOf("href=") != -1;
        final String result;
        if (needsRewrite) {
            // Rewrite URLs
            final FastStringBuffer sb = new FastStringBuffer(rawValue.length() * 2);// just an approx of the size it may take
            // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
            XFormsUtils.streamHTMLFragment(new ForwardingContentHandler() {

                private boolean isStartElement;

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

                        // Rewrite URI attribute if needed
                        // NOTE: Sould probably use xml:base but we don't have an Element available to gather xml:base information
                        final String rewrittenValue = XFormsUtils.getEscapedURLAttributeIfNeeded(pipelineContext, null, currentName, currentValue);

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
     * Add attributes differences for custom attributes.
     *
     * @param pipelineContext       current pipeline context
     * @param originalControl       original control, possibly null
     * @param attributesImpl        attributes to add to
     * @param isNewRepeatIteration  whether the current controls is within a new repeat iteration
     * @return                      true if any attribute was added, false otherwise
     */
    public boolean addAttributesDiffs(PipelineContext pipelineContext, XFormsSingleNodeControl originalControl,
                                      AttributesImpl attributesImpl, boolean isNewRepeatIteration) {

        final QName[] extensionAttributes = getExtensionAttributes();
        if (extensionAttributes != null) {
            // By default, diff only attributes in the xxforms:* namespace
            return addAttributesDiffs(originalControl, attributesImpl, isNewRepeatIteration, extensionAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);
        } else {
            return false;
        }
    }

    private boolean addAttributesDiffs(XFormsControl other, AttributesImpl attributesImpl, boolean isNewRepeatIteration, QName[] attributeQNames, String namespaceURI) {

        final XFormsControl control1 = (XFormsControl) other;
        final XFormsControl control2 = this;

        boolean added = false;

        for (int i = 0; i < attributeQNames.length; i++) {
            final QName avtAttributeQName = attributeQNames[i];

            // Skip if namespace URI is excluded
            if (namespaceURI != null && !namespaceURI.equals(avtAttributeQName.getNamespaceURI()))
                continue;

            final String value1 = (control1 == null) ? null : control1.getExtensionAttributeValue(avtAttributeQName);
            final String value2 = control2.getExtensionAttributeValue(avtAttributeQName);

            if (!XFormsUtils.compareStrings(value1, value2)) {
                final String attributeValue = value2 != null ? value2 : "";
                // NOTE: For now we use the local name; may want to use a full name?
                added |= addAttributeIfNeeded(attributesImpl, avtAttributeQName.getName(), attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }

        return added;
    }

    protected static boolean addAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            attributesImpl.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
            return true;
        }
    }

    public void addAttributesDiffs(XFormsSingleNodeControl originalControl, ContentHandlerHelper ch, boolean isNewRepeatIteration) {
        final QName[] extensionAttributes = EXTENSION_ATTRIBUTES;
        if (extensionAttributes != null) {
            final XFormsControl control1 = (XFormsControl) originalControl;
            final XFormsControl control2 = this;

            for (int i = 0; i < extensionAttributes.length; i++) {
                final QName avtAttributeQName = extensionAttributes[i];

                final String value1 = (control1 == null) ? null : control1.getExtensionAttributeValue(avtAttributeQName);
                final String value2 = control2.getExtensionAttributeValue(avtAttributeQName);

                if (!XFormsUtils.compareStrings(value1, value2)) {
                    final String attributeValue = value2 != null ? value2 : "";

                    final AttributesImpl attributesImpl = new AttributesImpl();
                    // Control id
                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, control2.getEffectiveId());

                    // The client does not store an HTML representation of the xxforms:attribute control, so we
                    // have to output these attributes.

                    // HTML element id
                    addAttributeIfNeeded(attributesImpl, "for", control2.getEffectiveId(), isNewRepeatIteration, false);

                    // Attribute name
                    addAttributeIfNeeded(attributesImpl, "name", avtAttributeQName.getName(), isNewRepeatIteration, false);

                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "attribute", attributesImpl);
                    ch.text(attributeValue);
                    ch.endElement();
                }
            }
        }
    }

    /**
     * Evaluate an attribute of the control as an AVT.
     *
     * @param pipelineContext   current pipeline contextStack
     * @param avt               value of the attribute
     * @return                  value of the AVT or null if cannot be computed
     */
    protected String evaluateAvt(PipelineContext pipelineContext, String avt) {


        if (avt.indexOf('{') == -1) {
            // Not an AVT

            return avt;
        } else {
            // Possible AVT

            final XFormsContextStack contextStack = getContextStack();
            contextStack.setBinding(this);
    
            // NOTE: the control may or may not be bound, so don't use getBoundNode()
            final List contextNodeset = bindingContext.getNodeset();
            if (contextNodeset.size() == 0) {
                // TODO: in the future we should be able to try evaluating anyway
                return null;
            } else {
                final Map prefixToURIMap = containingDocument.getNamespaceMappings(getControlElement());
                return XFormsUtils.resolveAttributeValueTemplates(pipelineContext, contextNodeset,
                            bindingContext.getPosition(), bindingContext.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                            contextStack.getFunctionContext(), prefixToURIMap, getLocationData(), avt);
            }
        }
    }

    protected String getExtensionAttributeValue(QName attributeName) {
        return (extensionAttributesValues == null) ? null : (String) extensionAttributesValues.get(attributeName);
    }

    /**
     * Add all non-null values of extension attributes to the given list of attributes.
     *
     * @param attributesImpl    attributes to add to
     * @param namespaceURI      restriction on namespace URI, or null if all attributes
     */
    public void addExtensionAttributes(AttributesImpl attributesImpl, String namespaceURI) {
        if (extensionAttributesValues != null) {
            for (Iterator i = extensionAttributesValues.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final QName currentName = (QName) currentEntry.getKey();

                // Skip if namespace URI is excluded
                if (namespaceURI != null && !namespaceURI.equals(currentName.getNamespaceURI()))
                    continue;

                final String currentValue = (String) currentEntry.getValue();

                if (currentValue != null) {
                    final String localName = currentName.getName();
                    attributesImpl.addAttribute("", localName, localName, ContentHandlerHelper.CDATA, currentValue);
                }
            }
        }
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
                // The trees don't keep wasteful references
                cloned.currentLocal = cloned.initialLocal;
                this.initialLocal = this.currentLocal;
            } else {
                // The new tree must have its own copy
                // NOTE: We could implement a copy-on-write flag here
                cloned.initialLocal = cloned.currentLocal = (XFormsControlLocal) this.currentLocal.clone();
            }
        }

        // Handle extension attributes if any
        if (extensionAttributesValues != null) {
            cloned.extensionAttributesValues = new HashMap(this.extensionAttributesValues);
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

    /**
     * Compare two nodesets.
     *
     * @param nodeset1  List<NodeInfo> first nodeset
     * @param nodeset2  List<NodeInfo> second nodeset
     * @return  true iif the nodesets point to the same nodes
     */
    protected boolean compareNodesets(List nodeset1, List nodeset2) {

        // Can't be the same if the size has changed
        if (nodeset1.size() != nodeset2.size())
            return false;

        final Iterator j = nodeset2.iterator();
        for (Iterator i = nodeset1.iterator(); i.hasNext(); ) {
            final NodeInfo currentNodeInfo1 = (NodeInfo) i.next();
            final NodeInfo currentNodeInfo2 = (NodeInfo) j.next();

            // Found a difference
            if (!currentNodeInfo1.isSameNodeInfo(currentNodeInfo2))
                return false;
        }
        return true;
    }
}
