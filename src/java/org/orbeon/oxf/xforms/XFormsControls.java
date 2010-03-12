/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents all this XForms containing document controls and the context in which they operate.
 */
public class XFormsControls implements XFormsObjectResolver {

    public static final String LOGGING_CATEGORY = "control";
    public static final Logger logger = LoggerFactory.createLogger(XFormsModel.class);
    public final IndentedLogger indentedLogger;

    private boolean initialized;
    private ControlTree initialControlTree;
    private ControlTree currentControlTree;

    private boolean dirtySinceLastRequest;

    // Whether we currently require a UI refresh
    private boolean requireRefresh;

    private XFormsContainingDocument containingDocument;
    private XBLContainer rootContainer;
    
    private Map<String, Itemset> constantItems;

    public XFormsControls(XFormsContainingDocument containingDocument) {

        this.indentedLogger = containingDocument.getIndentedLogger(LOGGING_CATEGORY);

        this.containingDocument = containingDocument;
        this.rootContainer = this.containingDocument;

        // Create minimal tree
        initialControlTree = new ControlTree(containingDocument, indentedLogger);
        currentControlTree = initialControlTree;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    public boolean isDirtySinceLastRequest() {
        return dirtySinceLastRequest;
    }
    
    public void markDirtySinceLastRequest(boolean bindingsAffected) {
        dirtySinceLastRequest = true;
        if (bindingsAffected)
            currentControlTree.markBindingsDirty();
    }

    public void requireRefresh() {
        this.requireRefresh = true;
        markDirtySinceLastRequest(true);
    }

    public boolean isRequireRefresh() {
        return requireRefresh;
    }

    public void refreshDone() {
        requireRefresh = false;
    }

    public void refreshIfNeeded(PropertyContext propertyContext, XBLContainer container) {
        if (requireRefresh) {
            // Refresh is global for now
            // Will be cleared by doRefresh()
            doRefresh(propertyContext, container);
        }
    }

    /**
     * Initialize the controls if needed. This is called upon initial creation of the engine OR when new external events
     * arrive.
     *
     * TODO: this is called in XFormsContainingDocument.prepareForExternalEventsSequence() but it is not really an
     * initialization in that case.
     *
     * @param propertyContext   current context
     */
    public void initialize(PropertyContext propertyContext) {
        initializeState(propertyContext);
    }

    /**
     * Initialize the controls if needed, passing initial state information. This is called if the state of the engine
     * needs to be built or rebuilt.
     *
     * @param propertyContext   current context
     */
    public void initializeState(PropertyContext propertyContext) {

        final XFormsStaticState staticState = containingDocument.getStaticState();
        if (staticState.getControlsDocument() != null) {

            // We are now clean
            // NOTE: Do this here, because ControlTree.initialize() can dispatch events and make the tree dirty
            dirtySinceLastRequest = false;
            initialControlTree.markBindingsClean();

            if (initialized) {
                // Use existing controls tree

                initialControlTree = currentControlTree;

                // Need to make sure that current == initial within controls
                visitAllControls(new XFormsControls.XFormsControlVisitorAdapter() {
                    public boolean startVisitControl(XFormsControl control) {
                        control.resetLocal();
                        return true;
                    }
                });

            } else {
                // Create new controls tree
                // NOTE: We set this first so that the tree is made available during construction to XPath functions like index() or xxforms:case() 
                currentControlTree = initialControlTree = new ControlTree(containingDocument, indentedLogger);

                // Initialize new control tree
                currentControlTree.initialize(propertyContext, containingDocument, rootContainer);
            }
        }

//        rootContainer.getContextStack().resetBindingContext(propertyContext);// not sure we actually need to do this here

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
            public boolean startVisitControl(XFormsControl control) {
                final Map nameValues = control.serializeLocal();
                if (nameValues != null) {
                    final Element controlElement = controlsElement.addElement("control");
                    controlElement.addAttribute("effective-id", control.getEffectiveId());
                    for (Object o: nameValues.entrySet()) {
                        final Map.Entry currentEntry = (Map.Entry) o;
                        controlElement.addAttribute((String) currentEntry.getKey(), (String) currentEntry.getValue());
                    }
                }
                return true;
            }
        });
        // Only add the element if necessary
        if (controlsElement.hasContent())
            dynamicStateElement.add(controlsElement);
    }

    /**
     * Get serialized control state as a Map. Only the information that cannot be rebuilt from the instances is
     * deserialized.
     *
     * @param   dynamicStateElement
     * @return  Map<String effectiveId, Element serializedState>
     */
    public Map<String, Element> getSerializedControlStateMap(Element dynamicStateElement) {
        final Map<String, Element> result = new HashMap<String, Element>();
        final Element controlsElement = dynamicStateElement.element("controls");
        if (controlsElement != null) {
            for (Element currentControlElement : Dom4jUtils.elements(controlsElement, "control")) {
                result.put(currentControlElement.attributeValue("effective-id"), currentControlElement);
            }
        }
        return result;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    // TODO: Many callers of this won't get the proper context stack when dealing with components
    public XFormsContextStack getContextStack() {
        return rootContainer.getContextStack();
    }

    /**
     * Create a new repeat iteration for insertion into the current tree of controls.
     *
     * WARNING: The binding context must be set to the current iteration before calling.
     *
     * @param propertyContext   current context
     * @param bindingContext    binding context set to the context of the new iteration
     * @param repeatControl     repeat control
     * @param iterationIndex    new iteration index (1..repeat size + 1)
     * @return                  newly created repeat iteration control
     */
    public XFormsRepeatIterationControl createRepeatIterationTree(final PropertyContext propertyContext, XFormsContextStack.BindingContext bindingContext,
                                                                  XFormsRepeatControl repeatControl, int iterationIndex) {

        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences())
            throw new OXFException("Cannot call insertRepeatIteration() when initialControlTree == currentControlTree");

        final XFormsRepeatIterationControl repeatIterationControl;
        indentedLogger.startHandleOperation("controls", "adding iteration");
        {
            repeatIterationControl = currentControlTree.createRepeatIterationTree(propertyContext, containingDocument, bindingContext, repeatControl, iterationIndex);
        }
        indentedLogger.endHandleOperation();

        return repeatIterationControl;
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
    public void cloneInitialStateIfNeeded(PropertyContext propertyContext) {
        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences()) {
            indentedLogger.startHandleOperation("controls", "cloning");
            {
                // NOTE: We clone "back", that is the new tree is used as the "initial" tree. This is done so that
                // if we started working with controls in the initial tree, we can keep using those references safely.
                initialControlTree = (ControlTree) currentControlTree.getBackCopy(propertyContext);
            }
            indentedLogger.endHandleOperation();
        }
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public Object getObjectByEffectiveId(String effectiveId) {
        return currentControlTree.getControl(effectiveId);
    }

    /**
     * Resolve an object. This optionally depends on a source control, and involves resolving whether the source is within a
     * repeat or a component.
     *
     * @param sourceControlEffectiveId  effective id of the source control
     * @param targetStaticId            static id of the target
     * @param contextItem               context item, or null (used for bind resolution only)
     * @return                          object, or null if not found
     */
    public Object resolveObjectById(String sourceControlEffectiveId, String targetStaticId, Item contextItem) {
        final String effectiveControlId = getCurrentControlTree().findEffectiveControlId(sourceControlEffectiveId, targetStaticId);
        return (effectiveControlId != null) ? getObjectByEffectiveId(effectiveControlId) : null;
    }

    /**
     * Visit all the controls elements by following repeats to allow creating the actual control.
     */
    public static void visitControlElementsHandleRepeat(PropertyContext propertyContext, XFormsContainingDocument containingDocument,
                                                        XBLContainer rootXBLContainer, ControlElementVisitorListener controlElementVisitorListener) {
        rootXBLContainer.getContextStack().resetBindingContext(propertyContext);
        final boolean isOptimizeRelevance = XFormsProperties.isOptimizeRelevance(containingDocument);
        final Element rootElement = containingDocument.getStaticState().getControlsDocument().getRootElement();
    
        visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
                containingDocument.getStaticState(), rootXBLContainer, null, rootElement, "", "");
    }

    public static void visitControlElementsHandleRepeat(PropertyContext propertyContext, XFormsRepeatControl enclosingRepeatControl,
                                                        int iterationIndex, ControlElementVisitorListener controlElementVisitorListener) {

        final XFormsContainingDocument containingDocument = enclosingRepeatControl.getXBLContainer().getContainingDocument();
        final boolean isOptimizeRelevance = XFormsProperties.isOptimizeRelevance(containingDocument);

        // Set binding context on the particular repeat iteration
        final XFormsContextStack contextStack = enclosingRepeatControl.getXBLContainer().getContextStack();
        contextStack.setBinding(enclosingRepeatControl);
        contextStack.pushIteration(iterationIndex);

        // Start visiting children of the xforms:repeat element
        final Element repeatControlElement = containingDocument.getStaticState().getControlElement(enclosingRepeatControl.getPrefixedId());
        XFormsControls.visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
                containingDocument.getStaticState(), enclosingRepeatControl.getXBLContainer(), enclosingRepeatControl, repeatControlElement,
                XFormsUtils.getEffectiveIdPrefix(enclosingRepeatControl.getEffectiveId()),
                XFormsUtils.getEffectiveIdSuffix(XFormsUtils.getIterationEffectiveId(enclosingRepeatControl.getEffectiveId(), iterationIndex)));
    }

    public static void visitControlElementsHandleRepeat(PropertyContext propertyContext, XFormsContainerControl containerControl,
                                                        ControlElementVisitorListener controlElementVisitorListener) {

        final XFormsControl control = (XFormsControl) containerControl;
        final XFormsContainingDocument containingDocument = control.getXBLContainer().getContainingDocument();
        final boolean isOptimizeRelevance = XFormsProperties.isOptimizeRelevance(containingDocument);

        // Set binding context on the container control
        final XFormsContextStack contextStack = control.getXBLContainer().getContextStack();
        contextStack.setBinding(control);

        final XBLContainer currentXBLContainer = control.getXBLContainer();

        // Start visit container control
        controlElementVisitorListener.startVisitControl(currentXBLContainer, control.getControlElement(), control.getEffectiveId());
        {
            // Start visiting children of the xforms:repeat element
            XFormsControls.visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
                    containingDocument.getStaticState(), currentXBLContainer, control, control.getControlElement(),
                    XFormsUtils.getEffectiveIdPrefix(control.getEffectiveId()),
                    XFormsUtils.getEffectiveIdSuffix(control.getEffectiveId()));
        }
        // End visit container control
        controlElementVisitorListener.endVisitControl(control.getControlElement(), control.getEffectiveId());
    }

    private static void visitControlElementsHandleRepeat(PropertyContext propertyContext, ControlElementVisitorListener controlElementVisitorListener,
                                                    boolean isOptimizeRelevance, XFormsStaticState staticState, XBLContainer currentXBLContainer,
                                                    XFormsControl currentControlsContainer, Element containerElement, String idPrefix, String idPostfix) {

        int variablesCount = 0;
        final XFormsContextStack currentContextStack = currentXBLContainer.getContextStack();
        for (final Element currentControlElement: Dom4jUtils.elements(containerElement)) {
            final String currentControlURI = currentControlElement.getNamespaceURI();
            final String currentControlName = currentControlElement.getName();

            final String controlStaticId = currentControlElement.attributeValue("id");
            final String controlPrefixedId = idPrefix + controlStaticId;
            final String controlEffectiveId = controlPrefixedId + (idPostfix.equals("") ? "" : XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + idPostfix);

            final XBLBindings.Scope newScope = staticState.getXBLBindings().getResolutionScopeByPrefixedId(controlPrefixedId);

            if (currentControlName.equals("repeat")) {
                // Handle xforms:repeat
                currentContextStack.pushBinding(propertyContext, currentControlElement, controlEffectiveId, newScope);

                // Visit xforms:repeat element
                final XFormsControl newRepeatControl = controlElementVisitorListener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);

                // Iterate over current xforms:repeat nodeset
                final List<Item> currentNodeSet = currentContextStack.getCurrentNodeset();
                if (currentNodeSet != null) {
                    for (int iterationIndex = 1; iterationIndex <= currentNodeSet.size(); iterationIndex++) {
                        // Push "artificial" binding with just current node in nodeset
                        currentContextStack.pushIteration(iterationIndex);
                        {
                            // Handle children of xforms:repeat
                            // TODO: handle isOptimizeRelevance()

                            // Compute repeat iteration id
                            final String iterationEffectiveId = XFormsUtils.getIterationEffectiveId(controlEffectiveId, iterationIndex);

                            final boolean recurse = controlElementVisitorListener.startRepeatIteration(currentXBLContainer, iterationIndex, iterationEffectiveId);
                            if (recurse) {
                                // When updating controls, the callee has the option of disabling recursion into an
                                // iteration. This is used for handling new iterations.
                                final String newIdPostfix = idPostfix.equals("") ? Integer.toString(iterationIndex) : (idPostfix + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + iterationIndex);
                                visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
                                        staticState, currentXBLContainer, newRepeatControl, currentControlElement, idPrefix, newIdPostfix);
                            }
                            controlElementVisitorListener.endRepeatIteration(iterationIndex);
                        }
                        currentContextStack.popBinding();
                    }
                }

                controlElementVisitorListener.endVisitControl(currentControlElement, controlEffectiveId);
                currentContextStack.popBinding();
            } else if (XFormsControlFactory.isContainerControl(currentControlURI, currentControlName)) {
                // Handle XForms grouping controls
                currentContextStack.pushBinding(propertyContext, currentControlElement, controlEffectiveId, newScope);
                final XFormsControl newContainerControl = controlElementVisitorListener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);
                final XFormsContextStack.BindingContext currentBindingContext = currentContextStack.getCurrentBindingContext();
                {
                    // Recurse into grouping control and components if we don't optimize relevance, OR if we do
                    // optimize and we are not bound to a node OR we are bound to a relevant node

                    // NOTE: Simply excluding non-selected cases with the expression below doesn't work. So for
                    // now, we don't consider hidden cases as non-relevant. In the future, we might want to improve
                    // this.
                    // && (!controlName.equals("case") || isCaseSelectedByControlElement(controlElement, effectiveControlId, idPostfix))

//                    if (ControlTree.TESTING_DIALOG_OPTIMIZATION && newContainerControl instanceof XXFormsDialogControl) {// TODO: FOR TESTING DIALOG OPTIMIZATION
//                        // Visit dialog children only if dialog is visible
//                        if (((XXFormsDialogControl) newContainerControl).isVisible()) {
//                            visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
//                                    staticState, currentXBLContainer, newContainerControl, currentControlElement, idPrefix, idPostfix);
//                        }
//                    } else {
                        // TODO: relevance logic here is wrong, see correct logic in XFormsSingleNodeControl
                        if (!isOptimizeRelevance
                                || (!currentBindingContext.isNewBind()
                                || (currentBindingContext.getSingleItem() != null && InstanceData.getInheritedRelevant((NodeInfo) currentBindingContext.getSingleItem())))) {

                            visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
                                    staticState, currentXBLContainer, newContainerControl, currentControlElement, idPrefix, idPostfix);
                        }
