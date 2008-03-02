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

import org.dom4j.QName;
import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

/**
 * Handle xforms:trigger.
 */
public class XFormsTriggerHandler extends HandlerBase {

    private AttributesImpl xxformsImgAttributes;
    private AttributesImpl elementAttributes;

    public XFormsTriggerHandler() {
        super(false, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        elementAttributes = new AttributesImpl(attributes);

        // Reset state, as this handler is reused
        xxformsImgAttributes = null;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (XFormsConstants.XXFORMS_NAMESPACE_URI.equals(uri)) {
            if (localname.equals("img")) {
                // xxforms:img
                xxformsImgAttributes = new AttributesImpl(attributes);
            }
        }
        super.startElement(uri, localname, qName, attributes);
    }

    public void end(String uri, String localname, String qName) throws SAXException {

        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // xforms:trigger and xforms:submit

        final String id = handlerContext.getId(elementAttributes);
        final String effectiveId = handlerContext.getEffectiveId(elementAttributes);
        final XFormsTriggerControl xformsControl = handlerContext.isGenerateTemplate() ? null : ((XFormsTriggerControl) containingDocument.getObjectById(effectiveId));

        if (isStaticReadonly(xformsControl))
            return;

        final boolean isConcreteControl = xformsControl != null;

        if (isConcreteControl && !xformsControl.hasLabel())
            throw new ValidationException("Missing label on xforms:trigger element.", xformsControl.getLocationData());

        final String labelValue = handlerContext.isGenerateTemplate() ? "$xforms-template-label$" : isConcreteControl ? (xformsControl.getLabel(pipelineContext) != null ? xformsControl.getLabel(pipelineContext) : "") : "";

        final QName appearance = getAppearance(elementAttributes);

        final StringBuffer classes = getInitialClasses(localname, elementAttributes, xformsControl);
        handleMIPClasses(classes, xformsControl);
        final AttributesImpl newAttributes = getAttributes(elementAttributes, classes.toString(), effectiveId);

        // Handle accessibility attributes
        handleAccessibilityAttributes(elementAttributes, newAttributes);

        // Add title attribute if not yet present and there is a hint
        if (newAttributes.getValue("title") == null) {
            final String hintValue = isConcreteControl ? xformsControl.getHint(pipelineContext) : null;
            if (hintValue != null)
                newAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, hintValue);
        }

        if (appearance != null && (XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(appearance) || XFormsConstants.XXFORMS_LINK_APPEARANCE_QNAME.equals(appearance))) {
            // Minimal or link appearance

            // TODO: probably needs f:url-norewrite="true"
            if (true) {
                newAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "");
            } else {
                // TODO: Complete experimenting with outputting href value
                String hrefValue = "";
                {
                    // Try to compute an href value right away if we detect just a nested xforms:load
                    // TODO: Need to tell the client not to handle clicks on hyperlink
                    // TODO: This should probably be done at the control level
                    // TODO: This duplicates code in XFormsLoadAction
                    if (xformsControl != null && xformsControl.getControlElement() != null) {
                        final Element controlElement = xformsControl.getControlElement();
                        final Element loadElement = controlElement.element(XFormsConstants.XFORMS_LOAD_QNAME);
                        if (loadElement != null && XFormsEvents.XFORMS_DOM_ACTIVATE.equals(loadElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME))) {
                            final String resource = loadElement.attributeValue("resource");
                            if (resource != null && resource.indexOf('{') == -1) {
                                // Static resource URL
                                hrefValue = resource;
                            } else if (resource != null) {
                                // Computed resource URL
                                final XFormsContextStack.BindingContext currentBindingContext = xformsControl.getBindingContext();
                                if (currentBindingContext != null && currentBindingContext.getSingleNode() != null) {

                                    final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(id);
                                    final XFormsContextStack contextStack = containingDocument.getXFormsControls().getContextStack();
                                    hrefValue = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, currentBindingContext.getSingleNode(),
                                            contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                            contextStack.getFunctionContext(), prefixToURIMap, xformsControl.getLocationData(), resource);
                                }
                            } else {
                                // Assume single-node binding
                                final XFormsContextStack.BindingContext curBindingContext = xformsControl.getBindingContext();
                                if (curBindingContext != null && curBindingContext.getSingleNode() != null) {
                                    hrefValue = XFormsInstance.getValueForNodeInfo(curBindingContext.getSingleNode());
                                }
                            }
                        }
                    }
                }

                newAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, hrefValue);
            }

            // xhtml:a
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "a");
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "a", spanQName, newAttributes);
            XFormsUtils.streamHTMLFragment(contentHandler, labelValue, isConcreteControl ? xformsControl.getLocationData() : null, xhtmlPrefix);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", spanQName);

        } else if (appearance != null && XFormsConstants.XXFORMS_IMAGE_APPEARANCE_QNAME.equals(appearance)) {
            // Image appearance

            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "image");
            newAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, labelValue);

            // Handle nested xxforms:img
            if (xxformsImgAttributes != null) { // it should not be null
                // Add @src attribute
                newAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, xxformsImgAttributes.getValue("src"));

                // Copy everything else except @src, @alt, and @id
                // NOTE: It is not 100% clear what attributes make sense for propagation.
                for (int i = 0; i < xxformsImgAttributes.getLength(); i++) {
                    final String attributeURI = xxformsImgAttributes.getURI(i);
                    final String attributeValue = xxformsImgAttributes.getValue(i);
                    final String attributeType = xxformsImgAttributes.getType(i);
                    final String attributeQName = xxformsImgAttributes.getQName(i);
                    final String attributeLocalname = xxformsImgAttributes.getLocalName(i);

                    if (!(attributeURI.equals("") && (attributeLocalname.equals("src") || attributeLocalname.equals("alt")) || attributeLocalname.equals("id")))
                        newAttributes.addAttribute(attributeURI, attributeLocalname, attributeQName, attributeType, attributeValue);
                }

            }

            // xhtml:input
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "input");
            handleReadOnlyAttribute(newAttributes, containingDocument, xformsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "input", spanQName, newAttributes);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "input", spanQName);

        } else {
            // Default appearance (button)

            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "button");

            // xhtml:button
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "button");
            handleReadOnlyAttribute(newAttributes, containingDocument, xformsControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "button", spanQName, newAttributes);
            XFormsUtils.streamHTMLFragment(contentHandler, labelValue, isConcreteControl ? xformsControl.getLocationData() : null, xhtmlPrefix);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "button", spanQName);
        }

        // xforms:help
        handleLabelHintHelpAlert(id, effectiveId, "help", xformsControl);
    }
}
