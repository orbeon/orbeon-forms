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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.XFormsLinkErrorEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.style.StandardNames;
import org.xml.sax.Locator;

import java.io.IOException;
import java.util.*;

/**
 * Represents all this XForms containing document controls and the context in which they operate.
 */
public class XFormsControls {

    private Locator locator;

    private Map itemsetIdToItemsetInfoMap;// TODO: this may be also refactored into ControlsState

    private ControlsState initialControlsState;
    private ControlsState currentControlsState;

    private Map itemsetIdToItemsetInfoUpdateMap;

    private XFormsContainingDocument containingDocument;
    private Document controlsDocument;

    protected Stack contextStack = new Stack();

    private FunctionLibrary functionLibrary = new XFormsFunctionLibrary(this);

    private static final Map groupingControls = new HashMap();
    private static final Map valueControls = new HashMap();
    private static final Map noValueControls = new HashMap();
    private static final Map leafControls = new HashMap();
    private static final Map actualControls = new HashMap();
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

        actualControls.putAll(groupingControls);
        actualControls.putAll(leafControls);

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
    }

    public void initialize(PipelineContext pipelineContext) {

        resetBindingContext();

        if (controlsDocument != null) {
            // Initialize itemset information
            itemsetIdToItemsetInfoMap = getItemsetInfo(pipelineContext, null);
            // Get initial controls state information
            initialControlsState = getControlsState(pipelineContext);
            currentControlsState = initialControlsState;
        }
    }

    /**
     * Reset the binding context to the root of the containing document.
     */
    public void resetBindingContext() {
        // Clear existing stack
        contextStack.clear();

        // Push the default context
        final XFormsModel defaultModel = containingDocument.getModel("");
        final List defaultNodeset = Arrays.asList(new Object[]{defaultModel.getDefaultInstance().getDocument().getRootElement()});
        contextStack.push(new BindingContext(defaultModel, defaultNodeset, true, null));
    }

    public Document getControlsDocument() {
        return controlsDocument;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public static boolean isValueControl(String controlName) {
        return valueControls.get(controlName) != null;
    }

    public static boolean isGroupingControl(String controlName) {
        return groupingControls.get(controlName) != null;
    }

    public static boolean isLeafControl(String controlName) {
        return leafControls.get(controlName) != null;
    }

    public static boolean isActualControl(String controlName) {
        return actualControls.get(controlName) != null;
    }

    public void setBinding(PipelineContext pipelineContext, ControlInfo controlInfo) {

        // Reinitialize context stack
        resetBindingContext();

        // Create ancestors-or-self list
        final List ancestorsOrSelf = new ArrayList();
        ControlInfo currentControlInfo = controlInfo;
        while (currentControlInfo != null) {
            ancestorsOrSelf.add(currentControlInfo);
            currentControlInfo = currentControlInfo.getParent();
        }
        Collections.reverse(ancestorsOrSelf);

        // Bind up to the specified element
        for (Iterator i = ancestorsOrSelf.iterator(); i.hasNext();) {
            pushBinding(pipelineContext, (ControlInfo) i.next());
        }
    }

    private void pushBinding(PipelineContext pipelineContext, ControlInfo controlInfo) {

        final Element bindingElement = controlInfo.getElement();
        if (!(controlInfo instanceof RepeatIterationInfo)) {
            // Regular ControlInfo backed by an element

            final String ref = bindingElement.attributeValue("ref");
            final String nodeset = bindingElement.attributeValue("nodeset");
            final String model = bindingElement.attributeValue("model");
            final String bind = bindingElement.attributeValue("bind");

            final Map bindingElementNamespaceContext =
                    (ref != null || nodeset != null) ? Dom4jUtils.getNamespaceContextNoDefault(bindingElement) : null;

            pushBinding(pipelineContext, ref, nodeset, model, bind, bindingElement, bindingElementNamespaceContext);
        } else {
            // RepeatIterationInfo

            final ControlInfo repeatControlInfo = controlInfo.getParent();
            final List repeatChildren = repeatControlInfo.getChildren();
            final List currentNodeset = getCurrentNodeset();

            if (repeatChildren.size() != currentNodeset.size())
                throw new IllegalStateException("repeatChildren and newNodeset have different sizes.");

            // Push "artificial" binding with just current node in nodeset
            final XFormsModel newModel = getCurrentModel();
            final int position = ((RepeatIterationInfo) controlInfo).getIteration();
            contextStack.push(new BindingContext(newModel, currentNodeset.subList(position - 1, position), true, null));
        }
    }

    /**
     * Set the specified element with binding at the top of the stack and build the stack for the
     * parents.
     */
    public void setBinding(PipelineContext pipelineContext, Element bindingElement) {

        // Reinitialize context stack
        resetBindingContext();

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
        final String ref = bindingElement.attributeValue("ref");
        final String nodeset = bindingElement.attributeValue("nodeset");
        final String model = bindingElement.attributeValue("model");
        final String bind = bindingElement.attributeValue("bind");

        pushBinding(pipelineContext, ref, nodeset, model, bind, bindingElement,
                (ref != null || nodeset != null) ? Dom4jUtils.getNamespaceContextNoDefault(bindingElement) : null);
    }

    public void pushBinding(PipelineContext pipelineContext, String ref, String nodeset, String model, String bind,
                            Element bindingElement, Map bindingElementNamespaceContext) {

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
                    && !(bindingElement.attribute("nodeset") != null || bindingElement.attribute("bind") != null)) {
                throw new OXFException("Missing mandatory nodeset binding for element: " + bindingElement.getQualifiedName());
            }
            if (noNodesetControls.get(controlName) != null
                    && bindingElement.attribute("nodeset") != null) {
                throw new OXFException("Node-set binding is prohibited for element: " + bindingElement.getQualifiedName());
            }
        }

        // Determine current context
        final BindingContext currentBindingContext = getCurrentContext();

        // Handle model
        final XFormsModel newModel;
        if (model != null) {
            newModel = containingDocument.getModel(model);
        } else {
            newModel = currentBindingContext.model;
        }

        // Handle nodeset
        final List newNodeset;
        {
            if (bind != null) {
                // Resolve the bind id to a node
                newNodeset = newModel.getBindNodeset(pipelineContext, newModel.getModelBindById(bind));
            } else if (ref != null || nodeset != null) {
                // Evaluate new XPath in context of current node
                newNodeset = newModel.getDefaultInstance().evaluateXPath(pipelineContext, getCurrentSingleNode(newModel.getId()),
                        ref != null ? ref : nodeset, bindingElementNamespaceContext, null, functionLibrary, null);

                if (ref != null && newNodeset.isEmpty())
                    throw new ValidationException("Single-node binding expression '"
                            + ref + "' returned an empty nodeset", new LocationData(locator));
            } else {
                // No change to current nodeset
                newNodeset = currentBindingContext.nodeset;
            }
        }

        // Push new context
        contextStack.push(new BindingContext(newModel, newNodeset, newNodeset != currentBindingContext.nodeset, bindingElement));
    }

    protected BindingContext getCurrentContext() {
        return (BindingContext) contextStack.peek();
    }

    /**
     * Get the current single node binding, if any.
     */
    public Node getCurrentSingleNode(BindingContext currentBindingContext) {
        if (currentBindingContext.nodeset.size() == 0)
            throw new ValidationException("Single node binding to nonexistent node in instance", new LocationData(locator));

        return (Node) currentBindingContext.nodeset.get(0);
    }

    /**
     * Get the current single node binding for the given model id.
     */
    public Node getCurrentSingleNode(String modelId) {

        for (int i = contextStack.size() - 1; i >= 0; i--) {
            BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            String currentModelId = currentBindingContext.model.getId();
            if ((currentModelId == null && modelId == null) || (modelId != null && modelId.equals(currentModelId)))
                return (Node) currentBindingContext.nodeset.get(0);
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

    public void popBinding() {
        if (contextStack.size() == 1)
            throw new OXFException("Attempt to clear XForms controls context stack.");
        contextStack.pop();
    }

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Update xforms:switch/xforms:case information with newly selected case id.
     */
    public void updateSwitchInfo(final PipelineContext pipelineContext, final String selectedCaseId) {

        // Find SwitchControlInfo
        final ControlInfo caseControlInfo = (ControlInfo) currentControlsState.getIdsToControlInfo().get(selectedCaseId);
        if (caseControlInfo == null)
            throw new OXFException("No ControlInfo found for case id '" + selectedCaseId + "'.");
        final ControlInfo switchControlInfo = (ControlInfo) caseControlInfo.getParent();
        if (switchControlInfo == null)
            throw new OXFException("No SwitchControlInfo found for case id '" + selectedCaseId + "'.");

        final String currentSelectedCaseId = (String) currentControlsState.getSwitchIdToSelectedCaseIdMap().get(switchControlInfo.getId());
        if (!selectedCaseId.equals(currentSelectedCaseId)) {
            // A new selection occurred on this switch

            // "This action adjusts all selected attributes on the affected cases to reflect the
            // new state, and then performs the following:"
            currentControlsState.getSwitchIdToSelectedCaseIdMap().put(switchControlInfo.getId(), selectedCaseId);

            // "1. Dispatching an xforms-deselect event to the currently selected case."
            containingDocument.dispatchEvent(pipelineContext, new org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent((XFormsEventTarget) currentControlsState.getIdsToControlInfo().get(currentSelectedCaseId)));

            // "2. Dispatching an xform-select event to the case to be selected."
            containingDocument.dispatchEvent(pipelineContext, new XFormsSelectEvent((XFormsEventTarget) currentControlsState.getIdsToControlInfo().get(selectedCaseId)));
        }
    }

    /**
     * Update switch info state for the given case id.
     */
    public void updateSwitchInfo(String caseId, boolean visible) {

        // Find SwitchControlInfo
        final ControlInfo caseControlInfo = (ControlInfo) currentControlsState.getIdsToControlInfo().get(caseId);
        if (caseControlInfo == null)
            throw new OXFException("No ControlInfo found for case id '" + caseId + "'.");
        final ControlInfo switchControlInfo = (ControlInfo) caseControlInfo.getParent();
        if (switchControlInfo == null)
            throw new OXFException("No SwitchControlInfo found for case id '" + caseId + "'.");

        // Update currently selected case id
        if (visible) {
            currentControlsState.getSwitchIdToSelectedCaseIdMap().put(switchControlInfo.getId(), caseId);
        }
    }

    private ControlsState getControlsState(final PipelineContext pipelineContext) {

        final ControlsState result = new ControlsState();

        final ControlInfo rootControlInfo = new ControlInfo(null, null, "root", null);// this is temporary and won't be stored
        final Map idsToControlInfo = new HashMap();

        final Map switchIdToSelectedCaseIdMap = new HashMap();

        visitAllControlsHandleRepeat(pipelineContext, new XFormsControls.ControlVisitorListener() {

            private ControlInfo currentControlsContainer = rootControlInfo;

            public boolean startVisitControl(Element controlElement, String effectiveControlId) {

                if (effectiveControlId == null)
                    throw new OXFException("Control element doesn't have an id: " + controlElement.getQualifiedName());

                final String controlName = controlElement.getName();

                // Check repeat iteration containers
                if (currentControlsContainer.getName().equals("repeat")) {
                    final int currentIteration = Integer.parseInt(effectiveControlId.substring(effectiveControlId.lastIndexOf('-') + 1));
                    final ControlInfo repeatIterationInfo = new RepeatIterationInfo(currentControlsContainer, currentIteration);
                    currentControlsContainer.addChild(repeatIterationInfo);
                    currentControlsContainer = repeatIterationInfo;
                } else if (currentControlsContainer instanceof RepeatIterationInfo) {
                    final int currentIteration = Integer.parseInt(effectiveControlId.substring(effectiveControlId.lastIndexOf('-') + 1));
                    final int containerIteration = ((RepeatIterationInfo) currentControlsContainer).getIteration();
                    if (currentIteration != containerIteration) {
                        final ControlInfo repeatIterationInfo = new RepeatIterationInfo(currentControlsContainer.getParent(), currentIteration);
                        currentControlsContainer.getParent().addChild(repeatIterationInfo);
                        currentControlsContainer = repeatIterationInfo;
                    }
                }

                // Create ControlInfo with basic information
                final ControlInfo controlInfo;
                {
                    if (controlName.equals("repeat")) {
                        final RepeatControlInfo repeatControlInfo = new RepeatControlInfo(currentControlsContainer, controlElement, controlElement.getName(), effectiveControlId);
                        result.setInitialRepeatIndex(controlElement.attributeValue("id"), repeatControlInfo.getStartIndex());
                        controlInfo = repeatControlInfo;
                    } else if (controlName.equals("submit")) {
                        controlInfo = new SubmitControlInfo(currentControlsContainer, controlElement, controlName, effectiveControlId);
                    } else {
                        controlInfo = new ControlInfo(currentControlsContainer, controlElement, controlName, effectiveControlId);
                    }
                }
                idsToControlInfo.put(effectiveControlId, controlInfo);

                // Handle xforms:case
                if (controlName.equals("case")) {
                    if (!(currentControlsContainer.getName().equals("switch")))
                        throw new OXFException("xforms:case with id '" + effectiveControlId + "' is not directly within an xforms:switch container.");
                    final String switchId = currentControlsContainer.getId();

                    if (switchIdToSelectedCaseIdMap.get(switchId) == null) {
                        // If case is not already selected for this switch and there is a select attribute, set it
                        final String selectedAttribute = controlElement.attributeValue("selected");
                        if ("true".equals(selectedAttribute))
                            switchIdToSelectedCaseIdMap.put(switchId, effectiveControlId);
                    }
                }

                // Get control children values
                controlInfo.setLabel(getLabelValue(pipelineContext));
                controlInfo.setHelp(getHelpValue(pipelineContext));
                controlInfo.setHint(getHintValue(pipelineContext));
                controlInfo.setAlert(getAlertValue(pipelineContext));

                // Set current binding for control element
                final Node currentNode = getCurrentSingleNode();

                // Get model item properties
                final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(currentNode);
                if (instanceData != null) {
                    controlInfo.setReadonly(instanceData.getReadonly().get());
                    controlInfo.setRequired(instanceData.getRequired().get());
                    controlInfo.setRelevant(instanceData.getRelevant().get());
                    controlInfo.setValid(instanceData.getValid().get());
                    final int typeCode = instanceData.getType().get();
                    if (typeCode != 0) {
                        controlInfo.setType(StandardNames.getPrefix(typeCode) + ":" + StandardNames.getLocalName(typeCode));
                    }
                }

                // Get current value if possible for this control
                if (isValueControl(controlName)) {
                    controlInfo.setValue(XFormsInstance.getValueForNode(currentNode));
                }

                // Handle grouping controls
                currentControlsContainer.addChild(controlInfo);
                if (isGroupingControl(controlName)) {
                    currentControlsContainer = controlInfo;
                }

                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {

                final String controlName = controlElement.getName();

                // Handle xforms:switch
                if (controlName.equals("switch")) {

                    if (switchIdToSelectedCaseIdMap.get(effectiveControlId) == null) {
                        // No case was selected, select first case id
                        final List children = currentControlsContainer.getChildren();
                        if (children != null && children.size() > 0)
                            switchIdToSelectedCaseIdMap.put(effectiveControlId, ((ControlInfo) children.get(0)).getId());
                    }
                }

                // Handle grouping controls
                if (isGroupingControl(controlName)) {
                    if (controlName.equals("repeat") && currentControlsContainer instanceof RepeatIterationInfo) {
                        // Store number of repeat iterations for the effective id
                        result.setRepeatIterations(effectiveControlId, currentControlsContainer.getParent().getChildren().size());
                        // Get back to parent of repeat
                        currentControlsContainer = currentControlsContainer.getParent().getParent();
                    } else {
                        currentControlsContainer = currentControlsContainer.getParent();
                    }
                }

                return true;
            }
        });

        // Make it so that all the root ControlInfo don't have a parent
        final List rootChildren = rootControlInfo.getChildren();
        for (Iterator i = rootChildren.iterator(); i.hasNext();) {
            final ControlInfo currentControlInfo = (ControlInfo) i.next();
            currentControlInfo.detach();
        }

        result.setChildren(rootChildren);
        result.setIdsToControlInfo(idsToControlInfo);
        result.setSwitchIdToSelectedCaseIdMap(switchIdToSelectedCaseIdMap);

        return result;
    }

    /**
     * Get the ControlsState computed right at the end of the initialize() method.
     */
    public ControlsState getInitialControlsState() {
        return initialControlsState;
    }

    /**
     * Get the last computed ControlsState.
     */
    public ControlsState getCurrentControlsState() {
        return currentControlsState;
    }

    /**
     * Rebuild the current ControlInfo and other controls state information.
     */
    public void rebuildCurrentControlsState(PipelineContext pipelineContext) {
        // Create new controls state
        final ControlsState result = getControlsState(pipelineContext);

        // Transfer some of the previous information
        // Keep repeat index information
        final Map currentRepeatIdToIndex = currentControlsState.getRepeatIdToIndex();
        if (currentRepeatIdToIndex != null) {
            result.setRepeatIdToIndex(currentRepeatIdToIndex);
        }
        // Keep switch index information
        final Map switchIdToSelectedCaseIdMap = currentControlsState.getSwitchIdToSelectedCaseIdMap();
        if (switchIdToSelectedCaseIdMap != null) {
            result.setSwitchIdToSelectedCaseIdMap(switchIdToSelectedCaseIdMap);
        }

        currentControlsState = result;
    }

    /**
     * Compute all default xforms:itemset information.
     */
    private Map getItemsetInfo(final PipelineContext pipelineContext, final XFormsModel model) {
        final Map[] resultMap = new Map[1];
        visitAllControlsHandleRepeat(pipelineContext, new ControlVisitorListener() {
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                final String controlName = controlElement.getName();
                if (controlName.equals("select") || controlName.equals("select1")) {

                    final Element itemsetElement = controlElement.element(XFormsConstants.XFORMS_ITEMSET_QNAME);

                    if (itemsetElement != null) {
                        final String selectControlId = controlElement.attributeValue("id");

                        // Iterate through the collection
                        pushBinding(pipelineContext, itemsetElement);
                        {
                            final BindingContext currentBindingContext = getCurrentContext();

                            if (model == null || model == currentBindingContext.model) { // it is possible to filter on a particular model
                                final List items = new ArrayList();
                                for (Iterator i = getCurrentNodeset().iterator(); i.hasNext();) {
                                    Node currentNode = (Node) i.next();

                                    // Push "artificial" binding with just current node in nodeset
                                    contextStack.push(new BindingContext(currentBindingContext.model, Collections.singletonList(currentNode), true, null));
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
        final Map repeatIdToIndex = currentControlsState.getRepeatIdToIndex();
        final Integer currentIndex = (Integer) repeatIdToIndex.get(repeatId);
        return (currentIndex == null) ? -1 : currentIndex.intValue();
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
     * Get the object with the id specified.
     */
    public Object getObjectById(String controlId) {
        return currentControlsState.getIdsToControlInfo().get(controlId);
    }

    /**
     * Visit all the effective controls elements.
     */
    public void visitAllControlsHandleRepeat(PipelineContext pipelineContext, ControlVisitorListener controlVisitorListener) {
        resetBindingContext();
        handleControls(pipelineContext, controlVisitorListener, controlsDocument.getRootElement(), "");
    }

    private boolean handleControls(PipelineContext pipelineContext, ControlVisitorListener controlVisitorListener,
                                   Element container, String idPostfix) {
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
                    final BindingContext currentBindingContext = getCurrentContext();

                    // Visit xforms:repeat element
                    doContinue = controlVisitorListener.startVisitControl(controlElement, effectiveControlId);

                    // Iterate over current xforms:repeat nodeset
                    final List currentNodeset = getCurrentNodeset();
                    int currentIndex = 1;
                    for (Iterator j = currentNodeset.iterator(); j.hasNext(); currentIndex++) {
                        Node currentNode = (Node) j.next();

                        // Push "artificial" binding with just current node in nodeset
                        contextStack.push(new BindingContext(currentBindingContext.model, Collections.singletonList(currentNode), true, null));
                        try {
                            // Handle children of xforms:repeat
                            if (doContinue)
                                doContinue = handleControls(pipelineContext, controlVisitorListener, controlElement, idPostfix + "-" + currentIndex);
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
                        doContinue = handleControls(pipelineContext, controlVisitorListener, controlElement, idPostfix);
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
                    containingDocument.dispatchEvent(pipelineContext, new XFormsLinkErrorEvent(currentModel, srcAttributeValue, childElement, e));
                }
            }
        }

        // Try to get static value
        if (result == null)
            result = childElement.getStringValue();

        popBinding();
        return result;
    }

    protected static class BindingContext {
        public List nodeset;
        public XFormsModel model;
        public boolean newBind;
        public Element controlElement;

        public BindingContext(XFormsModel model, List nodeSet, boolean newBind, Element controlElement) {
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

    public static interface ControlInfoVisitorListener {
        public void startVisitControl(ControlInfo controlInfo);
        public void endVisitControl(ControlInfo controlInfo);
    }

    /**
     * Represents the state of a tree of XForms controls.
     */
    public static class ControlsState {
        private List children;
        private Map idsToControlInfo;
        private Map initialRepeatIdToIndex;
        private Map repeatIdToIndex;
        private Map effectiveRepeatIdToIterations;
        private Map switchIdToSelectedCaseIdMap;

        public ControlsState() {
        }

        public void setChildren(List children) {
            this.children = children;
        }

        public void setIdsToControlInfo(Map idsToControlInfo) {
            this.idsToControlInfo = idsToControlInfo;
        }

        public void setInitialRepeatIndex(String controlId, int index) {
            if (initialRepeatIdToIndex == null)
                initialRepeatIdToIndex = new HashMap();
            initialRepeatIdToIndex.put(controlId, new Integer(index));
        }

        public void updateRepeatIndex(String controlId, int index) {
            if (repeatIdToIndex == null)
                repeatIdToIndex = new HashMap(initialRepeatIdToIndex);
            repeatIdToIndex.put(controlId, new Integer(index));
        }

        public void setRepeatIdToIndex(Map repeatIdToIndex) {
            this.repeatIdToIndex = new HashMap(repeatIdToIndex);
        }

        public void setRepeatIterations(String effectiveControlId, int iterations) {
            if (effectiveRepeatIdToIterations == null)
                effectiveRepeatIdToIterations = new HashMap();
            effectiveRepeatIdToIterations.put(effectiveControlId, new Integer(iterations));
        }

        public List getChildren() {
            return children;
        }

        public Map getIdsToControlInfo() {
            return idsToControlInfo;
        }

        public Map getSwitchIdToSelectedCaseIdMap() {
            return switchIdToSelectedCaseIdMap;
        }

        public void setSwitchIdToSelectedCaseIdMap(Map switchIdToSelectedCaseIdMap) {
            this.switchIdToSelectedCaseIdMap = switchIdToSelectedCaseIdMap;
        }

        public Map getInitialRepeatIdToIndex() {
            return initialRepeatIdToIndex;
        }

        public Map getRepeatIdToIndex() {
            if (repeatIdToIndex == null && initialRepeatIdToIndex != null)
                repeatIdToIndex = new HashMap(initialRepeatIdToIndex);
            return repeatIdToIndex;
        }

        public Map getEffectiveRepeatIdToIterations() {
            return effectiveRepeatIdToIterations;
        }

        /**
         * Return the list of repeat ids descendent of a given repeat id, null if none.
         */
        public List getNestedRepeatIds(String repeatId) {
            final Map repeatIdToRepeatControlInfo = getRepeatIdToRepeatControlInfo();
            final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) repeatIdToRepeatControlInfo.get(repeatId);
            final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();
            final List newChildren = repeatControlInfo.getChildren();
            if (newChildren != null && newChildren.size() > 0) {
                Map result = new HashMap();
                visitRepeatHierarchy(result, Collections.singletonList(newChildren.get(index - 1)));
                return new ArrayList(result.keySet());
            } else {
                return null;
            }
        }

        /**
         * Return a map of repeat ids -> RepeatControlInfo objects, following the branches of the
         * current indexes of the repeat elements.
         */
        public Map getRepeatIdToRepeatControlInfo() {
            final Map result = new HashMap();
            visitRepeatHierarchy(result);
            return result;
        }

        private void visitRepeatHierarchy(Map result) {
            visitRepeatHierarchy(result, this.children);
        }

        private void visitRepeatHierarchy(Map result, List children) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final ControlInfo currentControlInfo = (ControlInfo) i.next();

                if ("repeat".equals(currentControlInfo.getName())) {

                    final String repeatId = currentControlInfo.getElement().attributeValue("id");
                    final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();

                    result.put(repeatId, currentControlInfo);

                    if (index > 0) {
                        final List newChildren = currentControlInfo.getChildren();
                        if (newChildren != null && newChildren.size() > 0)
                            visitRepeatHierarchy(result, Collections.singletonList(newChildren.get(index - 1)));
                    }

                } else {
                    final List newChildren = currentControlInfo.getChildren();
                    if (newChildren != null)
                        visitRepeatHierarchy(result, newChildren);
                }
            }
        }
    }

    /**
     * Represents XForms control state information.
     */
    public class ControlInfo implements XFormsEventTarget, XFormsEventHandlerContainer {

        private ControlInfo parent;
        private String name;

        private Element element;

        private String id;
        private String label;
        private String help;
        private String hint;
        private String alert;
        private String value;

        private boolean readonly;
        private boolean required;
        private boolean relevant;
        private boolean valid;
        private String type;

        private List children;
        private List eventHandlers;

        public ControlInfo(ControlInfo parent, Element element, String name, String id) {
            this.parent = parent;
            this.element = element;
            this.name = name;
            this.id = id;

            // Extract event handlers
            if (element != null)
                eventHandlers = XFormsEventHandlerImpl.extractEventHandlers(containingDocument, this, element);
        }

        public void addChild(ControlInfo controlInfo) {
            if (children == null)
                children = new ArrayList();
            children.add(controlInfo);
        }

        public String getId() {
            return id;
        }

        public List getChildren() {
            return children;
        }

        public String getAlert() {
            return alert;
        }

        public void setAlert(String alert) {
            this.alert = alert;
        }

        public String getHelp() {
            return help;
        }

        public void setHelp(String help) {
            this.help = help;
        }

        public String getHint() {
            return hint;
        }

        public void setHint(String hint) {
            this.hint = hint;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isReadonly() {
            return readonly;
        }

        public void setReadonly(boolean readonly) {
            this.readonly = readonly;
        }

        public boolean isRelevant() {
            return relevant;
        }

        public void setRelevant(boolean relevant) {
            this.relevant = relevant;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public ControlInfo getParent() {
            return parent;
        }

        public void detach() {
            this.parent = null;
        }

        public Element getElement() {
            return element;
        }

        public boolean equals(Object obj) {

            if (obj == null || !(obj instanceof ControlInfo))
                return false;

            if (this == obj)
                return true;

            final ControlInfo other = (ControlInfo) obj;

            if (!((name == null && other.name == null) || (name != null && other.name != null && name.equals(other.name))))
                return false;
            if (!((id == null && other.id == null) || (id != null && other.id != null && id.equals(other.id))))
                return false;
            if (!((label == null && other.label == null) || (label != null && other.label != null && label.equals(other.label))))
                return false;
            if (!((help == null && other.help == null) || (help != null && other.help != null && help.equals(other.help))))
                return false;
            if (!((hint == null && other.hint == null) || (hint != null && other.hint != null && hint.equals(other.hint))))
                return false;
            if (!((alert == null && other.alert == null) || (alert != null && other.alert != null && alert.equals(other.alert))))
                return false;
            if (!((value == null && other.value == null) || (value != null && other.value != null && value.equals(other.value))))
                return false;

            if (readonly != other.readonly)
                return false;
            if (required != other.required)
                return false;
            if (relevant != other.relevant)
                return false;
            if (valid != other.valid)
                return false;

            if (!((type == null && other.type == null) || (type != null && other.type != null && type.equals(other.type))))
                return false;

            return true;
        }

        public XFormsEventHandlerContainer getParentContainer() {
            return parent;
        }

        public List getEventHandlers() {
            return eventHandlers;
        }

        public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
            if (XFormsEvents.XFORMS_DOM_FOCUS_IN.equals(event.getEventName())) {

                // Try to update xforms:repeat indices based on this
                {
                    final List ancestorRepeats = new ArrayList();
                    final Map ancestorRepeatsMap = new HashMap();

                    // Find current path through ancestor xforms:repeat elements, if any
                    ControlInfo currentControlInfo = getParent();
                    while (currentControlInfo != null) {

                        if (currentControlInfo instanceof RepeatIterationInfo) {
                            final RepeatIterationInfo repeatIterationInfo = (RepeatIterationInfo) currentControlInfo;
                            final int iteration = repeatIterationInfo.getIteration();
                            final String repeatId = repeatIterationInfo.getParent().getElement().attributeValue("id");

                            ancestorRepeats.add(repeatId);
                            ancestorRepeatsMap.put(repeatId,  new Integer(iteration));
                        }

                        currentControlInfo = currentControlInfo.getParent();
                    }

                    if (ancestorRepeats.size() > 0) {

                        // Update ControlsState if needed as we are going to make some changes
                        if (initialControlsState == currentControlsState)
                            rebuildCurrentControlsState(pipelineContext);

                        // Iterate from root to leaf
                        Collections.reverse(ancestorRepeats);
                        for (Iterator i = ancestorRepeats.iterator(); i.hasNext();) {
                            final String repeatId = (String) i.next();
                            final Integer iteration = (Integer) ancestorRepeatsMap.get(repeatId);

                            currentControlsState.updateRepeatIndex(repeatId, iteration.intValue());
                        }
                    }
                }
            }
        }
    }

    public class SubmitControlInfo extends ControlInfo {
        public SubmitControlInfo(ControlInfo parent, Element element, String name, String id) {
            super(parent, element, name, id);
        }

        public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
            // Do the default stuff upon receiving a DOMActivate event
            if (XFormsEvents.XFORMS_DOM_ACTIVATE.equals(event.getEventName())) {

                // Find submission id
                final String submissionId = getElement().attributeValue("submission");
                if (submissionId == null)
                    throw new OXFException("xforms:submit requires a submission attribute.");

                // Find submission object and dispatch submit event to it
                final Object object = containingDocument.getObjectById(pipelineContext, submissionId);
                if (object instanceof XFormsModelSubmission) {
                    final XFormsModelSubmission submission = (XFormsModelSubmission) object;
                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitEvent(submission));
                } else {
                    throw new OXFException("xforms:submit submission attribute must point to an xforms:submission element.");
                }
            }
            super.performDefaultAction(pipelineContext, event);
        }
    }

    /**
     * Represents xforms:repeat iteration information.
     *
     * This is not really a control, but an abstraction for xforms:repeat branches.
     */
    public class RepeatIterationInfo extends ControlInfo {
        private int iteration;
        public RepeatIterationInfo(ControlInfo parent, int iteration) {
            super(parent, null, "xxforms-repeat-iteration", null);
            this.iteration = iteration;
        }

        public int getIteration() {
            return iteration;
        }

        public boolean equals(Object obj) {
            if (!super.equals(obj))
                return false;
            final RepeatIterationInfo other = (RepeatIterationInfo) obj;
            return this.iteration == other.iteration;
        }
    }

    /**
     * Represents xforms:repeat information.
     */
    public class RepeatControlInfo extends ControlInfo {
        private int startIndex;
        public RepeatControlInfo(ControlInfo parent, Element element, String name, String id) {
            super(parent, element, name, id);

            // Store initial repeat index information
            final String startIndexString = element.attributeValue("startindex");
            this.startIndex = (startIndexString != null) ? Integer.parseInt(startIndexString) : 1;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public boolean equals(Object obj) {
            if (!super.equals(obj))
                return false;
            final RepeatControlInfo other = (RepeatControlInfo) obj;
            return this.startIndex == other.startIndex;
        }
    }

    /**
     * Represents xforms:itemset information.
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
