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
package org.orbeon.oxf.xforms.processor.handlers.xhtml;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.processor.handlers.XFormsControlLifecycleHandlerDelegate;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This class is a helper base class which handles the lifecycle of producing markup for a control. The following
 * phases are handled:
 *
 * o Give the handler a chance to do some prep work: prepareHandler()
 * o Get custom information: addCustomClasses()
 * o Check whether the control wants any output at all: isMustOutputControl()
 * o Output label, control, hint, help, and alert in order specified by properties
 *
 * Outputting the control is split into two parts: handleControlStart() and handleControlEnd(). In most cases, only
 * handleControlStart() is used, but container controls will use handleControlEnd().
 */
public abstract class XFormsControlLifecyleHandler extends XFormsBaseHandlerXHTML {

	private XFormsControlLifecycleHandlerDelegate xFormsControlLifecycleHandlerDelegate;
	private Attributes attributes;
    
    private String[] endConfig;
    private String containingElementQName;

    protected XFormsControlLifecyleHandler(boolean repeating) {
        super(repeating, false);
    }

    protected XFormsControlLifecyleHandler(boolean repeating, boolean forwarding) {
        super(repeating, forwarding);
    }

    protected String getContainingElementName() {
        // By default, controls are enclosed with a <span>
        return "span";
    }

    protected String getContainingElementQName() {
        if (containingElementQName == null) {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            containingElementQName = XMLUtils.buildQName(xhtmlPrefix, getContainingElementName());
        }
        return containingElementQName;
    }
    
	protected boolean isTemplate() {
		return xFormsControlLifecycleHandlerDelegate.isTemplate();
	}

	protected String getPrefixedId() {
		return xFormsControlLifecycleHandlerDelegate.getPrefixedId();
	}

	protected String getEffectiveId() {
		return xFormsControlLifecycleHandlerDelegate.getEffectiveId();
	}

	protected XFormsControl getControl() {
		return xFormsControlLifecycleHandlerDelegate.getControl();
	}
	
	protected String getStaticId() {
		return xFormsControlLifecycleHandlerDelegate.getStaticId();
	}
	

	@Override
    public void init(String uri, String localname, String qName, Attributes attributes, Object matched) throws SAXException {
        super.init(uri, localname, qName, attributes, matched);
        xFormsControlLifecycleHandlerDelegate = new XFormsControlLifecycleHandlerDelegate(handlerContext, containingDocument, attributes);
    }

    @Override
    public final void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (isMustOutputControl(getControl())) {

            final ContentHandler contentHandler = handlerContext.getController().getOutput();
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (handlerContext.isSpanHTMLLayout() && isMustOutputContainerElement()) {
                // Open control element, usually <span>
                final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName(), containerAttributes);
            }

            // Get local order for control
            final String localOrder = attributes.getValue(XFormsConstants.XXFORMS_ORDER_QNAME.getNamespaceURI(),
                    XFormsConstants.XXFORMS_ORDER_QNAME.getName());

            // Use local or default config
            final String[] config = (localOrder != null) ? StringUtils.split(localOrder) : handlerContext.getDocumentOrder();

