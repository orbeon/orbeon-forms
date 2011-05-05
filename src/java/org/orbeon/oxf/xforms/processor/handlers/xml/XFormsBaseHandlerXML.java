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

import java.util.Map;

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
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
        final boolean hasBinding = containingDocument.getStaticOps().hasNodeBinding(controlPrefixedId);
        if (hasBinding) {
            if (control != null) {
                // Output standard MIP classes
            	newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "relevant", XFormsConstants.XXFORMS_PREFIX + ":relevant", ContentHandlerHelper.CDATA, control.isRelevant()?"true":"false");
            	
                if (control instanceof XFormsSingleNodeControl) {
                    // TODO: inherit from this method instead rather than using instanceof
                    final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                    
                    newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "valid", XFormsConstants.XXFORMS_PREFIX + ":valid", ContentHandlerHelper.CDATA, singleNodeControl.isValid()?"true":"false");
                    newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "read-only", XFormsConstants.XXFORMS_PREFIX + ":read-only", ContentHandlerHelper.CDATA, singleNodeControl.isReadonly()?"true":"false");
                    newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "static-read-only", XFormsConstants.XXFORMS_PREFIX + ":static-read-only", ContentHandlerHelper.CDATA, isStaticReadonly(control)?"true":"false");
                    boolean required = singleNodeControl.isRequired();
					newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "required", XFormsConstants.XXFORMS_PREFIX + ":required", ContentHandlerHelper.CDATA, required?"true":"false");
                    if (required)
                    {
                    	newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "required-and-empty", XFormsConstants.XXFORMS_PREFIX + ":required-and-empty", ContentHandlerHelper.CDATA, isEmpty(control)?"true":"false");
                    }

                    // Output custom MIPs classes
                    final Map<String, String> customMIPs = singleNodeControl.getCustomMIPs();
                    if (customMIPs != null) {
	                    for(Map.Entry<String, String> customMip : customMIPs.entrySet()) {
	                    	final String customMipName = customMip.getKey();
							newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, customMipName, XFormsConstants.XXFORMS_PREFIX + ":" + customMipName, ContentHandlerHelper.CDATA, customMip.getValue());
	                    }
                    }
                    
                    // Output type class
                    final String typeName = singleNodeControl.getBuiltinTypeName();
                    if (typeName != null) {
                        // Control is bound to built-in schema type
                    	newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "xforms-type", XFormsConstants.XXFORMS_PREFIX + ":xforms-type", ContentHandlerHelper.CDATA, typeName);
                    } else {
                        // Output custom type class
                       final String customTypeName = singleNodeControl.getTypeLocalName();
                       if (customTypeName != null) {
                           // Control is bound to a custom schema type
                    	   newAttributes.addAttribute(XFormsConstants.XXFORMS_NAMESPACE_URI, "xforms-type-custom", XFormsConstants.XXFORMS_PREFIX + ":xforms-type-custom", ContentHandlerHelper.CDATA, customTypeName);
                       }
                    }
                }
            }
        }
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
    protected void handleLabelHintHelpAlert(String controlEffectiveId, String forEffectiveId, LHHAC lhhaType, XFormsControl control, boolean isTemplate, boolean addIds) throws SAXException {

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
