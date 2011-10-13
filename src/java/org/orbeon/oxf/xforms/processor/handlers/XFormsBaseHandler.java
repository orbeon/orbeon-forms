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

import java.util.HashMap;
import java.util.Map;

import org.dom4j.QName;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModelBinds;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.ElementHandler;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Base XForms handler used as base class in both the xml and xhtml handlers.
 *
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
    
    private final boolean repeating;
    private final boolean forwarding;
    
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
    public boolean isHTMLDisabled(XFormsControl control) {
        return
//                control == null || !control.isRelevant() ||
//                !handlerContext.getCaseVisibility() || // no longer do this as it is better handled with CSS
                (control instanceof XFormsSingleNodeControl) && ((XFormsSingleNodeControl) control).isReadonly() && !XFormsProperties.isStaticReadonlyAppearance(containingDocument);
    }

    protected boolean isEmpty(XFormsControl control) {
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

    protected static String getLHHACId(XFormsContainingDocument containingDocument, String controlEffectiveId, String suffix) {
        // E.g. foo$bar.1-2-3 -> foo$bar$$alert.1-2-3
        return XFormsUtils.namespaceId(containingDocument, XFormsUtils.appendToEffectiveId(controlEffectiveId, XFormsConstants.LHHAC_SEPARATOR + suffix));
    }

    protected QName getAppearance(Attributes controlAttributes) {
        return handlerContext.getController().getAttributeQNameValue(controlAttributes.getValue(XFormsConstants.APPEARANCE_QNAME.getName()));
    }
    
    protected Attributes handleAVTsAndIDs(Attributes attributes, String[] refIdAttributeNames) {
		final String staticId = handlerContext.getId(attributes);
        final String prefixedId = handlerContext.getIdPrefix() + staticId;

        if (staticId != null) {
            final boolean hasAVT = containingDocument.getStaticOps().hasAttributeControl(prefixedId);
            final String effectiveId = handlerContext.getEffectiveId(attributes);
            boolean found = false;
            if (hasAVT) {
                // This element has at least one AVT so process its attributes

                final int attributesCount = attributes.getLength();
                for (int i = 0; i < attributesCount; i++) {
                    final String attributeValue = attributes.getValue(i);
                    if (XFormsUtils.maybeAVT(attributeValue)) {
                        // This is an AVT most likely
                        found = true;

                        final String attributeLocalName = attributes.getLocalName(i);
                        final String attributeQName = attributes.getQName(i);// use qualified name so we match on "xml:lang"

                        // Control analysis
                        final AttributeControl controlAnalysis = containingDocument.getStaticOps().getAttributeControl(prefixedId, attributeQName);

                        // Get static id of attribute control associated with this particular attribute
                        final String attributeControlStaticId = controlAnalysis.element().attributeValue(XFormsConstants.ID_QNAME);

                        // Find concrete control if possible
                        final XXFormsAttributeControl attributeControl;
                        if (handlerContext.isTemplate()) {
                            attributeControl = null;
                        } else if (attributeControlStaticId != null) {
                            final String attributeControlEffectiveId = XFormsUtils.getRelatedEffectiveId(effectiveId, attributeControlStaticId);
                            attributeControl = (XXFormsAttributeControl) containingDocument.getControls().getObjectByEffectiveId(attributeControlEffectiveId);
                        } else {
                            // This should not happen
                            attributeControl = null;
                        }

                        // Determine attribute value
                        // NOTE: This also handles dummy images for the xhtml:img/@src case
                        final String effectiveAttributeValue = XXFormsAttributeControl.getExternalValue(attributeControl, controlAnalysis);

                        // Set the value of the attribute
                        attributes = XMLUtils.addOrReplaceAttribute(attributes, attributes.getURI(i),
                                XMLUtils.prefixFromQName(attributeQName), attributeLocalName, effectiveAttributeValue);
                    }
                }

                if (found) {
                    // Update the value of the id attribute
                    attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "id", XFormsUtils.namespaceId(containingDocument, effectiveId));
                }
            }

            if (!found) {
                // Id was not replaced as part of AVT processing

                // Update the value of the id attribute
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", "id", XFormsUtils.namespaceId(containingDocument, effectiveId));
            }
        }

        // Check @for attribute
        for(String refIdAttributeName : refIdAttributeNames)
        {
            final String forAttribute = attributes.getValue(refIdAttributeName);
            if (forAttribute != null) {
                attributes = XMLUtils.addOrReplaceAttribute(attributes, "", "", refIdAttributeName, handlerContext.getIdPrefix() + forAttribute + handlerContext.getIdPostfix());
            }
        }
		return attributes;
	}
}
