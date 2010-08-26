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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.controls.ControlAnalysis;
import org.orbeon.oxf.xforms.function.Instance;
import org.orbeon.oxf.xforms.function.xxforms.XXFormsInstance;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.Axis;
import org.orbeon.saxon.om.NamePool;

import java.util.*;

public class XPathAnalysis {

    public final XFormsStaticState staticState;
    public final String xpathString;
    public final PathMap pathmap;

    public final boolean figuredOutDependencies;

    public final Set<String> dependentPaths = new HashSet<String>();
    public final Set<String> returnablePaths = new HashSet<String>();

    public final Set<String> dependentModels = new HashSet<String>();
    public final Set<String> dependentInstances = new HashSet<String>();
    public final Set<String> returnableInstances = new HashSet<String>();

    public static XPathAnalysis CONSTANT_ANALYSIS = new XPathAnalysis(true);
    public static XPathAnalysis CONSTANT_NEGATIVE_ANALYSIS = new XPathAnalysis(false);

    private XPathAnalysis(boolean figuredOutDependencies) {
        this.staticState = null;
        this.xpathString = null;
        this.pathmap = null;
        this.figuredOutDependencies = figuredOutDependencies;
    }

    public XPathAnalysis(XFormsStaticState staticState, Expression expression, String xpathString,
                         XPathAnalysis baseAnalysis, Map<String, ControlAnalysis> inScopeVariables,
                         XBLBindings.Scope scope, String modelPrefixedId, String defaultInstancePrefixedId) {
        
        this.staticState = staticState;
        this.xpathString = xpathString;

        try {
            final Map<String, PathMap> variables = new HashMap<String, PathMap>();
            if (inScopeVariables != null) {
                for (final Map.Entry<String, ControlAnalysis> entry: inScopeVariables.entrySet()) {
                    variables.put(entry.getKey(), entry.getValue().valueAnalysis.pathmap);
                }
            }

            final PathMap pathmap;
            if (baseAnalysis == null) {
                // We are at the top, start with a new PathMap
                pathmap = new PathMap(expression, variables);
            } else {
                if ((expression.getDependencies() & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
                    // Expression depends on the context item

                    if (baseAnalysis.figuredOutDependencies) {
                        // We clone the base analysis and add to an existing PathMap
                        pathmap = baseAnalysis.pathmap.clone();
                        pathmap.setInScopeVariables(variables);
                        pathmap.updateFinalNodes(expression.addToPathMap(pathmap, pathmap.findFinalNodes()));
                    } else {
                        // Base analysis failed, we fail analysis too
                        this.pathmap = null;
                        this.figuredOutDependencies = false;

                        return;
                    }
                } else {
                    // Expression does not depend on the context item
                    pathmap = new PathMap(expression, variables);
                }
            }

            // Try to reduce ancestor axis
            reduceAncestorAxis(pathmap);

            this.pathmap = pathmap;

            // Produce resulting paths
            this.figuredOutDependencies = processPaths(scope, modelPrefixedId, defaultInstancePrefixedId);

        } catch (Exception e) {
            throw new OXFException("Exception while analyzing XPath expression: " + xpathString, e);
        }
    }

    public void combine(XPathAnalysis other) {

        assert figuredOutDependencies && other.figuredOutDependencies;

        pathmap.addRoots(other.pathmap.getPathMapRoots());
        dependentPaths.addAll(other.dependentPaths);
        returnablePaths.addAll(other.returnablePaths);
        dependentModels.addAll(other.dependentModels);
        dependentInstances.addAll(other.dependentInstances);
        returnableInstances.addAll(other.returnableInstances);
    }

    public boolean intersectsBinding(Set<String> touchedPaths) {
        // Return true if any path matches
        // TODO: for now naively just check exact paths
        for (final String path: dependentPaths) {
            if (touchedPaths.contains(path))
                return true;
        }
        return false;
    }

    public boolean intersectsValue(Set<String> touchedPaths) {
        // Return true if any path matches
        // TODO: for now naively just check exact paths
        for (final String path: returnablePaths) {
            if (touchedPaths.contains(path))
                return true;
        }
        for (final String path: dependentPaths) {
            if (touchedPaths.contains(path))
                return true;
        }
        return false;
    }

    public boolean intersectsModels(Set<String> touchedModels) {
        for (final String model: touchedModels) {
            if (dependentModels.contains(model))
                return true;
        }
        return false;
    }

    private boolean processPaths(XBLBindings.Scope scope, String modelPrefixedId, String defaultInstancePrefixedId) {
        final List<Expression> stack = new ArrayList<Expression>();
        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            stack.add(root.getRootExpression());
            final boolean success = processNode(stack, root, scope, modelPrefixedId, defaultInstancePrefixedId);
            if (!success)
                return false;
            stack.remove(stack.size() - 1);
        }
        return true;
    }

