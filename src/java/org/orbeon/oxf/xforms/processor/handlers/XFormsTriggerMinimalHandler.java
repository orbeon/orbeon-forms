/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.processor.handlers;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xml.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Minimal (AKA "link") appearance.
 */
public class XFormsTriggerMinimalHandler extends XFormsTriggerHandler {

    protected static final String ENCLOSING_ELEMENT_NAME = "a";

    protected void handleControlStart(String uri, String localname, String qName, Attributes attributes, String staticId, String effectiveId, XFormsControl control) throws SAXException {

        final XFormsTriggerControl triggerControl = (XFormsTriggerControl) control;
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();

        final AttributesImpl containerAttributes = getContainerAttributes(uri, localname, attributes, effectiveId, triggerControl, true);

        // TODO: needs f:url-norewrite="true"?
        containerAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, "#");

        // xhtml:a
        final String xhtmlPrefix = handlerContext.findXHTMLPrefix();
        final String aQName = XMLUtils.buildQName(xhtmlPrefix, ENCLOSING_ELEMENT_NAME);
        xmlReceiver.startElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, aQName, containerAttributes);
        {
            final String labelValue = getTriggerLabel(triggerControl);
            final boolean mustOutputHTMLFragment = triggerControl != null && triggerControl.isHTMLLabel();
            outputLabelText(xmlReceiver, triggerControl, labelValue, xhtmlPrefix, mustOutputHTMLFragment);
        }
        xmlReceiver.endElement(XMLConstants.XHTML_NAMESPACE_URI, ENCLOSING_ELEMENT_NAME, aQName);
    }

    @Override
    protected void addCustomClasses(StringBuilder classes, XFormsControl control) {
        // Ask super first
        super.addCustomClasses(classes, control);

        if (handlerContext.isSpanHTMLLayout() && control != null) {
            final XFormsTriggerControl triggerControl = (XFormsTriggerControl) control;
            if (triggerControl.isReadonly()) {
                // Add a special class to facilitate styling of readonly links with IE 6
                classes.append(" xforms-trigger-readonly");
            }
        }
    }

    //    private void hrefExperiment() {
//        // TODO: Complete experimenting with outputting href value
//        String hrefValue = "#";
//        {
//            // Try to compute an href value right away if we detect just a nested xforms:load
//            // TODO: Need to tell the client not to handle clicks on hyperlink
//            // TODO: This should probably be done at the control level
//            // TODO: This duplicates code in XFormsLoadAction
//            if (triggerControl != null && triggerControl.getControlElement() != null) {
//                final Element controlElement = triggerControl.getControlElement();
//                final Element loadElement = controlElement.element(XFormsConstants.XFORMS_LOAD_QNAME);
//                if (loadElement != null && XFormsEvents.XFORMS_DOM_ACTIVATE.equals(loadElement.attributeValue(XFormsConstants.XML_EVENTS_EVENT_ATTRIBUTE_QNAME))) {
//                    final String resource = loadElement.attributeValue(XFormsConstants.RESOURCE_QNAME);
//                    if (resource != null && resource.indexOf('{') == -1) {
//                        // Static resource URL
//                        hrefValue = resource;
//                    } else if (resource != null) {
//                        // Computed resource URL
//                        final XFormsContextStack.BindingContext currentBindingContext = triggerControl.getBindingContext();
//                        if (currentBindingContext != null && currentBindingContext.getSingleNode() != null) {
//                            final Map prefixToURIMap = triggerControl.getNamespaceMappings();
//                            final XFormsContextStack contextStack = triggerControl.getContextStack();
//                            hrefValue = XFormsUtils.resolveAttributeValueTemplates(pipelineContext,
//                                    currentBindingContext.getNodeset(), currentBindingContext.getPosition(),
//                                    contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
//                                    contextStack.getFunctionContext(), prefixToURIMap, triggerControl.getLocationData(), resource);
//                        }
//                    } else {
//                        // Assume single-node binding
//                        final XFormsContextStack.BindingContext curBindingContext = triggerControl.getBindingContext();
//                        if (curBindingContext != null && curBindingContext.getSingleNode() != null) {
//                            hrefValue = XFormsInstance.getValueForNodeInfo(curBindingContext.getSingleNode());
//                        }
//                    }
//                }
//            }
//        }
//
//        newAttributes.addAttribute("", "href", "href", ContentHandlerHelper.CDATA, hrefValue);
//    }
}
