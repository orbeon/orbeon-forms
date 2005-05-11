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

import orbeon.apache.xml.utils.NamespaceSupport2;
import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.event.XFormsLinkError;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.xml.sax.Locator;

import java.io.IOException;
import java.util.*;

/**
 * Context in which control elements are executed.
 */
public class XFormsControls implements EventTarget {

    private Locator locator;

    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();
    private Map repeatIdToIndex = new HashMap();

    private XFormsContainingDocument containingDocument;
    private Document controlsDocument;

    private Stack contextStack = new Stack();

    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);

    public XFormsControls(XFormsContainingDocument containingDocument, Document controlsDocument) {
        this.containingDocument = containingDocument;
        this.controlsDocument = controlsDocument;
    }

    public void initialize() {

        // Clear existing stack
        contextStack.clear();

        // Push the default context
        final XFormsModel defaultModel = containingDocument.getModel("");
        final List defaultNodeset = Arrays.asList(new Object[] { defaultModel.getDefaultInstance().getDocument() });
        contextStack.push(new Context(defaultModel, defaultNodeset, true, null));
    }

    public Document getControlsDocument() {
        return controlsDocument;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    /**
     * Set the specified element with binding at the top of the stack and build the stack for the
     * parents.
     */
    public void setBinding(PipelineContext pipelineContext, Element bindingElement) {
        // Reinitialize context stack
        initialize();

        // Create ancestors-or-self list
        final List ancestorsOrSelf = new ArrayList();
        Element currentElement = bindingElement;
        while (currentElement != null) {
            ancestorsOrSelf.add(currentElement);
            currentElement = currentElement.getParent();
        }
        Collections.reverse(ancestorsOrSelf);

        // Bind up to the specified element
        for (Iterator i = ancestorsOrSelf.iterator(); i.hasNext();) {
            pushBinding(pipelineContext, (Element) i.next());
        }
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     */
    public void pushBinding(PipelineContext pipelineContext, Element bindingElement) {
        pushBinding(pipelineContext, bindingElement.attributeValue("ref"), bindingElement.attributeValue("nodeset"),
                bindingElement.attributeValue("model"), bindingElement.attributeValue("bind"), bindingElement);
    }

    public void pushBinding(PipelineContext pipelineContext, String ref, String nodeset, String model, String bind, Element bindingElement) {

        // Determine current context
        Context currentContext = getCurrentContext();

        // Handle model
        final XFormsModel newModel;
        if (model != null) {
            newModel = containingDocument.getModel(model);
        } else {
            newModel = currentContext.model;
        }

        // Handle nodeset
        final List newNodeset;
        {
            if (bind != null) {
                // Resolve the bind id to a node
                newNodeset = newModel.getBindNodeset(pipelineContext, newModel.getModelBindById(bind));
            } else if (ref != null || nodeset != null) {
                // Evaluate new XPath in context of current node
                newNodeset = newModel.getDefaultInstance().evaluateXPath(pipelineContext, getCurrentSingleNode(newModel.getModelId()),
                        ref != null ? ref : nodeset, getCurrentPrefixToURIMap(), null, functionLibrary, null);

                if (ref != null && newNodeset.isEmpty())
                    throw new ValidationException("Single-node binding expression '"
                            + ref + "' returned an empty nodeset", new LocationData(locator));
            } else {
                // No change to current nodeset
                newNodeset = currentContext.nodeset;
            }
        }

        // Push new context
        contextStack.push(new Context(newModel, newNodeset, newNodeset != currentContext.nodeset, bindingElement));
    }

    private Context getCurrentContext() {
        return (Context) contextStack.peek();
    }

    /**
     * Get the current single node binding, if any.
     */
    public Node getCurrentSingleNode(Context currentContext) {
        if (currentContext.nodeset.size() == 0)
            throw new ValidationException("Single node binding to nonexistent node in instance", new LocationData(locator));

        return (Node) currentContext.nodeset.get(0);
    }

    /**
     * Get the current single node binding for the given model id.
     */
    public Node getCurrentSingleNode(String modelId) {

        for (int i = contextStack.size() - 1; i >=0; i--) {
            Context currentContext = (Context) contextStack.get(i);

            String currentModelId = currentContext.model.getModelId();
            if ((currentModelId == null && modelId == null) || (modelId != null && modelId.equals(currentModelId)))
                return (Node) currentContext.nodeset.get(0);
        }

        // If not found, return the document element of the model's default instance
        return containingDocument.getModel(modelId).getInstance("").getDocument();
    }

    /**
     * Get the current single node binding, if any.
     */
    public Node getCurrentSingleNode() {
        return getCurrentSingleNode(getCurrentContext());
    }

    /**
     * Get the current nodeset binding, if any.
     */
    public List getCurrentNodeset() {
        return getCurrentContext().nodeset;
    }

    public Map getCurrentPrefixToURIMap() {
        Map prefixToURI = new HashMap();
        for (Enumeration e = namespaceSupport.getPrefixes(); e.hasMoreElements();) {
            String prefix = (String) e.nextElement();
            prefixToURI.put(prefix, namespaceSupport.getURI(prefix));
        }
        return prefixToURI;
    }

    public void popBinding() {
        if (contextStack.size() == 1)
            throw new OXFException("Attempt to clear XForms controls context stack.");
        contextStack.pop();
    }

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Returns the text value of the currently referenced node in the instance.
     */
    public String getRefValue() {
        Node node = getCurrentSingleNode(getCurrentContext());
        return node instanceof Element ? ((Element) node).getStringValue()
            : node instanceof Attribute ? ((Attribute) node).getValue()
            : null;
    }

    public void startRepeatId(String repeatId) {
        contextStack.push(null);
    }

    public void setRepeatIdIndex(String repeatId, int index) {
        // Update current element of nodeset in stack
        popBinding();
        List newNodeset = new ArrayList();
        newNodeset.add(getCurrentNodeset().get(index - 1));
        contextStack.push(new Context(getCurrentContext().model, newNodeset, true, null));

        if (repeatId != null)
            repeatIdToIndex.put(repeatId, new Integer(index));
    }

    public void endRepeatId(String repeatId) {
        if (repeatId != null)
            repeatIdToIndex.remove(repeatId);
        popBinding();
    }

    public int getRepeatIdIndex(String repeatId, LocationData locationData) {
        Object index = repeatIdToIndex.get(repeatId);
        if (index == null)
            throw new ValidationException("Function index uses repeat id '" + repeatId
                    + "' which it not in scope", locationData);
        return ((Integer) index).intValue();
    }

    public Map getRepeatIdToIndex() {
        return repeatIdToIndex;
    }

    public NamespaceSupport2 getNamespaceSupport() {
        return namespaceSupport;
    }

    public Locator getLocator() {
        return locator;
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }

    public XFormsModel getCurrentModel() {
        return getCurrentContext().model;
    }

    public XFormsInstance getCurrentInstance() {
        return getCurrentContext().model.getDefaultInstance();
    }

    protected static class Context {
        public List nodeset;
        public XFormsModel model;
        public boolean newBind;
        public Element controlElement;

        public Context(XFormsModel model, List nodeSet, boolean newBind, Element controlElement) {
            this.model = model;
            this.nodeset = nodeSet;
            this.newBind = newBind;
            this.controlElement = controlElement;
        }
    }

    public Element getControlElement(PipelineContext pipelineContext, String controlId) {
        // Create XPath variables
        Map variables = new HashMap();
        variables.put("control-id", controlId);

        // Create XPath expression
        PooledXPathExpression xpathExpression =
            XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(getControlsDocument(), null).wrap(getControlsDocument()),
                    "/xxf:controls//*[@id = $control-id]", XFormsServer.XFORMS_NAMESPACES, variables);

        // Get action element
        Element controlElement;
        try {
            controlElement = (Element) xpathExpression.evaluateSingle();
            if (controlElement == null)
                throw new OXFException("Cannot find control with id '" + controlId + "'.");
        } catch (org.orbeon.saxon.xpath.XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
        return controlElement;
    }

    /**
     * Return an ordered list of all the control elements, with the addition of xforms:group.
     */
    public List getAllControlElements(PipelineContext pipelineContext) {
        PooledXPathExpression xpathExpression =
                XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument),
                        "/xxf:controls//(xf:group|xf:input|xf:secret|xf:textarea|xf:output|xf:upload|xf:range|xf:trigger|xf:submit|xf:select|xf:select1)[@ref]", XFormsServer.XFORMS_NAMESPACES);
        try {
            return xpathExpression.evaluate();
        } catch (org.orbeon.saxon.xpath.XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
    }

    /**
     * Return the value of the label element for the current control, null if none.
     *
     * 8.3.3 The label Element
     */
    public String getLabelValue(PipelineContext pipelineContext) {
        return getChildElementValue(pipelineContext, XFormsConstants.XFORMS_LABEL_QNAME);
    }

    /**
     * Return the value of the help element for the current control, null if none.
     *
     * 8.3.4 The help Element
     */
    public String getHelpValue(PipelineContext pipelineContext) {
        return getChildElementValue(pipelineContext, XFormsConstants.XFORMS_HELP_QNAME);
    }

    /**
     * Return the value of the hint element for the current control, null if none.
     *
     * 8.3.5 The hint Element
     */
    public String getHintValue(PipelineContext pipelineContext) {
        return getChildElementValue(pipelineContext, XFormsConstants.XFORMS_HINT_QNAME);
    }

    /**
     * Return the value of the alert element for the current control, null if none.
     *
     * 8.3.6 The alert Element
     */
    public String getAlertValue(PipelineContext pipelineContext) {
        return getChildElementValue(pipelineContext, XFormsConstants.XFORMS_ALERT_QNAME);
    }

    private String getChildElementValue(PipelineContext pipelineContext, QName qName) {
        // Check first if there is a current control element
        Element controlElement = getCurrentContext().controlElement;
        if (controlElement == null)
            return null;

        // Check that there is a current child element
        Element childElement = controlElement.element(qName);
        if (childElement == null)
            return null;

        // Child element becomes the new binding
        pushBinding(pipelineContext, childElement);
        String result = null;

        // "the order of precedence is: single node binding attributes, linking attributes, inline text."

        // Try to get single node binding
        if (getCurrentContext().newBind) {
            final Node currentNode = getCurrentSingleNode();
            if (currentNode != null)
                result = XFormsInstance.getValueForNode(currentNode);
        }

        // Try to get linking attribute
        if (result == null) {
            String srcAttributeValue = childElement.attributeValue("src");
            if (srcAttributeValue != null) {
                try {
                    // TODO: should cache this?
                    result = XFormsUtils.retrieveSrcValue(srcAttributeValue);
                } catch (IOException e) {
                    // Dispatch xforms-link-error to model
                    getCurrentModel().dispatchEvent(pipelineContext, new XFormsLinkError(srcAttributeValue, childElement, e));
                }
            }
        }

        // Try to get static value
        if (result == null)
            result = childElement.getStringValue();

        popBinding();
        return result;
    }

    private Element getEventHandler(PipelineContext pipelineContext, Element controlElement, String eventName) {
        // Create XPath variables
        Map variables = new HashMap();
        variables.put("event-name", eventName);

        // Create XPath expression
        PooledXPathExpression xpathExpression =
            XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(getControlsDocument(), null).wrap(controlElement),
                    "*[@ev:event = $event-name]", XFormsServer.XFORMS_NAMESPACES, variables);

        // Get event handler element
        Element eventHandlerElement;
        try {
            eventHandlerElement = (Element) xpathExpression.evaluateSingle();
//            if (eventHandlerElement == null)
//                throw new OXFException("Cannot find event handler with name '" + eventName + "'.");
        } catch (org.orbeon.saxon.xpath.XPathException e) {
            throw new OXFException(e);
        } finally {
            if (xpathExpression != null)
                xpathExpression.returnToPool();
        }
        return eventHandlerElement;
    }

    public void dispatchEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        dispatchEvent(pipelineContext, xformsEvent, xformsEvent.getEventName());
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsGenericEvent xformsEvent, String eventName) {
        if (XFormsEvents.XFORMS_DOM_ACTIVATE.equals(eventName)) {
            // 4.4.1 The DOMActivate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent, eventName, xformsEvent.getControlElement());

        } else if (XFormsEvents.XFORMS_DOM_FOCUS_OUT.equals(eventName)) {
            // 4.4.9 The DOMFocusOut Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent, eventName, xformsEvent.getControlElement());

        } else if (XFormsEvents.XFORMS_VALUE_CHANGED.equals(eventName)) {
            // 4.4.2 The xforms-value-changed Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent, eventName, xformsEvent.getControlElement());

        } else {
            throw new OXFException("Invalid action requested: " + eventName);
        }
    }

    private boolean callEventHandlers(PipelineContext pipelineContext, XFormsGenericEvent XFormsEvent, String eventName, Element controlElement) {
        // Find event handler
        Element eventHandlerElement = getEventHandler(pipelineContext, controlElement, eventName);
        // If found, run actions
        if (eventHandlerElement != null) {
            runAction(pipelineContext, eventHandlerElement, XFormsEvent);
            return true;
        } else {
            return false;
        }
    }

    private void runAction(final PipelineContext pipelineContext, Element eventHandlerElement,
                                XFormsGenericEvent XFormsEvent) {

        String actionNamespaceURI = eventHandlerElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        String actionEventName = eventHandlerElement.getName();

        if (XFormsEvents.XFORMS_SETVALUE_ACTION.equals(actionEventName)) {
            // 10.1.9 The setvalue Element
            // xforms:setvalue

            // Set binding for current action element
            setBinding(pipelineContext, eventHandlerElement);

            final String value = eventHandlerElement.attributeValue("value");
            final String content = eventHandlerElement.getStringValue();

            final XFormsInstance instance = getCurrentInstance();
            final String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                Map namespaceContext = Dom4jUtils.getNamespaceContext(eventHandlerElement);
                valueToSet = instance.evaluateXPath(pipelineContext, value, namespaceContext, null, functionLibrary, null);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            // Set value on current node
            Node currentNode = getCurrentSingleNode();
            XFormsInstance.setValueForNode(currentNode, valueToSet);

        } else if (XFormsEvents.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element
            // xforms:toggle

            // Find case with that id and select it
            String caseId = eventHandlerElement.attributeValue("case");
            XFormsEvent.addDivToShow(caseId);

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
                        XFormsEvent.addDivToHide(caseElement.attributeValue(new QName("id")));
                    }
                } catch (org.orbeon.saxon.xpath.XPathException e) {
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

            for (Iterator i = eventHandlerElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                runAction(pipelineContext, embeddedActionElement, XFormsEvent);
            }

        } else {
            throw new OXFException("Invalid action requested: " + actionEventName);
        }
    }
}
