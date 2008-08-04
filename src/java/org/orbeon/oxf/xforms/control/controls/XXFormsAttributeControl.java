/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls;

import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.XFormsPseudoControl;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.dom4j.Element;

import java.util.List;

/**
 * Represents an extension xxforms:attribute control.
 */
public class XXFormsAttributeControl extends XFormsValueControl implements XFormsPseudoControl {

    private String forAttribute;
    private String nameAttribute;
    private String valueAttribute;

    public XXFormsAttributeControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        super(containingDocument, parent, element, name, effectiveId);

        // Remember attributes
        this.forAttribute = element.attributeValue("for");
        this.nameAttribute = element.attributeValue("name");
        this.valueAttribute = element.attributeValue("value");
    }

    /**
     * Special constructor used for label, etc. content AVT handling.
     *
     * @param containingDocument    containing document
     * @param element               control element (should not be used here)
     * @param avtExpression         attribute template expression
     */
    public XXFormsAttributeControl(XFormsContainingDocument containingDocument, Element element, String avtExpression) {
        super(containingDocument, null, element, element.getName(), null);
        this.valueAttribute = avtExpression;
    }

    protected void evaluateValue(final PipelineContext pipelineContext) {
        final String rawValue;
        // Value comes from the AVT value attribute
        final List currentNodeset = bindingContext.getNodeset();
        if (currentNodeset != null && currentNodeset.size() > 0) {

            rawValue = XPathCache.evaluateAsAvt(pipelineContext,
                    currentNodeset, bindingContext.getPosition(),
                    valueAttribute, containingDocument.getNamespaceMappings(getControlElement()), bindingContext.getInScopeVariables(),
                    XFormsContainingDocument.getFunctionLibrary(), getContextStack().getFunctionContext(), null, getLocationData());

        } else {
            rawValue = "";
        }

        super.setValue(rawValue);
    }

    public String getNameAttribute() {
        return nameAttribute;
    }

    public String getEffectiveForAttribute() {
        // A kind of hacky way of getting the effective id of the HTML element
        final String effectiveId = getEffectiveId();
        final String prefix; {
            final int prefixIndex = effectiveId.lastIndexOf(XFormsConstants.COMPONENT_SEPARATOR);
            prefix = (prefixIndex == -1) ? "" : effectiveId.substring(0, prefixIndex + 1);
        }
        final String suffix; {
            final int suffixIndex = effectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
            suffix = (suffixIndex == -1) ? "" : effectiveId.substring(suffixIndex);
        }
        return prefix + forAttribute + suffix;
    }
}
