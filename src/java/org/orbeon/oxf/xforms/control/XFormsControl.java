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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.converter.XHTMLRewrite;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ForwardingContentHandler;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ValueRepresentation;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Represents an XForms control.
 */
public abstract class XFormsControl implements XFormsEventTarget, XFormsEventObserver, ExternalCopyable {

    // List of standard extension attributes
    private static final QName[] STANDARD_EXTENSION_ATTRIBUTES = {
            XFormsConstants.STYLE_QNAME,
            XFormsConstants.CLASS_QNAME
    };

    private final XBLContainer container;
    protected final XFormsContainingDocument containingDocument;

    // Static information (never changes for the lifetime of the containing document)
    private final Element controlElement;
    private final String id;
    private final String prefixedId;
    private final String name;

    private String appearance;// could become more dynamic in the future
    private String mediatype;// could become more dynamic in the future

    // Semi-dynamic information (depends on the tree of controls, but does not change over time)
    private XFormsControl parent;

    // Dynamic information (changes depending on the content of XForms instances)
    private String previousEffectiveId;
    private String tempEffectiveId;
    private String effectiveId;

    protected XFormsContextStack.BindingContext bindingContext;

    private boolean isEvaluated;

    // Relevance
    private boolean relevant;
    private boolean wasRelevant;

    // Optional extension attributes supported by the control
    private Map<QName, String> extensionAttributesValues;

    // Label, help, hint and alert (evaluated lazily)
    private LHHA label, help, hint, alert;

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

    public XFormsControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        this.container = container;
        this.containingDocument = (container != null) ? container.getContainingDocument() : null;// some special cases pass null (bad, we know...)
        this.parent = parent;
        this.controlElement = element;
        this.name = name;