    public static String buildInstanceString(String instanceId) {
        return "instance('" + instanceId.replaceAll("'", "''") + "')";
    }

    private boolean processNode(List<Expression> stack, PathMap.PathMapNode node, XBLBindings.Scope scope, String modelPrefixedId, String defaultInstancePrefixedId) {
        boolean success = true;
        if (node.getArcs().length == 0 || node.isReturnable()) {

            final StringBuilder sb = new StringBuilder();
            success &= createPath(sb, stack, scope, modelPrefixedId, defaultInstancePrefixedId, node.isReturnable());
            if (success) {
                if (sb.length() > 0) {
                    // A path was created
                    final String s = sb.toString();
                    if (node.isReturnable()) {
                        returnablePaths.add(s);
                    } else {
                        dependentPaths.add(s);
                    }
                } else {
                    // NOP: don't add the path as this is not considered a dependency
                }
            } else {
                // We can't deal with this path
                return false;
            }
        }

        // Process children nodes
        if (node.getArcs().length > 0) {
            for (final PathMap.PathMapArc arc: node.getArcs()) {
                stack.add(arc.getStep());
                success &= processNode(stack, arc.getTarget(), scope, modelPrefixedId, defaultInstancePrefixedId);
                if (!success) {
                    return false;
                }
                stack.remove(stack.size() - 1);
            }
        }

        // We managed to deal with this path
        return true;
    }

    private boolean createPath(StringBuilder sb, List<Expression> stack, XBLBindings.Scope scope, String modelPrefixedId, String defaultInstancePrefixedId, boolean isReturnable) {
        boolean success = true;
        for (final Expression expression: stack) {
            if (expression instanceof Instance || expression instanceof XXFormsInstance) {
                // Instance function
                final FunctionCall instanceExpression = (FunctionCall) expression;

                final boolean hasParameter = instanceExpression.getArguments().length > 0;
                if (!hasParameter) {
                    // instance() resolves to default instance for scope
                    if (defaultInstancePrefixedId != null) {
                        sb.append(buildInstanceString(defaultInstancePrefixedId));
                        dependentModels.add(modelPrefixedId);
                        dependentInstances.add(defaultInstancePrefixedId);
                        if (isReturnable)
                            returnableInstances.add(defaultInstancePrefixedId);
                    } else {
                        // Model does not have a default instance
                        // This is successful, but the path must not be added
                        sb.setLength(0);
                        success = true;
                        break;
                    }
                } else {
                    final Expression instanceNameExpression = instanceExpression.getArguments()[0];
                    if (instanceNameExpression instanceof StringLiteral) {
                        final String originalInstanceId = ((StringLiteral) instanceNameExpression).getStringValue();
                        final boolean searchAncestors = expression instanceof XXFormsInstance;

                        final String prefixedInstanceId;
                        if (searchAncestors) {
                            // xxforms:instance()
                            prefixedInstanceId = staticState.findInstancePrefixedId(scope, originalInstanceId);
                        } else if (originalInstanceId.indexOf(XFormsConstants.COMPONENT_SEPARATOR) != -1) {
                            // HACK: datatable e.g. uses instance(prefixedId)!
                            prefixedInstanceId = originalInstanceId;
                        } else {
                            // Normal use of instance()
                            prefixedInstanceId = scope.getPrefixedIdForStaticId(originalInstanceId);
                        }

                        if (prefixedInstanceId != null) {
                            // Instance found
                            sb.append(buildInstanceString(prefixedInstanceId));
                            dependentModels.add(modelPrefixedId);
                            dependentInstances.add(prefixedInstanceId);
                            if (isReturnable)
                                returnableInstances.add(prefixedInstanceId);
                        } else {
                            // Instance not found (could be reference to unknown instance e.g. author typo!)
                            // TODO: must also catch case where id is found but does not correspond to instance
                            // TODO: warn in log
                            // This is successful, but the path must not be added
                            sb.setLength(0);
                            success = true;
                            break;
                        }
                    } else {
                        // Non-literal instance name
                        success = false;
                        break;
                    }
                }
            } else if (expression instanceof AxisExpression) {
                final AxisExpression axisExpression = (AxisExpression) expression;
                if (axisExpression.getAxis() == Axis.SELF) {
                    // NOP
                } else if (axisExpression.getAxis() == Axis.CHILD || axisExpression.getAxis() == Axis.ATTRIBUTE) {
                    // Child or attribute axis
                    if (sb.length() > 0)
                        sb.append('/');
                    final int fingerprint = axisExpression.getNodeTest().getFingerprint();
                    if (fingerprint != -1) {
                        if (axisExpression.getAxis() == Axis.ATTRIBUTE)
                            sb.append("@");
                        sb.append(fingerprint);
                    } else {
                        // Unnamed node
                        success = false;
                        break;
                    }
                } else {
                    // Unhandled axis
                    success = false;
                    break;
                }
            } else {
                success = false;
                break;
            }
        }
        return success;
    }

