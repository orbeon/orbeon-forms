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
package org.orbeon.oxf.xforms;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.xpath.XPathException;

import java.util.*;

/**
 * Represents an XForms containing document.
 *
 * Includes:
 *
 * o models
 * o instances
 * o controls / handlers hierarchy
 */
public class XFormsContainingDocument implements EventTarget {

    private List models;
    private Map modelsMap = new HashMap();
    private XFormsControls xFormsControls;

    public XFormsContainingDocument(List models, Document controlsDocument) {
        this.models = models;

        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            modelsMap.put(model.getId(), model);
        }

        this.xFormsControls = new XFormsControls(this, controlsDocument);
    }

    /**
     * Return model with the specified id, null if not found. If the id is the empty string, return
     * the default model, i.e. the first model.
     */
    public XFormsModel getModel(String id) {
        return (XFormsModel) ("".equals(id) ? models.get(0) : modelsMap.get(id));
    }

    /**
     * Get a list of all the models in this document.
     */
    public List getModels() {
        return models;
    }

    /**
     * Return the controls document.
     */
    private Document getControlsDocument() {
        return xFormsControls.getControlsDocument();
    }

    /**
     * Return the XForms controls.
     */
    public XFormsControls getxFormsControls() {
        return xFormsControls;
    }

    /**
     * Initialize the XForms engine.
     */
    public void initialize(PipelineContext pipelineContext) {
        // NOP for now
    }

    /**
     * Execute an action on control with id controlId and action/event eventName
     */
    public EventResult executeEvent(PipelineContext pipelineContext, String controlId, String eventName, String eventValue) {

        // Create XPath variables
        Map variables = new HashMap();
        variables.put("control-id", controlId);
        variables.put("control-name", eventName);

        // Create XPath expression
        PooledXPathExpression xpathExpression =
            XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(getControlsDocument(), null).wrap(getControlsDocument()),
                    "/xxf:controls//*[@id = $control-id]/xf:*[@ev:event = $control-name]", XFormsServer.XFORMS_NAMESPACES, variables);

        // Get action element
        Element actionElement;
        try {
            actionElement = (Element) xpathExpression.evaluateSingle();
            if (actionElement == null)
                throw new OXFException("Cannot find control with id '" + controlId + "' and event '" + eventName + "'.");
        } catch (XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }

        // Interpret action
        EventResult eventResult = new EventResult();
        interpretEvent(pipelineContext, actionElement, eventResult, eventValue);
        return eventResult;
    }

    private void interpretEvent(final PipelineContext pipelineContext, Element actionElement, EventResult eventResult, String eventValue) {

        String actionNamespaceURI = actionElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        String actionEventName = actionElement.getName();

        if (XFormsEvents.XFORMS_SETVALUE_ACTION.equals(actionEventName)) {
            // 10.1.9 The setvalue Element
            // xforms:setvalue


            // Set binding for current action element
            xFormsControls.setBinding(pipelineContext, actionElement);

            final String value = actionElement.attributeValue("value");
            final String content = actionElement.getStringValue();

            final XFormsInstance instance = xFormsControls.getCurrentInstance();
            final String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                Map namespaceContext = Dom4jUtils.getNamespaceContext(actionElement);
                valueToSet = instance.evaluateXPath(pipelineContext, value, namespaceContext);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            // Set value on current node
            Node currentNode = xFormsControls.getCurrentSingleNode();
            instance.setValueForNode(currentNode, valueToSet);

        } else if (XFormsEvents.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element
            // xforms:toggle

            // Find case with that id and select it
            String caseId = actionElement.attributeValue("case");
            eventResult.addDivToShow(caseId);

            // Deselect other cases in that switch
            {
                Map variables = new HashMap();
                variables.put("case-id", caseId);

                PooledXPathExpression xpathExpression =
                    XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(getControlsDocument(), null).wrap(getControlsDocument()),
                            "/xxf:controls//xf:case[@id = $case-id]/ancestor::xf:switch[1]//xf:case[not(@id = $case-id)]", XFormsServer.XFORMS_NAMESPACES, variables);
                try {

                    for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                        Element caseElement = (Element) i.next();
                        eventResult.addDivToHide(caseElement.attributeValue(new QName("id")));
                    }
                } catch (XPathException e) {
                    throw new OXFException(e);
                } finally {
                    if (xpathExpression != null)
                        xpathExpression.returnToPool();
                }
            }

            // TODO:
            // 1. Dispatching an xforms-deselect event to the currently selected case.
            // 2. Dispatching an xform-select event to the case to be selected.

        } else if (XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE.equals(actionEventName)) {
            // 4.6.7 Sequence: Value Change with Focus Change

            // 1. xforms-recalculate
            // 2. xforms-revalidate
            // 3. [n] xforms-valid/xforms-invalid; xforms-enabled/xforms-disabled; xforms-optional/xforms-required; xforms-readonly/xforms-readwrite
            // 4. xforms-value-changed
            // 5. DOMFocusOut
            // 6. DOMFocusIn
            // 7. xforms-refresh
            // Reevaluation of binding expressions must occur before step 3 above.

            // Set current context
            xFormsControls.setBinding(pipelineContext, actionElement);

            // Set value into the instance
            xFormsControls.getCurrentInstance().setValueForNode(xFormsControls.getCurrentSingleNode(), eventValue);

            // Dispatch events
            XFormsModel model = xFormsControls.getCurrentModel();
            model.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_RECALCULATE);
            model.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_REVALIDATE);

            xFormsControls.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_DOM_FOCUS_OUT, actionElement);
            xFormsControls.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_VALUE_CHANGED, actionElement);

            // TODO
            //xFormsControls.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_DOM_FOCUS_IN, newControlElement);

            model.dispatchEvent(pipelineContext, XFormsEvents.XFORMS_REFRESH);


        } else if (XFormsEvents.XFORMS_ACTION_ACTION.equals(actionEventName)) {
            // 10.1.1 The action Element
            // xforms:action

            for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                interpretEvent(pipelineContext, embeddedActionElement, eventResult, eventValue);
            }

        } else {
            throw new OXFException("Invalid action requested: " + actionEventName);
        }
    }

    public void dispatchEvent(PipelineContext pipelineContext, String eventName) {
        if (XFormsEvents.XXFORMS_INITIALIZE.equals(eventName)) {
            // 4.2 Initialization Events

            // 1. Dispatch xforms-model-construct to all models
            // 2. Dispatch xforms-model-construct-done to all models
            // 3. Dispatch xforms-ready to all models

            final String[] eventsToDispatch = { XFormsEvents.XFORMS_MODEL_CONSTRUCT, XFormsEvents.XFORMS_MODEL_DONE, XFormsEvents.XFORMS_READY };
            for (int i = 0; i < eventsToDispatch.length; i++) {
                if (XFormsEvents.XFORMS_MODEL_DONE.equals(eventsToDispatch[i])) {
                    dispatchEvent(pipelineContext, XFormsEvents.XXFORMS_INITIALIZE_CONTROLS);
                }
                for (Iterator j = getModels().iterator(); j.hasNext();) {
                    XFormsModel model = (XFormsModel) j.next();
                    model.dispatchEvent(pipelineContext, eventsToDispatch[i]);
                }
            }
        } else if (XFormsEvents.XXFORMS_INITIALIZE_CONTROLS.equals(eventName)) {
            // Make sure controls are initialized
            xFormsControls.initialize();
        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
        }
    }

    public static class EventResult {

        private List divsToShow;
        private List divsToHide;

        public void addDivToShow(String divId) {
            if (divsToShow == null)
                divsToShow = new ArrayList();

            divsToShow.add(divId);
        }

        public void addDivToHide(String divId) {
            if (divsToHide == null)
                divsToHide = new ArrayList();
            divsToHide.add(divId);
        }

        public List getDivsToShow() {
            return divsToShow;
        }

        public List getDivsToHide() {
            return divsToHide;
        }
    }
}
