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
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.model.XFormsModel;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.xforms.xbl.Scope;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxf:invalidate-instance action.
 */
public class XXFormsInvalidateInstanceAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, Element actionElement,
                        Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        // Evaluate AVTs
        final String resourceURI = actionInterpreter.resolveAVT(actionElement, "resource");
        final String handleXIncludeString = actionInterpreter.resolveAVT(actionElement, "xinclude");

        // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
        final IndentedLogger indentedLogger = actionInterpreter.containingDocument().getIndentedLogger(XFormsModel.LoggingCategory());

        if (handleXIncludeString == null) {
            // No @xinclude attribute specified so remove all instances matching @resource
            // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
            XFormsServerSharedInstancesCache.remove(resourceURI, null, true, indentedLogger);
            XFormsServerSharedInstancesCache.remove(resourceURI, null, false, indentedLogger);
        } else {
            // Just remove instances matching both @resource and @xinclude
            final boolean handleXInclude = Boolean.valueOf(handleXIncludeString);
            // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
            XFormsServerSharedInstancesCache.remove(resourceURI, null, handleXInclude, indentedLogger);
        }
    }
}