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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:invalidate-instance action.
 */
public class XXFormsInvalidateInstanceAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, XFormsEvent event,
                        XFormsEventObserver eventObserver, Element actionElement,
                        XBLBindingsBase.Scope actionScope, boolean hasOverriddenContext, Item overriddenContext) {

        // Evaluate AVTs
        final String resourceURI = actionInterpreter.resolveAVT(actionElement, "resource");
        final String handleXIncludeString = actionInterpreter.resolveAVT(actionElement, "xinclude");

        // Use XFormsModel logger because it's what's used by XFormsServerSharedInstancesCache in other places
        final IndentedLogger indentedLogger = actionInterpreter.getContainingDocument().getIndentedLogger(XFormsModel.LOGGING_CATEGORY);

        // Resolve as service URL
        // NOTE: BaseSubmission.getAbsoluteSubmissionURL() supports resource URLs and norewrite. Should we?
        final String resolvedResourceURI = XFormsUtils.resolveServiceURL(actionInterpreter.getContainingDocument(), actionElement,
                resourceURI, ExternalContext.Response.REWRITE_MODE_ABSOLUTE);

        if (handleXIncludeString == null) {
            // No @xinclude attribute specified so remove all instances matching @resource
            // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
            XFormsServerSharedInstancesCache.instance().remove(indentedLogger, resolvedResourceURI, null, true);
            XFormsServerSharedInstancesCache.instance().remove(indentedLogger, resolvedResourceURI, null, false);
        } else {
            // Just remove instances matching both @resource and @xinclude
            final boolean handleXInclude = Boolean.valueOf(handleXIncludeString);
            // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
            XFormsServerSharedInstancesCache.instance().remove(indentedLogger, resolvedResourceURI, null, handleXInclude);
        }
    }
}