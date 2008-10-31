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

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.NodeInfo;
import org.xml.sax.Locator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents all this XForms containing document controls and the context in which they operate.
 */
public class XFormsControls {

    private Locator locator;

    private boolean initialized;
    private ControlTree initialControlTree;
    private ControlTree currentControlTree;

    private boolean dirtySinceLastRequest;

    private XFormsContainingDocument containingDocument;
    private XFormsContainer rootContainer;
    
    private Map constantItems;

    public XFormsControls(XFormsContainingDocument containingDocument) {

        this.containingDocument = containingDocument;
        this.rootContainer = this.containingDocument;

        // Create minimal tree
        initialControlTree = new ControlTree();
        currentControlTree = initialControlTree;
    }

    public boolean isDirtySinceLastRequest() {
        return dirtySinceLastRequest;
    }
    
    public void markDirtySinceLastRequest(boolean bindingsAffected) {
        dirtySinceLastRequest = true;
        if (bindingsAffected)
            currentControlTree.markBindingsDirty();
    }

    /**
     * Returns whether there is any event handler registered anywhere in the controls for the given event name.
     *
     * @param eventName event name, like xforms-value-changed
     * @return          true if there is a handler, false otherwise
     */
    public boolean hasHandlerForEvent(String eventName) {
        return containingDocument.getStaticState().getEventNamesMap().get(eventName) != null;
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
        initializeState(pipelineContext, false);
    }

    /**
     * Initialize the controls if needed, passing initial state information. This is called if the state of the engine
     * needs to be rebuilt.
     *
     * @param pipelineContext       current PipelineContext
     * @param evaluateItemsets      whether to evaluateItemsets (for dynamic state restoration)
     */
    public void initializeState(PipelineContext pipelineContext, boolean evaluateItemsets) {

        final XFormsStaticState staticState = containingDocument.getStaticState();
        if (staticState != null && staticState.getControlsDocument() != null) {

            if (initialized) {
                // Use existing controls tree

                initialControlTree = currentControlTree;

                // Need to make sure that current == initial within controls
                visitAllControls(new XFormsControls.XFormsControlVisitorAdapter() {
                    public void startVisitControl(XFormsControl control) {
                        control.resetLocal();
                    }
                });

            } else {
                // Create new controls tree
                initialControlTree = createControlsTree(pipelineContext, evaluateItemsets);
                currentControlTree = initialControlTree;
            }

            // We are now clean
            dirtySinceLastRequest = false;
            initialControlTree.markBindingsClean();
        }

        rootContainer.getContextStack().resetBindingContext(pipelineContext);// not sure we actually need to do this

        initialized = true;
    }

