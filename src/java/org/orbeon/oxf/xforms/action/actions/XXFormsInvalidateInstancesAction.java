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
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:invalidate-instances action.
 */
public class XXFormsInvalidateInstancesAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
        final IndentedLogger indentedLogger = actionInterpreter.getContainingDocument().getIndentedLogger(XFormsModel.LOGGING_CATEGORY);
        XFormsServerSharedInstancesCache.instance().removeAll(indentedLogger);
    }
}