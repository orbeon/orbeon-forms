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
package org.orbeon.saxon.expr;

import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.Axis;
import org.orbeon.saxon.pattern.NodeKindTest;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.sxpath.XPathExpression;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * A PathMap is a description of all the paths followed by an expression.
 * It is a set of trees. Each tree contains as its root an expression that selects
 * nodes without any dependency on the context. The arcs in the tree are axis steps.
 * So the expression doc('a.xml')/a[b=2]/c has a single root (the call on doc()), with
 * a single arc representing child::a, this leads to a node which has two further arcs
 * representing child::b and child::c. Because element b is atomized, there will also be
 * an arc for the step descendant::text() indicating the requirement to access the text
 * nodes of the element.
 *
 * <p>The current implementation works only for XPath 2.0 expressions (for example, constructs
 * like xsl:for-each-group are not handled.)</p>
 */

public class PathMap implements Cloneable {

    private List<PathMapRoot> pathMapRoots = new ArrayList<PathMapRoot>(); // a list of PathMapRoot objects
//    private HashMap pathsForVariables = new HashMap();  // a map from a variable Binding to a PathMapNodeSet
    private Configuration config;

    /**
     * A node in the path map. A node holds a set of arcs, each representing a link to another
     * node in the path map.
     */

    public static class PathMapNode implements Cloneable {
        List<PathMapArc> arcs = new ArrayList<PathMapArc>();
        private boolean returnable;
        private boolean atomized;
        private boolean hasUnknownDependencies;

        @Override
        public PathMapNode clone() throws CloneNotSupportedException {
            final PathMapNode cloned = (PathMapNode) super.clone();

            if (arcs != null) {
                cloned.arcs = new ArrayList<PathMapArc>(arcs.size());
                for (final PathMapArc arc: arcs) {
                    cloned.arcs.add(arc.clone());
                }
            }

            return cloned;
        }

        /**
         * Create a node in the PathMap (initially with no arcs)
         */

        private PathMapNode() {}

        /**
         * Create a new arc
         * @param step the AxisExpression representing this step
         * @return the newly-constructed target of the new arc
         */

        public PathMapNode createArc(AxisExpression step) {
            PathMapNode target = new PathMapNode();
            PathMapArc arc = new PathMapArc(step, target);
            arcs.add(arc);
            return target;
        }

        /**
         * Create a new arc to an existing target
         * @param step the AxisExpression representing this step
         * @param target the target node of the new arc
         */

        public void createArc(AxisExpression step, PathMapNode target) {
            for (int i=0; i<arcs.size(); i++) {
                PathMapArc a = (PathMapArc)arcs.get(i);
                if (a.getStep().equals(step) && a.getTarget() == target) {
                    // TODO: if it's a different target, then merge the two targets into one. XMark Q8
                    a.getTarget().setReturnable(a.getTarget().isReturnable() || target.isReturnable());
                    if (target.isAtomized()) {
                        a.getTarget().setAtomized();
                    }
                    return;
                }
            }
            PathMapArc arc = new PathMapArc(step, target);
            arcs.add(arc);
        }

        /**
         * Get the arcs emanating from this node in the PathMap
         * @return the arcs, each representing an AxisStep. The order of arcs in the array is undefined.
         */

        public PathMapArc[] getArcs() {
            return (PathMapArc[])arcs.toArray(new PathMapArc[arcs.size()]);
        }

        public void addArcs(List<PathMapArc> arcs) {
            this.arcs.addAll(arcs);
        }

        /**
         * Indicate that the node represents a value that is returnable as the result of the
         * supplied expression, rather than merely a node that is visited en route
         * @param returnable true if the node represents a final result of the expression
         */

        public void setReturnable(boolean returnable) {
            this.returnable = true;
        }

        /**
         * Ask whether the node represents a value that is returnable as the result of the
         * supplied expression, rather than merely a node that is visited en route
         * @return true if the node represents a final result of the expression
         */

