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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.converter.XHTMLRewrite;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.analysis.controls.AppearanceTrait;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XXFormsBindingErrorEvent;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ForwardingXMLReceiver;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.ValueRepresentation;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import scala.Tuple3;

import java.util.*;

/**
 * Represents an XForms control.
 */
public abstract class XFormsControl implements XFormsEventTarget, XFormsEventObserver, ExternalCopyable {

    // List of standard extension attributes
    // TODO: standard and non-standard extension attributes should be partly handled in the ControlAnalysis hierarchy
    private static final QName[] STANDARD_EXTENSION_ATTRIBUTES = {
            XFormsConstants.STYLE_QNAME,
            XFormsConstants.CLASS_QNAME
    };

    private final XBLContainer container;
    public final XFormsContainingDocument containingDocument;

    // Static information (never changes for the lifetime of the containing document)
    private final Element controlElement;
    private final String id;
    private final String prefixedId;
    private final String name;

    private String mediatype;// could become more dynamic in the future

    // Semi-dynamic information (depends on the tree of controls, but does not change over time)
    private XFormsControl parent;

    // Dynamic information (changes depending on the content of XForms instances)
    private String previousEffectiveId;
    private String effectiveId;

    protected XFormsContextStack.BindingContext bindingContext;

    // Relevance
    private boolean relevant = false;
    private boolean wasRelevant;

    // Optional extension attributes supported by the control
    // TODO: must be evaluated lazily
    private Map<QName, String> extensionAttributesValues;

    // Label, help, hint and alert (evaluated lazily)
    private Map<XFormsConstants.LHHA, LHHA> lhha = new HashMap<XFormsConstants.LHHA, LHHA>(XFormsConstants.LHHA.values().length);

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

