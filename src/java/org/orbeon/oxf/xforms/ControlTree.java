/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.control.*;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represents a tree of XForms controls.
 */
public class ControlTree implements Cloneable {

    // Top-level controls
    private List children;              // List<XFormsControl control>

    // Indexing of controls
    private Map effectiveIdsToControls; // Map<String effectiveId, XFormsControl control>
    private Map controlTypes;           // Map<String type, LinkedHashMap<String effectiveId, XFormsControl control>>

    private Map eventsToDispatch = new HashMap(); // Map<String effectiveId, EventSchedule eventSchedule>

    private boolean isBindingsDirty;    // whether the bindings must be reevaluated

    public ControlTree() {
    }

    /**
     * Build the entire tree of controls and associated information.
     *
     * @param pipelineContext   pipeline context
     * @param evaluateItemsets  whether to evaluate itemsets (true when restoring dynamic state only)
     */
    public ControlTree(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XFormsContainer rootContainer, boolean evaluateItemsets) {

        // Create the tree of controls
        final XXFormsRootControl rootControl = new XXFormsRootControl(containingDocument);// this is temporary and won't be stored
        XFormsControls.visitControlElementsHandleRepeat(pipelineContext, containingDocument, rootContainer,
                new CreateControlsListener(pipelineContext, this, rootControl, evaluateItemsets, false));

        // Detach all root XFormsControl
        final List rootChildren = rootControl.getChildren();
        if (rootChildren != null) {
            for (Iterator i = rootChildren.iterator(); i.hasNext();) {
                final XFormsControl currentXFormsControl = (XFormsControl) i.next();
                currentXFormsControl.detach();
            }
        }

        setChildren(rootChildren);
    }

    public Object clone() throws CloneNotSupportedException {

        // Clone this
        final ControlTree cloned = (ControlTree) super.clone();

        // Clone children if any
        if (children != null) {
            cloned.children = new ArrayList(children.size());
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();
                final XFormsControl currentClone = (XFormsControl) currentControl.clone();
                cloned.children.add(currentClone);
            }
        }

        // NOTE: The cloned tree does not make use of this so we clear it
        cloned.effectiveIdsToControls = null;
        cloned.controlTypes = null;
        cloned.eventsToDispatch = null;

        cloned.isBindingsDirty = false;

        return cloned;
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

        // Create iteration and set its binding context
        final XFormsRepeatIterationControl repeatIterationControl = new XFormsRepeatIterationControl(repeatControl.getContainer(), repeatControl, iterationIndex);
        repeatIterationControl.setBindingContext(bindingContext);

        // Index this control
        // NOTE: Must do after setting the context, so that relevance can be properly determined
        indexControl(repeatIterationControl, true);

        // Create the subtree
        XFormsControls.visitControlElementsHandleRepeat(pipelineContext, repeatControl, iterationIndex,
                new CreateControlsListener(pipelineContext, this, repeatIterationControl, false, true));

