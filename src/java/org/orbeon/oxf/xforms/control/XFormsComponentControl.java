/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
import org.orbeon.oxf.xforms.XFormsContainer;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;

/**
 * Control that represents a custom components.
 */
public class XFormsComponentControl extends XFormsNoSingleNodeContainerControl {

    private XFormsContainer nestedContainer;

    public XFormsComponentControl(XFormsContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);

        // Create container and nested models if any
        nestedContainer = container.createChildContainer(effectiveId);
        nestedContainer.addAllModels();// NOTE: there may or may not be nested models

        // Make sure there is location data
        nestedContainer.setLocationData(XFormsUtils.getNodeLocationData(element));
    }

    public void setBindingContext(PipelineContext pipelineContext, XFormsContextStack.BindingContext bindingContext) {
        final boolean isNewBinding = getBindingContext() == null;
        final boolean isNodesetChange = isNewBinding|| !compareNodesets(getBindingContext().getNodeset(), bindingContext.getNodeset());

        // Set/update binding context on control
        super.setBindingContext(pipelineContext, bindingContext);

        nestedContainer.setBindingContext(bindingContext);
        nestedContainer.getContextStack().resetBindingContext(pipelineContext);

        // Set/update binding context on container
        if (isNewBinding) {
            // Control is newly bound

            if (containingDocument.isRestoringDynamicState(pipelineContext)) {
                // Restore models
                nestedContainer.restoreModelsState(pipelineContext);
            } else {
                // Initialize models
                nestedContainer.initializeModels(pipelineContext);
            }
        } else if (isNodesetChange) {
            // Control's binding changed
            
        }
    }

    public XFormsContainer getNestedContainer() {
        return nestedContainer;
    }

    public void updateEffectiveId() {

        // This is called iif the iteration index changes

        // Update rest of control tree
        super.updateEffectiveId();

        // Update container with new effective id
        nestedContainer.updateEffectiveId(getEffectiveId());
    }

    public void iterationRemoved(PipelineContext pipelineContext) {
        // Inform descendants
        super.iterationRemoved(pipelineContext);

        // Destroy container and models if any
        nestedContainer.destroy(pipelineContext);
    }
}
