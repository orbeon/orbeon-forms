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
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.value.*;

import java.util.*;

/**
 * Base class for all controls that hold a value.
 */
public abstract class XFormsValueControl extends XFormsSingleNodeControl {

    // Value
    private String value; // TODO: use ControlProperty<String>?

    // Previous value for refresh
    private String previousValue;

    // External value (evaluated lazily)
    private boolean isExternalValueEvaluated;
    private String externalValue;

    protected XFormsValueControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        value = null;
        previousValue = null;

        markExternalValueDirty();
    }

    @Override
    protected void evaluateImpl() {

        // Evaluate other aspects of the control if necessary
        super.evaluateImpl();

        // Evaluate control values
        if (isRelevant()) {
            // Control is relevant
            if (value == null) {
                // Only evaluate if the value is not already available
                evaluateValue();
            }
        } else {
            // Control is not relevant
            isExternalValueEvaluated = true;
            value = null;
        }

        // NOTE: We no longer evaluate the external value here, instead we do lazy evaluation. This is good in particular when there
        // are multiple refreshes during an Ajax request, and LHHA values are only needed in the end.
    }

    @Override
    protected void markDirtyImpl(XPathDependencies xpathDependencies) {
        super.markDirtyImpl(xpathDependencies);

        // Handle value update
        if (xpathDependencies.requireValueUpdate(getPrefixedId())) {

            value = null;

            // Always mark the external value dirty if the value is dirty
            markExternalValueDirty();
        }
    }

    protected void evaluateValue() {
        setValue(XFormsUtils.getBoundItemValue(getBoundItem()));
    }

    protected void evaluateExternalValue() {
        // By default, same as value
        setExternalValue(getValue());
    }

    protected void markExternalValueDirty() {
        isExternalValueEvaluated = false;
        externalValue = null;
    }

    protected boolean isExternalValueDirty() {
        return !isExternalValueEvaluated;
    }

    @Override
    public boolean isValueChanged() {
        final boolean result = !XFormsUtils.compareStrings(previousValue, value);
        previousValue = value;
        return result;
    }

    /**
     * Notify the control that its value has changed due to external user interaction. The value passed is a value as
     * understood by the UI layer.
     *
     * @param value             the new external value
     * @param type
     */
    public void storeExternalValue(String value, String type) {
        // Set value into the instance

        final Item boundItem = getBoundItem();
        if (!(boundItem instanceof NodeInfo)) // this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.");
        XFormsSetvalueAction.doSetValue(containingDocument, getIndentedLogger(), this, (NodeInfo) boundItem, value, type, "client", false);

        // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
        // the controls as dirty, and they will be evaluated when necessary later.
    }

    protected static final NamespaceMapping FORMAT_NAMESPACE_MAPPING;
    static {
        final Map mapping = new HashMap<String, String>();
        // Assume xs: prefix for default formats
        mapping.put(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
        FORMAT_NAMESPACE_MAPPING = new NamespaceMapping(mapping);
    }

    protected String getValueUseFormat(String format) {

        assert isRelevant();
        assert getValue() != null;

        final String result;
        if (format == null) {
            // Try default format for known types

            // Format according to type
            final String typeName = getBuiltinTypeName();
            if (typeName != null) {
//                final String lang = XFormsUtils.resolveXMLang(getControlElement());// this could be done as part of the static analysis?
//                format = XFormsProperties.getTypeOutputFormat(containingDocument, typeName, lang);
                format = XFormsProperties.getTypeOutputFormat(containingDocument, typeName);
            }

            if (format != null) {
                result = evaluateAsString(StringValue.makeStringValue(getValue()), format,
                        FORMAT_NAMESPACE_MAPPING, getContextStack().getCurrentVariables());
            } else {
                result = null;
            }

        } else {
            // Format value according to format attribute
            result = evaluateAsString(format, Collections.<Item>singletonList(StringValue.makeStringValue(getValue())), 1);
        }
        return result;
    }

    /**
     * Return the control's internal value.
     *
     */
    public final String getValue() {
        return value;
    }

    /**
     * Return the control's external value is the value as exposed to the UI layer.
     *
     */
    public final String getExternalValue() {
        if (!isExternalValueEvaluated) {
            if (isRelevant()) {
                evaluateExternalValue();
            } else {
                // NOTE: if the control is not relevant, nobody should ask about this in the first place
                setExternalValue(null);
            }
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
        return getExternalValue();
    }

    protected final void setValue(String value) {
        this.value = value;
    }

    protected final void setExternalValue(String externalValue) {
        this.externalValue = externalValue;
    }

    public String getNonRelevantEscapedExternalValue(PropertyContext propertyContext) {
        return "";
    }

    @Override
    public Object getBackCopy() {

        // Evaluate lazy values
        getExternalValue();

        return super.getBackCopy();
    }

    @Override
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null || !(other instanceof XFormsValueControl))
            return false;

        if (this == other)
            return true;

        final XFormsValueControl otherValueControl = (XFormsValueControl) other;

        // Compare on external value, not internal value
        if (!XFormsUtils.compareStrings(getExternalValue(), otherValueControl.getExternalValue()))
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_IN);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_FOCUS_OUT);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XFORMS_HELP);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.DOM_ACTIVATE);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE);// for noscript mode
    }

    @Override
    protected Set<String> getAllowedExternalEvents() {
        return ALLOWED_EXTERNAL_EVENTS;
    }
}
