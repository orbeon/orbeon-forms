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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all controls that hold a value.
 */
public abstract class XFormsValueControl extends XFormsSingleNodeControl {

    private boolean isValueEvaluated;
    private String value;
    private boolean isDisplayValueEvaluated;
    private String displayValue;
    private boolean isExternalValueEvaluated;
    private String externalValue;

    protected XFormsValueControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        super(containingDocument, parent, element, name, effectiveId);
    }

    protected void evaluate(PipelineContext pipelineContext) {

        // Set context and evaluate other aspects of the control if necessary
        super.evaluate(pipelineContext);

        // Evaluate control values
        getValue(pipelineContext);
        getDisplayValue(pipelineContext);
        getExternalValue(pipelineContext);
    }


    protected void evaluateValue(PipelineContext pipelineContext) {
        // Just get the value from the bound node
        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null)
            return;

        setValue(XFormsInstance.getValueForNodeInfo(boundNode));
    }

    protected void evaluateDisplayValue(PipelineContext pipelineContext) {
        // No display value for most controls
        setDisplayValue(null);
    }

    protected void evaluateExternalValue(PipelineContext pipelineContext) {
        // By default, same as value
        setExternalValue(getValue(pipelineContext));
    }

    /**
     * Notify the control that its value has changed due to external user interaction. The value passed is a value as
     * understood by the UI layer.
     *
     * @param value     the new external value
     */
    public void storeExternalValue(PipelineContext pipelineContext, String value, String type) {
        // Set value into the instance

        final NodeInfo boundNode = getBoundNode();
        if (boundNode == null) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.");
        XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, this, boundNode, value, type, false);

        // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
        // the controls as dirty, and they will be evaluated when necessary later.
    }

    protected void evaluateDisplayValueUseFormat(PipelineContext pipelineContext, String format) {
        final String result;
        if (format == null) {
            // Try default format for known types

            final Map prefixToURIMap = new HashMap();
            prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

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
                        format = XFormsProperties.getDateFormat(containingDocument);
                    } else if ("dateTime".equals(typeName)) {
                        // Format a dateTime
                        format = XFormsProperties.getDateTimeFormat(containingDocument);
                    } else if ("time".equals(typeName)) {
                        // Format a time
                        format = XFormsProperties.getTimeFormat(containingDocument);
                    } else if ("decimal".equals(typeName)) {
                        // Format a decimal
                        format = XFormsProperties.getDecimalFormat(containingDocument);
                    } else if ("integer".equals(typeName)) {
                        // Format an integer
                        format = XFormsProperties.getIntegerFormat(containingDocument);
                    } else if ("float".equals(typeName)) {
                        // Format a float
                        format = XFormsProperties.getFloatFormat(containingDocument);
                    } else if ("double".equals(typeName)) {
                        // Format a double
                        format = XFormsProperties.getDoubleFormat(containingDocument);
                    }
                }
            }

            if (format != null) {
                final NodeInfo boundNode = getBoundNode();
                if (boundNode == null) {
                    result = null;
                } else {
                    result = XPathCache.evaluateAsString(pipelineContext, boundNode,
                            format, prefixToURIMap, getContextStack().getCurrentVariables(),
                            XFormsContainingDocument.getFunctionLibrary(),
                            getContextStack().getFunctionContext(), null, getLocationData());
                }
            } else {
                result = null;
            }

        } else {
            // Format value according to format attribute
            final Map prefixToURIMap = containingDocument.getNamespaceMappings(getControlElement());

            final NodeInfo boundNode = getBoundNode();
            if (boundNode == null) {
                result = null;
            } else {
                result = XPathCache.evaluateAsString(pipelineContext, boundNode,
                        format, prefixToURIMap, getContextStack().getCurrentVariables(),
                        XFormsContainingDocument.getFunctionLibrary(),
                        getContextStack().getFunctionContext(), null, getLocationData());
            }
        }
        setDisplayValue(result);
    }

    /**
     * Return the control's internal value.
     */
    public final String getValue(PipelineContext pipelineContext) {
        if (!isValueEvaluated) {
            evaluateValue(pipelineContext);
            isValueEvaluated = true;
        }
        return value;
    }

    /**
     * Return a formatted display value of the control value, null if there is no such display value.
     */
    public final String getDisplayValue(PipelineContext pipelineContext) {
        if (!isDisplayValueEvaluated) {
            evaluateDisplayValue(pipelineContext);
            isDisplayValueEvaluated = true;
        }
        return displayValue;
    }

    /**
     * Return the control's external value is the value as exposed to the UI layer.
     */
    public final String getExternalValue(PipelineContext pipelineContext) {
        if (!isExternalValueEvaluated) {
            evaluateExternalValue(pipelineContext);
            isExternalValueEvaluated = true;
        }
        return externalValue;
    }

    /**
     * Return the external value ready to be inserted into the client after an Ajax response.
     *
     * @param pipelineContext   current PipelineContext
     * @return                  external value
     */
    public String getEscapedExternalValue(PipelineContext pipelineContext) {
        return getExternalValue(pipelineContext);
    }

    /**
     * Return a formatted display value of the control value, or the external control value if there is no such display
     * value.
     */
    public String getDisplayValueOrExternalValue(PipelineContext pipelineContext) {
        return displayValue != null ? displayValue : getExternalValue(pipelineContext);
    }

    protected final void setValue(String value) {
        this.value = value;
    }

    protected final void setDisplayValue(String displayValue) {
        this.displayValue = displayValue;
    }

    protected final void setExternalValue(String externalValue) {
        this.externalValue = externalValue;
    }

    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {

        if (obj == null || !(obj instanceof XFormsValueControl))
            return false;

        if (this == obj)
            return true;

        final XFormsValueControl other = (XFormsValueControl) obj;

        if (!XFormsUtils.compareStrings(getDisplayValueOrExternalValue(pipelineContext), other.getDisplayValueOrExternalValue(pipelineContext)))
            return false;

        return super.equalsExternal(pipelineContext, obj);
    }

    public boolean isValueControl() {
        return XFormsControls.isValueControl(getName());
    }
}
