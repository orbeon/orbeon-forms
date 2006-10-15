/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.action;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;

import java.util.List;

/**
 * Dispatch XForms actions.
 */
public class XFormsActionInterpreter {

    private XFormsContainingDocument containingDocument;
    private XFormsControls xformsControls;

    public XFormsActionInterpreter(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
        this.xformsControls = containingDocument.getXFormsControls();
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public XFormsControls getXFormsControls() {
        return xformsControls;
    }

    /**
     * Execute an XForms action.
     *
     * @param pipelineContext       current PipelineContext
     * @param targetId              id of the target control
     * @param eventHandlerContainer event handler containe this action is running in
     * @param actionElement         Element specifying the action to execute
     */
    public void runAction(final PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {

        // Check that we understand the action element
        final String actionNamespaceURI = actionElement.getNamespaceURI();
        final String actionName = actionElement.getName();
        if (!XFormsActions.isActionName(actionNamespaceURI, actionName)) {
            throw new OXFException("Invalid action name: " + XMLUtils.buildExplodedQName(actionNamespaceURI, actionName));
        }

        // Handle conditional action (@if / @exf:if)
        final String conditionAttribute;
        {
            final String ifAttribute = actionElement.attributeValue("if");
            if (ifAttribute != null)
                conditionAttribute = ifAttribute;
            else
                conditionAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_IF_ATTRIBUTE_QNAME);
        }

        // Handle iterated action (@while / @exf:while)
        final String iterationAttribute;
        {
            final String whileAttribute = actionElement.attributeValue("while");
            if (whileAttribute != null)
                iterationAttribute = whileAttribute;
            else
                iterationAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_WHILE_ATTRIBUTE_QNAME);
        }

        final String eventHandlerContainerId = eventHandlerContainer.getEffectiveId();
        int iteration = 1;
        while (true) {
            // Check if the conditionAttribute attribute exists and stop if false
            if (conditionAttribute != null) {
                boolean result = evaluateCondition(pipelineContext, eventHandlerContainerId, actionElement, actionName, conditionAttribute, "if");
                if (!result)
                    break;
            }
            // Check if the iterationAttribute attribute exists and stop if false
            if (iterationAttribute != null) {
                boolean result = evaluateCondition(pipelineContext, eventHandlerContainerId, actionElement, actionName, iterationAttribute, "while");
                if (!result)
                    break;
            }

            // Set binding context to action element
            setActionBindingContext(pipelineContext, containingDocument, eventHandlerContainerId, actionElement);

            // We are executing the action
            if (XFormsServer.logger.isDebugEnabled()) {
                if (iterationAttribute == null)
                    XFormsServer.logger.debug("XForms - executing action: " + actionName);
                else
                    XFormsServer.logger.debug("XForms - executing action (iteration " + iteration  +"): " + actionName);
            }

            // Get action and execute it
            final XFormsAction xformsAction = XFormsActions.getAction(actionNamespaceURI, actionName);
            xformsAction.execute(this, pipelineContext, targetId, eventHandlerContainer, actionElement);

            // Stop if there is no iteration
            if (iterationAttribute == null)
                break;

            iteration++;
        }
    }

    private static void setActionBindingContext(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String eventHandlerContainerId, Element actionElement) {

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        // Get "fresh" event handler containier
        final XFormsEventHandlerContainer eventHandlerContainer = (XFormsEventHandlerContainer) containingDocument.getObjectById(pipelineContext, eventHandlerContainerId);

        if (eventHandlerContainer instanceof XFormsControl) {
            // The event handler is contained within a control. Bindings are relative to that control
            xformsControls.setBinding(pipelineContext, (XFormsControl) eventHandlerContainer);
        } else {
            // The event handler is not contained within a control (e.g. model or submission)
            // TODO: Separate context handling from XFormsControls
            if (eventHandlerContainer instanceof XFormsModel) {
                final XFormsModel xformsModel = (XFormsModel) eventHandlerContainer;
                xformsControls.resetBindingContext(xformsModel);
            } else if (eventHandlerContainer instanceof XFormsModelSubmission) {
                final XFormsModelSubmission submission = (XFormsModelSubmission) eventHandlerContainer;
                final XFormsModel xformsModel = (XFormsModel) submission.getParentContainer();
                xformsControls.resetBindingContext(xformsModel);
                xformsControls.pushBinding(pipelineContext, submission.getSubmissionElement());
            } else {
                // TODO: Other possible contexts?
                xformsControls.resetBindingContext();
            }
        }
        xformsControls.pushBinding(pipelineContext, actionElement);
    }

    public void pushContextAttributeIfNeeded(PipelineContext pipelineContext, Element actionElement) {
        final String contextAttribute = actionElement.attributeValue("context");
        if (contextAttribute != null) {
            xformsControls.pushBinding(pipelineContext, null, null, contextAttribute, null, null, null, Dom4jUtils.getNamespaceContextNoDefault(actionElement));
        }
    }

    private boolean evaluateCondition(PipelineContext pipelineContext, String eventHandlerContainerId, Element actionElement,
                                      String actionName, String conditionAttribute, String conditionType) {
        // Set binding context to action element (including @context/@nodeset, @ref, or @bind)
        setActionBindingContext(pipelineContext, containingDocument, eventHandlerContainerId, actionElement);

        // Get into context set by the parent of the action
        // "It contains an XPath 1.0 expression that is evaluated using the in-scope evaluation context before
        // the action is executed"
        xformsControls.popBinding();
        // But the @context attribute actually _replaces_ that context
        pushContextAttributeIfNeeded(pipelineContext, actionElement);
        // TODO: also push @model
        // We are now in the right context to evaluate the condition

        // Don't evaluate the condition if the context has gone missing
        final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
        if (currentSingleNode == null || containingDocument.getInstanceForNode(currentSingleNode) == null) {
            if (XFormsServer.logger.isDebugEnabled())
                XFormsServer.logger.debug("XForms - not executing \"" + conditionType + "\" conditional action (missing context): " + actionName);
            return false;
        }

        final List conditionResult = containingDocument.getEvaluator().evaluate(pipelineContext,
            currentSingleNode, "boolean(" + conditionAttribute + ")",
            Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, xformsControls.getFunctionLibrary(), null);

        if (!((Boolean) conditionResult.get(0)).booleanValue()) {
            // Don't execute action

            if (XFormsServer.logger.isDebugEnabled())
                XFormsServer.logger.debug("XForms - not executing \"" + conditionType + "\" conditional action (condition evaluated to 'false'). Action name: " + actionName + ", condition: " + conditionAttribute);

            return false;
        }

        // Condition is true
        return true;
    }
}