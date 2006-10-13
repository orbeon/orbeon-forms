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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all controls that hold a value.
 */
public abstract class XFormsValueControl extends XFormsControl {

    private String value;
    private String displayValue;

    protected XFormsValueControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        super(containingDocument, parent, element, name, effectiveId);
    }

    public void evaluate(PipelineContext pipelineContext) {

        // Set context and evaluate other aspects of the control if necessary
        super.evaluate(pipelineContext);

        // Evaluate control value and display value if necessary
        evaluateValue(pipelineContext);
        evaluateDisplayValue(pipelineContext);
    }


    protected void evaluateValue(PipelineContext pipelineContext) {
        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null)
            return;

        setValue(XFormsInstance.getValueForNode(boundNode));
    }

    protected void evaluateDisplayValue(PipelineContext pipelineContext) {
        // NOP for most controls
    }

    /**
     * Notify the control that its value has changed due to external user interaction.
     *
     * @param value the new value
     */
    public void setExternalValue(PipelineContext pipelineContext, String value) {
        // Set value into the instance

        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.");
        final boolean changed = XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, boundNode, value);

        if (changed) {
            // Update this particular control's value
            evaluate(pipelineContext);
        }
    }

    public String convertToExternalValue(String internalValue) {
        return internalValue;
    }

    protected void evaluateDisplayValueUseFormat(PipelineContext pipelineContext, String format) {
        final String result;
        if (format == null) {
            // Try default format for known types

            final Map prefixToURIMap = new HashMap();
            prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

            final OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet();

            final String type = getType();
            if ("{http://www.w3.org/2001/XMLSchema}date".equals(type)) {
                // Format a date
                final String DEFAULT_FORMAT = "if (. castable as xs:date) then format-date(xs:date(.), '[MNn] [D], [Y]', 'en', (), ()) else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DATE_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}dateTime".equals(type)) {
                // Format a dateTime
                final String DEFAULT_FORMAT = "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[MNn] [D], [Y] [H01]:[m01]:[s01] UTC', 'en', (), ()) else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DATETIME_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}time".equals(type)) {
                // Format a time
                final String DEFAULT_FORMAT = "if (. castable as xs:time) then format-time(xs:time(.), '[H01]:[m01]:[s01] UTC', 'en', (), ()) else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_TIME_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}decimal".equals(type)) {
                // Format a decimal
                final String DEFAULT_FORMAT = "if (. castable as xs:decimal) then . else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DECIMAL_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}integer".equals(type)) {
                // Format an integer
                final String DEFAULT_FORMAT = "if (. castable as xs:integer) then . else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_INTEGER_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}float".equals(type)) {
                // Format a float
                final String DEFAULT_FORMAT = "if (. castable as xs:float) then . else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_FLOAT_FORMAT_PROPERTY, DEFAULT_FORMAT);
            } else if ("{http://www.w3.org/2001/XMLSchema}double".equals(type)) {
                // Format a double
                final String DEFAULT_FORMAT = "if (. castable as xs:double) then . else .";
                format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DOUBLE_FORMAT_PROPERTY, DEFAULT_FORMAT);
            }

            if (format != null) {
                final NodeInfo boundNode = getBoundNode();
                if (boundNode == null) {
                    result = null;
                } else {
                    result = containingDocument.getEvaluator().evaluateAsString(pipelineContext, boundNode,
                            format, prefixToURIMap, null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
                }
            } else {
                result = null;
            }

        } else {
            // Format value according to format attribute
            final Map prefixToURIMap = Dom4jUtils.getNamespaceContextNoDefault(getControlElement());

            final NodeInfo boundNode = getBoundNode();
            if (boundNode == null) {
                result = null;
            } else {
                result = containingDocument.getEvaluator().evaluateAsString(pipelineContext, boundNode,
                        format, prefixToURIMap, null, containingDocument.getXFormsControls().getFunctionLibrary(), null);
            }
        }
        setDisplayValue(result);
    }

    public String getValue() {
        return value;
    }

    /**
     * Return a formatted display value of the control value, null if there is no such display value.
     */
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Return a formatted display value of the control value, or the raw control value if there is no such display value.
     */
    public String getDisplayValueOrValue() {
        return displayValue != null ? displayValue : value;
    }

    protected void setValue(String value) {
        this.value = value;
    }

    protected void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    public boolean equals(Object obj) {

        if (obj == null || !(obj instanceof XFormsValueControl))
            return false;

        if (this == obj)
            return true;

        final XFormsValueControl other = (XFormsValueControl) obj;

        if (!((value == null && other.value == null) || (value != null && other.value != null && value.equals(other.value))))
            return false;

        return super.equals(obj);
    }

    public boolean isValueControl() {
        return XFormsControls.isValueControl(getName());
    }
}
