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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.control.*;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xforms.event.Dispatch;

import java.util.*;

/**
 * Represents a tree of XForms controls.
 */
public class ControlTree implements ExternalCopyable {

    private boolean isAllowSendingValueChangedEvents;
    private boolean isAllowSendingRequiredEvents;
    private boolean isAllowSendingRelevantEvents;
    private boolean isAllowSendingReadonlyEvents;
    private boolean isAllowSendingValidEvents;
    private boolean isAllowSendingRefreshEvents;
    private boolean isAllowSendingIterationMovedEvents;

    private final IndentedLogger indentedLogger;

    // Top-level controls
    private XFormsContainerControl root; // top-level control

    // Index of controls
    private ControlIndex controlIndex;

    // Repeat indexes for Ajax updates only
    private Map<String, Integer> indexes = Collections.emptyMap();

    private boolean isBindingsDirty;    // whether the bindings must be reevaluated

    public ControlTree(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger) {
        this.indentedLogger = indentedLogger;
        this.controlIndex = new ControlIndex(containingDocument.getStaticState().isNoscript());
        updateAllowedEvents(containingDocument);
    }

    public void updateAllowedEvents(XFormsContainingDocument containingDocument) {
        // Obtain global information about event handlers. This is a rough optimization so we can avoid sending certain
        // types of events below. Mostly useful for simple forms!

        final StaticStateGlobalOps ops = containingDocument.getStaticOps();

        isAllowSendingValueChangedEvents = ops.hasHandlerForEvent(XFormsEvents.XFORMS_VALUE_CHANGED);
        isAllowSendingRequiredEvents = ops.hasHandlerForEvent(XFormsEvents.XFORMS_REQUIRED) || ops.hasHandlerForEvent(XFormsEvents.XFORMS_OPTIONAL);
        isAllowSendingRelevantEvents = ops.hasHandlerForEvent(XFormsEvents.XFORMS_ENABLED) || ops.hasHandlerForEvent(XFormsEvents.XFORMS_DISABLED);
        isAllowSendingReadonlyEvents = ops.hasHandlerForEvent(XFormsEvents.XFORMS_READONLY) || ops.hasHandlerForEvent(XFormsEvents.XFORMS_READWRITE);
        isAllowSendingValidEvents = ops.hasHandlerForEvent(XFormsEvents.XFORMS_VALID) || ops.hasHandlerForEvent(XFormsEvents.XFORMS_INVALID);
        isAllowSendingIterationMovedEvents = ops.hasHandlerForEvent(XFormsEvents.XXFORMS_ITERATION_MOVED);
        final boolean isAllowSendingNodesetChangedEvent = ops.hasHandlerForEvent(XFormsEvents.XXFORMS_NODESET_CHANGED);
        final boolean isAllowSendingIndexChangedEvent = ops.hasHandlerForEvent(XFormsEvents.XXFORMS_INDEX_CHANGED);

        isAllowSendingRefreshEvents = isAllowSendingValueChangedEvents
                || isAllowSendingRequiredEvents
                || isAllowSendingRelevantEvents
                || isAllowSendingReadonlyEvents
                || isAllowSendingValidEvents
                || isAllowSendingIterationMovedEvents
                || isAllowSendingNodesetChangedEvent
                || isAllowSendingIndexChangedEvent;
    }

    /**
     * Build the entire tree of controls and associated information.
     */
    public void initialize(XFormsContainingDocument containingDocument) {

        indentedLogger.startHandleOperation("controls", "building");

        // Visit the static tree of controls to create the actual tree of controls
        Controls.createTree(containingDocument, controlIndex);

        // Evaluate all controls
        final Collection<XFormsControl> allControls = controlIndex.getEffectiveIdsToControls().values();
        ControlIndex.evaluateAll(indentedLogger, allControls);

        // Dispatch initialization events for all controls created in index
        if (!containingDocument.isRestoringDynamicState()) {
            // Copy list because it can be modified concurrently as events are being dispatched and handled
            final List<String> controlsEffectiveIds = new ArrayList<String>(controlIndex.getEffectiveIdsToControls().keySet());
            dispatchRefreshEvents(controlsEffectiveIds);
        } else {
            // Make sure all controls state such as relevance, value changed, etc. does not mark a difference
            for (final XFormsControl control: allControls) {
                control.commitCurrentUIState();
            }
        }

        indentedLogger.endHandleOperation(
            "controls created", Integer.toString(allControls.size())
        );
    }

