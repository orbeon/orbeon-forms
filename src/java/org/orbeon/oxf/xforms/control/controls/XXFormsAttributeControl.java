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
package org.orbeon.oxf.xforms.control.controls;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Represents an extension xxforms:attribute control.
 */
public class XXFormsAttributeControl extends XFormsValueControl implements XFormsPseudoControl {

    private String forAttribute;
    private String nameAttribute;
    private String valueAttribute;

    public XXFormsAttributeControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);

        // Remember attributes
        this.forAttribute = element.attributeValue(XFormsConstants.FOR_QNAME);
        this.nameAttribute = element.attributeValue(XFormsConstants.NAME_QNAME);
        this.valueAttribute = element.attributeValue(XFormsConstants.VALUE_QNAME);
    }

    /**
     * Special constructor used for label, etc. content AVT handling.
     *
     * @param container             container
     * @param element               control element (should not be used here)
     * @param nameAttribute         name of the attribute
     * @param avtExpression         attribute template expression
     */
    public XXFormsAttributeControl(XBLContainer container, Element element, String nameAttribute, String avtExpression) {
        super(container, null, element, element.getName(), null);
        this.nameAttribute = nameAttribute;
        this.valueAttribute = avtExpression;
    }

    @Override
    protected void evaluateValue(final PropertyContext propertyContext) {
        // Value comes from the AVT value attribute
        final String rawValue = evaluateAvt(propertyContext, valueAttribute);
        super.setValue((rawValue != null) ? rawValue : "");
    }

    @Override
    public String getEscapedExternalValue(PipelineContext pipelineContext) {
        // Rewrite URI attribute if needed
        // This will resolve as a resource or render URL
        return XFormsUtils.getEscapedURLAttributeIfNeeded(pipelineContext, getXBLContainer().getContainingDocument(), getControlElement(), nameAttribute, getExternalValue(pipelineContext));
    }

    @Override
    protected void evaluateExternalValue(PropertyContext propertyContext) {
        // Determine attribute value
        setExternalValue(getExternalValueHandleSrc(getValue(), nameAttribute));
    }

    public String getNameAttribute() {
        return nameAttribute;
    }

    public String getEffectiveForAttribute() {
        return XFormsUtils.getRelatedEffectiveId(getEffectiveId(), forAttribute);
    }

    private static String getExternalValueHandleSrc(String controlValue, String attributeName) {
        String externalValue;
        if ("src".equals(attributeName)) {
            // TODO: make sure this is on xhtml:img!
            // Special case of xhtml:img/@src
            if (StringUtils.isNotBlank(controlValue))
                externalValue = controlValue;
            else
                externalValue = XFormsConstants.DUMMY_IMAGE_URI;
        } else if (controlValue == null) {
            // No usable value
            externalValue = "";
        } else {
            // Use value as is
            externalValue = controlValue;
        }
        return externalValue;
    }

    @Override
    public String getNonRelevantEscapedExternalValue(PropertyContext propertyContext) {
        if ("src".equals(nameAttribute)) {
            // TODO: make sure this is on xhtml:img!
            // Return rewritten URL of dummy image URL
            return XFormsUtils.resolveResourceURL(propertyContext, containingDocument, getControlElement(), XFormsConstants.DUMMY_IMAGE_URI,
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
        } else {
            return super.getNonRelevantEscapedExternalValue(propertyContext);
        }
    }

    public static String getExternalValue(XXFormsAttributeControl attributeControl, String attributeName) {
        if (attributeControl != null) {
            // Get control value
            return getExternalValueHandleSrc(attributeControl.getValue(), attributeName);
        } else {
            // Provide default
            return getExternalValueHandleSrc(null, attributeName);
        }
    }

    @Override
    public boolean setFocus() {
        // Can't focus on AVTs
        return false;
    }

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other, AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        assert attributesImpl.getLength() == 0;

        final XXFormsAttributeControl attributeControl2 = this;

        // Whether it is necessary to output information about this control
        boolean doOutputElement = false;

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, XFormsUtils.namespaceId(containingDocument, attributeControl2.getEffectiveId()));

        // The client does not store an HTML representation of the xxforms:attribute control, so we
        // have to output these attributes.
        {
            // HTML element id
            final String effectiveFor2 = attributeControl2.getEffectiveForAttribute();
            doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "for", XFormsUtils.namespaceId(containingDocument, effectiveFor2), isNewlyVisibleSubtree, false);
        }

        {
            // Attribute name
            final String name2 = attributeControl2.getNameAttribute();
            doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "name", name2, isNewlyVisibleSubtree, false);
        }

        // Output element
        outputValueElement(pipelineContext, ch, attributeControl2, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "attribute");
    }

    @Override
    public boolean supportFullAjaxUpdates() {
        return false;
    }
}
