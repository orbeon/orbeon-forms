/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.dom4j.Element;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.Item;

import java.util.*;

/**
 * Represents a tree of XForms controls.
 */
public class ControlTree implements Cloneable {

    private final boolean isAllowSendingValueChangedEvents;
    private final boolean isAllowSendingRequiredEvents;
    private final boolean isAllowSendingRelevantEvents;
    private final boolean isAllowSendingReadonlyEvents;
    private final boolean isAllowSendingValidEvents;
    private final boolean isAllowSendingRefreshEvents;

    private final IndentedLogger indentedLogger;

    private final XFormsStaticState staticState;

    // Top-level controls
    private List<XFormsControl> children;   // top-level controls

    // Index of controls
    private ControlIndex controlIndex;

    private boolean isBindingsDirty;    // whether the bindings must be reevaluated

    public ControlTree(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger) {

        this.indentedLogger = indentedLogger;

        staticState = containingDocument.getStaticState();
        controlIndex = new ControlIndex(XFormsProperties.isNoscript(containingDocument));

        // Obtain global information about event handlers. This is a rough optimization so we can avoid sending certain
        // types of events below.

        isAllowSendingValueChangedEvents = staticState.hasHandlerForEvent(XFormsEvents.XFORMS_VALUE_CHANGED);
        isAllowSendingRequiredEvents = staticState.hasHandlerForEvent(XFormsEvents.XFORMS_REQUIRED) || staticState.hasHandlerForEvent(XFormsEvents.XFORMS_OPTIONAL);
        isAllowSendingRelevantEvents = staticState.hasHandlerForEvent(XFormsEvents.XFORMS_ENABLED) || staticState.hasHandlerForEvent(XFormsEvents.XFORMS_DISABLED);
        isAllowSendingReadonlyEvents = staticState.hasHandlerForEvent(XFormsEvents.XFORMS_READONLY) || staticState.hasHandlerForEvent(XFormsEvents.XFORMS_READWRITE);
        isAllowSendingValidEvents = staticState.hasHandlerForEvent(XFormsEvents.XFORMS_VALID) || staticState.hasHandlerForEvent(XFormsEvents.XFORMS_INVALID);

        isAllowSendingRefreshEvents = isAllowSendingValueChangedEvents || isAllowSendingRequiredEvents || isAllowSendingRelevantEvents || isAllowSendingReadonlyEvents || isAllowSendingValidEvents;
    }

    /**
     * Build the entire tree of controls and associated information.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     * @param rootContainer         root XBL container
     * @param isRestoringState      whether this is called while restoring the state
     */
    public void initialize(PropertyContext propertyContext, XFormsContainingDocument containingDocument, XBLContainer rootContainer, boolean isRestoringState) {

        indentedLogger.startHandleOperation("controls", "building");

        final int PROFILING_ITERATIONS = 1;
        for (int k = 0; k < PROFILING_ITERATIONS; k++) {

            // Create temporary root control
            final XXFormsRootControl rootControl = new XXFormsRootControl(containingDocument) {
                public void addChild(XFormsControl XFormsControl) {
                    // Add child to root control
                    super.addChild(XFormsControl);
                    // Forward children list to ControlTree. This so that the tree is made available during construction
                    // to XPath functions like index() or xxforms:case()
                    if (ControlTree.this.children == null) {
                        ControlTree.this.children = super.getChildren();
                    }
                }
            };

            // Visit the static tree of controls to create the actual tree of controls
            final CreateControlsListener listener
                    = new CreateControlsListener(propertyContext, controlIndex, rootControl, containingDocument.getSerializedControlStatesMap(propertyContext), isRestoringState);
            XFormsControls.visitControlElementsHandleRepeat(propertyContext, containingDocument, rootContainer, listener);

            // Detach all root XFormsControl
            if (children != null) {
                for (XFormsControl currentXFormsControl: children) {
                    currentXFormsControl.detach();
                }
            }

            // Evaluate controls
            controlIndex.evaluateAll(indentedLogger, propertyContext);

            // Dispatch initialization events for all controls created in index
            if (!isRestoringState) {
                // Copy list because it can be modified concurrently as events are being dispatched and handled
                final List<String> controlsEffectiveIds = new ArrayList<String>(controlIndex.getEffectiveIdsToControls().keySet());
                dispatchCreationEvents(propertyContext, controlsEffectiveIds);
            }

            if (k == PROFILING_ITERATIONS - 1) {
                indentedLogger.endHandleOperation(
                        "controls updated", Integer.toString(listener.getUpdateCount()),
                        "repeat iterations", Integer.toString(listener.getIterationCount())
                );
            } else {
                // This is for profiling only
                children = null;
                controlIndex.clear();
            }
        }
    }

