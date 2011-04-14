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
import org.dom4j.QName;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.value.AtomicValue;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Control with a single-node binding (possibly optional). Such controls can have MIPs.
 * 
 * @noinspection SimplifiableIfStatement
 */
public abstract class XFormsSingleNodeControl extends XFormsControl {

    // Bound item
    private Item boundItem;

    // Standard MIPs
    private boolean readonly;
    private boolean required;
    private boolean valid = true;

    // Previous values for refresh
    private boolean wasReadonly;
    private boolean wasRequired;
    private boolean wasValid = true;

    // Type
    private QName type;

    // Custom MIPs
    private Map<String, String> customMIPs;
    private String customMIPsAsString;

    public XFormsSingleNodeControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Set default MIPs so that diff picks up the right values
        setDefaultMIPs();
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        readBinding();

        wasReadonly = false;
        wasRequired = false;
        wasValid = true;
    }

    @Override
    protected void onBindingUpdate(XFormsContextStack.BindingContext oldBinding, XFormsContextStack.BindingContext newBinding) {
        super.onBindingUpdate(oldBinding, newBinding);
        readBinding();
    }

    private void readBinding() {
        // Set bound item, only considering actual bindings (with @bind, @ref or @nodeset)
        if (bindingContext.isNewBind())
            this.boundItem = bindingContext.getSingleItem();

        // Get MIPs
        final Item currentItem = getBoundItem();
        if (currentItem != null) {
            if (currentItem instanceof NodeInfo) {
                // Control is bound to a node - get model item properties
                final NodeInfo currentNodeInfo = (NodeInfo) currentItem;
                this.readonly = InstanceData.getInheritedReadonly(currentNodeInfo);
                this.required = InstanceData.getRequired(currentNodeInfo);
                this.valid = InstanceData.getValid(currentNodeInfo);
                this.type = InstanceData.getType(currentNodeInfo);

                // Custom MIPs
                final Map<String, String> tempCustomMIPs = InstanceData.getAllCustom(currentNodeInfo);
                if (tempCustomMIPs != null)
                    this.customMIPs = new HashMap<String, String>(tempCustomMIPs);

                // Handle global read-only setting
                if (XFormsProperties.isReadonly(containingDocument))
                    this.readonly = true;
            } else {
                // Control is not bound to a node (i.e. bound to an atomic value), MIPs get default values
                setDefaultMIPs();
            }
        } else {
            // Control is not bound to a node because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
            setDefaultMIPs();
        }
    }

    private void setDefaultMIPs() {
        this.readonly = false;
        this.required = false;
        this.valid = true;// by default, a control is not invalid
        this.type = null;
        this.customMIPs = null;
        this.customMIPsAsString = null;
    }

    @Override
    public void commitCurrentUIState() {
        super.commitCurrentUIState();

        isValueChanged();
        wasRequired();
        wasReadonly();
        wasValid();
    }

    /**
     * Return the item (usually a node) to which the control is bound, if any. If the control is not bound to any item,
     * return null.
     *
     * @return bound item or null
     */
    public final Item getBoundItem() {
        return boundItem;
    }

    public final boolean isReadonly() {
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

    public final boolean isRequired() {
        return required;
    }

    public final boolean wasReadonly() {
        final boolean result = wasReadonly;
        wasReadonly = readonly;
        return result;
    }

    public final boolean wasRequired() {
        final boolean result = wasRequired;
        wasRequired = required;
        return result;
    }

    public final boolean wasValid() {
        final boolean result = wasValid;
        wasValid = valid;
        return result;
    }

    public boolean isValueChanged() {
        return false;
    }

    public QName getType() {
        return type;
    }

    public String getTypeExplodedQName() {
        return Dom4jUtils.qNameToExplodedQName(type);
    }

    public Map<String, String> getCustomMIPs() {
        return customMIPs;
    }

    /**
     * Return this control's custom MIPs as a String containing space-separated classes.
     *
     * @return  classes, or null if no custom MIPs
     */
    public String getCustomMIPsClasses() {
        final Map<String, String> customMIPs = getCustomMIPs();
        if (customMIPs != null) {
            // There are custom MIPs
            if (customMIPsAsString == null) {
                // Must compute now

                final StringBuilder sb = new StringBuilder(20);

                for (final Map.Entry<String, String> entry: customMIPs.entrySet()) {
                    final String name = entry.getKey();
                    final String value = entry.getValue();

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
        final QName type = getType();

        if (type != null) {
            final boolean isBuiltInSchemaType = type.getNamespaceURI().equals(XMLConstants.XSD_URI);
            final boolean isBuiltInXFormsType = type.getNamespaceURI().equals(XFormsConstants.XFORMS_NAMESPACE_URI);

            if (isBuiltInSchemaType || isBuiltInXFormsType) {
                return type.getName();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Convenience method to return the local name of the XML Schema type.
     *
     * @return the local name of the type, or null if not found
     */
    public String getTypeLocalName() {
        final QName type = getType();

        if (type != null) {
            return type.getName();
        } else {
            return null;
        }
    }     

    public boolean isValid() {
        return valid;
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
    public boolean equalsExternal(XFormsControl other) {

        if (other == null || !(other instanceof XFormsSingleNodeControl))
            return false;

        if (this == other)
            return true;

        final XFormsSingleNodeControl otherSingleNodeControl = (XFormsSingleNodeControl) other;

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

        return super.equalsExternal(other);
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

    @Override
    public void outputAjaxDiff(ContentHandlerHelper ch, XFormsControl other,
                               AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree) {

        assert attributesImpl.getLength() == 0;

        final XFormsSingleNodeControl control1 = (XFormsSingleNodeControl) other;
        final XFormsSingleNodeControl control2 = this;

        // Add attributes
        final boolean doOutputElement = addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other);

        // Get current value if possible for this control
        // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
        // client not to update the value, unlike with attributes which can be omitted
        if (control2 instanceof XFormsValueControl && !(control2 instanceof XFormsUploadControl)) {

            // TODO: Output value only when changed

            // Output element
            final XFormsValueControl xformsValueControl = (XFormsValueControl) control2;
            outputValueElement(ch, xformsValueControl, doOutputElement, isNewlyVisibleSubtree, attributesImpl, "control");
        } else {
            // No value, just output element with no content (but there may be attributes)
            if (doOutputElement)
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
        }

        // Output extension attributes in no namespace
        // TODO: If only some attributes changed, then we also output xxf:control above, which is unnecessary
        control2.addAjaxStandardAttributes(control1, ch, isNewlyVisibleSubtree);
    }

    @Override
    protected boolean addAjaxAttributes(AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree, XFormsControl other) {

        final XFormsSingleNodeControl control1 = (XFormsSingleNodeControl) other;
        final XFormsSingleNodeControl control2 = this;

        // Call base class for the standard stuff
        boolean added = super.addAjaxAttributes(attributesImpl, isNewlyVisibleSubtree, other);

        // MIPs
        added |= addAjaxMIPs(attributesImpl, isNewlyVisibleSubtree, control1, control2);

        return added;
    }

    private boolean addAjaxMIPs(AttributesImpl attributesImpl, boolean isNewlyVisibleSubtree,
                                XFormsSingleNodeControl control1, XFormsSingleNodeControl control2) {

        boolean added = false;
        if (isNewlyVisibleSubtree && control2.isReadonly()
                || control1 != null && control1.isReadonly() != control2.isReadonly()) {
            attributesImpl.addAttribute("", XFormsConstants.READONLY_ATTRIBUTE_NAME,
                    XFormsConstants.READONLY_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(control2.isReadonly()));
            added = true;
        }
        if (isNewlyVisibleSubtree && control2.isRequired()
                || control1 != null && control1.isRequired() != control2.isRequired()) {
            attributesImpl.addAttribute("", XFormsConstants.REQUIRED_ATTRIBUTE_NAME,
                    XFormsConstants.REQUIRED_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(control2.isRequired()));
            added = true;
        }

        // NOTE: We used to have a configurable default for the relevance. Not sure why this was needed. Here consider the default is true.
        if (isNewlyVisibleSubtree && !control2.isRelevant()
                //|| XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl1) != XFormsSingleNodeControl.isRelevant(xformsSingleNodeControl2)) {
                || control1 != null && control1.isRelevant() != control2.isRelevant()) {//TODO: not sure why the above alternative fails tests. Which is more correct?
            attributesImpl.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                    XFormsConstants.RELEVANT_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(control2.isRelevant()));
            added = true;
        }
        if (isNewlyVisibleSubtree && !control2.isValid()
                || control1 != null && control1.isValid() != control2.isValid()) {
            attributesImpl.addAttribute("", XFormsConstants.VALID_ATTRIBUTE_NAME,
                    XFormsConstants.VALID_ATTRIBUTE_NAME,
                    ContentHandlerHelper.CDATA, Boolean.toString(control2.isValid()));
            added = true;
        }

        // Type attribute
        {
            final String typeValue1 = isNewlyVisibleSubtree ? null : control1.getTypeExplodedQName();
            final String typeValue2 = control2.getTypeExplodedQName();

            if (isNewlyVisibleSubtree || !XFormsUtils.compareStrings(typeValue1, typeValue2)) {
                final String attributeValue = typeValue2 != null ? typeValue2 : "";
                // NOTE: No type is considered equivalent to xs:string or xforms:string
                // TODO: should have more generic code in XForms engine to equate "no type" and "xs:string"
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "type", attributeValue, isNewlyVisibleSubtree,
                        attributeValue.equals("") || XMLConstants.XS_STRING_EXPLODED_QNAME.equals(attributeValue) || XFormsConstants.XFORMS_STRING_EXPLODED_QNAME.equals(attributeValue));
            }
        }

        // Custom MIPs
        added |= addAjaxCustomMIPs(attributesImpl, isNewlyVisibleSubtree, control1, control2);

        return added;
    }

    protected void outputValueElement(ContentHandlerHelper ch, XFormsValueControl xformsValueControl,
                                      boolean doOutputElement, boolean isNewlyVisibleSubtree, Attributes attributesImpl, String elementName) {
        // Create element with text value
        final String value;
        if (xformsValueControl.isRelevant()) {
            // NOTE: Not sure if it is still possible to have a null value when the control is relevant
            final String tempValue = xformsValueControl.getEscapedExternalValue();
            value = (tempValue == null) ? "" : tempValue;
        } else {
            // Some controls don't have "" as non-relevant value
            value = xformsValueControl.getNonRelevantEscapedExternalValue();
        }
        if (doOutputElement || !isNewlyVisibleSubtree || !value.equals("")) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, elementName, attributesImpl);
            if (value.length() > 0)
                ch.text(value);
            ch.endElement();
        }
    }

    // public for unit tests
    public static boolean addAjaxCustomMIPs(AttributesImpl attributesImpl, boolean newlyVisibleSubtree,
                                            XFormsSingleNodeControl control1, XFormsSingleNodeControl control2) {

        boolean added = false;

        final Map<String, String> customMIPs1 = (control1 == null) ? null : control1.getCustomMIPs();
        final Map<String, String> customMIPs2 = control2.getCustomMIPs();

        if (newlyVisibleSubtree || !XFormsSingleNodeControl.compareCustomMIPs(customMIPs1, customMIPs2)) {
            // Custom MIPs changed

            final String attributeValue;
            if (customMIPs1 == null) {
                attributeValue = control2.getCustomMIPsClasses();
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
                added |= addOrAppendToAttributeIfNeeded(attributesImpl, "class", attributeValue, newlyVisibleSubtree, attributeValue.equals(""));
        }
        return added;
    }
}