        return repeatIterationControl;
    }

    /**
     * Index a single controls.
     *
     * @param control           control to index
     * @param registerEvents    whether to register a relevance event if the control is bound and relevant
     */
    private void indexControl(XFormsControl control, boolean registerEvents) {
        // Remember by effective id
        if (effectiveIdsToControls == null)
            effectiveIdsToControls = new LinkedHashMap();// order is not strictly needed, but it can help debugging

        effectiveIdsToControls.put(control.getEffectiveId(), control);

        // Remember by control type (for certain controls we know we need)
        if (control instanceof XFormsUploadControl || control instanceof XFormsRepeatControl) {
            if (controlTypes == null)
                controlTypes = new HashMap();

            Map controlsMap = (Map) controlTypes.get(control.getName());
            if (controlsMap == null) {
                controlsMap = new LinkedHashMap();
                controlTypes.put(control.getName(), controlsMap);
            }

            controlsMap.put(control.getEffectiveId(), control);

        }

        // Add event if necessary
        if (registerEvents && control instanceof XFormsSingleNodeControl) {
            final NodeInfo boundNode = control.getBoundNode();
            if (boundNode != null && InstanceData.getInheritedRelevant(boundNode)) {
                // Control just came to existence and is now bound to a node and relevant
                eventsToDispatch.put(control.getEffectiveId(),
                        new XFormsModel.EventSchedule(control.getEffectiveId(), XFormsModel.EventSchedule.RELEVANT_BINDING, control));
            }
        }
    }

    /**
     * Deindex a single control.
     *
     * @param control           control to deindex
     * @param registerEvents    whether to register a relevance event if the control was bound and relevant
     */
    private void deindexControl(XFormsControl control, boolean registerEvents) {

        // Remove by effective id
        if (effectiveIdsToControls != null) {
            effectiveIdsToControls.remove(control.getEffectiveId());
        }

        // Remove by control type (for certain controls we know we need)
        if (control instanceof XFormsUploadControl || control instanceof XFormsRepeatControl) {
            if (controlTypes != null) {
                Map controlsMap = (Map) controlTypes.get(control.getName());
                if (controlsMap != null) {
                    controlsMap.remove(control.getEffectiveId());
                }
            }
        }

        // Add event if necessary
        if (registerEvents && control instanceof XFormsSingleNodeControl) {
            final NodeInfo boundNode = control.getBoundNode();
            if (boundNode != null && InstanceData.getInheritedRelevant(boundNode)) {
                // Control was bound to a node and relevant and is going out of existence
                eventsToDispatch.put(control.getEffectiveId(),
                        new XFormsModel.EventSchedule(control.getEffectiveId(), XFormsModel.EventSchedule.RELEVANT_BINDING, control));
            }
        }
    }

    /**
     * Index a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     * @param registerEvents    whether to register a relevance event if the control is bound and relevant
     */
    public void indexSubtree(XFormsContainerControl containerControl, boolean includeCurrent, final boolean registerEvents) {
        visitChildrenControls(containerControl, includeCurrent, new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                // Index control
                indexControl(control, registerEvents);
            }
        });
    }

    /**
     * Deindex a subtree of controls. Also handle special relevance binding events.
     *
     * @param containerControl  container control to start with
     * @param includeCurrent    whether to index the container control itself
     * @param registerEvents    whether to register a relevance event if the control was bound and relevant
     */
    public void deindexSubtree(XFormsContainerControl containerControl, boolean includeCurrent, final boolean registerEvents) {
        visitChildrenControls(containerControl, includeCurrent, new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                // Deindex control
                deindexControl(control, registerEvents);
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

    public Map getEventsToDispatch() {
        return eventsToDispatch;
    }

    public void clearEventsToDispatch() {
        this.eventsToDispatch = new HashMap();
    }

    public void setChildren(List children) {
        this.children = children;
    }

    public List getChildren() {
        return children;
    }

    public Map getEffectiveIdsToControls() {
        return effectiveIdsToControls;
    }

    public Map getInitialMinimalRepeatIdToIndex(XFormsStaticState staticState) {

        // TODO: for now, get the Map all the time, but should be optimized

        final Map repeatIdToIndex = new HashMap();

        visitControlsFollowRepeats(new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                if (control instanceof XFormsRepeatControl) {
                    // Found xforms:repeat
                    final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
                    repeatIdToIndex.put(repeatControl.getPrefixedId(), new Integer(((XFormsRepeatControl.XFormsRepeatControlLocal) repeatControl.getInitialLocal()).getIndex()));
                }
            }
        });

        addMissingRepeatIndexes(staticState, repeatIdToIndex);

        return repeatIdToIndex;
    }

    public Map getMinimalRepeatIdToIndex(XFormsStaticState staticState) {

        // TODO: for now, get the Map all the time, but should be optimized

        final Map repeatIdToIndex = new HashMap();

        visitControlsFollowRepeats(new XFormsControls.XFormsControlVisitorAdapter() {
            public void startVisitControl(XFormsControl control) {
                if (control instanceof XFormsRepeatControl) {
                    // Found xforms:repeat
                    final XFormsRepeatControl repeatControl = (XFormsRepeatControl) control;
                    repeatIdToIndex.put(repeatControl.getPrefixedId(), new Integer(repeatControl.getIndex()));
                }
            }
        });

        addMissingRepeatIndexes(staticState, repeatIdToIndex);

        return repeatIdToIndex;
    }

    private void addMissingRepeatIndexes(XFormsStaticState staticState, Map repeatIdToIndex) {
        final Map repeats = staticState.getRepeatControlInfoMap();
        if (repeats != null) {
            for (Iterator i = repeats.keySet().iterator(); i.hasNext();) {
                final String repeatPrefixedId = (String) i.next();
                if (repeatIdToIndex.get(repeatPrefixedId) == null) {
                    repeatIdToIndex.put(repeatPrefixedId, new Integer(0));
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

    private static void handleControl(XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener, List children) {
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();
                xformsControlVisitorListener.startVisitControl(currentControl);
                if (currentControl instanceof XFormsContainerControl)
                    handleControl(xformsControlVisitorListener, ((XFormsContainerControl) currentControl).getChildren());
                xformsControlVisitorListener.endVisitControl(currentControl);
            }
        }
    }

    public Map getUploadControls() {
        return (Map) ((controlTypes != null) ? controlTypes.get("upload") : null);
    }

    public Map getRepeatControls() {
        return (Map) ((controlTypes != null) ? controlTypes.get("repeat") : null);
    }

    /**
     * Visit all the XFormsControl elements by following the current repeat indexes.
     */
    private void visitControlsFollowRepeats(XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener) {
        visitControlsFollowRepeats(this.children, xformsControlVisitorListener);
    }

    private void visitControlsFollowRepeats(List children, XFormsControls.XFormsControlVisitorListener xformsControlVisitorListener) {
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();

                xformsControlVisitorListener.startVisitControl(currentControl);
                {
                    if (currentControl instanceof XFormsRepeatControl) {
                        // Follow repeat branch
                        final XFormsRepeatControl currentRepeatControl = (XFormsRepeatControl) currentControl;
                        final int currentRepeatIndex = currentRepeatControl.getIndex();

                        if (currentRepeatIndex > 0) {
                            final List newChildren = currentRepeatControl.getChildren();
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
     * Find an effective control id based on a control id, following the branches of the
     * current indexes of the repeat elements. age$age.1 - xforms-element-10
     */
    public String findEffectiveControlId(String sourceEffectiveId, String targetId) {
        // Don't iterate if we don't have controls
        if (this.children == null)
            return null;

        if (sourceEffectiveId != null && XFormsUtils.getEffectiveIdPrefix(sourceEffectiveId).length() > 0) {
            // The source is within a particular component, so search only within that component

            // Start from source control
            XFormsControl componentControl = (XFormsControl) effectiveIdsToControls.get(sourceEffectiveId);
            // Go down parents until component is found
            while (!(componentControl instanceof XFormsComponentControl)) {
                componentControl =  componentControl.getParent();
            }

            // Search from the root of the component
            return findEffectiveControlId(sourceEffectiveId, targetId, ((XFormsComponentControl) componentControl).getChildren());
        } else {
            // Search from the root
            return findEffectiveControlId(sourceEffectiveId, targetId, this.children);
        }
    }

    private String findEffectiveControlId(String sourceEffectiveId, String targetId, List children) {
        // TODO: use sourceId properly as defined in XForms 1.1 under 4.7.1 References to Elements within a repeat Element
        if (children != null && children.size() > 0) {
            for (Iterator i = children.iterator(); i.hasNext();) {
                final XFormsControl currentControl = (XFormsControl) i.next();
                final String staticControlId = currentControl.getId();

                if (targetId.equals(staticControlId)) {
                    // Found control
                    return currentControl.getEffectiveId();
                } else if (currentControl instanceof XFormsRepeatControl) {
                    // Handle repeat
                    final XFormsRepeatControl currentRepeatControl = (XFormsRepeatControl) currentControl;

                    // Find index and try to follow the current repeat index
                    final int index = currentRepeatControl.getIndex();
                    if (index > 0) {
                        final List newChildren = currentRepeatControl.getChildren();
                        if (newChildren != null && newChildren.size() > 0) {
                            final String result = findEffectiveControlId(sourceEffectiveId, targetId, Collections.singletonList(newChildren.get(index - 1)));
                            if (result != null)
                                return result;
                        }
                    }

                } else if (currentControl instanceof XFormsContainerControl) {
                    // Handle container control
                    final List newChildren = ((XFormsContainerControl) currentControl).getChildren();
                    if (newChildren != null && newChildren.size() > 0) {
                        final String result = findEffectiveControlId(sourceEffectiveId, targetId, newChildren);
                        if (result != null)
                            return result;
                    }
                }
            }
        }
        // Not found
        return null;
    }

    public static class CreateControlsListener implements XFormsControls.ControlElementVisitorListener {

        private XFormsControl currentControlsContainer;

        private final boolean evaluateItemsets;
        private final PipelineContext pipelineContext;
        private final ControlTree result;
        private final boolean registerEvents;

        public CreateControlsListener(PipelineContext pipelineContext, ControlTree result, XFormsControl rootControl, boolean evaluateItemsets, boolean registerEvents) {

            this.currentControlsContainer = rootControl;

            this.evaluateItemsets = evaluateItemsets;
            this.pipelineContext = pipelineContext;
            this.result = result;
            this.registerEvents = registerEvents;
        }

        public void startVisitControl(XFormsContainer container, Element controlElement, String effectiveControlId) {

            // Create XFormsControl with basic information
            final XFormsControl control = XFormsControlFactory.createXFormsControl(container, currentControlsContainer, controlElement, effectiveControlId);

            // Control type-specific handling
            if (control instanceof XFormsSelectControl || control instanceof XFormsSelect1Control) {
                // Handle xforms:itemset
                final XFormsSelect1Control select1Control = ((XFormsSelect1Control) control);
                // Evaluate itemsets only if specified (case of restoring dynamic state)
                if (evaluateItemsets)
                    select1Control.getItemset(pipelineContext, false);
            }

            // Set current binding for control element
            final XFormsContextStack.BindingContext currentBindingContext = container.getContextStack().getCurrentBindingContext();
            control.setBindingContext(currentBindingContext);

            // Index this control
            // NOTE: Must do after setting the context, so that relevance can be properly determined
            result.indexControl(control, registerEvents);

            // Add to current container control
            ((XFormsContainerControl) currentControlsContainer).addChild(control);

            // Current grouping control becomes the current controls container
            if (control instanceof XFormsContainerControl) {
                currentControlsContainer = control;
            }
        }

        public void endVisitControl(Element controlElement, String effectiveControlId) {
            final XFormsControl control = (XFormsControl) result.effectiveIdsToControls.get(effectiveControlId);
            if (control instanceof XFormsContainerControl) {
                // Notify container controls that all children have been added
                final XFormsContainerControl containerControl = (XFormsContainerControl) control;
                containerControl.childrenAdded();

                // Go back up to parent
                currentControlsContainer = currentControlsContainer.getParent();
            }
        }

        public boolean startRepeatIteration(XFormsContainer container, int iteration, String effectiveIterationId) {

            final XFormsRepeatIterationControl repeatIterationControl = new XFormsRepeatIterationControl(container, (XFormsRepeatControl) currentControlsContainer, iteration);

            ((XFormsContainerControl) currentControlsContainer).addChild(repeatIterationControl);
            currentControlsContainer = repeatIterationControl;

            // Set current binding for iteration
            final XFormsContextStack.BindingContext currentBindingContext = container.getContextStack().getCurrentBindingContext();
            repeatIterationControl.setBindingContext(currentBindingContext);

            // Index this control
            // NOTE: Must do after setting the context, so that relevance can be properly determined
            result.indexControl(repeatIterationControl, registerEvents);

            return true;
        }

        public void endRepeatIteration(int iteration) {
            currentControlsContainer = currentControlsContainer.getParent();
        }
    }
}
