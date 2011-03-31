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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.events.XFormsRefreshEvent;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;

/**
 * 10.1.6 The refresh Element
 */
public class XFormsRefreshAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindings.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final XBLContainer container = actionInterpreter.getXBLContainer();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String modelId = actionElement.attributeValue(XFormsConstants.MODEL_QNAME);
        final XFormsModel model = actionInterpreter.resolveModel(actionElement, modelId);

        if (model == null)
            throw new ValidationException("Invalid model id: " + modelId, (LocationData) actionElement.getData());

        // NOTE: We no longer need to force the refresh flag here because the refresh flag is global. If a change in any
        // model occurred, then the flag will be already set and we are safe. Otherwise, it is safe not to do anything.
        container.dispatchEvent(new XFormsRefreshEvent(containingDocument, model));
    }
}
