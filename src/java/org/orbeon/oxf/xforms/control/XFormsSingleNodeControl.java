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

import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.saxon.om.NodeInfo;
import org.dom4j.Element;

/**
 * Control with a single-node binding (possibly optional). Such controls can have MIPs.
 * 
 * @noinspection SimplifiableIfStatement
 */
public class XFormsSingleNodeControl extends XFormsControl {

    protected boolean mipsRead;
    protected boolean readonly;
    protected boolean required;
    protected boolean relevant;
    protected boolean valid;
    protected String type;

    public XFormsSingleNodeControl(XFormsContainingDocument containingDocument, XFormsControl parent, Element element, String name, String effectiveId) {
        super(containingDocument, parent, element, name, effectiveId);
    }

    public boolean isReadonly() {
        getMIPsIfNeeded();
        return readonly;
    }

    public boolean isRelevant() {
        getMIPsIfNeeded();
        return relevant;
    }

    public boolean isRequired() {
        getMIPsIfNeeded();
        return required;
    }

    public String getType() {
        getMIPsIfNeeded();
        return type;
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
            final NodeInfo currentNodeInfo = bindingContext.getSingleNode();
            if (bindingContext.isNewBind()) {
                if (currentNodeInfo != null) {
                    // Control is bound to a node - get model item properties
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
                this.relevant = (currentNodeInfo != null) ? InstanceData.getInheritedRelevant(currentNodeInfo) : false; // inherit relevance anyway
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
