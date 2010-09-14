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
package org.orbeon.oxf.xforms.analysis;

import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.controls.ControlAnalysis;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * This implementation of dependencies uses static XPath analysis to provide more efficient reevaluation of bindings
 * and values.
 */
public class PathMapXPathDependencies implements XPathDependencies {

    private final XFormsContainingDocument containingDocument;
    private final XFormsStaticState staticState;

    private IndentedLogger logger;

    private final Map<String, Set<NodeInfo>> modifiedNodes = new HashMap<String, Set<NodeInfo>>();
    private final Set<String> structuralChanges = new HashSet<String>();

    private boolean modifiedPathsSet;
    private Set<String> modifiedPaths = new HashSet<String>();

    // Cache to speedup checks on repeated items
    private Map<String, Boolean> modifiedBindingCache = new HashMap<String, Boolean>();
    private Map<String, Boolean> modifiedValueCache = new HashMap<String, Boolean>();

//    // Set of MIP nodes to check in the end
//    private Set<NodeInfo> touchedMIPNodes = new HashSet<NodeInfo>();

    private int bindingUpdateCount;
    private int valueUpdateCount;

    public PathMapXPathDependencies(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
        this.staticState = containingDocument.getStaticState();
        // Defer logger initialization as controls might not have been initialized at time this constructor is called. 
    }

    // Constructor for unit tests
    protected PathMapXPathDependencies(IndentedLogger logger, XFormsStaticState staticState) {
        this.containingDocument = null;
        this.logger = logger;
        this.staticState = staticState;
    }

    private IndentedLogger getLogger() {
        if (logger == null) {
            logger = containingDocument.getControls().getIndentedLogger();
        }
        return logger;
    }

    public void markValueChanged(XFormsModel model, NodeInfo nodeInfo) {

        // Caller must only call this for a mutable node belonging to the given model
        assert nodeInfo instanceof NodeWrapper;
        assert model.getInstanceForNode(nodeInfo).getModel(containingDocument) ==  model;

        if (!structuralChanges.contains(model.getPrefixedId())) {
            // Only care about path changes if there is no structural change for this model, since structural changes
            // for now disable any more subtle path-based check.
            Set<NodeInfo> nodeInfos = modifiedNodes.get(model.getPrefixedId());
            if (nodeInfos == null) {
                nodeInfos = new HashSet<NodeInfo>();
                modifiedNodes.put(model.getPrefixedId(), nodeInfos);
            }

            nodeInfos.add(nodeInfo);
        }
    }

    public void markStructuralChange(XFormsModel model) {
        structuralChanges.add(model.getPrefixedId());
    }

    // For unit tests
    public void markStructuralChange(String modelPrefixedId) {
        structuralChanges.add(modelPrefixedId);
    }

    public void refreshDone() {

        getLogger().logDebug("dependencies", "refresh done",
                "bindings updated", Integer.toString(bindingUpdateCount),
                "values updated", Integer.toString(valueUpdateCount));

        modifiedNodes.clear();
        structuralChanges.clear();

        modifiedPathsSet = false;
        modifiedPaths.clear();
        modifiedBindingCache.clear();
        modifiedValueCache.clear();

//        touchedMIPNodes.clear();

        bindingUpdateCount = 0;
        valueUpdateCount = 0;
    }

    // Protected to help with unit tests
    protected Set<String> getModifiedPaths() {
        if (!modifiedPathsSet) {

            if (modifiedNodes.size() != 0) {
                for (final Map.Entry<String, Set<NodeInfo>> entry: modifiedNodes.entrySet()) {
                    for (final NodeInfo node: entry.getValue()) {
                        final XFormsInstance instance = containingDocument.getInstanceForNode(node);
                        if (instance != null)
                            modifiedPaths.add(createNodePath(instance, node));
                    }
                }
            }
            modifiedPathsSet = true;
        }
        return modifiedPaths;
    }

    private static String createNodePath(XFormsInstance instance, NodeInfo node) {

        final StringBuilder sb = new StringBuilder();

        final List<NodeInfo> ancestorOrSelf = new ArrayList<NodeInfo>();
        {
            ancestorOrSelf.add(node);
            NodeInfo currentParent = node.getParent();
            while (currentParent != null) {
                ancestorOrSelf.add(currentParent);
                currentParent = currentParent.getParent();
            }
        }

        Collections.reverse(ancestorOrSelf);

        sb.append(XPathAnalysis.buildInstanceString(instance.getPrefixedId()));

        if (ancestorOrSelf.size() > 2) { // first is the document, second is the root element
            for (final NodeInfo currentNode: ancestorOrSelf.subList(2, ancestorOrSelf.size())) {
                sb.append('/');
                if (currentNode.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                    sb.append(currentNode.getFingerprint());
                } else if (currentNode.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE) {
                    sb.append('@');
                    sb.append(currentNode.getFingerprint());
                }
            }
        }

        return sb.toString();
    }

