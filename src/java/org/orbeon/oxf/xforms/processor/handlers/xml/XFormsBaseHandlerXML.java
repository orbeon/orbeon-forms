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
package org.orbeon.oxf.xforms.processor.handlers.xml;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.processor.handlers.XFormsBaseHandler;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Base class for all XHTML and XForms element handlers.
 */
public abstract class XFormsBaseHandlerXML extends XFormsBaseHandler {

    protected XFormsBaseHandlerXML(boolean repeating, boolean forwarding) {
        super(repeating, forwarding);
    }

    public void handleMIPAttributes(AttributesImpl newAttributes, String controlPrefixedId, XFormsControl control) {

        // Output MIP classes only having a binding
        final boolean hasBinding = containingDocument.getStaticOps().hasBinding(controlPrefixedId);
        if (hasBinding && control != null)
            // Output standard MIP classes
            control.writeMIPsAsAttributes(newAttributes);
    }
    
    public void handleValueAttribute(AttributesImpl newAttributes, String controlPrefixedId, XFormsControl control) {

        if (containingDocument.getStaticOps().isValueControl(controlPrefixedId)) {
            if (control instanceof XFormsValueControl)
            {
            	XFormsValueControl valueControl = (XFormsValueControl) control;
            	if (valueControl.getValue() != null) {
            		newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "value", XFormsConstants.XXFORMS_PREFIX + ":value", ContentHandlerHelper.CDATA, ((XFormsValueControl) control).getValue());
            	}
            	if (valueControl.getExternalValue() != null) {
            		newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "external-value", XFormsConstants.XXFORMS_PREFIX + ":external-value", ContentHandlerHelper.CDATA, ((XFormsValueControl) control).getExternalValue());
            	}
            }
        }
    }

    // TODO: implement label hint and alert for XML content
    protected void handleLabelHintHelpAlert(String controlEffectiveId, String forEffectiveId, LHHAC lhhaType, XFormsControl control, boolean isTemplate) throws SAXException {

        // NOTE: We used to not handle alerts and help in read-only mode. We now prefer to controls this with CSS.
        final boolean isHint = lhhaType == LHHAC.HINT;
        final boolean isAlert = lhhaType == LHHAC.ALERT;
        final boolean isLabel = lhhaType == LHHAC.LABEL;
        final boolean isHelp = lhhaType == LHHAC.HELP;

        final String labelHintHelpAlertValue;
        final boolean mustOutputHTMLFragment;
        if (control != null) {
            // Get actual value from control
            if (isLabel) {
                labelHintHelpAlertValue = control.getLabel();
                mustOutputHTMLFragment = control.isHTMLLabel();
            } else if (isHelp) {
                // NOTE: Special case here where we get the escaped help to facilitate work below. Help is a special
                // case because it is stored as escaped HTML within a <label> element.
                labelHintHelpAlertValue = control.getEscapedHelp();
                mustOutputHTMLFragment = false;
            } else if (isHint) {
                labelHintHelpAlertValue = control.getHint();
                mustOutputHTMLFragment = control.isHTMLHint();
            } else if (isAlert) {
                labelHintHelpAlertValue = control.getAlert();
                mustOutputHTMLFragment = control.isHTMLAlert();
            } else {
                throw new IllegalStateException("Illegal type requested");
            }
        } else {
            // Placeholder
            labelHintHelpAlertValue = null;
            mustOutputHTMLFragment = false;
        }
        
        final String elementName;
        final AttributesImpl labelHintHelpAlertAttributes;
        {
            // Statically obtain attributes information
        	final StaticStateGlobalOps globalOps = containingDocument.getStaticOps();
            final LHHAAnalysis lhhaAnalysis;
            final String forPrefixedId = XFormsUtils.getPrefixedId(controlEffectiveId);
            if (isLabel) {
                elementName = handlerContext.getLabelElementName();
                lhhaAnalysis = globalOps.getLabel(forPrefixedId);
            } else if (isHelp) {
                elementName = handlerContext.getHelpElementName();
                lhhaAnalysis = globalOps.getHelp(forPrefixedId);
            } else if (isHint) {
                elementName = handlerContext.getHintElementName();
                lhhaAnalysis = globalOps.getHint(forPrefixedId);
            } else if (isAlert) {
                elementName = handlerContext.getAlertElementName();
                lhhaAnalysis = globalOps.getAlert(forPrefixedId);
            } else {
                throw new IllegalStateException("Illegal type requested");
            }

            labelHintHelpAlertAttributes = (lhhaAnalysis != null) ? (AttributesImpl)XMLUtils.getSAXAttributes(lhhaAnalysis.element()) : null;
            
            if(labelHintHelpAlertAttributes != null) {
            	updateID(labelHintHelpAlertAttributes, controlEffectiveId, lhhaType);
            }
        }
        
        final XMLReceiver xmlReceiver = handlerContext.getController().getOutput();
        xmlReceiver.startElement(XFormsConstants.XFORMS_NAMESPACE_URI, elementName, XFormsConstants.XFORMS_PREFIX + ":" + elementName, labelHintHelpAlertAttributes);
        
        if (labelHintHelpAlertValue != null && !labelHintHelpAlertValue.equals("")) {
            if (mustOutputHTMLFragment) {
            	XMLUtils.parseDocumentFragment(labelHintHelpAlertValue, xmlReceiver);
            }
            else
                xmlReceiver.characters(labelHintHelpAlertValue.toCharArray(), 0, labelHintHelpAlertValue.length());
        }
        
        xmlReceiver.endElement(XFormsConstants.XFORMS_NAMESPACE_URI, elementName, XFormsConstants.XFORMS_PREFIX + ":" + elementName);
    }

    void updateID(AttributesImpl attributes, String effectiveId, LHHAC lhhac) {
    	attributes.setAttribute(attributes.getIndex("id"),"", "id", "id", ContentHandlerHelper.CDATA, getLHHACId(containingDocument, effectiveId, LHHAC_CODES.get(lhhac)));
    }
}