    public boolean isAllowSendingRefreshEvents() {
        return isAllowSendingRefreshEvents;
    }

    private void dispatchCreationEvents(PropertyContext propertyContext, Collection<String> controlsEffectiveIds) {
        indentedLogger.startHandleOperation("controls", "dispatching creation events");
        {
            for (String effectiveId: controlsEffectiveIds) {
                final XFormsControl control = controlIndex.getControl(effectiveId);
                dispatchCreationEvents(propertyContext, control);
            }
        }
        indentedLogger.endHandleOperation();
    }

    public void dispatchCreationEvents(PropertyContext propertyContext, XFormsControl control) {
        if (XFormsControl.supportsRefreshEvents(control)) {
            if (control.isRelevant()) {
                final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                final XBLContainer container = singleNodeControl.getXBLContainer();
                final XFormsContainingDocument containingDocument = container.getContainingDocument();

                // Dispatch xforms-enabled if needed
                if (isAllowSendingRelevantEvents) {
                    container.dispatchEvent(propertyContext, new XFormsEnabledEvent(containingDocument, singleNodeControl));
                }

                // Dispatch events only if the MIP value is different from the default

                // Dispatch xforms-required if needed
                // TODO: must reacquire control and test for relevance again
                if (isAllowSendingRequiredEvents && singleNodeControl.isRequired()) {
                    container.dispatchEvent(propertyContext, new XFormsRequiredEvent(containingDocument, singleNodeControl));
                }

                // Dispatch xforms-readonly if needed
                // TODO: must reacquire control and test for relevance again
                if (isAllowSendingReadonlyEvents && singleNodeControl.isReadonly()) {
                    container.dispatchEvent(propertyContext, new XFormsReadonlyEvent(containingDocument, singleNodeControl));
                }

                // Dispatch xforms-invalid if needed
                // TODO: must reacquire control and test for relevance again
                if (isAllowSendingValidEvents && !singleNodeControl.isValid()) {
                    container.dispatchEvent(propertyContext, new XFormsInvalidEvent(containingDocument, singleNodeControl));
                }
            }
        }
    }

    public void dispatchDestructionEvents(PropertyContext propertyContext, XFormsRepeatIterationControl removedIteration) {
        // Gather ids of controls to handle
        final List<String> controlsEffectiveIds = new ArrayList<String>();
        visitChildrenControls(removedIteration, true, new XFormsControls.XFormsControlVisitorAdapter() {
            @Override
            public void startVisitControl(XFormsControl control) {
                // Don't handle container controls here
                if (!(control instanceof XFormsContainerControl))
                    controlsEffectiveIds.add(control.getEffectiveId());
            }
            @Override
            public void endVisitControl(XFormsControl control) {
                // Add container control after all its children have been added
                if (control instanceof XFormsContainerControl)
                    controlsEffectiveIds.add(control.getEffectiveId());
            }
        });

        // Dispatch events
        dispatchDestructionEvents(propertyContext, controlIndex, controlsEffectiveIds);
    }

