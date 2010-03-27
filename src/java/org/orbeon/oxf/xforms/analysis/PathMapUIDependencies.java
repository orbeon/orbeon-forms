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

import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.controls.ControlAnalysis;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * This implementation of UI dependencies uses static XPath analysis to provide more efficient reevaluation of bindings
 * and values.
 */
public class PathMapUIDependencies implements UIDependencies {

    private final XFormsContainingDocument containingDocument;
    private final XFormsStaticState staticState;

    private final Map<String, Set<NodeInfo>> modifiedNodes = new HashMap<String, Set<NodeInfo>>();
    private final Set<String> structuralChanges = new HashSet<String>();

    private boolean modifiedPathsSet;
    private Set<String> modifiedPaths = new HashSet<String>();

    // Cache to speedup checks on repeated items
    private Map<String, Boolean> modifiedBindingCache = new HashMap<String, Boolean>();
    private Map<String, Boolean> modifiedValueCache = new HashMap<String, Boolean>();

    private int bindingUpdateCount;
    private int valueUpdateCount;

    public PathMapUIDependencies(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
        this.staticState = containingDocument.getStaticState();
    }

    public void markValueChanged(XFormsModel model, NodeInfo nodeInfo) {

        // Caller must only call this for a mutable node belonging to the given model
        assert nodeInfo instanceof NodeWrapper;
        assert model.getInstanceForNode(nodeInfo).getModel(containingDocument) ==  model;

        Set<NodeInfo> nodeInfos = modifiedNodes.get(model.getPrefixedId());
        if (nodeInfos == null) {
            nodeInfos = new HashSet<NodeInfo>();
            modifiedNodes.put(model.getPrefixedId(), nodeInfos);
        }

        nodeInfos.add(nodeInfo);
    }

    public void markStructuralChange(XFormsModel model) {
        structuralChanges.add(model.getPrefixedId());
    }

    public void refreshDone() {

        containingDocument.getControls().getIndentedLogger().logDebug("dependencies", "refresh done",
                "bindings updated", Integer.toString(bindingUpdateCount),
                "values updated", Integer.toString(valueUpdateCount));

        modifiedNodes.clear();
        structuralChanges.clear();

        modifiedPathsSet = false;
        modifiedPaths.clear();
        modifiedBindingCache.clear();
        modifiedValueCache.clear();

        bindingUpdateCount = 0;
        valueUpdateCount = 0;
    }

    private Set<String> getModifiedPaths() {
        if (!modifiedPathsSet) {

            assert modifiedNodes.isEmpty();

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

    public boolean requireValueUpdate(String controlPrefixedId) {

        final boolean result;
        final Boolean cached = modifiedValueCache.get(controlPrefixedId);
        final XPathAnalysis valueAnalysis;
        if (cached != null) {
            result =  cached;
            valueAnalysis = result ? staticState.getControlAnalysis(controlPrefixedId).valueAnalysis : null;
        } else {
            final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId);
            valueAnalysis = controlAnalysis.valueAnalysis;
            if (valueAnalysis == null) {
                // Control does not have a value
                result = true;//TODO: should be able to return false here; change once markDirty is handled better
            } else if (!valueAnalysis.figuredOutDependencies) {
                // Value dependencies are unknown
                result = true;
            } else {
                // Value dependencies are known
                if (structuralChanges.isEmpty()) {
                    // No structural change
                    result = valueAnalysis.intersectsValue(getModifiedPaths());
                } else {
                    // Structural change
                    result = valueAnalysis.intersectsModels(structuralChanges);
                }
            }
            if (result && valueAnalysis != null) {
                containingDocument.getControls().getIndentedLogger().logDebug("dependencies", "value modified", "prefixed id", controlPrefixedId,
                        "XPath", valueAnalysis.xpathString);
            }

            modifiedValueCache.put(controlPrefixedId, result);
        }

        if (result && valueAnalysis != null) {// TODO: see above, check on valueAnalysis only because non-value controls still call this method
            valueUpdateCount++;
        }
        return result;
    }

    public boolean requireBindingUpdate(String controlPrefixedId) {

        final boolean result;
        final Boolean cached = modifiedBindingCache.get(controlPrefixedId);
        if (cached != null) {
            result = cached;
        } else {
            final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId);
            if (controlAnalysis.bindingAnalysis == null) {
                // Control does not have an XPath binding
                result = false;
            } else if (!controlAnalysis.bindingAnalysis.figuredOutDependencies) {
                // Binding dependencies are unknown
                result = true;
            } else {
                // Binding dependencies are known
                if (structuralChanges.isEmpty()) {
                    // No structural change
                    result = controlAnalysis.bindingAnalysis.intersectsBinding(getModifiedPaths());
                } else {
                    // Structural change
                    result = controlAnalysis.bindingAnalysis.intersectsModels(structuralChanges);
                }
            }
            if (result) {
                containingDocument.getControls().getIndentedLogger().logDebug("dependencies", "binding modified", "prefixed id", controlPrefixedId,
                        "XPath", controlAnalysis.bindingAnalysis.xpathString);
                bindingUpdateCount++;
            }

            modifiedBindingCache.put(controlPrefixedId, result);
        }

        if (result) {
            bindingUpdateCount++;
        }
        return result;
    }
}
