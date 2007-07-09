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
import org.orbeon.oxf.util.XPathCache;
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

        setValue(XFormsInstance.getValueForNodeInfo(boundNode));
    }

    protected void evaluateDisplayValue(PipelineContext pipelineContext) {
        // NOP for most controls
    }

    /**
     * Notify the control that its value has changed due to external user interaction.
     *
     * @param value the new value
     */
    public void setExternalValue(PipelineContext pipelineContext, String value, String type) {
        // Set value into the instance

        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.");
        XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, boundNode, value, type);

        // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
        // the controls as dirty, and they will be evaluated when necessary later.
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

            // Format according to type
            final String type = getType();
            if (type != null){
                // Support both xs:* and xforms:*
                final boolean isBuiltInSchemaType = type.startsWith(XFormsConstants.XSD_EXPLODED_TYPE_PREFIX);
                final boolean isBuiltInXFormsType = type.startsWith(XFormsConstants.XFORMS_EXPLODED_TYPE_PREFIX);

                if (isBuiltInSchemaType || isBuiltInXFormsType) {
                    final String typeName = type.substring(type.indexOf('}') + 1);

                    if ("date".equals(typeName)) {
                        // Format a date
                        final String DEFAULT_FORMAT = "if (. castable as xs:date) then format-date(xs:date(.), '[FNn] [MNn] [D], [Y] [ZN]', 'en', (), ()) else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DATE_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    } else if ("dateTime".equals(typeName)) {
                        // Format a dateTime
                        final String DEFAULT_FORMAT = "if (. castable as xs:dateTime) then format-dateTime(xs:dateTime(.), '[FNn] [MNn] [D], [Y] [H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DATETIME_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    } else if ("time".equals(typeName)) {
                        // Format a time
                        final String DEFAULT_FORMAT = "if (. castable as xs:time) then format-time(xs:time(.), '[H01]:[m01]:[s01] [ZN]', 'en', (), ()) else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_TIME_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    } else if ("decimal".equals(typeName)) {
                        // Format a decimal
                        final String DEFAULT_FORMAT = "if (. castable as xs:decimal) then format-number(xs:decimal(.),'###,###,###,##0.00') else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DECIMAL_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    } else if ("integer".equals(typeName)) {
                        // Format an integer
                        final String DEFAULT_FORMAT = "if (. castable as xs:integer) then format-number(xs:integer(.),'###,###,###,##0') else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_INTEGER_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    } else if ("float".equals(typeName)) {
                        // Format a float
                        final String DEFAULT_FORMAT = "if (. castable as xs:float) then format-number(xs:float(.),'#,##0.000') else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_FLOAT_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    } else if ("double".equals(typeName)) {
                        // Format a double
                        final String DEFAULT_FORMAT = "if (. castable as xs:double) then format-number(xs:double(.),'#,##0.000') else .";
                        format = propertySet.getString(XFormsConstants.XFORMS_DEFAULT_DOUBLE_FORMAT_PROPERTY, DEFAULT_FORMAT);
                    }
                }
            }

            if (format != null) {
                final NodeInfo boundNode = getBoundNode();
                if (boundNode == null) {
                    result = null;
                } else {
                    result = XPathCache.evaluateAsString(pipelineContext, boundNode,
                            format, prefixToURIMap, null, containingDocument.getXFormsControls().getFunctionLibrary(), null, getLocationData());
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
                result = XPathCache.evaluateAsString(pipelineContext, boundNode,
                        format, prefixToURIMap, null, containingDocument.getXFormsControls().getFunctionLibrary(), null, getLocationData());
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

        final String displayValueOrValue = getDisplayValueOrValue();
        final String otherDisplayValueOrValue = other.getDisplayValueOrValue();

        if (!((displayValueOrValue == null && otherDisplayValueOrValue == null)
                || (displayValueOrValue != null && otherDisplayValueOrValue != null && displayValueOrValue.equals(otherDisplayValueOrValue))))
            return false;

        return super.equals(obj);
    }

    public boolean isValueControl() {
        return XFormsControls.isValueControl(getName());
    }
}