    private void dispatchDestructionEvents(PropertyContext propertyContext, ControlIndex index, List<String> controlsEffectiveIds) {
        indentedLogger.startHandleOperation("controls", "dispatching destruction events");
        // NOTE: We do not need copy the list here because it was created in dispatchDestructionEvents() above
        for (String effectiveId: controlsEffectiveIds) {
            final XFormsControl control = index.getControl(effectiveId);
            dispatchDestructionEvents(propertyContext, control);
        }
        indentedLogger.endHandleOperation();
    }

    public void dispatchDestructionEvents(PropertyContext propertyContext, XFormsControl control) {
        if (XFormsControl.supportsRefreshEvents(control)) {
            final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
            final XBLContainer container = singleNodeControl.getXBLContainer();
            final XFormsContainingDocument containingDocument = container.getContainingDocument();

            // Don't test for relevance here
            // o In iteration removal case, control is still relevant
            // o In refresh case, control is non-relevant

            // Dispatch xforms-disabled if needed
            if (isAllowSendingRelevantEvents) {
                container.dispatchEvent(propertyContext, new XFormsDisabledEvent(containingDocument, singleNodeControl));
            }

            // TODO: if control *becomes* non-relevant and value changed, arguably we should dispatch the value-changed event
        }
    }

    public void dispatchChangeEvents(PropertyContext propertyContext, XFormsControl control) {

        // NOTE: For the purpose of dispatching value change and MIP events, we used to make a
        // distinction between value controls and plain single-node controls. However it seems that it is
        // still reasonable to dispatch those events to xforms:group, xforms:switch, and even repeat
        // iterations if they are bound.

        if (XFormsControl.supportsRefreshEvents(control)) {
            if (control.isRelevant()) {
                final XFormsSingleNodeControl singleNodeControl = (XFormsSingleNodeControl) control;
                final XBLContainer container = singleNodeControl.getXBLContainer();
                final XFormsContainingDocument containingDocument = container.getContainingDocument();

                if (isAllowSendingValueChangedEvents && singleNodeControl.isValueChanged()) {
                    container.dispatchEvent(propertyContext, new XFormsValueChangeEvent(containingDocument, singleNodeControl));
                }

                // Dispatch events only if the MIP value is different from the previous value

                // TODO: must reacquire control and test for relevance again
                if (isAllowSendingValidEvents) {
                    final boolean previousValidState = singleNodeControl.wasValid();
                    final boolean newValidState = singleNodeControl.isValid();

                    if (previousValidState != newValidState) {
                        if (newValidState) {
                            container.dispatchEvent(propertyContext, new XFormsValidEvent(containingDocument, singleNodeControl));
                        } else {
                            container.dispatchEvent(propertyContext, new XFormsInvalidEvent(containingDocument, singleNodeControl));
                        }
                    }
                }
                // TODO: must reacquire control and test for relevance again
                if (isAllowSendingRequiredEvents) {
                    final boolean previousRequiredState = singleNodeControl.wasRequired();
                    final boolean newRequiredState = singleNodeControl.isRequired();

                    if (previousRequiredState != newRequiredState) {
                        if (newRequiredState) {
                            container.dispatchEvent(propertyContext, new XFormsRequiredEvent(containingDocument, singleNodeControl));
                        } else {
                            container.dispatchEvent(propertyContext, new XFormsOptionalEvent(containingDocument, singleNodeControl));
                        }
                    }
                }
                // TODO: must reacquire control and test for relevance again
                if (isAllowSendingReadonlyEvents) {
                    final boolean previousReadonlyState = singleNodeControl.wasReadonly();
                    final boolean newReadonlyState = singleNodeControl.isReadonly();

                    if (previousReadonlyState != newReadonlyState) {
                        if (newReadonlyState) {
                            container.dispatchEvent(propertyContext, new XFormsReadonlyEvent(containingDocument, singleNodeControl));
                        } else {
                            container.dispatchEvent(propertyContext, new XFormsReadwriteEvent(containingDocument, singleNodeControl));
                        }
                    }
                }
            }
        }
    }

