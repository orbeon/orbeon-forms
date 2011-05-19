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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Represents an extension xxforms:attribute control.
 */
public class XXFormsAttributeControl extends XFormsValueControl implements XFormsPseudoControl {

    private AttributeControl attributeControl;
    private String attributeName;
    private String attributeValue;
    private String forName;

    public XXFormsAttributeControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);

        this.attributeControl = (AttributeControl) container.getContainingDocument().getStaticState().getControlAnalysis(getPrefixedId());
        this.attributeName = attributeControl.attributeName();
        this.attributeValue = attributeControl.attributeValue();
        this.forName = attributeControl.forName();
    }

    /**
     * Special constructor used for label, etc. content AVT handling.
     *
     * @param container             container
     * @param element               control element (should not be used here)
     * @param attributeName         name of the attribute
     * @param avtExpression         attribute template expression
     * @param forName               name of the element the attribute is on
     */
    public XXFormsAttributeControl(XBLContainer container, Element element, String attributeName, String avtExpression, String forName) {
        super(container, null, element, element.getName(), null);
        this.attributeControl = null;
        this.attributeName = attributeName;
        this.attributeValue = avtExpression;
        this.forName = forName;
    }

    @Override
    protected void evaluateValue() {
        // Value comes from the AVT value attribute
        final String rawValue = evaluateAvt(attributeValue);
        super.setValue((rawValue != null) ? rawValue : "");
    }

    @Override
    public String getEscapedExternalValue() {
        // Rewrite URI attribute if needed
        final String rewrittenValue;
        if ("src".equals(attributeName)) {
            rewrittenValue = XFormsUtils.resolveResourceURL(containingDocument, getControlElement(), getExternalValue(), ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        } else if ("href".equals(attributeName)) {

            final String urlType = attributeControl.urlType();
            if ("action".equals(urlType)) {
                rewrittenValue = XFormsUtils.resolveActionURL(containingDocument, getControlElement(), getExternalValue(), false);
            } else if ("resource".equals(urlType)) {
                rewrittenValue = XFormsUtils.resolveResourceURL(containingDocument, getControlElement(), getExternalValue(), ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
            } else {
                // Default is "render"
                rewrittenValue = XFormsUtils.resolveRenderURL(containingDocument, getControlElement(), getExternalValue(), false);
            }

        } else {
            rewrittenValue = getExternalValue();
        }
        return rewrittenValue;
    }

    @Override
    protected void evaluateExternalValue() {
        // Determine attribute value
        setExternalValue(getExternalValueHandleSrc(getValue(), attributeName, forName));
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getEffectiveForAttribute() {
        return XFormsUtils.getRelatedEffectiveId(getEffectiveId(), attributeControl.forStaticId());
    }

    private static String getExternalValueHandleSrc(String controlValue, String attributeName, String forName) {
        String externalValue;
        if ("src".equals(attributeName)) {
            // Special case of xhtml:img/@src
            if ("img".equals(forName) && StringUtils.isBlank(controlValue))
                externalValue = XFormsConstants.DUMMY_IMAGE_URI;
            else
                externalValue = controlValue;
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
    public String getNonRelevantEscapedExternalValue() {
        if ("img".equals(forName) && "src".equals(attributeName)) {
            // Return rewritten URL of dummy image URL
            return XFormsUtils.resolveResourceURL(containingDocument, getControlElement(), XFormsConstants.DUMMY_IMAGE_URI,
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH);
        } else {
            return super.getNonRelevantEscapedExternalValue();
        }
    }

    public static String getExternalValue(XXFormsAttributeControl concreteControl, AttributeControl attributeControl) {
        if (concreteControl != null) {
            // Get control value
            return getExternalValueHandleSrc(concreteControl.getValue(), attributeControl.attributeName(), attributeControl.forName());
        } else {
            // Provide default
            return getExternalValueHandleSrc(null, attributeControl.attributeName(), attributeControl.forName());
        }
    }

    @Override
    public boolean setFocus() {
        // Can't focus on AVTs
        return false;
    }

    @Override
    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other, AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

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
            final String name2 = attributeControl2.getAttributeName();
            doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "name", name2, isNewlyVisibleSubtree, false);
        }

        // Output element
        outputValueElement(ch, attributeControl2, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "attribute");
    }

    @Override
    public boolean supportFullAjaxUpdates() {
        return false;
    }
}
