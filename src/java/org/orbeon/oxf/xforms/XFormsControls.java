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
import org.orbeon.oxf.xforms.event.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.XFormsLinkError;
import org.orbeon.oxf.xforms.event.XFormsSelectEvent;
import org.orbeon.oxf.xforms.event.XFormsInsertEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
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
    private RepeatInfo repeatInfo;

    private XFormsContainingDocument containingDocument;
    private Document controlsDocument;
    private DocumentXPathEvaluator documentXPathEvaluator;

    private Stack contextStack = new Stack();

    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);

    private static final Map groupingControls = new HashMap();
    private static final Map valueControls = new HashMap();
    private static final Map noValueControls = new HashMap();
    private static final Map leafControls = new HashMap();

    static {
        groupingControls.put("group", "group");
        groupingControls.put("repeat", "repeat");
        groupingControls.put("switch", "switch");
        groupingControls.put("case", "case");

        valueControls.put("input", "input");
        valueControls.put("secret", "secret");
        valueControls.put("textarea", "textarea");
        valueControls.put("output", "output");
        valueControls.put("upload", "upload");
        valueControls.put("range", "range");
        valueControls.put("select", "select");
        valueControls.put("select1", "select1");

        noValueControls.put("submit", "submit");
        noValueControls.put("trigger", "trigger");

        leafControls.putAll(valueControls);
        leafControls.putAll(noValueControls);
    }

    public XFormsControls(XFormsContainingDocument containingDocument, Document controlsDocument) {
        this.containingDocument = containingDocument;
        this.controlsDocument = controlsDocument;
        if (controlsDocument != null)
            this.documentXPathEvaluator = new DocumentXPathEvaluator(controlsDocument);
    }

    public void initialize() {

        // Clear existing stack
        contextStack.clear();

        // Push the default context
        final XFormsModel defaultModel = containingDocument.getModel("");
        final List defaultNodeset = Arrays.asList(new Object[]{defaultModel.getDefaultInstance().getDocument()});
        contextStack.push(new Context(defaultModel, defaultNodeset, true, null));
    }

    public Document getControlsDocument() {
        return controlsDocument;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public boolean isValueControl(String controlName) {
        return valueControls.get(controlName) != null;
    }

    public boolean isGroupingControl(String controlName) {
        return groupingControls.get(controlName) != null;
    }

    public boolean isLeafControl(String controlName) {
        return leafControls.get(controlName) != null;
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

        for (int i = contextStack.size() - 1; i >= 0; i--) {
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

    /**
     * Set the current index of xforms:repeat with specified id.
     */
    public void setRepeatIdIndex(String repeatId, int index) {
        // Update current element of nodeset in stack
        popBinding();
        List newNodeset = new ArrayList();
        newNodeset.add(getCurrentNodeset().get(index - 1));
        contextStack.push(new Context(getCurrentContext().model, newNodeset, true, null));//TODO: check this

        if (repeatId != null)
            repeatIdToIndex.put(repeatId, new Integer(index));
    }

    public void endRepeatId(String repeatId) {
        if (repeatId != null)
            repeatIdToIndex.remove(repeatId);
        popBinding();
    }

    /**
     * Set all current repeat indices.
     */
    public void setRepeatIndices(Map repeatIds) {
        // TODO
    }

    /**
     * Return the current repeat index for the given xforms:repeat id. Null if the id is not found.
     */
    public Integer getRepeatIdIndex(String repeatId) {
        return (Integer) repeatIdToIndex.get(repeatId);
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

        // Get action element
        Element controlElement;
        controlElement = (Element) documentXPathEvaluator.evaluateSingle(pipelineContext, getControlsDocument(),
                "/xxf:controls//*[@id = $control-id]", XFormsServer.XFORMS_NAMESPACES, variables, null, null);
        if (controlElement == null)
            throw new OXFException("Cannot find control with id '" + controlId + "'.");
        return controlElement;
    }

    /**
     * Visit all the effective controls elements. Handle xforms:repeat if updateRepeats is true.
     */
    public void visitAllControls(PipelineContext pipelineContext, ControlVisitorListener controlVisitorListener, boolean updateRepeats) {
        initialize();
        handleControls(pipelineContext, controlVisitorListener, controlsDocument.getRootElement(), "", updateRepeats, null);
    }

    private boolean handleControls(PipelineContext pipelineContext, ControlVisitorListener controlVisitorListener,
                                   Element container, String idPostfix, boolean updateRepeats, RepeatInfo repeatInfo) {
        boolean doContinue = true;
        for (Iterator i = container.elements().iterator(); i.hasNext();) {
            final Element controlElement = (Element) i.next();
            final String controlName = controlElement.getName();

            final String currentControlId = controlElement.attributeValue("id") + idPostfix;

            if (controlName.equals("repeat")) {
                // Handle xforms:repeat
                String repeatId = controlElement.attributeValue("id");

                if (updateRepeats) {
                    if (repeatInfo == null)
                        this.repeatInfo = repeatInfo = new RepeatInfo(repeatId);
                    else {
                        RepeatInfo childRepeatInfo = new RepeatInfo(repeatId);
                        repeatInfo.addChild(childRepeatInfo);
                        repeatInfo = childRepeatInfo;
                    }
                }

                String startIndexString = controlElement.attributeValue("startindex");
                //String numberString = controlElement.attributeValue("number");

                // Handle repeat index and default repeat index
                // TODO: this must be done in a separate pass so that forward ids are available
                // TODO: must support obtaining
                if (repeatIdToIndex.get(repeatId) == null) {
                    Integer startIndex = new Integer((startIndexString != null) ? Integer.parseInt(startIndexString) : 1);
                    repeatIdToIndex.put(repeatId, startIndex);
                }

                // Push binding for xforms:repeat
                pushBinding(pipelineContext, controlElement);
                try {
                    final Context currentContext = getCurrentContext();

                    // Visit xforms:repeat element
                    doContinue = controlVisitorListener.startVisitControl(controlElement, currentControlId);

                    // Iterate over current xforms:repeat nodeset
                    final List currentNodeset = getCurrentNodeset();
                    int currentIndex = 1;
                    for (Iterator j = currentNodeset.iterator(); j.hasNext(); currentIndex++) {
                        Node currentNode = (Node) j.next();

                        // Push "artificial" binding with just current node in nodeset
                        contextStack.push(new Context(currentContext.model, Collections.singletonList(currentNode), true, null));
                        try {
                            // Handle children of xforms:repeat
                            if (doContinue)
                                doContinue = handleControls(pipelineContext, controlVisitorListener, controlElement, idPostfix + "-" + currentIndex, updateRepeats, repeatInfo);
                        } finally {
                            contextStack.pop();
                        }
                        if (!doContinue)
                            break;
                    }
                    if (updateRepeats)
                        repeatInfo.setOccurs(currentIndex - 1);

                    doContinue = doContinue && controlVisitorListener.endVisitControl(controlElement, currentControlId);

                } finally {
                    popBinding();
                }

            } else  if (isGroupingControl(controlName)) {
                // Handle XForms grouping controls
                pushBinding(pipelineContext, controlElement);
                try {
                    doContinue = controlVisitorListener.startVisitControl(controlElement, currentControlId);
                    if (doContinue)
                        doContinue = handleControls(pipelineContext, controlVisitorListener, controlElement, idPostfix, updateRepeats, repeatInfo);
                    doContinue = doContinue && controlVisitorListener.endVisitControl(controlElement, currentControlId);
                } finally {
                    popBinding();
                }
            } else if (isLeafControl(controlName)) {
                // Handle leaf control
                pushBinding(pipelineContext, controlElement);
                try {
                    doContinue = controlVisitorListener.startVisitControl(controlElement, currentControlId);
                    doContinue = doContinue && controlVisitorListener.endVisitControl(controlElement, currentControlId);
                } finally {
                    popBinding();
                }
            }
            if (!doContinue)
                break;
        }
        return doContinue;
    }

    /**
     * Get xforms:repeat information.
     */
    public RepeatInfo getRepeatInfo() {
        return repeatInfo;
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

        // Get event handler element
        Element eventHandlerElement;
        eventHandlerElement = (Element) documentXPathEvaluator.evaluateSingle(pipelineContext, controlElement,
                "*[@ev:event = $event-name]", XFormsServer.XFORMS_NAMESPACES, variables, null, null);
//            if (eventHandlerElement == null)
//                throw new OXFException("Cannot find event handler with name '" + eventName + "'.");
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

        } else if (XFormsEvents.XFORMS_SELECT.equals(eventName) || XFormsEvents.XFORMS_DESELECT.equals(eventName)) {
            // 4.4.3 The xforms-select and xforms-deselect Events
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
                valueToSet = instance.evaluateXPathAsString(pipelineContext, value, namespaceContext, null, functionLibrary, null);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            // Set value on current node
            Node currentNode = getCurrentSingleNode();
            XFormsInstance.setValueForNode(currentNode, valueToSet);

        } else if (XFormsEvents.XFORMS_ACTION_ACTION.equals(actionEventName)) {
            // 10.1.1 The action Element
            // xforms:action

            for (Iterator i = eventHandlerElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                runAction(pipelineContext, embeddedActionElement, XFormsEvent);
            }

        } else if (XFormsEvents.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element
            // xforms:toggle

            // Find case with that id and select it
            String caseId = eventHandlerElement.attributeValue("case");
            XFormsEvent.addDivToShow(caseId);

            // Find case element with current case id
            final Element currentCaseElement;
            Map variables = new HashMap();
            variables.put("case-id", caseId);
            currentCaseElement = (Element) documentXPathEvaluator.evaluateSingle(pipelineContext, getControlsDocument(),
                    "/xxf:controls//xf:case[@id = $case-id]", XFormsServer.XFORMS_NAMESPACES, variables, null, null);
            // "1. Dispatching an xforms-deselect event to the currently selected case."
            dispatchEvent(pipelineContext, new XFormsDeselectEvent(currentCaseElement));

            // Deselect other cases in that switch
            {
                final List xpathExpressionResult = documentXPathEvaluator.evaluate(pipelineContext, currentCaseElement,
                        "./ancestor::xf:switch[1]//xf:case[not(@id = $case-id)]",
                        XFormsServer.XFORMS_NAMESPACES, variables, null, null);

                for (Iterator i = xpathExpressionResult.iterator(); i.hasNext();) {
                    Element caseElement = (Element) i.next();
                    XFormsEvent.addDivToHide(caseElement.attributeValue(new QName("id")));

                    // "2. Dispatching an xform-select event to the case to be selected."
                    dispatchEvent(pipelineContext, new XFormsSelectEvent(caseElement));
                }
            }

        } else if (XFormsEvents.XFORMS_INSERT_ACTION.equals(actionEventName)) {
            // 9.3.5 The insert Element
            final String atAttribute = eventHandlerElement.attributeValue("at");
            final String positionAttribute = eventHandlerElement.attributeValue("position");

            // Set current binding in order to evaluate the current nodeset
            // "1. The homogeneous collection to be updated is determined by evaluating binding attribute nodeset."
            setBinding(pipelineContext, eventHandlerElement);
            final List collectionToBeUpdated = getCurrentNodeset();

            // "2. The corresponding node-set of the initial instance data is located to determine
            // the prototypical member of the collection. The final member of this collection is
            // cloned to produce the node that will be inserted."
            final Element clonedElement;
            {
                final List initialInstanceNodeset = collectionToBeUpdated; // TODO: use initial instance to compute this!!!
                final Element lastElement = (Element) initialInstanceNodeset.get(initialInstanceNodeset.size() - 1);
                clonedElement = (Element) lastElement.createCopy();
            }

            // "Finally, this newly created node is inserted into the instance data at the location
            // specified by attributes position and at."
            final XFormsInstance currentInstance = getCurrentInstance();
            final String insersionIndexString = currentInstance.evaluateXPathAsString(pipelineContext,
                    "round(" + atAttribute + ")", getCurrentPrefixToURIMap(), null, functionLibrary, null);

            // Don't think we will get NaN with XPath 2.0...
            int insersionIndex = "NaN".equals(insersionIndexString) ? collectionToBeUpdated.size() : Integer.parseInt(insersionIndexString) ;

            // Adjust index to be in range
            if (insersionIndex > collectionToBeUpdated.size())
                insersionIndex = collectionToBeUpdated.size();

            if (insersionIndex < 1)
                insersionIndex = 1;

            // Find actual insersion point and insert
            final Element indexElement = (Element) collectionToBeUpdated.get(insersionIndex - 1);

            final Element parentElement = indexElement.getParent();
            final List siblingElements = parentElement.elements();
            final int actualIndex = siblingElements.indexOf(indexElement);

            // Insert new element (changes to the list are reflected in the document)
            final int newNodeIndex;
            if ("after".equals(positionAttribute) || "NaN".equals(insersionIndexString)) {
                siblingElements.add(actualIndex + 1, clonedElement);
                newNodeIndex = insersionIndex + 1;
            } else if ("before".equals(positionAttribute)) {
                siblingElements.add(actualIndex, clonedElement);
                newNodeIndex = insersionIndex;
            } else {
                throw new OXFException("Invalid 'position' attribute: " + positionAttribute + ". Must be either 'before' or 'after'.");
            }

            // "3. The index for any repeating sequence that is bound to the homogeneous collection
            // where the node was added is updated to point to the newly added node. The indexes for
            // inner nested repeat collections are re-initialized to 1."

            visitAllControls(pipelineContext, new ControlVisitorListener() {
                private Element xformsRepeatElement;
                public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                    if (controlElement.getName().equals("repeat")) {
                        setBinding(pipelineContext, controlElement);
                        Element currentNode = (Element) getCurrentSingleNode();
                        Element currentParent = currentNode.getParent();
                        if (xformsRepeatElement != null) {
                            // This is a child of the updated xforms:repeat
                            // Set index to 1
                            setRepeatIdIndex(controlElement.attributeValue("id"), 1);
                        } else if (currentParent == parentElement) {
                            // Found the xforms:repeat for which the insert was done
                            xformsRepeatElement = controlElement;

                            // Set index to new position
                            setRepeatIdIndex(controlElement.attributeValue("id"), newNodeIndex);
                        }
                    }
                    return true;
                }
                public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                    // Stop when we have handled all the children of the found xforms:repeat element
                    return ((xformsRepeatElement != null) && (controlElement != xformsRepeatElement));
                }
            }, false);

            // "4. If the insert is successful, the event xforms-insert is dispatched."
            currentInstance.dispatchEvent(pipelineContext, new XFormsInsertEvent(atAttribute));

        } else if (XFormsEvents.XFORMS_DELETE_ACTION.equals(actionEventName)) {
            // 9.3.6 The delete Element


        } else if (XFormsEvents.XFORMS_SETINDEX_ACTION.equals(actionEventName)) {
            // 9.3.7 The setindex Element

        } else {
            throw new OXFException("Invalid action requested: " + actionEventName);
        }
    }

    public static interface ControlVisitorListener {
        public boolean startVisitControl(Element controlElement, String effectiveControlId);
        public boolean endVisitControl(Element controlElement, String effectiveControlId);
    }

    /**
     * Represents xforms:repeat information.
     */
    public static class RepeatInfo {
        private String id;
        private int occurs;
        private List children;

        public RepeatInfo(String id) {
            this.id = id;
        }

        public void setOccurs(int occurs) {
            this.occurs = occurs;
        }

        public void addChild(RepeatInfo repeatInfo) {
            if (children == null)
                children = new ArrayList();
            children.add(repeatInfo);
        }

        public String getId() {
            return id;
        }

        public int getOccurs() {
            return occurs;
        }

        public List getChildren() {
            return children;
        }
    }
}
