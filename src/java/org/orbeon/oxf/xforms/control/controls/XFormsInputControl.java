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
import org.dom4j.QName;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xml.XMLConstants;

/**
 * Represents an xforms:input control.
 */
public class XFormsInputControl extends XFormsValueControl {

    // Optional display format
    private String format;

    public XFormsInputControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.format = element.attributeValue(new QName("format", XFormsConstants.XXFORMS_NAMESPACE));
    }

    public String getFormat() {
        return format;
    }

    public void evaluateDisplayValue(PipelineContext pipelineContext) {
        evaluateDisplayValueUseFormat(pipelineContext, format);
    }

    public void setExternalValue(PipelineContext pipelineContext, String value, String type) {
        super.setExternalValue(pipelineContext, convertFromExternalValue(value), type);
    }

    private String convertFromExternalValue(String externalValue) {
        final String type = getType();
        // Store "false" when we get a blank value from the client when type is xs:boolean (case of single checkbox)
        if (type != null && XMLConstants.XS_BOOLEAN_EXPLODED_QNAME.equals(type) && externalValue.trim().equals("")) {
            return "false";
        } else {
            return externalValue;
        }
    }
}
