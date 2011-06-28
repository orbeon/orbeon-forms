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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.events.XFormsRecalculateEvent;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.4 The recalculate Element
 */
public class XFormsRecalculateAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XBLContainer container = actionInterpreter.getXBLContainer();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String modelId = actionElement.attributeValue(XFormsConstants.MODEL_QNAME);
        final XFormsModel model = actionInterpreter.resolveModel(actionElement, modelId);

        if (model == null)
            throw new ValidationException("Invalid model id: " + modelId, (LocationData) actionElement.getData());

        // @xxforms:defaults
        final boolean applyInitialValues; {
            final String applyInitialValuesString = actionInterpreter.resolveAVT(actionElement, XFormsConstants.XXFORMS_DEFAULTS_QNAME);
            applyInitialValues = Boolean.parseBoolean(applyInitialValuesString);
        }

        // Because of inter-model dependencies, we consider for now that the action must force the operation
        model.getDeferredActionContext().recalculate = true;
        container.dispatchEvent(new XFormsRecalculateEvent(containingDocument, model, applyInitialValues));
    }
}