        this.id = (element != null) ? element.attributeValue(XFormsConstants.ID_QNAME) : null;
        this.prefixedId = XFormsUtils.getPrefixedId(effectiveId);
        this.effectiveId = effectiveId;
    }

    public final String getId() {
        return id;
    }

    public final String getPrefixedId() {
     return prefixedId;
    }

    public ElementAnalysis getElementAnalysis() {
        // TODO: Control must point to this directly
        return getXBLContainer().getPartAnalysis().getControlAnalysis(getPrefixedId());
    }

    public final XBLContainer getXBLContainer() {
        return container;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public final XBLContainer getXBLContainer(XFormsContainingDocument containingDocument) {
        return getXBLContainer();
    }

    protected XFormsContextStack getContextStack() {
        return container.getContextStack();
    }

    public IndentedLogger getIndentedLogger() {
        return containingDocument.getControls().getIndentedLogger();
    }

    public void iterationRemoved() {
        // NOP, can be overridden
    }

    public final XBLBindingsBase.Scope getResolutionScope() {
        return container.getPartAnalysis().getResolutionScopeByPrefixedId(getPrefixedId());
    }

    public XBLBindingsBase.Scope getChildElementScope(Element element) {
        return container.getPartAnalysis().getResolutionScopeByPrefixedId(getXBLContainer().getFullPrefix() + element.attributeValue(XFormsConstants.ID_QNAME));
    }

    /**
     * Update this control's effective id based on the parent's effective id.
     */
    public void updateEffectiveId() {
        final String parentEffectiveId = getParent().getEffectiveId();
        final String parentSuffix = XFormsUtils.getEffectiveIdSuffix(parentEffectiveId);

        if (!parentSuffix.equals("")) {
            // Update effective id
            effectiveId = XFormsUtils.getPrefixedId(effectiveId) + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + parentSuffix;
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

    /**
     * Set this control's binding context.
     */
    public void setBindingContext(XFormsContextStack.BindingContext bindingContext) {
        final XFormsContextStack.BindingContext oldBinding = this.bindingContext;
        this.bindingContext = bindingContext;

        // Relevance is a property of all controls
        final boolean oldRelevant = this.relevant;
        final boolean newRelevant = computeRelevant();

        if (!oldRelevant && newRelevant) {
            // Control is created
            this.relevant = newRelevant;
            onCreate();
        } else if (oldRelevant && !newRelevant) {
            // Control is destroyed
            onDestroy();
            this.relevant = newRelevant;
        } else if (newRelevant) {
            onBindingUpdate(oldBinding, bindingContext);
        }
    }

    public final boolean isRelevant() {
        return relevant;
    }

    protected void onCreate() {
        wasRelevant = false;
    }

    protected void onDestroy() {
    }

    protected void onBindingUpdate(XFormsContextStack.BindingContext oldBinding, XFormsContextStack.BindingContext newBinding) {
    }

    protected boolean computeRelevant() {
        // By default: if there is a parent, we have the same relevance as the parent, otherwise we are top-level so
        // we are relevant by default
        final XFormsControl parent = getParent();
        return (parent == null) || parent.isRelevant();
    }

    public String getPreviousEffectiveId() {
        final String result = previousEffectiveId;
        previousEffectiveId = effectiveId;
        return result;
    }

    public boolean wasRelevant() {
        final boolean result = wasRelevant;
        wasRelevant = relevant;
        return result;
    }

    public void commitCurrentUIState() {
        wasRelevant();
        getPreviousEffectiveId();
    }

    public boolean supportsRefreshEvents() {
        // TODO: should probably return true because most controls could then dispatch relevance events
        return false;
    }

    public static boolean supportsRefreshEvents(XFormsControl control) {
        return control != null && control.supportsRefreshEvents();
    }

    private LHHA getLabelLHHA() {
        LHHA result = lhha.get(XFormsConstants.LHHA.LABEL);
        if (result == null) {
            final LHHAAnalysis lhhaElement = getXBLContainer().getPartAnalysis().getLabel(getPrefixedId());
            result = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(lhhaElement, isSupportHTMLLabels());
            lhha.put(XFormsConstants.LHHA.LABEL, result);
        }
        return result;
    }

    private LHHA getHelpLHHA() {
        LHHA result = lhha.get(XFormsConstants.LHHA.HELP);
        if (result == null) {
            final LHHAAnalysis lhhaElement = getXBLContainer().getPartAnalysis().getHelp(getPrefixedId());
            result = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(lhhaElement, true);
            lhha.put(XFormsConstants.LHHA.HELP, result);
        }
        return result;
    }

    private LHHA getHintLHHA() {
        LHHA result = lhha.get(XFormsConstants.LHHA.HINT);
        if (result == null) {
            final LHHAAnalysis lhhaElement = getXBLContainer().getPartAnalysis().getHint(getPrefixedId());
            result = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(lhhaElement, isSupportHTMLHints());
            lhha.put(XFormsConstants.LHHA.HINT, result);
        }
        return result;
    }

    private LHHA getAlertLHHA() {
        LHHA result = lhha.get(XFormsConstants.LHHA.ALERT);
        if (result == null) {
            final LHHAAnalysis lhhaElement = getXBLContainer().getPartAnalysis().getAlert(getPrefixedId());
            result = (lhhaElement == null) ? NULL_LHHA : new ConcreteLHHA(lhhaElement, true);
            lhha.put(XFormsConstants.LHHA.ALERT, result);
        }
        return result;
    }

    public String getLabel() {
        return getLabelLHHA().getValue();
    }

    public String getEscapedLabel() {
        return getLabelLHHA().getEscapedValue();
    }

    public boolean isHTMLLabel() {
        return getLabelLHHA().isHTML();
    }

    public String getHelp() {
        return getHelpLHHA().getValue();
    }

    public String getEscapedHelp() {
        return getHelpLHHA().getEscapedValue();
    }

    public boolean isHTMLHelp() {
        return getHelpLHHA().isHTML();
    }

    public String getHint() {
        return getHintLHHA().getValue();
    }

    public String getEscapedHint() {
        return getHintLHHA().getEscapedValue();
    }

    public boolean isHTMLHint() {
        return getHintLHHA().isHTML();
    }

    public String getAlert() {
        return getAlertLHHA().getValue();
    }

    public boolean isHTMLAlert() {
        return getAlertLHHA().isHTML();
    }

    public String getEscapedAlert() {
        return getAlertLHHA().getEscapedValue();
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

    public final String getName() {
        return name;
    }

    public final XFormsControl getParent() {
        return parent;
    }

    protected void setParent(XFormsControl parent) {
        this.parent = parent;
    }

    public void detach() {
        this.parent = null;
    }

    public final Element getControlElement() {
        return controlElement;
    }

    /**
     * Whether a given control has an associated xforms:label element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:label element
     */
    public static boolean hasLabel(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticOps().getLabel(prefixedId) != null;
    }

    /**
     * Whether a given control has an associated xforms:hint element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:hint element
     */
    public static boolean hasHint(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticOps().getHint(prefixedId) != null;
    }

    /**
     * Whether a given control has an associated xforms:help element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:help element
     */
    public static boolean hasHelp(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticOps().getHelp(prefixedId) != null;
    }

    /**
     * Whether a given control has an associated xforms:alert element.
     *
     * @param containingDocument    containing document
     * @param prefixedId              static control id
     * @return                      true iif there is an xforms:alert element
     */
    public static boolean hasAlert(XFormsContainingDocument containingDocument, String prefixedId) {
        return containingDocument.getStaticOps().getAlert(prefixedId) != null;
    }

    /**
     * Return the control's appearances as a set of QNames.
     */
    public Set<QName> getAppearances() {
        return getAppearances(getElementAnalysis());
    }

    /**
     * Return the control's mediatype.
     */
    public String getMediatype() {
        if (mediatype == null)
            mediatype = controlElement.attributeValue(XFormsConstants.MEDIATYPE_QNAME);
        return mediatype;
    }

    public Tuple3<String, String, String> getJavaScriptInitialization() {
        return null;
    }
    
    protected Tuple3<String, String, String> getCommonJavaScriptInitialization() {
        final Set<QName> appearances = getAppearances();
        final String appearance = (appearances.size() > 0) ? Dom4jUtils.qNameToExplodedQName(appearances.iterator().next()) : null;
        return new Tuple3<String, String, String>(getName(), appearance != null ? appearance : getMediatype(), getEffectiveId());
    }

    /**
     * Compare this control with another control, as far as the comparison is relevant for the external world.
     *
     * @param other             other control
     * @return                  true if the controls are identical for the purpose of an external diff, false otherwise
     */
    public boolean equalsExternal(XFormsControl other) {

        if (other == null)
            return false;

        if (this == other)
            return true;

        // Compare only what matters

        if (relevant != other.relevant)
            return false;

        if (!XFormsUtils.compareStrings(getLabel(), other.getLabel()))
            return false;
        if (!XFormsUtils.compareStrings(getHelp(), other.getHelp()))
            return false;
        if (!XFormsUtils.compareStrings(getHint(), other.getHint()))
            return false;
        if (!XFormsUtils.compareStrings(getAlert(), other.getAlert()))
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
     * Return the binding context for this control.
     */
    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    public XFormsContextStack.BindingContext getBindingContext(XFormsContainingDocument containingDocument) {
        return getBindingContext();
    }

    public final void evaluate() {
        try {
            evaluateImpl();
        } catch (ValidationException e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "evaluating control",
                    getControlElement(), "element", Dom4jUtils.elementToDebugString(getControlElement())));
        }
    }

    private void evaluateExtensionAttributes(QName[] attributeQNames) {
        final Element controlElement = getControlElement();
        for (final QName avtAttributeQName: attributeQNames) {
            final String attributeValue = controlElement.attributeValue(avtAttributeQName);

            if (attributeValue != null) {
                // NOTE: This can return null if there is no context
                final String resolvedValue = evaluateAvt(attributeValue);

                if (extensionAttributesValues == null)
                    extensionAttributesValues = new HashMap<QName, String>();

                extensionAttributesValues.put(avtAttributeQName, resolvedValue);
            }
        }
    }

    /**
     * Notify the control that some of its aspects (value, label, etc.) might have changed and require re-evaluation. It
     * is left to the control to figure out if this can be optimized.
     */
    public final void markDirty(XPathDependencies xpathDependencies) {
        markDirtyImpl(xpathDependencies);
    }

    protected void markDirtyImpl(XPathDependencies xpathDependencies) {

        // Check LHHA
        for (final LHHA currentLHHA : lhha.values())
            if (currentLHHA != null)
                currentLHHA.handleMarkDirty();

        // For now clear this all the time
        // TODO: dependencies
        if (extensionAttributesValues != null)
            extensionAttributesValues.clear();
    }

    /**
     * Evaluate this control.
     */
    // TODO: move this method to XFormsValueControl and XFormsValueContainerControl
    protected void evaluateImpl() {

        // TODO: these should be evaluated lazily
        // Evaluate standard extension attributes
        evaluateExtensionAttributes(STANDARD_EXTENSION_ATTRIBUTES);
        // Evaluate custom extension attributes
        final QName[] extensionAttributes = getExtensionAttributes();
        if (extensionAttributes != null) {
            evaluateExtensionAttributes(extensionAttributes);
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

    public void performDefaultAction(XFormsEvent event) {
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

                        repeatControl.setIndex(newRepeatIndex);
                    }
                }

                if (XFormsEvents.XFORMS_FOCUS.equals(event.getName())) {
                    // Focus on current control if possible
                    setFocus();
                }
            }
        } else if (XFormsEvents.XFORMS_HELP.equals(event.getName())) {
            containingDocument.setClientHelpEffectiveControlId(getEffectiveId());
        } else if (XFormsEvents.XXFORMS_BINDING_ERROR.equals(event.getName())) {
            final XXFormsBindingErrorEvent ev = (XXFormsBindingErrorEvent) event;
            XFormsError.handleNonFatalSetvalueError(containingDocument, ev.locationData(), ev.reason());
        }
    }

    public void performTargetAction(XBLContainer container, XFormsEvent event) {
        // NOP
    }

    public boolean isStaticReadonly() {
        return false;
    }

    /**
     * Rewrite an HTML value which may contain URLs, for example in @src or @href attributes. Also deals with closing element tags.
     *
     *
     * @param rawValue          value to rewrite
     * @return                  rewritten value
     */
    public static String getEscapedHTMLValue(final LocationData locationData, String rawValue) {

        if (rawValue == null)
            return null;

        final StringBuilder sb = new StringBuilder(rawValue.length() * 2);// just an approx of the size it may take
        // NOTE: we do our own serialization here, but it's really simple (no namespaces) and probably reasonably efficient
        final ExternalContext.Rewriter rewriter = NetUtils.getExternalContext().getResponse();
        XFormsUtils.streamHTMLFragment(new XHTMLRewrite().getRewriteXMLReceiver(rewriter, new ForwardingXMLReceiver() {
            
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
                if (!isStartElement || !XFormsUtils.isVoidElement(localname)) {
                    // We serialize to HTML: don't close elements that just opened (will cover <br>, <hr>, etc.). Be sure not to drop closing elements of other tags though!
                    sb.append("</");
                    sb.append(localname);
                    sb.append('>');
                }
                isStartElement = false;
            }
        }, true), rawValue, locationData, "xhtml");
        return sb.toString();
    }

    /**
     * Evaluate an attribute of the control as an AVT.
     *
     * @param attributeValue    value of the attribute
     * @return                  value of the AVT or null if cannot be computed
     */
    protected String evaluateAvt(String attributeValue) {

        if (!XFormsUtils.maybeAVT(attributeValue)) {
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
                    return XPathCache.evaluateAsAvt(contextNodeset, bindingContext.getPosition(), attributeValue, getNamespaceMappings(),
                        bindingContext.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(), getFunctionContext(), null, getLocationData());
                } catch (Exception e) {
                    // Don't consider this as fatal
                    XFormsError.handleNonFatalXPathError(containingDocument, e);
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
     * @param xpathString       XPath expression
     * @return                  value, or null if cannot be computed
     */
    protected String evaluateAsString(String xpathString, List<Item> contextItems, int contextPosition) {

        // NOTE: the control may or may not be bound, so don't use getBoundNode()
        if (contextItems == null || contextItems.size() == 0) {
            // TODO: in the future we should be able to try evaluating anyway
            return null;
        } else {
            // Need to ensure the binding on the context stack is correct before evaluating XPath expressions
            // Reason is that XPath functions might use the context stack to get the current model, etc.
            final XFormsContextStack contextStack = getContextStack();
            contextStack.setBinding(this);

            try {
                return XPathCache.evaluateAsString(contextItems, contextPosition,
                                    xpathString, getNamespaceMappings(), bindingContext.getInScopeVariables(),
                                    XFormsContainingDocument.getFunctionLibrary(),
                                    getFunctionContext(), null, getLocationData());
            } catch (Exception e) {
                // Don't consider this as fatal
                XFormsError.handleNonFatalXPathError(containingDocument, e);
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
     * @param contextItem           context item
     * @param xpathString           XPath expression
     * @param namespaceMapping      namespace mappings to use
     * @param variableToValueMap    variables to use
     * @return                      value, or null if cannot be computed
     */
    protected String evaluateAsString(Item contextItem, String xpathString,
                                      NamespaceMapping namespaceMapping, Map<String, ValueRepresentation> variableToValueMap) {

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
                return XPathCache.evaluateAsString(contextItem,
                                xpathString, namespaceMapping, variableToValueMap,
                                XFormsContainingDocument.getFunctionLibrary(),
                                getFunctionContext(), null, getLocationData());
            } catch (Exception e) {
                // Don't consider this as fatal
                XFormsError.handleNonFatalXPathError(containingDocument, e);
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
     * @return              mapping
     */
    public NamespaceMapping getNamespaceMappings() {
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
     * Clone a control. It is important to understand why this is implemented: to create a copy of a tree of controls
     * before updates that may change control bindings. Also, it is important to understand that we clone "back", that
     * is the new clone will be used as the reference copy for the difference engine.
     *
     * @return  new XFormsControl
     */
    public Object getBackCopy() {
        
        // NOTE: this.parent is handled by subclasses
        final XFormsControl cloned;
        try {
            cloned = (XFormsControl) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);
        }

        // Clone LHHA if not null and not constant
        cloned.lhha = new HashMap<XFormsConstants.LHHA, LHHA>(XFormsConstants.LHHA.values().length);
        for (final Map.Entry<XFormsConstants.LHHA, LHHA> entry: lhha.entrySet()) {
            final XFormsConstants.LHHA key = entry.getKey();
            final LHHA value = entry.getValue();

            // Evaluate lazy value before copying
            value.getValue();

            // Clone
            final LHHA clonedLHHA;
            if (value != null && value != NULL_LHHA)
                clonedLHHA = value.clone();
            else
                clonedLHHA = value;

            cloned.lhha.put(key, clonedLHHA);
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
            if (!XFormsUtils.compareItems(currentItem1, currentItem2)) {
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

    /**
     * Represent a property of the control that can be evaluated, marked dirty, and optimized.
     */
    protected abstract class ControlProperty<T> implements Cloneable {
        private T value;
        private boolean isEvaluated;
        private boolean isOptimized;

        public ControlProperty() {}

        protected abstract boolean requireUpdate();
        protected abstract void notifyCompute();
        protected abstract void notifyOptimized();
        protected abstract T evaluateValue();

        public T getValue() {// NOTE: making this method final produces an AbstractMethodError with Java 5 (ok with Java 6)
            if (!isEvaluated) {
                if (XFormsControl.this.isRelevant()) {
                    notifyCompute();
                    value = evaluateValue();
                } else {
                    // NOTE: if the control is not relevant, nobody should ask about this in the first place
                    value = null;
                }
                isEvaluated = true;
            } else if (isOptimized) {
                // This is only for statistics: if the value was not re-evaluated because of the dependency engine
                // giving us the green light, the first time the value is asked we notify the dependency engine of that
                // situation.
                notifyOptimized();
                isOptimized = false;
            }
            return value;
        }

        public void handleMarkDirty() {
            if (!isDirty()) { // don't do anything if we are already dirty
                if (relevant != wasRelevant) {
                    // Control becomes relevant or non-relevant
                    markDirty();
                } else if (relevant) {
                    // Control remains relevant
                    if (requireUpdate())
                        markDirty();
                    else
                        markOptimized(); // for statistics only
                }
                // If control is clean and remains non-relevant, no need to mark dirty as it's value remains null
                // NOTE: Ideally nobody should call to get the value if the control is not relevant
            }
        }

        protected void markDirty() {
            value = null;
            isEvaluated = false;
            isOptimized = false;
        }

        protected void markOptimized() {
            isOptimized = true;
        }

        private boolean isDirty() {
            return !isEvaluated;
        }

        @Override
        public ControlProperty<T> clone() {
            try {
                return (ControlProperty<T>) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new OXFException("");// must not happen
            }
        }
    }

    protected class ConstantControlProperty<T> extends ControlProperty<T> {

        private T value;

        public ConstantControlProperty(T value) {
            this.value = value;
        }

        @Override
        protected boolean requireUpdate() {
            return false;
        }

        @Override
        protected void notifyCompute() {
        }

        @Override
        protected void notifyOptimized() {
        }

        @Override
        protected T evaluateValue() {
            return value;
        }
    }

    // Empty LHHA
    protected static final LHHA NULL_LHHA = new NullLHHA();

    protected static class NullLHHA implements LHHA {
        public String getValue() { return null; }
        public String getEscapedValue() { return null; }
        public boolean isHTML() { return false; }
        public void handleMarkDirty() {}

        @Override
        public NullLHHA clone() {
            try {
                return (NullLHHA) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new OXFException(e);
            }
        }
    }

    protected interface LHHA extends Cloneable {
        String getValue();
        String getEscapedValue();
        boolean isHTML();
        void handleMarkDirty();
        LHHA clone();
    }

    // LHHA corresponding to an existing xforms:label, etc. element
    private class ConcreteLHHA extends ControlProperty<String> implements LHHA {

        private final LHHAAnalysis lhhaAnalysis;
        private final Element lhhaElement;
        private final boolean supportsHTML;

        private boolean isHTML;

        public ConcreteLHHA(LHHAAnalysis lhhaAnalysis, boolean supportsHTML) {

            assert lhhaAnalysis != null && lhhaAnalysis.element() != null : "LHHA analysis/element can't be null";

            this.lhhaAnalysis = lhhaAnalysis;
            this.lhhaElement = lhhaAnalysis.element();
            this.supportsHTML = supportsHTML;
        }

        @Override
        protected String evaluateValue() {
            final String result = doEvaluateValue();
            isHTML = result != null && tempContainsHTML[0];
            return result;
        }

        public String getEscapedValue() {
            final String value = getValue();
            return isHTML ? XFormsControl.getEscapedHTMLValue(getLocationData(), value) : XMLUtils.escapeXMLMinimal(value);
        }

        public boolean isHTML() {
            getValue();
            return isHTML;
        }

        @Override
        protected void markDirty() {
            super.markDirty();
            isHTML = false;
        }

        @Override
        protected boolean requireUpdate() {
            return containingDocument.getXPathDependencies().requireLHHAUpdate(lhhaElement.getName(), getPrefixedId());
        }

        @Override
        protected void notifyCompute() {
            containingDocument.getXPathDependencies().notifyComputeLHHA();
        }

        @Override
        protected void notifyOptimized() {
            containingDocument.getXPathDependencies().notifyOptimizeLHHA();
        }

        @Override
        public ConcreteLHHA clone() {
            return (ConcreteLHHA) super.clone();
        }

        /**
         * Evaluate the value of a LHHA related to this control.
         *
         * @return                      string containing the result of the evaluation, null if evaluation failed
         */
        private String doEvaluateValue() {

            final XFormsControl control = XFormsControl.this;

            final XFormsContextStack contextStack = control.getContextStack();
            final String value;
            if (lhhaAnalysis.isLocal()) {
                // LHHA is direct child of control, evaluate within context
                contextStack.setBinding(control);
                contextStack.pushBinding(lhhaElement, control.effectiveId, control.getChildElementScope(lhhaElement));
                value = XFormsUtils.getElementValue(control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, control.tempContainsHTML);
                contextStack.popBinding();
            } else {
                // LHHA is somewhere else, assumed as a child of xforms:* or xxforms:*

                // Find context object for XPath evaluation
                final Element contextElement = lhhaElement.getParent();
                final String contextStaticId = contextElement.attributeValue(XFormsConstants.ID_QNAME);
                final String contextEffectiveId;
                if (contextStaticId == null) {
                    // Assume we are at the top-level
                    contextStack.resetBindingContext();
                    contextEffectiveId = control.container.getFirstControlEffectiveId();
                } else {
                    // Not at top-level, find containing object
                    final XFormsControl ancestorContextControl = findAncestorContextControl(contextStaticId, lhhaElement.attributeValue(XFormsConstants.ID_QNAME));
                    if (ancestorContextControl != null) {
                        contextStack.setBinding(ancestorContextControl);
                        contextEffectiveId = ancestorContextControl.effectiveId;
                    } else {
                        contextEffectiveId = null;
                    }
                }

                if (contextEffectiveId != null) {
                    // Push binding relative to context established above and evaluate
                    contextStack.pushBinding(lhhaElement, contextEffectiveId, control.getResolutionScope());
                    value = XFormsUtils.getElementValue(control.container, contextStack, control.effectiveId, lhhaElement, supportsHTML, control.tempContainsHTML);
                    contextStack.popBinding();
                } else {
                    // Do as if there was no LHHA
                    value = null;
                }
            }
            return value;
        }

        private XFormsControl findAncestorContextControl(String contextStaticId, String lhhaStaticId) {

            final XFormsControl control = XFormsControl.this;

            // NOTE: LHHA element must be in the same resolution scope as the current control (since @for refers to @id)
            final XBLBindingsBase.Scope lhhaScope = control.getResolutionScope();
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
        return equalsExternal(other);
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

    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {
        // NOP
    }

    protected boolean addAjaxAttributes(AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree, XFormsControl other) {

        boolean added = false;

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, getEffectiveId()));

        // Class attribute
        added |= addAjaxClass(attributesImpl, isNewlyVisibleSubtree, other, this);

        // Label, help, hint, alert, etc.
        added |= addAjaxLHHA(attributesImpl, isNewlyVisibleSubtree, other, this);

        // Output control-specific attributes
        added |= addAjaxCustomAttributes(attributesImpl, isNewlyVisibleSubtree, other);

        return added;
    }

    // public for unit tests
    public static boolean addAjaxClass(AttributesImpl attributesImpl, boolean newlyVisibleSubtree,
                                       XFormsControl control1, XFormsControl control2) {

        boolean added = false;

        final String class1 = (control1 == null) ? null : control1.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);
        final String class2 = control2.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);

        if (newlyVisibleSubtree || !XFormsUtils.compareStrings(class1, class2)) {
            // Custom MIPs changed

            final String attributeValue;
            if (class1 == null) {
                attributeValue = class2;
            } else {
                final StringBuilder sb = new StringBuilder(100);

                final Set<String> classes1 = tokenize(class1);
                final Set<String> classes2 = tokenize(class2);

                // Classes to remove
                for (final String currentClass: classes1) {
                    if (!classes2.contains(currentClass)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('-');
                        sb.append(currentClass);
                    }
                }

                // Classes to add
                for (final String currentClass: classes2) {
                    if (!classes1.contains(currentClass)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('+');
                        sb.append(currentClass);
                    }
                }

                attributeValue = sb.toString();
            }
            // This attribute is a space-separate list of class names prefixed with either '-' or '+'
            if (attributeValue != null)
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue.equals(""));
        }
        return added;
    }

    private static Set<String> tokenize(String value) {
        final Set<String> result;
        if (value != null) {
            result = new LinkedHashSet<String>();
            for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
                result.add(st.nextToken());
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }

    private boolean addAjaxLHHA(AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree,
                                XFormsControl control1, XFormsControl control2) {

        boolean added = false;
        {
            final String labelValue1 = isNewlyVisibleSubtree ? null : control1.getLabel();
            final String labelValue2 = control2.getLabel();

            if (!XFormsUtils.compareStrings(labelValue1, labelValue2)) {
                final String escapedLabelValue2 = control2.getEscapedLabel();
                final String attributeValue = escapedLabelValue2 != null ? escapedLabelValue2 : "";
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "label", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        {
            final String helpValue1 = isNewlyVisibleSubtree ? null : control1.getHelp();
            final String helpValue2 = control2.getHelp();

            if (!XFormsUtils.compareStrings(helpValue1, helpValue2)) {
                final String escapedHelpValue2 = control2.getEscapedHelp();
                final String attributeValue = escapedHelpValue2 != null ? escapedHelpValue2 : "";
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "help", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        {
            final String hintValue1 = isNewlyVisibleSubtree ? null : control1.getHint();
            final String hintValue2 = control2.getHint();

            if (!XFormsUtils.compareStrings(hintValue1, hintValue2)) {
                final String escapedHintValue2 = control2.getEscapedHint();
                final String attributeValue = escapedHintValue2 != null ? escapedHintValue2 : "";
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "hint", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        {
            final String alertValue1 = isNewlyVisibleSubtree ? null : control1.getAlert();
            final String alertValue2 = control2.getAlert();

            if (!XFormsUtils.compareStrings(alertValue1, alertValue2)) {
                final String escapedAlertValue2 = control2.getEscapedAlert();
                final String attributeValue = escapedAlertValue2 != null ? escapedAlertValue2 : "";
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "alert", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }
        return added;
    }

    protected static boolean addOrAppendToAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            XMLUtils.addOrAppendToAttribute(attributesImpl, name, value);
            return true;
        }
    }

    /**
     * Add attributes differences for custom attributes.
     *
     * @param attributesImpl        attributes to add to
     * @param isNewRepeatIteration  whether the current controls is within a new repeat iteration
     * @param other                 original control, possibly null
     * @return                      true if any attribute was added, false otherwise
     */
    protected boolean addAjaxCustomAttributes(AttributesImpl attributesImpl, boolean isNewRepeatIteration, XFormsControl other) {

        final QName[] extensionAttributes = getExtensionAttributes();
        // By default, diff only attributes in the xxforms:* namespace
        return extensionAttributes != null && addAttributesDiffs(other, attributesImpl, isNewRepeatIteration, extensionAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);
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

    protected void addAjaxStandardAttributes(XFormsSingleNodeControl originalControl, ContentHandlerHelper ch, boolean isNewRepeatIteration) {
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
                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, control2.getEffectiveId()));

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

    public void addListener(String eventName, org.orbeon.oxf.xforms.event.EventListener listener) {
        throw new UnsupportedOperationException();
    }

    public void removeListener(String eventName, org.orbeon.oxf.xforms.event.EventListener listener) {
        throw new UnsupportedOperationException();
    }

    public List<org.orbeon.oxf.xforms.event.EventListener> getListeners(String eventName) {
        return null;
    }

    public static Set<QName> getAppearances(ElementAnalysis elementAnalysis) {
        if (elementAnalysis instanceof AppearanceTrait) {
            return ((AppearanceTrait) elementAnalysis).jAppearances();
        } else {
            return Collections.emptySet();
        }
    }
}
