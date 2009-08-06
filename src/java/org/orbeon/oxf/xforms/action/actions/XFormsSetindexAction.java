/**
 * Copyright (C) 2009 Orbeon, Inc.
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
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

/**
 * 9.3.7 The setindex Element
 */
public class XFormsSetindexAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PropertyContext propertyContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        final String repeatId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("repeat"));
        final String indexXPath = actionElement.attributeValue("index");

        final NodeInfo currentSingleNode = actionInterpreter.getContextStack().getCurrentSingleNode();
        if (currentSingleNode == null)
            return;

        final String indexString = XPathCache.evaluateAsString(propertyContext,
                actionInterpreter.getContextStack().getCurrentNodeset(), actionInterpreter.getContextStack().getCurrentPosition(),
                "number(" + indexXPath + ")", actionInterpreter.getNamespaceMappings(actionElement),
                contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                contextStack.getFunctionContext(), null,
                (LocationData) actionElement.getData());

        actionInterpreter.getIndentedLogger().logDebug("xforms:setindex", "setting index", "index", indexString);

        executeSetindexAction(containingDocument, actionInterpreter.getXBLContainer(), actionInterpreter.getIndentedLogger(), repeatId, indexString);
    }

    protected static void executeSetindexAction(XFormsContainingDocument containingDocument, XBLContainer container, IndentedLogger indentedLogger, String repeatStaticId, String indexString) {
        if ("NaN".equals(indexString)) {
            // "If the index evaluates to NaN the action has no effect."
            return;
        }

        // Find repeat control
        final Object control = container.resolveObjectById(null, repeatStaticId);// TODO: pass sourceId
        if (control instanceof XFormsRepeatControl) {
            // Set its new index
            final int newRepeatIndex = Integer.parseInt(indexString);
            final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
            if (repeatControl.getIndex() != newRepeatIndex) {

                if (indentedLogger.logger.isDebugEnabled()) {
                    indentedLogger.logDebug("xforms:setindex", "setting index upon xforms:setfocus",
                            "new index", Integer.toString(newRepeatIndex));
                }

                repeatControl.setIndex(newRepeatIndex);

                final XFormsControls controls = containingDocument.getControls();
                controls.markDirtySinceLastRequest(true);
                container.setDeferredFlagsForSetindex();
            }
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("xforms:setindex", "index does not refer to an existing xforms:repeat element, ignoring action",
                        "repeat id", repeatStaticId);
        }
    }
}