    public Object clone() throws CloneNotSupportedException {

        // Clone this
        final ControlTree cloned = (ControlTree) super.clone();

        // Clone children if any
        if (children != null) {
            cloned.children = new ArrayList<XFormsControl>(children.size());
            for (XFormsControl currentControl: children) {
                final XFormsControl currentClone = (XFormsControl) currentControl.clone();
                cloned.children.add(currentClone);
            }
        }

        // NOTE: The cloned tree does not make use of this so we clear it
        cloned.controlIndex = null;

        cloned.isBindingsDirty = false;

        return cloned;
    }

    /**
     * Create a new repeat iteration for insertion into the current tree of controls.
     *
     * WARNING: The binding context must be set to the current iteration before calling.
     *
     * @param propertyContext       current context
     * @param containingDocument    containing document
     * @param bindingContext        binding context set to the context of the new iteration
     * @param repeatControl         repeat control
     * @param iterationIndex        new iteration to repeat (1..repeat size + 1)
     * @return                      newly created repeat iteration control
     */
    public XFormsRepeatIterationControl createRepeatIterationTree(PropertyContext propertyContext,
                                                                  XFormsContainingDocument containingDocument,
                                                                  XFormsContextStack.BindingContext bindingContext,
                                                                  XFormsRepeatControl repeatControl, int iterationIndex) {

        // Create new index for the controls created in the iteration
        final ControlIndex iterationControlIndex = new ControlIndex(XFormsProperties.isNoscript(containingDocument));

        // Create iteration and set its binding context
        final XFormsRepeatIterationControl repeatIterationControl = new XFormsRepeatIterationControl(repeatControl.getXBLContainer(), repeatControl, iterationIndex);
        repeatIterationControl.setBindingContext(propertyContext, bindingContext);

        // Index this control
        iterationControlIndex.indexControl(repeatIterationControl);

        // Create the subtree
        XFormsControls.visitControlElementsHandleRepeat(propertyContext, repeatControl, iterationIndex,
                new CreateControlsListener(propertyContext, iterationControlIndex, repeatIterationControl, null, false));

        // Update main index before dispatching, so that events can access newly created controls
        controlIndex.addAll(iterationControlIndex);

        return repeatIterationControl;
    }

    public void initializeRepeatIterationTree(PropertyContext propertyContext, XFormsRepeatIterationControl repeatIteration) {

        // Gather all control ids and controls
        final Map<String, XFormsControl> effectiveIdsToControls = new LinkedHashMap<String, XFormsControl>();
        visitChildrenControls(repeatIteration, true, new XFormsControls.XFormsControlVisitorAdapter() {
            @Override
            public void startVisitControl(XFormsControl control) {
                effectiveIdsToControls.put(control.getEffectiveId(), control);
            }
        });

        // Evaluate controls, passing directly all the controls
        ControlIndex.evaluateAll(indentedLogger, propertyContext, effectiveIdsToControls.values());

        // Dispatch initialization events, passing the ids only
        dispatchCreationEvents(propertyContext, effectiveIdsToControls.keySet());
    }

    // NOTE: not used yet as of 2009-08
    public void createSubTree(PropertyContext propertyContext, XFormsContainerControl containerControl) {

        // TODO: implement in a way similar to createRepeatIterationTree()
        final XFormsControl control = (XFormsControl) containerControl;
        XFormsControls.visitControlElementsHandleRepeat(propertyContext, containerControl,
                new CreateControlsListener(propertyContext, controlIndex, control, null, false));
    }

