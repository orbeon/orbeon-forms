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
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represents a tree of XForms controls.
 */
public class ControlTree implements Cloneable {

    private final boolean isNoscript;   // whether we are in noscript mode

    // Top-level controls
    private List<XFormsControl> children;                               // List<XFormsControl control>

    // Indexing of controls
    private Map<String, XFormsControl> effectiveIdsToControls;          // Map<String effectiveId, XFormsControl control>
    private Map<String, Map<String, XFormsControl>> controlTypes;       // Map<String type, LinkedHashMap<String effectiveId, XFormsControl control>>

    private Map<String, XFormsControls.EventSchedule> eventsToDispatch; // Map<String effectiveId, EventSchedule eventSchedule>

    private boolean isBindingsDirty;    // whether the bindings must be reevaluated

    public ControlTree(boolean isNoscript) {
        this.isNoscript = isNoscript;
        clearEventsToDispatch();
    }

    /**
     * Build the entire tree of controls and associated information.
     *
     * @param pipelineContext   pipeline context
     * @param evaluateItemsets  whether to evaluate itemsets (true when restoring dynamic state only)
     */
    public void initialize(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, XBLContainer rootContainer, boolean evaluateItemsets) {

        containingDocument.startHandleOperation("controls", "building");

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
                    = new CreateControlsListener(pipelineContext, this, rootControl, containingDocument.getSerializedControlStatesMap(pipelineContext), evaluateItemsets, false);
            XFormsControls.visitControlElementsHandleRepeat(pipelineContext, containingDocument, rootContainer, listener);

            // Detach all root XFormsControl
            if (children != null) {
                for (Iterator i = children.iterator(); i.hasNext();) {
                    final XFormsControl currentXFormsControl = (XFormsControl) i.next();
                    currentXFormsControl.detach();
                }
            }

            if (k == PROFILING_ITERATIONS - 1) {
                containingDocument.endHandleOperation(new String[] {
                        "controls updated", Integer.toString(listener.getUpdateCount()),
                        "repeat iterations", Integer.toString(listener.getIterationCount())
                });
            } else {
                // This is for profiling only
                children = null;
                effectiveIdsToControls = null;
                controlTypes = null;
                clearEventsToDispatch();
            }
        }
    }

    public Object clone() throws CloneNotSupportedException {

        // Clone this
        final ControlTree cloned = (ControlTree) super.clone();

        // Clone children if any
        if (children != null) {
            cloned.children = new ArrayList<XFormsControl>(children.size());
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
        final XFormsRepeatIterationControl repeatIterationControl = new XFormsRepeatIterationControl(repeatControl.getXBLContainer(), repeatControl, iterationIndex);
        repeatIterationControl.setBindingContext(pipelineContext, bindingContext);

        // Index this control
        // NOTE: Must do after setting the context, so that relevance can be properly determined
        // NOTE: We don't dispatch events to repeat iterations
        indexControl(repeatIterationControl, false);

        // Create the subtree
        XFormsControls.visitControlElementsHandleRepeat(pipelineContext, repeatControl, iterationIndex,
                new CreateControlsListener(pipelineContext, this, repeatIterationControl, null, false, true));

        return repeatIterationControl;
    }

    public void createSubTree(PipelineContext pipelineContext, XFormsContainerControl containerControl) {

        final XFormsControl control = (XFormsControl) containerControl;
        XFormsControls.visitControlElementsHandleRepeat(pipelineContext, containerControl,
                new CreateControlsListener(pipelineContext, this, control, null, false, true));
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
            effectiveIdsToControls = new LinkedHashMap<String, XFormsControl>();// order is not strictly needed, but it can help debugging

        effectiveIdsToControls.put(control.getEffectiveId(), control);

        // Remember by control type (for certain controls we know we need)
        if (mustMapControl(control)) {
            if (controlTypes == null)
                controlTypes = new HashMap<String, Map<String, XFormsControl>>();// no need for order here

            Map<String, XFormsControl> controlsMap = controlTypes.get(control.getName());
            if (controlsMap == null) {
                controlsMap = new LinkedHashMap<String, XFormsControl>(); // need for order here!
                controlTypes.put(control.getName(), controlsMap);
            }

            controlsMap.put(control.getEffectiveId(), control);
        }

        // Add event if necessary
        // NOTE: We don't dispatch events to repeat iterations
        if (registerEvents && control instanceof XFormsSingleNodeControl && !(control instanceof XFormsRepeatIterationControl)) {
            final NodeInfo boundNode = control.getBoundNode();
            if (boundNode != null && InstanceData.getInheritedRelevant(boundNode)) {
                // Control just came to existence and is now bound to a node and relevant
                eventsToDispatch.put(control.getEffectiveId(),
                        new XFormsControls.EventSchedule(control.getEffectiveId(), XFormsControls.EventSchedule.RELEVANT_BINDING, control));
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
        if (mustMapControl(control)) {
            if (controlTypes != null) {
                final Map controlsMap = (Map) controlTypes.get(control.getName());
                if (controlsMap != null) {
                    controlsMap.remove(control.getEffectiveId());
                }
            }
        }

        // Add event if necessary
        // NOTE: We don't dispatch events to repeat iterations
        if (registerEvents && control instanceof XFormsSingleNodeControl && !(control instanceof XFormsRepeatIterationControl)) {
            final NodeInfo boundNode = control.getBoundNode();
            if (boundNode != null && InstanceData.getInheritedRelevant(boundNode)) {
                // Control was bound to a node and relevant and is going out of existence
                eventsToDispatch.put(control.getEffectiveId(),
                        new XFormsControls.EventSchedule(control.getEffectiveId(), XFormsControls.EventSchedule.RELEVANT_BINDING, control));
            }
        }
    }

    private final boolean mustMapControl(XFormsControl control) {

        // Remember:
        // xforms:upload
        // xforms:repeat
        // xforms:select[@appearance = 'full'] in noscript mode
        return control instanceof XFormsUploadControl
                || control instanceof XFormsRepeatControl
                || (isNoscript && control instanceof XFormsSelectControl && ((XFormsSelectControl) control).isFullAppearance());
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

    public Map<String, XFormsControls.EventSchedule> getEventsToDispatch() {
        return eventsToDispatch;
    }

    public void clearEventsToDispatch() {
        this.eventsToDispatch = new LinkedHashMap<String, XFormsControls.EventSchedule>();
    }

    public void setChildren(List<XFormsControl> children) {
        this.children = children;
    }

    public List<XFormsControl> getChildren() {
        return children;
    }

    public Map<String, XFormsControl> getEffectiveIdsToControls() {
        return effectiveIdsToControls;
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
        final Map repeats = staticState.getRepeatControlInfoMap();
        if (repeats != null) {
            for (Iterator i = repeats.keySet().iterator(); i.hasNext();) {
                final String repeatPrefixedId = (String) i.next();
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

    public Map<String, XFormsControl> getUploadControls() {
        return (controlTypes != null) ? controlTypes.get("upload") : null;
    }

    public Map<String, XFormsControl> getRepeatControls() {
        return (controlTypes != null) ? controlTypes.get("repeat") : null;
    }

    /**
     * Return the list of xforms:select[@appearance = 'full'] in noscript mode.
     *
     * @return LinkedHashMap<String effectiveId, XFormsSelectControl control>
     */
    public Map<String, XFormsControl> getSelectFullControls() {
        return (controlTypes != null) ? controlTypes.get("select") : null;
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
    public String findEffectiveControlId(String sourceControlEffectiveId, String targetId) {
        // Don't iterate if we don't have controls
        if (this.children == null)
            return null;

        if (sourceControlEffectiveId != null && XFormsUtils.getEffectiveIdPrefix(sourceControlEffectiveId).length() > 0) {
            // The source is within a particular component, so search only within that component

            // Start from source control
            XFormsControl componentControl = (XFormsControl) effectiveIdsToControls.get(sourceControlEffectiveId);
            if (componentControl != null) {
                // Source is an existing control, go down parents until component is found
                while (componentControl != null && !(componentControl instanceof XFormsComponentControl)) {
                    componentControl =  componentControl.getParent();
                }
            } else {
                // Source is not a control, it is likely a model or nested model element

                // NOTE: This is kind of hacky due to the fact that we handle models differently from controls. In the
                // future this should be changed, see:
                // http://www.orbeon.com/forms/projects/xforms-model-scoping-rules
                // Also, this manipulation of prefix/suffix has the potential to be wrong with repeat iterations, e.g.
                // if the component is within repeats, and the control is within repeats within the component.
                final String componentEffectiveId = XFormsUtils.getEffectiveIdPrefixNoSeparator(sourceControlEffectiveId) + XFormsUtils.getEffectiveIdSuffixWithSeparator(sourceControlEffectiveId);
                componentControl = (XFormsControl) effectiveIdsToControls.get(componentEffectiveId);
            }

            // Can't keep going if the source control or one of its ancestors is not found
            if (componentControl == null)
                return null;

            // Search from the root of the component
            return findEffectiveControlId(sourceControlEffectiveId, targetId, ((XFormsComponentControl) componentControl).getChildren());
        } else {
            // Search from the root
            return findEffectiveControlId(sourceControlEffectiveId, targetId, this.children);
        }
    }

    private String findEffectiveControlId(String sourceControlEffectiveId, String targetId, List children) {
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
                            final String result = findEffectiveControlId(sourceControlEffectiveId, targetId, Collections.singletonList(newChildren.get(index - 1)));
                            if (result != null)
                                return result;
                        }
                    }

                } else if (currentControl instanceof XFormsContainerControl) {
                    // Handle container control
                    final List newChildren = ((XFormsContainerControl) currentControl).getChildren();
                    if (newChildren != null && newChildren.size() > 0) {
                        final String result = findEffectiveControlId(sourceControlEffectiveId, targetId, newChildren);
                        if (result != null)
                            return result;
                    }
                }
            }
        }
        // Not found
        return null;
    }

    /**
     * Listener used to create a tree of control from scratch. Used:
     *
     * o the first time controls are created
     * o for new repeat iterations
     * o for new non-relevant parts of a tree (under development as of 2009-02-24)
     */
    public static class CreateControlsListener implements XFormsControls.ControlElementVisitorListener {

        private XFormsControl currentControlsContainer;

        private final Map serializedControls;
        private final boolean evaluateItemsets;
        private final PipelineContext pipelineContext;
        private final ControlTree result;
        private final boolean registerEvents;

        private transient int updateCount;
        private transient int iterationCount;

        public CreateControlsListener(PipelineContext pipelineContext, ControlTree result, XFormsControl rootControl, Map serializedControlStateMap, boolean evaluateItemsets, boolean registerEvents) {

            this.currentControlsContainer = rootControl;

            this.serializedControls = serializedControlStateMap;
            this.evaluateItemsets = evaluateItemsets;
            this.pipelineContext = pipelineContext;
            this.result = result;
            this.registerEvents = registerEvents;
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
            control.setBindingContext(pipelineContext, currentBindingContext);

            // Index this control
            // NOTE: Must do after setting the context, so that relevance can be properly determined
            result.indexControl(control, registerEvents);

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
            final XFormsControl control = (XFormsControl) result.effectiveIdsToControls.get(effectiveControlId);
            if (control instanceof XFormsContainerControl) {
                // Notify container controls that all children have been added
                final XFormsContainerControl containerControl = (XFormsContainerControl) control;
                containerControl.childrenAdded(pipelineContext);

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
            repeatIterationControl.setBindingContext(pipelineContext, currentBindingContext);

            // Index this control
            // NOTE: Must do after setting the context, so that relevance can be properly determined
            // NOTE: We don't dispatch events to repeat iterations
            result.indexControl(repeatIterationControl, false);

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
}
