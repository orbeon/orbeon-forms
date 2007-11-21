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
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerImpl;
import org.orbeon.oxf.xforms.event.events.XFormsDeselectEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSelectEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.XPathCache;
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

    private SwitchState initialSwitchState;
    private SwitchState currentSwitchState;

    private DialogState initialDialogState;
    private DialogState currentDialogState;

    private boolean dirtySinceLastRequest;

    private XFormsContainingDocument containingDocument;
    private Document controlsDocument;
    private Map eventsMap;
    
    private Map constantItems;

    protected Stack contextStack = new Stack();

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
    private static final Map singleNodeOrValueControls = new HashMap();

    static {
        groupingControls.put("group", "");
        groupingControls.put("repeat", "");
        groupingControls.put("switch", "");
        groupingControls.put("case", "");
        groupingControls.put("dialog", "");

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

        singleNodeOrValueControls.put("output", "");

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
        // Set initial repeat indexes
        if (controlsDocument != null) {
            final ControlsState result = new ControlsState();
            eventsMap = new HashMap();
            getDefaultRepeatIndexesEventNames(result, eventsMap);
            initialControlsState = result;
            currentControlsState = initialControlsState;
        }

        // Set repeat index state if any
        setRepeatIndexState(repeatIndexesElement);
    }

    public boolean isDirtySinceLastRequest() {
        return dirtySinceLastRequest;
    }
    
    public void markDirtySinceLastRequest() {
        dirtySinceLastRequest = true;
        if (this.currentControlsState != null)// can be null for legacy XForms engine
            this.currentControlsState.markDirty();
    }

    /**
     * Iterate statically through controls and set the default repeat index for xforms:repeat.
     *
     * @param controlsState    ControlsState to update with setDefaultRepeatIndex()
     * @param eventNames       Map to gather event names, null if not necessary
     */
    private void getDefaultRepeatIndexesEventNames(final ControlsState controlsState, final Map eventNames) {
        visitAllControlStatic(new ControlElementVisitorListener() {

            private Stack repeatStack = new Stack();

            public boolean startVisitControl(Element controlElement, String controlId) {
                if (controlElement.getName().equals("repeat")) {
                    // Create control without parent, just to hold iterations
                    final XFormsRepeatControl repeatControl
                            = new XFormsRepeatControl(containingDocument, null, controlElement, controlElement.getName(), controlId);

                    // Set initial index
                    controlsState.setDefaultRepeatIndex(repeatControl.getRepeatId(), repeatControl.getStartIndex());

                    // Keep control on stack
                    repeatStack.push(repeatControl);
                }

                // Gather event names if required
                if (eventNames != null) {
                    XFormsEventHandlerImpl.gatherEventHandlerNames(eventNames, controlElement);
                }

                return true;
            }

            public boolean endVisitControl(Element controlElement, String controlId) {
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


    /**
     * Returns whether there is any event handler registered anywhere in the controls for the given event name.
     *
     * @param eventName event name, like xforms-value-changed
     * @return          true if there is a handler, false otherwise
     */
    public boolean hasHandlerForEvent(String eventName) {
        return eventsMap.get(eventName) != null;
    }

    /**
     * Initialize the controls if needed. This is called upon initial creation of the engine OR when new exernal events
     * arrive.
     *
     * TODO: this is called in XFormsContainingDocument.prepareForExternalEventsSequence() but it is not really an
     * initialization in that case.
     *
     * @param pipelineContext   current PipelineContext
     */
    public void initialize(PipelineContext pipelineContext) {
        initializeState(pipelineContext, null, null, false);
    }

    /**
     * Initialize the controls if needed, passing initial state information. This is called if the state of the engine
     * needs to be rebuilt.
     *
     * @param pipelineContext       current PipelineContext
     * @param divsElement           current div elements, or null
     * @param repeatIndexesElement  current repeat indexes, or null
     */
    public void initializeState(PipelineContext pipelineContext, Element divsElement, Element repeatIndexesElement, boolean evaluateItemsets) {

        if (controlsDocument != null) {

            if (initialized) {
                // Use existing controls state
                initialControlsState = currentControlsState;
                initialSwitchState = currentSwitchState;
                initialDialogState = currentDialogState;
            } else {
                // Build controls state

                // Get initial controls state information
                initialControlsState = buildControlsState(pipelineContext, evaluateItemsets);
                currentControlsState = initialControlsState;

                initialSwitchState = new SwitchState(initialControlsState.getSwitchIdToSelectedCaseIdMap());
                currentSwitchState = initialSwitchState;

                initialDialogState = new DialogState(initialControlsState.getDialogIdToVisibleMap());
                currentDialogState = initialDialogState;

                // Set switch state if necessary
                if (divsElement != null) {
                    for (Iterator i = divsElement.elements().iterator(); i.hasNext();) {
                        final Element divElement = (Element) i.next();

                        final String dialogId = divElement.attributeValue("dialog-id");
                        final boolean isShow;
                        {
                            final String visibilityString = divElement.attributeValue("visibility");
                            isShow = "visible".equals(visibilityString);
                        }

                        if (dialogId != null) {
                            // xxforms:dialog
                            final String neighbor = divElement.attributeValue("neighbor");
                            final boolean constrainToViewport = "true".equals(divElement.attributeValue("constrain"));
                            currentDialogState.showHide(dialogId, isShow, isShow ? neighbor : null, constrainToViewport);
                        } else {
                            // xforms:switch/xforms:case
                            final String switchId = divElement.attributeValue("switch-id");
                            final String caseId = divElement.attributeValue("case-id");
                            currentSwitchState.initializeState(switchId, caseId, initialControlsState, isShow);
                        }
                    }
                }

                // Handle repeat indexes if needed
                if (initialControlsState.isHasRepeat()) {
                    // Get default xforms:repeat indexes beforehand
                    getDefaultRepeatIndexesEventNames(initialControlsState, null);// TODO: redundant since we called this in initializeMinimal()? If so, migrate initial indexes?

                    // Set external updates
                    setRepeatIndexState(repeatIndexesElement);

                    // Adjust repeat indexes
                    XFormsIndexUtils.adjustIndexes(pipelineContext, XFormsControls.this, initialControlsState);
                }
            }

            // We are now clean
            containingDocument.markCleanSinceLastRequest();
            dirtySinceLastRequest = false;
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
        resetBindingContext(containingDocument.getModel(""));
    }

    public void resetBindingContext(XFormsModel xformsModel) {
        // Clear existing stack
        contextStack.clear();

        // Push the default context
        if (xformsModel.getInstanceCount() > 0) {
            final List defaultNodeset = Arrays.asList(new Object[]{xformsModel.getDefaultInstance().getInstanceRootElementInfo()});
            contextStack.push(new BindingContext(null, xformsModel, defaultNodeset, 1, null, true, null, xformsModel.getDefaultInstance().getLocationData()));
        } else {
            contextStack.push(new BindingContext(null, xformsModel, Collections.EMPTY_LIST, 0, null, true, null, xformsModel.getLocationData()));
        }
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

    private void pushBinding(PipelineContext pipelineContext, XFormsControl xformsControl) {

        final Element bindingElement = xformsControl.getControlElement();
        if (!(xformsControl instanceof RepeatIterationControl)) {
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

            final XFormsControl repeatXFormsControl = xformsControl.getParent();
            final List repeatChildren = repeatXFormsControl.getChildren();
            final BindingContext currentBindingContext = getCurrentBindingContext();
            final List currentNodeset = currentBindingContext.getNodeset();

            final int repeatChildrenSize = (repeatChildren == null) ? 0 : repeatChildren.size();
            final int currentNodesetSize = (currentNodeset == null) ? 0 : currentNodeset.size();

            if (repeatChildrenSize != currentNodesetSize)
                throw new ValidationException("repeatChildren and newNodeset have different sizes.", xformsControl.getLocationData());

            // Push "artificial" binding with just current node in nodeset
            final XFormsModel newModel = currentBindingContext.getModel();
            final int position = ((RepeatIterationControl) xformsControl).getIteration();
            contextStack.push(new BindingContext(currentBindingContext, newModel, currentNodeset, position, xformsControl.getParent().getOriginalId(), true, null, repeatXFormsControl.getLocationData()));
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

        // TODO: PERF: Dom4jUtils.getNamespaceContextNoDefault() takes time. We should maybe cache those?
        final Map bindingElementNamespaceContext;
        bindingElementNamespaceContext = Dom4jUtils.getNamespaceContextNoDefault(bindingElement);
//        if (ref != null || nodeset != null) {
//            if (bindingElement instanceof NonLazyUserDataElement) {
//                final NonLazyUserDataElement dataElement = (NonLazyUserDataElement) bindingElement;
//                final Object data = dataElement.getData();
//
//                if (data == null) {
//                    // Get data and cache it
//                    bindingElementNamespaceContext = Dom4jUtils.getNamespaceContextNoDefault(bindingElement);
//                    dataElement.setData(bindingElementNamespaceContext);
//                } else if (data instanceof TreeMap) {
//                    // Use cached data
//                    bindingElementNamespaceContext = (Map) data;
//                } else {
//                    // Just compute the data
//                    bindingElementNamespaceContext = Dom4jUtils.getNamespaceContextNoDefault(bindingElement);
//                }
//            } else {
//                // Just compute the data
//                bindingElementNamespaceContext = Dom4jUtils.getNamespaceContextNoDefault(bindingElement);
//            }
//        } else {
//            // No need for the data
//            bindingElementNamespaceContext = null;
//        }
        pushBinding(pipelineContext, ref, context, nodeset, model, bind, bindingElement, bindingElementNamespaceContext);
    }

    public void pushBinding(PipelineContext pipelineContext, String ref, String context, String nodeset, String modelId, String bindId,
                            Element bindingElement, Map bindingElementNamespaceContext) {

        // Get location data for error reporting
        final LocationData locationData = (bindingElement == null)
                ? containingDocument.getLocationData()
                : new ExtendedLocationData((LocationData) bindingElement.getData(), "pushing XForms control binding", bindingElement);

        // Check for mandatory and optional bindings
        // TODO: This is static analysis to do only once and should be moved somewhere else
        if (bindingElement != null && XFormsConstants.XFORMS_NAMESPACE_URI.equals(bindingElement.getNamespaceURI())) {
            final String controlName = bindingElement.getName();
            if (mandatorySingleNodeControls.get(controlName) != null
                    && !(bindingElement.attribute("ref") != null || bindingElement.attribute("bind") != null)) {
                throw new ValidationException("Missing mandatory single node binding for element: " + bindingElement.getQualifiedName(), locationData);
            }
            if (noSingleNodeControls.get(controlName) != null
                    && (bindingElement.attribute("ref") != null || bindingElement.attribute("bind") != null)) {
                throw new ValidationException("Single node binding is prohibited for element: " + bindingElement.getQualifiedName(), locationData);
            }
            if (mandatoryNodesetControls.get(controlName) != null
                    && !(bindingElement.attribute("nodeset") != null || bindingElement.attribute("bind") != null)) {
                throw new ValidationException("Missing mandatory nodeset binding for element: " + bindingElement.getQualifiedName(), locationData);
            }
            if (noNodesetControls.get(controlName) != null
                    && bindingElement.attribute("nodeset") != null) {
                throw new ValidationException("Node-set binding is prohibited for element: " + bindingElement.getQualifiedName(), locationData);
            }
            if (singleNodeOrValueControls.get(controlName) != null
                    && !(bindingElement.attribute("ref") != null || bindingElement.attribute("bind") != null || bindingElement.attribute("value") != null)) {
                throw new ValidationException("Missing mandatory single node binding or value attribute for element: " + bindingElement.getQualifiedName(), locationData);
            }
        }

        // Determine current context
        final BindingContext currentBindingContext = getCurrentBindingContext();

        // Handle model
        final XFormsModel newModel;
        final boolean isNewModel;
        if (modelId != null) {
            newModel = containingDocument.getModel(modelId);
            if (newModel == null)
                throw new ValidationException("Invalid model id: " + modelId, locationData);
            isNewModel = newModel != currentBindingContext.getModel();// don't say it's a new model unless it has really changed
        } else {
            newModel = currentBindingContext.getModel();
            isNewModel = false;
        }

        // Handle nodeset
        final boolean isNewBind;
        final int newPosition;
        final List newNodeset;
        {
            if (bindId != null) {
                // Resolve the bind id to a nodeset
                final ModelBind modelBind = newModel.getModelBindById(bindId);
                if (modelBind == null)
                    throw new ValidationException("Cannot find bind for id: " + bindId, locationData);
                newNodeset = newModel.getBindNodeset(pipelineContext, modelBind);
                isNewBind = true;
                newPosition = 1;
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
                            contextStack.push(new BindingContext(currentBindingContext, newModel, modelBindingContext.getNodeset(), modelBindingContext.getPosition(), null, false, null, locationData));
                        else
                            contextStack.push(new BindingContext(currentBindingContext, newModel, getCurrentNodeset(newModel.getEffectiveId()), 1, null, false, null, locationData));

                        // Evaluate new node-set
                        newNodeset = XPathCache.evaluate(pipelineContext, Collections.singletonList(currentSingleNodeForModel), 1,
                                ref != null ? ref : nodeset, bindingElementNamespaceContext, null, XFormsContainingDocument.getFunctionLibrary(), XFormsControls.this, null, locationData);

                        // Restore context
                        contextStack.pop();
                    } else {
                        newNodeset = Collections.EMPTY_LIST;
                    }

                } else {
                    // Simply evaluate new node-set
                    final BindingContext contextBindingContext = getCurrentBindingContext();
                    if (contextBindingContext != null && contextBindingContext.getNodeset().size() > 0) {
                        newNodeset = XPathCache.evaluate(pipelineContext, contextBindingContext.getNodeset(), contextBindingContext.getPosition(),
                                ref != null ? ref : nodeset, bindingElementNamespaceContext, null, XFormsContainingDocument.getFunctionLibrary(), XFormsControls.this, null, locationData);
                    } else {
                        newNodeset = Collections.EMPTY_LIST;
                    }
                }

                // Restore optional context
                if (nodeset != null && context != null) {
                    popBinding();
                }
                isNewBind = true;
                newPosition = 1;
            } else if (isNewModel) {
                // Only the model has changed
                final NodeInfo currentSingleNodeForModel = getCurrentSingleNode(newModel.getEffectiveId());
                if (currentSingleNodeForModel != null) {
                    newNodeset = Collections.singletonList(currentSingleNodeForModel);
                    newPosition = 1;
                } else {
                    newNodeset = Collections.EMPTY_LIST;
                    newPosition = -1;// this is not a valid position and nobody should use the node-set anyway
                }
                isNewBind = false;
            } else {
                // No change to current nodeset
                newNodeset = currentBindingContext.getNodeset();
                isNewBind = false;
                newPosition = currentBindingContext.getPosition();
            }
        }

        // Push new context
        final String id = (bindingElement == null) ? null : bindingElement.attributeValue("id");
        contextStack.push(new BindingContext(currentBindingContext, newModel, newNodeset, newPosition, id, isNewBind, bindingElement, locationData));
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
        final XFormsInstance defaultInstance = containingDocument.getModel(modelId).getDefaultInstance();
        if (defaultInstance == null)
            throw new ValidationException("Cannot find default instance in model: " + modelId, bindingContext.getLocationData());
        return Collections.singletonList(defaultInstance.getInstanceRootElementInfo());
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
            return XFormsInstance.getValueForNodeInfo(currentSingleNode);
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
            if (bindingElement == null && repeatIdForIteration != null) {// NOTE: test on bindingElement == null is just to detect whether this is a repeat iteration
                if (repeatId == null || repeatId.equals(repeatIdForIteration)) {
                    // Found binding context for relevant repeat iteration
                    return currentBindingContext.getSingleNode();
                }
            }
        }
        // It is required that there is a relevant enclosing xforms:repeat
        if (repeatId == null)
            throw new ValidationException("No enclosing xforms:repeat found.", getCurrentBindingContext().getLocationData());
        else
            throw new ValidationException("No enclosing xforms:repeat found for repeat id: " + repeatId, getCurrentBindingContext().getLocationData());
    }

    /**
     * Return the closest enclosing repeat id.
     *
     * @return  repeat id, throw if not found
     */
    public String getEnclosingRepeatId() {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final Element bindingElement = currentBindingContext.getControlElement();
            final String repeatIdForIteration = currentBindingContext.getIdForContext();
            if (bindingElement == null && repeatIdForIteration != null) {// NOTE: test on bindingElement == null is just to detect whether this is a repeat iteration
                // Found binding context for relevant repeat iteration
                return repeatIdForIteration;
            }
        }
        // It is required that there is a relevant enclosing xforms:repeat
        throw new ValidationException("Enclosing xforms:repeat not found.", getCurrentBindingContext().getLocationData());
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
            if (bindingElement != null && idForContext != null && groupingControls.get(bindingElement.getName()) != null) {
                if (contextId == null || contextId.equals(idForContext)) {
                    // Found matching binding context
                    return currentBindingContext.getNodeset();
                }
            }
        }
        // It is required that there is a matching enclosing id?
        if (contextId == null)
            throw new ValidationException("No enclosing container XForms control found.", getCurrentBindingContext().getLocationData());
        else
            throw new ValidationException("No enclosing container XForms control found for id: " + contextId, getCurrentBindingContext().getLocationData());
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
                return getContainingDocument().getInstanceForNode(currentSingleNode);
        }
        return null;
    }

    private ControlsState buildControlsState(final PipelineContext pipelineContext, final boolean evaluateItemsets) {

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
        final Map dialogIdToVisibleMap = new HashMap();

        visitAllControlsHandleRepeat(pipelineContext, new XFormsControls.ControlElementVisitorListener() {

            private XFormsControl currentControlsContainer = rootXFormsControl;

            public boolean startVisitControl(Element controlElement, String effectiveControlId) {

                if (effectiveControlId == null)
                    throw new ValidationException("Control element doesn't have an id", new ExtendedLocationData((LocationData) controlElement.getData(),
                            "analyzing control element", controlElement));

                final String controlName = controlElement.getName();

                // Create XFormsControl with basic information
                final XFormsControl xformsControl = XFormsControlFactory.createXFormsControl(containingDocument, currentControlsContainer, controlElement, controlName, effectiveControlId);

                // TODO: the following block should be part of a static analysis
                {
                    if (xformsControl instanceof XFormsRepeatControl)
                        result.setHasRepeat(true);
                    else if (xformsControl instanceof XFormsUploadControl)
                        result.addUploadControl((XFormsUploadControl) xformsControl);

                    // Make sure there are no duplicate ids
                    if (idsToXFormsControls.get(effectiveControlId) != null)
                        throw new ValidationException("Duplicate id for XForms control: " + effectiveControlId, new ExtendedLocationData((LocationData) controlElement.getData(),
                                "analyzing control element", controlElement, new String[] { "id", effectiveControlId }));
                }

                idsToXFormsControls.put(effectiveControlId, xformsControl);

                // Handle xforms:case
                if (controlName.equals("case")) {
                    if (!(currentControlsContainer.getName().equals("switch")))
                        throw new ValidationException("xforms:case with id '" + effectiveControlId + "' is not directly within an xforms:switch container.", xformsControl.getLocationData());
                    final String switchId = currentControlsContainer.getEffectiveId();

                    if (switchIdToSelectedCaseIdMap.get(switchId) == null) {
                        // If case is not already selected for this switch and there is a select attribute, set it
                        final String selectedAttribute = controlElement.attributeValue("selected");
                        if ("true".equals(selectedAttribute))
                            switchIdToSelectedCaseIdMap.put(switchId, effectiveControlId);
                    }
                }

                // Handle xxforms:dialog
                if (controlName.equals("dialog")) {
                    dialogIdToVisibleMap.put(effectiveControlId, new DialogState.DialogInfo(false, null, false));
                }

                // Handle xforms:itemset
                if (xformsControl instanceof XFormsSelectControl || xformsControl instanceof XFormsSelect1Control) {
                    final XFormsSelect1Control select1Control = ((XFormsSelect1Control) xformsControl);
                    // NOTE: This is dirty anyway because we just created the control
                    select1Control.markItemsetDirty();
                    // Evaluate itemsets only if specified (case of restoring dynamic state)
                    if (evaluateItemsets)
                        select1Control.getItemset(pipelineContext, false);
                }

                // Set current binding for control element
                final BindingContext currentBindingContext = getCurrentBindingContext();
                xformsControl.setBindingContext(currentBindingContext);

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

                final XFormsControl repeatIterationControl = new RepeatIterationControl(containingDocument, currentControlsContainer, iteration);
                currentControlsContainer.addChild(repeatIterationControl);
                currentControlsContainer = repeatIterationControl;

                // Set current binding for control
                final BindingContext currentBindingContext = getCurrentBindingContext();
                repeatIterationControl.setBindingContext(currentBindingContext);
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
        result.setDialogIdToVisibleMap(dialogIdToVisibleMap);

        if (XFormsServer.logger.isDebugEnabled()) {
            XFormsServer.logger.debug("XForms - building controls state end: " + (System.currentTimeMillis() - startTime) + " ms.");
        }

        return result;
    }

    /**
     * Evaluate all the controls if needed. Should be used before output initial XHTML and before computing differences
     * in XFormsServer.
     *
     * @param pipelineContext   current PipelineContext
     */
    public void evaluateAllControlsIfNeeded(PipelineContext pipelineContext) {

        final long startTime;
        if (XFormsServer.logger.isDebugEnabled()) {
            XFormsServer.logger.debug("XForms - evaluating controls start.");
            startTime = System.currentTimeMillis();
        } else {
            startTime = 0;
        }

        final Map idsToXFormsControls = getCurrentControlsState().getIdsToXFormsControls();
        // Evaluate all controls
        for (Iterator i = idsToXFormsControls.entrySet().iterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final XFormsControl currentControl = (XFormsControl) currentEntry.getValue();
            currentControl.evaluateIfNeeded(pipelineContext);
        }

        if (XFormsServer.logger.isDebugEnabled()) {
            XFormsServer.logger.debug("XForms - evaluating controls end: " + (System.currentTimeMillis() - startTime) + " ms.");
        }
    }

    /**
     * Get the ControlsState computed in the initialize() method.
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
     * Get the SwitchState computed in the initialize() method.
     */
    public SwitchState getInitialSwitchState() {
        return initialSwitchState;
    }

    /**
     * Get the last computed SwitchState.
     */
    public SwitchState getCurrentSwitchState() {
        return currentSwitchState;
    }


    public DialogState getInitialDialogState() {
        return initialDialogState;
    }

    public DialogState getCurrentDialogState() {
        return currentDialogState;
    }

    /**
     * Activate a switch case by case id.
     *
     * @param pipelineContext   current PipelineContext
     * @param caseId            case id to activate
     */
    public void activateCase(PipelineContext pipelineContext, String caseId) {

        if (initialSwitchState == currentSwitchState)
            currentSwitchState = new SwitchState(new HashMap(initialSwitchState.getSwitchIdToSelectedCaseIdMap()));

        // TODO: if switch state changes AND we optimize relevance, then mark controls as dirty

        getCurrentSwitchState().updateSwitchInfo(pipelineContext, containingDocument, getCurrentControlsState(), caseId);
        containingDocument.markDirtySinceLastRequest();
    }


    public void showHideDialog(String dialogId, boolean show, String neighbor, boolean constrainToViewport) {

        // Make sure the id refers to an existing xxforms:dialog
        final Object object = getObjectById(dialogId);
        if (object == null || !(object instanceof XXFormsDialogControl))
            return;

        // Update state
        if (initialDialogState == currentDialogState)
            currentDialogState = new DialogState(new HashMap(initialDialogState.getDialogIdToVisibleMap()));

        currentDialogState.showHide(dialogId, show, neighbor, constrainToViewport);
        containingDocument.markDirtySinceLastRequest();
    }

    /**
     * Rebuild the current controls state information if needed.
     */
    public boolean rebuildCurrentControlsStateIfNeeded(PipelineContext pipelineContext) {

        if (!initialized) {
            // This can occur because we lazily initialize controls during initialization
            initialize(pipelineContext);
            return true;
        } else {
            // This is the regular case

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
    }

    /**
     * Rebuild the current controls state information.
     */
    public void rebuildCurrentControlsState(PipelineContext pipelineContext) {

        // If we haven't been initialized yet, don't do anything
        if (!initialized)
            return;

        // Remember current state
        final ControlsState currentControlsState = this.currentControlsState;

        // Create new controls state
        final ControlsState result = buildControlsState(pipelineContext, false);

        // Transfer some of the previous information
        final Map currentRepeatIdToIndex = currentControlsState.getRepeatIdToIndex();
        if (currentRepeatIdToIndex.size() != 0) {
            // Keep repeat index information
            result.setRepeatIdToIndex(currentRepeatIdToIndex);
            // Adjust repeat indexes if necessary
            XFormsIndexUtils.adjustIndexes(pipelineContext, XFormsControls.this, result);
        }

        // Update xforms;switch information: use new values, except where old values are available
        final Map oldSwitchIdToSelectedCaseIdMap = getCurrentSwitchState().getSwitchIdToSelectedCaseIdMap();
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

            this.currentSwitchState = new SwitchState(newSwitchIdToSelectedCaseIdMap);
        }

        // Update xxforms:dialog information
        // NOP!

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

        if (currentIndex != null) {
            return currentIndex.intValue();
        } else {
            // If the controls are not initialized, then we give the caller a chance and return 0. This will make sure
            // that index() used in a calculate MIP will return something during the recalculate at the end of
            // xforms-model-construct, instead of throwing an exception. Ideally, we should still return -1 if there is
            // no xforms:repeat with the id provided.
            return (initialized) ? -1 : 0;
        }
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

//        for (Iterator i = currentControlsState.getIdToControl().entrySet().iterator(); i.hasNext(); ) {
//            final Map.Entry entry = (Map.Entry) i.next();
//            System.out.println("XXX entry: " + entry.getKey() + " => " + entry.getValue());
//        }

        return currentControlsState.getIdsToXFormsControls().get(controlId);
    }

    /**
     * Visit all the effective controls elements.
     */
    public void visitAllControlsHandleRepeat(PipelineContext pipelineContext, ControlElementVisitorListener controlElementVisitorListener) {
        resetBindingContext();
        final boolean isOptimizeRelevance = XFormsProperties.isOptimizeRelevance();
        handleControls(pipelineContext, controlElementVisitorListener, isOptimizeRelevance, controlsDocument.getRootElement(), "");
    }

    private boolean handleControls(PipelineContext pipelineContext, ControlElementVisitorListener controlElementVisitorListener,
                                   boolean isOptimizeRelevance, Element container, String idPostfix) {
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
                            contextStack.push(new BindingContext(currentBindingContext, currentBindingContext.getModel(), currentNodeSet, currentPosition, controlId, true, null, (LocationData) controlElement.getData()));
                            try {
                                // Handle children of xforms:repeat
                                if (doContinue) {
                                    // TODO: handle isOptimizeRelevance()
                                    controlElementVisitorListener.startRepeatIteration(currentPosition);
                                    final String newIdPostfix = idPostfix.equals("") ? Integer.toString(currentPosition) : (idPostfix + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + currentPosition);
                                    doContinue = handleControls(pipelineContext, controlElementVisitorListener, isOptimizeRelevance, controlElement, newIdPostfix);
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
                    final BindingContext currentBindingContext = getCurrentBindingContext();
                    if (doContinue) {
                        // Recurse into grouping control if we don't optimize relevance, OR if we do optimize and we are
                        // not bound to a node OR we are bound to a relevant node

                        // NOTE: Simply excluding non-selected cases with the expression below doesn't work. So for
                        // now, we don't consider hidden cases as non-relevant. In the future, we might want to improve
                        // this.
                        // && (!controlName.equals("case") || isCaseSelectedByControlElement(controlElement, effectiveControlId, idPostfix))

                        if (!isOptimizeRelevance
                                || (!currentBindingContext.isNewBind()
                                     || (currentBindingContext.getSingleNode() != null && InstanceData.getInheritedRelevant(currentBindingContext.getSingleNode())))) {

                            doContinue = handleControls(pipelineContext, controlElementVisitorListener, isOptimizeRelevance, controlElement, idPostfix);
                        }
                    }
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

//    private boolean isCaseSelectedByControlElement(Element controlElement, String caseElementEffectiveId, String idPostfix) {
//        final Element switchElement = controlElement.getParent();
//        final String switchId = switchElement.attributeValue("id");
//        final String switchElementEffectiveId = switchId + (idPostfix.equals("") ? "" : XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + idPostfix);
//
//        final XFormsControls.SwitchState switchState = containingDocument.getXFormsControls().getCurrentSwitchState();
//
//        // TODO: check @selected attribute, no?
//        if (switchState == null)
//            return true;
//
//        final Map switchIdToSelectedCaseIdMap = switchState.getSwitchIdToSelectedCaseIdMap();
//        final String selectedCaseId = (String) switchIdToSelectedCaseIdMap.get(switchElementEffectiveId);
//
//        return caseElementEffectiveId.equals(selectedCaseId);
//    }

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
        private LocationData locationData;

        public BindingContext(BindingContext parent, XFormsModel model, List nodeSet, int position, String idForContext, boolean newBind, Element controlElement, LocationData locationData) {
            this.parent = parent;
            this.model = model;
            this.nodeset = nodeSet;
            this.position = position;
            this.idForContext = idForContext;
            this.newBind = newBind;
            this.controlElement = controlElement;
            this.locationData = (locationData != null) ? locationData : (controlElement != null) ? (LocationData) controlElement.getData() : null;

            if (nodeset != null && nodeset.size() > 0) {
                // TODO: PERF: This seems to take some significant time
                for (Iterator i = nodeset.iterator(); i.hasNext();) {
                    final Object currentItem = i.next();
                    if (!(currentItem instanceof NodeInfo))
                        throw new ValidationException("A reference to a node (such as text, element, or attribute) is required in a binding. Attempted to bind to the invalid item type: " + currentItem.getClass(), this.locationData);
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
         * Convenience method returning the location data associated with the XForms element (typically, a control)
         * associated with the binding. If location data was passed during construction, pass that, otherwise try to get
         * location data from passed element.
         *
         * @return  LocationData object, or null if not found
         */
        public LocationData getLocationData() {
            return locationData;
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
     * Represents the state of switches.
     */
    public static class SwitchState {

        private Map switchIdToSelectedCaseIdMap;

        public SwitchState(Map switchIdToSelectedCaseIdMap) {
            this.switchIdToSelectedCaseIdMap = switchIdToSelectedCaseIdMap;
        }

        public Map getSwitchIdToSelectedCaseIdMap() {
            return switchIdToSelectedCaseIdMap;
        }

        /**
         * Update xforms:switch/xforms:case information with newly selected case id.
         */
        public void updateSwitchInfo(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, ControlsState controlsState, String selectedCaseId) {

            // Find SwitchXFormsControl
            final XFormsControl caseXFormsControl = (XFormsControl) controlsState.getIdsToXFormsControls().get(selectedCaseId);
            if (caseXFormsControl == null)
                throw new OXFException("No XFormsControl found for case id '" + selectedCaseId + "'.");
            final XFormsControl switchXFormsControl = (XFormsControl) caseXFormsControl.getParent();
            if (switchXFormsControl == null)
                throw new OXFException("No SwitchXFormsControl found for case id '" + selectedCaseId + "'.");

            final String currentSelectedCaseId = (String) getSwitchIdToSelectedCaseIdMap().get(switchXFormsControl.getEffectiveId());
            if (!selectedCaseId.equals(currentSelectedCaseId)) {
                // A new selection occurred on this switch

                // "This action adjusts all selected attributes on the affected cases to reflect the
                // new state, and then performs the following:"
                getSwitchIdToSelectedCaseIdMap().put(switchXFormsControl.getEffectiveId(), selectedCaseId);

                // "1. Dispatching an xforms-deselect event to the currently selected case."
                containingDocument.dispatchEvent(pipelineContext, new XFormsDeselectEvent((XFormsEventTarget) controlsState.getIdsToXFormsControls().get(currentSelectedCaseId)));

                // "2. Dispatching an xform-select event to the case to be selected."
                containingDocument.dispatchEvent(pipelineContext, new XFormsSelectEvent((XFormsEventTarget) controlsState.getIdsToXFormsControls().get(selectedCaseId)));
            }
        }

        /**
         * Update switch info state for the given case id.
         */
        public void initializeState(String switchId, String caseId, ControlsState controlsState, boolean visible) {
            // Update currently selected case id
            if (visible) {
                getSwitchIdToSelectedCaseIdMap().put(switchId, caseId);
            }
        }
    }

    /**
     * Represents the state of dialogs.
     */
    public static class DialogState {
        private Map dialogIdToVisibleMap;

        public DialogState(Map dialogIdToVisibleMap) {
            this.dialogIdToVisibleMap = dialogIdToVisibleMap;
        }

        public Map getDialogIdToVisibleMap() {
            return dialogIdToVisibleMap;
        }

        public void showHide(String dialogId, boolean show, String neighbor, boolean constrainToViewport) {
            dialogIdToVisibleMap.put(dialogId, new DialogInfo(show, neighbor, constrainToViewport));
        }

        public static class DialogInfo {
            public boolean show;
            public String neighbor;
            public boolean constrainToViewport;

            public DialogInfo(boolean show, String neighbor, boolean constrainToViewport) {
                this.show = show;
                this.neighbor = neighbor;
                this.constrainToViewport = constrainToViewport;
            }

            public boolean isShow() {
                return show;
            }

            public String getNeighbor() {
                return neighbor;
            }

            public boolean isConstrainToViewport() {
                return constrainToViewport;
            }
        }
    }

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
        private Map dialogIdToVisibleMap;

        private boolean hasRepeat;
        private List uploadControlsList;

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

        public Map getIdsToXFormsControls() {
            return idsToXFormsControls;
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

        public Map getSwitchIdToSelectedCaseIdMap() {
            return switchIdToSelectedCaseIdMap;
        }

        public void setSwitchIdToSelectedCaseIdMap(Map switchIdToSelectedCaseIdMap) {
            this.switchIdToSelectedCaseIdMap = switchIdToSelectedCaseIdMap;
        }


        public Map getDialogIdToVisibleMap() {
            return dialogIdToVisibleMap;
        }

        public void setDialogIdToVisibleMap(Map dialogIdToVisibleMap) {
            this.dialogIdToVisibleMap = dialogIdToVisibleMap;
        }

        public boolean isHasRepeat() {
            return hasRepeat;
        }

        public void setHasRepeat(boolean hasRepeat) {
            this.hasRepeat = hasRepeat;
        }

        public void addUploadControl(XFormsUploadControl uploadControl) {
            if (uploadControlsList == null)
                uploadControlsList = new ArrayList();
            uploadControlsList.add(uploadControl);
        }

        public List getUploadControls() {
            return uploadControlsList;
        }

        public boolean isHasUpload() {
            return uploadControlsList != null && uploadControlsList.size() > 0;
        }

        /**
         * Return the list of repeat ids descendent of a given repeat id, null if none.
         */
        public List getNestedRepeatIds(XFormsControls xformsControls, final String repeatId) {

            final List result = new ArrayList();

            xformsControls.visitAllControlStatic(new ControlElementVisitorListener() {

                private boolean found;

                public boolean startVisitControl(Element controlElement, String controlId) {
                    if (controlElement.getName().equals("repeat")) {

                        if (!found) {
                            // Not found yet
                            if (repeatId.equals(controlId))
                                found = true;
                        } else {
                            // We are within the searched repeat id
                            result.add(controlId);
                        }
                    }
                    return true;
                }

                public boolean endVisitControl(Element controlElement, String controlId) {
                    if (found) {
                        if (repeatId.equals(controlId))
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
            if (leadingControl instanceof XFormsSingleNodeControl) {
                // xforms:repeat doesn't need to be handled independently, iterations do it

                final XFormsSingleNodeControl xformsSingleNodeControl1 = (XFormsSingleNodeControl) xformsControl1;
                final XFormsSingleNodeControl xformsSingleNodeControl2 = (XFormsSingleNodeControl) xformsControl2;

                String foundControlId = null;
                XFormsControl targetControl = null;
                int eventType = 0;
                if (xformsControl1 != null && xformsControl2 != null) {
                    final NodeInfo boundNode1 = xformsControl1.getBoundNode();
                    final NodeInfo boundNode2 = xformsControl2.getBoundNode();

                    if (boundNode1 != null && xformsSingleNodeControl1.isRelevant() && boundNode2 == null) {
                        // A control was bound to a node and relevant, but has become no longer bound to a node
                        foundControlId = xformsControl2.getEffectiveId();
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    } else if (boundNode1 == null && boundNode2 != null && xformsSingleNodeControl2.isRelevant()) {
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
                    if (boundNode2 != null && xformsSingleNodeControl2.isRelevant()) {
                        // A control was not bound to a node, but has now become bound and relevant
                        foundControlId = xformsControl2.getEffectiveId();
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    }
                } else if (xformsControl1 != null) {
                    final NodeInfo boundNode1 = xformsControl1.getBoundNode();
                    if (boundNode1 != null && xformsSingleNodeControl1.isRelevant()) {
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

    /**
     * Get the items for a given control id. This is not an effective id, but an original control id.
     *
     * @param controlId     original control id
     * @return              List of Item
     */
    public List getConstantItems(String controlId) {
        if (constantItems == null)
            return null;
        else
            return (List) constantItems.get(controlId);
    }

    /**
     * Set the items for a given control id. This is not an effective id, but an original control id.
     *
     * @param controlId     original control id
     * @param items         List of Item
     */
    public void setConstantItems(String controlId, List items) {
        if (constantItems == null)
            constantItems = new HashMap();
        constantItems.put(controlId, items);
    }
}
