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
package org.orbeon.oxf.xforms.control;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all controls that hold a value.
 */
public abstract class XFormsValueControl extends XFormsSingleNodeControl {

    private boolean isValueEvaluated;
    private String value;
    private boolean isExternalValueEvaluated;
    private String externalValue;

    // Previous value for refresh
    private String previousValue;

    protected XFormsValueControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    @Override
    protected void evaluate(PropertyContext propertyContext, boolean isRefresh) {

        // Evaluate other aspects of the control if necessary
        super.evaluate(propertyContext, isRefresh);

        // Evaluate control values
        if (isRelevant()) {
            // Control is relevant
            getValue(propertyContext);
            getExternalValue(propertyContext);
        } else {
            // Control is not relevant
            isValueEvaluated = true;
            isExternalValueEvaluated = true;
            value = null;
            externalValue = null;
        }

        if (!isRefresh) {
            // Sync values
            previousValue = value;
            // TODO: what about external value?
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();

        // Keep previous values
        previousValue = value;
        // TODO: what about external value?

        isValueEvaluated = false;
        isExternalValueEvaluated = false;
        value = null;
        externalValue = null;
    }

    protected void evaluateValue(PropertyContext propertyContext) {
        setValue(XFormsUtils.getBoundItemValue(getBoundItem()));
    }

    protected void evaluateExternalValue(PropertyContext propertyContext) {
        // By default, same as value
        setExternalValue(getValue(propertyContext));
    }

    @Override
    public boolean isValueChanged() {
        return !XFormsUtils.compareStrings(previousValue, value);
    }

    /**
     * Notify the control that its value has changed due to external user interaction. The value passed is a value as
     * understood by the UI layer.
     *
     * @param propertyContext   current context
     * @param value             the new external value
     * @param type
     * @param filesElement      special filesElement construct for controls that need it
     */
    public void storeExternalValue(PropertyContext propertyContext, String value, String type, Element filesElement) {
        // Set value into the instance

        final Item boundItem = getBoundItem();
        if (!(boundItem instanceof NodeInfo)) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.");
        XFormsSetvalueAction.doSetValue(propertyContext, containingDocument, getIndentedLogger(), this, (NodeInfo) boundItem, value, type, false);

        // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
        // the controls as dirty, and they will be evaluated when necessary later.
    }

    protected String getValueUseFormat(PropertyContext propertyContext, String format) {

        final String result;
        if (format == null) {
            // Try default format for known types

            // Assume xs: prefix for default formats
            final Map<String, String> prefixToURIMap = new HashMap<String, String>();
            prefixToURIMap.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);

            // Format according to type
            final String typeName = getBuiltinTypeName();
            if (typeName != null) {
//                final String lang = XFormsUtils.resolveXMLang(getControlElement());// this could be done as part of the static analysis?
//                format = XFormsProperties.getTypeOutputFormat(containingDocument, typeName, lang);
                format = XFormsProperties.getTypeOutputFormat(containingDocument, typeName);
            }

            if (format != null) {
                result = evaluateAsString(propertyContext, getBoundItem(), format,
                        prefixToURIMap, getContextStack().getCurrentVariables());
            } else {
                result = null;
            }

        } else {
            // Format value according to format attribute
            result = evaluateAsString(propertyContext, format);
        }
        return result;
    }

    /**
     * Return the control's internal value.
     *
     * @param propertyContext   current context
     */
    public final String getValue(PropertyContext propertyContext) {
        if (!isValueEvaluated) {
            evaluateValue(propertyContext);
            isValueEvaluated = true;
        }
        return value;
    }

    /**
     * Return the control's external value is the value as exposed to the UI layer.
     *
     * @param propertyContext   current context
     */
    public final String getExternalValue(PropertyContext propertyContext) {
        if (!isExternalValueEvaluated) {
            evaluateExternalValue(propertyContext);
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

    protected final void setValue(String value) {
        this.value = value;
    }

    protected final void setExternalValue(String externalValue) {
        this.externalValue = externalValue;
    }

    @Override
    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {

        if (obj == null || !(obj instanceof XFormsValueControl))
            return false;

        if (this == obj)
            return true;

        final XFormsValueControl other = (XFormsValueControl) obj;

        if (!XFormsUtils.compareStrings(getExternalValue(pipelineContext), other.getExternalValue(pipelineContext)))
            return false;

        return super.equalsExternal(pipelineContext, obj);
    }
}
