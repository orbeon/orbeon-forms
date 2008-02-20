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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.Item;

import java.util.List;
import java.util.Collections;

/**
 * Execute a top-level XForms action and the included nested actions if any.
 */
public class XFormsActionInterpreter {

    private XFormsContainingDocument containingDocument;

    private XFormsControls xformsControls;
    private XFormsContextStack contextStack;

    public XFormsActionInterpreter(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {
        this.containingDocument = containingDocument;

        this.xformsControls = containingDocument.getXFormsControls();
        this.contextStack = new XFormsContextStack(containingDocument);

        // Set context on top-level action
        setActionBindingContext(pipelineContext, containingDocument, eventHandlerContainer.getEffectiveId(), actionElement);
    }

    private void setActionBindingContext(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String eventHandlerContainerId, Element actionElement) {
        // Get "fresh" event handler container
        final XFormsEventHandlerContainer eventHandlerContainer = (XFormsEventHandlerContainer) containingDocument.getObjectById(pipelineContext, eventHandlerContainerId);

        // Set context on container element
        contextStack.setBinding(pipelineContext, eventHandlerContainer);

        // Push binding for outermost action
        contextStack.pushBinding(pipelineContext, actionElement);
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public XFormsControls getXFormsControls() {
        return xformsControls;
    }

    public XFormsContextStack getContextStack() {
        return contextStack;
    }

    public XFormsFunction.Context getFunctionContext() {
        return contextStack.getFunctionContext();
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
            throw new ValidationException("Invalid action: " + XMLUtils.buildExplodedQName(actionNamespaceURI, actionName),
                    new ExtendedLocationData((LocationData) actionElement.getData(), "running XForms action", actionElement,
                            new String[] { "action name", XMLUtils.buildExplodedQName(actionNamespaceURI, actionName) }));
        }

        try {
            // Handle conditional action (@if / @exf:if)
            final String ifConditionAttribute;
            {
                final String ifAttribute = actionElement.attributeValue("if");
                if (ifAttribute != null)
                    ifConditionAttribute = ifAttribute;
                else
                    ifConditionAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_IF_ATTRIBUTE_QNAME);
            }

            // Handle iterated action (@while / @exf:while)
            final String whileIterationAttribute;
            {
                final String whileAttribute = actionElement.attributeValue("while");
                if (whileAttribute != null)
                    whileIterationAttribute = whileAttribute;
                else
                    whileIterationAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_WHILE_ATTRIBUTE_QNAME);
            }

            // Handle iterated action (@xxforms:iterate / @exf:iterate)
            final String iterateIterationAttribute;
            {
                final String xxformsIterateAttribute = actionElement.attributeValue(XFormsConstants.XXFORMS_ITERATE_ATTRIBUTE_QNAME);
                if (xxformsIterateAttribute != null)
                    iterateIterationAttribute = xxformsIterateAttribute;
                else
                    iterateIterationAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_ITERATE_ATTRIBUTE_QNAME);
            }

            // NOTE: At this point, the context has already been set to the current action element

