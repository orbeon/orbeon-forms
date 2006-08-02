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

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.generator.TidyConfig;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.w3c.tidy.Tidy;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Locator;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Handle xforms:output.
 */
public class XFormsOutputHandler extends XFormsValueControlHandler {

    private Attributes elementAttributes;

    public XFormsOutputHandler() {
        super(false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);
        super.start(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsOutputControl xformsOutputControl = handlerContext.isGenerateTemplate()
                ? null : (XFormsOutputControl) containingDocument.getObjectById(pipelineContext, effectiveId);

        // The "control" is allowed to be null when xforms:output is in
        // xforms:label|xforms:hint|xforms:alert|xforms:help, because in that case currently we don't put the control in
        // the regular hierarchy of controls
        if (xformsOutputControl == null && !handlerContext.isGenerateTemplate())
            return;

        // xforms:label
        handleLabelHintHelpAlert(effectiveId, "label", xformsOutputControl);

        final AttributesImpl newAttributes;
        final boolean isDateOrTime;
        final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsOutputControl);

        final String mediatypeValue = elementAttributes.getValue("mediatype");
        final boolean isImageMediatype = mediatypeValue != null && mediatypeValue.startsWith("image/");
        final boolean isHTMLMediaType = (mediatypeValue != null && mediatypeValue.equals("text/html"))
                || XFormsConstants.XXFORMS_HTML_APPEARANCE_QNAME.equals(getAppearance(elementAttributes));

        if (!handlerContext.isGenerateTemplate()) {

            // Find classes to add
            isDateOrTime = isDateOrTime(xformsOutputControl.getType());
            handleMIPClasses(classes, xformsOutputControl);

            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        } else {
            isDateOrTime = false;

            // Find classes to add
            newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);
        }

        // Create xhtml:span or xhtml:div
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        // For IE we need to generate a div here for IE, which doesn't support working with innterHTML on spans.
        final String enclosingElementLocalname = isHTMLMediaType ? "div" : "span";
        final String enclosingElementQName = XMLUtils.buildQName(xhtmlPrefix, enclosingElementLocalname);

        contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName, newAttributes);
        if (!handlerContext.isGenerateTemplate()) {
            if (isImageMediatype) {
                // Case of image media type with URI
                final String imgQName = XMLUtils.buildQName(xhtmlPrefix, "img");
                final AttributesImpl imgAttributes = new AttributesImpl();
                // @src="..."
                imgAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, xformsOutputControl.getValue());
                // @f:url-norewrite="true"
                final String formattingPrefix;
                final boolean isNewPrefix;
                {
                    final String existingFormattingPrefix = handlerContext.findFormattingPrefix();
                    if (existingFormattingPrefix == null || "".equals(existingFormattingPrefix)) {
                        // No prefix is currently mapped
                        formattingPrefix = handlerContext.findNewPrefix();
                        isNewPrefix = true;
                    } else {
                        formattingPrefix = existingFormattingPrefix;
                        isNewPrefix = false;
                    }
                    imgAttributes.addAttribute(XMLConstants.OPS_FORMATTING_URI, "url-norewrite", XMLUtils.buildQName(formattingPrefix, "url-norewrite"), ContentHandlerHelper.CDATA, "true");
                }
                if (isNewPrefix)
                    contentHandler.startPrefixMapping(formattingPrefix, XMLConstants.OPS_FORMATTING_URI);
                contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName, imgAttributes);
                contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "img", imgQName);
                if (isNewPrefix)
                    contentHandler.endPrefixMapping(formattingPrefix);
            } else if (isDateOrTime) {
                // Display formatted value for dates
                final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                if (displayValue != null)
                    contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
            } else if (isHTMLMediaType) {
                // HTML case

                final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                if (displayValue != null && displayValue.length() > 0) {
                    // Create and configure Tidy instance
                    final Tidy tidy = new Tidy();
                    tidy.setShowWarnings(false);
                    tidy.setQuiet(true);
                    tidy.setCharEncoding(TidyConfig.getTidyEncoding("utf-8"));

                    // Parse and output to SAXResult
                    final byte[] valueBytes;
                    try {
                        valueBytes = displayValue.getBytes("utf-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new OXFException(e); // will not happen
                    }
                    // TODO: optimize and skip creation of Dom4j document
                    final Document dom4jResult;
                    try {
                        final InputStream is = new ByteArrayInputStream(valueBytes);
                        final org.w3c.dom.Document result = tidy.parseDOM(is, null);
                        dom4jResult = TransformerUtils.domToDom4jDocument(result);
                    } catch (TransformerException e) {
                        throw new OXFException(e);
                    }

                    // Create content document
                    final Element htmlElement = dom4jResult.getRootElement();
                    final Element bodyElement = htmlElement.element("body");
                    final Document bodyDocument =  Dom4jUtils.createDocument();
                    bodyDocument.setRootElement((Element) bodyElement.detach());

                    // Stream fragment to the output
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        identity.transform(new DocumentSource(bodyDocument), new SAXResult(new ForwardingContentHandler(contentHandler) {

                            private int level = 0;

                            public void startDocument() {
                            }

                            public void endDocument() {
                            }

                            public void startPrefixMapping(String s, String s1) {
                            }

                            public void endPrefixMapping(String s) {
                            }

                            public void setDocumentLocator(Locator locator) {
                            }

                            public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
                                if (level > 0) {
                                    final String xhtmlQName = XMLUtils.buildQName(xhtmlPrefix, localname);
                                    super.startElement(XMLConstants.XHTML_NAMESPACE_URI, localname, xhtmlQName, attributes);
                                }

                                level++;
                            }

                            public void endElement(String uri, String localname, String qName) throws SAXException {
                                level--;

                                if (level > 0) {
                                    final String xhtmlQName = XMLUtils.buildQName(xhtmlPrefix, localname);
                                    super.endElement(XMLConstants.XHTML_NAMESPACE_URI, localname, xhtmlQName);
                                }
                            }

                        }));
                    } catch (TransformerException e) {
                        throw new OXFException(e);
                    }
                }

            } else {
                // Regular text case
                final String displayValue = xformsOutputControl.getDisplayValueOrValue();
                if (displayValue != null && displayValue.length() > 0)
                    contentHandler.characters(displayValue.toCharArray(), 0, displayValue.length());
            }
        }
        contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, enclosingElementLocalname, enclosingElementQName);

        // xforms:help
        handleLabelHintHelpAlert(effectiveId, "help", xformsOutputControl);

        // xforms:alert
        if (elementAttributes.getValue("value") == null)
            handleLabelHintHelpAlert(effectiveId, "alert", xformsOutputControl);

        // xforms:hint
        handleLabelHintHelpAlert(effectiveId, "hint", xformsOutputControl);
    }
}