        public boolean isReturnable() {
            return returnable;
        }

        /**
         * Indicate that the typed value or string value of the node reached by this path
         * will be used.
         */

        public void setAtomized() {
            this.atomized = true;
        }

        /**
         * Ask whether the typed value (or string value) of the node reached by this path
         * will be required.
         * @return true if the typed value or string value of the node is required
         */

        public boolean isAtomized() {
            return atomized;
        }

        /**
         * Indicate that the path has unknown dependencies, typically because a node reached
         * by the path is supplied as an argument to a user-defined function
         */

        public void setHasUnknownDependencies() {
            hasUnknownDependencies = true;
        }

        /**
         * Ask whether the path has unknown dependencies, typically because a node reached
         * by the path is supplied as an argument to a user-defined function
         * @return true if the path has unknown dependencies
         */

        public boolean hasUnknownDependencies() {
            return hasUnknownDependencies;
        }
    }

    /**
     * A root node in the path map. A root node represents either (a) a subexpression that is the first step in
     * a path expression, or (b) a subexpression that is not the first step in a path, but which returns nodes
     * (for example, a call on the doc() function).
     */

    public static class PathMapRoot extends PathMapNode {

        private Expression rootExpression;
        private boolean isDownwardsOnly;

        /**
         * Create a PathMapRoot
         * @param root the expression at the root of a path
         */
        private PathMapRoot(Expression root) {
            this.rootExpression = root;
        }


        public Expression getRootExpression() {
            return rootExpression;
        }

        @Override
        public PathMapRoot clone() throws CloneNotSupportedException {
            return (PathMapRoot) super.clone();
        }
    }

    /**
     * An arc joining two nodes in the path map. The arc has a target (destination) node, and is
     * labelled with an AxisExpression representing a step in a path expression
     */

    public static class PathMapArc implements Cloneable {
        private PathMapNode target;
        private AxisExpression step;

        /**
         * Create a PathMapArc
         * @param step the axis step, represented by an AxisExpression
         * @param target the node reached by following this arc
         */
        private PathMapArc(AxisExpression step, PathMapNode target) {
            this.step = step;
            this.target = target;
        }

        /**
         * Get the AxisExpression associated with this arc
         * @return the AxisExpression
         */

        public AxisExpression getStep() {
            return step;
        }

        /**
         * Get the target node representing the destination of this arc
         * @return the target node
         */

        public PathMapNode getTarget() {
            return target;
        }

        @Override
        public PathMapArc clone() throws CloneNotSupportedException {
            final PathMapArc cloned = (PathMapArc) super.clone();
            if (target != null) {
                cloned.target = target.clone();
            }

            return cloned;
        }
    }

    /**
     * Create the PathMap for an expression
     * @param exp the expression whose PathMap is required
     */

    public PathMap(ComputedExpression exp, Configuration config) {
        this.config = config;

        PathMapNode finalNodes = exp.addToPathMap(this, null);
        // TODO: this is different from Saxon 9.1, might need to backport or upgrade
//        if (finalNodes != null) {
//            for (Iterator iter = finalNodes.iterator(); iter.hasNext(); ) {
//                PathMapNode node = (PathMapNode)iter.next();
//                node.setReturnable(true);
//            }
//        }
    }

    /**
     * Make a new root node in the path map
     * @param exp the expression represented by this root node
     * @return the new root node
     */

    public PathMapRoot makeNewRoot(Expression exp) {
        PathMapRoot root = new PathMapRoot(exp);
        pathMapRoots.add(root);
        return root;
    }

    /**
     * Get all the root expressions from the path map
     * @return an array containing the root expressions
     */

    public PathMapRoot[] getPathMapRoots() {
        return (PathMapRoot[])pathMapRoots.toArray(new PathMapRoot[pathMapRoots.size()]);
    }

    /**
     * Display a printed representation of the path map
     * @param out the output stream to which the output will be written
     */

