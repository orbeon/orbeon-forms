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

import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Handle xforms:upload.
 */
public class XFormsUploadHandler extends XFormsValueControlHandler {

    private Attributes elementAttributes;

    public XFormsUploadHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsUploadControl xformsControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsUploadControl) containingDocument.getObjectById(pipelineContext, effectiveId);

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", xformsControl);

        final AttributesImpl newAttributes;
        {
            final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsControl);
            if (!handlerContext.isGenerateTemplate()) {
                // The control is initially empty
                // TODO: can it be initially non-empty?
                classes.append(" xforms-upload-state-empty");
                handleMIPClasses(classes, xformsControl);
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            } else {
                newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
            }
        }

        // Handle accessibility attributes
        handleAccessibilityAttributes(elementAttributes, newAttributes);

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
                reusableAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA,
                        handlerContext.isGenerateTemplate() ? "" : xformsControl.getValue() != null ? xformsControl.getValue() : "");

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
                    final String filename = (xformsControl == null) ? null : xformsControl.getFilename(pipelineContext);
                    if (filename != null)
                        contentHandler.characters(filename.toCharArray(), 0, filename.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }
                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-mediatype");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    final String mediatype = (xformsControl == null) ? null : xformsControl.getMediatype(pipelineContext);
                    if (mediatype != null)
                        contentHandler.characters(mediatype.toCharArray(), 0, mediatype.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }
                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-size");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName, reusableAttributes);
                    final String size = (xformsControl == null) ? null : xformsControl.getSize(pipelineContext);
                    if (size != null)
                        contentHandler.characters(size.toCharArray(), 0, size.length());
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
                }
                {
                    reusableAttributes.clear();
                    reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, "xforms-upload-remove");
                    reusableAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, "/ops/images/xforms/remove.png");
                    reusableAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, "Remove File");
                    final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                    contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, reusableAttributes);
                    contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                }

                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
            }
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "span", spanQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsControl);

        // xforms:alert
        handleLabelHintHelpAlert(effectiveId, "alert", xformsControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsControl);
    }
}
