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
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.action.XFormsAction;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 10.1.9 The setvalue Element
 */
public class XFormsSetvalueAction extends XFormsAction {
    public void execute(XFormsActionInterpreter actionInterpreter, PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        final XFormsControls xformsControls = actionInterpreter.getXFormsControls();
        final XFormsContainingDocument containingDocument = actionInterpreter.getContainingDocument();

        final String value = actionElement.attributeValue("value");
        final String content = actionElement.getStringValue();

        final String valueToSet;
        if (value != null) {
            // Value to set is computed with an XPath expression
            final Map namespaceContext = Dom4jUtils.getNamespaceContextNoDefault(actionElement);

            final List currentNodeset;
            {
                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();// TODO: we should not use this
                currentNodeset = (xformsControls.getCurrentNodeset() != null && xformsControls.getCurrentNodeset().size() > 0)
                        ? xformsControls.getCurrentNodeset()
                        : Collections.singletonList(currentInstance.getInstanceDocumentInfo());

                // NOTE: The above is actually not correct: the context should not become null or empty. This is
                // therefore just a workaround for a bug we hit:

                // o Do 2 setvalue in sequence
                // o The first one changes the context around the control containing the actions
                // o When the second one runs, context is empty, and setvalue either crashes or does nothing
                //
                // The correct solution is probably to NOT reevaluate the context of actions unless a rebuild is done.
                // This would require an update to the way we impelement the processing model.
            }

            valueToSet = containingDocument.getEvaluator().evaluateAsString(pipelineContext,
                    currentNodeset, xformsControls.getCurrentPosition(),
                    value, namespaceContext, null, xformsControls.getFunctionLibrary(), null);
        } else {
            // Value to set is static content
            valueToSet = content;
        }

        // Set value on current node
        final NodeInfo currentNode = xformsControls.getCurrentSingleNode();
        if (currentNode != null) {
            // Node exists, we can try to set the value
            doSetValue(pipelineContext, containingDocument, currentNode, valueToSet);
        } else {
            // Node doesn't exist, don't do anything
            // NOP
        }
    }

    public static boolean doSetValue(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, NodeInfo currentNode, String valueToSet) {
        final String currentValue = XFormsInstance.getValueForNode(currentNode);
        if (!currentValue.equals(valueToSet)) {// TODO: Check if we are allowed to do this optimization
            XFormsInstance.setValueForNodeInfo(pipelineContext, currentNode, valueToSet, null);

            final XFormsInstance modifiedInstance = containingDocument.getInstanceForNode(currentNode);
            final XFormsModel.DeferredActionContext deferredActionContext = modifiedInstance.getModel().getDeferredActionContext();

            // "XForms Actions that change only the value of an instance node results in setting the flags for
            // recalculate, revalidate, and refresh to true and making no change to the flag for rebuild".
            deferredActionContext.recalculate = true;
            deferredActionContext.revalidate = true;
            deferredActionContext.refresh = true;

            containingDocument.getXFormsControls().markDirty();

            return true;
        } else {
            return false;
        }
    }
}
