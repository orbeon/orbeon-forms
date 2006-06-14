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
import org.orbeon.oxf.xforms.controls.ControlInfo;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Base class for controls with values.
 */
public abstract class XFormsValueControlHandler extends HandlerBase {

    private int level = 0;

    private Attributes labelAttributes;
    private Attributes helpAttributes;
    private Attributes hintAttributes;
    private Attributes alertAttributes;

    protected XFormsValueControlHandler(boolean repeating) {
        super(repeating, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Reset state, as this handler may be reused
        level = 0;
        labelAttributes = null;
        helpAttributes = null;
        hintAttributes = null;
        alertAttributes = null;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        level++;
        if (level == 1 && XFormsConstants.XFORMS_NAMESPACE_URI.equals(uri)) {
            // Handle direct children only
            if ("label".equals(localname)) {
                labelAttributes = new AttributesImpl(attributes);
            } else if ("hint".equals(localname)) {
                hintAttributes = new AttributesImpl(attributes);
            } else if ("help".equals(localname)) {
                helpAttributes = new AttributesImpl(attributes);
            } else if ("alert".equals(localname)) {
                alertAttributes = new AttributesImpl(attributes);
            }
        }
        super.startElement(uri, localname, qName, attributes);
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        super.endElement(uri, localname, qName);
        level--;
    }

    protected void handleLabelHintHelpAlert(String parentId, String type, ControlInfo controlInfo) throws SAXException {
        final String value;

        if (controlInfo != null) {
            // Get actual value from control
            if (type.equals("label")) {
                value = controlInfo.getLabel();
            } else if (type.equals("help")) {
                value = controlInfo.getHelp();
            } else if (type.equals("hint")) {
                value = controlInfo.getHint();
            } else if (type.equals("alert")) {
                value = controlInfo.getAlert();
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        } else {
            // Placeholder
            value = null;
        }

        // Find id
        final Attributes labelHintHelpAlertAttributes;
        if (type.equals("label")) {
            labelHintHelpAlertAttributes = labelAttributes;
        } else if (type.equals("help")) {
            labelHintHelpAlertAttributes = helpAttributes;
        } else if (type.equals("hint")) {
            labelHintHelpAlertAttributes = hintAttributes;
        } else if (type.equals("alert")) {
            labelHintHelpAlertAttributes = alertAttributes;
        } else {
            throw new IllegalStateException("Illegal type requested");
        }

        // If no attributes were found, there is no such label / help / hint
        if (labelHintHelpAlertAttributes != null) {
            //final String id = labelHintHelpId +  handlerContext.getIdPostfix();

            final StringBuffer classes = new StringBuffer("xforms-");
            classes.append(type);
            if (type.equals("alert")) {
                if (!handlerContext.isGenerateTemplate() && !controlInfo.isValid())
                    classes.append(" xforms-alert-active");
                else
                    classes.append(" xforms-alert-inactive");
            }

            outputLabelHintHelpAlert(handlerContext, getAttributes(labelHintHelpAlertAttributes, classes.toString(), null), parentId, value);
        }
    }

    public static void outputLabelHintHelpAlert(HandlerContext handlerContext, AttributesImpl labelHintHelpAlertAttributes, String parentId, String value) throws SAXException {
        labelHintHelpAlertAttributes.addAttribute("", "for", "for", ContentHandlerHelper.CDATA, parentId);

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String labelQName = XMLUtils.buildQName(xhtmlPrefix, "label");
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "label", labelQName, labelHintHelpAlertAttributes);
        if (!handlerContext.isGenerateTemplate() && value != null) {
            contentHandler.characters(value.toCharArray(), 0, value.length());
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "label", labelQName);
    }

    protected static void copyAttributes(Attributes sourceAttributes, String sourceNamespaceURI, String[] sourceAttributeLocalNames, AttributesImpl destAttributes) {
        for (int i = 0; i < sourceAttributeLocalNames.length; i++) {
            final String attributeName = sourceAttributeLocalNames[i];
            final String attributeValue = sourceAttributes.getValue(sourceNamespaceURI, attributeName);
            if (attributeValue != null)
                destAttributes.addAttribute("", attributeName, attributeName, ContentHandlerHelper.CDATA, attributeValue);
        }
    }
}