    public boolean isAllowSendingRefreshEvents() {
        return isAllowSendingRefreshEvents;
    }

    public void dispatchRefreshEvents(Collection<String> controlsEffectiveIds) {
        indentedLogger.startHandleOperation("controls", "dispatching refresh events");
        for (final String controlEffectiveId: controlsEffectiveIds) {
            final XFormsControl control = getControl(controlEffectiveId);
            dispatchRefreshEvents(control);
        }
        indentedLogger.endHandleOperation();
    }

    private void dispatchRefreshEvents(XFormsControl control) {
        if (XFormsControl.controlSupportsRefreshEvents(control)) {

            final boolean oldRelevantState = control.wasRelevantCommit();
            final boolean newRelevantState = control.isRelevant();

            if (newRelevantState && !oldRelevantState) {
                // Control has become relevant
                dispatchCreationEvents(control);
            } else if (!newRelevantState && oldRelevantState) {
                // Control has become non-relevant
                dispatchDestructionEvents(control);
            } else if (newRelevantState) {
                // Control was and is relevant
                dispatchChangeEvents(control);
            }
        }
    }

    private void dispatchCreationEvents(XFormsControl control) {
        if (XFormsControl.controlSupportsRefreshEvents(control)) {
            if (control.isRelevant()) {
                final XFormsContainingDocument containingDocument = control.containingDocument();

                // Commit current control state
                control.commitCurrentUIState();

                // Dispatch xforms-enabled if needed
                if (isAllowSendingRelevantEvents) {
                    Dispatch.dispatchEvent(new XFormsEnabledEvent(containingDocument, control));
                }
                if (control instanceof XFormsSingleNodeControl) {
                    final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                    // Dispatch events only if the MIP value is different from the default

                    // Dispatch xforms-required if needed
                    // TODO: must reacquire control and test for relevance again
                    if (isAllowSendingRequiredEvents && singleNodeControl.isRequired()) {
                        Dispatch.dispatchEvent(new XFormsRequiredEvent(containingDocument, singleNodeControl));
                    }

                    // Dispatch xforms-readonly if needed
                    // TODO: must reacquire control and test for relevance again
                    if (isAllowSendingReadonlyEvents && singleNodeControl.isReadonly()) {
                        Dispatch.dispatchEvent(new XFormsReadonlyEvent(containingDocument, singleNodeControl));
                    }

                    // Dispatch xforms-invalid if needed
                    // TODO: must reacquire control and test for relevance again
                    if (isAllowSendingValidEvents && !singleNodeControl.isValid()) {
                        Dispatch.dispatchEvent(new XFormsInvalidEvent(containingDocument, singleNodeControl));
                    }
                }
            }
        }
    }

    public void dispatchDestructionEventsForRemovedContainer(XFormsContainerControl removedControl, boolean includeCurrent) {

        indentedLogger.startHandleOperation("controls", "dispatching destruction events");

        // Gather ids of controls to handle
        final List<String> controlsEffectiveIds = new ArrayList<String>();
        Controls.visitControls(removedControl, includeCurrent, new Controls.XFormsControlVisitorAdapter() {
            @Override
            public boolean startVisitControl(XFormsControl control) {
                // Don't handle container controls here
                if (!(control instanceof XFormsContainerControl))
                    controlsEffectiveIds.add(control.getEffectiveId());
                return true;
            }
            @Override
            public void endVisitControl(XFormsControl control) {
                // Add container control after all its children have been added
                if (control instanceof XFormsContainerControl)
                    controlsEffectiveIds.add(control.getEffectiveId());
            }
        });

        // Dispatch events
        for (final String effectiveId : controlsEffectiveIds) {
            final XFormsControl control = controlIndex.getControl(effectiveId);
            // Directly call destruction events as we know the iteration is going away
            dispatchDestructionEvents(control);
        }

        indentedLogger.endHandleOperation();
    }

    private void dispatchDestructionEvents(XFormsControl control) {
        if (XFormsControl.controlSupportsRefreshEvents(control)) {
            final XFormsContainingDocument containingDocument = control.containingDocument();

            // Don't test for relevance here
            // o In iteration removal case, control is still relevant
            // o In refresh case, control is non-relevant

            // Dispatch xforms-disabled if needed
            if (isAllowSendingRelevantEvents) {
                Dispatch.dispatchEvent(new XFormsDisabledEvent(containingDocument, control));
            }

            // TODO: if control *becomes* non-relevant and value changed, arguably we should dispatch the value-changed event
        }
    }

