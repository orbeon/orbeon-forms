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

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Default full appearance (button).
 *
 * This can also be the "pseudo-minimal" appearance for noscript mode.
 */
public class XFormsTriggerFullHandler extends XFormsTriggerHandler {

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsTriggerControl triggerControl = (XFormsTriggerControl) control;
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        final String labelValue = getTriggerLabel(triggerControl);
        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, triggerControl, true);

        final boolean mustOutputHTMLFragment = triggerControl != null && triggerControl.isHTMLLabel();
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

        final String elementName;
        if (handlerContext.isNoScript()) {
            // Noscript mode

            // We need a name to detect activation
            containerAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);

            final String inputType;
            if (handlerContext.isRenderingEngineIE6OrEarlier()) {

                // IE 6 does not support discriminating between multiple buttons: it sends them all, so we use
                // "input" instead. The code below tries to output <input type="submit"> or <input type="image">
                // depending on the content of the label. This has limitations: we can only handle text or a single
                // image.

                final String inputLabelValue;
                if (mustOutputHTMLFragment) {
                    // Only output character content within input
                    final XFormsControls xformsControls = containingDocument.getControls();
                    xformsControls.getIndentedLogger().logWarning("xforms:trigger", "IE 6 does not support <button> elements properly. Only text within HTML content will appear garbled.", "control id", effectiveId);

                    final StringBuilder sb = new StringBuilder(labelValue.length());

                    final String[] imageInfo = new String[3];// who needs classes? ;-)
                    XFormsUtils.streamHTMLFragment(new XMLReceiverAdapter() {
                        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
                            if (imageInfo[0] == null && "img".equals(localName)) {
                                // Remember information of first image found
                                imageInfo[0] = atts.getValue("src");
                                imageInfo[1] = atts.getValue("alt");
                                imageInfo[2] = atts.getValue("title");
                            }
                        }

                        public void characters(char ch[], int start, int length) throws SAXException {
                            sb.append(ch, start, length);
                        }
                    }, labelValue, triggerControl.getLocationData(), xhtmlPrefix);

                    final String sbString = sb.toString();
                    if (imageInfo[0] != null && sbString.trim().equals("")) {
                        // There is an image and no text, output image
                        inputType = "image";
                        inputLabelValue = "";
                        containerAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, imageInfo[0]);
                        if (imageInfo[1] != null)
                            containerAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, imageInfo[1]);
                        if (imageInfo[2] != null)
                            containerAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, imageInfo[2]);
                    } else {
                        // Output text
                        inputType = "submit";
                        inputLabelValue = sbString;
                    }
                } else {
                    inputType = "submit";
                    inputLabelValue = labelValue;
                }

                containerAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, inputLabelValue);
                elementName = "input";
            } else {
                // We need a value so we can detect an activated button with IE 7
                // NOTE: IE 6/7 sends the <button> content as value instead of the value attribute!
                containerAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "activate");
                elementName = "button";
                inputType = "submit";
            }

            // In JS-free mode, all buttons are submit inputs or image inputs
            containerAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, inputType);
        } else {
            // Script mode

            // Just a button without action
            containerAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "button");
            elementName = "button";
        }

        // xhtml:button or xhtml:input
        final String spanQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
        if (isHTMLDisabled(triggerControl))
            outputDisabledAttribute(containerAttributes);
        xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, spanQName, containerAttributes);
        {
            if ("button".equals(elementName)) {
                // Output content of <button> element
                outputLabelText(xmlReceiver, triggerControl, labelValue, xhtmlPrefix, mustOutputHTMLFragment);
            }
        }
        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, spanQName);
    }
}
