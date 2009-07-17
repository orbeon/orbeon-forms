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
package org.orbeon.oxf.xforms.action.actions;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.saxon.om.Item;

/**
 * Extension xxforms:invalidate-instance action.
 */
public class XXFormsInvalidateInstanceAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        // Evaluate AVTs
        final String resourceURI = resolveAVT(actionInterpreter, propertyContext, actionElement, "resource", false);
        final String handleXIncludeString = resolveAVT(actionInterpreter, propertyContext, actionElement, "xinclude", false);

        if (handleXIncludeString == null) {
            // No @xinclude attribute specified so remove all instances matching @resource
            // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
            XFormsServerSharedInstancesCache.instance().remove(propertyContext, resourceURI, null, true);
            XFormsServerSharedInstancesCache.instance().remove(propertyContext, resourceURI, null, false);
        } else {
            // Just remove instances matching both @resource and @xinclude
            final boolean handleXInclude = Boolean.valueOf(handleXIncludeString);
            // NOTE: For now, we can't individually invalidate instances obtained through POST or PUT
            XFormsServerSharedInstancesCache.instance().remove(propertyContext, resourceURI, null, handleXInclude);
        }
    }
}