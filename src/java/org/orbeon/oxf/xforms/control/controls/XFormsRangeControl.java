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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;

/**
 * Represents an xforms:range control.
 */
public class XFormsRangeControl extends XFormsControl {

    private String start;
    private String end;
    private String step;

    public XFormsRangeControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String id) {
        super(containingDocument, parent, element, name, id);
        this.start = element.attributeValue("start");
        this.end = element.attributeValue("end");
        this.step = element.attributeValue("step");
    }

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

    public void setExternalValue(PipelineContext pipelineContext, String value) {
        super.setExternalValue(pipelineContext, convertFromExternalValue(value));
    }

    private String convertFromExternalValue(String externalValue) {

        if (getStart() != null && getEnd() != null
                && XMLUtils.buildExplodedQName(XMLConstants.XSD_URI, "integer").equals(getType())) {

            final int start = Integer.parseInt(getStart());
            final int end = Integer.parseInt(getEnd());

            final int value = start + ((int) (Double.parseDouble(externalValue) * (double) (end - start)));
            return Integer.toString(value);
        } else {
            return externalValue;
        }
    }

    public String convertToExternalValue(String internalValue) {

        if (getStart() != null && getEnd() != null
                && XMLUtils.buildExplodedQName(XMLConstants.XSD_URI, "integer").equals(getType())) {

            final int start = Integer.parseInt(getStart());
            final int end = Integer.parseInt(getEnd());

            final double value = ((double) (Integer.parseInt(internalValue) - start)) / ((double) end - start);
            return Double.toString(value);
        } else {
            return internalValue;
        }
    }
}
