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

import org.dom4j.Element;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLConstants;

/**
 * Represents an xforms:range control.
 */
public class XFormsRangeControl extends XFormsValueControl {

    private String start;
    private String end;
    private String step;

    public XFormsRangeControl(XBLContainer container, XFormsControl parent, Element element, String name, String id) {
        super(container, parent, element, name, id);
        this.start = element.attributeValue("start");
        this.end = element.attributeValue("end");
        this.step = element.attributeValue("step");
    }

    @Override
    public boolean hasJavaScriptInitialization() {
        return true;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }

    public String getStep() {
        return step;
    }

    @Override
    public void storeExternalValue(PropertyContext propertyContext, String value, String type) {
        // Store after converting
        super.storeExternalValue(propertyContext, convertFromExternalValue(value), type);
    }

    private String convertFromExternalValue(String externalValue) {
        final String typeName = getBuiltinTypeName();
        if (getStart() != null && getEnd() != null && "integer".equals(typeName)) {
            final int start = Integer.parseInt(getStart());
            final int end = Integer.parseInt(getEnd());

            final int value = start + ((int) (Double.parseDouble(externalValue) * (double) (end - start)));
            return Integer.toString(value);
        } else {
            return externalValue;
        }
    }

    @Override
    protected void evaluateExternalValue(PropertyContext propertyContext) {
        final String internalValue = getValue();
        final String updatedValue;
        if (internalValue == null) {// can it be really?
            updatedValue = null;
        } else if (getStart() != null && getEnd() != null
                && (XMLConstants.XS_INTEGER_QNAME.equals(getType()) || XFormsConstants.XFORMS_INTEGER_QNAME.equals(getType()))) {

            final int start = Integer.parseInt(getStart());
            final int end = Integer.parseInt(getEnd());

            final double value = ((double) (Integer.parseInt(internalValue) - start)) / ((double) end - start);
            updatedValue = Double.toString(value);
        } else {
            updatedValue = internalValue;
        }
        setExternalValue(updatedValue);
    }
}