    private void dispatchChangeEvents(XFormsControl control) {

        // NOTE: For the purpose of dispatching value change and MIP events, we used to make a
        // distinction between value controls and plain single-node controls. However it seems that it is
        // still reasonable to dispatch those events to xforms:group, xforms:switch, and even repeat
        // iterations if they are bound.

        if (XFormsControl.controlSupportsRefreshEvents(control)) {
            if (control.isRelevant()) {
                // TODO: implement dispatchRefreshEvents() on all controls instead of using if ()
                if (control instanceof XFormsSingleNodeControl) {

                    final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                    final XFormsContainingDocument containingDocument = control.containingDocument();

                    // xforms-value-changed
                    if (isAllowSendingValueChangedEvents && singleNodeControl.isValueChanged()) {
                        Dispatch.dispatchEvent(new XFormsValueChangeEvent(containingDocument, singleNodeControl));
                    }

                    // Dispatch moved xxforms-iteration-changed if needed
                    if (isAllowSendingIterationMovedEvents
                            && control.previousEffectiveIdCommit() != control.getEffectiveId()
                            && control.container().getPartAnalysis().observerHasHandlerForEvent(control.getPrefixedId(), XFormsEvents.XXFORMS_ITERATION_MOVED)) {
                        Dispatch.dispatchEvent(new XXFormsIterationMovedEvent(containingDocument, control));
                    }

                    // Dispatch events only if the MIP value is different from the previous value

                    // TODO: must reacquire control and test for relevance again
                    if (isAllowSendingValidEvents) {
                        final boolean previousValidState = singleNodeControl.wasValid();
                        final boolean newValidState = singleNodeControl.isValid();

                        if (previousValidState != newValidState) {
                            if (newValidState) {
                                Dispatch.dispatchEvent(new XFormsValidEvent(containingDocument, singleNodeControl));
                            } else {
                                Dispatch.dispatchEvent(new XFormsInvalidEvent(containingDocument, singleNodeControl));
                            }
                        }
                    }
                    // TODO: must reacquire control and test for relevance again
                    if (isAllowSendingRequiredEvents) {
                        final boolean previousRequiredState = singleNodeControl.wasRequired();
                        final boolean newRequiredState = singleNodeControl.isRequired();

                        if (previousRequiredState != newRequiredState) {
                            if (newRequiredState) {
                                Dispatch.dispatchEvent(new XFormsRequiredEvent(containingDocument, singleNodeControl));
                            } else {
                                Dispatch.dispatchEvent(new XFormsOptionalEvent(containingDocument, singleNodeControl));
                            }
                        }
                    }
                    // TODO: must reacquire control and test for relevance again
                    if (isAllowSendingReadonlyEvents) {
                        final boolean previousReadonlyState = singleNodeControl.wasReadonly();
                        final boolean newReadonlyState = singleNodeControl.isReadonly();

                        if (previousReadonlyState != newReadonlyState) {
                            if (newReadonlyState) {
                                Dispatch.dispatchEvent(new XFormsReadonlyEvent(containingDocument, singleNodeControl));
                            } else {
                                Dispatch.dispatchEvent(new XFormsReadwriteEvent(containingDocument, singleNodeControl));
                            }
                        }
                    }
                } else if (control instanceof XFormsRepeatControl) {
                    ((XFormsRepeatControl) control).dispatchRefreshEvents();
                }
            }
        }
    }

    public Object getBackCopy() {

        // Clone this
        final ControlTree cloned;
        try {
            cloned = (ControlTree) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new OXFException(e);
        }

        if (root != null) {
            // Gather repeat indexes if any
            // Do this before cloning controls so that initial/current locals are still different
            cloned.indexes = XFormsRepeatControl.findInitialIndexes(((XFormsControl) root).containingDocument(), this);

            // Clone children if any
            cloned.root = (XFormsContainerControl) root.getBackCopy();
        }

        // NOTE: The cloned tree does not make use of this so we clear it
        cloned.controlIndex = null;

        cloned.isBindingsDirty = false;

        return cloned;
    }

