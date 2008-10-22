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

import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.ContentHandlerAdapter;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Map;

/**
 * Handle xforms:trigger.
 */
public class XFormsTriggerHandler extends XFormsControlLifecyleHandler {

    private boolean isModal;

    public XFormsTriggerHandler() {
        super(false);
    }

    protected void handleLabel(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // Label is handled differently
    }

    protected void handleHint(String staticId, String effectiveId, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // Hint is handled differently
    }

    protected void handleAlert(String staticId, String effectiveId, Attributes attributes, XFormsSingleNodeControl xformsControl, boolean isTemplate) throws SAXException {
        // Triggers don't need an alert
    }

    protected void prepareHandler(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) {
        final String modalAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "modal");
        this.isModal = "true".equals(modalAttribute);
    }

    protected void addCustomClasses(FastStringBuffer classes, XFormsSingleNodeControl xformsControl) {
        // Set modal class
        // TODO: xf:trigger/@xxforms:modal; do this in static state?
        if (isModal)
            classes.append(" xforms-trigger-appearance-modal");
    }

    protected QName getAppearance(Attributes controlAttributes) {
        // Override appearance in noscript mode
        final QName originalAppearance = super.getAppearance(controlAttributes);
        if (handlerContext.isNoScript() && originalAppearance != null && XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(originalAppearance))
            return XFormsConstants.XXFORMS_MINIMAL_APPEARANCE_QNAME;
        else
            return originalAppearance;
    }

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsTriggerControl triggerControl = (XFormsTriggerControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // xforms:trigger and xforms:submit

        if (isStaticReadonly(triggerControl))
            return;

        final boolean isConcreteControl = triggerControl != null;

        final boolean hasLabel = XFormsControl.hasLabel(containingDocument, getPrefixedId());
        if (isConcreteControl && !hasLabel)
            throw new ValidationException("Missing label on xforms:trigger element.", triggerControl.getLocationData());

        final String labelValue = handlerContext.isTemplate() ? "$xforms-template-label$" : isConcreteControl ? (triggerControl.getLabel(pipelineContext) != null ? triggerControl.getLabel(pipelineContext) : "") : "";

        final AttributesImpl newAttributes;
        if (handlerContext.isNewXHTMLLayout()) {
            reusableAttributes.clear();
            newAttributes = reusableAttributes;
        } else {
            final FastStringBuffer classes = getInitialClasses(uri, localname, attributes, triggerControl);
            handleMIPClasses(classes, getPrefixedId(), triggerControl);
            containingDocument.getStaticState().appendClasses(classes, getPrefixedId());
            addCustomClasses(classes, triggerControl);
            newAttributes = getAttributes(attributes, classes.toString(), effectiveId);
        }

        // Handle accessibility attributes
        handleAccessibilityAttributes(attributes, newAttributes);

        // Add title attribute if not yet present and there is a hint
        if (newAttributes.getValue("title") == null) {
            final String hintValue = isConcreteControl ? triggerControl.getHint(pipelineContext) : null;
            if (hintValue != null)
                newAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, hintValue);
        }

        final QName appearance = getAppearance(attributes);
        if (appearance != null && (XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(appearance) || XFormsConstants.XXFORMS_LINK_APPEARANCE_QNAME.equals(appearance))) {
            // Minimal (AKA "link") appearance

            // TODO: probably needs f:url-norewrite="true"
            if (true) {
                newAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#");
            } else {
                // TODO: Complete experimenting with outputting href value
                String hrefValue = "#";
                {
                    // Try to compute an href value right away if we detect just a nested xforms:load
                    // TODO: Need to tell the client not to handle clicks on hyperlink
                    // TODO: This should probably be done at the control level
                    // TODO: This duplicates code in XFormsLoadAction
                    if (triggerControl != null && triggerControl.getControlElement() != null) {
                        final Element controlElement = triggerControl.getControlElement();
                        final Element loadElement = controlElement.element(XFormsConstants.XFORMS_LOAD_QNAME);
                        if (loadElement != null && XFormsEvents.XFORMS_DOM_ACTIVATE.equals(loadElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME))) {
                            final String resource = loadElement.attributeValue("resource");
                            if (resource != null && resource.indexOf('{') == -1) {
                                // Static resource URL
                                hrefValue = resource;
                            } else if (resource != null) {
                                // Computed resource URL
                                final XFormsContextStack.BindingContext currentBindingContext = triggerControl.getBindingContext();
                                if (currentBindingContext != null && currentBindingContext.getSingleNode() != null) {
                                    final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(controlElement);
                                    final XFormsContextStack contextStack = containingDocument.getControls().getContextStack();
                                    hrefValue = XFormsUtils.resolveAttributeValueTemplates(pipelineContext,
                                            currentBindingContext.getNodeset(), currentBindingContext.getPosition(),
                                            contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                            contextStack.getFunctionContext(), prefixToURIMap, triggerControl.getLocationData(), resource);
                                }
                            } else {
                                // Assume single-node binding
                                final XFormsContextStack.BindingContext curBindingContext = triggerControl.getBindingContext();
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

            final boolean mustOutputHTMLFragment = xformsControl != null && xformsControl.isHTMLLabel(pipelineContext);
            outputLabelText(contentHandler, xformsControl, labelValue, xhtmlPrefix, mustOutputHTMLFragment);

            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", spanQName);

        } else if (appearance != null && XFormsConstants.XXFORMS_IMAGE_APPEARANCE_QNAME.equals(appearance)) {
            // No longer supported image appearance
            throw new ValidationException("\"xxforms:image\" appearance is no longer supported. Use the \"minimal\" appearance instead.", handlerContext.getLocationData());
        } else {
            // Default full appearance (button)
            // This can also be the "pseudo-minimal" appearance for noscript mode

            final boolean mustOutputHTMLFragment = xformsControl != null && xformsControl.isHTMLLabel(pipelineContext);
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();

            final String elementName;
            if (handlerContext.isNoScript()) {
                // We need a name to detect activation
                newAttributes.addAttribute("", "name", "name", ContentHandlerHelper.CDATA, effectiveId);

                final String inputType;
                if (handlerContext.isRenderingEngineIE6OrEarlier()) {

                    // IE 6 does not support discriminating between multiple buttons: it sends them all, so we use
                    // "input" instead. The code below tries to output <input type="submit"> or <input type="image">
                    // depending on the content of the label. This has limitations: we can only handle text or a single
                    // image.

                    final String inputLabelValue;
                    if (mustOutputHTMLFragment) {
                        // Only output character content within input
                        XFormsServer.logger.warn("IE 6 does not support <button> elements properly. Only text within HTML content will appear garbled. Control id: " + effectiveId);

                        final FastStringBuffer sb = new FastStringBuffer(labelValue.length());

                        final String[] imageInfo = new String[3];// who needs classes? ;-)
                        XFormsUtils.streamHTMLFragment(new ContentHandlerAdapter() {
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
                        }, labelValue, xformsControl != null ? xformsControl.getLocationData() : null, xhtmlPrefix);

                        final String sbString = sb.toString();
                        if (imageInfo[0] != null && sbString.trim().equals("")) {
                            // There is an image and no text, output image
                            inputType = "image";
                            inputLabelValue = "";
                            newAttributes.addAttribute("", "src", "src", ContentHandlerHelper.CDATA, imageInfo[0]);
                            if (imageInfo[1] != null)
                                newAttributes.addAttribute("", "alt", "alt", ContentHandlerHelper.CDATA, imageInfo[1]);
                            if (imageInfo[2] != null)
                                newAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, imageInfo[2]);
                        } else {
                            // Output text
                            inputType = "submit";
                            inputLabelValue = sbString;
                        }
                    } else {
                        inputType = "submit";
                        inputLabelValue = labelValue;
                    }

                    newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, inputLabelValue);
                    elementName = "input";
                } else {
                    // We need a value so we can detect an activated button with IE 7
                    // NOTE: IE 6/7 sends the <button> content as value instead of the value attribute!
                    newAttributes.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, "activate");
                    elementName = "button";
                    inputType = "submit";
                }

                // In JS-free mode, all buttons are submit inputs or image inputs
                newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, inputType);
            } else {
                // Just a button without action
                newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "button");
                elementName = "button";
            }

            // xhtml:button or xhtml:input
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, elementName);
            handleReadOnlyAttribute(newAttributes, containingDocument, triggerControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, spanQName, newAttributes);

            if ("button".equals(elementName)) {
                // Output content of <button> element
                outputLabelText(contentHandler, xformsControl, labelValue, xhtmlPrefix, mustOutputHTMLFragment);
            }

            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, elementName, spanQName);
        }
    }
}
