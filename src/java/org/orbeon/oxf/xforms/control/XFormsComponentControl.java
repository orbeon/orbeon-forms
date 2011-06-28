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
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

/**
 * Control that represents a custom components.
 *
 * A component control contains a nested container, which handles:
 *
 * o models nested within component (which we are not 100% happy with as models should be allowed in other places)
 * o HOWEVER this might still be all right for models within xbl:implementation if any
 * o event dispatching
 */
public class XFormsComponentControl extends XFormsNoSingleNodeContainerControl {

    private XBLContainer nestedContainer;

    public XFormsComponentControl(XBLContainer container, XFormsControl parent, Element element, String name, String effectiveId) {
        super(container, parent, element, name, effectiveId);

        // Create container and nested models if any
        nestedContainer = container.createChildContainer(this);
        nestedContainer.addAllModels();// NOTE: there may or may not be nested models

        // Make sure there is location data
        nestedContainer.setLocationData(getLocationData());
    }

    @Override
    // TODO: we should not override this, but currently due to the way XFormsContextStack works with XBL, even non-relevant
    // XFormsComponentControl expect the binding to be set.
    public void setBindingContext(XFormsContextStack.BindingContext bindingContext) {
        super.setBindingContext(bindingContext);

        nestedContainer.setBindingContext(bindingContext);
        nestedContainer.getContextStack().resetBindingContext();
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        // Control is newly created

        if (containingDocument.isRestoringDynamicState()) {
            // Restore models
            nestedContainer.restoreModelsState();
        } else {
            // Start models initialization
            nestedContainer.initializeModels(new String[] {
                    XFormsEvents.XFORMS_MODEL_CONSTRUCT,
                    XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE
            });
        }
        nestedContainer.getContextStack().resetBindingContext();
    }

    @Override
    protected void onBindingUpdate(XFormsContextStack.BindingContext oldBinding, XFormsContextStack.BindingContext newBinding) {
        super.onBindingUpdate(oldBinding, newBinding);

        final boolean isNodesetChange = !compareNodesets(oldBinding.getNodeset(), newBinding.getNodeset());
        if (isNodesetChange) {
            // Control's binding changed
            // NOP for now
        }
    }

    @Override
    public void childrenAdded() {
        super.childrenAdded();

        // It doesn't seem to make much sense to dispatch xforms-ready to nested models. If we still did want to do
        // that, we should do it once ALL controls have been initialized. But likely this is not a good idea
        // either.
    }

    public XBLContainer getNestedContainer() {
        return nestedContainer;
    }

    @Override
    public void updateEffectiveId() {

        // This is called iif the iteration index changes

        // Update rest of control tree
        super.updateEffectiveId();

        // Update container with new effective id
        nestedContainer.updateEffectiveId(getEffectiveId());
    }

    @Override
    public void iterationRemoved() {
        // Inform descendants
        super.iterationRemoved();

        // Destroy container and models if any
        nestedContainer.destroy();
    }
}
