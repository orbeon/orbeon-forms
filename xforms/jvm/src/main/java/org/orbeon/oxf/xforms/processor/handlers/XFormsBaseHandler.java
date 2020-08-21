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

import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl;
import org.orbeon.oxf.xml.*;
import org.orbeon.xforms.XFormsId;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Base XForms handler used as base class in both the xml and xhtml handlers.
 */
public abstract class XFormsBaseHandler extends ElementHandler {

    private final boolean repeating;
    private final boolean forwarding;

    protected HandlerContext xformsHandlerContext;
    public HandlerContext getXFormsHandlerContext() {
        return xformsHandlerContext;
    }

    protected ElementAnalysis elementAnalysis;
    protected XFormsContainingDocument containingDocument;
    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    };

    protected AttributesImpl reusableAttributes = new AttributesImpl();

    protected XFormsBaseHandler(String uri, String localname, String qName, Attributes attributes, Object matched, Object handlerContext, boolean repeating, boolean forwarding) {
        super(uri, localname, qName, attributes, matched, handlerContext);

        this.repeating  = repeating;
        this.forwarding = forwarding;

        // All elements corresponding to XForms controls must cause elementAnalysis to be available
        if (matched instanceof ElementAnalysis)
            this.elementAnalysis = (ElementAnalysis) matched;

        this.xformsHandlerContext = (HandlerContext) handlerContext;
        this.containingDocument = xformsHandlerContext.getContainingDocument();
    }

    public boolean isRepeating() {
        return repeating;
    }
    public boolean isForwarding() {
        return forwarding;
    }

    public boolean isNonRelevant(XFormsControl control) {
        return control == null || ! control.isRelevant();
    }

    public static void handleAccessibilityAttributes(Attributes srcAttributes, AttributesImpl destAttributes) {
        // Handle "tabindex"
        {
            // This is the standard XForms attribute
            String value = srcAttributes.getValue("navindex");
            if (value == null) {
                // Try the the XHTML attribute
                value = srcAttributes.getValue("tabindex");
            }

            if (value != null)
                destAttributes.addAttribute("", "tabindex", "tabindex", XMLReceiverHelper.CDATA, value);
        }
        // Handle "accesskey"
        {
            final String value = srcAttributes.getValue("accesskey");
            if (value != null)
                destAttributes.addAttribute("", "accesskey", "accesskey", XMLReceiverHelper.CDATA, value);
        }
        // Handle "role"
        {
            final String value = srcAttributes.getValue("role");
            if (value != null)
                destAttributes.addAttribute("", "role", "role", XMLReceiverHelper.CDATA, value);
        }
    }

    public static void handleAriaAttributes(boolean required, boolean valid, AttributesImpl destAttributes) {
        if (required)
            destAttributes.addAttribute("", "aria-required", "aria-required", XMLReceiverHelper.CDATA, "true");
        if (! valid)
            destAttributes.addAttribute("", "aria-invalid", "aria-invalid", XMLReceiverHelper.CDATA, "true");
    }

    protected AttributesImpl getIdClassXHTMLAttributes(Attributes elementAttributes, String classes, String effectiveId) {
        return getIdClassXHTMLAttributes(containingDocument, reusableAttributes, elementAttributes, classes, effectiveId);
    }

    public static AttributesImpl getIdClassXHTMLAttributes(XFormsContainingDocument containingDocument, AttributesImpl reusableAttributes, Attributes elementAttributes, String classes, String effectiveId) {
        reusableAttributes.clear();

        // Copy "id"
        if (effectiveId != null) {
            reusableAttributes.addAttribute("", "id", "id", XMLReceiverHelper.CDATA, XFormsUtils.namespaceId(containingDocument, effectiveId));
        }
        // Create "class" attribute if necessary
        {
            if (classes != null && classes.length() > 0) {
                reusableAttributes.addAttribute("", "class", "class", XMLReceiverHelper.CDATA, classes);
            }
        }
        // Copy attributes in the xhtml namespace to no namespace
        for (int i = 0; i < elementAttributes.getLength(); i++) {
            if (XMLConstants.XHTML_NAMESPACE_URI().equals(elementAttributes.getURI(i))) {
                final String name = elementAttributes.getLocalName(i);
                if (!"class".equals(name)) {
                    reusableAttributes.addAttribute("", name, name, XMLReceiverHelper.CDATA, elementAttributes.getValue(i));
                }
            }
        }

        return reusableAttributes;
    }

    public static boolean isStaticReadonly(XFormsControl control) {
        return control != null && control.isStaticReadonly();
    }

    public static String getLHHACId(XFormsContainingDocument containingDocument, String controlEffectiveId, String suffix) {
        // E.g. foo$bar.1-2-3 -> foo$bar$$alert.1-2-3
        return XFormsUtils.namespaceId(containingDocument, XFormsId.appendToEffectiveId(controlEffectiveId, XFormsConstants.LHHAC_SEPARATOR() + suffix));
    }

    protected static Attributes handleAVTsAndIDs(Attributes attributes, String[] refIdAttributeNames, HandlerContext xformsHandlerContext) {
        final String prefixedId = xformsHandlerContext.getPrefixedId(attributes);

        if (prefixedId != null) {

            final XFormsContainingDocument containingDocument = xformsHandlerContext.getContainingDocument();

            final boolean hasAVT = xformsHandlerContext.getPartAnalysis().hasAttributeControl(prefixedId);
            final String effectiveId = xformsHandlerContext.getEffectiveId(attributes);
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
                        final AttributeControl controlAnalysis = xformsHandlerContext.getPartAnalysis().getAttributeControl(prefixedId, attributeQName);

                        // Get static id of attribute control associated with this particular attribute
                        final String attributeControlStaticId = controlAnalysis.staticId();

                        // Find concrete control
                        final String attributeControlEffectiveId = XFormsId.getRelatedEffectiveId(effectiveId, attributeControlStaticId);
                        final XXFormsAttributeControl attributeControl =
                            (XXFormsAttributeControl) containingDocument.getControlByEffectiveId(attributeControlEffectiveId);

                        // Determine attribute value
                        // NOTE: This also handles dummy images for the xhtml:img/@src case
                        final String effectiveAttributeValue = XXFormsAttributeControl.getExternalValueHandleSrc(attributeControl, controlAnalysis);

                        // Set the value of the attribute
                        attributes = SAXUtils.addOrReplaceAttribute(attributes, attributes.getURI(i),
                                XMLUtils.prefixFromQName(attributeQName), attributeLocalName, effectiveAttributeValue);
                    }
                }

                if (found) {
                    // Update the value of the id attribute
                    attributes = SAXUtils.addOrReplaceAttribute(attributes, "", "", "id", XFormsUtils.namespaceId(containingDocument, effectiveId));
                }
            }

            if (!found) {
                // Id was not replaced as part of AVT processing

                // Update the value of the id attribute
                attributes = SAXUtils.addOrReplaceAttribute(attributes, "", "", "id", XFormsUtils.namespaceId(containingDocument, effectiveId));
            }
        }

        // Check @for or other attribute
        for (String refIdAttributeName : refIdAttributeNames)
        {
            final String forAttribute = attributes.getValue(refIdAttributeName);
            if (forAttribute != null) {
                attributes = SAXUtils.addOrReplaceAttribute(attributes, "", "", refIdAttributeName, xformsHandlerContext.getIdPrefix() + forAttribute + xformsHandlerContext.getIdPostfix());
            }
        }
		return attributes;
	}
}
