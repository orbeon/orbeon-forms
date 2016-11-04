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
import org.orbeon.oxf.xforms.state.ControlState;
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
    // trees need not be compared. A general mechanism detecting mutations in the proper places would be better.
    private boolean dirtySinceLastRequest;

    // Whether we currently require a UI refresh
    private boolean requireRefresh = false;

    // Whether we are currently in a refresh
    private boolean inRefresh = false;

    private final XFormsContainingDocument containingDocument;

    private Map<String, Itemset> constantItems;

    private final XPathDependencies xpathDependencies;

    public XFormsControls(XFormsContainingDocument containingDocument) {

        this.indentedLogger = containingDocument.getIndentedLogger(LOGGING_CATEGORY);
        this.containingDocument = containingDocument;
        this.xpathDependencies = containingDocument.getXPathDependencies();

        // Create minimal tree
        initialControlTree = new ControlTree(indentedLogger);
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

    public boolean isInRefresh() {
        return inRefresh;
    }

    public void refreshStart() {
        requireRefresh = false;
        inRefresh = true;

        xpathDependencies.refreshStart();
    }

    public void refreshDone() {
        inRefresh = false;

        xpathDependencies.refreshDone();
    }

    /**
     * Create the controls, whether upon initial creation of restoration of the controls.
     */
    public void createControlTree(scala.Option<scala.collection.immutable.Map<String, ControlState>> state) {

        assert !initialized;

        if (containingDocument.getStaticState().topLevelPart().hasControls()) {

            // Create new controls tree
            // NOTE: We set this first so that the tree is made available during construction to XPath functions like index() or case()
            currentControlTree = initialControlTree = new ControlTree(indentedLogger);

            // Set this here so that while initialize() runs below, refresh events will find the flag set
            initialized = true;

            // Initialize new control tree
            currentControlTree.initialize(containingDocument, state);
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
    public XFormsControl getObjectByEffectiveId(String effectiveId) {
        return currentControlTree.findControlOrNull(effectiveId);
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
        return Controls.resolveObjectByIdJava(containingDocument, sourceControlEffectiveId, targetStaticId);
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

    public void doRefresh() {

        if (inRefresh) {
            // Ignore "nested refresh"
            // See https://github.com/orbeon/orbeon-forms/issues/1550
            indentedLogger.logDebug("controls", "attempt to do nested refresh");
            return;
        }

        // This method implements the new refresh event algorithm:
        // http://wiki.orbeon.com/forms/doc/developer-guide/xforms-refresh-events

        // Don't do anything if there are no children controls
        if (getCurrentControlTree().children().isEmpty()) {
            indentedLogger.logDebug("controls", "not performing refresh because no controls are available");
            refreshStart();
            refreshDone();
        } else {
            indentedLogger.startHandleOperation("controls", "performing refresh");
            {
                final XFormsControl focusedBefore;
                final Controls.BindingUpdater updater;
                final List<String> controlsEffectiveIds;

                // Notify dependencies
                refreshStart();
                try {

                    // Focused control before updating bindings
                    focusedBefore = getFocusedControl();

                    // Update control bindings
                    // NOTE: During this process, ideally, no events are dispatched. However, at this point, the code
                    // can an dispatch, upon removed repeat iterations, xforms-disabled, DOMFocusOut and possibly events
                    // arising from updating the binding of nested XBL controls.
                    // This unfortunately means that side effects can take place. This should be fixed, maybe by simply
                    // detaching removed iterations first, and then dispatching events after all bindings have been
                    // updated as part of dispatchRefreshEvents() below. This requires that controls are able to kind of
                    // stay alive in detached mode, and then that the index is also available while these events are
                    // dispatched.
                    updater = updateControlBindings();

                    // There are potentially event handlers for UI events, so do the whole processing

                    // Gather controls to which to dispatch refresh events
                    controlsEffectiveIds = (updater != null) ? gatherControlsForRefresh() : null;
                } finally {
                    // "Actions that directly invoke rebuild, recalculate, revalidate, or refresh always have an immediate
                    // effect, and clear the corresponding flag."
                    refreshDone();
                }

                if (updater != null) {
                    // Dispatch events
                    currentControlTree.dispatchRefreshEventsJava(controlsEffectiveIds);

                    // Handle focus changes
                    Focus.updateFocusWithEvents(focusedBefore, updater.partialFocusRepeat());
                }
            }
            indentedLogger.endHandleOperation();
        }
    }

    /**
     * Update all the control bindings.
     *
     * Return null if controls are not initialized or if control bindings are not dirty. Otherwise, control bindings are
     * updated and the BindingUpdater is returned.
     */
    private Controls.BindingUpdater updateControlBindings() {

        if (!initialized) {
            return null;
        } else {
            // This is the regular case

            // Don't do anything if bindings are clean
            if (!currentControlTree.bindingsDirty())
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

        // There are potentially event handlers for UI events, so do the whole processing

        // Gather controls to which to dispatch refresh events
        final List<String> eventsToDispatch = gatherControlsForRefresh(containerControl);

        // Dispatch events
        currentControlTree.dispatchRefreshEventsJava(eventsToDispatch);

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
        }, true);

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
