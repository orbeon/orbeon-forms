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
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:upload.
 */
public class XFormsUploadHandler extends XFormsControlLifecyleHandler {

    public XFormsUploadHandler() {
        super(false);
    }

    protected void addCustomClasses(FastStringBuffer classes, XFormsSingleNodeControl xformsControl) {

        // Control value
        final XFormsUploadControl uploadControl = (XFormsUploadControl) xformsControl;
        final String value = handlerContext.isTemplate() || uploadControl == null || uploadControl.getExternalValue(pipelineContext) == null ? "" : uploadControl.getExternalValue(pipelineContext);

        if (value.equals(""))
            classes.append(" xforms-upload-state-empty");
        else
            classes.append(" xforms-upload-state-file");
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsUploadControl uploadControl = (XFormsUploadControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, uploadControl);
            addCustomClasses(classes, xformsControl);
            handleMIPClasses(classes, getPrefixedId(), uploadControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

            if (uploadControl != null) {
                // Output extension attributes in no namespace
                uploadControl.addExtensionAttributes(newAttributes, "");
            }
        }

        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        {
            // Create enclosing xhtml:span
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "span");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, newAttributes);
            {
                // Create xhtml:input
                final String inputQName = XMLUtils.buildQName(xhtmlPrefix, "input");

                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-select");
                reusableAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "file");
                reusableAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);
                // IE causes issues when the user types in or pastes in an incorrect file name. Some sites use this to
                // disable pasting in the file. See http://tinyurl.com/6dcd6a
                reusableAttributes.addAttribute("", "unselectable", "unselectable", ContentHandlerHelper.CDATA, "on");
                // NOTE: @value was meant to suggest an initial file name, but this is not supported by browsers

                // Handle accessibility attributes
                handleAccessibilityAttributes(attributes, reusableAttributes);

                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName, reusableAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", inputQName);
            }
            {
                // Create nested xhtml:span
                reusableAttributes.clear();
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-info");
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);

                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-filename");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    final String filename = (uploadControl == null) ? null : uploadControl.getFileName(pipelineContext);
                    if (filename != null)
                        contentHandler.characters(filename.toCharArray(), 0, filename.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }
                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-mediatype");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    final String mediatype = (uploadControl == null) ? null : uploadControl.getFileMediatype(pipelineContext);
                    if (mediatype != null)
                        contentHandler.characters(mediatype.toCharArray(), 0, mediatype.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }
                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-size");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    final String size = (uploadControl == null) ? null : uploadControl.getFileSize(pipelineContext);
                    if (size != null)
                        contentHandler.characters(size.toCharArray(), 0, size.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }
                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-remove");
                    reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, "/ops/images/xforms/remove.gif");
                    reusableAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, "Remove File");
                    reusableAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, "Remove File");
                    final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                }

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }
    }
}
