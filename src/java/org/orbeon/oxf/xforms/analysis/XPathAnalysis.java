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

import org.orbeon.oxf.xforms.analysis.controls.ControlAnalysis;
import org.orbeon.oxf.xforms.function.Instance;
import org.orbeon.saxon.expr.*;
import org.orbeon.saxon.om.NamePool;
import org.orbeon.saxon.pattern.NodeKindTest;

import java.io.PrintStream;
import java.util.*;

public class XPathAnalysis {

    public final String xpathString;
    public PathMap pathmap;
    public final int dependencies;

    final Set<String> paths = new HashSet<String>();

    public XPathAnalysis(String xpathString, PathMap pathmap, int dependencies) {
        this.xpathString = xpathString;
        this.pathmap = pathmap;
        this.dependencies = dependencies;
    }

    public boolean intersects(Set<String> touchedPaths) {
        // Return true if any path matches
        // TODO: for now naively just check exact paths
        for (final String path: paths) {
            if (touchedPaths.contains(path))
            return true;
        }
        return false;
    }

    public void rebase(XPathAnalysis parentAnalysis, Map<String, ControlAnalysis> inScopeVariables) {

        final Map<String, List<PathMap.PathMapNode>> finalNodes = new HashMap<String, List<PathMap.PathMapNode>>();

        // TODO: check returnable stuff

        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            final Expression rootExpression = root.getRootExpression();
            if (rootExpression instanceof VariableReference && !(rootExpression instanceof LocalVariableReference)) {
                final String variableName = getSaxonVariableName((VariableReference) rootExpression);
                final XPathAnalysis variableAnalysis = inScopeVariables.get(variableName).valueAnalysis;

                List<PathMap.PathMapNode> finals = getFinals(pathmap, finalNodes, variableAnalysis, variableName);
                rebase(finals, root);
                pathmap.removeRoot(root);
            } else if (rootExpression instanceof ContextItemExpression) {
                List<PathMap.PathMapNode> finals = getFinals(pathmap, finalNodes, parentAnalysis, ".");
                rebase(finals, root);
                pathmap.removeRoot(root);
            } else {
                // Keep root as is
            }
        }
    }

    private List<PathMap.PathMapNode> getFinals(PathMap result, Map<String, List<PathMap.PathMapNode>> finalNodes, XPathAnalysis baseAnalysis, String name) {
        List<PathMap.PathMapNode> finals = finalNodes.get(name);
        if (finals == null) {
            // Clone the parent and find its final nodes
            final PathMap cloneParent = baseAnalysis.pathmap.clone();
            finals = cloneParent.findFinalNodes();
            finalNodes.put(name, finals);

            // Add all the parent roots
            result.addRoots(cloneParent.getPathMapRoots());
        }
        return finals;
    }

    private void rebase(List<PathMap.PathMapNode> finals, PathMap.PathMapRoot root) {
        for (final PathMap.PathMapNode returnedNode: finals) {
            returnedNode.addArcs(Arrays.asList(root.getArcs()));
        }
    }

//    private PathMap rebase(XPathAnalysis parentAnalysis, List<PathMap.PathMapNode> roots) {
//
//        final List<PathMap.PathMapNode> returnedNodes = result.findFinalNodes();
//
//        for (final PathMap.PathMapNode returnedNode: returnedNodes) {
//            for (final PathMap.PathMapNode contextRoot: roots) {
//                returnedNode.addArcs(Arrays.asList(contextRoot.getArcs()));
//            }
//        }
//        return result;
//    }

    private String getSaxonVariableName(VariableReference variableReference) {
        return variableReference.getBinding().getVariableQName().getDisplayName();
    }

    public void processPaths() {
        final List<Expression> stack = new ArrayList<Expression>();
        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
            stack.add(root.getRootExpression());
            processNode(paths, stack, root);
            stack.remove(stack.size() - 1);
        }
    }

    public void processNode(Set<String> result, List<Expression> stack, PathMap.PathMapNode node) {
        if (node.getArcs().length == 0) {
            final StringBuilder sb = new StringBuilder();

            // Add only if last expression is not //text()
            final Expression lastExpression = stack.get(stack.size() - 1);
            if (!(lastExpression instanceof AxisExpression
                    && ((AxisExpression) lastExpression).getNodeTest() == NodeKindTest.TEXT)) {

                for (final Expression expression: stack) {
                    if (expression instanceof Instance) {
                        final Expression instanceNameExpression = ((Instance) expression).getArguments()[0];
                        if (instanceNameExpression instanceof StringLiteral) {
                            sb.append("instance('");
                            sb.append(((StringLiteral) instanceNameExpression).getStringValue());
                            sb.append("')");
                        } else {
                            //TODO: what to do here?
                        }
                    } else if (expression instanceof AxisExpression) {
                        if (sb.length() > 0)
                            sb.append('/');
                        final int fingerprint = ((AxisExpression) expression).getNodeTest().getFingerprint();
                        if (fingerprint != -1)
                            sb.append(NamePool.getDefaultNamePool().getDisplayName(fingerprint));
                        else
                            sb.append("/text()");
                    }
                }
                result.add(sb.toString());
            }
        } else {
            for (final PathMap.PathMapArc arc: node.getArcs()) {
                stack.add(arc.getStep());
                processNode(result, stack, arc.getTarget());
                stack.remove(stack.size() - 1);
            }
        }
    }

    public void dump(PrintStream out) {
        out.println("PATHMAP - expression: " + xpathString);

        for (final String path: paths) {
            out.println("  path: " + path);
        }

        pathmap.diagnosticDump(out);
        dumpDependencies(out);
//            if (instanceIds == null)
//                out.println("  EXPRESSION DEPENDS ON MORE THAN INSTANCES: " + xpathString);
//            else {
//                out.println("  EXPRESSION DEPENDS ON INSTANCES: " + xpathString);
//                for (final String instanceId: instanceIds) {
//                    out.println("    instance: " + instanceId);
//                }
//            }
    }

    public void dumpDependencies(PrintStream out) {
        if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
            out.println("  DEPENDS_ON_CONTEXT_ITEM");
        }
        if ((dependencies & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
            out.println("  DEPENDS_ON_CURRENT_ITEM");
        }
        if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT) != 0) {
            out.println("  DEPENDS_ON_CONTEXT_DOCUMENT");
        }
        if ((dependencies & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
            out.println("  DEPENDS_ON_LOCAL_VARIABLES");
        }
        if ((dependencies & StaticProperty.NON_CREATIVE) != 0) {
            out.println("  NON_CREATIVE");
        }
    }
}
