/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.FastStringBuffer;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Control with a single-node binding (possibly optional). Such controls can have MIPs.
 * 
 * @noinspection SimplifiableIfStatement
 */
public abstract class XFormsSingleNodeControl extends XFormsControl {

    // Whether MIPs have been read from the node
    private boolean mipsRead;

    // Standard MIPs
    private boolean readonly;
    private boolean required;
    private boolean relevant;
    private boolean valid;

    // Custom MIPs
    private Map customMIPs;
    private String customMIPsAsString;

    // Type
    private String type;

    public XFormsSingleNodeControl(XFormsContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    public void markDirty() {
        super.markDirty();
        mipsRead = false;
        customMIPsAsString = null;
    }

    public boolean isReadonly() {
        getMIPsIfNeeded();
        return readonly;
    }

    public boolean isRelevant() {
        getMIPsIfNeeded();
        return relevant;
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

    public boolean isRequired() {
        getMIPsIfNeeded();
        return required;
    }

    public String getType() {
        getMIPsIfNeeded();
        return type;
    }

    public Map getCustomMIPs() {
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

                for (Iterator iterator = customMIPs.entrySet().iterator(); iterator.hasNext();) {
                    final Map.Entry entry = (Map.Entry) iterator.next();
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

    protected void evaluate(PipelineContext pipelineContext) {
        super.evaluate(pipelineContext);
        getMIPsIfNeeded();
    }

    protected void getMIPsIfNeeded() {
        if (!mipsRead) {
            final Item currentItem = bindingContext.getSingleItem();
            if (bindingContext.isNewBind()) {
                if (currentItem instanceof NodeInfo) {
                    // Control is bound to a node - get model item properties
                    final NodeInfo currentNodeInfo = (NodeInfo) currentItem;
                    this.readonly = InstanceData.getInheritedReadonly(currentNodeInfo);
                    this.required = InstanceData.getRequired(currentNodeInfo);
                    this.relevant = InstanceData.getInheritedRelevant(currentNodeInfo);
                    this.valid = InstanceData.getValid(currentNodeInfo);
                    this.type = InstanceData.getType(currentNodeInfo);

                    // Custom MIPs
                    this.customMIPs = InstanceData.getAllCustom(currentNodeInfo);
                    if (this.customMIPs != null)
                        this.customMIPs = new HashMap(this.customMIPs);

                    // Handle global read-only setting
                    if (XFormsProperties.isReadonly(containingDocument))
                        this.readonly = true;
                } else {
                    // Control is not bound to a node - it becomes non-relevant
                    // TODO: We could probably optimize and not even *create* the control object and its descendants
                    this.readonly = false;
                    this.required = false;
                    this.relevant = false;
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

                final XFormsControl parent = getParent();
                if (parent instanceof XFormsSingleNodeControl) {
                    // Inherit relevance based on outer control
                    this.relevant = ((XFormsSingleNodeControl) parent).isRelevant();
                } else if (parent instanceof XFormsRepeatControl) {
                    // Must not happen because a repeat iteration is always bound to a node
                    throw new OXFException("Unexpected parent control class: " + parent.getClass().getName());
                } else if (parent instanceof XFormsNoSingleNodeContainerControl) {
                    // Includes dialog, case, and component
                    // TODO: those must have special relevance handling
                    this.relevant = true;
                } else if (parent == null) {
                    // This means we are at the top-level, therefore we are relevant
                    this.relevant = true;
                } else {
                    // Must not happen
                    throw new OXFException("Unexpected parent control class: " + parent.getClass().getName());
                }

                //this.relevant = (currentItem instanceof NodeInfo) ? InstanceData.getInheritedRelevant((NodeInfo) currentItem) : false; // inherit relevance anyway
            }
            mipsRead = true;
        }
    }

    public boolean equalsExternal(PipelineContext pipelineContext, XFormsControl obj) {

        if (obj == null || !(obj instanceof XFormsSingleNodeControl))
            return false;

        if (this == obj)
            return true;

        final XFormsSingleNodeControl other = (XFormsSingleNodeControl) obj;

        // Make sure the MIPs are up to date before comparing them
        getMIPsIfNeeded();
        other.getMIPsIfNeeded();;

        // Stanard MIPs
        if (readonly != other.readonly)
            return false;
        if (required != other.required)
            return false;
        if (relevant != other.relevant)
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

    public boolean isStaticReadonly() {
        // Static read-only if we are read-only and static (global or local setting)
        return isReadonly()
                && (XFormsProperties.isStaticReadonlyAppearance(containingDocument)
                    || XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE.equals(getControlElement().attributeValue(XFormsConstants.XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME)));
    }
}
