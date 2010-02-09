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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.value.AtomicValue;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * Control with a single-node binding (possibly optional). Such controls can have MIPs.
 * 
 * @noinspection SimplifiableIfStatement
 */
public abstract class XFormsSingleNodeControl extends XFormsControl {

    // Bound item
    private Item boundItem;

    // Whether MIPs have been read from the node
    private boolean mipsRead;

    // Standard MIPs
    private boolean readonly;
    private boolean required;
    private boolean valid = true;

    // Previous values for refresh
    private boolean wasReadonly;
    private boolean wasRequired;
    private boolean wasValid;

    // Type
    private String type;

    // Custom MIPs
    private Map<String, String> customMIPs;
    private String customMIPsAsString;

    public XFormsSingleNodeControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    @Override
    public void markDirty() {
        super.markDirty();

        // Keep previous values
        wasReadonly = readonly;
        wasRequired = required;
        wasValid = valid;

        // Clear everything
        mipsRead = false;
        type = null;
        customMIPs = null;
        customMIPsAsString = null;
    }

    @Override
    public void setBindingContext(PropertyContext propertyContext, XFormsContextStack.BindingContext bindingContext, boolean isCreate) {

        // Keep binding context
        super.setBindingContext(propertyContext, bindingContext, isCreate);

        // Set bound item, only considering actual bindings with @bind, @ref or @nodeset
        if (bindingContext.isNewBind())
            this.boundItem = bindingContext.getSingleItem();
    }

    /**
     * Return the item (usually a node) to which the control is bound, if any. If the control is not bound to any item,
     * return null.
     *
     * @return bound item or null
     */
    public Item getBoundItem() {
        return boundItem;
    }

    public boolean isReadonly() {
        getMIPsIfNeeded();
        return readonly;
    }

    /**
     * Convenience method to figure out when a control is relevant, assuming a "null" control is non-relevant.
     *
     * @param control   control to test or not
     * @return          true if the control is not null and it is relevant
     */
    public static boolean isRelevant(XFormsSingleNodeControl control) {
        return control != null && control.isRelevant();
    }

    @Override
    public boolean supportsRefreshEvents() {
        // Single-node controls support refresh events
        return true;
    }

    public boolean isRequired() {
        getMIPsIfNeeded();
        return required;
    }

    public boolean wasReadonly() {
        return wasReadonly;
    }

    public boolean wasRequired() {
        return wasRequired;
    }

    public boolean wasValid() {
        return wasValid;
    }

    public boolean isValueChanged() {
        return false;
    }

    public String getType() {
        getMIPsIfNeeded();
        return type;
    }

    public Map<String, String> getCustomMIPs() {
        getMIPsIfNeeded();
        return customMIPs;
    }

    /**
     * Return this control's custom MIPs as a String containing space-separated classes.
     *
     * @return  classes, or null if no custom MIPs
     */
    public String getCustomMIPsClasses() {
        final Map customMIPs = getCustomMIPs();
        if (customMIPs != null) {
            // There are custom MIPs
            if (customMIPsAsString == null) {
                // Must compute now

                final FastStringBuffer sb = new FastStringBuffer(20);

                for (Object o: customMIPs.entrySet()) {
                    final Map.Entry entry = (Map.Entry) o;
                    final String name = (String) entry.getKey();
                    final String value = (String) entry.getValue();

                    if (sb.length() > 0)
                        sb.append(' ');

                    // TODO: encode so that there are no spaces
                    sb.append(name);
                    sb.append('-');
                    sb.append(value);
                }
                customMIPsAsString = sb.toString();
            }
        } else {
            // No custom MIPs so string value is null
        }

        return customMIPsAsString;
    }

