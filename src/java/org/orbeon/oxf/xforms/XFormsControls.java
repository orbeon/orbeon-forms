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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Locator;

import java.util.*;

/**
 * Represents all this XForms containing document controls and the context in which they operate.
 *
 * TODO: It would be nice to separate the context stack code from here.
 */
public class XFormsControls {

    private Locator locator;

    private boolean initialized;
    private ControlsState initialControlsState;
    private ControlsState currentControlsState;

    private boolean dirty;

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

    public boolean isDirty() {
        return dirty;
    }

    
    public void markDirty() {
        this.dirty = true;
        this.currentControlsState.markDirty();
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
                    final XFormsRepeatControl repeatControl
                            = new XFormsRepeatControl(containingDocument, null, controlElement, controlElement.getName(), effectiveControlId);

                    // Set initial index
                    controlsState.setDefaultRepeatIndex(repeatControl.getRepeatId(), repeatControl.getStartIndex());

                    // Keep control on stack
                    repeatStack.push(repeatControl);
                }
                return true;
            }

            public boolean endVisitControl(Element controlElement, String effectiveControlId) {
                return true;
            }

            public void startRepeatIteration(int iteration) {
                // One more iteration in current repeat
                final XFormsRepeatControl repeatControl = (XFormsRepeatControl) repeatStack.peek();
                repeatControl.addChild(new RepeatIterationControl(containingDocument, repeatControl, iteration));
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
                // Build controls state

                // Get initial controls state information
                initialControlsState = buildControlsState(pipelineContext);
                currentControlsState = initialControlsState;

                // Set switch state if necessary
                if (divsElement != null) {
                    for (Iterator i = divsElement.elements().iterator(); i.hasNext();) {
                        final Element divElement = (Element) i.next();

                        final String caseId = divElement.attributeValue("id");
                        final String visibility = divElement.attributeValue("visibility");

                        updateSwitchInfo(caseId, "visible".equals(visibility));
                    }
                }

                // Handle repeat indexes if needed
                if (initialControlsState.isHasRepeat()) {
                    // Get default xforms:repeat indexes beforehand
                    getDefaultRepeatIndexes(initialControlsState);
                    
                    // Set external updates
                    setRepeatIndexState(repeatIndexesElement);

                    // Adjust repeat indexes
                    XFormsIndexUtils.adjustIndexes(pipelineContext, XFormsControls.this, initialControlsState);
                }

                // Evaluate values after index state has been computed
//                initialControlsState.evaluateControls(pipelineContext);
            }

            // We are now clean
            dirty = false;
            initialControlsState.dirty = false;
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
        final List defaultNodeset = Arrays.asList(new Object[]{defaultModel.getDefaultInstance().getInstanceRootElementInfo()});
        contextStack.push(new BindingContext(null, defaultModel, defaultNodeset, 1, null, true, null));
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

    /**
     * Set the binding context to the current control.
     *
     * @param pipelineContext   current PipelineContext
     * @param xformsControl       control to bind
     */
    public void setBinding(PipelineContext pipelineContext, XFormsControl xformsControl) {

        // Create ancestors-or-self list
        final List ancestorsOrSelf = new ArrayList();
        BindingContext controlBindingContext = xformsControl.getBindingContext();
        while (controlBindingContext != null) {
            ancestorsOrSelf.add(controlBindingContext);
            controlBindingContext = controlBindingContext.getParent();
        }
        Collections.reverse(ancestorsOrSelf);

        // Bind up to the specified element
        contextStack.clear();
        contextStack.addAll(ancestorsOrSelf);
    }

    private void pushBinding(PipelineContext pipelineContext, XFormsControl XFormsControl) {

        final Element bindingElement = XFormsControl.getControlElement();
        if (!(XFormsControl instanceof RepeatIterationControl)) {
            // Regular XFormsControl backed by an element

            final String ref = bindingElement.attributeValue("ref");
            final String context = bindingElement.attributeValue("context");
            final String nodeset = bindingElement.attributeValue("nodeset");
            final String modelId = XFormsUtils.namespaceId(containingDocument, bindingElement.attributeValue("model"));
            final String bindId = XFormsUtils.namespaceId(containingDocument, bindingElement.attributeValue("bind"));

            final Map bindingElementNamespaceContext =
                    (ref != null || nodeset != null) ? Dom4jUtils.getNamespaceContextNoDefault(bindingElement) : null;

            pushBinding(pipelineContext, ref, context, nodeset, modelId, bindId, bindingElement, bindingElementNamespaceContext);
        } else {
            // RepeatIterationInfo

            final XFormsControl repeatXFormsControl = XFormsControl.getParent();
            final List repeatChildren = repeatXFormsControl.getChildren();
            final BindingContext currentBindingContext = getCurrentBindingContext();
            final List currentNodeset = currentBindingContext.getNodeset();

            final int repeatChildrenSize = (repeatChildren == null) ? 0 : repeatChildren.size();
            final int currentNodesetSize = (currentNodeset == null) ? 0 : currentNodeset.size();

            if (repeatChildrenSize != currentNodesetSize)
                throw new IllegalStateException("repeatChildren and newNodeset have different sizes.");

            // Push "artificial" binding with just current node in nodeset
            final XFormsModel newModel = currentBindingContext.getModel();
            final int position = ((RepeatIterationControl) XFormsControl).getIteration();
            contextStack.push(new BindingContext(currentBindingContext, newModel, currentNodeset, position, XFormsControl.getParent().getOriginalId(), true, null));
        }
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     */
    public void pushBinding(PipelineContext pipelineContext, Element bindingElement) {
        pushBinding(pipelineContext, bindingElement, null);
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     *
     * @param pipelineContext   current PipelineContext
     * @param bindingElement    current element containing node binding attributes
     * @param model             if specified, overrides a potential @model attribute on the element
     */
    public void pushBinding(PipelineContext pipelineContext, Element bindingElement, String model) {
        final String ref = bindingElement.attributeValue("ref");
        final String context = bindingElement.attributeValue("context");
        final String nodeset = bindingElement.attributeValue("nodeset");
        if (model == null)
            model = XFormsUtils.namespaceId(containingDocument, bindingElement.attributeValue("model"));
        final String bind = XFormsUtils.namespaceId(containingDocument, bindingElement.attributeValue("bind"));

        pushBinding(pipelineContext, ref, context, nodeset, model, bind, bindingElement,
                (ref != null || nodeset != null) ? Dom4jUtils.getNamespaceContextNoDefault(bindingElement) : null);
    }

    public void pushBinding(PipelineContext pipelineContext, String ref, String context, String nodeset, String modelId, String bindId,
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
        final BindingContext currentBindingContext = getCurrentBindingContext();

        // Handle model
        final XFormsModel newModel;
        final boolean isNewModel;
        if (modelId != null) {
            newModel = containingDocument.getModel(modelId);
            isNewModel = true;
        } else {
            newModel = currentBindingContext.getModel();
            isNewModel = false;
        }

        // Handle nodeset
        final List newNodeset;
        {
            if (bindId != null) {
                // Resolve the bind id to a nodeset
                final ModelBind modelBind = newModel.getModelBindById(bindId);
                if (modelBind == null)
                    throw new OXFException("Cannot find bind for id: " + bindId);
                newNodeset = newModel.getBindNodeset(pipelineContext, modelBind);
            } else if (ref != null || nodeset != null) {

                // Check whether there is an optional context
                if (nodeset != null && context != null) {
                    pushBinding(pipelineContext, null, null, context, modelId, null, null, bindingElementNamespaceContext);
                }

                // Evaluate new XPath in context
                if (isNewModel) {
                    // Model was switched

                    final NodeInfo currentSingleNodeForModel = getCurrentSingleNode(newModel.getEffectiveId());
                    if (currentSingleNodeForModel != null) {

                        // Temporarily update the context so that the function library's instance() function works
                        final BindingContext modelBindingContext = getCurrentBindingContextForModel(newModel.getEffectiveId());
                        if (modelBindingContext != null)
                            contextStack.push(new BindingContext(currentBindingContext, newModel, modelBindingContext.getNodeset(), modelBindingContext.getPosition(), null, false, null));
                        else
                            contextStack.push(new BindingContext(currentBindingContext, newModel, getCurrentNodeset(newModel.getEffectiveId()), 1, null, false, null));

                        // Evaluate new node-set
                        newNodeset = containingDocument.getEvaluator().evaluate(pipelineContext, currentSingleNodeForModel,
                                ref != null ? ref : nodeset, bindingElementNamespaceContext, null, functionLibrary, null);

                        // Restore context
                        contextStack.pop();
                    } else {
                        newNodeset = Collections.EMPTY_LIST;
                    }

                } else {
                    // Simply evaluate new node-set
                    final NodeInfo currentSingleNode = getCurrentSingleNode();
                    if (currentSingleNode != null) {
                        newNodeset = containingDocument.getEvaluator().evaluate(pipelineContext, currentSingleNode,
                                ref != null ? ref : nodeset, bindingElementNamespaceContext, null, functionLibrary, null);
                    } else {
                        newNodeset = Collections.EMPTY_LIST;
                    }
                }

                // Restore optional context
                if (nodeset != null && context != null) {
                    popBinding();
                }

            } else {
                // No change to current nodeset
                newNodeset = currentBindingContext.getNodeset();
            }
        }

        // Push new context
        final boolean isNewBind = newNodeset != currentBindingContext.getNodeset();// TODO: this is used only in one place later; check
        final String id = (bindingElement == null) ? null : bindingElement.attributeValue("id");
        contextStack.push(new BindingContext(currentBindingContext, newModel, newNodeset, isNewBind ? 1 : currentBindingContext.getPosition(), id, isNewBind, bindingElement));
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
    public BindingContext getCurrentBindingContext() {
        return (BindingContext) contextStack.peek();
    }

    public BindingContext popBinding() {
        if (contextStack.size() == 1)
            throw new OXFException("Attempt to clear XForms controls context stack.");
        return (BindingContext) contextStack.pop();
    }

    /**
     * Get the current node-set binding for the given model id.
     */
    public BindingContext getCurrentBindingContextForModel(String modelId) {

        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final String currentModelId = currentBindingContext.getModel().getEffectiveId();
            if ((currentModelId == null && modelId == null) || (modelId != null && modelId.equals(currentModelId)))
                return currentBindingContext;
        }

        return null;
    }

    /**
     * Get the current node-set binding for the given model id.
     */
    public List getCurrentNodeset(String modelId) {

        final BindingContext bindingContext = getCurrentBindingContextForModel(modelId);

        // If a context exists, return its node-set
        if (bindingContext != null)
            return bindingContext.getNodeset();

        // If not found, return the document element of the model's default instance
        return Collections.singletonList(containingDocument.getModel(modelId).getDefaultInstance().getInstanceRootElementInfo());
    }

    /**
     * Get the current single node binding for the given model id.
     */
    public NodeInfo getCurrentSingleNode(String modelId) {
        final List currentNodeset = getCurrentNodeset(modelId);
        return (NodeInfo) ((currentNodeset == null || currentNodeset.size() == 0) ? null : currentNodeset.get(0));
    }

    /**
     * Get the current single node binding, if any.
     */
    public NodeInfo getCurrentSingleNode() {
        return getCurrentBindingContext().getSingleNode();
    }

    public String getCurrentSingleNodeValue() {
        final NodeInfo currentSingleNode = getCurrentSingleNode();
        if (currentSingleNode != null)
            return XFormsInstance.getValueForNode(currentSingleNode);
        else
            return null;
    }

    /**
     * Get the current nodeset binding, if any.
     */
    public List getCurrentNodeset() {
        return getCurrentBindingContext().getNodeset();
    }

    /**
     * Get the current position in current nodeset binding.
     */
    public int getCurrentPosition() {
        return getCurrentBindingContext().getPosition();
    }

    /**
     * Return the single node associated with the iteration of the repeat specified. If a null
     * repeat id is passed, return the single node associated with the closest enclosing repeat
     * iteration.
     *
     * @param repeatId  enclosing repeat id, or null
     * @return          the single node
     */
    public NodeInfo getRepeatCurrentSingleNode(String repeatId) {
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
     * For the given case id and the current binding, try to find an effective case id.
     *
     * The effective case id is for now the effective case id following repeat branches. This can be improved in the
     * future.
     *
     * @param caseId    a case id
     * @return          an effective case id if possible
     */
    public String findEffectiveCaseId(String caseId) {
        return getCurrentControlsState().findEffectiveControlId(caseId);
    }

    /**
     * Return the context node-set based on the enclosing xforms:repeat, xforms:group or
     * xforms:switch, either the closest one if no argument is passed, or context at the level of
     * the element with the given id passed.
     *
     * @param contextId  enclosing context id, or null
     * @return           the node-set
     */
    public List getContextForId(String contextId) {
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

    /**
     * Return the currrent model for the current nodeset binding.
     */
    public XFormsModel getCurrentModel() {
        return getCurrentBindingContext().getModel();
    }

    /**
     * Return the current instance for the current nodeset binding.
     *
     * This method goes up the context stack until it finds a node, and returns the instance associated with that node.
     */
    public XFormsInstance getCurrentInstance() {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);
            final NodeInfo currentSingleNode = currentBindingContext.getSingleNode();

            if (currentSingleNode != null)
                return currentBindingContext.getModel().getInstanceForNode(currentSingleNode);
        }
        return null;
    }

    public FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Update xforms:switch/xforms:case information with newly selected case id.
     */
    public void updateSwitchInfo(final PipelineContext pipelineContext, final String selectedCaseId) {

        // Find SwitchXFormsControl
        final XFormsControl caseXFormsControl = (XFormsControl) currentControlsState.getIdToControl().get(selectedCaseId);
        if (caseXFormsControl == null)
            throw new OXFException("No XFormsControl found for case id '" + selectedCaseId + "'.");
        final XFormsControl switchXFormsControl = (XFormsControl) caseXFormsControl.getParent();
        if (switchXFormsControl == null)
            throw new OXFException("No SwitchXFormsControl found for case id '" + selectedCaseId + "'.");

        final String currentSelectedCaseId = (String) currentControlsState.getSwitchIdToSelectedCaseIdMap().get(switchXFormsControl.getEffectiveId());
        if (!selectedCaseId.equals(currentSelectedCaseId)) {
            // A new selection occurred on this switch

            // "This action adjusts all selected attributes on the affected cases to reflect the
            // new state, and then performs the following:"
            currentControlsState.getSwitchIdToSelectedCaseIdMap().put(switchXFormsControl.getEffectiveId(), selectedCaseId);

            // "1. Dispatching an xforms-deselect event to the currently selected case."
            containingDocument.dispatchEvent(pipelineContext, new XFormsDeselectEvent((XFormsEventTarget) currentControlsState.getIdToControl().get(currentSelectedCaseId)));

            // "2. Dispatching an xform-select event to the case to be selected."
            containingDocument.dispatchEvent(pipelineContext, new XFormsSelectEvent((XFormsEventTarget) currentControlsState.getIdToControl().get(selectedCaseId)));
        }
    }

    /**
     * Update switch info state for the given case id.
     */
    public void updateSwitchInfo(String caseId, boolean visible) {

        // Find SwitchXFormsControl
        final XFormsControl caseControl = (XFormsControl) currentControlsState.getIdToControl().get(caseId);
        if (caseControl == null)
            throw new OXFException("No XFormsControl found for case id '" + caseId + "'.");
        final XFormsControl switchControl = (XFormsControl) caseControl.getParent();
        if (switchControl == null)
            throw new OXFException("No XFormsSwitchControl found for case id '" + caseId + "'.");

        // Update currently selected case id
        if (visible) {
            currentControlsState.getSwitchIdToSelectedCaseIdMap().put(switchControl.getEffectiveId(), caseId);
        }
    }

    private ControlsState buildControlsState(final PipelineContext pipelineContext) {

        final long startTime;
        if (XFormsServer.logger.isDebugEnabled()) {
            XFormsServer.logger.debug("XForms - building controls state start.");
            startTime = System.currentTimeMillis();
        } else {
            startTime = 0;
        }

        final ControlsState result = new ControlsState();

        final XFormsControl rootXFormsControl = new RootControl(containingDocument);// this is temporary and won't be stored
        final Map idsToXFormsControls = new HashMap();

        final Map switchIdToSelectedCaseIdMap = new HashMap();

        visitAllControlsHandleRepeat(pipelineContext, new XFormsControls.ControlElementVisitorListener() {

            private XFormsControl currentControlsContainer = rootXFormsControl;

            public boolean startVisitControl(Element controlElement, String effectiveControlId) {

                if (effectiveControlId == null)
                    throw new OXFException("Control element doesn't have an id: " + controlElement.getQualifiedName());

                final String controlName = controlElement.getName();

                // Create XFormsControl with basic information
                final XFormsControl xformsControl = XFormsControlFactory.createXFormsControl(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);
                if (xformsControl instanceof XFormsRepeatControl)
                    result.setHasRepeat(true);
                else if (xformsControl instanceof XFormsUploadControl)
                    result.setHasUpload(true);

                // Make sure there are no duplicate ids
                if (idsToXFormsControls.get(effectiveControlId) != null)
                    throw new ValidationException("Duplicate id for XForms control: " + effectiveControlId, new ExtendedLocationData((LocationData) controlElement.getData(),
                            "analyzing control element", controlElement, new String[] { "id", effectiveControlId }));

                idsToXFormsControls.put(effectiveControlId, xformsControl);

                // Handle xforms:case
                if (controlName.equals("case")) {
                    if (!(currentControlsContainer.getName().equals("switch")))
                        throw new OXFException("xforms:case with id '" + effectiveControlId + "' is not directly within an xforms:switch container.");
                    final String switchId = currentControlsContainer.getEffectiveId();

                    if (switchIdToSelectedCaseIdMap.get(switchId) == null) {
                        // If case is not already selected for this switch and there is a select attribute, set it
                        final String selectedAttribute = controlElement.attributeValue("selected");
                        if ("true".equals(selectedAttribute))
                            switchIdToSelectedCaseIdMap.put(switchId, effectiveControlId);
                    }
                }

                // Handle xforms:itemset
                // TODO: We don't need to do this every time, only when we need to produce output, right?
                if (xformsControl instanceof XFormsSelectControl || xformsControl instanceof XFormsSelect1Control) {
                    ((XFormsSelect1Control) xformsControl).evaluateItemsets(pipelineContext);
                }

                // Set current binding for control element
                final BindingContext currentBindingContext = getCurrentBindingContext();
                final List currentNodeSet = currentBindingContext.getNodeset();

                // Bind the control to the binding context
                xformsControl.setBindingContext(currentBindingContext);

                // Update MIPs on control
                // TODO: we don't need to set these values until we produce output, right?
                if (!(xformsControl instanceof XFormsRepeatControl && currentNodeSet != null && currentNodeSet.size() == 0)) {
                    final NodeInfo currentNode = currentBindingContext.getSingleNode();
                    if (currentNode != null) {
                        // Control is bound to a node - get model item properties
                        final InstanceData instanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNode);
                        if (instanceData != null) {
                            xformsControl.setReadonly(instanceData.getInheritedReadonly().get());
                            xformsControl.setRequired(instanceData.getRequired().get());
                            xformsControl.setRelevant(instanceData.getInheritedRelevant().get());
                            xformsControl.setValid(instanceData.getValid().get());
                            final String typeAsString = instanceData.getType().getAsString();
                            if (typeAsString != null) {
                                xformsControl.setType(typeAsString);
                            }
                        }
                        // Handle global read-only setting
                        if (containingDocument.isReadonly())
                            xformsControl.setReadonly(true);
                    } else {
                        // Control is not bound to a node - it becomes non-relevant
                        xformsControl.setReadonly(false);
                        xformsControl.setRequired(false);
                        xformsControl.setRelevant(false);
                        xformsControl.setValid(true);// by default, a control is not invalid
                        xformsControl.setType(null);
                    }
                }

                // Add to current controls container
                currentControlsContainer.addChild(xformsControl);

                // Current grouping control becomes the current controls container
                if (isGroupingControl(controlName)) {
                    currentControlsContainer = xformsControl;
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
                            switchIdToSelectedCaseIdMap.put(effectiveControlId, ((XFormsControl) children.get(0)).getEffectiveId());
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

                final XFormsControl repeatIterationXForms = new RepeatIterationControl(containingDocument, currentControlsContainer, iteration);
                currentControlsContainer.addChild(repeatIterationXForms);
                currentControlsContainer = repeatIterationXForms;

                // Set current binding for control element
                final BindingContext currentBindingContext = getCurrentBindingContext();

                // Bind the control to the binding context
                repeatIterationXForms.setBindingContext(currentBindingContext);

                // Set current binding for control element
                final NodeInfo currentNode = currentBindingContext.getSingleNode();

                // Get model item properties
                final InstanceData instanceData = XFormsUtils.getInstanceDataUpdateInherited(currentNode);
                if (instanceData != null) {
                    repeatIterationXForms.setReadonly(instanceData.getInheritedReadonly().get());
                    repeatIterationXForms.setRequired(instanceData.getRequired().get());
                    repeatIterationXForms.setRelevant(instanceData.getInheritedRelevant().get());
                    repeatIterationXForms.setValid(instanceData.getValid().get());
                }
                // Handle global read-only setting
                if (containingDocument.isReadonly())
                    repeatIterationXForms.setReadonly(true);
            }

            public void endRepeatIteration(int iteration) {
                currentControlsContainer = currentControlsContainer.getParent();
            }
        });

        // Make it so that all the root XFormsControl don't have a parent
        final List rootChildren = rootXFormsControl.getChildren();
        if (rootChildren != null) {
            for (Iterator i = rootChildren.iterator(); i.hasNext();) {
                final XFormsControl currentXFormsControl = (XFormsControl) i.next();
                currentXFormsControl.detach();
            }
        }

        result.setChildren(rootChildren);
        result.setIdsToXFormsControls(idsToXFormsControls);
        result.setSwitchIdToSelectedCaseIdMap(switchIdToSelectedCaseIdMap);

        // Evaluate all controls
        for (Iterator i = idsToXFormsControls.entrySet().iterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final XFormsControl currentControl = (XFormsControl) currentEntry.getValue();
            currentControl.evaluate(pipelineContext);
        }

        if (XFormsServer.logger.isDebugEnabled()) {
            XFormsServer.logger.debug("XForms - building controls state end: " + (System.currentTimeMillis() - startTime) + " ms.");
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
     * Rebuild the current controls state information if needed.
     */
    public boolean rebuildCurrentControlsStateIfNeeded(PipelineContext pipelineContext) {

        // Don't do anything if we are clean
        if (!currentControlsState.isDirty())
            return false;

        // Rebuild
        rebuildCurrentControlsState(pipelineContext);

        // Everything is clean
        initialControlsState.dirty = false;
        currentControlsState.dirty = false;

        return true;
    }

    /**
     * Rebuild the current controls state information.
     */
    public void rebuildCurrentControlsState(PipelineContext pipelineContext) {

        // Remember current state
        final ControlsState currentControlsState = this.currentControlsState;

        // Create new controls state
        final ControlsState result = buildControlsState(pipelineContext);

        // Transfer some of the previous information
        final Map currentRepeatIdToIndex = currentControlsState.getRepeatIdToIndex();
        if (currentRepeatIdToIndex.size() != 0) {
            // Keep repeat index information
            result.setRepeatIdToIndex(currentRepeatIdToIndex);
            // Adjust repeat indexes if necessary
            XFormsIndexUtils.adjustIndexes(pipelineContext, XFormsControls.this, result);
        }

        // Update switch information
        final Map oldSwitchIdToSelectedCaseIdMap = currentControlsState.getSwitchIdToSelectedCaseIdMap();
        final Map newSwitchIdToSelectedCaseIdMap = result.getSwitchIdToSelectedCaseIdMap();
        {
            for (Iterator i = newSwitchIdToSelectedCaseIdMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry entry = (Map.Entry) i.next();
                final String switchId =  (String) entry.getKey();

                // Keep old switch state
                final String oldSelectedCaseId = (String) oldSwitchIdToSelectedCaseIdMap.get(switchId);
                if (oldSelectedCaseId != null) {
                    entry.setValue(oldSelectedCaseId);
                }
            }

            result.setSwitchIdToSelectedCaseIdMap(newSwitchIdToSelectedCaseIdMap);
        }

        // Update current state
        this.currentControlsState = result;

        // Handle relevance of controls that are no longer bound to instance data nodes
        final Map[] eventsToDispatch = new Map[] { currentControlsState.getEventsToDispatch() } ;
        findSpecialRelevanceChanges(currentControlsState.getChildren(), result.getChildren(), eventsToDispatch);
        this.currentControlsState.setEventsToDispatch(eventsToDispatch[0]);
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

    /**
     * Get the object with the id specified, null if not found.
     */
    public Object getObjectById(String controlId) {
        return currentControlsState.getIdToControl().get(controlId);
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
                    final BindingContext currentBindingContext = getCurrentBindingContext();

                    // Visit xforms:repeat element
                    doContinue = controlElementVisitorListener.startVisitControl(controlElement, effectiveControlId);

                    // Iterate over current xforms:repeat nodeset
                    final List currentNodeSet = getCurrentNodeset();
                    if (currentNodeSet != null) {
                        for (int currentPosition = 1; currentPosition <= currentNodeSet.size(); currentPosition++) {
                            // Push "artificial" binding with just current node in nodeset
                            contextStack.push(new BindingContext(currentBindingContext, currentBindingContext.getModel(), currentNodeSet, currentPosition, controlId, true, null));
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
     * Visit all the current XFormsControls.
     */
    public void visitAllControls(XFormsControlVisitorListener xformsControlVisitorListener) {
        handleControl(xformsControlVisitorListener, currentControlsState.getChildren());
    }

    /**
     * Visit all the children of the given XFormsControl.
     */
    public void visitAllControls(XFormsControlVisitorListener xformsControlVisitorListener, XFormsControl currentXFormsControl) {
        handleControl(xformsControlVisitorListener, currentXFormsControl.getChildren());
    }

    private void handleControl(XFormsControlVisitorListener xformsControlVisitorListener, List children) {
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl XFormsControl = (XFormsControl) i.next();
                xformsControlVisitorListener.startVisitControl(XFormsControl);
                handleControl(xformsControlVisitorListener, XFormsControl.getChildren());
                xformsControlVisitorListener.endVisitControl(XFormsControl);
            }
        }
    }

    public static class BindingContext {
        private BindingContext parent;
        private XFormsModel model;
        private List nodeset;
        private int position = 1;
        private String idForContext;
        private boolean newBind;
        private Element controlElement;

        public BindingContext(BindingContext parent, XFormsModel model, List nodeSet, int position, String idForContext, boolean newBind, Element controlElement) {
            this.parent = parent;
            this.model = model;
            this.nodeset = nodeSet;
            this.position = position;
            this.idForContext = idForContext;
            this.newBind = newBind;
            this.controlElement = controlElement;

            if (nodeset != null && nodeset.size() > 0) {
                for (Iterator i = nodeset.iterator(); i.hasNext();) {
                    final Object currentItem = i.next();
                    if (!(currentItem instanceof NodeInfo))
                        throw new OXFException("A reference to a node (such as text, element, or attribute) is required in a binding. Attempted to bind to the invalid item type: " + currentItem.getClass());
                }
            }
        }

        public BindingContext getParent() {
            return parent;
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
        public NodeInfo getSingleNode() {
            if (nodeset == null || nodeset.size() == 0)
                return null;

            return (NodeInfo) nodeset.get(position - 1);
        }
    }

    public static interface ControlElementVisitorListener {
        public boolean startVisitControl(Element controlElement, String effectiveControlId);
        public boolean endVisitControl(Element controlElement, String effectiveControlId);
        public void startRepeatIteration(int iteration);
        public void endRepeatIteration(int iteration);
    }

    public static interface XFormsControlVisitorListener {
        public void startVisitControl(XFormsControl xformsControl);
        public void endVisitControl(XFormsControl xformsControl);
    }

    /**
     * Represents the state of repeat indexes.
     */
//    public static class RepeatIndexesState {
//        public RepeatIndexesState() {
//        }
//
//
//    }

    /**
     * Represents the state of a tree of XForms controls.
     */
    public static class ControlsState {
        private List children;
        private Map idsToXFormsControls;
        private Map defaultRepeatIdToIndex;
        private Map repeatIdToIndex;
        private Map effectiveRepeatIdToIterations;
        private Map switchIdToSelectedCaseIdMap;

        private boolean hasRepeat;
        private boolean hasUpload;

        private boolean dirty;

        private Map eventsToDispatch;

        public ControlsState() {
        }

        public boolean isDirty() {
            return dirty;
        }

        public void markDirty() {
            this.dirty = true;
        }

        public Map getEventsToDispatch() {
            return eventsToDispatch;
        }

        public void setEventsToDispatch(Map eventsToDispatch) {
            this.eventsToDispatch = eventsToDispatch;
        }

        public void setChildren(List children) {
            this.children = children;
        }

        public void setIdsToXFormsControls(Map idsToXFormsControls) {
            this.idsToXFormsControls = idsToXFormsControls;
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

        public Map getIdToControl() {
            return idsToXFormsControls;
        }

        public Map getSwitchIdToSelectedCaseIdMap() {
            return switchIdToSelectedCaseIdMap;
        }

        public void setSwitchIdToSelectedCaseIdMap(Map switchIdToSelectedCaseIdMap) {
            this.switchIdToSelectedCaseIdMap = switchIdToSelectedCaseIdMap;
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
         * Return a map of repeat ids -> RepeatXFormsControl objects, following the branches of the
         * current indexes of the repeat elements.
         */
        public Map getRepeatIdToRepeatXFormsControl() {
            final Map result = new HashMap();
            visitRepeatHierarchy(result);
            return result;
        }

        private void visitRepeatHierarchy(Map result) {
            visitRepeatHierarchy(result, this.children);
        }

        private void visitRepeatHierarchy(Map result, List children) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentXFormsControl = (XFormsControl) i.next();

                if (currentXFormsControl instanceof XFormsRepeatControl) {
                    final XFormsRepeatControl currentRepeatXFormsControl = (XFormsRepeatControl) currentXFormsControl;
                    final String repeatId = currentRepeatXFormsControl.getRepeatId();
                    final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();

                    result.put(repeatId, currentXFormsControl);

                    if (index > 0) {
                        final List newChildren = currentXFormsControl.getChildren();
                        if (newChildren != null && newChildren.size() > 0)
                            visitRepeatHierarchy(result, Collections.singletonList(newChildren.get(index - 1)));
                    }

                } else {
                    final List newChildren = currentXFormsControl.getChildren();
                    if (newChildren != null)
                        visitRepeatHierarchy(result, newChildren);
                }
            }
        }

        /**
         * Visit all the XFormsControl elements by following the current repeat indexes and setting
         * current bindings.
         */
        public void visitControlsFollowRepeats(PipelineContext pipelineContext, XFormsControls xformsControls, XFormsControlVisitorListener xformsControlVisitorListener) {
            // Don't iterate if we don't have controls
            if (this.children == null)
                return;

            xformsControls.resetBindingContext();
            visitControlsFollowRepeats(pipelineContext, xformsControls, this.children, xformsControlVisitorListener);
        }

        private void visitControlsFollowRepeats(PipelineContext pipelineContext, XFormsControls xformsControls, List children, XFormsControlVisitorListener xformsControlVisitorListener) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentXFormsControl = (XFormsControl) i.next();

                xformsControls.pushBinding(pipelineContext, currentXFormsControl);
                xformsControlVisitorListener.startVisitControl(currentXFormsControl);
                {
                    if (currentXFormsControl instanceof XFormsRepeatControl) {
                        final XFormsRepeatControl currentRepeatXFormsControl = (XFormsRepeatControl) currentXFormsControl;
                        final String repeatId = currentRepeatXFormsControl.getRepeatId();
                        final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();

                        if (index > 0) {
                            final List newChildren = currentXFormsControl.getChildren();
                            if (newChildren != null && newChildren.size() > 0)
                                visitControlsFollowRepeats(pipelineContext, xformsControls, Collections.singletonList(newChildren.get(index - 1)), xformsControlVisitorListener);
                        }

                    } else {
                        final List newChildren = currentXFormsControl.getChildren();
                        if (newChildren != null)
                            visitControlsFollowRepeats(pipelineContext, xformsControls, newChildren, xformsControlVisitorListener);
                    }
                }
                xformsControlVisitorListener.endVisitControl(currentXFormsControl);
                xformsControls.popBinding();
            }
        }

        /**
         * Find an effective control id based on a control id, following the branches of the
         * current indexes of the repeat elements.
         */
        public String findEffectiveControlId(String controlId) {
            // Don't iterate if we don't have controls
            if (this.children == null)
                return null;

            return findEffectiveControlId(controlId, this.children);
        }

        private String findEffectiveControlId(String controlId, List children) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentXFormsControl = (XFormsControl) i.next();
                final String originalControlId = currentXFormsControl.getOriginalId();

                if (controlId.equals(originalControlId)) {
                    return currentXFormsControl.getEffectiveId();
                } else if (currentXFormsControl instanceof XFormsRepeatControl) {
                    final XFormsRepeatControl currentRepeatXFormsControl = (XFormsRepeatControl) currentXFormsControl;
                    final String repeatId = currentRepeatXFormsControl.getRepeatId();
                    final int index = ((Integer) getRepeatIdToIndex().get(repeatId)).intValue();

                    if (index > 0) {
                        final List newChildren = currentXFormsControl.getChildren();
                        if (newChildren != null && newChildren.size() > 0) {
                            final String result = findEffectiveControlId(controlId, Collections.singletonList(newChildren.get(index - 1)));
                            if (result != null)
                                return result;
                        }
                    }

                } else {
                    final List newChildren = currentXFormsControl.getChildren();
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
     * Analyze differences of relevance for controls getting bound and unbound to nodes.
     */
    private void findSpecialRelevanceChanges(List state1, List state2, Map[] eventsToDispatch) {

        // Trivial case
        if (state1 == null && state2 == null)
            return;

        // Both lists must have the same size if present; state1 can be null
        if (state1 != null && state2 != null && state1.size() != state2.size()) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        final Iterator j = (state1 == null) ? null : state1.iterator();
        final Iterator i = (state2 == null) ? null : state2.iterator();
        final Iterator leadingIterator = (i != null) ? i : j;
        while (leadingIterator.hasNext()) {
            final XFormsControl xformsControl1 = (state1 == null) ? null : (XFormsControl) j.next();
            final XFormsControl xformsControl2 = (state2 == null) ? null : (XFormsControl) i.next();

            final XFormsControl leadingControl = (xformsControl2 != null) ? xformsControl2 : xformsControl1; // never null

            // 1: Check current control
            if (!(leadingControl instanceof XFormsRepeatControl)) {
                // xforms:repeat doesn't need to be handled independently, iterations do it

                String foundControlId = null;
                XFormsControl targetControl = null;
                int eventType = 0;
                if (xformsControl1 != null && xformsControl2 != null) {
                    final NodeInfo boundNode1 = xformsControl1.getBoundNode();
                    final NodeInfo boundNode2 = xformsControl2.getBoundNode();

                    if (boundNode1 != null && xformsControl1.isRelevant() && boundNode2 == null) {
                        // A control was bound to a node and relevant, but has become no longer bound to a node
                        foundControlId = xformsControl2.getEffectiveId();
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    } else if (boundNode1 == null && boundNode2 != null && xformsControl2.isRelevant()) {
                        // A control was not bound to a node, but has now become bound and relevant
                        foundControlId = xformsControl2.getEffectiveId();
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    } else if (boundNode1 != null && boundNode2 != null && !boundNode1.isSameNodeInfo(boundNode2)) {
                        // The control is now bound to a different node
                        // In this case, we schedule the control to dispatch all the events

                        // NOTE: This is not really proper according to the spec, but it does help applications to
                        // force dispatching in such cases
                        foundControlId = xformsControl2.getEffectiveId();
                        eventType = XFormsModel.EventSchedule.ALL;
                    }
                } else if (xformsControl2 != null) {
                    final NodeInfo boundNode2 = xformsControl2.getBoundNode();
                    if (boundNode2 != null && xformsControl2.isRelevant()) {
                        // A control was not bound to a node, but has now become bound and relevant
                        foundControlId = xformsControl2.getEffectiveId();
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    }
                } else if (xformsControl1 != null) {
                    final NodeInfo boundNode1 = xformsControl1.getBoundNode();
                    if (boundNode1 != null && xformsControl1.isRelevant()) {
                        // A control was bound to a node and relevant, but has become no longer bound to a node
                        foundControlId = xformsControl1.getEffectiveId();
                        // NOTE: This is the only case where we must dispatch the event to an obsolete control
                        targetControl = xformsControl1;
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    }
                }

                // Remember that we need to dispatch information about this control
                if (foundControlId != null) {
                    if (eventsToDispatch[0] == null)
                        eventsToDispatch[0] = new HashMap();
                    eventsToDispatch[0].put(foundControlId,
                            new XFormsModel.EventSchedule(foundControlId, eventType, targetControl));
                }
            }

            // 2: Check children if any
            if (XFormsControls.isGroupingControl(leadingControl.getName()) || leadingControl instanceof RepeatIterationControl) {

                final List children1 = (xformsControl1 == null) ? null : xformsControl1.getChildren();
                final List children2 = (xformsControl2 == null) ? null : xformsControl2.getChildren();

                final int size1 = children1 == null ? 0 : children1.size();
                final int size2 = children2 == null ? 0 : children2.size();

                if (leadingControl instanceof XFormsRepeatControl) {
                    // Special case of repeat update

                    if (size1 == size2) {
                        // No add or remove of children
                        findSpecialRelevanceChanges(children1, children2, eventsToDispatch);
                    } else if (size2 > size1) {
                        // Size has grown

                        // Diff the common subset
                        findSpecialRelevanceChanges(children1, children2.subList(0, size1), eventsToDispatch);

                        // Issue new values for new iterations
                        findSpecialRelevanceChanges(null, children2.subList(size1, size2), eventsToDispatch);

                    } else if (size2 < size1) {
                        // Size has shrunk

                        // Diff the common subset
                        findSpecialRelevanceChanges(children1.subList(0, size2), children2, eventsToDispatch);

                        // Issue new values for new iterations
                        findSpecialRelevanceChanges(children1.subList(size2, size1), null, eventsToDispatch);
                    }
                } else {
                    // Other grouping controls
                    findSpecialRelevanceChanges(size1 == 0 ? null : children1, size2 == 0 ? null : children2, eventsToDispatch);
                }
            }
        }
    }
}