    // Only for initial tree after getBackCopy has been called
    public Map<String, Integer> getIndexes() {
        return indexes;
    }

    /**
     * Create a new repeat iteration for insertion into the current tree of controls.
     *
     * @param containingDocument    containing document
     * @param repeatControl         repeat control
     * @param iterationIndex        new iteration to repeat (1..repeat size + 1)
     * @return                      newly created repeat iteration control
     */
    public XFormsRepeatIterationControl createRepeatIterationTree(XFormsContainingDocument containingDocument,
                                                                  XFormsRepeatControl repeatControl, int iterationIndex) {

        // Index for the controls created in the iteration
        // NOTE: We used to create a separate index, but this caused this bug:
        // [ #316177 ] When new repeat iteration is created upon repeat update, controls are not immediately accessible by id
        return Controls.createRepeatIterationTree(containingDocument, controlIndex, repeatControl, iterationIndex);
    }

    public void initializeSubTree(XFormsContainerControl containerControl, boolean includeCurrent) {

        // Gather all control ids and controls
        final Map<String, XFormsControl> effectiveIdsToControls = new LinkedHashMap<String, XFormsControl>();
        Controls.visitControls(containerControl, includeCurrent, new Controls.XFormsControlVisitorAdapter() {
            @Override
            public boolean startVisitControl(XFormsControl control) {
                effectiveIdsToControls.put(control.getEffectiveId(), control);
                return true;
            }
        });

        // Evaluate controls, passing directly all the controls
        ControlIndex.evaluateAll(indentedLogger, effectiveIdsToControls.values());

        // Dispatch initialization events, passing the ids only
        dispatchRefreshEvents(effectiveIdsToControls.keySet());
    }

    public void createAndInitializeSubTree(XBLContainer container, XFormsContainerControl containerControl, ElementAnalysis elementAnalysis) {

        // Index for the controls created in the subtree
        // NOTE: We used to create a separate index, but this caused this bug:
        // [ #316177 ] When new repeat iteration is created upon repeat update, controls are not immediately accessible by id
        Controls.createSubTree(container, controlIndex, containerControl, elementAnalysis);

        initializeSubTree(containerControl, false);
    }

    /**
     * Index a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     */
    public void indexSubtree(XFormsContainerControl containerControl, boolean includeCurrent) {
        Controls.visitControls(containerControl, includeCurrent, new Controls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                // Index control
                controlIndex.indexControl(control);
                return true;
            }
        });
    }

    /**
     * Deindex a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     */
    public void deindexSubtree(XFormsContainerControl containerControl, boolean includeCurrent) {
        Controls.visitControls(containerControl, includeCurrent, new Controls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                // Deindex control
                controlIndex.deindexControl(control);
                return true;
            }
        });
    }

    public boolean isBindingsDirty() {
        return isBindingsDirty;
    }

    public void markBindingsClean() {
        this.isBindingsDirty = false;
    }

    public void markBindingsDirty() {
        this.isBindingsDirty = true;
    }

    public XFormsContainerControl getRoot() {
        return root;
    }

    public void setRoot(XFormsContainerControl root) {
        this.root = root;
    }

    public List<XFormsControl> getChildren() {
        return root != null ? root.childrenJava() : Collections.<XFormsControl>emptyList();
    }

    public Map<String, XFormsControl> getEffectiveIdsToControls() {
        return controlIndex.getEffectiveIdsToControls();
    }

    public XFormsControl getControl(String effectiveId) {
        // Delegate
        return controlIndex.getControl(effectiveId);
    }

    public Map<String, XFormsControl> getUploadControls() {
        // Delegate
        return controlIndex.getUploadControls();
    }

    public Map<String, XFormsControl> getRepeatControls() {
        // Delegate
        return controlIndex != null ? controlIndex.getRepeatControls() : Collections.<String, XFormsControl>emptyMap();
    }

    public Map<String, XFormsControl> getDialogControls() {
        // Delegate
        return controlIndex.getDialogControls();
    }

    /**
     * Return the list of xforms:select[@appearance = 'full'] in noscript mode.
     *
     * @return LinkedHashMap<String effectiveId, XFormsSelectControl control>
     */
    public Map<String, XFormsControl> getSelectFullControls() {
        // Delegate
        return controlIndex.getSelectFullControls();
    }
}