    /**
     * Convenience method to return the local name of a built-in XML Schema or XForms type.
     *
     * @return the local name of the built-in type, or null if not found
     */
    public String getBuiltinTypeName() {
        final String type = getType();

        if (type != null) {
            final boolean isBuiltInSchemaType = type.startsWith(XFormsConstants.XSD_EXPLODED_TYPE_PREFIX);
            final boolean isBuiltInXFormsType = type.startsWith(XFormsConstants.XFORMS_EXPLODED_TYPE_PREFIX);

            if (isBuiltInSchemaType || isBuiltInXFormsType) {
                return type.substring(type.indexOf('}') + 1);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean isValid() {
        getMIPsIfNeeded();
        return valid;
    }

    @Override
    protected void evaluate(PropertyContext propertyContext, boolean isRefresh) {
        super.evaluate(propertyContext, isRefresh);

        getMIPsIfNeeded();

        if (!isRefresh) {
            // Sync values
            wasReadonly = readonly;
            wasRequired = required;
            wasValid = valid;
        }
    }

    // Experiment with not evaluating labels, etc. if control is not relevant.
//    @Override
//    protected void evaluate(PipelineContext pipelineContext) {
//        getMIPsIfNeeded();
//        super.evaluate(pipelineContext);
//    }
//
//    @Override
//    public String getLabel(PipelineContext pipelineContext) {
//        getMIPsIfNeeded();
//        // Do not compute if the control is not relevant
//        return relevant ? super.getLabel(pipelineContext) : null;
//    }
//
//    @Override
//    public String getHelp(PipelineContext pipelineContext) {
//        getMIPsIfNeeded();
//        // Do not compute if the control is not relevant
//        return relevant ? super.getHelp(pipelineContext) : null;
//    }
//
//    @Override
//    public String getHint(PipelineContext pipelineContext) {
//        getMIPsIfNeeded();
//        // Do not compute if the control is not relevant
//        return relevant ? super.getHint(pipelineContext) : null;
//    }
//
//    @Override
//    public String getAlert(PipelineContext pipelineContext) {
//        getMIPsIfNeeded();
//        // Do not compute if the control is not relevant
//        return relevant ? super.getAlert(pipelineContext) : null;
//    }

    protected void getMIPsIfNeeded() {
        if (!mipsRead) {
            final Item currentItem = bindingContext.getSingleItem();
            if (bindingContext.isNewBind()) {
                if (currentItem instanceof NodeInfo) {
                    // Control is bound to a node - get model item properties
                    final NodeInfo currentNodeInfo = (NodeInfo) currentItem;
                    this.readonly = InstanceData.getInheritedReadonly(currentNodeInfo);
                    this.required = InstanceData.getRequired(currentNodeInfo);
                    this.valid = InstanceData.getValid(currentNodeInfo);
                    this.type = InstanceData.getType(currentNodeInfo);

                    // Custom MIPs
                    this.customMIPs = InstanceData.getAllCustom(currentNodeInfo);
                    if (this.customMIPs != null)
                        this.customMIPs = new HashMap<String, String>(this.customMIPs);

                    // Handle global read-only setting
                    if (XFormsProperties.isReadonly(containingDocument))
                        this.readonly = true;
                } else {
                    // Control is not bound to a node, MIPs get default values
                    this.readonly = false;
                    this.required = false;
                    this.valid = true;// by default, a control is not invalid
                    this.type = null;
                    this.customMIPs = null;
                }
            } else {
                // Control is not bound to a node because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
                this.readonly = false;
                this.required = false;
                this.valid = true;// by default, a control is not invalid
                this.type = null;
                this.customMIPs = null;
            }
            mipsRead = true;
        }
    }

    @Override
    protected boolean computeRelevant() {

        // If parent is not relevant then we are not relevant either
        if (!super.computeRelevant())
            return false;

        final Item currentItem = bindingContext.getSingleItem();
        if (bindingContext.isNewBind()) {
            // There is a binding
            if (currentItem instanceof NodeInfo) {
                final NodeInfo currentNodeInfo = (NodeInfo) currentItem;
                // Control is bound to a node, get node relevance
                return InstanceData.getInheritedRelevant(currentNodeInfo);
            } else if (currentItem instanceof AtomicValue) {
                // Control bound to a value is considered relevant
                return true;
            } else {
                // Control is not bound to a node or item, consider non-relevant
                return false;
            }
        } else {
            // Control is not bound because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
            return true;
        }
    }

    @Override
    public boolean equalsExternal(PropertyContext propertyContext, XFormsControl other) {

        if (other == null || !(other instanceof XFormsSingleNodeControl))
            return false;

        if (this == other)
            return true;

        final XFormsSingleNodeControl otherSingleNodeControl = (XFormsSingleNodeControl) other;

        // Make sure the MIPs are up to date before comparing them
        getMIPsIfNeeded();
        otherSingleNodeControl.getMIPsIfNeeded();

        // Standard MIPs
        if (readonly != otherSingleNodeControl.readonly)
            return false;
        if (required != otherSingleNodeControl.required)
            return false;
        if (valid != otherSingleNodeControl.valid)
            return false;

        // Custom MIPs
        if (!compareCustomMIPs(customMIPs, otherSingleNodeControl.customMIPs))
            return false;

        // Type
        if (!XFormsUtils.compareStrings(type, otherSingleNodeControl.type))
            return false;

        return super.equalsExternal(propertyContext, other);
    }

    public static boolean compareCustomMIPs(Map mips1, Map mips2) {
        // equal if the mappings are equal, so Map equality should work fine
        return mips1 == null && mips2 == null || mips1 != null && mips1.equals(mips2);
    }

    @Override
    public boolean isStaticReadonly() {
        // Static read-only if we are read-only and static (global or local setting)
        return isReadonly()
                && (XFormsProperties.isStaticReadonlyAppearance(containingDocument)
                    || XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE.equals(getControlElement().attributeValue(XFormsConstants.XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME)));
    }

    @Override
    public boolean setFocus() {
        if (isRelevant() && !isReadonly()) {
            // "4.3.7 The xforms-focus Event [...] Any form control is able to accept the focus if it is relevant"
            // In addition, we don't allow focusing on read-only controls

            // Store new focus information for client
            containingDocument.setClientFocusEffectiveControlId(getEffectiveId());
            return true;
        } else {
            return false;
        }
    }

    public static final boolean DEFAULT_RELEVANCE_FOR_NEW_ITERATION = true;

    @Override
    public void outputAjaxDiff(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        assert attributesImpl.getLength() == 0;

        final XFormsSingleNodeControl xformsSingleNodeControl1 = (XFormsSingleNodeControl) other;
        final XFormsSingleNodeControl xformsSingleNodeControl2 = this;

        // Control id
        attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsSingleNodeControl2.getEffectiveId());

        // Whether it is necessary to output information about this control
        boolean doOutputElement = false;

        // Model item properties
        if (isNewlyVisibleSubtree && xformsSingleNodeControl2.isReadonly()
                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isReadonly() != xformsSingleNodeControl2.isReadonly()) {
            attributesImpl.addAttribute("", XFormsConstants.READONLY_ATTRIBUTE_NAME,
                    XFormsConstants.READONLY_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isReadonly()));
            doOutputElement = true;
        }
        if (isNewlyVisibleSubtree && xformsSingleNodeControl2.isRequired()
                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRequired() != xformsSingleNodeControl2.isRequired()) {
            attributesImpl.addAttribute("", XFormsConstants.REQUIRED_ATTRIBUTE_NAME,
                    XFormsConstants.REQUIRED_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRequired()));
            doOutputElement = true;
        }

        // Default for relevance
        if (isNewlyVisibleSubtree && xformsSingleNodeControl2.isRelevant() != DEFAULT_RELEVANCE_FOR_NEW_ITERATION
                //|| XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl1) != XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl2)) {
                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isRelevant() != xformsSingleNodeControl2.isRelevant()) {//TODO: not sure why the above alternative fails tests. Which is more correct?
            attributesImpl.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                    XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isRelevant()));
            doOutputElement = true;
        }
        if (isNewlyVisibleSubtree && !xformsSingleNodeControl2.isValid()
                || xformsSingleNodeControl1 != null && xformsSingleNodeControl1.isValid() != xformsSingleNodeControl2.isValid()) {
            attributesImpl.addAttribute("", XFormsConstants.VALID_ATTRIBUTE_NAME,
                    XFormsConstants.VALID_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(xformsSingleNodeControl2.isValid()));
            doOutputElement = true;
        }

        // Custom MIPs
        doOutputElement = diffCustomMIPs(attributesImpl, xformsSingleNodeControl1, xformsSingleNodeControl2, isNewlyVisibleSubtree, doOutputElement);
        doOutputElement = diffClassAVT(attributesImpl, xformsSingleNodeControl1, xformsSingleNodeControl2, isNewlyVisibleSubtree, doOutputElement);

        // Type attribute
        {

            final String typeValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getType();
            final String typeValue2 = xformsSingleNodeControl2.getType();

            if (isNewlyVisibleSubtree || !XFormsUtils.compareStrings(typeValue1, typeValue2)) {
                final String attributeValue = typeValue2 != null ? typeValue2 : "";
                // NOTE: No type is considered equivalent to xs:string or xforms:string
                // TODO: should have more generic code in XForms engine to equate "no type" and "xs:string"
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "type", attributeValue, isNewlyVisibleSubtree,
                        attributeValue.equals("") || XMLConstants.XS_STRING_EXPLODED_QNAME.equals(attributeValue) || XFormsConstants.XFORMS_STRING_EXPLODED_QNAME.equals(attributeValue));
            }
        }

        // Label, help, hint, alert, etc.
        {
            final String labelValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getLabel(pipelineContext);
            final String labelValue2 = xformsSingleNodeControl2.getLabel(pipelineContext);

            if (!XFormsUtils.compareStrings(labelValue1, labelValue2)) {
                final String escapedLabelValue2 = xformsSingleNodeControl2.getEscapedLabel(pipelineContext);
                final String attributeValue = escapedLabelValue2 != null ? escapedLabelValue2 : "";
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "label", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        {
            final String helpValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getHelp(pipelineContext);
            final String helpValue2 = xformsSingleNodeControl2.getHelp(pipelineContext);

            if (!XFormsUtils.compareStrings(helpValue1, helpValue2)) {
                final String escapedHelpValue2 = xformsSingleNodeControl2.getEscapedHelp(pipelineContext);
                final String attributeValue = escapedHelpValue2 != null ? escapedHelpValue2 : "";
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "help", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        {
            final String hintValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getHint(pipelineContext);
            final String hintValue2 = xformsSingleNodeControl2.getHint(pipelineContext);

            if (!XFormsUtils.compareStrings(hintValue1, hintValue2)) {
                final String escapedHintValue2 = xformsSingleNodeControl2.getEscapedHint(pipelineContext);
                final String attributeValue = escapedHintValue2 != null ? escapedHintValue2 : "";
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "hint", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        {
            final String alertValue1 = isNewlyVisibleSubtree ? null : xformsSingleNodeControl1.getAlert(pipelineContext);
            final String alertValue2 = xformsSingleNodeControl2.getAlert(pipelineContext);

            if (!XFormsUtils.compareStrings(alertValue1, alertValue2)) {
                final String escapedAlertValue2 = xformsSingleNodeControl2.getEscapedAlert(pipelineContext);
                final String attributeValue = escapedAlertValue2 != null ? escapedAlertValue2 : "";
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "alert", attributeValue, isNewlyVisibleSubtree, attributeValue.equals(""));
            }
        }

        // Output control-specific attributes
        doOutputElement |= xformsSingleNodeControl2.addCustomAttributesDiffs(pipelineContext, xformsSingleNodeControl1, attributesImpl, isNewlyVisibleSubtree);

        // Get current value if possible for this control
        // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
        // client not to update the value, unlike with attributes which can be omitted
        if (xformsSingleNodeControl2 instanceof XFormsValueControl && !(xformsSingleNodeControl2 instanceof XFormsUploadControl)) {

            // TODO: Output value only when changed

            // Output element
            final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsSingleNodeControl2;
            outputElement(pipelineContext, ch, xformsValueControl, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "control");
        } else {
            // No value, just output element with no content (but there may be attributes)
            if (doOutputElement)
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
        }

        // Output extension attributes in no namespace
        // TODO: If only some attributes changed, then we also output xxf:control above, which is unnecessary
        xformsSingleNodeControl2.addStandardAttributesDiffs(xformsSingleNodeControl1, ch, isNewlyVisibleSubtree);
    }

    protected void outputElement(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsValueControl xformsValueControl,
                                 boolean doOutputElement, boolean isNewlyVisibleSubtree, Attributes attributesImpl, String elementName) {
        // Create element with text value
        final String value;
        if (xformsValueControl.isRelevant()) {
            // NOTE: Not sure if it is still possible to have a null value when the control is relevant
            final String tempValue = xformsValueControl.getEscapedExternalValue(pipelineContext);
            value = (tempValue == null) ? "" : tempValue;
        } else {
            value = "";
        }
        if (doOutputElement || !isNewlyVisibleSubtree || !value.equals("")) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, elementName, attributesImpl);
            if (value.length() > 0)
                ch.text(value);
            ch.endElement();
        }
    }

    protected static boolean addOrAppendToAttributeIfNeeded(AttributesImpl attributesImpl, String name, String value, boolean isNewRepeatIteration, boolean isDefaultValue) {
        if (isNewRepeatIteration && isDefaultValue) {
            return false;
        } else {
            XMLUtils.addOrAppendToAttribute(attributesImpl, name, value);
            return true;
        }
    }

    // public for unit tests
    public static boolean diffCustomMIPs(AttributesImpl attributesImpl, XFormsSingleNodeControl xformsSingleNodeControl1,
                                         XFormsSingleNodeControl xformsSingleNodeControl2, boolean newlyVisibleSubtree, boolean doOutputElement) {
        final Map<String, String> customMIPs1 = (xformsSingleNodeControl1 == null) ? null : xformsSingleNodeControl1.getCustomMIPs();
        final Map<String, String> customMIPs2 = xformsSingleNodeControl2.getCustomMIPs();

        if (newlyVisibleSubtree || !XFormsSingleNodeControl.compareCustomMIPs(customMIPs1, customMIPs2)) {
            // Custom MIPs changed

            final String attributeValue;
            if (customMIPs1 == null) {
                attributeValue = xformsSingleNodeControl2.getCustomMIPsClasses();
            } else {
                final StringBuilder sb = new StringBuilder(100);

                // Classes to remove
                for (final Map.Entry<String, String> entry: customMIPs1.entrySet()) {
                    final String name = entry.getKey();
                    final String value = entry.getValue();

                    // customMIPs2 may be null if the control becomes no longer bound
                    final String newValue = (customMIPs2 == null) ? null : customMIPs2.get(name);
                    if (newValue == null || !value.equals(newValue)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('-');
                        // TODO: encode so that there are no spaces
                        sb.append(name);
                        sb.append('-');
                        sb.append(value);
                    }
                }

                // Classes to add
                // customMIPs2 may be null if the control becomes no longer bound
                if (customMIPs2 != null) {
                    for (final Map.Entry<String, String> entry: customMIPs2.entrySet()) {
                        final String name = entry.getKey();
                        final String value = entry.getValue();

                        final String oldValue = customMIPs1.get(name);
                        if (oldValue == null || !value.equals(oldValue)) {

                            if (sb.length() > 0)
                                sb.append(' ');

                            sb.append('+');
                            // TODO: encode so that there are no spaces
                            sb.append(name);
                            sb.append('-');
                            sb.append(value);
                        }
                    }
                }

                attributeValue = sb.toString();
            }
            // This attribute is a space-separate list of class names prefixed with either '-' or '+'
            if (attributeValue != null)
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue.equals(""));
        }
        return doOutputElement;
    }

    // public for unit tests
    public static boolean diffClassAVT(AttributesImpl attributesImpl, XFormsControl control1, XFormsControl control2,
                                       boolean newlyVisibleSubtree, boolean doOutputElement) {

        final String class1 = (control1 == null) ? null : control1.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);
        final String class2 = control2.getExtensionAttributeValue(XFormsConstants.CLASS_QNAME);

        if (newlyVisibleSubtree || !XFormsUtils.compareStrings(class1, class2)) {
            // Custom MIPs changed

            final String attributeValue;
            if (class1 == null) {
                attributeValue = class2;
            } else {
                final StringBuilder sb = new StringBuilder(100);

                final Set<String> classes1 = tokenize(class1);
                final Set<String> classes2 = tokenize(class2);

                // Classes to remove
                for (final String currentClass: classes1) {
                    if (!classes2.contains(currentClass)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('-');
                        sb.append(currentClass);
                    }
                }

                // Classes to add
                for (final String currentClass: classes2) {
                    if (!classes1.contains(currentClass)) {

                        if (sb.length() > 0)
                            sb.append(' ');

                        sb.append('+');
                        sb.append(currentClass);
                    }
                }

                attributeValue = sb.toString();
            }
            // This attribute is a space-separate list of class names prefixed with either '-' or '+'
            if (attributeValue != null)
                doOutputElement |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue.equals(""));
        }
        return doOutputElement;
    }

    private static Set<String> tokenize(String value) {
        final Set<String> result;
        if (value != null) {
            result = new LinkedHashSet<String>();
            for (final StringTokenizer st = new StringTokenizer(value); st.hasMoreTokens();) {
                result.add(st.nextToken());
            }
        } else {
            result = Collections.emptySet();
        }
        return result;
    }
}