    /**
     * Index a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     */
    public void indexSubtree(XFormsContainerControl containerControl, boolean includeCurrent) {
        visitChildrenControls(containerControl, includeCurrent, new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                // Index control
                controlIndex.indexControl(control);
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
        visitChildrenControls(containerControl, includeCurrent, new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                // Deindex control
                controlIndex.deindexControl(control);
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

    public List<XFormsControl> getChildren() {
        return children;
    }

    public Map<String, XFormsControl> getEffectiveIdsToControls() {
        return controlIndex.getEffectiveIdsToControls();
    }

    public Map<String, Integer> getInitialMinimalRepeatIdToIndex(XFormsStaticState staticState) {

        // TODO: for now, get the Map all the time, but should be optimized

        final Map<String, Integer> repeatIdToIndex = new LinkedHashMap<String, Integer>();

        visitControlsFollowRepeats(new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                if (control instanceof XFormsRepeatControl) {
                    // Found xforms:repeat
                    final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
                    repeatIdToIndex.put(repeatControl.getPrefixedId(), ((XFormsRepeatControl.XFormsRepeatControlLocal) repeatControl.getInitialLocal()).getIndex());
                }
            }
        });

        addMissingRepeatIndexes(staticState, repeatIdToIndex);

        return repeatIdToIndex;
    }

    public Map<String, Integer> getMinimalRepeatIdToIndex(XFormsStaticState staticState) {

        // TODO: for now, get the Map all the time, but should be optimized

        final Map<String, Integer> repeatIdToIndex = new LinkedHashMap<String, Integer>();

        visitControlsFollowRepeats(new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                if (control instanceof XFormsRepeatControl) {
                    // Found xforms:repeat
                    final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
                    repeatIdToIndex.put(repeatControl.getPrefixedId(), repeatControl.getIndex());
                }
            }
        });

        addMissingRepeatIndexes(staticState, repeatIdToIndex);