    public void diagnosticDump(PrintStream out) {
        for (int i=0; i<pathMapRoots.size(); i++) {
            out.println("\nROOT EXPRESSION " + i);
            PathMapRoot mapRoot = (PathMapRoot)pathMapRoots.get(i);
            Expression exp = mapRoot.rootExpression;
            exp.display(0, out, config);
            out.println("\nTREE FOR EXPRESSION " + i);
            showArcs(out, mapRoot, 2);
        }
    }

    /**
     * Internal helper method called by diagnosticDump, to show the arcs emanating from a node
     * @param out the output stream
     * @param node the node in the path map whose arcs are to be displayed
     * @param indent the indentation level in the output
     */

    private void showArcs(PrintStream out, PathMapNode node, int indent) {
        String pad = "                                           ".substring(0, indent);
        List arcs = node.arcs;
        out.println(pad + "returnable: " + node.isReturnable());
        for (int i=0; i<arcs.size(); i++) {
            out.println(pad + ((PathMapArc)arcs.get(i)).step);
            showArcs(out, ((PathMapArc)arcs.get(i)).target, indent+2);
        }
    }

//    /**
//     * Register the path used when evaluating a given variable binding
//     * @param binding the variable binding
//     * @param nodeset the set of PathMap nodes reachable when evaluating that variable
//     */
//
//    public void registerPathForVariable(Binding binding, PathMapNode nodeset) {
//        pathsForVariables.put(binding, nodeset);
//    }
//
//    /**
//     * Get the path used when evaluating a given variable binding
//     * @param binding the variable binding
//     * @return the set of PathMap nodes reachable when evaluating that variable
//     */
//
//    public PathMapNode getPathForVariable(Binding binding) {
//        return (PathMapNode)pathsForVariables.get(binding);
//    }

