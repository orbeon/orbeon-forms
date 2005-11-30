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
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xhtml:group.
 */
public class XFormsGroupHandler extends HandlerBase {

    protected String effectiveGroupId;
    private XFormsControls.ControlInfo groupControlInfo;
    private boolean inLabel;

    public XFormsGroupHandler() {
        super(false, true);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        effectiveGroupId = handlerContext.getEffectiveId(attributes);

        // Find classes to add
        final StringBuffer classes = new StringBuffer("xforms-" + localname);
        if (!handlerContext.isGenerateTemplate()) {
            groupControlInfo = ((XFormsControls.ControlInfo) containingDocument.getObjectById(handlerContext.getPipelineContext(), effectiveGroupId));

            HandlerBase.handleMIPClasses(classes, groupControlInfo);
        }

        // Create xhtml:span
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
        handlerContext.getController().getOutput().startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, getAttributes(attributes, classes.toString(), effectiveGroupId));
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && localname.equals("label")) {
            // xforms:label
            final String labelValue = handlerContext.isGenerateTemplate() ? null : groupControlInfo.getLabel();

            final AttributesImpl labelAttributes = getAttributes(attributes, "xforms-label", null);
            XFormsValueControlHandler.outputLabelHintHelpAlert(handlerContext, labelAttributes, effectiveGroupId, labelValue);

            inLabel = true;

        } else {
            super.startElement(uri, localname, qName, attributes);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {

        if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri) && localname.equals("label")) {
            // xforms:label
            inLabel = false;
        } else {
            super.endElement(uri, localname, qName);
        }
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (!inLabel)
            super.characters(chars, start, length);
    }

    public void end(String uri, String localname, String qName) throws SAXException {
        // Close xhtml:span
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
        handlerContext.getController().getOutput().endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
    }
}
