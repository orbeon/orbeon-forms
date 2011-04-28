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

import java.util.HashMap;
import java.util.Map;

import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.xforms.StaticStateGlobalOps;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModelBinds;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Base class for all XHTML and XForms element handlers.
 */
public abstract class XFormsBaseHandler extends ElementHandler {

    public enum LHHAC {
        LABEL, HELP, HINT, ALERT, CONTROL
    }

    protected static final Map<LHHAC, String> LHHAC_CODES = new HashMap<LHHAC, String>();
    static {
        LHHAC_CODES.put(LHHAC.LABEL, "l");
        LHHAC_CODES.put(LHHAC.HELP, "p");
        LHHAC_CODES.put(LHHAC.HINT, "t");
        LHHAC_CODES.put(LHHAC.ALERT, "a");
        LHHAC_CODES.put(LHHAC.CONTROL, "c");
        // "i" is also used for help image
    }

    private boolean repeating;
    private boolean forwarding;

    protected HandlerContext handlerContext;

    protected XFormsContainingDocument containingDocument;

    protected AttributesImpl reusableAttributes = new AttributesImpl();

    protected XFormsBaseHandler(boolean repeating, boolean forwarding) {
        this.repeating = repeating;
        this.forwarding = forwarding;
    }

    public void setContext(Object context) {
        this.handlerContext = (HandlerContext) context;

        this.containingDocument = handlerContext.getContainingDocument();

        super.setContext(context);
    }

    public boolean isRepeating() {
        return repeating;
    }

    public boolean isForwarding() {
        return forwarding;
    }

    /**
     * Whether the control is disabled in the resulting HTML. Occurs when:
     * 
     * o control is readonly but not static readonly
     *
     * @param control   control to check or null if no concrete control available
     * @return          whether the control is to be marked as disabled
     */
    protected boolean isHTMLDisabled(XFormsControl control) {
        return
//                control == null || !control.isRelevant() ||
//                !handlerContext.getCaseVisibility() || // no longer do this as it is better handled with CSS
                (control instanceof XFormsSingleNodeControl) && ((XFormsSingleNodeControl) control).isReadonly() && !XFormsProperties.isStaticReadonlyAppearance(containingDocument);
    }

    protected static void outputDisabledAttribute(AttributesImpl newAttributes) {
        // @disabled="disabled"
        // HTML 4: @disabled supported on: input, button, select, optgroup, option, and textarea.
        newAttributes.addAttribute("", "disabled", "disabled", ContentHandlerHelper.CDATA, "disabled");
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

    private boolean isEmpty(XFormsControl control) {
        return control instanceof XFormsValueControl && XFormsModelBinds.isEmptyValue(((XFormsValueControl) control).getValue());
    }

    protected static void handleAccessibilityAttributes(Attributes srcAttributes, AttributesImpl destAttributes) {
        // Handle "tabindex"
        {
            // This is the standard XForms attribute
            String value = srcAttributes.getValue("navindex");
            if (value == null) {
                // Try the the XHTML attribute
                value = srcAttributes.getValue("tabindex");
            }
//            if (value == null) {
//                // Use automatically generated index
//                value = Integer.toString(handlerContext.nextTabIndex());
//            }

            if (value != null)
                destAttributes.addAttribute("", "tabindex", "tabindex", ContentHandlerHelper.CDATA, value);
        }
        // Handle "accesskey"
        {
            final String value = srcAttributes.getValue("accesskey");
            if (value != null)
                destAttributes.addAttribute("", "accesskey", "accesskey", ContentHandlerHelper.CDATA, value);
        }
    }

    protected AttributesImpl getAttributes(Attributes elementAttributes, String classes, String effectiveId) {
        return getAttributes(containingDocument, reusableAttributes, elementAttributes, classes, effectiveId);
    }

    protected static AttributesImpl getAttributes(XFormsContainingDocument containingDocument, AttributesImpl reusableAttributes, Attributes elementAttributes, String classes, String effectiveId) {
        reusableAttributes.clear();

        // Copy "id"
        if (effectiveId != null) {
            reusableAttributes.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, effectiveId));
        }
        // Create "class" attribute if necessary
        {
            if (classes != null && classes.length() > 0) {
                reusableAttributes.addAttribute("", "class", "class", ContentHandlerHelper.CDATA, classes);
            }
        }
        // Copy attributes in the xhtml namespace to no namespace
        for (int i = 0; i < elementAttributes.getLength(); i++) {
            if (XMLConstants.XHTML_NAMESPACE_URI.equals(elementAttributes.getURI(i))) {
                final String name = elementAttributes.getLocalName(i);
                if (!"class".equals(name)) {
                    reusableAttributes.addAttribute("", name, name, ContentHandlerHelper.CDATA, elementAttributes.getValue(i));
                }
            }
        }

        return reusableAttributes;
    }



    protected boolean isStaticReadonly(XFormsControl control) {
        return control != null && control.isStaticReadonly();
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


    protected static String getLHHACId(XFormsContainingDocument containingDocument, String controlEffectiveId, String suffix) {
        // E.g. foo$bar.1-2-3 -> foo$bar$$alert.1-2-3
        return XFormsUtils.namespaceId(containingDocument, XFormsUtils.appendToEffectiveId(controlEffectiveId, XFormsConstants.LHHAC_SEPARATOR + suffix));
    }


    protected QName getAppearance(Attributes controlAttributes) {
        return handlerContext.getController().getAttributeQNameValue(controlAttributes.getValue(XFormsConstants.APPEARANCE_QNAME.getName()));
    }
    
    void updateID(AttributesImpl attributes, String effectiveId, LHHAC lhhac) {
    	attributes.setAttribute(attributes.getIndex("id"),"", "id", "id", ContentHandlerHelper.CDATA, getLHHACId(containingDocument, effectiveId, LHHAC_CODES.get(lhhac)));
    }
}