        return repeatIdToIndex;
    }

    private void addMissingRepeatIndexes(XFormsStaticState staticState, Map<String, Integer> repeatIdToIndex) {
        final Map<String, XFormsStaticState.ControlInfo> repeats = staticState.getRepeatControlInfoMap();
        if (repeats != null) {
            for (String repeatPrefixedId: repeats.keySet()) {
                if (repeatIdToIndex.get(repeatPrefixedId) == null) {
                    repeatIdToIndex.put(repeatPrefixedId, 0);
                }
            }
        }
    }

    /**
     * Visit all the controls.
     */
    public void visitAllControls(XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener) {
        handleControl(xformsControlVisitorListener, getChildren());
    }

    private static void visitChildrenControls(XFormsContainerControl containerControl, boolean includeCurrent, XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener) {
        if (includeCurrent) {
            xformsControlVisitorListener.startVisitControl((XFormsControl) containerControl);
        }
        handleControl(xformsControlVisitorListener, containerControl.getChildren());
        if (includeCurrent) {
            xformsControlVisitorListener.endVisitControl((XFormsControl) containerControl);
        }
    }

    private static void handleControl(XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener, List<XFormsControl> children) {
        if (children != null && children.size() > 0) {
            for (XFormsControl currentControl: children) {
                xformsControlVisitorListener.startVisitControl(currentControl);
                if (currentControl instanceof XFormsContainerControl)
                    handleControl(xformsControlVisitorListener, ((XFormsContainerControl) currentControl).getChildren());
                xformsControlVisitorListener.endVisitControl(currentControl);
            }
        }
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
        return controlIndex.getRepeatControls();
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

    /**
     * Visit all the XFormsControl elements by following the current repeat indexes.
     */
    private void visitControlsFollowRepeats(XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener) {
        visitControlsFollowRepeats(this.children, xformsControlVisitorListener);
    }

    private void visitControlsFollowRepeats(List<XFormsControl> children, XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener) {
        if (children != null && children.size() > 0) {
            for (XFormsControl currentControl: children) {
                xformsControlVisitorListener.startVisitControl(currentControl);
                {
                    if (currentControl instanceof XFormsRepeatControl) {
                        // Follow repeat branch
                        final XFormsRepeatControl currentRepeatControl = (XFormsRepeatControl) currentControl;
                        final int currentRepeatIndex = currentRepeatControl.getIndex();

                        if (currentRepeatIndex > 0) {
                            final List<XFormsControl> newChildren = currentRepeatControl.getChildren();
                            if (newChildren != null && newChildren.size() > 0)
                                visitControlsFollowRepeats(Collections.singletonList(newChildren.get(currentRepeatIndex - 1)), xformsControlVisitorListener);
                        }
                    } else if (currentControl instanceof XFormsContainerControl) {
                        // Handle container control children
                        visitControlsFollowRepeats(((XFormsContainerControl) currentControl).getChildren(), xformsControlVisitorListener);
                    }
                }
                xformsControlVisitorListener.endVisitControl(currentControl);
            }
        }
    }

    /**
     * Find an effective control id based on a source and a control static id, following XBL scoping and the repeat
     * structure.
     *
     * @param sourceControlEffectiveId  reference to source control, e.g. "list$age.3"
     * @param targetControlStaticId     reference to target control, e.g. "xf-10"
     * @return                          effective control id, or null if not found
     */
    public String findEffectiveControlId(String sourceControlEffectiveId, String targetControlStaticId) {

        // NOTE: The implementation tries to do a maximum using the static state. One reason is that the source
        // control's effective id might not yet have an associated control during construction. E.g.:
        //
        // <xf:group id="my-group" ref="employee[index('employee-repeat')]">
        //
        // In that case, the XFormsGroupControl does not yet exist when its binding is evaluated. However, its
        // effective id is known and passed as source, and can be used for resolving the id passed to the index()
        // function.
        //
        // We trust the caller to pass a valid source effective id. That value is always internal, i.e. not created by a
        // form author. On the other hand, the target id cannot be trusted as it is typically specified by the form
        // author.

        // Don't do anything if there are no controls
        if (this.children == null)
            return null;

        // 1: Check preconditions
        if (sourceControlEffectiveId == null)
            throw new IllegalArgumentException("Source control effective id is required.");

        final XBLBindings bindings = staticState.getXBLBindings();

        // 2: Obtain target prefixed id
        final String sourcePrefixedId = XFormsUtils.getPrefixedId(sourceControlEffectiveId);
        final XBLBindings.Scope scope = bindings.getResolutionScope(sourcePrefixedId);
        final String targetPrefixedId = bindings.getPrefixedIdInScope(scope, targetControlStaticId);

        // 3: Implement XForms 1.1 "4.7.1 References to Elements within a repeat Element" algorithm

        // Find closest common ancestor repeat

        final StringBuilder targetIndexBuilder = new StringBuilder();

        final String closestCommonAncestorRepeatPrefixedId = staticState.findClosestCommonAncestorRepeat(sourcePrefixedId, targetPrefixedId);
        if (closestCommonAncestorRepeatPrefixedId != null) {
            // There is a common ancestor repeat, use the current iteration as starting point

            final int ancestorCount = staticState.getAncestorRepeats(closestCommonAncestorRepeatPrefixedId, null).size() + 1;
            if (ancestorCount > 0) {
                final Integer[] parts = XFormsUtils.getEffectiveIdSuffixParts(sourceControlEffectiveId);
                for (int i = 0; i < ancestorCount; i ++) {
                    appendIterationToSuffix(targetIndexBuilder, parts[i]);
                }
            }
        }

        // Find list of ancestor repeats for destination WITHOUT including the closest ancestor repeat if any
        final List<String> targetAncestorRepeats = staticState.getAncestorRepeats(targetPrefixedId, closestCommonAncestorRepeatPrefixedId);
        Collections.reverse(targetAncestorRepeats); // go from trunk to leaf

        // Follow repeat indexes towards target
        for (final String repeatPrefixedId: targetAncestorRepeats) {
            // Get repeat control
            final XFormsRepeatControl repeatIterationControl = (XFormsRepeatControl) getControl(repeatPrefixedId + targetIndexBuilder);

            // Controls might not exist
            if (repeatIterationControl == null)
                return null;

            // Update iteration suffix
            appendIterationToSuffix(targetIndexBuilder, repeatIterationControl.getIndex());
        }

        // Return target
        return targetPrefixedId + targetIndexBuilder;
    }

    private void appendIterationToSuffix(StringBuilder suffix, int iteration) {
        if (suffix.length() == 0)
            suffix.append(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        else if (suffix.length() != 1)
            suffix.append(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2);

        suffix.append(Integer.toString(iteration));
    }

    /**
     * Listener used to create a tree of controls from scratch. Used:
     *
     * o the first time controls are created
     * o for new repeat iterations
     * o for new non-relevant parts of a tree (under development as of 2009-02-24)
     */
    private static class CreateControlsListener implements XFormsControls.ControlElementVisitorListener {

        private XFormsControl currentControlsContainer;

        private final Map serializedControls;
        private final boolean evaluateItemsets;
        private final PropertyContext propertyContext;
        private final ControlIndex controlIndex;

        private transient int updateCount;
        private transient int iterationCount;

        public CreateControlsListener(PropertyContext propertyContext, ControlIndex controlIndex, XFormsControl rootControl,
                                      Map serializedControlStateMap,boolean evaluateItemsets) {

            this.currentControlsContainer = rootControl;

            this.serializedControls = serializedControlStateMap;
            this.evaluateItemsets = evaluateItemsets;
            this.propertyContext = propertyContext;
            this.controlIndex = controlIndex;
        }

        public XFormsControl startVisitControl(XBLContainer container, Element controlElement, String effectiveControlId) {

            updateCount++;

            // Create XFormsControl with basic information
            final XFormsControl control = XFormsControlFactory.createXFormsControl(container, currentControlsContainer, controlElement, effectiveControlId);

            // If needed, deserialize control state
            if (serializedControls != null) {
                final Element element = (Element) serializedControls.get(effectiveControlId);
                if (element != null)
                    control.deserializeLocal(element);
            }

            // Set current binding for control element
            final XFormsContextStack.BindingContext currentBindingContext = container.getContextStack().getCurrentBindingContext();
            control.setBindingContext(propertyContext, currentBindingContext);

            // Control type-specific handling
            if (control instanceof XFormsSelectControl || control instanceof XFormsSelect1Control) {
                // Handle xforms:itemset
                final XFormsSelect1Control select1Control = ((XFormsSelect1Control) control);
                // Evaluate itemsets only if specified (case of restoring dynamic state)
                if (evaluateItemsets)
                    select1Control.getItemset(propertyContext, false);
            }

            // Index this control
            // NOTE: Must do after setting the context, so that relevance can be properly determined
            controlIndex.indexControl(control);

            // Add to current container control
            ((XFormsContainerControl) currentControlsContainer).addChild(control);

            // Current grouping control becomes the current controls container
            if (control instanceof XFormsContainerControl) {
                currentControlsContainer = control;
            }

//            if (control instanceof XFormsComponentControl) {
//                // Compute new id prefix for nested component
//                final String newIdPrefix = idPrefix + staticControlId + XFormsConstants.COMPONENT_SEPARATOR;
//
//                // Recurse into component tree
//                final Element shadowTreeDocumentElement = staticState.getCompactShadowTree(idPrefix + staticControlId);
//                XFormsControls.visitControlElementsHandleRepeat(pipelineContext, this, isOptimizeRelevance,
//                        staticState, newContainer, shadowTreeDocumentElement, newIdPrefix, idPostfix);
//            }

            return control;
        }

        public void endVisitControl(Element controlElement, String effectiveControlId) {
            final XFormsControl control = controlIndex.getControl(effectiveControlId);
            if (control instanceof XFormsContainerControl) {
                // Notify container controls that all children have been added
                final XFormsContainerControl containerControl = (XFormsContainerControl) control;
                containerControl.childrenAdded(propertyContext);

                // Go back up to parent
                currentControlsContainer = currentControlsContainer.getParent();
            }
        }

        public boolean startRepeatIteration(XBLContainer container, int iteration, String effectiveIterationId) {

            iterationCount++;

            final XFormsRepeatIterationControl repeatIterationControl = new XFormsRepeatIterationControl(container, (XFormsRepeatControl) currentControlsContainer, iteration);

            ((XFormsContainerControl) currentControlsContainer).addChild(repeatIterationControl);
            currentControlsContainer = repeatIterationControl;

            // Set current binding for iteration
            final XFormsContextStack.BindingContext currentBindingContext = container.getContextStack().getCurrentBindingContext();
            repeatIterationControl.setBindingContext(propertyContext, currentBindingContext);

            // Index this control
            // NOTE: Must do after setting the context, so that relevance can be properly determined
            // NOTE: We don't dispatch events to repeat iterations
            controlIndex.indexControl(repeatIterationControl);

            return true;
        }

        public void endRepeatIteration(int iteration) {
            currentControlsContainer = currentControlsContainer.getParent();
        }

        public int getUpdateCount() {
            return updateCount;
        }

        public int getIterationCount() {
            return iterationCount;
        }
    }

    public static final boolean TESTING_DIALOG_OPTIMIZATION = false;

    /**
     * Listener used to update an existing tree of controls. Used during refresh.
     */
    public static class UpdateBindingsListener implements XFormsControls.ControlElementVisitorListener {

        private final PropertyContext propertyContext;
        private final Map effectiveIdsToControls;

        private transient int updateCount;
        private transient int iterationCount;

        public UpdateBindingsListener(PropertyContext propertyContext, Map effectiveIdsToControls) {
            this.propertyContext = propertyContext;
            this.effectiveIdsToControls = effectiveIdsToControls;
        }

        private Map<String, XFormsRepeatIterationControl> newIterationsMap = new HashMap<String, XFormsRepeatIterationControl>();

        public XFormsControl startVisitControl(XBLContainer container, Element controlElement, String effectiveControlId) {

            updateCount++;

            final XFormsControl control = (XFormsControl) effectiveIdsToControls.get(effectiveControlId);

            final XFormsContextStack.BindingContext oldBindingContext = control.getBindingContext();
            final XFormsContextStack.BindingContext newBindingContext = container.getContextStack().getCurrentBindingContext();

            if (control instanceof XFormsRepeatControl) {
                final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;


                // Handle repeat
                // TODO: handle this through inheritance

                // Get old nodeset
                final List<Item> oldRepeatNodeset = oldBindingContext.getNodeset();

                // Get new nodeset
                final List<Item> newRepeatNodeset = newBindingContext.getNodeset();

                // Set new current binding for control element
                repeatControl.setBindingContext(propertyContext, newBindingContext);

                // Update iterations
                final List<XFormsRepeatIterationControl> newIterations = repeatControl.updateIterations(propertyContext, oldRepeatNodeset, newRepeatNodeset, null);

                // Remember newly created iterations so we don't recurse into them in startRepeatIteration()
                // o It is not needed to recurse into them because their bindings are up to date since they have just been created
                // o However they have not yet been evaluated. They will be evaluated at the same time the other controls are evaluated
                for (XFormsRepeatIterationControl newIteration: newIterations) {
                    newIterationsMap.put(newIteration.getEffectiveId(), newIteration);
                }
            } else if (TESTING_DIALOG_OPTIMIZATION && control instanceof XXFormsDialogControl) {// TODO: TESTING DIALOG OPTIMIZATION
                // Handle dialog
                // TODO: handle this through inheritance

                control.setBindingContext(propertyContext, newBindingContext);

                final XXFormsDialogControl dialogControl = (XXFormsDialogControl) control;
                final boolean isVisible = dialogControl.isVisible();
                dialogControl.updateContent(propertyContext, isVisible);

            } else {
                // Handle all other controls
                // TODO: handle other container controls

                // Set new current binding for control element
                control.setBindingContext(propertyContext, newBindingContext);
            }

            // Mark the control as dirty so it gets reevaluated
            // NOTE: existing repeat iterations are marked dirty below in startRepeatIteration()
            control.markDirty();

            return control;
        }

        public void endVisitControl(Element controlElement, String effectiveControlId) {
        }

        public boolean startRepeatIteration(XBLContainer container, int iteration, String effectiveIterationId) {

            iterationCount++;

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

        public int getUpdateCount() {
            return updateCount;
        }

        public int getIterationCount() {
            return iterationCount;
        }
    }
}
