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
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.events.XFormsRebuildEvent;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.3 The rebuild Element
 */
public class XFormsRebuildAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.containingDocument();

        final String modelId = actionElement.attributeValue(XFormsConstants.MODEL_QNAME);
        final XFormsModel model = actionInterpreter.resolveModel(actionElement, modelId);

        // Because of inter-model dependencies, we consider for now that the action must force the operation
        model.getDeferredActionContext().rebuild = true;
        Dispatch.dispatchEvent(new XFormsRebuildEvent(containingDocument, model));
    }
}