    /**
     * Serialize controls into the dynamic state. Only the information that cannot be rebuilt from the instances is
     * serialized.
     *
     * @param dynamicStateElement
     */
    public void serializeControls(Element dynamicStateElement) {
        final Element controlsElement = Dom4jUtils.createElement("controls");
        visitAllControls(new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                final Map nameValues = control.serializeLocal();
                if (nameValues != null) {
                    final Element controlElement = controlsElement.addElement("control");
                    controlElement.addAttribute("effective-id", control.getEffectiveId());
                    for (Iterator i = nameValues.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry currentEntry = (Map.Entry) i.next();
                        controlElement.addAttribute((String) currentEntry.getKey(), (String) currentEntry.getValue());
                    }
                }
            }
        });
        // Only add the element if necessary
        if (controlsElement.hasContent())
            dynamicStateElement.add(controlsElement);
    }

    /**
     * Deserialize controls from the dynamic state. Only the information that cannot be rebuilt from the instances is
     * deserialized.
     *
     * @param dynamicStateElement
     */
    public void deserializeControls(Element dynamicStateElement) {
        final Element controlsElement = dynamicStateElement.element("controls");
        if (controlsElement != null) {
            for (Iterator i = controlsElement.elements("control").iterator(); i.hasNext();) {
                final Element currentControlElement = (Element) i.next();
                final XFormsControl currentControl = (XFormsControl) getObjectByEffectiveId(currentControlElement.attributeValue("effective-id"));
                currentControl.deserializeLocal(currentControlElement);
            }
        }
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    // TODO: Many callers of this won't get the proper context stack when dealing with components
    public XFormsContextStack getContextStack() {
        return rootContainer.getContextStack();
    }

    /**
     * Build the entire tree of controls and associated information.
     *
     * @param pipelineContext   pipeline context
     * @param evaluateItemsets  whether to evaluate itemsets (true when restoring dynamic state only)
     * @return                  controls tree
     */
    private ControlTree createControlsTree(final PipelineContext pipelineContext, final boolean evaluateItemsets) {

        containingDocument.startHandleOperation("controls", "building");
        final ControlTree result = new ControlTree(pipelineContext, containingDocument, rootContainer, evaluateItemsets);
        containingDocument.endHandleOperation();

        return result;
    }

    /**
     * Create a new repeat iteration for insertion into the current tree of controls.
     *
     * WARNING: The binding context must be set to the current iteration before calling.
     *
     * @param pipelineContext   pipeline context
     * @param repeatControl     repeat control
     * @param iterationIndex    new iteration to repeat (1..repeat size + 1)
     */
    public XFormsRepeatIterationControl createRepeatIterationTree(final PipelineContext pipelineContext, XFormsContextStack.BindingContext bindingContext,
                                                                  XFormsRepeatControl repeatControl, int iterationIndex) {

        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences())
            throw new OXFException("Cannot call insertRepeatIteration() when initialControlTree == currentControlTree");

        final XFormsRepeatIterationControl repeatIterationControl;
        containingDocument.startHandleOperation("controls", "adding iteration");
        repeatIterationControl = currentControlTree.createRepeatIterationTree(pipelineContext, bindingContext, repeatControl, iterationIndex);
        containingDocument.endHandleOperation();

        return repeatIterationControl;
    }

    /**
     * Evaluate all the controls if needed. Should be used before output initial XHTML and before computing differences
     * in XFormsServer.
     *
     * @param pipelineContext   current PipelineContext
     */
    public void evaluateControlValuesIfNeeded(PipelineContext pipelineContext) {

        containingDocument.startHandleOperation("controls", "evaluating");
        {
            final Map effectiveIdsToControls = getCurrentControlTree().getEffectiveIdsToControls();
            // Evaluate all controls
            if (effectiveIdsToControls != null) {
                for (Iterator i = effectiveIdsToControls.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final XFormsControl currentControl = (XFormsControl) currentEntry.getValue();
                    currentControl.evaluateIfNeeded(pipelineContext);
                }
            }
        }
        containingDocument.endHandleOperation();
    }

    /**
     * Get the ControlTree computed in the initialize() method.
     */
    public ControlTree getInitialControlTree() {
        return initialControlTree;
    }

    /**
     * Get the last computed ControlTree.
     */
    public ControlTree getCurrentControlTree() {
        return currentControlTree;
    }

    /**
     * Clone the current controls tree if:
     *
     * 1. it hasn't yet been cloned
     * 2. we are not during the XForms engine initialization
     *
     * The rationale for #2 is that there is no controls comparison needed during initialization. Only during further
     * client requests do the controls need to be compared.
     */
    public void cloneInitialStateIfNeeded() {
        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences()) {
            containingDocument.startHandleOperation("controls", "cloning");
            {
                try {
                    // NOTE: We clone "back", that is the new tree is used as the "initial" tree. This is done so that
                    // if we started working with controls in the initial tree, we can keep using those references safely.
                    initialControlTree = (ControlTree) currentControlTree.clone();
                } catch (CloneNotSupportedException e) {
                    throw new OXFException(e);
                }
            }
            containingDocument.endHandleOperation();
        }
    }

    /**
     * Rebuild the controls tree bindings if needed.
     */
    public boolean updateControlBindingsIfNeeded(final PipelineContext pipelineContext) {

        if (!initialized) {
            return false;
        } else {
            // This is the regular case

            // Don't do anything if bindings are clean
            if (!currentControlTree.isBindingsDirty())
                return false;

            // Clone if needed
            cloneInitialStateIfNeeded();

            containingDocument.startHandleOperation("controls", "updating bindings");
            {
                // Visit all controls and update their bindings
                visitControlElementsHandleRepeat(pipelineContext, containingDocument, rootContainer,
                        new UpdateBindingsListener(pipelineContext, currentControlTree.getEffectiveIdsToControls(), currentControlTree.getEventsToDispatch()));
            }
            containingDocument.endHandleOperation();

            // Controls are clean
            initialControlTree.markBindingsClean();
            currentControlTree.markBindingsClean();

            return true;
        }
    }

    /**
     * Return the current repeat index for the given xforms:repeat id, -1 if the id is not found.
     */
    public int getRepeatIndex(XFormsContainer container, String repeatStaticId) {
        // TODO: pass sourceId
        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) resolveObjectById(null, repeatStaticId);
        if (repeatControl != null) {
            // Found
            return repeatControl.getIndex();
        } else if (containingDocument.getStaticState().getControlInfoMap().get(container.getFullPrefix() + repeatStaticId) != null) {
            // A repeat element does exist for this id, but it has zero iterations

            // NOTE: above we make sure to use prefixed id, e.g. my-stuff$my-foo-bar$my-repeat
            return 0;
        } else {
            // No repeat element exists
            return -1;
        }
    }

    public Locator getLocator() {
        return locator;
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public Object getObjectByEffectiveId(String effectiveId) {
        // Until xforms-ready is dispatched, ids map may be null
        final Map effectiveIdsToControls = currentControlTree.getEffectiveIdsToControls();
        return (effectiveIdsToControls != null) ? effectiveIdsToControls.get(effectiveId) : null;
    }

    /**
     * Resolve an object. This optionally depends on a source control, and involves resolving whether the source is within a
     * repeat or a component.
     *
     * @param effectiveSourceId  effective id of the source control, or null
     * @param targetId           id of the target
     * @return                   object, or null if not found
     */
    public Object resolveObjectById(String effectiveSourceId, String targetId) {
        final String effectiveControlId = getCurrentControlTree().findEffectiveControlId(effectiveSourceId, targetId);
        return (effectiveControlId != null) ? getObjectByEffectiveId(effectiveControlId) : null;
    }

    /**
     * Visit all the controls elements by following repeats to allow creating the actual control.
     */
    public static void visitControlElementsHandleRepeat(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XFormsContainer rootContainer, ControlElementVisitorListener controlElementVisitorListener) {
        rootContainer.getContextStack().resetBindingContext(pipelineContext);
        final boolean isOptimizeRelevance = XFormsProperties.isOptimizeRelevance(containingDocument);
        final Element rootElement = containingDocument.getStaticState().getControlsDocument().getRootElement();

        visitControlElementsHandleRepeat(pipelineContext, controlElementVisitorListener, isOptimizeRelevance,
                containingDocument.getStaticState(), rootContainer, rootElement, "", "");
    }

    public static void visitControlElementsHandleRepeat(PipelineContext pipelineContext, XFormsRepeatControl enclosingRepeatControl, int iterationIndex, XFormsControls.ControlElementVisitorListener controlElementVisitorListener) {

        final XFormsContainingDocument containingDocument = enclosingRepeatControl.getContainer().getContainingDocument();
        final boolean isOptimizeRelevance = XFormsProperties.isOptimizeRelevance(containingDocument);

        // Set binding context on the particular repeat iteration
        final XFormsContextStack contextStack = enclosingRepeatControl.getContainer().getContextStack();
        contextStack.setBinding(enclosingRepeatControl);
        contextStack.pushIteration(iterationIndex);

        // Start visiting children of the xforms:repeat element
        final Element repeatControlElement = ((XFormsStaticState.ControlInfo) containingDocument.getStaticState().getControlInfoMap().get(enclosingRepeatControl.getPrefixedId())).getElement();
        XFormsControls.visitControlElementsHandleRepeat(pipelineContext, controlElementVisitorListener, isOptimizeRelevance,
                containingDocument.getStaticState(), enclosingRepeatControl.getContainer(), repeatControlElement,
                XFormsUtils.getEffectiveIdPrefix(enclosingRepeatControl.getEffectiveId()),
                XFormsUtils.getEffectiveIdSuffix(XFormsUtils.getIterationEffectiveId(enclosingRepeatControl.getEffectiveId(), iterationIndex)));
    }

    private static void visitControlElementsHandleRepeat(PipelineContext pipelineContext, ControlElementVisitorListener controlElementVisitorListener,
                                                    boolean isOptimizeRelevance, XFormsStaticState staticState, XFormsContainer currentContainer,
                                                    Element containerElement, String idPrefix, String idPostfix) {

        int variablesCount = 0;
        final XFormsContextStack currentContextStack = currentContainer.getContextStack();
        for (Iterator i = containerElement.elements().iterator(); i.hasNext();) {
            final Element currentControlElement = (Element) i.next();
            final String currentControlURI = currentControlElement.getNamespaceURI();
            final String currentControlName = currentControlElement.getName();

            final String staticControlId = currentControlElement.attributeValue("id");
            final String effectiveControlId
                    = idPrefix + staticControlId + (idPostfix.equals("") ? "" : XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + idPostfix);

            if (currentControlName.equals("repeat")) {
                // Handle xforms:repeat
                currentContextStack.pushBinding(pipelineContext, currentControlElement);

                // Visit xforms:repeat element
                controlElementVisitorListener.startVisitControl(currentContainer, currentControlElement, effectiveControlId);

                // Iterate over current xforms:repeat nodeset
                final List currentNodeSet = currentContextStack.getCurrentNodeset();
                if (currentNodeSet != null) {
                    for (int iterationIndex = 1; iterationIndex <= currentNodeSet.size(); iterationIndex++) {
                        // Push "artificial" binding with just current node in nodeset
                        currentContextStack.pushIteration(iterationIndex);
                        {
                            // Handle children of xforms:repeat
                            // TODO: handle isOptimizeRelevance()

                            // Compute repeat iteration id
                            final String iterationEffectiveId = XFormsUtils.getIterationEffectiveId(effectiveControlId, iterationIndex);

                            final boolean recurse = controlElementVisitorListener.startRepeatIteration(currentContainer, iterationIndex, iterationEffectiveId);
                            if (recurse) {
                                // When updating controls, the callee has the option of disabling recursion into an
                                // iteration. This is used for handling new iterations.
                                final String newIdPostfix = idPostfix.equals("") ? Integer.toString(iterationIndex) : (idPostfix + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + iterationIndex);
                                visitControlElementsHandleRepeat(pipelineContext, controlElementVisitorListener, isOptimizeRelevance,
                                        staticState, currentContainer, currentControlElement, idPrefix, newIdPostfix);
                            }
                            controlElementVisitorListener.endRepeatIteration(iterationIndex);
                        }
                        currentContextStack.popBinding();
                    }
                }

                controlElementVisitorListener.endVisitControl(currentControlElement, effectiveControlId);
                currentContextStack.popBinding();
            } else if (XFormsControlFactory.isContainerControl(currentControlURI, currentControlName)) {
                // Handle XForms grouping controls
                currentContextStack.pushBinding(pipelineContext, currentControlElement);
                controlElementVisitorListener.startVisitControl(currentContainer, currentControlElement, effectiveControlId);
                final XFormsContextStack.BindingContext currentBindingContext = currentContextStack.getCurrentBindingContext();
                {
                    // Recurse into grouping control and components if we don't optimize relevance, OR if we do
                    // optimize and we are not bound to a node OR we are bound to a relevant node

                    // NOTE: Simply excluding non-selected cases with the expression below doesn't work. So for
                    // now, we don't consider hidden cases as non-relevant. In the future, we might want to improve
                    // this.
                    // && (!controlName.equals("case") || isCaseSelectedByControlElement(controlElement, effectiveControlId, idPostfix))

                    if (!isOptimizeRelevance
                            || (!currentBindingContext.isNewBind()
                                 || (currentBindingContext.getSingleNode() != null && InstanceData.getInheritedRelevant(currentBindingContext.getSingleNode())))) {

                        visitControlElementsHandleRepeat(pipelineContext, controlElementVisitorListener, isOptimizeRelevance,
                                staticState, currentContainer, currentControlElement, idPrefix, idPostfix);
                    }
                }
                controlElementVisitorListener.endVisitControl(currentControlElement, effectiveControlId);
                currentContextStack.popBinding();
            } else if (XFormsControlFactory.isCoreControl(currentControlURI, currentControlName)) {
                // Handle leaf control
                currentContextStack.pushBinding(pipelineContext, currentControlElement);
                controlElementVisitorListener.startVisitControl(currentContainer, currentControlElement, effectiveControlId);
                controlElementVisitorListener.endVisitControl(currentControlElement, effectiveControlId);
                currentContextStack.popBinding();
            } else if (currentControlName.equals("variable")) {
                // Handle xxforms:variable specifically

                // Create variable object
                final Variable variable = new Variable(currentContainer.getContainingDocument(), currentContextStack, currentControlElement);

                // Push the variable on the context stack. Note that we do as if each variable was a "parent" of the following controls and variables.
                // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation in the future.
                currentContextStack.pushVariable(currentControlElement, variable.getVariableName(), variable.getVariableValue(pipelineContext, true));

                variablesCount++;
            } else if (staticState.isComponent(currentControlElement.getQName())) {
                // Handle components

                // NOTE: don't push the binding here, this is handled if necessary by the component implementation
                controlElementVisitorListener.startVisitControl(currentContainer, currentControlElement, effectiveControlId);
                {
                    // Compute new id prefix for nested component
                    final String newIdPrefix = idPrefix + staticControlId + XFormsConstants.COMPONENT_SEPARATOR;

                    // Get container
                    XFormsContainer newContainer = currentContainer.getChildById(staticControlId);
                    if (newContainer == null) {
                        // Container does not exist yet, create one
                        newContainer = currentContainer.createChildContainer(staticControlId, effectiveControlId, newIdPrefix);
                        newContainer.addAllModels();// NOTE: there may or may not be nested models
                        newContainer.initializeModels(pipelineContext);
                    } else {
                        // Container exists
                        // o controls are rebuilt
                        // o containers have been restored from the dynamic state
                    }

                    // Make sure there is location data
                    newContainer.setLocationData(XFormsUtils.getNodeLocationData(currentControlElement));

                    // In all cases, reset the binding context as this is used for recursing in the tree below
                    newContainer.setBindingContext(currentContextStack.getCurrentBindingContext());
                    newContainer.getContextStack().resetBindingContext(pipelineContext);

                    // Recurse into component tree
                    final Element shadowTreeDocumentElement = staticState.getCompactShadowTree(idPrefix + staticControlId);
                    visitControlElementsHandleRepeat(pipelineContext, controlElementVisitorListener, isOptimizeRelevance,
                            staticState, newContainer, shadowTreeDocumentElement, newIdPrefix, idPostfix);
                }
                controlElementVisitorListener.endVisitControl(currentControlElement, effectiveControlId);

            } else {
                // Ignore, this is not a control
            }
        }

        // Unscope all variables
        for (int i = 0; i < variablesCount; i++)
            currentContextStack.popBinding();
    }

    /**
     * Visit all the current XFormsControls.
     */
    public void visitAllControls(XFormsControlVisitorListener xformsControlVisitorListener) {
        currentControlTree.visitAllControls(xformsControlVisitorListener);
    }

    public static interface ControlElementVisitorListener {
        public void startVisitControl(XFormsContainer container, Element controlElement, String effectiveControlId);
        public void endVisitControl(Element controlElement, String effectiveControlId);
        public boolean startRepeatIteration(XFormsContainer container, int iteration, String effectiveIterationId);
        public void endRepeatIteration(int iteration);
    }

    public static interface XFormsControlVisitorListener {
        public void startVisitControl(XFormsControl control);
        public void endVisitControl(XFormsControl control);
    }

    public static class XFormsControlVisitorAdapter implements XFormsControlVisitorListener {
        public void startVisitControl(XFormsControl control) {}
        public void endVisitControl(XFormsControl control) {}
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
     * @param controlId     static control id
     * @param items         List<Item>
     */
    public void setConstantItems(String controlId, List items) {
        if (constantItems == null)
            constantItems = new HashMap();
        constantItems.put(controlId, items);
    }

    private static class UpdateBindingsListener implements ControlElementVisitorListener {

        private final PipelineContext pipelineContext;
        private final Map effectiveIdsToControls;
        private final Map eventsToDispatch;

        private UpdateBindingsListener(PipelineContext pipelineContext, Map effectiveIdsToControls, Map eventsToDispatch) {
            this.pipelineContext = pipelineContext;
            this.effectiveIdsToControls = effectiveIdsToControls;
            this.eventsToDispatch = eventsToDispatch;
        }

        private Map newIterationsMap = new HashMap();

        public void startVisitControl(XFormsContainer container, Element controlElement, String effectiveControlId) {
            final XFormsControl control = (XFormsControl) effectiveIdsToControls.get(effectiveControlId);

            final XFormsContextStack.BindingContext oldBindingContext = control.getBindingContext();
            final XFormsContextStack.BindingContext newBindingContext = container.getContextStack().getCurrentBindingContext();

            // Handle special relevance events
            {
                if (control instanceof XFormsSingleNodeControl) {
                    final NodeInfo boundNode1 = control.getBoundNode();
                    final NodeInfo boundNode2 = newBindingContext.getSingleNode();

                    boolean found = false;
                    int eventType = 0;
                    if (boundNode1 != null && InstanceData.getInheritedRelevant(boundNode1) && boundNode2 == null) {
                        // A control was bound to a node and relevant, but has become no longer bound to a node
                        found = true;
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    } else if (boundNode1 == null && boundNode2 != null && InstanceData.getInheritedRelevant(boundNode2)) {
                        // A control was not bound to a node, but has now become bound and relevant
                        found = true;
                        eventType = XFormsModel.EventSchedule.RELEVANT_BINDING;
                    } else if (boundNode1 != null && boundNode2 != null && !boundNode1.isSameNodeInfo(boundNode2)) {
                        // The control is now bound to a different node
                        // In this case, we schedule the control to dispatch all the events

                        // NOTE: This is not really proper according to the spec, but it does help applications to
                        // force dispatching in such cases
                        found = true;
                        eventType = XFormsModel.EventSchedule.ALL;
                    }

                    // Remember that we need to dispatch information about this control
                    if (found) {
                        eventsToDispatch.put(control.getEffectiveId(),
                                new XFormsModel.EventSchedule(control.getEffectiveId(), eventType, control));
                    }
                }
            }

            if (control instanceof XFormsRepeatControl) {
                // Get old nodeset
                final List oldRepeatNodeset = oldBindingContext.getNodeset();

                // Get new nodeset
                final List newRepeatNodeset = newBindingContext.getNodeset();

                // Set new current binding for control element
                control.setBindingContext(newBindingContext);

                // Update iterations
                final List newIterations = ((XFormsRepeatControl) control).updateIterations(pipelineContext, oldRepeatNodeset, newRepeatNodeset, null);

                // Remember newly created iterations so we don't recurse into them in startRepeatIteration()
                for (Iterator i = newIterations.iterator(); i.hasNext();) {
                    final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl)i.next();
                    newIterationsMap.put(repeatIterationControl.getEffectiveId(), repeatIterationControl);
                }
            } else {
                // Set new current binding for control element
                control.setBindingContext(newBindingContext);
            }

            // Mark the control as dirty so it gets reevaluated
            // NOTE: existing repeat iterations are marked dirty below in startRepeatIteration()
            control.markDirty();
        }

        public void endVisitControl(Element controlElement, String effectiveControlId) {
        }

        public boolean startRepeatIteration(XFormsContainer container, int iteration, String effectiveIterationId) {

            // Get reference to iteration control
            final XFormsRepeatIterationControl repeatIterationControl = (XFormsRepeatIterationControl) effectiveIdsToControls.get(effectiveIterationId);

            // Check whether this is an existing iteration as opposed to a newly created iteration
            final boolean isExistingIteration = newIterationsMap.get(effectiveIterationId) == null;
            if (isExistingIteration) {
                // Mark the control as dirty so it gets reevaluated
                repeatIterationControl.markDirty();
            }

            // Allow recursing into this iteration only if it is not a newly created iteration
            return isExistingIteration;
        }

        public void endRepeatIteration(int iteration) {
        }
    }
}
