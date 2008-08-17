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
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
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

    protected boolean isMustOutputControl(XFormsSingleNodeControl xformsControl) {
        // Don't do anything when xforms:output is used as a "pseudo-control", that is when it is within a leaf control,
        // because in that case we don't put the control in the regular hierarchy of controls.
        final String parentHandlerName = handlerContext.getController().getParentHandlerExplodedQName();
        if (parentHandlerName != null) {
            final String parentHandlerLocalname;
            final int bracketIndex = parentHandlerName.indexOf("}");
            if (bracketIndex != -1) {
                parentHandlerLocalname = parentHandlerName.substring(bracketIndex + 1);
            } else {
                parentHandlerLocalname = parentHandlerName;
            }
            if (XFormsControlFactory.isCoreControl(parentHandlerLocalname))
                return false;
        }
        return true;
    }

    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // Handle alert only if there is no value attribute
        if (attributes.getValue("value") == null)
            super.handleAlert(staticId, effectiveId, attributes, xformsControl, isTemplate);
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsOutputControl outputControl = (XFormsOutputControl) xformsControl;

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final boolean isConcreteControl = outputControl != null;

        final AttributesImpl newAttributes;
        final FastStringBuffer classes = getInitialClasses(localname, attributes, outputControl);

        final String mediatypeValue = attributes.getValue("mediatype");
        final boolean isImageMediatype = mediatypeValue != null && mediatypeValue.startsWith("image/");
        final boolean isHTMLMediaType = (mediatypeValue != null && mediatypeValue.equals("text/html"));

        handleMIPClasses(classes, id, outputControl);
        newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

        if (XFormsConstants.XXFORMS_TEXT_APPEARANCE_QNAME.equals(getAppearance(attributes))) {
            // Just output value for "text" appearance
            if (isImageMediatype || isHTMLMediaType) {
                throw new ValidationException("Cannot use mediatype value for \"xxforms:text\" appearance: " + mediatypeValue, handlerContext.getLocationData());
            }

            if (isConcreteControl) {
                final String displayValue = outputControl.getDisplayValueOrExternalValue(pipelineContext);
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
                    final String srcValue = isConcreteControl ? outputControl.getExternalValue(pipelineContext) : XFormsConstants.DUMMY_IMAGE_URI;
                    imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, srcValue.trim().equals("") ? XFormsConstants.DUMMY_IMAGE_URI : srcValue);

                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                } else if (isHTMLMediaType) {
                    // HTML case
                    if (isConcreteControl) {
                        final String displayValue = outputControl.getDisplayValueOrExternalValue(pipelineContext);
                        XFormsUtils.streamHTMLFragment(contentHandler, displayValue, outputControl.getLocationData(), xhtmlPrefix);
                    }
                } else {
                    // Regular text case
                    if (isConcreteControl) {
                        final String displayValue = outputControl.getDisplayValueOrExternalValue(pipelineContext);
                        if (displayValue != null && displayValue.length() > 0)
                            contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
                    }
                }
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);
        }
    }
}
