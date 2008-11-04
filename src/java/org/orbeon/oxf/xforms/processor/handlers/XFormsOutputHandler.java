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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:output.
 * 
 * @noinspection SimplifiableIfStatement
 */
public class XFormsOutputHandler extends XFormsControlLifecyleHandler {

    public XFormsOutputHandler() {
        super(false);
    }

    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // Handle alert only if there is no value attribute
        if (attributes.getValue("value") == null)
            super.handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsOutputControl outputControl = (XFormsOutputControl) xformsControl;

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = outputControl != null;

        final String mediatypeValue = attributes.getValue("mediatype");
        final boolean isImageMediatype = mediatypeValue != null && mediatypeValue.startsWith("image/");
        final boolean isHTMLMediaType = (mediatypeValue != null && mediatypeValue.equals("text/html"));

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, outputControl);
            handleMIPClasses(classes, getPrefixedId(), outputControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
        }

        if (XFormsConstants.XXFORMS_TEXT_APPEARANCE_QNAME.equals(getAppearance(attributes))) {
            // Just output value for "text" appearance
            if (isImageMediatype || isHTMLMediaType) {
                throw new ValidationException("Cannot use mediatype value for \"xxforms:text\" appearance: " + mediatypeValue, handlerContext.getLocationData());
            }

            if (isConcreteControl) {
                final String displayValue = outputControl.getExternalValue(pipelineContext);
                if (displayValue != null && displayValue.length() > 0)
                    contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
            }

        } else {
            // Create xhtml:span or xhtml:div
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            // For IE we need to generate a div here for IE, which doesn't support working with innterHTML on spans.
            final String enclosingElementLocalname = isHTMLMediaType ? "div" : "span";
            final String enclosingElementQName = XMLUtils.buildQName(xhtmlPrefix, enclosingElementLocalname);

            // Handle accessibility attributes (de facto, tabindex is supported on all elements)
            handleAccessibilityAttributes(attributes, newAttributes);

            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, newAttributes);
            {
                if (isImageMediatype) {
                    // Case of image media type with URI
                    final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                    final AttributesImpl imgAttributes = new AttributesImpl();
                    // @src="..."
                    // NOTE: If producing a template, or if the image URL is blank, we point to an existing dummy image
                    final String srcValue = XFormsOutputControl.getExternalValue(pipelineContext, outputControl, mediatypeValue);
                    imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, srcValue);

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                } else if (isHTMLMediaType) {
                    // HTML case
                    if (isConcreteControl) {
                        final String htmlValue = XFormsOutputControl.getExternalValue(pipelineContext, outputControl, mediatypeValue);
                        XFormsUtils.streamHTMLFragment(contentHandler, htmlValue, outputControl.getLocationData(), xhtmlPrefix);
                    }
                } else {
                    // Regular text case
                    if (isConcreteControl) {
                        final String textValue = XFormsOutputControl.getExternalValue(pipelineContext, outputControl, mediatypeValue);
                        if (textValue != null && textValue.length() > 0)
                            contentHandler.characters(textValue.toCharArray(), 0, textValue.length());
                    }
                }
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);
        }
    }
}
