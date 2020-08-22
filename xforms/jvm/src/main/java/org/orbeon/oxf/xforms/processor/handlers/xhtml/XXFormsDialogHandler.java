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

import org.orbeon.xforms.XFormsNames;
import org.orbeon.oxf.xforms.control.XFormsControl$;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLReceiverHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Handle xxf:dialog.
 *
 * TODO: Subclasses per appearance.
 */
public class XXFormsDialogHandler extends XFormsBaseHandlerXHTML {

    public XXFormsDialogHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext) {
        super(uri, localname, qName, attributes, matched, handlerContext, false, true);
    }

    public void start() throws SAXException {

        final String effectiveDialogId = xformsHandlerContext.getEffectiveId(attributes());
        final XXFormsDialogControl dialogXFormsControl = ((XXFormsDialogControl) containingDocument.getControlByEffectiveId(effectiveDialogId));

        // Find classes to add

        // NOTE: attributes logic duplicated in XXFormsDialogControl
        // Get values statically so we can handle the case of the repeat template
        // TODO: 2020-02-27: There are no more repeat templates. Check this.
        final StringBuilder classes = getInitialClasses(uri(), localname(), attributes(), null, false, scala.Option.apply(null));
        {
            classes.append(" xforms-initially-hidden");
            classes.append(" xforms-dialog-");

            final String level; {
                final String explicitLevel = attributes().getValue("level");
                if (explicitLevel == null) {
                    level = XFormsControl$.MODULE$.appearances(elementAnalysis).contains(XFormsNames.XFORMS_MINIMAL_APPEARANCE_QNAME()) ? "modeless" : "modal";
                } else {
                    level = explicitLevel;
                }
            }

            classes.append(level);
            classes.append(" xforms-dialog-close-");
            classes.append(Boolean.toString(!"false".equals(attributes().getValue("close"))));
            classes.append(" xforms-dialog-draggable-");
            classes.append(Boolean.toString(!"false".equals(attributes().getValue("draggable"))));
            classes.append(" xforms-dialog-visible-");
            classes.append(Boolean.toString("true".equals(attributes().getValue("visible"))));
        }

        // Start main xhtml:div
        final String xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        final ContentHandler contentHandler = xformsHandlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), "div", divQName, getIdClassXHTMLAttributes(attributes(), classes.toString(), effectiveDialogId));

        // Child xhtml:div for label
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "hd xxforms-dialog-head");
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), "div", divQName, reusableAttributes);

        final String labelValue = (dialogXFormsControl != null) ? dialogXFormsControl.getLabel() : null;
        if (labelValue != null) {
            contentHandler.characters(labelValue.toCharArray(), 0, labelValue.length());
        }

        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), "div", divQName);

        // Child xhtml:div for body
        reusableAttributes.clear();
        reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, "bd xxforms-dialog-body");
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI(), "div", divQName, reusableAttributes);
    }

    @Override
    public void end() throws SAXException {

        // Close xhtml:div's
        final String xhtmlPrefix = xformsHandlerContext.findXHTMLPrefix();
        final String divQName = XMLUtils.buildQName(xhtmlPrefix, "div");
        final ContentHandler contentHandler = xformsHandlerContext.getController().getOutput();
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), "div", divQName);
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI(), "div", divQName);
    }
}
