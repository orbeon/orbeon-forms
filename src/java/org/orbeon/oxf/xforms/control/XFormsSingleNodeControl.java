/**
 * Copyright (C) 2009 Orbeon, Inc.
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
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.FastStringBuffer;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Control with a single-node binding (possibly optional). Such controls can have MIPs.
 * 
 * @noinspection SimplifiableIfStatement
 */
public abstract class XFormsSingleNodeControl extends XFormsControl {

    // Bound node
    private NodeInfo boundNode;

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
    public void setBindingContext(PropertyContext propertyContext, XFormsContextStack.BindingContext bindingContext) {

        // Keep binding context
        super.setBindingContext(propertyContext, bindingContext);

        // Set bound node, only considering actual bindings with @bind, @ref or @nodeset
        if (bindingContext.isNewBind())
            this.boundNode = bindingContext.getSingleNode();
    }

    /**
     * Return the node to which the control is bound, if any. If the control is not bound to any node, return null. If
     * the node to which the control no longer exists, return null.
     *
     * @return bound node or null
     */
    public NodeInfo getBoundNode() {
        return boundNode;
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
                    // Control is not bound to a node - it becomes non-relevant
                    // TODO: We could probably optimize and not even *create* the control object and its descendants
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
        final boolean parentRelevant = super.computeRelevant();

        final Item currentItem = bindingContext.getSingleItem();
        if (bindingContext.isNewBind()) {
            if (currentItem instanceof NodeInfo) {
                final NodeInfo currentNodeInfo = (NodeInfo) currentItem;
                // Control is bound to a node - get model item properties
                return parentRelevant && InstanceData.getInheritedRelevant(currentNodeInfo);
            } else {
                // Control is not bound to a node - it becomes non-relevant
                return false;
            }
        } else {
            // Control is not bound to a node because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
            return parentRelevant;
        }
    }

    @Override
    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {

        if (obj == null || !(obj instanceof XFormsSingleNodeControl))
            return false;

        if (this == obj)
            return true;

        final XFormsSingleNodeControl other = (XFormsSingleNodeControl) obj;

        // Make sure the MIPs are up to date before comparing them
        getMIPsIfNeeded();
        other.getMIPsIfNeeded();

        // Standard MIPs
        if (readonly != other.readonly)
            return false;
        if (required != other.required)
            return false;
        if (valid != other.valid)
            return false;

        // Custom MIPs
        if (!compareCustomMIPs(customMIPs, other.customMIPs))
            return false;

        // Type
        if (!XFormsUtils.compareStrings(type, other.type))
            return false;

        return super.equalsExternal(pipelineContext, obj);
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
}
