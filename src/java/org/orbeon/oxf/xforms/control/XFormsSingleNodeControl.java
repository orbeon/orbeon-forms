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
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.Item;

/**
 * Control with a single-node binding (possibly optional). Such controls can have MIPs.
 * 
 * @noinspection SimplifiableIfStatement
 */
public abstract class XFormsSingleNodeControl extends XFormsControl {

    private boolean mipsRead;
    private boolean readonly;
    private boolean required;
    private boolean relevant;
    private boolean valid;
    private String type;

    public XFormsSingleNodeControl(XFormsContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);
    }

    public void markDirty() {
        super.markDirty();
        mipsRead = false;
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
                }
            } else {
                // Control is not bound to a node because it doesn't have a binding (group, trigger, dialog, etc. without @ref)
                this.readonly = false;
                this.required = false;
                this.relevant = (currentItem instanceof NodeInfo) ? InstanceData.getInheritedRelevant((NodeInfo) currentItem) : false; // inherit relevance anyway
                this.valid = true;// by default, a control is not invalid
                this.type = null;
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

        if (readonly != other.readonly)
            return false;
        if (required != other.required)
            return false;
        if (relevant != other.relevant)
            return false;
        if (valid != other.valid)
            return false;

        if (!XFormsUtils.compareStrings(type, other.type))
            return false;

        return super.equalsExternal(pipelineContext, obj);
    }

    public boolean isStaticReadonly() {
        // Static read-only if we are read-only and static (global or local setting)
        return isReadonly()
                && (XFormsProperties.isStaticReadonlyAppearance(containingDocument)
                    || XFormsProperties.READONLY_APPEARANCE_STATIC_VALUE.equals(getControlElement().attributeValue(XFormsConstants.XXFORMS_READONLY_APPEARANCE_ATTRIBUTE_QNAME)));
    }
}