//                    }
                }
                controlElementVisitorListener.endVisitControl(currentControlElement, controlEffectiveId);
                currentContextStack.popBinding();
            } else if (XFormsControlFactory.isCoreControl(currentControlURI, currentControlName)) {
                // Handle leaf control
                currentContextStack.pushBinding(propertyContext, currentControlElement, controlEffectiveId, newScope);
                controlElementVisitorListener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);
                controlElementVisitorListener.endVisitControl(currentControlElement, controlEffectiveId);
                currentContextStack.popBinding();
            } else if (currentControlName.equals(XFormsConstants.XXFORMS_VARIABLE_NAME)) {
                // Handle xxforms:variable specially: as of 2009-12, variables are not considered controls!

                // Create variable object
                final Variable variable = new Variable(currentXBLContainer, currentContextStack, currentControlElement);

                // Push the variable on the context stack. Note that we do as if each variable was a "parent" of the following controls and variables.
                // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation in the future.

                final String sourceEffectiveId;
                if (currentControlsContainer == null) {
                    // We are a top-level variable and we can't figure out a source effective id
                    // NOTE: This is partly due to the fact that variables are not full controls at this point. So resolution
                    // by using the id of a variable won't work. This means that, at the top-level, resolution of e.g.
                    // index(), xxf:index(), xxf:case(), xxf:binding(), will not work for a top-level variable.

                    // We need to return a valid effective id, so we return the top-level model in scope
                    sourceEffectiveId = currentContextStack.getCurrentModel().getEffectiveId();
                } else {
                    // There is a container control, so use its effective id
                    sourceEffectiveId = currentControlsContainer.getEffectiveId();
                }

                currentContextStack.pushVariable(currentControlElement, variable.getVariableName(), variable.getVariableValue(propertyContext, sourceEffectiveId, true), newScope);

                variablesCount++;
            } else if (staticState.getXBLBindings().isComponent(currentControlElement.getQName())) {
                // Handle components

                // NOTE: don't push the binding here, this is handled if necessary by the component implementation
                final XFormsComponentControl newControl = (XFormsComponentControl) controlElementVisitorListener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);

                // Recurse into the shadow component tree
                final Element shadowTreeDocumentElement = staticState.getXBLBindings().getCompactShadowTree(idPrefix + controlStaticId);
                visitControlElementsHandleRepeat(propertyContext, controlElementVisitorListener, isOptimizeRelevance,
                        staticState, newControl.getNestedContainer(), newControl, shadowTreeDocumentElement, newControl.getNestedContainer().getFullPrefix(), idPostfix);

                controlElementVisitorListener.endVisitControl(currentControlElement, controlEffectiveId);

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
        XFormsControl startVisitControl(XBLContainer container, Element controlElement, String effectiveControlId);
        void endVisitControl(Element controlElement, String effectiveControlId);
        boolean startRepeatIteration(XBLContainer container, int iteration, String effectiveIterationId);
        void endRepeatIteration(int iteration);
    }

    public static interface XFormsControlVisitorListener {
        boolean startVisitControl(XFormsControl control);
        boolean endVisitControl(XFormsControl control);
    }

    public static class XFormsControlVisitorAdapter implements XFormsControlVisitorListener {
        public boolean startVisitControl(XFormsControl control) { return true; }
        public boolean endVisitControl(XFormsControl control) { return true; }
    }

    /**
     * Get the items for a given control id. This is not an effective id, but an original control id.
     *
     * @param controlId     original control id
     * @return              itemset
     */
    public Itemset getConstantItems(String controlId) {
        if (constantItems == null)
            return null;
        else
            return constantItems.get(controlId);
    }

    /**
     * Set the items for a given control id. This is not an effective id, but an original control id.
     *
     * @param controlId     static control id
     * @param itemset       itemset
     */
    public void setConstantItems(String controlId, Itemset itemset) {
        if (constantItems == null)
            constantItems = new HashMap<String, Itemset>();
        constantItems.put(controlId, itemset);
    }
    
    public void doRefresh(final PropertyContext propertyContext, XBLContainer container) {

        // This method implements the new refresh event algorithm:
        // http://wiki.orbeon.com/forms/doc/contributor-guide/xforms-refresh-events

        // Don't do anything if there are no children controls
        if (getCurrentControlTree().getChildren() == null) {
            indentedLogger.logDebug("model", "not performing refresh because no controls are available");
            // Don't forget to clear the flag or we risk infinite recursion
            refreshDone();
        } else {
            indentedLogger.startHandleOperation("model", "performing refresh", "container id", container.getEffectiveId());
            {
                // Update control bindings
                updateBindingsIfNeeded(propertyContext);
                // Update control values
                evaluateValuesIfNeeded(propertyContext);

                if (currentControlTree.isAllowSendingRefreshEvents()) {
                    // There are potentially event handlers for UI events, so do the whole processing

                    // Gather controls to which to dispatch refresh events
                    final List<String> controlsEffectiveIds = gatherControlsForRefresh();

                    // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always have an immediate
                    // effect, and clear the corresponding flag."
                    refreshDone();

                    // Dispatch events
                    dispatchRefreshEvents(propertyContext, controlsEffectiveIds);

                } else {
                    // No UI events to send because there is no event handlers for any of them
                    indentedLogger.logDebug("model", "refresh skipping sending of UI events because no listener was found", "container id", container.getEffectiveId());

                    // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always have an immediate
                    // effect, and clear the corresponding flag."
                    refreshDone();
                }
            }
            indentedLogger.endHandleOperation();
        }
    }

    /**
     * Rebuild the controls tree bindings if needed.
     *
     * @param propertyContext   current context
     * @return                  true iif bindings were updated
     */
    private boolean updateBindingsIfNeeded(final PropertyContext propertyContext) {

        if (!initialized) {
            return false;
        } else {
            // This is the regular case

            // Don't do anything if bindings are clean
            if (!currentControlTree.isBindingsDirty())
                return false;

            // Clone if needed
            cloneInitialStateIfNeeded(propertyContext);

            indentedLogger.startHandleOperation("controls", "updating bindings");
            final ControlTree.UpdateBindingsListener listener = new ControlTree.UpdateBindingsListener(propertyContext, currentControlTree.getEffectiveIdsToControls());
            {
                // Visit all controls and update their bindings
                visitControlElementsHandleRepeat(propertyContext, containingDocument, rootContainer, listener);
            }
            indentedLogger.endHandleOperation(
                    "controls updated", Integer.toString(listener.getUpdateCount()),
                    "repeat iterations", Integer.toString(listener.getIterationCount())
            );

            // Controls are clean
            initialControlTree.markBindingsClean();
            currentControlTree.markBindingsClean();

            return true;
        }
    }

    /**
     * Evaluate all the controls if needed. Should be used before output initial XHTML and before computing differences
     * in XFormsServer.
     *
     * @param propertyContext   current context
     */
    private void evaluateValuesIfNeeded(PropertyContext propertyContext) {

        indentedLogger.startHandleOperation("controls", "evaluating");
        {
            final Map<String, XFormsControl> effectiveIdsToControls = getCurrentControlTree().getEffectiveIdsToControls();
            // Evaluate all controls
            if (effectiveIdsToControls != null) {
                for (final Map.Entry<String, XFormsControl> currentEntry: effectiveIdsToControls.entrySet()) {
                    final XFormsControl currentControl = currentEntry.getValue();
                    currentControl.evaluateIfNeeded(propertyContext, true);
                }
            }
        }
        indentedLogger.endHandleOperation();
    }

    private List<String> gatherControlsForRefresh() {

        final List<String> eventsToDispatch = new ArrayList<String>();

        visitAllControls(new XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                if (XFormsControl.supportsRefreshEvents(control)) {// test here to make smaller list
                    eventsToDispatch.add(control.getEffectiveId());
                }
                return true;
            }
        });

        return eventsToDispatch;
    }

    public void dispatchRefreshEvents(PropertyContext propertyContext, List<String> controlsEffectiveIds) {

        for (final String controlEffectiveId: controlsEffectiveIds) {
            final XFormsControl control = currentControlTree.getControl(controlEffectiveId);

            if (XFormsControl.supportsRefreshEvents(control)) {

                final boolean oldRelevantState = control.wasRelevant();
                final boolean newRelevantState = control.isRelevant();

                if (newRelevantState && !oldRelevantState) {
                    // Control has become relevant
                    currentControlTree.dispatchCreationEvents(propertyContext, control);
                } else if (!newRelevantState && oldRelevantState) {
                    // Control has become non-relevant
                    currentControlTree.dispatchDestructionEvents(propertyContext, control);
                } else if (newRelevantState) {
                    // Control was and is relevant
                    currentControlTree.dispatchChangeEvents(propertyContext, control);
                }
            }
        }
    }

    /**
     * Do a refresh of a subtree of controls starting at the given container control.
     *
     * @param propertyContext   current context
     * @param containerControl  container control
     */
    public void doPartialRefresh(final PropertyContext propertyContext, XFormsContainerControl containerControl) {

        // Update bindings starting at the container control
        updateSubtreeBindings(propertyContext, containerControl);

        // Evaluate the controls
        ControlTree.visitControls(containerControl, true, new XFormsControlVisitorAdapter() {
            @Override
            public boolean startVisitControl(XFormsControl control) {
                control.evaluateIfNeeded(propertyContext, true);
                return true;
            }
        });

        if (currentControlTree.isAllowSendingRefreshEvents()) {
            // There are potentially event handlers for UI events, so do the whole processing

            // Gather controls to which to dispatch refresh events
            final List<String> eventsToDispatch = gatherControlsForRefresh(containerControl);

            // Dispatch events
            dispatchRefreshEvents(propertyContext, eventsToDispatch);
        }
    }

    /**
     * Update the bindings of a container control and its descendants.
     *
     * @param propertyContext   current context
     * @param containerControl  container control
     */
    private void updateSubtreeBindings(PropertyContext propertyContext, XFormsContainerControl containerControl) {
        // Clone if needed
        cloneInitialStateIfNeeded(propertyContext);

        indentedLogger.startHandleOperation("controls", "updating bindings");
        final ControlTree.UpdateBindingsListener listener = new ControlTree.UpdateBindingsListener(propertyContext, currentControlTree.getEffectiveIdsToControls());
        {
            // Visit all controls and update their bindings
            visitControlElementsHandleRepeat(propertyContext, containerControl, listener);
        }
        indentedLogger.endHandleOperation(
                "controls updated", Integer.toString(listener.getUpdateCount()),
                "repeat iterations", Integer.toString(listener.getIterationCount())
        );
    }

    private List<String> gatherControlsForRefresh(XFormsContainerControl containerControl) {

        final List<String> eventsToDispatch = new ArrayList<String>();

        ControlTree.visitControls(containerControl, true, new XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                if (XFormsControl.supportsRefreshEvents(control)) {// test here to make smaller list
                    eventsToDispatch.add(control.getEffectiveId());
                }
                return true;
            }
        });

        return eventsToDispatch;
    }
}
