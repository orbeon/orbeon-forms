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
import org.orbeon.oxf.xforms.analysis.VariableAnalysis;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDynamicControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsVariableControl;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.xbl.XBLBindingsBase;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;

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

    // Crude flag to indicate that something might have changed since the last request. This caches simples cases where
    // an incoming change on the document does not cause any change to the data or controls. In that case, the control
    // trees need not be compared
    private boolean dirtySinceLastRequest;

    // Whether we currently require a UI refresh
    private boolean requireRefresh;

    private XFormsContainingDocument containingDocument;
    private XBLContainer rootContainer;
    
    private Map<String, Itemset> constantItems;

    private final XPathDependencies xpathDependencies;

    public XFormsControls(XFormsContainingDocument containingDocument) {

        this.indentedLogger = containingDocument.getIndentedLogger(LOGGING_CATEGORY);

        this.containingDocument = containingDocument;
        this.rootContainer = this.containingDocument;

        this.xpathDependencies = containingDocument.getXPathDependencies();

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

    private void markCleanSinceLastRequest() {
        dirtySinceLastRequest = false;
        currentControlTree.markBindingsClean();
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
        xpathDependencies.refreshDone();
    }

    public void refreshIfNeeded(XBLContainer container) {
        if (requireRefresh) {
            // Refresh is global for now
            // Will be cleared by doRefresh()
            doRefresh(container);
        }
    }

    /**
     * Initialize the controls. This is called upon initial creation of the engine.
     *
     */
    public void initialize() {

        assert !initialized;

        createControlTree();
    }
    /**
     * Restore the controls. This is called when the document is restored.
     *
     */
    public void restoreControls() {

        assert !initialized;

        createControlTree();
    }

    private void createControlTree() {
        if (containingDocument.getStaticState().topLevelPart().hasControls()) {

            // Create new controls tree
            // NOTE: We set this first so that the tree is made available during construction to XPath functions like index() or xxforms:case()
            currentControlTree = initialControlTree = new ControlTree(containingDocument, indentedLogger);

            // Set this here so that while initialize() runs below, refresh events will find the flag set
            initialized = true;

            // Initialize new control tree
            currentControlTree.initialize(containingDocument, rootContainer);
        } else {
            // Consider initialized
            initialized = true;
        }
    }

    /**
     * Adjust the controls after sending a response.
     *
     * This makes sure that we don't keep duplicate control trees.
     */
    public void afterUpdateResponse() {

        assert initialized;

        if (containingDocument.getStaticState().topLevelPart().hasControls()) {

            // Keep only one control tree
            initialControlTree = currentControlTree;

            // We are now clean
            markCleanSinceLastRequest();

            // Need to make sure that current == initial within controls
            visitAllControls(new XFormsControls.XFormsControlVisitorAdapter() {
                public boolean startVisitControl(XFormsControl control) {
                    control.resetLocal();
                    return true;
                }
            });
        }
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
                if (control.isRelevant()) { // don't serialize anything for non-relevant controls
                    final Map nameValues = control.serializeLocal();
                    if (nameValues != null) {
                        final Element controlElement = controlsElement.addElement("control");
                        controlElement.addAttribute("effective-id", control.getEffectiveId());
                        for (Object o: nameValues.entrySet()) {
                            final Map.Entry currentEntry = (Map.Entry) o;
                            controlElement.addAttribute((String) currentEntry.getKey(), (String) currentEntry.getValue());
                        }
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
     * @param bindingContext    binding context set to the context of the new iteration
     * @param repeatControl     repeat control
     * @param iterationIndex    new iteration index (1..repeat size + 1)
     * @return                  newly created repeat iteration control
     */
    public XFormsRepeatIterationControl createRepeatIterationTree(XFormsContextStack.BindingContext bindingContext,
                                                                  XFormsRepeatControl repeatControl, int iterationIndex) {

        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences())
            throw new OXFException("Cannot call insertRepeatIteration() when initialControlTree == currentControlTree");

        final XFormsRepeatIterationControl repeatIterationControl;
        indentedLogger.startHandleOperation("controls", "adding iteration");
        {
            repeatIterationControl = currentControlTree.createRepeatIterationTree(containingDocument,
                    bindingContext, repeatControl, iterationIndex);
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
    public void cloneInitialStateIfNeeded() {
        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences()) {
            indentedLogger.startHandleOperation("controls", "cloning");
            {
                // NOTE: We clone "back", that is the new tree is used as the "initial" tree. This is done so that
                // if we started working with controls in the initial tree, we can keep using those references safely.
                initialControlTree = (ControlTree) currentControlTree.getBackCopy();
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
        final String effectiveControlId = getCurrentControlTree().findEffectiveControlId(containingDocument.getStaticOps(), sourceControlEffectiveId, targetStaticId);
        return (effectiveControlId != null) ? getObjectByEffectiveId(effectiveControlId) : null;
    }

    /**
     * Visit all the controls elements by following repeats to allow creating the actual control.
     */
    public void visitControlElementsHandleRepeat(XFormsContainingDocument containingDocument,
                                                 XBLContainer rootXBLContainer, ControlElementVisitorListener listener) {
        rootXBLContainer.getContextStack().resetBindingContext();
        final List<Element> topLevelControlElements = containingDocument.getStaticState().topLevelPart().getTopLevelControlElements();

        final XFormsStaticState staticState = containingDocument.getStaticState();

        for (final Element element : topLevelControlElements)
            visitControlElementsHandleRepeat(listener, staticState, rootXBLContainer, element, "", "");
    }

    // Used to create new repeat iterations
    public void visitControlElementsHandleRepeat(XFormsRepeatControl enclosingRepeatControl,
                                                 int iterationIndex, ControlElementVisitorListener listener) {

        final XFormsContainingDocument containingDocument = enclosingRepeatControl.getXBLContainer().getContainingDocument();

        // Set binding context on the particular repeat iteration
        final XFormsContextStack contextStack = enclosingRepeatControl.getXBLContainer().getContextStack();
        contextStack.setBinding(enclosingRepeatControl);
        contextStack.pushIteration(iterationIndex);

        // Start visiting children of the xforms:repeat element
        final Element repeatControlElement = enclosingRepeatControl.getXBLContainer().getPartAnalysis().getControlElement(enclosingRepeatControl.getPrefixedId());
        visitControlElementsHandleRepeat(listener,
                containingDocument.getStaticState(), enclosingRepeatControl.getXBLContainer(), repeatControlElement,
                XFormsUtils.getEffectiveIdPrefix(enclosingRepeatControl.getEffectiveId()),
                XFormsUtils.getEffectiveIdSuffix(XFormsUtils.getIterationEffectiveId(enclosingRepeatControl.getEffectiveId(), iterationIndex)));
    }

    // Used by xxf:dynamic
    public void visitControlElementsHandleRepeat(XXFormsDynamicControl dynamicControl, ControlElementVisitorListener listener) {

        final XFormsContainingDocument containingDocument = dynamicControl.getXBLContainer().getContainingDocument();

        // Set binding context on the container control
        final XBLContainer currentXBLContainer = dynamicControl.getNestedContainer();
        final XFormsContextStack contextStack = currentXBLContainer.getContextStack();
        contextStack.resetBindingContext();

        final XFormsStaticState staticState = containingDocument.getStaticState();
        final String idPrefix = currentXBLContainer.getFullPrefix();
        final String idPostfix = XFormsUtils.getEffectiveIdSuffix(dynamicControl.getEffectiveId());

        // Start visiting children of the xforms:repeat element
        for (final Element element : dynamicControl.getNestedPartAnalysis().getTopLevelControlElements())
            visitControlElementsHandleRepeat(listener, staticState, currentXBLContainer, element, idPrefix, idPostfix);
    }

    // Used for partial refresh (isXForms11Switch())
    public void visitControlElementsHandleRepeat(XFormsContainerControl containerControl, boolean includeCurrent, ControlElementVisitorListener listener) {

        final XFormsControl control = (XFormsControl) containerControl;
        final XFormsContainingDocument containingDocument = control.getXBLContainer().getContainingDocument();

        // Set binding context on the container control
        final XBLContainer currentXBLContainer = control.getXBLContainer();
        final XFormsContextStack contextStack = currentXBLContainer.getContextStack();
        contextStack.setBinding(control);

        // Start visit container control
        if (includeCurrent)
            listener.startVisitControl(currentXBLContainer, control.getControlElement(), control.getEffectiveId());

        final String idPrefix = XFormsUtils.getEffectiveIdPrefix(control.getEffectiveId());
        final String idPostfix = XFormsUtils.getEffectiveIdSuffix(control.getEffectiveId());

        // Start visiting children of the xforms:repeat element
        visitControlElementsHandleRepeat(listener, containingDocument.getStaticState(), currentXBLContainer,
                control.getControlElement(), idPrefix, idPostfix);

        // End visit container control
        if (includeCurrent)
            listener.endVisitControl(control.getControlElement(), control.getEffectiveId());
    }

    private void visitControlElementsHandleRepeat(ControlElementVisitorListener listener,
                                                  XFormsStaticState staticState, XBLContainer currentXBLContainer,
                                                  Element containerElement, String idPrefix, String idPostfix) {

        int variablesCount = 0;
        final XFormsContextStack currentContextStack = currentXBLContainer.getContextStack();
        for (final Element currentControlElement: Dom4jUtils.elements(containerElement)) {
            final String currentControlURI = currentControlElement.getNamespaceURI();
            final String currentControlName = currentControlElement.getName();

            final String controlStaticId = currentControlElement.attributeValue(XFormsConstants.ID_QNAME);
            final String controlPrefixedId = idPrefix + controlStaticId;
            final String controlEffectiveId = controlPrefixedId + (idPostfix.equals("") ? "" : XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + idPostfix);

            final XBLBindingsBase.Scope newScope = currentXBLContainer.getPartAnalysis().getResolutionScopeByPrefixedId(controlPrefixedId);

            if (currentControlName.equals("dynamic")) {
                // xxf:dynamic
                listener.pushBinding(currentContextStack, currentControlElement, controlPrefixedId, controlEffectiveId, newScope);

                final XXFormsDynamicControl dynamicControl = (XXFormsDynamicControl) listener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);

                // Recurse into the shadow component tree only upon binding update, as creation is handled by the control upon startVisitControl()7
                if (listener instanceof ControlTree.UpdateBindingsListener)
                    visitControlElementsHandleRepeat(dynamicControl, listener);

                listener.endVisitControl(currentControlElement, controlEffectiveId);

                currentContextStack.popBinding();
            } else  if (currentControlName.equals("repeat")) {
                // Handle xforms:repeat
                listener.pushBinding(currentContextStack, currentControlElement, controlPrefixedId, controlEffectiveId, newScope);

                // Visit xforms:repeat element
                listener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);

                // Iterate over current xforms:repeat nodeset
                final List<Item> currentNodeSet = currentContextStack.getCurrentNodeset();
                if (currentNodeSet != null) {
                    final int nodesetSize = currentNodeSet.size();
                    for (int iterationIndex = 1; iterationIndex <= nodesetSize; iterationIndex++) {
                        // Push "artificial" binding with just current node in nodeset
                        currentContextStack.pushIteration(iterationIndex);
                        {
                            // Handle children of xforms:repeat
                            // TODO: handle isOptimizeRelevance()

                            // Compute repeat iteration id
                            final String iterationEffectiveId = XFormsUtils.getIterationEffectiveId(controlEffectiveId, iterationIndex);

                            final boolean recurse = listener.startRepeatIteration(currentXBLContainer, iterationIndex, iterationEffectiveId);
                            if (recurse) {
                                // When updating controls, the callee has the option of disabling recursion into an
                                // iteration. This is used for handling new iterations.
                                final String newIdPostfix = idPostfix.equals("") ? Integer.toString(iterationIndex) : (idPostfix + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2 + iterationIndex);
                                visitControlElementsHandleRepeat(listener,
                                        staticState, currentXBLContainer, currentControlElement, idPrefix, newIdPostfix);
                            }
                            listener.endRepeatIteration(iterationIndex);
                        }
                        currentContextStack.popBinding();
                    }
                }

                listener.endVisitControl(currentControlElement, controlEffectiveId);
                currentContextStack.popBinding();
            } else if (XFormsControlFactory.isContainerControl(currentControlURI, currentControlName)) {
                // Handle XForms grouping controls
                listener.pushBinding(currentContextStack, currentControlElement, controlPrefixedId, controlEffectiveId, newScope);
                listener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);
                {
                    // Recurse into grouping control
                    visitControlElementsHandleRepeat(listener,
                            staticState, currentXBLContainer, currentControlElement, idPrefix, idPostfix);
                }
                listener.endVisitControl(currentControlElement, controlEffectiveId);
                currentContextStack.popBinding();
            } else if (XFormsControlFactory.isCoreControl(currentControlURI, currentControlName)) {
                // Handle leaf control

                listener.pushBinding(currentContextStack, currentControlElement, controlPrefixedId, controlEffectiveId, newScope);
                listener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);
                listener.endVisitControl(currentControlElement, controlEffectiveId);
                currentContextStack.popBinding();
            } else if (VariableAnalysis.isVariableElement(currentControlElement)) {
                // Handle xxforms:variable as a leaf control

                listener.pushBinding(currentContextStack, currentControlElement, controlPrefixedId, controlEffectiveId, newScope);
                final XXFormsVariableControl variable = (XXFormsVariableControl) listener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);
                listener.endVisitControl(currentControlElement, controlEffectiveId);

                // What startVisitControl() above does:
                //
                // o get existing variable control
                // o call markDirty() on it
                // o this will only reset the value of the variables if dependencies want that
                // o evaluate() below will only cause the variable to be re-evaluated if it was marked as dirty above

                // Evaluate variable right away in case it is used by further bindings
                variable.evaluate();

                currentContextStack.popBinding();

                // Push variable value on the stack
                currentContextStack.pushVariable(currentControlElement, variable.getVariableName(), variable.getValue(), newScope);
                variablesCount++;
            } else if (containingDocument.getStaticOps().isComponent(currentControlElement.getQName())) {
                // Handle components

                // NOTE: don't push the binding here, this is handled if necessary by the component implementation
                final XFormsComponentControl newControl = (XFormsComponentControl) listener.startVisitControl(currentXBLContainer, currentControlElement, controlEffectiveId);

                // Recurse into the shadow component tree
                final Element shadowTreeDocumentElement = currentXBLContainer.getPartAnalysis().getBinding(idPrefix + controlStaticId).compactShadowTree.getRootElement();
                visitControlElementsHandleRepeat(listener,
                        staticState, newControl.getNestedContainer(), shadowTreeDocumentElement, newControl.getNestedContainer().getFullPrefix(), idPostfix);

                listener.endVisitControl(currentControlElement, controlEffectiveId);

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
        boolean pushBinding(XFormsContextStack currentContextStack, Element currentControlElement,
                            String controlPrefixedId, String controlEffectiveId, XBLBindingsBase.Scope newScope);
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
    
    public void doRefresh(XBLContainer container) {

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
                // Notify dependencies
                xpathDependencies.refreshStart();

                // Update control bindings
                updateControlBindings();
                // Update control values
                evaluateControlValues();

                if (currentControlTree.isAllowSendingRefreshEvents()) {
                    // There are potentially event handlers for UI events, so do the whole processing

                    // Gather controls to which to dispatch refresh events
                    final List<String> controlsEffectiveIds = gatherControlsForRefresh();

                    // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always have an immediate
                    // effect, and clear the corresponding flag."
                    refreshDone();

                    // Dispatch events
                    currentControlTree.dispatchRefreshEvents(controlsEffectiveIds);

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
     * Update all the control bindings.
     *
     * @return                  true iif bindings were updated
     */
    private boolean updateControlBindings() {

        if (!initialized) {
            return false;
        } else {
            // This is the regular case

            // Don't do anything if bindings are clean
            if (!currentControlTree.isBindingsDirty())
                return false;

            // Clone if needed
            cloneInitialStateIfNeeded();

            indentedLogger.startHandleOperation("controls", "updating bindings");
            final ControlTree.UpdateBindingsListener listener = new ControlTree.UpdateBindingsListener(containingDocument, currentControlTree.getEffectiveIdsToControls());
            {
                // Visit all controls and update their bindings
                visitControlElementsHandleRepeat(containingDocument, rootContainer, listener);
            }
            indentedLogger.endHandleOperation(
                    "controls visited", Integer.toString(listener.getVisitedCount()),
                    "repeat iterations visited", Integer.toString(listener.getIterationCount()),
                    "bindings evaluated", Integer.toString(listener.getUpdateCount()),
                    "bindings optimized", Integer.toString(listener.getOptimizedCount())
            );

            // Controls are clean
            initialControlTree.markBindingsClean();
            currentControlTree.markBindingsClean();

            return true;
        }
    }

    /**
     * Evaluate all the control values.
     *
     */
    private void evaluateControlValues() {

        indentedLogger.startHandleOperation("controls", "evaluating");
        {
            final Map<String, XFormsControl> effectiveIdsToControls = getCurrentControlTree().getEffectiveIdsToControls();
            // Evaluate all controls if needed
            if (effectiveIdsToControls != null) {
                for (final Map.Entry<String, XFormsControl> currentEntry: effectiveIdsToControls.entrySet()) {
                    final XFormsControl currentControl = currentEntry.getValue();
                    currentControl.evaluate();
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

    /**
     * Do a refresh of a subtree of controls starting at the given container control.
     *
     * @param containerControl  container control
     */
    public void doPartialRefresh(XFormsContainerControl containerControl) {

        // Update bindings starting at the container control
        updateSubtreeBindings(containerControl);

        // Evaluate the controls
        ControlTree.visitControls(containerControl, true, new XFormsControlVisitorAdapter() {
            @Override
            public boolean startVisitControl(XFormsControl control) {
                control.evaluate();
                return true;
            }
        });

        if (currentControlTree.isAllowSendingRefreshEvents()) {
            // There are potentially event handlers for UI events, so do the whole processing

            // Gather controls to which to dispatch refresh events
            final List<String> eventsToDispatch = gatherControlsForRefresh(containerControl);

            // Dispatch events
            currentControlTree.dispatchRefreshEvents(eventsToDispatch);
        }
    }

    /**
     * Update the bindings of a container control and its descendants.
     *
     * @param containerControl  container control
     */
    private void updateSubtreeBindings(XFormsContainerControl containerControl) {
        // Clone if needed
        cloneInitialStateIfNeeded();

        indentedLogger.startHandleOperation("controls", "updating bindings");
        final ControlTree.UpdateBindingsListener listener = new ControlTree.UpdateBindingsListener(containingDocument, currentControlTree.getEffectiveIdsToControls());
        {
            // Visit all controls and update their bindings
            visitControlElementsHandleRepeat(containerControl, true, listener);
        }
        indentedLogger.endHandleOperation(
                "controls visited", Integer.toString(listener.getVisitedCount()),
                "repeat iterations visited", Integer.toString(listener.getIterationCount()),
                "bindings evaluated", Integer.toString(listener.getUpdateCount()),
                "bindings optimized", Integer.toString(listener.getOptimizedCount())
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
