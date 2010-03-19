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

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.controls.ControlAnalysis;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

public class UIDependencies {

    private final XFormsContainingDocument containingDocument;

    private final Map<String, Set<NodeInfo>> modifiedNodes = new HashMap<String, Set<NodeInfo>>();
    private final Set<String> structuralChanges = new HashSet<String>();

    private boolean modifiedPathsSet;
    private Set<String> modifiedPaths = new HashSet<String>();
    private int updateCount;

    public UIDependencies(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
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

        containingDocument.getControls().getIndentedLogger().logDebug("dependencies", "refresh done", "bindings updated", Integer.toString(updateCount));

        modifiedNodes.clear();
        structuralChanges.clear();

        modifiedPathsSet = false;
        modifiedPaths.clear();

        updateCount = 0;
    }

    public Set<String> getModifiedPaths() {
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

        sb.append("instance('");
        sb.append(instance.getPrefixedId());
        sb.append("')");

        if (ancestorOrSelf.size() > 2) { // first is the document, second is the root element
            for (final NodeInfo currentNode: ancestorOrSelf.subList(2, ancestorOrSelf.size())) {
                sb.append('/');
                if (currentNode.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                    sb.append(currentNode.getDisplayName());
                } else if (currentNode.getNodeKind() == org.w3c.dom.Document.ATTRIBUTE_NODE) {
                    sb.append('@');
                    sb.append(currentNode.getDisplayName());
                }
            }
        }

        return sb.toString();
    }

    public boolean requireBindingUpdate(XFormsStaticState staticState, Element currentControlElement,
                                        String controlPrefixedId, boolean contextUpdated) {

        final boolean result;
        final ControlAnalysis controlAnalysis = staticState.getControlAnalysis(controlPrefixedId);
        if (controlAnalysis.bindingAnalysis == null) {
            // Control does not have an XPath binding
            result = false;
        } else if (!controlAnalysis.bindingAnalysis.figuredOutDependencies) {
            // We couldn't figure out the dependencies for this binding
            result = true;
        } else {
            // Control has an XPath binding
            if (structuralChanges.isEmpty()) {
                // No structural change
                final XPathAnalysis analysis = staticState.getXPathAnalysis(controlPrefixedId);
                if (analysis != null) {
                    result = analysis.intersectsValue(getModifiedPaths());
                } else {
                    result = true;
                }
            } else {
                // Structural change
                // TODO: do model by model
                result = true;
            }
        }
        if (result) {
            containingDocument.getControls().getIndentedLogger().logDebug("dependencies", "binding modified", "prefixed id", controlPrefixedId,
                    "XPath", controlAnalysis.bindingAnalysis.xpathString);
            updateCount++;
        }

        return result;
    }
}
