/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor.handlers;

import org.apache.commons.lang.StringUtils;
import org.dom4j.QName;
import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
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
public abstract class XFormsControlLifecyleHandler extends XFormsBaseHandler {

    private String staticId;
    private String prefixedId;
    private String effectiveId;

    private XFormsSingleNodeControl xformsControl;
    private Attributes attributes;
    private String[] endConfig;
    private String spanQName;

    protected XFormsControlLifecyleHandler(boolean repeating) {
        super(repeating, false);
    }

    protected XFormsControlLifecyleHandler(boolean repeating, boolean forwarding) {
        super(repeating, forwarding);
    }

    protected String getPrefixedId() {
        return prefixedId;
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        staticId = handlerContext.getId(attributes);
        prefixedId = handlerContext.getIdPrefix() + staticId;
        effectiveId = handlerContext.getEffectiveId(attributes);

        xformsControl = handlerContext.isTemplate()
                ? null : (XFormsSingleNodeControl) containingDocument.getObjectByEffectiveId(effectiveId);

        // Give the handler a chance to do some prep work
        prepareHandler(uri, localname, qName, attributes, staticId, effectiveId, xformsControl);

        if (isMustOutputControl(xformsControl)) {

            final ContentHandler contentHandler = handlerContext.getController().getOutput();
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (handlerContext.isNewXHTMLLayout()) {
                // Open control <div>
                spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

                // Get appearance
                final QName appearance = getAppearance(attributes);

                // Get classes
                final FastStringBuffer classes;
                {
                    // Initial classes: xforms-control, xforms-[control name], incremental, appearance, mediatype, xforms-static
                    classes = getInitialClasses(uri, localname, attributes, xformsControl, appearance, isDefaultIncremental());
                    // All MIP-related classes
                    handleMIPClasses(classes, prefixedId, xformsControl);
                    // Static classes: xforms-online, xforms-offline, ...
                    containingDocument.getStaticState().appendClasses(classes, prefixedId);
                    // Dynamic classes added by the control 
                    addCustomClasses(classes, xformsControl);
                }

                // Get attributes
                final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

                // Add extension attributes in no namespace if possible
                if (xformsControl != null) {
                    xformsControl.addExtensionAttributes(newAttributes, "");
                }

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
            }

            // Get local order for control
            final String localOrder = attributes.getValue(XFormsConstants.XXFORMS_ORDER_QNAME.getNamespaceURI(),
                    XFormsConstants.XXFORMS_ORDER_QNAME.getName());

            // Use local or default config
            final String[] config = (localOrder != null) ? StringUtils.split(localOrder) : handlerContext.getDocumentOrder();

            // Output named anchor if the control has a help or alert. This is so that a separate help and error
            // sections can link back to the control.
            if (handlerContext.isNoScript()) {
                if (xformsControl != null
                        && (XFormsControl.hasHelp(containingDocument, prefixedId)
                            || XFormsControl.hasAlert(containingDocument, prefixedId))) {
                    final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, xformsControl.getEffectiveId());
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
                }
            }

            final boolean isTemplate = handlerContext.isTemplate();

            // Process everything up to and including the control
            for (int i = 0; i < config.length; i++) {
                final String current = config[i];

                if ("control".equals(current)) {
                    // Handle control
                    handleControlStart(uri, localname, qName, attributes, staticId, effectiveId, xformsControl);
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
                        handleLabel(staticId, effectiveId, attributes, xformsControl, isTemplate);
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (hasLocalAlert())
                        handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (hasLocalHint())
                        handleHint(staticId, effectiveId, xformsControl, isTemplate);
                } else {
                    // xforms:help
                    if (hasLocalHelp())
                        handleHelp(staticId, effectiveId, xformsControl, isTemplate);
                }
            }
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Process everything after the control has been shown
        if (endConfig != null) {
            final boolean isTemplate = handlerContext.isTemplate();

            for (int i = 0; i < endConfig.length; i++) {
                final String current = endConfig[i];

                if ("control".equals(current)) {
                    // Handle control
                    handleControlEnd(uri, localname, qName, attributes, staticId, effectiveId, xformsControl);
                } else if ("label".equals(current)) {
                    // xforms:label
                    if (hasLocalLabel())
                        handleLabel(staticId, effectiveId, attributes, xformsControl, isTemplate);
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (hasLocalAlert())
                        handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (hasLocalHint())
                        handleHint(staticId, effectiveId, xformsControl, isTemplate);
                } else {
                    // xforms:help
                    if (hasLocalHelp())
                        handleHelp(staticId, effectiveId, xformsControl, isTemplate);
                }
            }
        }

        if (handlerContext.isNewXHTMLLayout()) {
            if (isMustOutputControl(xformsControl)) {
                // Close control <div>
                final ContentHandler contentHandler = handlerContext.getController().getOutput();
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
        }
    }

    private final boolean hasLocalLabel() {
        final XFormsStaticState staticState = containingDocument.getStaticState();
        return hasLocalElement(staticState, staticState.getLabelElement(prefixedId));
    }

    private final boolean hasLocalHint() {
        final XFormsStaticState staticState = containingDocument.getStaticState();
        return hasLocalElement(staticState, staticState.getHintElement(prefixedId));
    }

    private final boolean hasLocalHelp() {
        final XFormsStaticState staticState = containingDocument.getStaticState();
        return hasLocalElement(staticState, staticState.getHelpElement(prefixedId));
    }

    private final boolean hasLocalAlert() {
        final XFormsStaticState staticState = containingDocument.getStaticState();
        return hasLocalElement(staticState, staticState.getAlertElement(prefixedId));
    }

    private final boolean hasLocalElement(XFormsStaticState staticState, Element lhhaElement) {
        if (lhhaElement == null)
            return false;

        final Element controlElement = staticState.getControlElement(prefixedId);
        return lhhaElement.getParent() == controlElement;
    }

    protected void prepareHandler(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
    }

    protected boolean isMustOutputControl(XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
        return true;
    }

    protected void addCustomClasses(FastStringBuffer classes, XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
    }

    protected boolean isDefaultIncremental() {
        // May be overridden by subclasses
        return false;
    }

    protected void handleLabel(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(effectiveId, "label", xformsControl, isTemplate);
    }

    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(effectiveId, "alert", xformsControl, isTemplate);
    }

    protected void handleHint(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(effectiveId, "hint", xformsControl, isTemplate);
    }

    protected void handleHelp(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(effectiveId, "help", xformsControl, isTemplate);
    }

    // Must be overridden by subclasses
    protected abstract void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException;

    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {
        // May be overridden by subclasses
    }
}
