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
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
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
public class XFormsTriggerHandler extends XFormsCoreControlHandler {

    public XFormsTriggerHandler() {
        super(false);
    }

    protected boolean isMustOutputStandardLabel() {
        // Label is handled differently
        return false;
    }

    protected boolean isMustOutputStandardHint() {
        // Hint is handled differently
        return false;
    }

    protected boolean isMustOutputStandardAlert(Attributes attributes) {
        // Triggers don't need an alert
        return false;
    }

    protected void handleControl(String uri, String localname, String qName, Attributes attributes, String id, String effectiveId, XFormsSingleNodeControl xformsControl) throws SAXException {

        final XFormsTriggerControl triggerControl = (XFormsTriggerControl) xformsControl;
        final ContentHandler contentHandler = handlerContext.getController().getOutput();

        // xforms:trigger and xforms:submit

        if (isStaticReadonly(triggerControl))
            return;

        final boolean isConcreteControl = triggerControl != null;

        if (isConcreteControl && !triggerControl.hasLabel())
            throw new ValidationException("Missing label on xforms:trigger element.", triggerControl.getLocationData());

        final String labelValue = handlerContext.isTemplate() ? "$xforms-template-label$" : isConcreteControl ? (triggerControl.getLabel(pipelineContext) != null ? triggerControl.getLabel(pipelineContext) : "") : "";

        final QName appearance = getAppearance(attributes);

        final FastStringBuffer classes = getInitialClasses(localname, attributes, triggerControl);
        handleMIPClasses(classes, id, triggerControl);
        containingDocument.getStaticState().appendClasses(classes, id);

        {
            // Set modal class
            final String modalAttribute = attributes.getValue(XFormsConstants.XXFORMS_NAMESPACE_URI, "modal");
            // Code below implements a different default for xforms:trigger and xforms:submit, but it's not clear we want that
//            final boolean isSubmit = localname.equals("submit");
//            if ((!isSubmit && "true".equals(modalAttribute)) || (isSubmit && !"false".equals(modalAttribute)))
            if ("true".equals(modalAttribute))
                classes.append(" xforms-trigger-appearance-modal");
        }

        final AttributesImpl newAttributes = getAttributes(attributes, classes.toString(), effectiveId);

        // Handle accessibility attributes
        handleAccessibilityAttributes(attributes, newAttributes);

        // Add title attribute if not yet present and there is a hint
        if (newAttributes.getValue("title") == null) {
            final String hintValue = isConcreteControl ? triggerControl.getHint(pipelineContext) : null;
            if (hintValue != null)
                newAttributes.addAttribute("", "title", "title", ContentHandlerHelper.CDATA, hintValue);
        }

        if (appearance != null && (XFormsConstants.XFORMS_MINIMAL_APPEARANCE_QNAME.equals(appearance) || XFormsConstants.XXFORMS_LINK_APPEARANCE_QNAME.equals(appearance))) {
            // Minimal or link appearance

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
                                    final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(id);
                                    final XFormsContextStack contextStack = containingDocument.getXFormsControls().getContextStack();
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
            XFormsUtils.streamHTMLFragment(contentHandler, labelValue, isConcreteControl ? triggerControl.getLocationData() : null, xhtmlPrefix);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "a", spanQName);

        } else if (appearance != null && XFormsConstants.XXFORMS_IMAGE_APPEARANCE_QNAME.equals(appearance)) {
            // No longer supported image appearance
            throw new ValidationException("\"xxforms:image\" appearance is no longer supported. Use the \"minimal\" appearance instead.", handlerContext.getLocationData());
        } else {
            // Default appearance (button)

            newAttributes.addAttribute("", "type", "type", ContentHandlerHelper.CDATA, "button");

            // xhtml:button
            final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
            final String spanQName = XMLUtils.buildQName(xhtmlPrefix, "button");
            handleReadOnlyAttribute(newAttributes, containingDocument, triggerControl);
            contentHandler.startElement(XMLConstants.XHTML_NAMESPACE_URI, "button", spanQName, newAttributes);
            XFormsUtils.streamHTMLFragment(contentHandler, labelValue, isConcreteControl ? triggerControl.getLocationData() : null, xhtmlPrefix);
            contentHandler.endElement(XMLConstants.XHTML_NAMESPACE_URI, "button", spanQName);
        }
    }
}
