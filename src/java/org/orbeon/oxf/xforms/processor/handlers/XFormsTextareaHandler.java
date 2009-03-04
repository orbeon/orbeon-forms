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

import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTextareaControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:textarea.
 */
public class XFormsTextareaHandler extends XFormsControlLifecyleHandler {

    public XFormsTextareaHandler() {
        super(false);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsTextareaControl textareaControl = (XFormsTextareaControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = textareaControl != null;

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, textareaControl);
            handleMIPClasses(classes, getPrefixedId(), textareaControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
            handleReadOnlyAttribute(newAttributes, containingDocument, textareaControl);

            if (isConcreteControl) {
                // Output extension attributes in no namespace
                textareaControl.addExtensionAttributes(newAttributes, "");
            }
        }

        // Create xhtml:textarea
        {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (!isStaticReadonly(textareaControl)) {
                final String textareaQName = XMLUtils.buildQName(xhtmlPrefix, "textarea");
                newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);

                // Handle accessibility attributes
                handleAccessibilityAttributes(attributes, newAttributes);

                // Output all extension attributes
                if (isConcreteControl) {
                    // Output xxforms:* extension attributes
                    textareaControl.addExtensionAttributes(reusableAttributes, XFormsConstants.XXFORMS_NAMESPACE_URI);
                }

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName, newAttributes);
                if (isConcreteControl) {
                    final String value = textareaControl.getExternalValue(pipelineContext);
                    if (value != null)
                        contentHandler.characters(value.toCharArray(), 0, value.length());
                }
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "textarea", textareaQName);
            } else {
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
                if (isConcreteControl) {
                    final String value = textareaControl.getExternalValue(pipelineContext);
                    if (value != null) {
                        final boolean isHTMLMediaType = "text/html".equals(textareaControl.getMediatype());
                        if (!isHTMLMediaType) {
                            contentHandler.characters(value.toCharArray(), 0, value.length());
                        } else {
                            XFormsUtils.streamHTMLFragment(contentHandler, value, textareaControl.getLocationData(), xhtmlPrefix);
                        }
                    }
                }
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
        }
    }
}
