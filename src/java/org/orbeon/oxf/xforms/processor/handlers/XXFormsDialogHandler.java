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
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xxforms:dialog.
 */
public class XXFormsDialogHandler extends HandlerBase {

    private boolean isMinimalAppearance;

    public XXFormsDialogHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        final String effectiveDialogId = handlerContext.getEffectiveId(attributes);
        final XXFormsDialogControl dialogXFormsControl = ((XXFormsDialogControl) containingDocument.getObjectByEffectiveId(effectiveDialogId));
        isMinimalAppearance = XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(getAppearance(attributes));

        // Find classes to add
        final FastStringBuffer classes = getInitialClasses(localname, attributes, null);
        classes.append(" xforms-initially-hidden");
        classes.append(" xforms-dialog-");
        classes.append(dialogXFormsControl.getLevel());
        classes.append(" xforms-dialog-close-");
        classes.append(Boolean.toString(dialogXFormsControl.isClose()));
        classes.append(" xforms-dialog-draggable-");
        classes.append(Boolean.toString(dialogXFormsControl.isDraggable()));
        classes.append(" xforms-dialog-visible-");
        classes.append(Boolean.toString(dialogXFormsControl.isInitiallyVisible()));

        // Start main xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, getAttributes(attributes, classes.toString(), effectiveDialogId));

        if (!isMinimalAppearance) {
            // Child xhtml:div for label
            final String labelValue = dialogXFormsControl.getLabel(pipelineContext);
            if (labelValue != null) {
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "hd");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
                contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
            }

            // Child xhtml:div for body
            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
        } else {
            // Two nested xhtml:div
            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd1");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
            reusableAttributes.clear();
            reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "bd2");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName, reusableAttributes);
        }
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        // Close xhtml:div's
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);

        // One more to close with minimal appearance
        if (isMinimalAppearance)
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "div", divQName);
    }
}
