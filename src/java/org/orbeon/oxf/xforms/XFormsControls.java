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
import org.orbeon.oxf.xforms.controls.*;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsLinkErrorEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.xml.sax.Locator;

import java.io.IOException;
import java.util.*;

/**
 * Represents all this XForms containing document controls and the context in which they operate.
 */
public class XFormsControls {

    private Locator locator;

    private boolean initialized;
    private ControlsState initialControlsState;
    private ControlsState currentControlsState;

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

    public XFormsControls(XFormsContainingDocument containingDocument, Document controlsDocument, Element repeatIndexesElement) {
        this.containingDocument = containingDocument;
        this.controlsDocument = controlsDocument;

        // Build minimal state with repeat indexes so that index() function works in XForms models
        // initialization
        initializeMinimal(repeatIndexesElement);
    }

    private void initializeMinimal(Element repeatIndexesElement) {
        // Set initial repeat index
        if (controlsDocument != null) {
            final ControlsState result = new ControlsState();
            getDefaultRepeatIndexes(result);
            initialControlsState = result;
            currentControlsState = initialControlsState;
        }

        // Set repeat index state if any
        setRepeatIndexState(repeatIndexesElement);
    }

    /**
     * Iterate statically through controls and set the default repeat index for xforms:repeat.
     *
     * @param controlsState    ControlsState to update with setDefaultRepeatIndex()
     */
    private void getDefaultRepeatIndexes(final ControlsState controlsState) {
        visitAllControlStatic(new ControlElementVisitorListener() {

            private Stack repeatStack = new Stack();

            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                if (controlElement.getName().equals("repeat")) {
                    // Create control without parent, just to hold iterations
                    final RepeatControlInfo repeatControlInfo
                            = new RepeatControlInfo(containingDocument, null, controlElement, controlElement.getName(), effectiveControlId);

                    // Set initial index
                    controlsState.setDefaultRepeatIndex(repeatControlInfo.getRepeatId(), repeatControlInfo.getStartIndex());

                    // Keep control on stack
                    repeatStack.push(repeatControlInfo);
                }
                return true;
            }

            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                return true;
            }

            public void startRepeatIteration(int iteration) {
                // One more iteration in current repeat
                final RepeatControlInfo repeatControlInfo = (RepeatControlInfo) repeatStack.peek();
                repeatControlInfo.addChild(new RepeatIterationInfo(containingDocument, repeatControlInfo, iteration));
            }

            public void endRepeatIteration(int iteration) {
            }
        });
    }

    public void initialize(PipelineContext pipelineContext, Element divsElement, Element repeatIndexesElement) {

        if (controlsDocument != null) {

            if (initialized) {
                // Use existing controls state
                initialControlsState = currentControlsState;
            } else {
                // Rebuild controls state

                // Get initial controls state information
                initialControlsState = buildControlsState(pipelineContext);
                currentControlsState = initialControlsState;

                // Set switch state if any
                if (divsElement != null) {
                    for (Iterator i = divsElement.elements().iterator(); i.hasNext();) {
                        final Element divElement = (Element) i.next();

                        final String caseId = divElement.attributeValue("id");
                        final String visibility = divElement.attributeValue("visibility");

                        updateSwitchInfo(caseId, "visible".equals(visibility));
                    }
                }

                // Set repeat index state if any
                setRepeatIndexState(repeatIndexesElement);

                // Evaluate values after index state has been computed
                initialControlsState.evaluateValueControls(pipelineContext);
            }
        }

        resetBindingContext();

        initialized = true;
    }

    private void setRepeatIndexState(Element repeatIndexesElement) {
        if (repeatIndexesElement != null) {
            for (Iterator i = repeatIndexesElement.elements().iterator(); i.hasNext();) {
                final Element repeatIndexElement = (Element) i.next();

                final String repeatId = repeatIndexElement.attributeValue("id");
                final String index = repeatIndexElement.attributeValue("index");

                initialControlsState.updateRepeatIndex(repeatId, Integer.parseInt(index));
            }
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
        final List defaultNodeset = Arrays.asList(new Object[]{defaultModel.getDefaultInstance().getInstanceDocument().getRootElement()});
        contextStack.push(new BindingContext(defaultModel, defaultNodeset, 1, null, true, null));
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

            final int repeatChildrenSize = (repeatChildren == null) ? 0 : repeatChildren.size();
            final int currentNodesetSize = (currentNodeset == null) ? 0 : currentNodeset.size();

            if (repeatChildrenSize != currentNodesetSize)
                throw new IllegalStateException("repeatChildren and newNodeset have different sizes.");

            // Push "artificial" binding with just current node in nodeset
            final XFormsModel newModel = getCurrentModel();
            final int position = ((RepeatIterationInfo) controlInfo).getIteration();
            contextStack.push(new BindingContext(newModel, currentNodeset, position, controlInfo.getParent().getOriginalId(), true, null));
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
            newModel = currentBindingContext.getModel();
        }

        // Handle nodeset
        final List newNodeset;
        {
            if (bind != null) {
                // Resolve the bind id to a node
                newNodeset = newModel.getBindNodeset(pipelineContext, newModel.getModelBindById(bind));
            } else if (ref != null || nodeset != null) {
                // Evaluate new XPath in context of current node
                final Node currentSingleNodeForModel = getCurrentSingleNode(newModel.getId());
                if (currentSingleNodeForModel != null) {
                    newNodeset = newModel.getDefaultInstance().evaluateXPath(pipelineContext, currentSingleNodeForModel,
                            ref != null ? ref : nodeset, bindingElementNamespaceContext, null, functionLibrary, null);

//                    if (ref != null && newNodeset.isEmpty())
//                        throw new ValidationException("Single-node binding expression '"
//                                + ref + "' returned an empty nodeset", new LocationData(locator));
                } else {
                    newNodeset = null;
                }
            } else {
                // No change to current nodeset
                newNodeset = currentBindingContext.getNodeset();
            }
        }

        // Push new context
        final boolean newBind = newNodeset != currentBindingContext.getNodeset();
        final String id = (bindingElement == null) ? null : bindingElement.attributeValue("id");
        contextStack.push(new BindingContext(newModel, newNodeset, newBind ? 1 : currentBindingContext.getPosition(), id, newBind, bindingElement));
    }

    /**
     * NOTE: Not sure this should be exposed.
     */
    public Stack getContextStack() {
        return contextStack;
    }

    /**
     * NOTE: Not sure this should be exposed.
     */
    public BindingContext getCurrentContext() {
        return (BindingContext) contextStack.peek();
    }

    /**
     * Get the current single node binding for the given model id.
     */
    public Node getCurrentSingleNode(String modelId) {

        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final String currentModelId = currentBindingContext.getModel().getId();
            if ((currentModelId == null && modelId == null) || (modelId != null && modelId.equals(currentModelId)))
                return currentBindingContext.getSingleNode();
        }

        // If not found, return the document element of the model's default instance
        return containingDocument.getModel(modelId).getDefaultInstance().getInstanceDocument();
    }

    /**
     * Get the current single node binding, if any.
     */
    public Node getCurrentSingleNode() {
        return getCurrentContext().getSingleNode();
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
        return getCurrentContext().getNodeset();
    }

    /**
     * Get the current position in current nodeset binding.
     */
    public int getCurrentPosition() {
        return getCurrentContext().getPosition();
    }

    /**
     * Return the single node associated with the iteration of the repeat specified. If a null
     * repeat id is passed, return the single node associated with the closest enclosing repeat
     * iteration.
     *
     * @param repeatId  enclosing repeat id, or null
     * @return          the single node
     */
    public Node getRepeatCurrentSingleNode(String repeatId) {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final Element bindingElement = currentBindingContext.getControlElement();
            final String repeatIdForIteration = currentBindingContext.getIdForContext();
            if (bindingElement == null && repeatIdForIteration != null) {
                if (repeatId == null || repeatId.equals(repeatIdForIteration)) {
                    // Found binding context for relevant repeat iteration
                    return currentBindingContext.getSingleNode();
                }
            }
        }
        // It is required that there is a relevant enclosing xforms:repeat
        if (repeatId == null)
            throw new OXFException("Enclosing xforms:repeat not found.");
        else
            throw new OXFException("Enclosing xforms:repeat not found for id: " + repeatId);
    }

    /**
     * Return the context node-set based on the enclosing xforms:repeat, xforms:group or
     * xforms:switch, either the closest one if no argument is passed, or context at the level of
     * the element with the given id passed.
     *
     * @param contextId  enclosing context id, or null
     * @return           the node-set
     */
    public List getCurrentContext(String contextId) {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final Element bindingElement = currentBindingContext.getControlElement();
            final String idForContext = currentBindingContext.getIdForContext();
            if (bindingElement != null && idForContext != null) {
                if (contextId == null || contextId.equals(idForContext)) {
                    // Found binding context for relevant repeat iteration
                    return currentBindingContext.getNodeset();
                }
            }
        }
        // It is required that there is a relevant enclosing xforms:repeat
        if (contextId == null)
            throw new OXFException("Enclosing XForms element not found.");
        else
            throw new OXFException("Enclosing XForms element not found for id: " + contextId);
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
            containingDocument.dispatchEvent(pipelineContext, new XFormsDeselectEvent((XFormsEventTarget) currentControlsState.getIdsToControlInfo().get(currentSelectedCaseId)));

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

    private ControlsState buildControlsState(final PipelineContext pipelineContext) {

        final ControlsState result = new ControlsState();

        final ControlInfo rootControlInfo = new ControlInfo(containingDocument, null, null, "root", null);// this is temporary and won't be stored
        final Map idsToControlInfo = new HashMap();

        final Map switchIdToSelectedCaseIdMap = new HashMap();
        final List valueControls = new ArrayList();

        visitAllControlsHandleRepeat(pipelineContext, new XFormsControls.ControlElementVisitorListener() {

            private ControlInfo currentControlsContainer = rootControlInfo;

            public boolean startVisitControl(Element controlElement, String effectiveControlId) {

                if (effectiveControlId == null)
                    throw new OXFException("Control element doesn't have an id: " + controlElement.getQualifiedName());

                final String controlName = controlElement.getName();

                // Create ControlInfo with basic information
                final ControlInfo controlInfo;
                {
                    // TODO: Create one ControlInfo per control, and use a factory to create those
                    if (controlName.equals("input")) {
                        controlInfo = new InputControlInfo(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                    } else if (controlName.equals("repeat")) {
                        controlInfo = new RepeatControlInfo(containingDocument, currentControlsContainer, controlElement, controlElement.getName(), effectiveControlId);
                        result.setHasRepeat(true);
                    } else if (controlName.equals("select")) {
                        controlInfo = new SelectControlInfo(containingDocument, currentControlsContainer, controlElement, controlElement.getName(), effectiveControlId);
                    } else if (controlName.equals("select1")) {
                        controlInfo = new Select1ControlInfo(containingDocument, currentControlsContainer, controlElement, controlElement.getName(), effectiveControlId);
                    } else if (controlName.equals("submit")) {
                        controlInfo = new SubmitControlInfo(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                    } else if (controlName.equals("output")) {
                        controlInfo = new OutputControlInfo(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                    } else if (controlName.equals("range")) {
                        controlInfo = new RangeControlInfo(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                    } else if (controlName.equals("upload")) {
                        controlInfo = new UploadControlInfo(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                        result.setHasUpload(true);
                    } else {
                        controlInfo = new ControlInfo(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                    }
                }
                // Make sure there are no duplicate ids
                if (idsToControlInfo.get(effectiveControlId) != null)
                    throw new ValidationException("Duplicate id for XForms control", new ExtendedLocationData((LocationData) controlElement.getData(),
                            "analyzing control element", controlElement));

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

                // Handle xforms:itemset
                if (controlInfo instanceof SelectControlInfo || controlInfo instanceof Select1ControlInfo) {
                    ((Select1ControlInfo) controlInfo).evaluateItemsets(pipelineContext);
                }

                // Get control children values
                controlInfo.setLabel(getLabelValue(pipelineContext));
                controlInfo.setHelp(getHelpValue(pipelineContext));
                controlInfo.setHint(getHintValue(pipelineContext));
                controlInfo.setAlert(getAlertValue(pipelineContext));

                // Set current binding for control element
                final BindingContext currentBindingContext = getCurrentContext();
                final List currentNodeSet = currentBindingContext.getNodeset();

                if (!(controlInfo instanceof RepeatControlInfo && currentNodeSet != null && currentNodeSet.size() == 0)) {
                    final Node currentNode = currentBindingContext.getSingleNode();
                    if (currentNode != null) {
                        // Control is bound to a node - get model item properties
                        final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(currentNode);
                        if (instanceData != null) {
                            controlInfo.setReadonly(instanceData.getReadonly().get());
                            controlInfo.setRequired(instanceData.getRequired().get());
                            controlInfo.setRelevant(instanceData.getRelevant().get());
                            controlInfo.setValid(instanceData.getValid().get());
                            final String typeAsString = instanceData.getType().getAsString();
                            if (typeAsString != null) {
                                controlInfo.setType(typeAsString);
                            }
                        }
                        // If control can have a value, prepare it
                        // NOTE: We defer the evaluation because some controls like xforms:output can use the index() function
                        if (controlInfo.isValueControl()) {
                            controlInfo.prepareValue(pipelineContext, currentBindingContext);
                            valueControls.add(controlInfo);
                        }
                    } else {
                        // Control is not bound to a node - it becomes non-relevant
                        controlInfo.setReadonly(false);
                        controlInfo.setRequired(false);
                        controlInfo.setRelevant(false);
                        controlInfo.setValid(false);
                        controlInfo.setType(null);
                    }
                }

                // Add to current controls container
                currentControlsContainer.addChild(controlInfo);

                // Current grouping control becomes the current controls container
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
                    if (controlName.equals("repeat")) {
                        // Store number of repeat iterations for the effective id
                        final List children = currentControlsContainer.getChildren();

                        if (children == null || children.size() == 0) {
                            // Current index is 0
                            result.setRepeatIterations(effectiveControlId, 0);
                        } else {
                            // Number of iterations is number of children
                            result.setRepeatIterations(effectiveControlId, children.size());
                        }
                    }

                    currentControlsContainer = currentControlsContainer.getParent();
                }

                return true;
            }

            public void startRepeatIteration(int iteration) {

                final ControlInfo repeatIterationInfo = new RepeatIterationInfo(containingDocument, currentControlsContainer, iteration);
                currentControlsContainer.addChild(repeatIterationInfo);
                currentControlsContainer = repeatIterationInfo;

                // Set current binding for control element
                final Node currentNode = getCurrentSingleNode();

                // Get model item properties
                final InstanceData instanceData = XFormsUtils.getInheritedInstanceData(currentNode);
                if (instanceData != null) {
                    repeatIterationInfo.setReadonly(instanceData.getReadonly().get());
                    repeatIterationInfo.setRequired(instanceData.getRequired().get());
                    repeatIterationInfo.setRelevant(instanceData.getRelevant().get());
                    repeatIterationInfo.setValid(instanceData.getValid().get());
                }
            }

            public void endRepeatIteration(int iteration) {
                currentControlsContainer = currentControlsContainer.getParent();
            }
        });

        // Make it so that all the root ControlInfo don't have a parent
        final List rootChildren = rootControlInfo.getChildren();
        if (rootChildren != null) {
            for (Iterator i = rootChildren.iterator(); i.hasNext();) {
                final ControlInfo currentControlInfo = (ControlInfo) i.next();
                currentControlInfo.detach();
            }
        }

        result.setChildren(rootChildren);
        result.setIdsToControlInfo(idsToControlInfo);
        result.setSwitchIdToSelectedCaseIdMap(switchIdToSelectedCaseIdMap);
        result.setValueControls(valueControls);

        // Handle repeat indexes if needed
        if (result.isHasRepeat()) {
            // Get default xforms:repeat indexes beforehand
            getDefaultRepeatIndexes(result);

            // Adjust repeat indexes
            XFormsIndexUtils.adjustIndexes(pipelineContext, this, result);
        }

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
        final ControlsState result = buildControlsState(pipelineContext);

        // Transfer some of the previous information
        // Keep repeat index information
        final Map currentRepeatIdToIndex = currentControlsState.getRepeatIdToIndex();
        if (currentRepeatIdToIndex.size() != 0) {
            result.setRepeatIdToIndex(currentRepeatIdToIndex);
        }
        // Keep switch index information
        final Map switchIdToSelectedCaseIdMap = currentControlsState.getSwitchIdToSelectedCaseIdMap();
        if (switchIdToSelectedCaseIdMap != null) {
            result.setSwitchIdToSelectedCaseIdMap(switchIdToSelectedCaseIdMap);
        }

        // Remember new state
        currentControlsState = result;

        // Evaluate controls values
        currentControlsState.evaluateValueControls(pipelineContext);
    }

    /**
     * Perform a refresh of the controls for a given model
     */
    public void refreshForModel(final PipelineContext pipelineContext, final XFormsModel model) {
        // NOP
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
        return getCurrentContext().getModel();
    }

    public XFormsInstance getCurrentInstance() {
        final Node currentSingleNode = getCurrentSingleNode();
        if (currentSingleNode != null)
            return getCurrentContext().getModel().getInstanceForNode(currentSingleNode);
        else
            return null;
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
    public void visitAllControlsHandleRepeat(PipelineContext pipelineContext, ControlElementVisitorListener controlElementVisitorListener) {
        resetBindingContext();
        handleControls(pipelineContext, controlElementVisitorListener, controlsDocument.getRootElement(), "");
    }

    private boolean handleControls(PipelineContext pipelineContext, ControlElementVisitorListener controlElementVisitorListener,
                                   Element container, String idPostfix) {
        boolean doContinue = true;
        for (Iterator i = container.elements().iterator(); i.hasNext();) {
            final Element controlElement = (Element) i.next();
            final String controlName = controlElement.getName();

            final String controlId = controlElement.attributeValue("id");
            final String effectiveControlId = controlId + (idPostfix.equals("") ? "" : XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + idPostfix);

            if (controlName.equals("repeat")) {
                // Handle xforms:repeat

                // Push binding for xforms:repeat
                pushBinding(pipelineContext, controlElement);
                try {
                    final BindingContext currentBindingContext = getCurrentContext();

                    // Visit xforms:repeat element
                    doContinue = controlElementVisitorListener.startVisitControl(controlElement, effectiveControlId);

                    // Iterate over current xforms:repeat nodeset
                    final List currentNodeSet = getCurrentNodeset();
                    if (currentNodeSet != null) {
                        for (int currentPosition = 1; currentPosition <= currentNodeSet.size(); currentPosition++) {
                            // Push "artificial" binding with just current node in nodeset
                            contextStack.push(new BindingContext(currentBindingContext.getModel(), currentNodeSet, currentPosition, controlId, true, null));
                            try {
                                // Handle children of xforms:repeat
                                if (doContinue) {
                                    controlElementVisitorListener.startRepeatIteration(currentPosition);
                                    final String newIdPostfix = idPostfix.equals("") ? Integer.toString(currentPosition) : (idPostfix + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + currentPosition);
                                    doContinue = handleControls(pipelineContext, controlElementVisitorListener, controlElement, newIdPostfix);
                                    controlElementVisitorListener.endRepeatIteration(currentPosition);
                                }
                            } finally {
                                contextStack.pop();
                            }
                            if (!doContinue)
                                break;
                        }
                    }

                    doContinue = doContinue && controlElementVisitorListener.endVisitControl(controlElement, effectiveControlId);

                } finally {
                    popBinding();
                }

            } else  if (isGroupingControl(controlName)) {
                // Handle XForms grouping controls
                pushBinding(pipelineContext, controlElement);
                try {
                    doContinue = controlElementVisitorListener.startVisitControl(controlElement, effectiveControlId);
                    if (doContinue)
                        doContinue = handleControls(pipelineContext, controlElementVisitorListener, controlElement, idPostfix);
                    doContinue = doContinue && controlElementVisitorListener.endVisitControl(controlElement, effectiveControlId);
                } finally {
                    popBinding();
                }
            } else if (isLeafControl(controlName)) {
                // Handle leaf control
                pushBinding(pipelineContext, controlElement);
                try {
                    doContinue = controlElementVisitorListener.startVisitControl(controlElement, effectiveControlId);
                    doContinue = doContinue && controlElementVisitorListener.endVisitControl(controlElement, effectiveControlId);
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
     * Visit all the control elements without handling repeats or looking at the binding contexts.
     */
    public void visitAllControlStatic(ControlElementVisitorListener controlElementVisitorListener) {
        handleControlsStatic(controlElementVisitorListener, controlsDocument.getRootElement());
    }

    private boolean handleControlsStatic(ControlElementVisitorListener controlElementVisitorListener, Element container) {
        boolean doContinue = true;
        for (Iterator i = container.elements().iterator(); i.hasNext();) {
            final Element controlElement = (Element) i.next();
            final String controlName = controlElement.getName();

            final String controlId = controlElement.attributeValue("id");

            if (isGroupingControl(controlName)) {
                // Handle XForms grouping controls
                doContinue = controlElementVisitorListener.startVisitControl(controlElement, controlId);
                if (doContinue)
                    doContinue = handleControlsStatic(controlElementVisitorListener, controlElement);
                doContinue = doContinue && controlElementVisitorListener.endVisitControl(controlElement, controlId);
            } else if (isLeafControl(controlName)) {
                // Handle leaf control
                doContinue = controlElementVisitorListener.startVisitControl(controlElement, controlId);
                doContinue = doContinue && controlElementVisitorListener.endVisitControl(controlElement, controlId);
            }
            if (!doContinue)
                break;
        }
        return doContinue;
    }

    /**
     * Visit all the current ControlInfo.
     */
    public void visitAllControlInfo(ControlInfoVisitorListener controlInfoVisitorListener) {
        handleControlInfo(controlInfoVisitorListener, currentControlsState.getChildren());
    }

    /**
     * Visit all the children of the given ControlInfo.
     */
    public void visitAllControlInfo(ControlInfoVisitorListener controlInfoVisitorListener, ControlInfo currentControlInfo) {
        handleControlInfo(controlInfoVisitorListener, currentControlInfo.getChildren());
    }

    private void handleControlInfo(ControlInfoVisitorListener controlInfoVisitorListener, List children) {
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final ControlInfo controlInfo= (ControlInfo) i.next();
                controlInfoVisitorListener.startVisitControl(controlInfo);
                handleControlInfo(controlInfoVisitorListener, controlInfo.getChildren());
                controlInfoVisitorListener.endVisitControl(controlInfo);
            }
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
        Element controlElement = getCurrentContext().getControlElement();
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
        if (getCurrentContext().isNewBind()) {
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
        if (result == null) {
            if (childElement.element(XFormsConstants.XFORMS_OUTPUT_QNAME) != null) {
                // There is at least one xforms:output element inside

//                final StringBuffer sb = new StringBuffer();
//                for (Iterator i = childElement.content().iterator(); i.hasNext();) {
//                    final Object currentObject = i.next();
//                    if (currentObject instanceof Element) {
//                        final Element currentElement = (Element) currentObject;
//                        if (currentElement.getQName().equals(XFormsConstants.XFORMS_OUTPUT_QNAME)) {
//                            // This is an xforms:output
//
//                            final OutputControlInfo outputControl = new OutputControlInfo(null, currentElement, currentElement.getName(), null);
//                            outputControl.
//                        }
//                    } else if (currentObject instanceof String) {
//                        sb.append(currentObject);
//                    }
//                }

                // TEMP
                result = childElement.getStringValue();
            } else {
                // Plain text
                result = childElement.getStringValue();
            }
        }

        popBinding();
        return result;
    }

    public static class BindingContext {
        private XFormsModel model;
        private List nodeset;
        private int position = 1;
        private String idForContext;
        private boolean newBind;
        private Element controlElement;

        public BindingContext(XFormsModel model, List nodeSet, int position, String idForContext, boolean newBind, Element controlElement) {
            this.model = model;
            this.nodeset = nodeSet;
            this.position = position;
            this.idForContext = idForContext;
            this.newBind = newBind;
            this.controlElement = controlElement;
        }

        public XFormsModel getModel() {
            return model;
        }

        public List getNodeset() {
            return nodeset;
        }

        public int getPosition() {
            return position;
        }

        public String getIdForContext() {
            return idForContext;
        }

        public boolean isNewBind() {
            return newBind;
        }

        public Element getControlElement() {
            return controlElement;
        }

        /**
         * Get the current single node binding, if any.
         */
        public Node getSingleNode() {
            if (nodeset == null || nodeset.size() == 0)
                return null;

            return (Node) nodeset.get(position - 1);
        }
    }

    public static interface ControlElementVisitorListener {
        public boolean startVisitControl(Element controlElement, String effectiveControlId);
        public boolean endVisitControl(Element controlElement, String effectiveControlId);
        public void startRepeatIteration(int iteration);
        public void endRepeatIteration(int iteration);
    }

    public static interface ControlInfoVisitorListener {
        public void startVisitControl(ControlInfo controlInfo);
        public void endVisitControl(ControlInfo controlInfo);
    }

    /**
     * Find the list of control ids bound to a particular node.
     */
    public List findBoundControlIds(final PipelineContext pipelineContext, final Node node) {
        final List[] result = new List[1];
        visitAllControlsHandleRepeat(pipelineContext, new XFormsControls.ControlElementVisitorListener() {
            public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                final List currentNodeset = getCurrentNodeset();
                if (currentNodeset != null && currentNodeset.size() > 0) {
                    final Node currentNode = getCurrentSingleNode();

                    if (currentNode == node) {
                        if (result[0] == null)
                            result[0] = new ArrayList();
                        result[0].add(effectiveControlId);
                    }
                }

                return true;
            }
            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                return true;
            }

            public void startRepeatIteration(int iteration) {
            }

            public void endRepeatIteration(int iteration) {
            }
        });

        return result[0];
    }

    /**
     * Represents the state of a tree of XForms controls.
     */
    public static class ControlsState {
        private List children;
        private Map idsToControlInfo;
        private Map defaultRepeatIdToIndex;
        private Map repeatIdToIndex;
        private Map effectiveRepeatIdToIterations;
        private Map switchIdToSelectedCaseIdMap;
        private List valueControls;

        private boolean hasRepeat;
        private boolean hasUpload;

        public ControlsState() {
        }

        public void setChildren(List children) {
            this.children = children;
        }

        public void setIdsToControlInfo(Map idsToControlInfo) {
            this.idsToControlInfo = idsToControlInfo;
        }

        public Map getDefaultRepeatIdToIndex() {
            return defaultRepeatIdToIndex;
        }

        public void setDefaultRepeatIndex(String controlId, int index) {
            if (defaultRepeatIdToIndex == null)
                defaultRepeatIdToIndex = new HashMap();
            defaultRepeatIdToIndex.put(controlId, new Integer(index));
        }

        public void updateRepeatIndex(String controlId, int index) {
            if (controlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1)
                throw new OXFException("Invalid repeat id provided: " + controlId);
            if (repeatIdToIndex == null)
                repeatIdToIndex = new HashMap(defaultRepeatIdToIndex);
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

        public void setValueControls(List valueControls) {
            this.valueControls = valueControls;
        }

        public void evaluateValueControls(PipelineContext pipelineContext) {
            for (Iterator i = valueControls.iterator(); i.hasNext();) {
                final ControlInfo currentControlInfo = (ControlInfo) i.next();
                currentControlInfo.evaluateValue(pipelineContext);
                currentControlInfo.evaluateDisplayValue(pipelineContext);
            }
        }

        public Map getRepeatIdToIndex() {
            if (repeatIdToIndex == null){
                if (defaultRepeatIdToIndex != null)
                    repeatIdToIndex = new HashMap(defaultRepeatIdToIndex);
                else // In this case there is no repeat
                    return Collections.EMPTY_MAP;
            }
            return repeatIdToIndex;
        }

        public Map getEffectiveRepeatIdToIterations() {
            return effectiveRepeatIdToIterations;
        }

        public boolean isHasRepeat() {
            return hasRepeat;
        }

        public void setHasRepeat(boolean hasRepeat) {
            this.hasRepeat = hasRepeat;
        }

        public boolean isHasUpload() {
            return hasUpload;
        }

        public void setHasUpload(boolean hasUpload) {
            this.hasUpload = hasUpload;
        }

        /**
         * Return the list of repeat ids descendent of a given repeat id, null if none.
         */
        public List getNestedRepeatIds(XFormsControls xformsControls, final String repeatId) {

            final List result = new ArrayList();

            xformsControls.visitAllControlStatic(new ControlElementVisitorListener() {

                private boolean found;

                public boolean startVisitControl(Element controlElement, String effectiveControlId) {
                    if (controlElement.getName().equals("repeat")) {

                        if (!found) {
                            // Not found yet
                            if (repeatId.equals(controlElement.attributeValue("id")))
                                found = true;
                        } else {
                            // We are within the searched repeat id
                            result.add(controlElement.attributeValue("id"));
                        }
                    }
                    return true;
                }

                public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                    if (found) {
                        if (repeatId.equals(controlElement.attributeValue("id")))
                            found = false;
                    }
                    return true;
                }

                public void startRepeatIteration(int iteration) {
                }

                public void endRepeatIteration(int iteration) {
                }
            });

            return result;
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

                if (currentControlInfo instanceof RepeatControlInfo) {
                    final RepeatControlInfo currentRepeatControlInfo = (RepeatControlInfo) currentControlInfo;
                    final String repeatId = currentRepeatControlInfo.getRepeatId();
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

        /**
         * Visit all the ControlInfo elements by following the current repeat indexes and setting
         * current bindings.
         */
        public void visitControlInfoFollowRepeats(PipelineContext pipelineContext, XFormsControls xformsControls, ControlInfoVisitorListener controlInfoVisitorListener) {
            xformsControls.resetBindingContext();
            visitControlInfoFollowRepeats(pipelineContext, xformsControls, this.children, controlInfoVisitorListener);
        }

        private void visitControlInfoFollowRepeats(PipelineContext pipelineContext, XFormsControls xformsControls, List children, ControlInfoVisitorListener controlInfoVisitorListener) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final ControlInfo currentControlInfo = (ControlInfo) i.next();

                xformsControls.pushBinding(pipelineContext, currentControlInfo);
                controlInfoVisitorListener.startVisitControl(currentControlInfo);
                {
                    if (currentControlInfo instanceof RepeatControlInfo) {
                        final RepeatControlInfo currentRepeatControlInfo = (RepeatControlInfo) currentControlInfo;
                        final String repeatId = currentRepeatControlInfo.getRepeatId();
                        final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();

                        if (index > 0) {
                            final List newChildren = currentControlInfo.getChildren();
                            if (newChildren != null && newChildren.size() > 0)
                                visitControlInfoFollowRepeats(pipelineContext, xformsControls, Collections.singletonList(newChildren.get(index - 1)), controlInfoVisitorListener);
                        }

                    } else {
                        final List newChildren = currentControlInfo.getChildren();
                        if (newChildren != null)
                            visitControlInfoFollowRepeats(pipelineContext, xformsControls, newChildren, controlInfoVisitorListener);
                    }
                }
                controlInfoVisitorListener.endVisitControl(currentControlInfo);
                xformsControls.popBinding();
            }
        }

        /**
         * Find an effective control id based on a control id, following the branches of the
         * current indexes of the repeat elements.
         */
        public String findEffectiveControlId(String controlId) {
            return findEffectiveControlId(controlId, this.children);
        }

        private String findEffectiveControlId(String controlId, List children) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final ControlInfo currentControlInfo = (ControlInfo) i.next();
                final String originalControlId = currentControlInfo.getOriginalId();

                if (controlId.equals(originalControlId)) {
                    return currentControlInfo.getId();
                } else if (currentControlInfo instanceof RepeatControlInfo) {
                    final RepeatControlInfo currentRepeatControlInfo = (RepeatControlInfo) currentControlInfo;
                    final String repeatId = currentRepeatControlInfo.getRepeatId();
                    final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();

                    if (index > 0) {
                        final List newChildren = currentControlInfo.getChildren();
                        if (newChildren != null && newChildren.size() > 0) {
                            final String result = findEffectiveControlId(controlId, Collections.singletonList(newChildren.get(index - 1)));
                            if (result != null)
                                return result;
                        }
                    }

                } else {
                    final List newChildren = currentControlInfo.getChildren();
                    if (newChildren != null) {
                        final String result = findEffectiveControlId(controlId, newChildren);
                        if (result != null)
                            return result;
                    }
                }
            }
            // Not found
            return null;
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
