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
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.controls.ControlAnalysis;
import org.orbeon.oxf.xforms.function.Instance;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.Axis;

import java.io.PrintStream;
import java.util.*;

public class XPathAnalysis {

    public final XFormsStaticState staticState;
    public final String xpathString;
    public final PathMap pathmap;

    final Set<String> dependentPaths = new HashSet<String>();
    final Set<String> returnablePaths = new HashSet<String>();

    final boolean figuredOutDependencies;

    public XPathAnalysis(XFormsStaticState staticState, Expression expression, String xpathString,
                         XPathAnalysis baseAnalysis, Map<String, ControlAnalysis> inScopeVariables,
                         String defaultInstancePrefixedId) {
        
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
                    // We clone and add to an existing PathMap
                    pathmap = baseAnalysis.pathmap.clone();
                    pathmap.setInScopeVariables(variables);
                    pathmap.updateFinalNodes(expression.addToPathMap(pathmap, pathmap.findFinalNodes()));
                } else {
                    // Expression does not depend on the context item
                    pathmap = new PathMap(expression, variables);
                }
            }

            // Try to reduce ancestor axis
            reduceAncestorAxis(pathmap);

            this.pathmap = pathmap;

            // Produce resulting paths
            figuredOutDependencies = processPaths(defaultInstancePrefixedId);

        } catch (Exception e) {
            throw new OXFException("Exception while analyzing XPath expression: " + xpathString, e);
        }
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

    private boolean processPaths(String defaultInstancePrefixedId) {
        // TODO: need to deal with namespaces!
        final List<Expression> stack = new ArrayList<Expression>();
        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            stack.add(root.getRootExpression());
            final boolean success = processNode(stack, root, defaultInstancePrefixedId);
            if (!success)
                return false;
            stack.remove(stack.size() - 1);
        }
        return true;
    }

    public static String buildInstanceString(String instanceId) {
        return "instance('" + instanceId.replaceAll("'", "''") + "')";
    }

    private boolean processNode(List<Expression> stack, PathMap.PathMapNode node, String defaultInstancePrefixedId) {
        boolean success = true;
        if (node.getArcs().length == 0 || node.isReturnable()) {

            final StringBuilder sb = new StringBuilder();

            for (final Expression expression: stack) {
                if (expression instanceof Instance) {
                    // Instance function
                    final Instance instanceExpression = ((Instance) expression);

                    final boolean hasParameter = instanceExpression.getArguments().length > 0;
                    if (!hasParameter) {
                        sb.append(buildInstanceString(defaultInstancePrefixedId));
                    } else {
                        final Expression instanceNameExpression = instanceExpression.getArguments()[0];
                        if (instanceNameExpression instanceof StringLiteral) {
                            sb.append(buildInstanceString(((StringLiteral) instanceNameExpression).getStringValue()));
                        } else {
                            // Non-literal instance name
                            success = false;
                            break;
                        }
                    }
                } else if (expression instanceof AxisExpression) {
                    final AxisExpression axisExpression = (AxisExpression) expression;
                    if (axisExpression.getAxis() == Axis.SELF) {
                        // Self axis
                        // NOP
                    } else if (axisExpression.getAxis() == Axis.CHILD) {
                        // Child axis
                        if (sb.length() > 0)
                            sb.append('/');
                        final int fingerprint = axisExpression.getNodeTest().getFingerprint();
                        sb.append(expression.getExecutable().getConfiguration().getNamePool().getDisplayName(fingerprint));
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
            if (success) {
                final String s = sb.toString();
                if (node.isReturnable()) {
                    returnablePaths.add(s);
                } else {
                    dependentPaths.add(s);
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
                success &= processNode(stack, arc.getTarget(), defaultInstancePrefixedId);
                if (!success) {
                    return false;
                }
                stack.remove(stack.size() - 1);
            }
        }

        // We managed to deal with this path
        return true;
    }

    public void dump(PrintStream out, int indent) {

        final String pad = "                                           ".substring(0, indent);

        out.println(pad + "PATHMAP - expression: " + xpathString);
        out.println(pad + "ok: " + figuredOutDependencies);
        out.println(pad + "dependent:");
        for (final String path: dependentPaths) {
            out.println(pad + "  path: " + path);
        }

        out.println(pad + "returnable:");
        for (final String path: returnablePaths) {
            out.println(pad + "  path: " + path);
        }
//
//        out.println(pad + "other:");
//        for (final String path: otherPaths) {
//            out.println(pad + "  path: " + path);
//        }

//        pathmap.diagnosticDump(out);
    }

//    public void dumpDependencies(PrintStream out) {
//        if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
//            out.println("  DEPENDS_ON_CONTEXT_ITEM");
//        }
//        if ((dependencies & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
//            out.println("  DEPENDS_ON_CURRENT_ITEM");
//        }
//        if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT) != 0) {
//            out.println("  DEPENDS_ON_CONTEXT_DOCUMENT");
//        }
//        if ((dependencies & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
//            out.println("  DEPENDS_ON_LOCAL_VARIABLES");
//        }
//        if ((dependencies & StaticProperty.NON_CREATIVE) != 0) {
//            out.println("  NON_CREATIVE");
//        }
//    }

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
                // TODO: handle ANCESTOR_OR_SELF and PARENT
                if (e.getAxis() == Axis.ANCESTOR && e.getNodeTest().getFingerprint() != -1) {
                    // Found ancestor::foobar

                    final int nodeName = e.getNodeTest().getFingerprint();
                    final PathMap.PathMapArc ancestorArc = ancestorWithFingerprint(stack, nodeName);
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