    public boolean requireBindingUpdate(String controlPrefixedId) {

        final boolean result;
        final Boolean cached = modifiedBindingCache.get(controlPrefixedId);
        if (cached != null) {
            result = cached;
        } else {
            final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId);
            if (controlAnalysis.getBindingAnalysis() == null) {
                // Control does not have an XPath binding
                result = false;
            } else if (!controlAnalysis.getBindingAnalysis().figuredOutDependencies) {
                // Binding dependencies are unknown
                result = true;
            } else {
                // Binding dependencies are known
                if (structuralChanges.isEmpty()) {
                    // No structural change, just test for paths
                    result = controlAnalysis.getBindingAnalysis().intersectsBinding(getModifiedPaths());
                } else {
                    // Structural change, also test for models
                    result = controlAnalysis.getBindingAnalysis().intersectsModels(structuralChanges)
                            || controlAnalysis.getBindingAnalysis().intersectsBinding(getModifiedPaths());
                }
            }
            if (result) {
                getLogger().logDebug("dependencies", "binding modified", "prefixed id", controlPrefixedId,
                        "XPath", controlAnalysis.getBindingAnalysis().xpathString);
            }

            modifiedBindingCache.put(controlPrefixedId, result);
        }

        if (result) {
            bindingUpdateCount++;
        }
        return result;
    }

    public boolean requireValueUpdate(String controlPrefixedId) {

        final boolean result;
        final Boolean cached = modifiedValueCache.get(controlPrefixedId);
        final XPathAnalysis valueAnalysis;
        if (cached != null) {
            result =  cached;
            valueAnalysis = result ? staticState.getControlAnalysis(controlPrefixedId).getValueAnalysis() : null;
        } else {
            final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId);
            valueAnalysis = controlAnalysis.getValueAnalysis();
            if (valueAnalysis == null) {
                // Control does not have a value
                result = true;//TODO: should be able to return false here; change once markDirty is handled better
            } else if (!valueAnalysis.figuredOutDependencies) {
                // Value dependencies are unknown
                result = true;
            } else {
                // Value dependencies are known
                if (structuralChanges.isEmpty()) {
                    // No structural change, just test for paths
                    result = valueAnalysis.intersectsValue(getModifiedPaths());
                } else {
                    // Structural change, also test for models
                    result = valueAnalysis.intersectsModels(structuralChanges)
                            || valueAnalysis.intersectsValue(getModifiedPaths());
                }
            }
            if (result && valueAnalysis != null) {
                getLogger().logDebug("dependencies", "value modified", "prefixed id", controlPrefixedId,
                        "XPath", valueAnalysis.xpathString);
            }

            modifiedValueCache.put(controlPrefixedId, result);
        }

        if (result && valueAnalysis != null) {// TODO: see above, check on valueAnalysis only because non-value controls still call this method
            valueUpdateCount++;
        }
        return result;
    }

    public boolean requireLHHAUpdate(XFormsConstants.LHHA lhha, String controlPrefixedId) {
        // TODO
//        final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId);
        return true;
    }

    public boolean requireBindCalculation(Model model, String instancePrefixedId) {
        return !model.figuredBindAnalysis || model.computedBindExpressionsInstances.contains(instancePrefixedId);
    }

    public boolean requireBindValidation(Model model, String instancePrefixedId) {
        return !model.figuredBindAnalysis || model.validationBindInstances.contains(instancePrefixedId);
    }

//    public void visitInstanceNode(XFormsModel model, NodeInfo nodeInfo) {
//        if (!touchedMIPNodes.contains(nodeInfo)) {
//            // First time this is called for a NodeInfo: keep old MIP values and remember NodeInfo
//            InstanceData.saveMIPs(nodeInfo);
//            touchedMIPNodes.add(nodeInfo);
//        }
//    }

    public void refreshStart() {
//        if (touchedMIPNodes.size() > 0) {
//            // All revalidations and recalculations are done, process information about nodes touched
//            for (final NodeInfo nodeInfo : touchedMIPNodes) {
//                if (InstanceData.getPreviousInheritedRelevant(nodeInfo) != InstanceData.getInheritedRelevant(nodeInfo)
//                        || InstanceData.getPreviousInheritedReadonly(nodeInfo) != InstanceData.getInheritedReadonly(nodeInfo)
//                        || InstanceData.getPreviousRequired(nodeInfo) != InstanceData.getRequired(nodeInfo)
//                        || InstanceData.getPreviousValid(nodeInfo) != InstanceData.getValid(nodeInfo)) {
//                    markMIPChanged(model, nodeInfo);
//                }
//            }
//        }
    }
}