    /**
     * Main method for testing
     * @param args Takes one argument, the XPath expression to be analyzed
     * @throws Exception
     */

    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        XPathEvaluator xpath = new XPathEvaluator(config);
        XPathExpression xpexp = xpath.createExpression(args[0]);
        Expression exp = xpexp.getInternalExpression();
        exp.display(0, System.err, config);
        PathMap initialPath = new PathMap((ComputedExpression)exp, config);
        initialPath.diagnosticDump(System.err);
    }

    /**
     * Given a PathMapRoot, simplify the tree rooted at this node so that
     * it only contains downwards selections: specifically, so that the only axes
     * used are child, attribute, namespace, and descendant. If the root expression
     * is a ContextItemExpression (that is, the path can start at any node) then it is rebased
     * to start at a root node, which means in effect that a path such as a/b/c is treated
     * as //a/b/c.
     * @param root the root of the path to be simplified
     * @return the path map root after converting the tree to use downwards axes only
     */

    public PathMapRoot reduceToDownwardsAxes(PathMapRoot root) {
        // If the path is rooted at an arbitrary context node, we rebase it to be rooted at the
        // document root. This involves changing the root to a RootExpression, and changing the axis
        // for initial steps from child to descendant where necessary
        if (root.isDownwardsOnly) {
            return root;
        }
        PathMapRoot newRoot = root;
        if (root.getRootExpression() instanceof ContextItemExpression) {
            RootExpression slash = new RootExpression();
            slash.setParentExpression(root.getRootExpression().getParentExpression());
            //root.setRootExpression(slash);
            newRoot = makeNewRoot(slash);
            for (int i=root.arcs.size()-1; i>=0; i--) {
                PathMapArc arc = (PathMapArc)root.arcs.get(i);
                byte axis = arc.getStep().getAxis();
                switch (axis) {
                    case Axis.ATTRIBUTE:
                    case Axis.NAMESPACE: {
                        AxisExpression newStep = new AxisExpression(Axis.DESCENDANT, NodeKindTest.ELEMENT);
                        PathMapNode newTarget = new PathMapNode();
                        newTarget.arcs.add(arc);
                        newRoot.createArc(newStep, newTarget);
                        break;
                    }
                    default: {
                        AxisExpression newStep = new AxisExpression(
                                Axis.DESCENDANT_OR_SELF, arc.getStep().getNodeTest());
                        newRoot.createArc(newStep, arc.getTarget());
                        break;
                    }
                }
            }
            for (int i=0; i<pathMapRoots.size(); i++) {
                if (pathMapRoots.get(i) == root) {
                    pathMapRoots.remove(i); break;
                }
            }
        }
        // Now process the tree of paths recursively, rewriting all axes in terms of downwards
        // selections, if necessary as downward selections from the root
        Stack<PathMapNode> nodeStack = new Stack<PathMapNode>();
        nodeStack.push(newRoot);
        reduceToDownwardsAxes(newRoot, nodeStack);
        newRoot.isDownwardsOnly = true;
        return newRoot;
    }

    /**
     * Supporting method for {@link #reduceToDownwardsAxes(org.orbeon.saxon.expr.PathMap.PathMapRoot)}
     * @param root the root of the path being simplified
     * @param nodeStack the sequence of nodes by which the current node in the path map was reached.
     * The node at the bottom of the stack is the root.
     */

    private void reduceToDownwardsAxes(PathMapRoot root, Stack<PathMapNode> nodeStack) {
        //PathMapArc lastArc = (PathMapArc)arcStack.peek();
        //byte lastAxis = lastArc.getStep().getAxis();
        PathMapNode node = nodeStack.peek();
        if (node.hasUnknownDependencies()) {
            root.setHasUnknownDependencies();
        }

        for (int i=0; i<node.arcs.size(); i++) {
            nodeStack.push(((PathMapArc)node.arcs.get(i)).getTarget());
            reduceToDownwardsAxes(root, nodeStack);
            nodeStack.pop();
        }

        for (int i=node.arcs.size()-1; i>=0; i--) {
            PathMapArc thisArc = (PathMapArc)node.arcs.get(i);
            AxisExpression axisStep = thisArc.getStep();
            PathMapNode grandParent =
                        (nodeStack.size() < 2 ? null : nodeStack.get(nodeStack.size()-2));
            byte lastAxis = -1;
            if (grandParent != null) {
                for (Iterator iter = grandParent.arcs.iterator(); iter.hasNext(); ) {
                    PathMapArc arc = ((PathMapArc)iter.next());
                    if (arc.getTarget() == node) {
                        lastAxis = arc.getStep().getAxis();
                    }
                }
            }
            switch (axisStep.getAxis()) {

                case Axis.ANCESTOR_OR_SELF:
                case Axis.DESCENDANT_OR_SELF:
                    if (axisStep.getNodeTest() == NodeKindTest.DOCUMENT) {
                        // This is typically an absolute path expression appearing within a predicate
                        node.arcs.remove(i);
                        for (Iterator iter = thisArc.getTarget().arcs.iterator(); iter.hasNext(); ) {
                            root.arcs.add((PathMapArc) iter.next());
                        }
                        break;
                    } else {
                        // fall through
                    }

                case Axis.ANCESTOR:
                case Axis.FOLLOWING:
                case Axis.PRECEDING: {
                    // replace the axis by a downwards axis from the root
                    if (axisStep.getAxis() != Axis.DESCENDANT_OR_SELF) {
                        AxisExpression newStep = new AxisExpression(Axis.DESCENDANT_OR_SELF, axisStep.getNodeTest());
                        newStep.setParentExpression(axisStep.getParentExpression());
                        root.createArc(newStep, thisArc.getTarget());
                        node.arcs.remove(i);
                    }
                    break;
                }

                case Axis.ATTRIBUTE:
                case Axis.CHILD:
                case Axis.DESCENDANT:
                case Axis.NAMESPACE:
                    // no action
                    break;

                case Axis.FOLLOWING_SIBLING:
                case Axis.PRECEDING_SIBLING: {
                    if (grandParent != null) {
                        AxisExpression newStep = new AxisExpression(lastAxis, axisStep.getNodeTest());
                        newStep.setParentExpression(axisStep.getParentExpression());
                        grandParent.createArc(newStep, thisArc.getTarget());
                        node.arcs.remove(i);
                        break;
                    } else {
                        AxisExpression newStep = new AxisExpression(Axis.CHILD, axisStep.getNodeTest());
                        newStep.setParentExpression(axisStep.getParentExpression());
                        root.createArc(newStep, thisArc.getTarget());
                        node.arcs.remove(i);
                        break;
                    }
                }
                case Axis.PARENT: {

                    if (lastAxis == Axis.CHILD || lastAxis == Axis.ATTRIBUTE || lastAxis == Axis.NAMESPACE) {
                        // ignore the parent step - it leads to somewhere we have already been.
                        // But it might become a returned node
                        if (node.isReturnable()) {
                            grandParent.setReturnable(true);
                        }
                        // any paths after the parent step need to be attached to the grandparent

                        PathMapNode target = thisArc.getTarget();
                        for (int a=0; a<target.arcs.size(); a++) {
                            grandParent.arcs.add(target.arcs.get(a));
                        }
                        node.arcs.remove(i);
                    } else if (lastAxis == Axis.DESCENDANT) {
                        AxisExpression newStep = new AxisExpression(Axis.DESCENDANT_OR_SELF, axisStep.getNodeTest());
                        newStep.setParentExpression(axisStep.getParentExpression());
                        if (thisArc.getTarget().arcs.isEmpty()) {
                            grandParent.createArc(newStep);
                        } else {
                            grandParent.createArc(newStep, thisArc.getTarget());
                        }
                        node.arcs.remove(i);
                    } else {
                        // don't try to be precise about a/b/../../c
                        AxisExpression newStep = new AxisExpression(Axis.DESCENDANT_OR_SELF, axisStep.getNodeTest());
                        newStep.setParentExpression(axisStep.getParentExpression());
                        if (thisArc.getTarget().arcs.isEmpty()) {
                            root.createArc(newStep);
                        } else {
                            root.createArc(newStep, thisArc.getTarget());
                        }
                        node.arcs.remove(i);
                    }
                    break;
                }
                case Axis.SELF: {
                    // This step can't take us anywhere we haven't been, so delete it
                    node.arcs.remove(i);
                    break;
                }
            }
        }

    }

    @Override
    public PathMap clone() {
        try {
            final PathMap cloned = (PathMap) super.clone();
            cloned.pathMapRoots = new ArrayList<PathMapRoot>(pathMapRoots.size());
            for (final PathMapRoot root: pathMapRoots) {
                cloned.pathMapRoots.add(root.clone());
            }
            return cloned;
        } catch (CloneNotSupportedException e) {
            return null;// won't happen
        }
    }

    public List<PathMapNode> findFinalNodes() {
        final List<PathMapNode> result = new ArrayList<PathMapNode>();
        for (final PathMapRoot root: pathMapRoots) {
            addNodes(result, root);
        }
        return result;
    }

    private void addNodes(List<PathMapNode> result, PathMapNode node) {
        if (node.arcs.isEmpty()) {
            result.add(node);
        } else {
            for (final PathMapArc arc: node.arcs) {
                addNodes(result, arc.getTarget());
            }
        }
    }

//    public List<PathMapArc> findFinalArcs() {
//        final List<PathMapArc> result = new ArrayList<PathMapArc>();
//        for (final PathMapRoot root: pathMapRoots) {
//            addArcs(result, root);
//        }
//        return result;
//    }
//
//    private void addArcs(List<PathMapArc> result, PathMapNode node) {
//        for (final PathMapArc arc: node.arcs) {
//            if (arc.target.arcs.isEmpty()) {
//                result.add(arc);
//            } else {
//                addArcs(result, arc.target);
//            }
//        }
//    }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Contributor(s):
//

