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
public class XFormsContainingDocument implements org.orbeon.oxf.xforms.EventTarget {

    private List models = new ArrayList();
    private Map modelsMap = new HashMap();
    private Document controlsDocument;

    public XFormsContainingDocument(Document controlsDocument) {
        this.controlsDocument = controlsDocument;
    }

    public void addModel(XFormsModel model) {
        models.add(model);
        modelsMap.put(model.getId(), model);
    }

    /**
     * Return model with the specified id, null if not found. If the id is the empty string, return
     * the default model, i.e. the first model.
     */
    public XFormsModel getModel(String id) {
        return (XFormsModel) ("".equals(id) ? models.get(0) : modelsMap.get(id));
    }

    public List getModels() {
        return models;
    }

    /**
     * Initialize the XForms engine.
     */
    public void initialize(PipelineContext pipelineContext) {
    }

    /**
     * Execute an action on control with id controlId and action/event eventName
     */
    public ActionResult executeAction(PipelineContext pipelineContext, String controlId, String eventName) {

        // Create XPath variables
        Map variables = new HashMap();
        variables.put("control-id", controlId);
        variables.put("control-name", eventName);

        // Create XPath expression
        PooledXPathExpression xpathExpression =
            XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument),
                    "/xxf:controls//*[@xxf:id = $control-id]/xf:*[@ev:event = $control-name]", XFormsServer.XFORMS_NAMESPACES, variables);

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
        ActionResult actionResult = new ActionResult();
        interpretAction(pipelineContext, actionElement, controlsDocument, actionResult);
        return actionResult;
    }

    private void interpretAction(final PipelineContext pipelineContext, Element actionElement, Document controlsDocument, ActionResult actionResult) {

        String actionNamespaceURI = actionElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        String actionEventName = actionElement.getName();

        if (XFormsEvents.XFORMS_SETVALUE_ACTION.equals(actionEventName)) {
            // 10.1.9 The setvalue Element
            // xforms:setvalue

            String ref = actionElement.attributeValue("ref");// TODO: support relative refs
            String value = actionElement.attributeValue("value");
            String content = actionElement.getStringValue();

            Map namespaceContext = Dom4jUtils.getNamespaceContext(actionElement);

            org.orbeon.oxf.xforms.XFormsInstance instance = org.orbeon.oxf.xforms.XFormsUtils.getInstanceFromSingleNodeBindingElement(this, actionElement);

            String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                valueToSet = instance.evaluateXPath(pipelineContext, value, namespaceContext);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            instance.setValueForParam(pipelineContext, ref, namespaceContext, valueToSet);
        } else if (XFormsEvents.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element
            // xforms:toggle

            // Find case with that id and select it
            String caseId = actionElement.attributeValue("case");
            actionResult.addDivToShow(caseId);

            // Deselect other cases in that switch
            {
                Map variables = new HashMap();
                variables.put("case-id", caseId);

                PooledXPathExpression xpathExpression =
                    XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument),
                            "/xxf:controls//xf:case[@id = $case-id]/ancestor::xf:switch[1]//xf:case[not(@id = $case-id)]", XFormsServer.XFORMS_NAMESPACES, variables);
                try {

                    for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                        Element caseElement = (Element) i.next();
                        actionResult.addDivToHide(caseElement.attributeValue(new QName("id")));
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


        } else if (XFormsEvents.XFORMS_ACTION_ACTION.equals(actionEventName)) {
            // 10.1.1 The action Element
            // xforms:action

            for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                interpretAction(pipelineContext, embeddedActionElement, controlsDocument, actionResult);
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
                for (Iterator j = getModels().iterator(); j.hasNext();) {
                    XFormsModel model = (XFormsModel) j.next();
                    model.dispatchEvent(pipelineContext, eventsToDispatch[i]);
                }
            }

        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
        }
    }

    public static class ActionResult {

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
