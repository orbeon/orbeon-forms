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

import org.orbeon.dom.Element;
import org.orbeon.oxf.xforms.model.XFormsModel;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.events.XFormsResetEvent;
import org.orbeon.xforms.xbl.Scope;
import org.orbeon.saxon.om.Item;
import scala.Option;

/**
 * 10.1.11 The reset Element
 *
 * TODO: Processing xforms-reset is not actually implemented yet in the model.
 */
public class XFormsResetAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        final Option<XFormsModel> modelOpt = actionInterpreter.actionXPathContext().getCurrentBindingContext().modelOpt();

        // "This action initiates reset processing by dispatching an xforms-reset event to the specified model."
        if (modelOpt.isDefined())
            Dispatch.dispatchEvent(new XFormsResetEvent(modelOpt.get()));
    }
}
