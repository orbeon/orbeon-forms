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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.*;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.itemset.Itemset;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
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

    private final XFormsContainingDocument containingDocument;
    
    private Map<String, Itemset> constantItems;

    private final XPathDependencies xpathDependencies;

    public XFormsControls(XFormsContainingDocument containingDocument) {

        this.indentedLogger = containingDocument.getIndentedLogger(LOGGING_CATEGORY);
        this.containingDocument = containingDocument;
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
            currentControlTree.initialize(containingDocument);
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
            Controls.visitAllControls(containingDocument, new Controls.XFormsControlVisitorAdapter() {
                public boolean startVisitControl(XFormsControl control) {
                    control.resetLocal();
                    return true;
                }
            });
        }
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    /**
     * Create a new repeat iteration for insertion into the current tree of controls.
     *
     * @param repeatControl     repeat control
     * @param iterationIndex    new iteration index (1..repeat size + 1)
     * @return                  newly created repeat iteration control
     */
    public XFormsRepeatIterationControl createRepeatIterationTree(XFormsRepeatControl repeatControl, int iterationIndex) {

        if (initialControlTree == currentControlTree && containingDocument.isHandleDifferences())
            throw new OXFException("Cannot call insertRepeatIteration() when initialControlTree == currentControlTree");

        final XFormsRepeatIterationControl repeatIterationControl;
        indentedLogger.startHandleOperation("controls", "adding iteration");
        repeatIterationControl = currentControlTree.createRepeatIterationTree(containingDocument, repeatControl, iterationIndex);
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
    public XFormsObject getObjectByEffectiveId(String effectiveId) {
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
    public XFormsObject resolveObjectById(String sourceControlEffectiveId, String targetStaticId, Item contextItem) {
        final String sourcePrefixedId = XFormsUtils.getPrefixedId(sourceControlEffectiveId);
        final Scope scope = containingDocument.getStaticOps().scopeForPrefixedId(sourcePrefixedId);
        final String targetPrefixedId = scope.prefixedIdForStaticId(targetStaticId);
        
        final String effectiveControlId = Controls.findEffectiveControlId(containingDocument.getStaticOps(), this, sourceControlEffectiveId, targetPrefixedId);
        return (effectiveControlId != null) ? getObjectByEffectiveId(effectiveControlId) : null;
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
        if (getCurrentControlTree().getChildren().isEmpty()) {
            indentedLogger.logDebug("controls", "not performing refresh because no controls are available");
            // Don't forget to clear the flag or we risk infinite recursion
            refreshDone();
        } else {
            indentedLogger.startHandleOperation("controls", "performing refresh", "container id", container.getEffectiveId());
            {
                // Notify dependencies
                xpathDependencies.refreshStart();

                // Focused control before updating bindings
                final XFormsControl focusedBefore = getFocusedControl();

                // Update control bindings
                final Controls.BindingUpdater updater = updateControlBindings();
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
                    indentedLogger.logDebug("controls", "refresh skipping sending of UI events because no listener was found", "container id", container.getEffectiveId());

                    // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always have an immediate
                    // effect, and clear the corresponding flag."
                    refreshDone();
                }

                // Handle focus changes
                Focus.updateFocusWithEvents(focusedBefore, updater.partialFocusRepeat());
            }
            indentedLogger.endHandleOperation();
        }
    }

    /**
     * Update all the control bindings.
     *
     * @return                  true iif bindings were updated
     */
    private Controls.BindingUpdater updateControlBindings() {

        if (!initialized) {
            return null;
        } else {
            // This is the regular case

            // Don't do anything if bindings are clean
            if (!currentControlTree.isBindingsDirty())
                return null;

            // Clone if needed
            cloneInitialStateIfNeeded();

            // Visit all controls and update their bindings
            indentedLogger.startHandleOperation("controls", "updating bindings");
            final Controls.BindingUpdater updater = Controls.updateBindings(containingDocument);
            indentedLogger.endHandleOperation(
                "controls visited", Integer.toString(updater.visitedCount()),
                "bindings evaluated", Integer.toString(updater.updatedCount()),
                "bindings optimized", Integer.toString(updater.optimizedCount())
                );

            // Controls are clean
            initialControlTree.markBindingsClean();
            currentControlTree.markBindingsClean();

            return updater;
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

        Controls.visitAllControls(containingDocument, new Controls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                if (XFormsControl.controlSupportsRefreshEvents(control)) {// test here to make smaller list
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

        // Focused control before updating bindings
        final XFormsControl focusedBefore = getFocusedControl();

        // Update bindings starting at the container control
        final Controls.BindingUpdater updater = updateSubtreeBindings(containerControl);

        // Evaluate the controls
        Controls.visitControls((XFormsControl) containerControl, new Controls.XFormsControlVisitorAdapter() {
            @Override
            public boolean startVisitControl(XFormsControl control) {
                control.evaluate();
                return true;
            }
        }, true, true);

        if (currentControlTree.isAllowSendingRefreshEvents()) {
            // There are potentially event handlers for UI events, so do the whole processing

            // Gather controls to which to dispatch refresh events
            final List<String> eventsToDispatch = gatherControlsForRefresh(containerControl);

            // Dispatch events
            currentControlTree.dispatchRefreshEvents(eventsToDispatch);
        }

        // Handle focus changes
        Focus.updateFocusWithEvents(focusedBefore, updater.partialFocusRepeat());
    }

    /**
     * Update the bindings of a container control and its descendants.
     *
     * @param containerControl  container control
     */
    private Controls.BindingUpdater updateSubtreeBindings(XFormsContainerControl containerControl) {
        // Clone if needed
        cloneInitialStateIfNeeded();

        indentedLogger.startHandleOperation("controls", "updating bindings", "container", ((XFormsControl) containerControl).getEffectiveId());
        final Controls.BindingUpdater updater = Controls.updateBindings(containerControl);
        indentedLogger.endHandleOperation(
            "controls visited", Integer.toString(updater.visitedCount()),
            "bindings evaluated", Integer.toString(updater.updatedCount()),
            "bindings optimized", Integer.toString(updater.optimizedCount())
        );
        
        return updater;
    }

    private List<String> gatherControlsForRefresh(XFormsContainerControl containerControl) {

        final List<String> eventsToDispatch = new ArrayList<String>();

        Controls.visitControls((XFormsControl) containerControl, new Controls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                if (XFormsControl.controlSupportsRefreshEvents(control)) {// test here to make smaller list
                    eventsToDispatch.add(control.getEffectiveId());
                }
                return true;
            }
        }, true, true);

        return eventsToDispatch;
    }

    // Remember which control owns focus if any
    private XFormsControl focusedControl = null;

    public XFormsControl getFocusedControl() {
        return focusedControl;
    }

    public void setFocusedControl(XFormsControl focusedControl) {
        this.focusedControl = focusedControl;
    }

    public void clearFocusedControl() {
        this.focusedControl = null;
    }
}
