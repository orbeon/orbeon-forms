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

import org.apache.commons.lang3.StringUtils;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.StaticLHHASupport;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.processor.handlers.XFormsControlLifecycleHandlerDelegate;
import org.orbeon.oxf.xml.XMLReceiverHelper;
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
 * - Give the handler a chance to do some prep work: prepareHandler()
 * - Get custom information: addCustomClasses()
 * - Check whether the control wants any output at all: isMustOutputControl()
 * - Output label, control, hint, help, and alert in order specified by properties
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
		return xFormsControlLifecycleHandlerDelegate.prefixedId();
	}

	protected String getEffectiveId() {
		return xFormsControlLifecycleHandlerDelegate.effectiveId();
	}

	protected XFormsControl currentControlOrNull() {
		return xFormsControlLifecycleHandlerDelegate.currentControlOrNull();
	}

    protected scala.Option<XFormsControl> currentControlOpt() {
		return xFormsControlLifecycleHandlerDelegate.currentControlOpt();
	}

	@Override
    public void init(String uri, String localname, String qName, Attributes attributes, Object matched) throws SAXException {
        super.init(uri, localname, qName, attributes, matched);
        xFormsControlLifecycleHandlerDelegate = new XFormsControlLifecycleHandlerDelegate(handlerContext, containingDocument, attributes);
    }

    @Override
    public final void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (isMustOutputControl(currentControlOrNull())) {

            final ContentHandler contentHandler = handlerContext.getController().getOutput();
            if (isMustOutputContainerElement()) {
                // Open control element, usually <span>
                final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName(), containerAttributes);
            }

            // Get local order for control
            final String localOrder = attributes.getValue(XFormsConstants.XXFORMS_ORDER_QNAME.getNamespaceURI(),
                    XFormsConstants.XXFORMS_ORDER_QNAME.getName());

            // Use local or default config
            final String[] config = (localOrder != null) ? StringUtils.split(localOrder) : handlerContext.getDocumentOrder();

            // 2012-12-17: Removed nested <a name="effective-id"> because the enclosing <span> for the control has the
            // same id and will be handled first by the browser as per HTML 5. This means the named anchor is actually
            // redundant.

            // Process everything up to and including the control
            for (int i = 0; i < config.length; i++) {
                final String current = config[i];

                if ("control".equals(current)) {
                    // Handle control
                    handleControlStart(uri, localname, qName, attributes, getEffectiveId(), currentControlOrNull());
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
                    // xf:label
                    if (hasLocalLabel())
                        handleLabel();
                } else if ("alert".equals(current)) {
                    // xf:alert
                    if (hasLocalAlert())
                        handleAlert();
                } else if ("hint".equals(current)) {
                    // xf:hint
                    if (hasLocalHint())
                        handleHint();
                } else {
                    // xf:help
                    if (hasLocalHelp())
                        handleHelp();
                }
            }
        }
    }

    @Override
    public final void end(String uri, String localname, String qName) throws SAXException {
        if (isMustOutputControl(currentControlOrNull())) {
            // Process everything after the control has been shown
            if (endConfig != null) {

                for (final String current : endConfig) {
                    if ("control".equals(current)) {
                        // Handle control
                        handleControlEnd(uri, localname, qName, attributes, getEffectiveId(), currentControlOrNull());
                    } else if ("label".equals(current)) {
                        // xf:label
                        if (hasLocalLabel())
                            handleLabel();
                    } else if ("alert".equals(current)) {
                        // xf:alert
                        if (hasLocalAlert())
                            handleAlert();
                    } else if ("hint".equals(current)) {
                        // xf:hint
                        if (hasLocalHint())
                            handleHint();
                    } else {
                        // xf:help
                        if (hasLocalHelp())
                            handleHelp();
                    }
                }
            }

            if (isMustOutputContainerElement()) {
                // Close control element, usually <span>
                final ContentHandler contentHandler = handlerContext.getController().getOutput();
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, getContainingElementName(), getContainingElementQName());
            }
        }
    }

    private boolean hasLocalLabel() {
        return hasLocalLHHA("label");
    }

    private boolean hasLocalHint() {
        return hasLocalLHHA("hint");
    }

    private boolean hasLocalHelp() {
        return hasLocalLHHA("help");
    }

    private boolean hasLocalAlert() {
        return hasLocalLHHA("alert");
    }

    private boolean hasLocalLHHA(String lhhaType) {
        final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
        final ElementAnalysis analysis = globalOps.getControlAnalysis(getPrefixedId());
        if (analysis instanceof StaticLHHASupport)
            return ((StaticLHHASupport) analysis).hasLocal(lhhaType);
        else
            return false;
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
        handleLabelHintHelpAlert(
            getStaticLHHA(getPrefixedId(), LHHAC.LABEL),
            getEffectiveId(),
            getForEffectiveId(getEffectiveId()),
            LHHAC.LABEL,
            isStaticReadonly(currentControlOrNull()) ? "span" : null,
            currentControlOrNull(),
            isTemplate(),
            false
        );
    }

    protected void handleAlert() throws SAXException {
        if (! isStaticReadonly(currentControlOrNull()))
            handleLabelHintHelpAlert(
                getStaticLHHA(getPrefixedId(), LHHAC.ALERT),
                getEffectiveId(),
                getForEffectiveId(getEffectiveId()),
                LHHAC.ALERT,
                null,
                currentControlOrNull(),
                isTemplate(),
                false
            );
    }

    protected void handleHint() throws SAXException {
        if (! isStaticReadonly(currentControlOrNull()))
            handleLabelHintHelpAlert(
                getStaticLHHA(getPrefixedId(), LHHAC.HINT),
                getEffectiveId(),
                getForEffectiveId(getEffectiveId()),
                LHHAC.HINT,
                null,
                currentControlOrNull(),
                isTemplate(),
                false
            );
    }

    protected void handleHelp() throws SAXException {
        if (! isStaticReadonly(currentControlOrNull()))
            handleLabelHintHelpAlert(
                getStaticLHHA(getPrefixedId(), LHHAC.HELP),
                getEffectiveId(),
                getForEffectiveId(getEffectiveId()),
                LHHAC.HELP,
                null,
                currentControlOrNull(),
                isTemplate(),
                false
            );
    }

    // Must be overridden by subclasses
    protected abstract void handleControlStart(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException;

    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String effectiveId, XFormsControl control) throws SAXException {
        // May be overridden by subclasses
    }

    protected AttributesImpl getEmptyNestedControlAttributesMaybeWithId(String uri, String localname, Attributes attributes, String effectiveId, XFormsControl control, boolean addId) {
        reusableAttributes.clear();
        final AttributesImpl containerAttributes = reusableAttributes;
        if (addId)
            containerAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, getLHHACId(containingDocument, effectiveId, LHHAC_CODES.get(LHHAC.CONTROL)));

        return containerAttributes;
    }

    private AttributesImpl getContainerAttributes(String uri, String localname, Attributes attributes) {

        // NOTE: Only reason we do not use the class members directly is to handle boolean xf:input, which delegates
        // its output to xf:select1. Should be improved some day.

        final String prefixedId = getPrefixedId();
        final String effectiveId = getEffectiveId();
        final XFormsControl xformsControl = currentControlOrNull();

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
        final AttributesImpl newAttributes = getIdClassXHTMLAttributes(attributes, classes.toString(), effectiveId);

        // Add extension attributes in no namespace if possible
        if (xformsControl != null) {
            xformsControl.addExtensionAttributesExceptClassAndAcceptForHandler(newAttributes, "");
        }
        return newAttributes;
    }

    /**
     * Return the effective id of the element to which label/@for, etc. must point to.
     */
    public String getForEffectiveId(String effectiveId) {
        // Default: point to foo$bar$$c.1-2-3
        return getLHHACId(containingDocument, getEffectiveId(), LHHAC_CODES.get(LHHAC.CONTROL));
    }
}