            // Output named anchor if the control has a help or alert. This is so that a separate help and error
            // sections can link back to the control.
            if (handlerContext.isNoScript()) {
                if (getControl() != null
                        && (XFormsControl.hasHelp(containingDocument, getPrefixedId())
                            || XFormsControl.hasAlert(containingDocument, getPrefixedId()))) {
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, getControl().getEffectiveId());
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
                }
            }

            // Process everything up to and including the control
            for (int i = 0; i < config.length; i++) {
                final String current = config[i];

                if ("control".equals(current)) {
                    // Handle control
                    handleControlStart(uri, localname, qName, attributes, getStaticId(), getEffectiveId(), getControl());
                    // Do the rest in end() below if needed
                    if (i < config.length - 1) {
                        // There remains stuff to process
                        final int endConfigLength = config.length - i;
                        endConfig = new String[endConfigLength];
                        System.arraycopy(config, i, endConfig, 0, endConfigLength);
                        this.attributes = new AttributesImpl(attributes);
                    }
                    break;
                } else if ("label".equals(current)) {
                    // xforms:label
                    if (hasLocalLabel())
                        handleLabel();
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (hasLocalAlert())
                        handleAlert();
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (hasLocalHint())
                        handleHint();
                } else {
                    // xforms:help
                    if (hasLocalHelp())
                        handleHelp();
                }
            }
        }
    }

    @Override
    public final void end(String uri, String localname, String qName) throws SAXException {
        if (isMustOutputControl(getControl())) {
            // Process everything after the control has been shown
            if (endConfig != null) {

                for (final String current: endConfig) {
                    if ("control".equals(current)) {
                        // Handle control
                        handleControlEnd(uri, localname, qName, attributes, getStaticId(), getEffectiveId(), getControl());
                    } else if ("label".equals(current)) {
                        // xforms:label
                        if (hasLocalLabel())
                            handleLabel();
                    } else if ("alert".equals(current)) {
                        // xforms:alert
                        if (hasLocalAlert())
                            handleAlert();
                    } else if ("hint".equals(current)) {
                        // xforms:hint
                        if (hasLocalHint())
                            handleHint();
                    } else {
                        // xforms:help
                        if (hasLocalHelp())
                            handleHelp();
                    }
                }
            }

            if (handlerContext.isSpanHTMLLayout() && isMustOutputContainerElement()) {
                // Close control element, usually <span>
                final ContentHandler contentHandler = handlerContext.getController().getOutput();
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName());
            }
        }
    }

    private boolean hasLocalLabel() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getLabel(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    private boolean hasLocalHint() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getHint(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    private boolean hasLocalHelp() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getHelp(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    private boolean hasLocalAlert() {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final LHHAAnalysis analysis = globalOps.getAlert(getPrefixedId());
        return analysis != null && analysis.isLocal();
    }

    protected boolean isMustOutputControl(XFormsControl control) {
        // May be overridden by subclasses
        return true;
    }

    protected boolean isMustOutputContainerElement() {
        // May be overridden by subclasses
        return true;
    }

    protected void addCustomClasses(StringBuilder classes, XFormsControl control) {
        // May be overridden by subclasses
    }

    protected boolean isDefaultIncremental() {
        // May be overridden by subclasses
        return false;
    }

    protected void handleLabel() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.LABEL, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleAlert() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.ALERT, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleHint() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.HINT, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    protected void handleHelp() throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(getEffectiveId(), getForEffectiveId(), LHHAC.HELP, getControl(), isTemplate(), !handlerContext.isSpanHTMLLayout());
    }

    // Must be overridden by subclasses
    protected abstract void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException;

    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {
        // May be overridden by subclasses
    }

    protected AttributesImpl getContainerAttributes(String uri, String localname, Attributes attributes, String effectiveId, XFormsControl control, boolean addId) {
        final AttributesImpl containerAttributes;
        if (handlerContext.isSpanHTMLLayout()) {
            reusableAttributes.clear();
            containerAttributes = reusableAttributes;
            if (addId)
                containerAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, getLHHACId(containingDocument, effectiveId, LHHAC_CODES.get(LHHAC.CONTROL)));
        } else {
            containerAttributes = getContainerAttributes(uri, localname, attributes);
        }
        return containerAttributes;
    }

    private AttributesImpl getContainerAttributes(String uri, String localname, Attributes attributes) {

        // NOTE: Only reason we do not use the class members directly is to handle boolean xf:input, which delegates
        // its output to xf:select1. Should be improved some day.

        final String prefixedId = getPrefixedId();
        final String effectiveId = getEffectiveId();
        final XFormsControl xformsControl = getControl();

        // Get classes
        final StringBuilder classes;
        {
            // Initial classes: xforms-control, xforms-[control name], incremental, appearance, mediatype, xforms-static
            classes = getInitialClasses(uri, localname, attributes, xformsControl, isDefaultIncremental());
            // All MIP-related classes
            handleMIPClasses(classes, prefixedId, xformsControl);
            // Static classes
            containingDocument.getStaticOps().appendClasses(classes, prefixedId);
            // Dynamic classes added by the control
            addCustomClasses(classes, xformsControl);
        }

        // Get attributes
        final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

        // Add extension attributes in no namespace if possible
        if (xformsControl != null) {
            xformsControl.addExtensionAttributes(newAttributes, "");
        }
        return newAttributes;
    }

    /**
     * Return the effective id of the element to which label/@for, etc. must point to.
     *
     * @return                      @for effective id
     */
    public String getForEffectiveId() {
        // Default:
        // o new layout: point to foo$bar$$c.1-2-3
        // o old layout: point to foo$bar.1-2-3
        return handlerContext.isSpanHTMLLayout() ? getLHHACId(containingDocument, getEffectiveId(), LHHAC_CODES.get(LHHAC.CONTROL)) : getEffectiveId();
    }
}