        this.id = (element != null) ? element.attributeValue("id") : null;
        this.prefixedId = XFormsUtils.getPrefixedId(effectiveId);
        this.effectiveId = effectiveId;
    }

    public String getId() {
        return id;
    }

    public XBLContainer getXBLContainer() {
        return container;
    }

    public XBLContainer getXBLContainer(XFormsContainingDocument containingDocument) {
        return getXBLContainer();
    }

    protected XFormsContextStack getContextStack() {
        return container.getContextStack();
    }

    public IndentedLogger getIndentedLogger() {
        return containingDocument.getControls().getIndentedLogger();
    }

    public void iterationRemoved(PropertyContext propertyContext) {
        // NOP, can be overridden
    }

    public XBLBindings.Scope getResolutionScope() {
        return containingDocument.getStaticState().getXBLBindings().getResolutionScopeByPrefixedId(getPrefixedId());
    }

    public XBLBindings.Scope getChildElementScope(Element element) {
        return containingDocument.getStaticState().getXBLBindings().getResolutionScopeByPrefixedId(getXBLContainer().getFullPrefix() + element.attributeValue("id"));
    }

    /**
     * Update this control's effective id based on the parent's effective id.
     */
    public void updateEffectiveId() {
        final String parentEffectiveId = getParent().getEffectiveId();
        final String parentSuffix = XFormsUtils.getEffectiveIdSuffix(parentEffectiveId);

        if (!parentSuffix.equals("")) {
            // Keep initial effective id for the next refresh
            if (tempEffectiveId == null)
                tempEffectiveId = effectiveId;
            // Update effective id
            effectiveId = XFormsUtils.getPrefixedId(effectiveId) + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + parentSuffix;
        } else {
            // Nothing to do as we are not in repeated content
        }
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    public String getPrefixedId() {
        return prefixedId;
    }

    protected void setEffectiveId(String effectiveId) {
        this.effectiveId = effectiveId;
    }

    public LocationData getLocationData() {
        return (controlElement != null) ? (LocationData) controlElement.getData() : null;
    }

    public boolean isRelevant() {
        return relevant;
    }

    protected final void setRelevant(boolean relevant) {
        this.relevant = relevant;
    }

    protected boolean computeRelevant() {
        // By default: if there is a parent, we have the same relevance as the parent, otherwise we are top-level so
        // we are relevant by default
        final XFormsControl parent = getParent();
        return (parent == null) || parent.isRelevant();
    }

    public String getPreviousEffectiveId() {
        return previousEffectiveId;
    }

    public boolean wasRelevant() {
        return wasRelevant;
    }

    public boolean supportsRefreshEvents() {
        // TODO: should probably return true because most controls could then dispatch relevance events
        return false;
    }

    public static boolean supportsRefreshEvents(XFormsControl control) {
        return control != null && control.supportsRefreshEvents();
    }

    public String getLabel(PropertyContext propertyContext) {
        if (label == null) {
            final Element lhhaElement = containingDocument.getStaticState().getLabelElement(getPrefixedId());
            label = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(this, lhhaElement, isSupportHTMLLabels());
        }

        return label.getValue(propertyContext);
    }

    public String getEscapedLabel(PipelineContext pipelineContext) {
        getLabel(pipelineContext);
        return label.getEscapedValue(pipelineContext);
    }

    public boolean isHTMLLabel(PropertyContext propertyContext) {
        getLabel(propertyContext);
        return label.isHTML(propertyContext);
    }

    public String getHelp(PropertyContext propertyContext) {
        if (help == null) {
            final Element lhhaElement = containingDocument.getStaticState().getHelpElement(getPrefixedId());
            help = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(this, lhhaElement, true);
        }

        return help.getValue(propertyContext);
    }

    public String getEscapedHelp(PipelineContext pipelineContext) {
        getHelp(pipelineContext);
        return help.getEscapedValue(pipelineContext);
    }

    public boolean isHTMLHelp(PropertyContext propertyContext) {
        getHelp(propertyContext);
        return help.isHTML(propertyContext);
    }

    public String getHint(PropertyContext propertyContext) {
        if (hint == null) {
            final Element lhhaElement = containingDocument.getStaticState().getHintElement(getPrefixedId());
            hint = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(this, lhhaElement, isSupportHTMLHints());
        }

        return hint.getValue(propertyContext);
    }

    public String getEscapedHint(PipelineContext pipelineContext) {
        getHint(pipelineContext);
        return hint.getEscapedValue(pipelineContext);
    }

    public boolean isHTMLHint(PropertyContext propertyContext) {
        getHint(propertyContext);
        return hint.isHTML(propertyContext);
    }

    public String getAlert(PropertyContext propertyContext) {
        if (alert == null) {
            final Element lhhaElement = containingDocument.getStaticState().getAlertElement(getPrefixedId());
            alert = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(this, lhhaElement, true);
        }
        return alert.getValue(propertyContext);
    }

    public boolean isHTMLAlert(PropertyContext propertyContext) {
        getAlert(propertyContext);
        return alert.isHTML(propertyContext);
    }

    public String getEscapedAlert(PipelineContext pipelineContext) {
        getAlert(pipelineContext);
        return alert.getEscapedValue(pipelineContext);
    }

    public boolean isHTMLAlert(PipelineContext pipelineContext) {
        getAlert(pipelineContext);
        return alert.isHTML(pipelineContext);
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
            appearance = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractAttributeValueQName(controlElement, XFormsConstants.APPEARANCE_QNAME.getName()));
        return appearance;
    }

    public String getAppearancePlain() {
        return controlElement.attributeValue(XFormsConstants.APPEARANCE_QNAME);
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
     * @param propertyContext   current context
     * @param other             other control
     * @return                  true if the controls are identical for the purpose of an external diff, false otherwise
     */
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null)
            return false;

        if (this == other)
            return true;

        // Compare only what matters

        if (relevant != other.relevant)
            return false;

        if (!XFormsUtils.compareStrings(getLabel(propertyContext), other.getLabel(propertyContext)))
            return false;
        if (!XFormsUtils.compareStrings(getHelp(propertyContext), other.getHelp(propertyContext)))
            return false;
        if (!XFormsUtils.compareStrings(getHint(propertyContext), other.getHint(propertyContext)))
            return false;
        if (!XFormsUtils.compareStrings(getAlert(propertyContext), other.getAlert(propertyContext)))
            return false;

        // Compare values of extension attributes if any
        if (extensionAttributesValues != null) {
            for (final Map.Entry<QName, String> currentEntry: extensionAttributesValues.entrySet()) {
                final QName currentName = currentEntry.getKey();
                final String currentValue = currentEntry.getValue();

                if (!XFormsUtils.compareStrings(currentValue, other.getExtensionAttributeValue(currentName)))
                    return false;
            }
        }

        return true;
    }

    /**
     * Set this control's binding context.
     */
    public void setBindingContext(PropertyContext propertyContext, XFormsContextStack.BindingContext bindingContext, boolean isCreate) {
        this.bindingContext = bindingContext;
    }

    /**
     * Return the binding context for this control.
     */
    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    public XFormsContextStack.BindingContext getBindingContext(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {
        return getBindingContext();
    }

    public final void evaluateIfNeeded(PropertyContext propertyContext, boolean isRefresh) {
        if (!isEvaluated) {
            isEvaluated = true;// be careful with this flag, you can get into a recursion if you don't set it before calling evaluate()
            try {
                evaluate(propertyContext, isRefresh);

                // Evaluate standard extension attributes
                evaluateExtensionAttributes(propertyContext, STANDARD_EXTENSION_ATTRIBUTES);
                // Evaluate custom extension attributes
                final QName[] extensionAttributes = getExtensionAttributes();
                if (extensionAttributes != null) {
                    evaluateExtensionAttributes(propertyContext, extensionAttributes);
                }
            } catch (ValidationException e) {
                    throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "evaluating control",
                        getControlElement(), "element", Dom4jUtils.elementToDebugString(getControlElement())));
            }
        }
    }

    private void evaluateExtensionAttributes(PropertyContext propertyContext, QName[] attributeQNames) {
        final Element controlElement = getControlElement();
        for (final QName avtAttributeQName: attributeQNames) {
            final String attributeValue = controlElement.attributeValue(avtAttributeQName);

            if (attributeValue != null) {
                // NOTE: This can return null if there is no context
                final String resolvedValue = evaluateAvt(propertyContext, attributeValue);

                if (extensionAttributesValues == null)
                    extensionAttributesValues = new HashMap<QName, String>();

                extensionAttributesValues.put(avtAttributeQName, resolvedValue);
            }
        }
    }

    /**
     * Mark a control as "dirty" to mean that the control:
     *
     * o needs to be re-evaluated
     * o must save its current state for purposes of refresh events dispatch
     *
     * As of 2010-02 this is called:
     *
     * o by UpdateBindingsListener during a refresh, just before evaluation
     * o when going online, before handling each external event
     */
    public void markDirty() {

        // Keep previous values for refresh updates
        // NOTE: effectiveId might have changed already upon updateEffectiveId(); in which case, use tempEffectiveId
        previousEffectiveId = (tempEffectiveId != null) ? tempEffectiveId : effectiveId;
        tempEffectiveId = null;
        wasRelevant = relevant;

        // Clear everything
        isEvaluated = false;
        if (label != null)
            label.markDirty();
        if (hint != null)
            hint.markDirty();
        if (help != null)
            help.markDirty();
        if (alert != null)
            alert.markDirty();
        if (extensionAttributesValues != null)
            extensionAttributesValues.clear();
    }

    /**
     * Evaluate this control.
     *
     * @param propertyContext   current context
     * @param isRefresh         true if part of the refresh process, false if initialization and new repeat iteration creation
     */
    protected void evaluate(PropertyContext propertyContext, boolean isRefresh) {

        // Relevance is a property of all controls
        setRelevant(computeRelevant());

        // NOTE: We no longer evaluate LHHA here, instead we do lazy evaluation. This is good in particular when there
        // are multiple refreshes during an Ajax request, and LHHA values are only needed in the end.

        if (!isRefresh) {
            // Sync values
            previousEffectiveId = effectiveId;
            wasRelevant = relevant;
        }
    }

    /**
     * Return an optional static list of extension attribute QNames provided by the control. If present these
     * attributes are evaluated as AVTs and copied over to the outer control element.
     */
    protected QName[] getExtensionAttributes() {
        return null;
    }

    public XFormsEventObserver getParentEventObserver(XBLContainer container) {
        // Consider that the parent of top-level controls is the containing document. This allows events to propagate to
        // the top-level.
        return parent != null ? parent : containingDocument;
    }

    public List getEventHandlers(XBLContainer container) {
        return containingDocument.getStaticState().getEventHandlers(getPrefixedId());
    }

    public void performDefaultAction(PropertyContext propertyContext, XFormsEvent event) {
        if (XFormsEvents.XXFORMS_REPEAT_FOCUS.equals(event.getName())
                || XFormsEvents.XFORMS_FOCUS.equals(event.getName())) {

            // Try to update xforms:repeat indexes based on this
            {
                // Find current path through ancestor xforms:repeat elements, if any
                final List<String> repeatIterationsToModify = new ArrayList<String>();
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
                    for (final String repeatIterationEffectiveId : repeatIterationsToModify) {
                        final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl) controls.getObjectByEffectiveId(repeatIterationEffectiveId);
                        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) repeatIterationControl.getParent();

                        final int newRepeatIndex = repeatIterationControl.getIterationIndex();

                        final IndentedLogger indentedLogger = controls.getIndentedLogger();
                        if (indentedLogger.isDebugEnabled()) {
                            indentedLogger.logDebug("xforms:repeat", "setting index upon focus change",
                                    "new index", Integer.toString(newRepeatIndex));
                        }

                        repeatControl.setIndex(propertyContext, newRepeatIndex);
                    }
                }

                if (XFormsEvents.XFORMS_FOCUS.equals(event.getName())) {
                    // Focus on current control if possible
                    setFocus();
                }
            }
        } else if (XFormsEvents.XFORMS_HELP.equals(event.getName())) {
            containingDocument.setClientHelpEffectiveControlId(getEffectiveId());
        }
    }

    public void performTargetAction(PropertyContext propertyContext, XBLContainer container, XFormsEvent event) {
        // NOP
    }

    public boolean isStaticReadonly() {
        return false;
    }

    /**
     * Rewrite an HTML value which may contain URLs, for example in @src or @href attributes.
     *
     * @param propertyContext   current context
     * @param rawValue          value to rewrite
     * @return                  rewritten value
     */
    public String getEscapedHTMLValue(final PropertyContext propertyContext, String rawValue) {

        if (rawValue == null)
            return null;

        // Quick check for the most common attributes, src and href. Ideally we should check more.
        final boolean needsRewrite = rawValue.indexOf("src=") != -1 || rawValue.indexOf("href=") != -1;
        final String result;
        if (needsRewrite) {
            // Rewrite URLs
            final StringBuilder sb = new StringBuilder(rawValue.length() * 2);// just an approx of the size it may take
            // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
            XFormsUtils.streamHTMLFragment(new XHTMLRewrite().getRewriteContentHandler(propertyContext, new ForwardingContentHandler() {

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

                        sb.append(' ');
                        sb.append(currentName);
                        sb.append("=\"");
                        sb.append(currentValue);
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
            }, true), rawValue, getLocationData(), "xhtml");
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
    public boolean addCustomAttributesDiffs(PipelineContext pipelineContext, XFormsSingleNodeControl originalControl,
                                            AttributesImpl attributesImpl, boolean isNewRepeatIteration) {

        final QName[] extensionAttributes = getExtensionAttributes();
        // By default, diff only attributes in the xxforms:* namespace
        return extensionAttributes != null && addAttributesDiffs(originalControl, attributesImpl, isNewRepeatIteration, extensionAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    private boolean addAttributesDiffs(XFormsControl control1, AttributesImpl attributesImpl, boolean isNewRepeatIteration, QName[] attributeQNames, String namespaceURI) {

        final XFormsControl control2 = this;

        boolean added = false;

        for (final QName avtAttributeQName: attributeQNames) {
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

    public void addStandardAttributesDiffs(XFormsSingleNodeControl originalControl, ContentHandlerHelper ch, boolean isNewRepeatIteration) {
        final QName[] extensionAttributes = STANDARD_EXTENSION_ATTRIBUTES;
        if (extensionAttributes != null) {
            final XFormsControl control2 = this;

            for (final QName avtAttributeQName: extensionAttributes) {

                // Skip @class because this is handled separately
                if (avtAttributeQName.equals(XFormsConstants.CLASS_QNAME))
                    continue;

                final String value1 = (originalControl == null) ? null : originalControl.getExtensionAttributeValue(avtAttributeQName);
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

    protected static boolean addAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            attributesImpl.addAttribute("", name, name, ContentHandlerHelper.CDATA, value);
            return true;
        }
    }

    /**
     * Evaluate an attribute of the control as an AVT.
     *
     * @param propertyContext   current context
     * @param attributeValue    value of the attribute
     * @return                  value of the AVT or null if cannot be computed
     */
    protected String evaluateAvt(PropertyContext propertyContext, String attributeValue) {

        if (attributeValue.indexOf('{') == -1) {
            // Definitely not an AVT

            return attributeValue;
        } else {
            // Possible AVT

            // NOTE: the control may or may not be bound, so don't use getBoundItem()
            final List<Item> contextNodeset = bindingContext.getNodeset();
            if (contextNodeset == null || contextNodeset.size() == 0) {
                // TODO: in the future we should be able to try evaluating anyway
                return null;
            } else {

                // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
                // Reason is that XPath functions might use the context stack to get the current model, etc.
                final XFormsContextStack contextStack = getContextStack();
                contextStack.setBinding(this);

                // Evaluate
                try {
                    return XPathCache.evaluateAsAvt(propertyContext, contextNodeset, bindingContext.getPosition(), attributeValue, getNamespaceMappings(),
                        bindingContext.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(), getFunctionContext(), null, getLocationData());
                } catch (Exception e) {
                    // Don't consider this as fatal
                    // TODO: must dispatch xforms-compute-error? Check if safe to do so.
                    final IndentedLogger indentedLogger = containingDocument.getControls().getIndentedLogger();
                    if (indentedLogger.isInfoEnabled())
                        indentedLogger.logInfo("", "exception while evaluating XPath expression", e);

                    return null;
                } finally {
                    // Restore function context to prevent leaks caused by context pointing to removed controls
                    returnFunctionContext();
                }
            }
        }
    }

    /**
     * Evaluate an XPath expression as a string in the context of this control.
     *
     * @param propertyContext   current context
     * @param xpathString       XPath expression
     * @return                  value, or null if cannot be computed
     */
    protected String evaluateAsString(PropertyContext propertyContext, String xpathString) {

        // NOTE: the control may or may not be bound, so don't use getBoundNode()
        final List<Item> contextNodeset = bindingContext.getNodeset();
        if (contextNodeset == null || contextNodeset.size() == 0) {
            // TODO: in the future we should be able to try evaluating anyway
            return null;
        } else {
            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            // Reason is that XPath functions might use the context stack to get the current model, etc.
            final XFormsContextStack contextStack = getContextStack();
            contextStack.setBinding(this);

            try {
                return XPathCache.evaluateAsString(propertyContext, contextNodeset, bindingContext.getPosition(),
                                    xpathString, getNamespaceMappings(), bindingContext.getInScopeVariables(),
                                    XFormsContainingDocument.getFunctionLibrary(),
                                    getFunctionContext(), null, getLocationData());
            } catch (Exception e) {
                // Don't consider this as fatal
                // TODO: must dispatch xforms-compute-error? Check if safe to do so.
                final IndentedLogger indentedLogger = containingDocument.getControls().getIndentedLogger();
                if (indentedLogger.isInfoEnabled())
                    indentedLogger.logInfo("", "exception while evaluating XPath expression", e);

                return null;
            } finally {
                // Restore function context to prevent leaks caused by context pointing to removed controls
                returnFunctionContext();
            }
        }
    }

    /**
     * Evaluate an XPath expression as a string in the context of this control.
     *
     * @param propertyContext       current context
     * @param contextItem           context item
     * @param xpathString           XPath expression
     * @param prefixToURIMap        namespace mappings to use
     * @param variableToValueMap    variables to use
     * @return                      value, or null if cannot be computed
     */
    protected String evaluateAsString(PropertyContext propertyContext, Item contextItem, String xpathString,
                                      Map<String, String> prefixToURIMap, Map<String, ValueRepresentation> variableToValueMap) {

        if (contextItem == null) {
            // TODO: in the future we should be able to try evaluating anyway
            return null;
        } else {
            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            // Reason is that XPath functions might use the context stack to get the current model, etc.
            final XFormsContextStack contextStack = getContextStack();
            contextStack.setBinding(this);

            // Evaluate
            try {
                return XPathCache.evaluateAsString(propertyContext, contextItem,
                                xpathString, prefixToURIMap, variableToValueMap,
                                XFormsContainingDocument.getFunctionLibrary(),
                                getFunctionContext(), null, getLocationData());
            } catch (Exception e) {
                // Don't consider this as fatal
                // TODO: must dispatch xforms-compute-error? Check if safe to do so.
                final IndentedLogger indentedLogger = containingDocument.getControls().getIndentedLogger();
                if (indentedLogger.isInfoEnabled())
                    indentedLogger.logInfo("", "exception while evaluating XPath expression", e);

                return null;
            } finally {
                // Restore function context to prevent leaks caused by context pointing to removed controls
                returnFunctionContext();
            }
        }
    }

    /**
     * Return an XPath function context having this control as source control.
     *
     * @return XPath function context
     */
    private XFormsFunction.Context getFunctionContext() {
        return getContextStack().getFunctionContext(getEffectiveId());
    }

    private void returnFunctionContext() {
        getContextStack().returnFunctionContext();
    }

    /**
     * Return the namespace mappings for this control.
     *
     * @return              Map<String prefix, String uri>
     */
    public Map<String, String> getNamespaceMappings() {
        return container.getNamespaceMappings(controlElement);
    }

    public String getExtensionAttributeValue(QName attributeName) {
        return (extensionAttributesValues == null) ? null : extensionAttributesValues.get(attributeName);
    }

    /**
     * Add all non-null values of extension attributes to the given list of attributes.
     *
     * @param attributesImpl    attributes to add to
     * @param namespaceURI      restriction on namespace URI, or null if all attributes
     */
    public void addExtensionAttributes(AttributesImpl attributesImpl, String namespaceURI) {
        if (extensionAttributesValues != null) {
            for (final Map.Entry<QName, String> currentEntry: extensionAttributesValues.entrySet()) {
                final QName currentName = currentEntry.getKey();

                // Skip if namespace URI is excluded
                if (namespaceURI != null && !namespaceURI.equals(currentName.getNamespaceURI()))
                    continue;

                // Skip @class because this is handled separately
                if (currentName.equals(XFormsConstants.CLASS_QNAME))
                    continue;

                final String currentValue = currentEntry.getValue();

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
     * @return  Map<String name, String value>
     */
    public Map<String, String> serializeLocal() {
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
    public Object getBackCopy(PropertyContext propertyContext) {
        
        // Evaluate lazy values
        getLabel(propertyContext);
        getHelp(propertyContext);
        getHint(propertyContext);
        getAlert(propertyContext);
        
        // NOTE: this.parent is handled by subclasses
        final XFormsControl cloned;
        try {
            cloned = (XFormsControl) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);
        }

        // Clone LHHA if not null and not constant
        if (label != null && label != NULL_LHHA) {
            cloned.label = (LHHA) label.clone();
        }
        if (help != null && help != NULL_LHHA) {
            cloned.help = (LHHA) help.clone();
        }
        if (hint != null && hint != NULL_LHHA) {
            cloned.hint = (LHHA) hint.clone();
        }
        if (alert != null && alert != NULL_LHHA) {
            cloned.alert = (LHHA) alert.clone();
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
            cloned.extensionAttributesValues = new HashMap<QName, String>(this.extensionAttributesValues);
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
     * @param nodeset1  first nodeset
     * @param nodeset2  second nodeset
     * @return          true iif the nodesets point to the same nodes
     */
    protected boolean compareNodesets(List<Item> nodeset1, List<Item> nodeset2) {

        // Can't be the same if the size has changed
        if (nodeset1.size() != nodeset2.size())
            return false;

        final Iterator<Item> j = nodeset2.iterator();
        for (Item currentItem1: nodeset1) {
            final Item currentItem2 = j.next();
            if (!currentItem1.equals(currentItem2)) {// equals() is the same as isSameNodeInfo() for NodeInfo, and compares the values for values
                // Found a difference
                return false;
            }
        }
        return true;
    }

    /**
     * Set the focus on this control.
     *
     * @return  true iif control accepted focus
     */
    public boolean setFocus() {
        // By default, a control doesn't accept focus
        return false;
    }

    // Allow special keypress event everywhere as client-side observer for those might be any control
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.KEYPRESS);
    }

    /**
     * Check whether this concrete control supports receiving the external event specified.
     *
     * @param indentedLogger    logger
     * @param logType           log type
     * @param eventName         event name to check
     * @return                  true iif the event is supported
     */
    public boolean allowExternalEvent(IndentedLogger indentedLogger, String logType, String eventName) {
        if (getAllowedExternalEvents().contains(eventName) || ALLOWED_EXTERNAL_EVENTS.contains(eventName)) {
            return true;
        } else {
            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug(logType, "ignoring invalid client event on control", "control type", getName(), "control id", getEffectiveId(), "event name", eventName);
            }
            return false;
        }
    }

    protected Set<String> getAllowedExternalEvents() {
        return Collections.emptySet();
    }

    // Empty LHHA
    protected static final LHHA NULL_LHHA = new LHHA();

    protected static class LHHA implements Cloneable {
        public String getValue(PropertyContext propertyContext) {
            return null;
        }

        public String getEscapedValue(PipelineContext pipelineContext) {
            return null;
        }

        public boolean isHTML(PropertyContext propertyContext) {
            return false;
        }

        public void markDirty() {}

        @Override
        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                throw new OXFException(e);
            }
        }
    }

    // LHHA corresponding to an existing xforms:label, etc. element
    private static class ConcreteLHHA extends LHHA {

        private final XFormsControl control;
        private final Element lhhaElement;
        private final boolean supportsHTML;

        private String value;
        private boolean isEvaluated;
        private boolean isHTML;

        public ConcreteLHHA(XFormsControl control, Element lhhaElement, boolean supportsHTML) {

            assert lhhaElement != null : "LHHA element can't be null";

            this.control = control;
            this.lhhaElement = lhhaElement;
            this.supportsHTML = supportsHTML;
        }

        @Override
        public String getValue(PropertyContext propertyContext) {

            assert control.isEvaluated : "control must be evaluated before LHHA value is evaluated";

            if (!isEvaluated) {
                if (control.isRelevant()) {
                    value = getLabelHelpHintAlertValue(propertyContext);
                    isHTML = value != null && control.tempContainsHTML[0];
                } else {
                    // NOTE: if the control is not relevant, nobody should ask about this in the first place
                    value = null;
                    isHTML = false;
                }
                isEvaluated = true;
            }
            return value;
        }

        @Override
        public String getEscapedValue(PipelineContext pipelineContext) {
            getValue(pipelineContext);
            return isHTML ? control.getEscapedHTMLValue(pipelineContext, value) : XMLUtils.escapeXMLMinimal(value);
        }

        @Override
        public boolean isHTML(PropertyContext propertyContext) {
            getValue(propertyContext);
            return isHTML;
        }

        @Override
        public void markDirty() {
            value = null;
            isEvaluated = false;
            isHTML = false;
        }

        /**
         * Get the value of a LHHA related to this control.
         *
         * @param propertyContext       current context
         * @return                      string containing the result of the evaluation, null if evaluation failed
         */
        private String getLabelHelpHintAlertValue(PropertyContext propertyContext) {

            final XFormsContextStack contextStack = control.getContextStack();
            final String value;
            if (lhhaElement.getParent() == control.getControlElement()) {
                // LHHA is direct child of control, evaluate within context
                contextStack.setBinding(control);
                contextStack.pushBinding(propertyContext, lhhaElement, control.effectiveId, control.getChildElementScope(lhhaElement));
                value = XFormsUtils.getElementValue(propertyContext, control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, control.tempContainsHTML);
                contextStack.popBinding();
            } else {
                // LHHA is somewhere else, assumed as a child of xforms:* or xxforms:*

                // Find context object for XPath evaluation
                final Element contextElement = lhhaElement.getParent();
                final String contextStaticId = contextElement.attributeValue("id");
                final String contextEffectiveId;
                if (contextStaticId == null) {
                    // Assume we are at the top-level
                    contextStack.resetBindingContext(propertyContext);
                    contextEffectiveId = control.container.getFirstControlEffectiveId();
                } else {
                    // Not at top-level, find containing object
                    final XFormsControl ancestorContextControl = findAncestorContextControl(contextStaticId, lhhaElement.attributeValue("id"));
                    if (ancestorContextControl != null) {
                        contextStack.setBinding(ancestorContextControl);
                        contextEffectiveId = ancestorContextControl.effectiveId;
                    } else {
                        contextEffectiveId = null;
                    }
                }

                if (contextEffectiveId != null) {
                    // Push binding relative to context established above and evaluate
                    contextStack.pushBinding(propertyContext, lhhaElement, contextEffectiveId, control.getResolutionScope());
                    value = XFormsUtils.getElementValue(propertyContext, control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, control.tempContainsHTML);
                    contextStack.popBinding();
                } else {
                    // Do as if there was no LHHA
                    value = null;
                }
            }
            return value;
        }

        private XFormsControl findAncestorContextControl(String contextStaticId, String lhhaStaticId) {
            // NOTE: LHHA element must be in the same resolution scope as the current control (since @for refers to @id)
            final XBLBindings.Scope lhhaScope = control.getResolutionScope();
            final String lhhaPrefixedId = lhhaScope.getPrefixedIdForStaticId(lhhaStaticId);

            // Assume that LHHA element is within same repeat iteration as its related control
            final String contextPrefixedId = XFormsUtils.getRelatedEffectiveId(lhhaPrefixedId, contextStaticId);
            final String contextEffectiveId = contextPrefixedId + XFormsUtils.getEffectiveIdSuffixWithSeparator(control.effectiveId);

            Object ancestorObject = control.container.getContainingDocument().getObjectByEffectiveId(contextEffectiveId);
            while (ancestorObject instanceof XFormsControl) {
                final XFormsControl ancestorControl = (XFormsControl) ancestorObject;
                if (ancestorControl.getResolutionScope() == lhhaScope) {
                    // Found ancestor in right scope
                    return ancestorControl;
                }
                ancestorObject = ancestorControl.getParent();
            }
            return null;
        }
    }

    public boolean equalsExternalRecurse(PropertyContext propertyContext, XFormsControl other) {
        // By default there are no children controls
        return equalsExternal(propertyContext, other);
    }

    /**
     * Whether the control support Ajax updates.
     *
     * @return true iif it does
     */
    public boolean supportAjaxUpdates() {
        return true;
    }

    /**
     * Whether the control support full Ajax updates.
     *
     * @return true iif it does
     */
    public boolean supportFullAjaxUpdates() {
        return true;
    }

    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {
        // NOP
    }
}
