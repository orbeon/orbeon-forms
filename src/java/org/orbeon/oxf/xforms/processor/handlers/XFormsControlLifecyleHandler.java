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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This class is a helper base class which handles the lifecycle of producing markup for a control. The following
 * phases are handled:
 *
 * o Give the handler a chance to do some prep work: prepareHandler()
 * o Check whether the control wants any output at all: isMustOutputControl()
 * o Output label, control, hint, help, and alert in order specified by properties
 *
 * Outputting the control is split into two parts: handleControlStart() and handleControlEnd(). In most cases, only
 * handleControlStart() is used, but container controls will use handleControlEnd().
 */
public abstract class XFormsControlLifecyleHandler extends XFormsBaseHandler {

    private String staticId;
    private String effectiveId;
    private XFormsSingleNodeControl xformsControl;
    private Attributes attributes;
    private String[] endConfig;

    protected XFormsControlLifecyleHandler(boolean repeating) {
        super(repeating, false);
    }

    protected XFormsControlLifecyleHandler(boolean repeating, boolean forwarding) {
        super(repeating, forwarding);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        staticId = handlerContext.getId(attributes);
        effectiveId = handlerContext.getEffectiveId(attributes);
        xformsControl = handlerContext.isTemplate()
                ? null : (XFormsSingleNodeControl) containingDocument.getObjectByEffectiveId(effectiveId);

        // Give the handler a chance to do some prep work
        prepareHandler(uri, localname, qName, attributes, staticId, effectiveId, xformsControl);

        if (isMustOutputControl(xformsControl)) {
            // Get local order for control
            final String localOrder = attributes.getValue(XFormsConstants.XXFORMS_ORDER_QNAME.getNamespaceURI(),
                    XFormsConstants.XXFORMS_ORDER_QNAME.getName());

            // Use local or default config
            final String[] config = (localOrder != null) ? StringUtils.split(localOrder) : handlerContext.getDocumentOrder();

            final boolean isTemplate = handlerContext.isTemplate();

            // Process everything up to and including the control
            for (int i = 0; i < config.length; i++) {
                final String current = config[i];

                if ("control".equals(current)) {
                    // Handle control

                    if (isNoscript) {
                        // Output named anchor if the control has a help. This is so that a separate help section can
                        // link back to the control.
                        if (xformsControl != null && XFormsControl.hasHelp(containingDocument, xformsControl, staticId)) {
                            final ContentHandler contentHandler = handlerContext.getController().getOutput();
                            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
                            final String aQName = XMLUtils.buildQName(xhtmlPrefix, "a");
                            reusableAttributes.clear();
                            reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, xformsControl.getEffectiveId());
                            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName, reusableAttributes);
                            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", aQName);
                        }
                    }

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
                    if (XFormsControl.hasLabel(containingDocument, xformsControl, staticId))
                        handleLabel(staticId, effectiveId, xformsControl, isTemplate);
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (XFormsControl.hasAlert(containingDocument, xformsControl, staticId))
                        handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (XFormsControl.hasHint(containingDocument, xformsControl, staticId))
                        handleHint(staticId, effectiveId, xformsControl, isTemplate);
                } else {
                    // xforms:help
                    if (XFormsControl.hasHelp(containingDocument, xformsControl, staticId))
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
                    if (XFormsControl.hasLabel(containingDocument, xformsControl, staticId))
                        handleLabel(staticId, effectiveId, xformsControl, isTemplate);
                } else if ("alert".equals(current)) {
                    // xforms:alert
                    if (XFormsControl.hasAlert(containingDocument, xformsControl, staticId))
                        handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
                } else if ("hint".equals(current)) {
                    // xforms:hint
                    if (XFormsControl.hasHint(containingDocument, xformsControl, staticId))
                        handleHint(staticId, effectiveId, xformsControl, isTemplate);
                } else {
                    // xforms:help
                    if (XFormsControl.hasHelp(containingDocument, xformsControl, staticId))
                        handleHelp(staticId, effectiveId, xformsControl, isTemplate);
                }
            }
        }
    }

    protected void prepareHandler(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
    }

    protected boolean isMustOutputControl(XFormsSingleNodeControl xformsControl) {
        // May be overridden by subclasses
        return true;
    }

    protected void handleLabel(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(staticId, effectiveId, "label", xformsControl, isTemplate);
    }

    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(staticId, effectiveId, "alert", xformsControl, isTemplate);
    }

    protected void handleHint(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(staticId, effectiveId, "hint", xformsControl, isTemplate);
    }

    protected void handleHelp(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // May be overridden by subclasses
        handleLabelHintHelpAlert(staticId, effectiveId, "help", xformsControl, isTemplate);
    }

    // Must be overridden by subclasses
    protected abstract void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException;

    protected void handleControlEnd(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {
        // May be overridden by subclasses
    }
}
