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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;
import java.util.HashMap;

/**
 * Handle xforms:output.
 */
public class XFormsOutputHandler extends XFormsValueControlHandler {

    private static final Map inlineParentsMap = new HashMap();
    static {
        inlineParentsMap.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, "label"), "");
        inlineParentsMap.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, "hint"), "");
        inlineParentsMap.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, "alert"), "");
        inlineParentsMap.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, "help"), "");
        inlineParentsMap.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, "message"), "");
        inlineParentsMap.put(XMLUtils.buildExplodedQName(XFormsConstants.XFORMS_NAMESPACE_URI, "value"), "");
    }

    private Attributes elementAttributes;

    public XFormsOutputHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsOutputControl xformsOutputControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsOutputControl) containingDocument.getObjectById(pipelineContext, effectiveId);
        final boolean isConcreteControl = xformsOutputControl != null;

        // Don't do anything when xforms:output is used as a "pseudo-control", that is when it is within
        // xforms:label|xforms:hint|xforms:alert|xforms:help|xforms:message|xforms:value, because in that case we don't
        // put the control in the regular hierarchy of controls.
        final String parentHandlerName = handlerContext.getController().getParentHandlerExplodedQName();
        if (parentHandlerName != null && inlineParentsMap.get(parentHandlerName) != null)
            return;

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", xformsOutputControl);

        final AttributesImpl newAttributes;
        final boolean isDateOrTime;
        final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsOutputControl);

        final String mediatypeValue = elementAttributes.getValue("mediatype");
        final boolean isImageMediatype = mediatypeValue != null && mediatypeValue.startsWith("image/");
        final boolean isHTMLMediaType = (mediatypeValue != null && mediatypeValue.equals("text/html"))
                || XFormsConstants.XXFORMS_HTML_APPEARANCE_QNAME.equals(getAppearance(elementAttributes));

        if (!handlerContext.isGenerateTemplate()) {

            // Find classes to add
            isDateOrTime = isConcreteControl && isDateOrTime(xformsOutputControl.getType());
            handleMIPClasses(classes, xformsOutputControl);

            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        } else {
            isDateOrTime = false;

            // Find classes to add
            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        }

        // Create xhtml:span or xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        // For IE we need to generate a div here for IE, which doesn't support working with innterHTML on spans.
        final String enclosingElementLocalname = isHTMLMediaType ? "div" : "span";
        final String enclosingElementQName = XMLUtils.buildQName(xhtmlPrefix, enclosingElementLocalname);

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, newAttributes);
        {
            if (isImageMediatype) {
                // Case of image media type with URI
                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                final AttributesImpl imgAttributes = new AttributesImpl();
                // @src="..."
                // NOTE: If producing a template, we must point to an existing image
                final String srcValue = isConcreteControl ? xformsOutputControl.getValue() : XFormsConstants.DUMMY_IMAGE_URI;
                imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, srcValue);
                // @f:url-norewrite="true"
                final String formattingPrefix;
                final boolean isNewPrefix;
                {
                    if (isConcreteControl) {
                        final String existingFormattingPrefix = handlerContext.findFormattingPrefix();
                        if (existingFormattingPrefix == null || "".equals(existingFormattingPrefix)) {
                            // No prefix is currently mapped
                            formattingPrefix = handlerContext.findNewPrefix();
                            isNewPrefix = true;
                        } else {
                            formattingPrefix = existingFormattingPrefix;
                            isNewPrefix = false;
                        }
                        imgAttributes.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
                    } else {
                        // In the case of a template, allow for rewriting
                        formattingPrefix = null;
                        isNewPrefix = false;
                    }
                }
                if (isNewPrefix)
                    contentHandler.startPrefixMapping(formattingPrefix, XMLConstants.OPS_FORMATTING_URI);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                if (isNewPrefix)
                    contentHandler.endPrefixMapping(formattingPrefix);
            } else if (isDateOrTime) {
                // Display formatted value for dates
                if (isConcreteControl) {
                    final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                    if (displayValue != null)
                        contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
                }
            } else if (isHTMLMediaType) {
                // HTML case

                if (isConcreteControl) {
                    final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                    XFormsUtils.streamHTMLFragment(contentHandler, displayValue, xformsOutputControl.getLocationData(), xhtmlPrefix);
                }
            } else {
                // Regular text case
                if (isConcreteControl) {
                    final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                    if (displayValue != null && displayValue.length() > 0)
                        contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
                }
            }
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsOutputControl);

        // xforms:alert
        if (elementAttributes.getValue("value") == null)
            handleLabelHintHelpAlert(effectiveId, "alert", xformsOutputControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsOutputControl);
    }

}
