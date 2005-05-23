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
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.xml.sax.Locator;

import java.io.IOException;
import java.util.*;

/**
 * Represents all this XForms containing document controls and the context in which they operate.
 */
public class XFormsControls implements XFormsEventTarget {

    private Locator locator;

    private NamespaceSupport2 namespaceSupport = new NamespaceSupport2();
    private RepeatInfo repeatInfo;
    private Map switchIdToToSwitchInfoMap;
    private Map caseIdToSwitchInfoMap;
    private Map itemsetIdToItemsetInfoMap;

    private Map itemsetIdToItemsetInfoUpdateMap;

    private XFormsContainingDocument containingDocument;
    private Document controlsDocument;
    private DocumentXPathEvaluator documentXPathEvaluator;

    protected Stack contextStack = new Stack();

    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);

    private static final Map groupingControls = new HashMap();
    private static final Map valueControls = new HashMap();
    private static final Map noValueControls = new HashMap();
    private static final Map leafControls = new HashMap();
    private static final Map mandatorySingleNodeControls = new HashMap();
    private static final Map optionalSingleNodeControls = new HashMap();
    private static final Map noSingleNodeControls = new HashMap();
    private static final Map mandatoryNodesetControls = new HashMap();
    private static final Map noNodesetControls = new HashMap();

    static {
        groupingControls.put("group", "");
        groupingControls.put("repeat", "");
        groupingControls.put("switch", "");
        groupingControls.put("case", "");

        valueControls.put("input", "");
        valueControls.put("secret", "");
        valueControls.put("textarea", "");
        valueControls.put("output", "");
        valueControls.put("upload", "");
        valueControls.put("range", "");
        valueControls.put("select", "");
        valueControls.put("select1", "");

        noValueControls.put("submit", "");
        noValueControls.put("trigger", "");

        leafControls.putAll(valueControls);
        leafControls.putAll(noValueControls);

        mandatorySingleNodeControls.putAll(valueControls);
        mandatorySingleNodeControls.remove("output");
        mandatorySingleNodeControls.put("filename", "");
        mandatorySingleNodeControls.put("mediatype", "");
        mandatorySingleNodeControls.put("setvalue", "");

        optionalSingleNodeControls.putAll(noValueControls);
        optionalSingleNodeControls.put("output", "");  // can have @value attribute
        optionalSingleNodeControls.put("value", "");   // can have inline text
        optionalSingleNodeControls.put("label", "");   // can have linking or inline text
        optionalSingleNodeControls.put("help", "");    // can have linking or inline text
        optionalSingleNodeControls.put("hint", "");    // can have linking or inline text
        optionalSingleNodeControls.put("alert", "");   // can have linking or inline text
        optionalSingleNodeControls.put("copy", "");
        optionalSingleNodeControls.put("load", "");     // can have linking
        optionalSingleNodeControls.put("message", "");  // can have linking or inline text
        optionalSingleNodeControls.put("group", "");
        optionalSingleNodeControls.put("switch", "");

        noSingleNodeControls.put("choices", "");
        noSingleNodeControls.put("item", "");
        noSingleNodeControls.put("case", "");
        noSingleNodeControls.put("toggle", "");

        mandatoryNodesetControls.put("repeat", "");
        mandatoryNodesetControls.put("itemset", "");
        mandatoryNodesetControls.put("insert", "");
        mandatoryNodesetControls.put("delete", "");

        noNodesetControls.putAll(mandatorySingleNodeControls);
        noNodesetControls.putAll(optionalSingleNodeControls);
        noNodesetControls.putAll(noSingleNodeControls);
    }

    public XFormsControls(XFormsContainingDocument containingDocument, Document controlsDocument) {
        this.containingDocument = containingDocument;
        this.controlsDocument = controlsDocument;
        if (controlsDocument != null)
            this.documentXPathEvaluator = new DocumentXPathEvaluator(controlsDocument);
    }

    public void initialize(PipelineContext pipelineContext) {

        initializeContextStack();

        if (controlsDocument != null) {
            // Initialize repeat information
            initializeRepeatInfo(pipelineContext);
            // Initialize switch information
            initializeSwitchInfo(pipelineContext);
            // Initialize itemset information
            itemsetIdToItemsetInfoMap = getItemsetInfo(pipelineContext, null);
        }
    }

    private void initializeContextStack() {
        // Clear existing stack
        contextStack.clear();

        // Push the default context
        final XFormsModel defaultModel = containingDocument.getModel("");
        final List defaultNodeset = Arrays.asList(new Object[]{defaultModel.getDefaultInstance().getDocument().getRootElement()});
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
        initializeContextStack();

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

        // Check for mandatory and optional bindings
        if (bindingElement != null && XFormsConstants.XFORMS_NAMESPACE_URI.equals(bindingElement.getNamespaceURI())) {
            final String controlName = bindingElement.getName();
            if (mandatorySingleNodeControls.get(controlName) != null
                    && !(bindingElement.attribute("ref") != null || bindingElement.attribute("bind") != null)) {
                throw new OXFException("Missing mandatory single node binding for element: " + bindingElement.getQualifiedName());
            }
            if (noSingleNodeControls.get(controlName) != null
                    && (bindingElement.attribute("ref") != null || bindingElement.attribute("bind") != null)) {
                throw new OXFException("Single node binding is prohibited for element: " + bindingElement.getQualifiedName());
            }
            if (mandatoryNodesetControls.get(controlName) != null
                    && !(bindingElement.attribute("nodeset") != null)) {
                throw new OXFException("Missing mandatory nodeset binding for element: " + bindingElement.getQualifiedName());
            }
            if (noNodesetControls.get(controlName) != null
                    && bindingElement.attribute("nodeset") != null) {
                throw new OXFException("Node-set binding is prohibited for element: " + bindingElement.getQualifiedName());
            }
        }

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

    protected Context getCurrentContext() {
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
        return containingDocument.getModel(modelId).getDefaultInstance().getDocument();
    }

    /**
     * Get the current single node binding, if any.
     */
    public Node getCurrentSingleNode() {
        return getCurrentSingleNode(getCurrentContext());
    }

    public String getCurrentSingleNodeValue() {
        final Node currentSingleNode = getCurrentSingleNode();
        if (currentSingleNode != null)
            return XFormsInstance.getValueForNode(currentSingleNode);
        else
            return null;
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
     * Set all default xforms:repeat indices and other information.
     */
    private void initializeRepeatInfo(PipelineContext pipelineContext) {
        repeatInfo = null;
        visitAllControls(pipelineContext, new ControlVisitorListener() {
            private Stack repeatInfoStack = new Stack();
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                if (controlElement.getName().equals("repeat")) {
                    final String repeatId = controlElement.attributeValue("id");
                    final String startIndexString = controlElement.attributeValue("startindex");
                    //final String numberString = controlElement.attributeValue("number"); // TODO
                    final Integer startIndex = new Integer((startIndexString != null) ? Integer.parseInt(startIndexString) : 1);

                    final RepeatInfo newRepeatInfo = new RepeatInfo(repeatId, startIndex.intValue(), getCurrentNodeset().size());

                    // Create new RepeatInfo
                    if (repeatInfo == null) {
                        XFormsControls.this.repeatInfo = newRepeatInfo;
                    } else {
                        ((RepeatInfo) (repeatInfoStack.peek())).addChild(newRepeatInfo);
                    }
                    repeatInfoStack.push(newRepeatInfo);
                }
                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                if (controlElement.getName().equals("repeat")) {
                    repeatInfoStack.pop();
                }
                return true;
            }
        });
    }

    /**
     * Compute all default xforms:switch/xforms:case information.
     */
    private void initializeSwitchInfo(PipelineContext pipelineContext) {
        final Map switchInfoMap = new HashMap();
        final Map caseIdToSwitchInfoMap = new HashMap();
        visitAllControls(pipelineContext, new ControlVisitorListener() {
            private Stack switchStack = new Stack();
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                final String controlName = controlElement.getName();
                final String controlId = controlElement.attributeValue("id");
                if (controlName.equals("switch")) {
                    switchStack.push(new SwitchInfo(controlId));
                } else if (controlName.equals("case")) {
                    final SwitchInfo switchInfo = (SwitchInfo) switchStack.peek();

                    caseIdToSwitchInfoMap.put(controlId, switchInfo);

                    if (switchInfo.getSelectedCaseId() == null) {
                        // If case is not already selected and there is a select attribute, set it
                        final String selectedAttribute = controlElement.attributeValue("selected");
                        if ("true".equals(selectedAttribute))
                            switchInfo.setSelectedCaseId(controlId);
                        else
                            switchInfo.addDeselectedCaseId(controlId);
                    } else {
                        // Remember deselected case id
                        switchInfo.addDeselectedCaseId(controlId);
                    }
                }

                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                final String controlName = controlElement.getName();
                if (controlName.equals("switch")) {
                    final SwitchInfo switchInfo = (SwitchInfo) switchStack.peek();
                    if (switchInfo.getSelectedCaseId() == null) {
                        // No case was selected, select first id
                        final List deselectedCaseIds = switchInfo.getDeselectedCaseIds();
                        if (deselectedCaseIds.size() > 0) {
                            switchInfo.setSelectedCaseId((String) deselectedCaseIds.get(0));
                            deselectedCaseIds.remove(0);
                        }
                    }

                    // Add new switchInfo
                    switchInfoMap.put(switchInfo.getSwitchId(), switchInfo);

                    switchStack.pop();
                }
                return true;
            }
        });
        this.switchIdToToSwitchInfoMap = switchInfoMap;
        this.caseIdToSwitchInfoMap = caseIdToSwitchInfoMap;
    }

    /**
     * Update xforms:switch/xforms:case information with newly selected case id.
     */
    private void updateSwitchInfo(final PipelineContext pipelineContext, final String selectedCaseId) {
        visitAllControls(pipelineContext, new ControlVisitorListener() {
            private Stack switchStack = new Stack();
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                final String controlName = controlElement.getName();
                final String controlId = controlElement.attributeValue("id");
                if (controlName.equals("switch")) {
                    switchStack.push(switchIdToToSwitchInfoMap.get(controlId));
                } else if (controlName.equals("case")) {
                    final SwitchInfo switchInfo = (SwitchInfo) switchStack.peek();

                    switchInfo.setControlForId(controlId, controlElement);

                    if (selectedCaseId.equals(controlId) && !selectedCaseId.equals(switchInfo.getSelectedCaseId())) {
                        // This is the case that just got selected, and it was not previously selected
                        switchInfo.startUpdateSelectedCaseId(selectedCaseId);
                    }
                }

                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                final String controlName = controlElement.getName();
                if (controlName.equals("switch")) {
                    final SwitchInfo switchInfo = (SwitchInfo) switchStack.peek();

                    final String previouslySelected = switchInfo.getPreviouslySelectedCaseId();
                    if (previouslySelected != null) {
                        // A new selection occurred on this switch

                        // "1. Dispatching an xforms-deselect event to the currently selected case."
                        dispatchEvent(pipelineContext, new XFormsDeselectEvent(switchInfo.getControlForId(previouslySelected)));

                        // "2. Dispatching an xform-select event to the case to be selected."
                        dispatchEvent(pipelineContext, new XFormsSelectEvent(switchInfo.getControlForId(switchInfo.getSelectedCaseId())));

                        switchInfo.endUpdateSelectedCaseId();
                    }

                    switchStack.pop();
                }
                return true;
            }
        });
    }

    public void updateSwitchInfo(String caseId, boolean visible) {
        SwitchInfo switchInfo = (SwitchInfo) caseIdToSwitchInfoMap.get(caseId);
        if (switchInfo == null)
            throw new OXFException("No SwitchInfo found for case id '" + caseId + "'.");
        if (visible) {
            switchInfo.startUpdateSelectedCaseId(caseId);
            switchInfo.endUpdateSelectedCaseId();
        }
    }

    /**
     * Compute all default xforms:itemset information.
     */
    private Map getItemsetInfo(final PipelineContext pipelineContext, final XFormsModel model) {
        final Map[] resultMap = new Map[1];
        visitAllControls(pipelineContext, new ControlVisitorListener() {
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                final String controlName = controlElement.getName();
                if (controlName.equals("select") || controlName.equals("select1")) {

                    final Element itemsetElement = controlElement.element(XFormsConstants.XFORMS_ITEMSET_QNAME);

                    if (itemsetElement != null) {
                        final String selectControlId = controlElement.attributeValue("id");

                        // Iterate through the collection
                        pushBinding(pipelineContext, itemsetElement);
                        {
                            final Context currentContext = getCurrentContext();

                            if (model == null || model == currentContext.model) { // it is possible to filter on a particular model
                                final List items = new ArrayList();
                                for (Iterator i = getCurrentNodeset().iterator(); i.hasNext();) {
                                    Node currentNode = (Node) i.next();

                                    // Push "artificial" binding with just current node in nodeset
                                    contextStack.push(new Context(currentContext.model, Collections.singletonList(currentNode), true, null));
                                    {
                                        // Handle children of xforms:itemset

                                        pushBinding(pipelineContext, itemsetElement.element(XFormsConstants.XFORMS_LABEL_QNAME));
                                        final String label = getCurrentSingleNodeValue();
                                        popBinding();
                                        final Element valueCopyElement;
                                        {
                                            final Element valueElement = itemsetElement.element(XFormsConstants.XFORMS_VALUE_QNAME);
                                            valueCopyElement = (valueElement != null)
                                                ? valueElement : itemsetElement.element(XFormsConstants.XFORMS_COPY_QNAME);
                                        }
                                        pushBinding(pipelineContext, valueCopyElement);
                                        final String value = getCurrentSingleNodeValue();;
                                        // TODO: handle xforms:copy
                                        items.add(new ItemsetInfo(selectControlId, label, value));

                                        popBinding();
                                    }
                                    contextStack.pop();
                                }
                                if (resultMap[0] == null)
                                    resultMap[0] = new HashMap();
                                resultMap[0].put(selectControlId, items);
                            }
                        }
                        popBinding();
                    }
                }

                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                return true;
            }
        });
        return resultMap[0];
    }

    /**
     * Perform a refresh of the controls for a given model
     */
    public void refreshForModel(final PipelineContext pipelineContext, final XFormsModel model) {

        // Get xforms:itemset info for this model
        final Map result = getItemsetInfo(pipelineContext, model);

        if (itemsetIdToItemsetInfoUpdateMap == null) {
            itemsetIdToItemsetInfoUpdateMap = result;
        } else {
            itemsetIdToItemsetInfoUpdateMap.putAll(result);
        }
    }

    /**
     * Return the current repeat index for the given xforms:repeat id, -1 if the id is not found.
     */
    public int getRepeatIdIndex(String repeatId) {
        RepeatInfo foundRepeatInfo = findRepeatInfo(repeatId, repeatInfo);
        return (foundRepeatInfo == null) ? -1 : foundRepeatInfo.getIndex();
    }

    private RepeatInfo findRepeatInfo(String repeatId, RepeatInfo repeatInfo) {
        if (repeatInfo.getId().equals(repeatId))
            return repeatInfo;

        if (repeatInfo.getChildren() != null) {
            for (Iterator i = repeatInfo.getChildren().iterator(); i.hasNext();) {
                RepeatInfo childRepeatInfo = (RepeatInfo) i.next();

                RepeatInfo childResult = findRepeatInfo(repeatId, childRepeatInfo);
                if (childResult != null)
                    return childResult;
            }
        }
        return null;
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
        return getCurrentContext().model.getInstanceForNode(getCurrentSingleNode());
    }

    /**
     * Get element with the id specified.
     */
    public Element getElementById(PipelineContext pipelineContext, String controlId) {
        // TODO: do this without XPath, and remove need for pipelineContext

        // Create XPath variables
        Map variables = new HashMap();
        variables.put("control-id", controlId);

        // Get action element
        Element controlElement;
        controlElement = (Element) documentXPathEvaluator.evaluateSingle(pipelineContext, getControlsDocument(),
                "/controls//*[@id = $control-id]", XFormsServer.XFORMS_NAMESPACES, variables, null, null);
        if (controlElement == null)
            throw new OXFException("Cannot find control with id '" + controlId + "'.");
        return controlElement;
    }

    /**
     * Visit all the effective controls elements.
     */
    public void visitAllControls(PipelineContext pipelineContext, ControlVisitorListener controlVisitorListener) {
        initializeContextStack();
        handleControls(pipelineContext, controlVisitorListener, controlsDocument.getRootElement(), "", null);
    }

    private boolean handleControls(PipelineContext pipelineContext, ControlVisitorListener controlVisitorListener,
                                   Element container, String idPostfix, RepeatInfo repeatInfo) {
        boolean doContinue = true;
        for (Iterator i = container.elements().iterator(); i.hasNext();) {
            final Element controlElement = (Element) i.next();
            final String controlName = controlElement.getName();

            final String controlId = controlElement.attributeValue("id");
            final String effectiveControlId = controlId + idPostfix;

            if (controlName.equals("repeat")) {
                // Handle xforms:repeat

                // Push binding for xforms:repeat
                pushBinding(pipelineContext, controlElement);
                try {
                    final Context currentContext = getCurrentContext();

                    // Visit xforms:repeat element
                    doContinue = controlVisitorListener.startVisitControl(controlElement, effectiveControlId);

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
                                doContinue = handleControls(pipelineContext, controlVisitorListener, controlElement, idPostfix + "-" + currentIndex, repeatInfo);
                        } finally {
                            contextStack.pop();
                        }
                        if (!doContinue)
                            break;
                    }

                    doContinue = doContinue && controlVisitorListener.endVisitControl(controlElement, effectiveControlId);

                } finally {
                    popBinding();
                }

            } else  if (isGroupingControl(controlName)) {
                // Handle XForms grouping controls
                pushBinding(pipelineContext, controlElement);
                try {
                    doContinue = controlVisitorListener.startVisitControl(controlElement, effectiveControlId);
                    if (doContinue)
                        doContinue = handleControls(pipelineContext, controlVisitorListener, controlElement, idPostfix, repeatInfo);
                    doContinue = doContinue && controlVisitorListener.endVisitControl(controlElement, effectiveControlId);
                } finally {
                    popBinding();
                }
            } else if (isLeafControl(controlName)) {
                // Handle leaf control
                pushBinding(pipelineContext, controlElement);
                try {
                    doContinue = controlVisitorListener.startVisitControl(controlElement, effectiveControlId);
                    doContinue = doContinue && controlVisitorListener.endVisitControl(controlElement, effectiveControlId);
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
     * Get xforms:switch information.
     */
    public Map getSwitchIdToToSwitchInfoMap() {
        return switchIdToToSwitchInfoMap;
    }

    /**
     * Get full xforms:itemset information.
     */
    public Map getItemsetFull() {
        return itemsetIdToItemsetInfoMap;
    }

    /**
     * Get xforms:itemset information to update.
     */
    public Map getItemsetUpdate() {

        if (itemsetIdToItemsetInfoUpdateMap == null) {
            // There is no update in the first place
            return null;
        } else if (itemsetIdToItemsetInfoMap == null) {
            // There was nothing before, return update
            return itemsetIdToItemsetInfoUpdateMap;
        } else {
            // Merge differences
            final Map result = new HashMap();

            for (Iterator i = itemsetIdToItemsetInfoUpdateMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String itemsetId = (String) currentEntry.getKey();
                final List newItems = (List) currentEntry.getValue();

                final List existingItems = (List) itemsetIdToItemsetInfoMap.get(itemsetId);
                if (existingItems == null || !existingItems.equals(newItems)) {
                    // No existing items or new items are different from existing items
                    result.put(itemsetId, newItems);
                }
            }

            return result;
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
                    final XFormsModel currentModel = getCurrentModel();
                    currentModel.dispatchEvent(pipelineContext, new XFormsLinkErrorEvent(currentModel, srcAttributeValue, childElement, e));
                }
            }
        }

        // Try to get static value
        if (result == null)
            result = childElement.getStringValue();

        popBinding();
        return result;
    }

    public void dispatchEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        final String eventName = xformsEvent.getEventName();
        if (XFormsEvents.XFORMS_DOM_ACTIVATE.equals(eventName)) {
            // 4.4.1 The DOMActivate Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            final Element targetElement = (Element) xformsEvent.getTargetObject();
            if (targetElement.getName().equals("submit")) {
                // xforms:submit reacts to DOMActivate in a special way

                // Find submission id
                final String submissionId = targetElement.attributeValue("submission");
                if (submissionId == null)
                    throw new OXFException("xforms:submit requires a submission attribute.");

                // Find submission object and dispatch submit event to it
                final Object object = containingDocument.getObjectById(pipelineContext, submissionId);
                if (object instanceof XFormsModelSubmission) {
                    final XFormsModelSubmission submission = (XFormsModelSubmission) object;

                    submission.dispatchEvent(pipelineContext, new XFormsSubmitEvent(submission));
                } else {
                    throw new OXFException("xforms:submit submission attribute must point to an xforms:submission element.");
                }

            } else {
                callEventHandlers(pipelineContext, xformsEvent);
            }

        } else if (XFormsEvents.XFORMS_DOM_FOCUS_OUT.equals(eventName)) {
            // 4.4.9 The DOMFocusOut Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent);

        } else if (XFormsEvents.XFORMS_VALUE_CHANGED.equals(eventName)) {
            // 4.4.2 The xforms-value-changed Event
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent);

        } else if (XFormsEvents.XFORMS_SELECT.equals(eventName)) {
            // 4.4.3 The xforms-select and xforms-deselect Events
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent);

        } else if (XFormsEvents.XFORMS_DESELECT.equals(eventName)) {
            // 4.4.3 The xforms-select and xforms-deselect Events
            // Bubbles: Yes / Cancelable: No / Context Info: None
            // The default action for this event results in the following: None; notification event only.

            callEventHandlers(pipelineContext, xformsEvent);

        } else {
            throw new OXFException("Invalid action requested: " + eventName);
        }
    }

    private boolean callEventHandlers(PipelineContext pipelineContext, XFormsEvent xformsEvent) {

        // TODO: capture / bubbling / cancel

        // Find event handler
        Element eventHandlerElement = getEventHandler(pipelineContext, (Element) xformsEvent.getTargetObject(), xformsEvent.getEventName());
        // If found, run actions
        if (eventHandlerElement != null) {
            runAction(pipelineContext, eventHandlerElement, xformsEvent);
            return true;
        } else {
            return false;
        }
    }

    private Element getEventHandler(PipelineContext pipelineContext, Element targetElement, String eventName) {
        // Create XPath variables
        Map variables = new HashMap();
        variables.put("event-name", eventName);

        // Get event handler element
        Element eventHandlerElement;
        eventHandlerElement = (Element) documentXPathEvaluator.evaluateSingle(pipelineContext, targetElement,
                "(for $node in (reverse(ancestor-or-self::xf:*)) return $node/xf:*[@ev:event = $event-name][1])[1]", XFormsServer.XFORMS_NAMESPACES, variables, null, null);
//            if (eventHandlerElement == null)
//                throw new OXFException("Cannot find event handler with name '" + eventName + "'.");
        return eventHandlerElement;
    }

    private void runAction(final PipelineContext pipelineContext, Element eventHandlerElement, XFormsEvent XFormsEvent) {

        final String actionNamespaceURI = eventHandlerElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        final String actionEventName = eventHandlerElement.getName();

        if (XFormsActions.XFORMS_SETVALUE_ACTION.equals(actionEventName)) {
            // 10.1.9 The setvalue Element
            // xforms:setvalue

            // Set binding for current action element
            setBinding(pipelineContext, eventHandlerElement);

            final String value = eventHandlerElement.attributeValue("value");
            final String content = eventHandlerElement.getStringValue();

            final XFormsInstance currentInstance = getCurrentInstance();
            final String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                Map namespaceContext = Dom4jUtils.getNamespaceContext(eventHandlerElement);
                valueToSet = currentInstance.evaluateXPathAsString(pipelineContext, value, namespaceContext, null, functionLibrary, null);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            // Set value on current node
            Node currentNode = getCurrentSingleNode();
            XFormsInstance.setValueForNode(currentNode, valueToSet);

        } else if (XFormsActions.XFORMS_ACTION_ACTION.equals(actionEventName)) {
            // 10.1.1 The action Element
            // xforms:action

            for (Iterator i = eventHandlerElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                runAction(pipelineContext, embeddedActionElement, XFormsEvent);
            }

        } else if (XFormsActions.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element
            // xforms:toggle

            String caseId = eventHandlerElement.attributeValue("case");

            // Update xforms:switch info and dispatch events
            updateSwitchInfo(pipelineContext, caseId);

        } else if (XFormsActions.XFORMS_INSERT_ACTION.equals(actionEventName)) {
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
                final List initialInstanceNodeset = collectionToBeUpdated;
                // TODO: use initial instance to compute this - well: errata appears to have canceled this
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

            // Find list of affected repeat ids
            final Map affectedRepeatIds = new HashMap();
            visitAllControls(pipelineContext, new ControlVisitorListener() {
                private Element foundControlElement = null;
                public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                    if (foundControlElement == null && controlElement.getName().equals("repeat")) {
                        setBinding(pipelineContext, controlElement);
                        final Element currentNode = (Element) getCurrentSingleNode();
                        final Element currentParent = currentNode.getParent();
                        if (currentParent == parentElement) {
                            // Found xforms:repeat affected by the change
                            affectedRepeatIds.put(controlElement.attributeValue("id"), "");
                            foundControlElement = controlElement;
                        }
                    }
                    return true;
                }
                public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                    if (foundControlElement == controlElement)
                        foundControlElement = null;
                    return true;
                }
            });

            // Update repeat information for the ids found
            initializeRepeatInfo(pipelineContext);

            for (Iterator i = affectedRepeatIds.keySet().iterator(); i.hasNext();) {
                final String repeatId = (String) i.next();
                final RepeatInfo foundRepeatInfo = findRepeatInfo(repeatId, XFormsControls.this.repeatInfo);

                // Set new index
                foundRepeatInfo.setIndex(newNodeIndex);
            }

            // "4. If the insert is successful, the event xforms-insert is dispatched."
            currentInstance.dispatchEvent(pipelineContext, new XFormsInsertEvent(currentInstance, atAttribute));

        } else if (XFormsActions.XFORMS_DELETE_ACTION.equals(actionEventName)) {
            // 9.3.6 The delete Element

            // TODO

        } else if (XFormsActions.XFORMS_SETINDEX_ACTION.equals(actionEventName)) {
            // 9.3.7 The setindex Element

            // TODO

        } else if (XFormsActions.XFORMS_SEND_ACTION.equals(actionEventName)) {
            // 10.1.10 The send Element

            // Find submission object
            final String submissionId = eventHandlerElement.attributeValue("submission");
            if (submissionId == null)
                throw new OXFException("Missing mandatory submission attribute on xforms:send element.");
            final Object submission = containingDocument.getObjectById(pipelineContext, submissionId);
            if (submission == null || !(submission instanceof XFormsModelSubmission))
                throw new OXFException("submission attribute on xforms:send element does not refer to existing xforms:submission element.");

            // Dispatch event to submission object
            ((XFormsModelSubmission) submission).dispatchEvent(pipelineContext, new XFormsSubmitEvent(submission));

        } else if (XFormsActions.XFORMS_DISPATCH_ACTION.equals(actionEventName)) {
            // 10.1.2 The dispatch Element

            // Mandatory attributes
            final String newEventName = eventHandlerElement.attributeValue("name");
            if (newEventName == null)
                throw new OXFException("Missing mandatory name attribute on xforms:dispatch element.");
            final String newEventTargetId = eventHandlerElement.attributeValue("target");
            if (newEventTargetId == null)
                throw new OXFException("Missing mandatory target attribute on xforms:dispatch element.");

            // Optional attributes
            final boolean newEventBubbles; {
                final String newEventBubblesString = eventHandlerElement.attributeValue("bubbles");
                // FIXME: "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                newEventBubbles = Boolean.getBoolean((newEventBubblesString == null) ? "true" : newEventBubblesString);
            }
            final boolean newEventCancelable; {
                // FIXME: "The default value depends on the definition of a custom event. For predefined events, this attribute has no effect."
                final String newEventCancelableString = eventHandlerElement.attributeValue("cancelable");
                newEventCancelable = Boolean.getBoolean((newEventCancelableString == null) ? "true" : newEventCancelableString);
            }

            final Object newTargetObject = containingDocument.getObjectById(pipelineContext, newEventTargetId);

            if (newTargetObject instanceof Element) {
                // This must be a control

            } else if (newTargetObject instanceof XFormsEventTarget) {
                // This can be anything
                ((XFormsEventTarget) newTargetObject).dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(newEventName, newTargetObject, newEventBubbles, newEventCancelable));
            } else {
                throw new OXFException("Invalid event target for id: " + newEventTargetId);
            }

        } else {
            throw new OXFException("Invalid action requested: " + actionEventName);
        }
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

    public static interface ControlVisitorListener {
        public boolean startVisitControl(Element controlElement, String effectiveControlId);
        public boolean endVisitControl(Element controlElement, String effectiveControlId);
    }

    /**
     * Represents xforms:case information.
     */
    public static class SwitchInfo {
        private String switchId;
        private String selectedCaseId;
        private List deselectedCaseIds = new ArrayList();

        private Map controlsMap;
        private String previouslySelectedCaseId;

        public SwitchInfo(String switchId) {
            this.switchId = switchId;
        }

        public String getSwitchId() {
            return switchId;
        }

        public String getSelectedCaseId() {
            return selectedCaseId;
        }

        public void setSelectedCaseId(String selectedCaseId) {
            this.selectedCaseId = selectedCaseId;
        }

        public List getDeselectedCaseIds() {
            return deselectedCaseIds;
        }

        public void addDeselectedCaseId(String caseId) {
            deselectedCaseIds.add(caseId);
        }

        public void setControlForId(String id, Element controlElement) {
            if (controlsMap == null)
                controlsMap = new HashMap();
            controlsMap.put(id, controlElement);
        }

        public Element getControlForId(String id) {
            return  (Element) ((controlsMap == null) ? null : controlsMap.get(id));
        }

        public String startUpdateSelectedCaseId(String newSelectedCaseId) {

            // Remember previously selected case id
            previouslySelectedCaseId = selectedCaseId;

            if (!newSelectedCaseId.equals(selectedCaseId)) {

                // Set new selected case id
                setSelectedCaseId(newSelectedCaseId);

                // Remove new selected case id from list of deselected ids
                final List previouslyDeselected = getDeselectedCaseIds();
                if (previouslyDeselected != null) {
                    int index = previouslyDeselected.indexOf(newSelectedCaseId);
                    if (index != -1)
                        previouslyDeselected.remove(index);
                }

                // Add previously selected case id to list of deselected ids
                addDeselectedCaseId(previouslySelectedCaseId);
            }
            return previouslySelectedCaseId;
        }

        public void endUpdateSelectedCaseId() {
            controlsMap = null;
            previouslySelectedCaseId = null;
        }

        public String getPreviouslySelectedCaseId() {
            return previouslySelectedCaseId;
        }
    }

    /**
     * Represents xforms:repeat information.
     */
    public static class RepeatInfo {
        private String id;
        private int occurs;
        private int index;
        private List children;

        public RepeatInfo(String id, int index, int occurs) {
            this.id = id;
            this.index = index;
            this.occurs = occurs;
        }

        public RepeatInfo(RepeatInfo repeatInfo) {
            this.id = repeatInfo.id;
            this.occurs = repeatInfo.occurs;
            this.index = repeatInfo.index;

            if (repeatInfo.getChildren() != null) {
                this.children = new ArrayList();
                for (Iterator i = repeatInfo.getChildren().iterator(); i.hasNext();) {
                    RepeatInfo childRepeatInfo = (RepeatInfo) i.next();

                    this.children.add(new RepeatInfo(childRepeatInfo));
                }
            }
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

        public int getIndex() {
            return index;
        }

        public List getChildren() {
            return children;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public void setOccurs(int occurs) {
            this.occurs = occurs;
        }
    }

    /**
     * Represents xforms:repeat information.
     */
    public static class ItemsetInfo {
        private String id;
        private String label;
        private String value;

        public ItemsetInfo(String id, String label, String value) {
            this.id = id;
            this.label = label;
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof ItemsetInfo))
                return false;

            final ItemsetInfo other = (ItemsetInfo) obj;
            return id.equals(other.id) && label.equals(other.label) && value.equals(other.value);
        }
    }
}
