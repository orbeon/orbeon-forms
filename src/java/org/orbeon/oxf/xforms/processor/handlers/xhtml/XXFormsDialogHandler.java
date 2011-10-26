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

import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xxforms:dialog.
 *
 * TODO: Subclasses per appearance.
 */
public class XXFormsDialogHandler extends XFormsBaseHandler {

    public XXFormsDialogHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String effectiveDialogId = handlerContext.getEffectiveId(attributes);
        final XXFormsDialogControl dialogXFormsControl = ((XXFormsDialogControl) containingDocument.getObjectByEffectiveId(effectiveDialogId));

        // Find classes to add

        // NOTE: attributes logic duplicated in XXFormsDialogControl
        // Get values statically so we can handle the case of the repeat template
        final StringBuilder classes = getInitialClasses(uri, localname, attributes, null);
        {
            classes.append(" xforms-initially-hidden");
            classes.append(" xforms-dialog-");

            final String level; {
                final String explicitLevel = attributes.getValue("level");
                if (explicitLevel == null) {
                    final QName appearance = getAppearance(attributes);
                    level = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(appearance) ? "modeless" : "modal";
                } else {
                    level = explicitLevel;
                }
            }

            classes.append(level);
            classes.append(" xforms-dialog-close-");
            classes.append(Boolean.toString(!"false".equals(attributes.getValue("close"))));
            classes.append(" xforms-dialog-draggable-");
            classes.append(Boolean.toString(!"false".equals(attributes.getValue("draggable"))));
            classes.append(" xforms-dialog-visible-");
            classes.append(Boolean.toString("true".equals(attributes.getValue("visible"))));
        }

        // Start main xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, getAttributes(attributes, classes.toString(), effectiveDialogId));

        // Child xhtml:div for label
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "hd xxforms-dialog-head");
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);

        final String labelValue = (dialogXFormsControl != null) ? dialogXFormsControl.getLabel() : null;
        if (labelValue != null) {
            contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

        // Child xhtml:div for body
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd xxforms-dialog-body");
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close xhtml:div's
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
    }
}