            if (iterateIterationAttribute != null) {
                // Gotta iterate

                // We have to restore the context to the in-scope evaluation context, then push @model/@context/@iterate
                final XFormsContextStack.BindingContext actionBindingContext = contextStack.popBinding();
                pushIterateAttribute(pipelineContext, actionElement, iterateIterationAttribute);
                {
                    final int iterationCount = contextStack.getCurrentNodeset().size();
                    for (int index = 1; index <= iterationCount; index++) {
                        contextStack.pushIteration(index);

                        final Item overriddenContextNodeInfo = contextStack.getCurrentSingleItem();
                        runSingleIteration(pipelineContext, targetId, eventHandlerContainer, actionElement, actionNamespaceURI,
                                actionName, ifConditionAttribute, whileIterationAttribute, true, overriddenContextNodeInfo);

                        contextStack.popBinding();
                    }

                }
                // Restore context stack
                contextStack.popBinding();
                contextStack.restoreBinding(actionBindingContext);
            } else {
                // Do a single iteration run (but this may repeat over the @while condition!)

                runSingleIteration(pipelineContext, targetId, eventHandlerContainer, actionElement, actionNamespaceURI,
                        actionName, ifConditionAttribute, whileIterationAttribute, contextStack.hasOverriddenContext(), contextStack.getContextItem());
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData((LocationData) actionElement.getData(), "running XForms action", actionElement,
                    new String[] { "action name", XMLUtils.buildExplodedQName(actionNamespaceURI, actionName) }));
        }
    }

    private void runSingleIteration(PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer,
                                    Element actionElement, String actionNamespaceURI, String actionName, String ifConditionAttribute,
                                    String whileIterationAttribute, boolean hasOverriddenContext, Item overriddenContextItem) {

        // The context is now the overridden context
        int whileIteration = 1;
        while (true) {
            // Check if the conditionAttribute attribute exists and stop if false
            if (ifConditionAttribute != null) {
                boolean result = evaluateCondition(pipelineContext, actionElement, actionName, ifConditionAttribute, "if", hasOverriddenContext, overriddenContextItem);
                if (!result)
                    break;
            }
            // Check if the iterationAttribute attribute exists and stop if false
            if (whileIterationAttribute != null) {
                boolean result = evaluateCondition(pipelineContext, actionElement, actionName, whileIterationAttribute, "while", hasOverriddenContext, overriddenContextItem);
                if (!result)
                    break;
            }

            // We are executing the action
            if (XFormsServer.logger.isDebugEnabled()) {
                if (whileIterationAttribute == null)
                    containingDocument.logDebug("action", "executing", new String[] { "action name", actionName });
                else
                    containingDocument.logDebug("action", "executing", new String[] { "action name", actionName, "while iteration", Integer.toString(whileIteration) });
            }

            // Get action and execute it
            final XFormsAction xformsAction = XFormsActions.getAction(actionNamespaceURI, actionName);
            containingDocument.startHandleOperation();
            xformsAction.execute(this, pipelineContext, targetId, eventHandlerContainer, actionElement, hasOverriddenContext, overriddenContextItem);
            containingDocument.endHandleOperation();

            // Stop if there is no iteration
            if (whileIterationAttribute == null)
                break;

            // If we repeat, we must re-evaluate the action binding.
            // For example:
            //   <xforms:delete nodeset="/*/foo[1]" while="/*/foo"/>
            // In that case, in the second iteration, xforms:repeat must find an up-to-date nodeset
            // NOTE: There is still the possibility that parent bindings will be out of date. What should be done there?
            contextStack.popBinding();
            contextStack.pushBinding(pipelineContext, actionElement);

            whileIteration++;
        }
    }

    public void pushIterateAttribute(PipelineContext pipelineContext, Element actionElement, String iterateAttribute) {
        final String contextAttribute = actionElement.attributeValue("context");
        final String modelAttribute = actionElement.attributeValue("model");
        contextStack.pushBinding(pipelineContext, null, contextAttribute, iterateAttribute, modelAttribute, null, actionElement,Dom4jUtils.getNamespaceContextNoDefault(actionElement));
    }

    private boolean evaluateCondition(PipelineContext pipelineContext, Element actionElement,
                                      String actionName, String conditionAttribute, String conditionType,
                                      boolean hasOverriddenContext, Item overriddenContextItem) {

        // Execute condition relative to the overridden context if it exists, or the in-scope context if not
        final List contextNodeset;
        final int contextPosition;
        {
            if (hasOverriddenContext && overriddenContextItem != null) {
                // Use provided context item
                contextNodeset = Collections.singletonList(overriddenContextItem);
                contextPosition = 1;
            } else if (hasOverriddenContext && overriddenContextItem == null) {
                // Use empty context
                contextNodeset = Collections.EMPTY_LIST;
                contextPosition = 0;
            } else {
                // Use regular context
                contextNodeset = contextStack.getCurrentNodeset();
                contextPosition = contextStack.getCurrentPosition();
            }
        }

        // Don't evaluate the condition if the context has gone missing
        {
            if (contextNodeset.size() == 0 || containingDocument.getInstanceForNode((NodeInfo) contextNodeset.get(contextPosition - 1)) == null) {
                if (XFormsServer.logger.isDebugEnabled())
                    containingDocument.logDebug("action", "not executing", new String[] { "action name", actionName, "condition type", conditionType, "reason", "missing context" });
                return false;
            }
        }

        final List conditionResult = XPathCache.evaluate(pipelineContext,
                contextNodeset, contextPosition, "boolean(" + conditionAttribute + ")",
            Dom4jUtils.getNamespaceContextNoDefault(actionElement), null, XFormsContainingDocument.getFunctionLibrary(),
            contextStack.getFunctionContext(), null, (LocationData) actionElement.getData());

        if (!((Boolean) conditionResult.get(0)).booleanValue()) {
            // Don't execute action

            if (XFormsServer.logger.isDebugEnabled())
                containingDocument.logDebug("action", "not executing", new String[] { "action name", actionName, "condition type", conditionType, "reason", "condition evaluated to 'false'", "condition", conditionAttribute });

            return false;
        } else {
            // Condition is true
            return true;
        }
    }
}