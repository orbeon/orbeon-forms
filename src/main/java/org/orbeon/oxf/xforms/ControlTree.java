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
import org.orbeon.oxf.xforms.control.Controls;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.state.ControlState;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.*;

/**
 * Represents a tree of XForms controls.
 */
public class ControlTree implements Cloneable {

    private final IndentedLogger indentedLogger;

    // Top-level controls
    private XFormsContainerControl root; // top-level control

    // Index of controls
    private ControlIndex controlIndex;

    // Repeat indexes for Ajax updates only
    private Map<String, Integer> indexes = Collections.emptyMap();

    private boolean isBindingsDirty;    // whether the bindings must be reevaluated

    public ControlTree(IndentedLogger indentedLogger) {
        this.indentedLogger = indentedLogger;
        this.controlIndex = new ControlIndex();
    }

    /**
     * Build the entire tree of controls and associated information.
     */
    public void initialize(XFormsContainingDocument containingDocument, scala.Option<scala.collection.immutable.Map<String, ControlState>> state) {

        indentedLogger.startHandleOperation("controls", "building");

        // Visit the static tree of controls to create the actual tree of controls
        Controls.createTree(containingDocument, controlIndex, state);

        // Evaluate all controls
        final Collection<XFormsControl> allControls = controlIndex.getEffectiveIdsToControls().values();

        // Dispatch initialization events for all controls created in index
        if (state.isEmpty()) {
            // Copy list because it can be modified concurrently as events are being dispatched and handled
            final List<String> controlsEffectiveIds = new ArrayList<String>(controlIndex.getEffectiveIdsToControls().keySet());
            dispatchRefreshEvents(controlsEffectiveIds);
        } else {
            // Make sure all control state such as relevance, value changed, etc. does not mark a difference
            for (final XFormsControl control: allControls) {
                control.commitCurrentUIState();
            }
        }

        indentedLogger.endHandleOperation(
            "controls created", Integer.toString(allControls.size())
        );
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
                control.dispatchCreationEvents();
            } else if (!newRelevantState && oldRelevantState) {
                // Control has become non-relevant
                control.dispatchDestructionEvents();
            } else if (newRelevantState) {
                // Control was and is relevant
                control.dispatchChangeEvents();
            }
        }
    }

    public void dispatchDestructionEventsForRemovedContainer(XFormsContainerControl removedControl, boolean includeCurrent) {

        indentedLogger.startHandleOperation("controls", "dispatching destruction events");

        // Gather ids of controls to handle
        final List<String> controlsEffectiveIds = new ArrayList<String>();
        Controls.visitControls((XFormsControl) removedControl, new Controls.XFormsControlVisitorAdapter() {
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
        }, includeCurrent);

        // Dispatch events
        for (final String effectiveId : controlsEffectiveIds) {
            final XFormsControl control = controlIndex.getControl(effectiveId);
            // Directly call destruction events as we know the iteration is going away
            if (XFormsControl.controlSupportsRefreshEvents(control))
                control.dispatchDestructionEvents();
        }

        indentedLogger.endHandleOperation("controls", Integer.toString(controlsEffectiveIds.size()));
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
            cloned.indexes = XFormsRepeatControl.initialIndexesJava(((XFormsControl) root).containingDocument());

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

        // NOTE: We used to create a separate index, but this caused this bug:
        // [ #316177 ] When new repeat iteration is created upon repeat update, controls are not immediately accessible by id
        return Controls.createRepeatIterationTree(containingDocument, controlIndex, repeatControl, iterationIndex);
    }

    public void initializeSubTree(XFormsContainerControl containerControl, boolean includeCurrent) {

        // Gather all control ids and controls
        final Map<String, XFormsControl> effectiveIdsToControls = new LinkedHashMap<String, XFormsControl>();
        Controls.visitControls((XFormsControl) containerControl, new Controls.XFormsControlVisitorAdapter() {
            @Override
            public boolean startVisitControl(XFormsControl control) {
                effectiveIdsToControls.put(control.getEffectiveId(), control);
                return true;
            }
        }, includeCurrent);

        // Dispatch initialization events, passing the ids only
        dispatchRefreshEvents(effectiveIdsToControls.keySet());
    }

    public void createAndInitializeDynamicSubTree(
        XBLContainer container,
        XFormsContainerControl containerControl,
        ElementAnalysis elementAnalysis,
        scala.Option<scala.collection.immutable.Map<String, ControlState>> state
    ) {

        Controls.createSubTree(container, controlIndex, containerControl, elementAnalysis, state);

        // NOTE: We dispatch refresh events for the subtree right away, by consistency with repeat iterations. But we
        // don't really have to do this, we could wait for the following refresh.
        initializeSubTree(containerControl, false);
    }

    /**
     * Index a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     */
    public void indexSubtree(XFormsContainerControl containerControl, boolean includeCurrent) {
        Controls.visitControls((XFormsControl) containerControl, new Controls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                // Index control
                controlIndex.indexControl(control);
                return true;
            }
        }, includeCurrent);
    }

    /**
     * Deindex a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     */
    public void deindexSubtree(XFormsContainerControl containerControl, boolean includeCurrent) {
        Controls.visitControls((XFormsControl) containerControl, new Controls.XFormsControlVisitorAdapter() {
            public boolean startVisitControl(XFormsControl control) {
                // Deindex control
                controlIndex.deindexControl(control);
                return true;
            }
        }, includeCurrent);
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
        // Delegate
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

    public Map<String, XFormsControl> getSelectFullControls() {
        // Delegate
        return controlIndex.getSelectFullControls();
    }
}
