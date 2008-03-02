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
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsControls;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;

import java.util.Map;

/**
 * 9.2.3 The toggle Element
 */
public class XFormsToggleAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId,
                        XFormsEventHandlerContainer eventHandlerContainer, Element actionElement,
                        boolean hasOverriddenContext, Item overriddenContext) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();
        final XFormsContextStack contextStack = actionInterpreter.getContextStack();
        final XFormsContextStack.BindingContext bindingContext = contextStack.getCurrentBindingContext();

        final String caseAttribute = actionElement.attributeValue("case");
        if (caseAttribute == null)
            throw new OXFException("Missing mandatory case attribute on xforms:toggle element.");

        final String caseId;
        if (bindingContext.getSingleNode() != null) {
            final Map prefixToURIMap = containingDocument.getStaticState().getNamespaceMappings(actionElement.attributeValue("id"));
            final LocationData locationData = (LocationData) actionElement.getData();
            final String resolvedCaseId = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, bindingContext.getSingleNode(),
                    contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(), actionInterpreter.getFunctionContext(), prefixToURIMap, locationData, caseAttribute);
            caseId = XFormsUtils.namespaceId(containingDocument, resolvedCaseId);
        } else {
            // TODO: Presence of context is not the right way to decide whether to evaluate AVTs or not
            caseId = caseAttribute;
        }

        final String effectiveCaseId = xformsControls.findEffectiveCaseId(caseId);

        if (effectiveCaseId != null) { // can be null if the switch is not relevant
            // Update xforms:switch info and dispatch events
            xformsControls.activateCase(pipelineContext, effectiveCaseId);
        } else {
            // "If there is a null search result for the target object and the source object is an XForms action such as
            // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("xforms:toggle", "case does not refer to an existing xforms:case element, ignoring action",
                        new String[] { "case id", caseId } );
        }
    }
}