    public void toXML(PropertyContext propertyContext, ContentHandlerHelper helper) {

        helper.startElement("analysis", new String[] { "expression", xpathString, "analyzed", Boolean.toString(figuredOutDependencies) } );

        if (dependentPaths.size() > 0) {
            helper.startElement("dependent");
            for (final String path: dependentPaths) {
                helper.element("path", getDisplayPath(path));
            }
            helper.endElement();
        }

        if (returnablePaths.size() > 0) {
            helper.startElement("returnable");
            for (final String path: returnablePaths) {
                helper.element("path", getDisplayPath(path));
            }
            helper.endElement();
        }

        helper.endElement();
    }

    // For debugging/logging
    private static String getDisplayPath(String path) {
        final NamePool pool = XPathCache.getGlobalConfiguration().getNamePool();

        final StringTokenizer st = new StringTokenizer(path, "/");
        final StringBuilder sb = new StringBuilder();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.startsWith("@")) {
                sb.append('@');
                token = token.substring(1);
            }
            try {
                final int i = Integer.parseInt(token);
                sb.append(pool.getDisplayName(i));
            } catch (NumberFormatException e) {
                sb.append(token);
            }
            if (st.hasMoreTokens())
                sb.append('/');
        }
        return sb.toString();
    }

    // For unit tests
    public static String getInternalPath(Map<String, String> namespaces, String path) {
        final NamePool pool = XPathCache.getGlobalConfiguration().getNamePool();

        final StringTokenizer st = new StringTokenizer(path, "/");
        final StringBuilder sb = new StringBuilder();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();

            if (token.startsWith("instance(")) {
                // instance(...)
                sb.append(token);
            } else {
                if (token.startsWith("@")) {
                    // Attribute
                    sb.append('@');
                    token = token.substring(1);
                }

                // Element or attribute name
                final String prefix = XMLUtils.prefixFromQName(token);
                final String localname = XMLUtils.localNameFromQName(token);

                sb.append(pool.allocate(prefix, namespaces.get(prefix), localname));
            }
            // Separator
            if (st.hasMoreTokens())
                sb.append('/');
        }
        return sb.toString();
    }

    private static class NodeArc {
        public final PathMap.PathMapNode node;
        public final PathMap.PathMapArc arc;

        private NodeArc(PathMap.PathMapNode node, PathMap.PathMapArc arc) {
            this.node = node;
            this.arc = arc;
        }
    }

    private boolean reduceAncestorAxis(PathMap pathmap) {
        final List<NodeArc> stack = new ArrayList<NodeArc>();
        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            // Apply as long as we find matches
            while (reduceAncestorAxis(stack, root)) {
                stack.clear();
            }
            stack.clear();
        }
        return true;
    }

    private boolean reduceAncestorAxis(List<NodeArc> stack, PathMap.PathMapNode node) {
        // Process children nodes
        if (node.getArcs().length > 0) {
            for (final PathMap.PathMapArc arc: node.getArcs()) {

                final AxisExpression e = arc.getStep();
                // TODO: handle ANCESTOR_OR_SELF axis
                if (e.getAxis() == Axis.ANCESTOR && e.getNodeTest().getFingerprint() != -1) {
                    // Found ancestor::foobar
                    final int nodeName = e.getNodeTest().getFingerprint();
                    final PathMap.PathMapArc ancestorArc = ancestorWithFingerprint(stack, nodeName);
                    if (moveArc(stack, node, arc, ancestorArc))
                        return true;
                } else if (e.getAxis() == Axis.PARENT) {
                    // Parent axis
                    final NodeArc grandparentNodeArc = stack.get(stack.size() - 2);
                    if (moveArc(stack, node, arc, grandparentNodeArc.arc))
                        return true;
                } else if (e.getAxis() == Axis.FOLLOWING_SIBLING || e.getAxis() == Axis.PRECEDING_SIBLING) {
                    // Simplify preceding-sibling::foobar / following-sibling::foobar
                    final NodeArc parentNodeArc = stack.get(stack.size() - 1);
                    if (stack.size() > 2) {
                        final NodeArc grandparentNodeArc = stack.get(stack.size() - 2);

                        final AxisExpression newStep = new AxisExpression(parentNodeArc.arc.getStep().getAxis(), e.getNodeTest());
                        newStep.setContainer(e.getContainer());
                        grandparentNodeArc.node.createArc(newStep, arc.getTarget());
                        node.removeArc(arc);
                    } else {
                        final AxisExpression newStep = new AxisExpression(Axis.CHILD, e.getNodeTest());
                        newStep.setContainer(e.getContainer());
                        parentNodeArc.node.createArc(newStep, arc.getTarget());
                        node.removeArc(arc);
                    }
                }

                {
                    stack.add(new NodeArc(node, arc));
                    if (reduceAncestorAxis(stack, arc.getTarget())) {
                        return true;
                    }
                    stack.remove(stack.size() - 1);
                }
            }
        }

        // We did not find a match
        return false;
    }

    private boolean moveArc(List<NodeArc> stack, PathMap.PathMapNode node, PathMap.PathMapArc arc, PathMap.PathMapArc ancestorArc) {
        if (ancestorArc != null) {

            // Move arcs
            ancestorArc.getTarget().addArcs(arc.getTarget().getArcs());

            // Remove current arc from its node as it's been moved
            node.removeArc(arc);

            // Remove orphan nodes
            removeOrphanNodes(stack);

            return true;
        } else {
            // Ignore for now
        }
        return false;
    }

    private PathMap.PathMapArc ancestorWithFingerprint(List<NodeArc> stack, int nodeName) {
        Collections.reverse(stack);
        for (final NodeArc nodeArc: stack.subList(1, stack.size())) {
            final AxisExpression e = nodeArc.arc.getStep();

            if (e.getAxis() == Axis.CHILD && e.getNodeTest().getFingerprint() == nodeName) {
                Collections.reverse(stack);
                return nodeArc.arc;
            }
        }
        // Not found
        Collections.reverse(stack);
        return null;
    }

    private void removeOrphanNodes(List<NodeArc> stack) {
        Collections.reverse(stack);
        for (final NodeArc nodeArc: stack) {
            if (nodeArc.arc.getTarget().getArcs().length == 0) {
                nodeArc.node.removeArc(nodeArc.arc);
            }
        }

        Collections.reverse(stack);
    }
}
