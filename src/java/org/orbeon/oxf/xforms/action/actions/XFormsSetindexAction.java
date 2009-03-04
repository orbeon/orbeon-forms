/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Iterator;

/**
 * 9.3.7 The setindex Element
 */
public class XFormsSetindexAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventObserver eventObserver, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();

        final String repeatId = XFormsUtils.namespaceId(containingDocument, actionElement.attributeValue("repeat"));
        final String indexXPath = actionElement.attributeValue("index");

        final NodeInfo currentSingleNode = actionInterpreter.getContextStack().getCurrentSingleNode();
        if (currentSingleNode == null)
            return;

        final String indexString = XPathCache.evaluateAsString(pipelineContext,
                actionInterpreter.getContextStack().getCurrentNodeset(), actionInterpreter.getContextStack().getCurrentPosition(),
                "number(" + indexXPath + ")", containingDocument.getNamespaceMappings(actionElement),
                contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                contextStack.getFunctionContext(), null,
                (LocationData) actionElement.getData());

        containingDocument.logDebug("xforms:setindex", "setting index", new String[] { "index", indexString });

        executeSetindexAction(containingDocument, repeatId, indexString);
    }

    protected static void executeSetindexAction(XFormsContainingDocument containingDocument, String repeatId, String indexString) {
        if ("NaN".equals(indexString)) {
            // "If the index evaluates to NaN the action has no effect."
            return;
        }

        final XFormsControls controls = containingDocument.getControls();

        // Find repeat control
        final Object control = controls.resolveObjectById(null, repeatId);// TODO: pass sourceId
        if (control instanceof XFormsRepeatControl) {
            // Set its new index
            final int newRepeatIndex = Integer.parseInt(indexString);
            final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
            if (repeatControl.getIndex() != newRepeatIndex) {

                if (XFormsServer.logger.isDebugEnabled()) {
                    containingDocument.logDebug("repeat", "setting index upon xforms:setfocus",
                            new String[] { "new index", Integer.toString(newRepeatIndex)});
                }

                repeatControl.setIndex(newRepeatIndex);
                
                controls.markDirtySinceLastRequest(true);// NOTE: currently, bindings are not really supposed to contain index() anymore, but control values may change still
                setDeferredFlagsForSetindex(containingDocument);
            }
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:setindex", "index does not refer to an existing xforms:repeat element, ignoring action",
                        new String[] { "repeat id", repeatId } );
        }
    }

    public static void setDeferredFlagsForSetindex(XFormsContainingDocument containingDocument) {
        // XForms 1.1: "This action affects deferred updates by performing deferred update in its initialization and by
        // setting the deferred update flags for recalculate, revalidate and refresh."
        // TODO: Nested containers?
        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();

            // NOTE: We used to do this, following XForms 1.0, but XForms 1.1 has changed the behavior
            //currentModel.getBinds().rebuild(pipelineContext);

            final XFormsModel.DeferredActionContext deferredActionContext = currentModel.getDeferredActionContext();
            deferredActionContext.recalculate = true;
            deferredActionContext.revalidate = true;
            deferredActionContext.refresh = true;
        }
    }
}
