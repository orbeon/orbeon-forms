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

import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSecretControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:secret.
 */
public class XFormsSecretHandler extends XFormsControlLifecyleHandler {

    private static final String HIDDEN_PASSWORD = "********";

    public XFormsSecretHandler() {
        super(false);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsSecretControl secretControl = (XFormsSecretControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, secretControl);
            handleMIPClasses(classes, getPrefixedId(), secretControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
            handleReadOnlyAttribute(newAttributes, containingDocument, secretControl);

            if (secretControl != null) {
                // Output extension attributes in no namespace
                secretControl.addExtensionAttributes(newAttributes, "");
            }
        }

        // Create xhtml:input
        {
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            if (!isStaticReadonly(secretControl)) {
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");
                newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "password");
                newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);
                newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA,
                        handlerContext.isTemplate() || secretControl == null || secretControl.getExternalValue(pipelineContext) == null ? "" : secretControl.getExternalValue(pipelineContext));

                // Handle accessibility attributes
                handleAccessibilityAttributes(attributes, newAttributes);

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, newAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
            } else {
                final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
                final String value = secretControl.getValue(pipelineContext);
                // TODO: Make sure that Ajax response doesn't send the value back
                if (value != null && value.length() > 0)
                    contentHandler.characters(HIDDEN_PASSWORD.toCharArray(), 0, HIDDEN_PASSWORD.length());
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
        }
    }
}
