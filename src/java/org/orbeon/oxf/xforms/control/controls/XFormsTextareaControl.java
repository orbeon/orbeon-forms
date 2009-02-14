/**
 *  Copyright (C) 2006 Orbeon, Inc.
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

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Represents an xforms:textarea control.
 */
public class XFormsTextareaControl extends XFormsValueControl {

    private static final String AUTOSIZE_APPEARANCE = Dom4jUtils.qNameToExplodedQName(XFormsConstants.XXFORMS_AUTOSIZE_APPEARANCE_QNAME);

    private boolean isMaxlengthEvaluated;

    // NOTE: textarea doesn't support maxlength natively (this is added in HTML 5), but this can be implemented natively
    private String maxlength;

    public XFormsTextareaControl(XFormsContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
    }

    public boolean hasJavaScriptInitialization() {
        return "text/html".equals(getMediatype()) || AUTOSIZE_APPEARANCE.equals(getAppearance());
    }

    protected void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);

        getMaxlength(pipelineContext);
    }

    public void markDirty() {
        super.markDirty();
        isMaxlengthEvaluated = false;
    }

    public String getMaxlength(PipelineContext pipelineContext) {
        if (!isMaxlengthEvaluated) {
            final String attributeValue = getControlElement().attributeValue(XFormsConstants.XXFORMS_MAXLENGTH_QNAME);
            maxlength = (attributeValue == null) ? null : evaluateAvt(pipelineContext, attributeValue);
            isMaxlengthEvaluated = true;
        }
        return maxlength;
    }

    public boolean addAttributesDiffs(PipelineContext pipelineContext, XFormsSingleNodeControl other, AttributesImpl attributesImpl, boolean isNewRepeatIteration) {
        final XFormsTextareaControl inputControlInfo1 = (XFormsTextareaControl) other;
        final XFormsTextareaControl inputControlInfo2 = this;

        boolean added = false;
        {
            // maxlength
            final String maxlengthValue1 = (inputControlInfo1 == null) ? null : inputControlInfo1.getMaxlength(pipelineContext);
            final String maxlengthValue2 = inputControlInfo2.getMaxlength(pipelineContext);

            if (!XFormsUtils.compareStrings(maxlengthValue1, maxlengthValue2)) {
                final String attributeValue = maxlengthValue2 != null ? maxlengthValue2 : "";
                added |= addAttributeIfNeeded(attributesImpl, "maxlength", attributeValue, isNewRepeatIteration, attributeValue.equals(""));
            }
        }

        return added;
    }

    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {
        if (obj == null || !(obj instanceof XFormsTextareaControl))
            return false;

        if (this == obj)
            return true;

        final XFormsTextareaControl other = (XFormsTextareaControl) obj;

        if (!XFormsUtils.compareStrings(getMaxlength(pipelineContext), other.getMaxlength(pipelineContext)))
            return false;

        return super.equalsExternal(pipelineContext, obj);
    }
}
