///**
//*  Copyright (C) 2007 Orbeon, Inc.
//*
//*  This program is free software; you can redistribute it and/or modify it under the terms of the
//*  GNU Lesser General Public License as published by the Free Software Foundation; either version
//*  2.1 of the License, or (at your option) any later version.
//*
//*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//*  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//*  See the GNU Lesser General Public License for more details.
//*
//*  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
//*/
//package org.orbeon.saxon.expr
//
//import java.io.PrintStream
//import collection.mutable.{HashSet, HashMap}
//import org.orbeon.saxon.pattern.AnyNodeTest
//import orbeon.apache.xerces.impl.xpath.XPath.Axis
//
///**
//* A PathMap is a description of all the paths followed by an expression.
//* It is a set of trees. Each tree contains as its root an expression that selects
//* nodes without any dependency on the context. The arcs in the tree are axis steps.
//* So the expression doc('a.xml')/a[b=2]/c has a single root (the call on doc()), with
//* a single arc representing child::a, this leads to a node which has two further arcs
//* representing child::b and child::c. Because element b is atomized, there will also be
//* an arc for the step descendant::text() indicating the requirement to access the text
//* nodes of the element.
//*
//* <p>The current implementation works only for XPath 2.0 expressions (for example, constructs
//* like xsl:for-each-group are not handled.)</p>
//*
//* <p>This class, together with the overloaded method
//* {@link Expression#addToPathMap(PathMap, org.orbeon.saxon.expr.PathMap.PathMapNodeSet)} can be
//* seen as an implementation of the static path analysis algorithm given in section 4 of
//* <a href="http://www-db.research.bell-labs.com/user/simeon/xml_projection.pdf">A. Marian and J. Simeon,
//* Projecting XML Documents, VLDB 2003</a>.</p>
//*/
//class PathMap {
//
//    private var pathMapRoots: List[PathMapRoot] = Nil
//
//    // Transient stuff
//    private var pathsForVariables = new HashMap[Binding, PathMapNodeSet]
//
//    // Transient stuff
//    // TODO: refactor this
//    private var inScopeVariables: Map[String, PathMap] = null
//    private var inScopeAncestorContexts: Map[String, PathMap] = null
//
//    private var invalidated: Boolean = false    // whether during PathMap construction it is found that we cannot produce a meaningful result
//
//    /**
//     * Create the PathMap for an expression
//     * @param exp the expression whose PathMap is required
//     */
//    def this(exp: Expression) {
//        this()
//        val finalNodes = exp.addToPathMap(this, null)
//        updateFinalNodes(finalNodes)
//    }
//
//    // Copy constructor
//    def this(other: PathMap) {
//        this()
//        pathMapRoots = other.getPathMapRoots // TODO
//    }
//
//    // ORBEON
//    def this(exp: Expression, inScopeVariables: Map[String, PathMap], inScopeAncestorContexts: Map[String, PathMap]) {
//        this(exp)
//        setProperties(inScopeVariables, inScopeAncestorContexts)
//    }
//
//    /**
//     * A node in the path map. A node holds a set of arcs, each representing a link to another
//     * node in the path map.
//     */
//
//    // Local class
//    class PathMapNode {
//        var arcs: List[PathMapArc] = Nil
//
//        // Copy constructor
//        def this(other: PathMapNode) {
//            this()
//            arcs = other.arcs
//        }
//
//        /**
//         * Whether the node represents a value that is returnable as the result of the
//         * supplied expression, rather than merely a node that is visited en route
//         */
//        var returnable = false
//
//        /**
//         * Whether the typed value (or string value) of the node reached by this path
//         * will be required.
//         */
//        var atomized = false
//
//        /**
//         * Indicate that the path has unknown dependencies, typically because a node reached
//         * by the path is supplied as an argument to a user-defined function
//         */
//        var hasUnknownDependencies = false
//
//        /**
//         * Create a new arc
//         * @param step the AxisExpression representing this step
//         * @return the newly-constructed target of the new arc
//         */
//        def createArc(step: AxisExpression): PathMapNode = {
//            arcs find (_ == step) match {
//                case Some(arc) => arc.getTarget
//                case None =>
//                    val target = new PathMapNode
//                    arcs = new PathMapArc(step, target) :: arcs
//                    target
//            }
//        }
//
//        /**
//         * Create a new arc to an existing target
//         * @param step the AxisExpression representing this step
//         * @param target the target node of the new arc
//         */
//        def createArc(step: AxisExpression, target: PathMapNode) {
//            arcs find (a => a.step == step && a.getTarget == target) match {
//                case Some(arc) =>
//                    arc.getTarget.setReturnable(arc.getTarget.isReeturnable || target.isReturnable)
//                    if (target.isAtomized)
//                        arc.getTarget.setAtomized()
//                case None =>
//                    arcs = new PathMapArc(step, target) :: arcs
//            }
//        }
//
//        /**
//         * Get the arcs emanating from this node in the PathMap
//         * @return the arcs, each representing an AxisStep. The order of arcs in the array is undefined.
//         */
//        def getArcs: Array[PathMapArc] = Array(arcs: _*)
//
//        // ORBEON
//        def addArcs(newArcs: List[PathMapArc]) {
//            arcs = newArc ::: arcs
//        }
//
//        // ORBEON
//        def addArcs(newArcs: Array[PathMapArc]) {
//            arcs = List(newArcs: _*) ::: arcs
//        }
//
//        // ORBEON
//        def removeArc(arc: PathMapArc) {
//            arcs = arcs - arc
//        }
//
//        /**
//         * Indicate that the path has unknown dependencies, typically because a node reached
//         * by the path is supplied as an argument to a user-defined function
//         */
//        def setHasUnknownDependencies() {
//            hasUnknownDependencies = true
//        }
//
//        def setAtomized() {
//            atomized = true
//        }
//    }
//
//    /**
//     * A root node in the path map. A root node represents either (a) a subexpression that is the first step in
//     * a path expression, or (b) a subexpression that is not the first step in a path, but which returns nodes
//     * (for example, a call on the doc() function).
//     */
//    class PathMapRoot(val rootExpression: Expression) extends PathMapNode {
//
//        // Copy constructor
//        def this(other: PathMapRoot) {
//            this(other.rootExpression)
//        }
//    }
//
//    /**
//     * An arc joining two nodes in the path map. The arc has a target (destination) node, and is
//     * labelled with an AxisExpression representing a step in a path expression
//     */
//    class PathMapArc(val step: AxisExpression, val target: PathMapNode) extends Object with Cloneable {
//
//        // Copy constructor
//        def this(other: PathMapArc) {
//            this(other.step, if (other.target != null) new PathMapNode(other.target) else null)
//        }
//    }
//
//    /**
//     * A (mutable) set of nodes in the path map
//     */
//    class PathMapNodeSet(singleton: PathMapNode) {
//        private val set = new HashSet[PathMapNode]
//
//        set.add(singleton)
//
//        /**
//         * Create an arc from each node in this node set to a corresponding newly-created
//         * target node
//         * @param step the AxisExpression defining the transition
//         * @return the set of new target nodes
//         */
//        def createArc(step: AxisExpression): PathMapNodeSet =
//            new PathMapNodeSet(set map (_.createArc(step)))
//
//        /**
//         * Combine two node sets into one
//         * @param nodes the set of nodes to be added to this set
//         */
//        def addNodeSet(nodes: PathMapNodeSet) {
//            set.addAll(nodes)
//        }
//
//        /**
//         * Set the atomized property on all nodes in this nodeset
//         */
//        def setAtomized() {
//            set foreach (_.setAtomized())
//        }
//
//        /**
//         * Indicate that all the descendants of the nodes in this nodeset are required
//         */
//        def addDescendants() {
//            set foreach (_.createArc(new AxisExpression(Axis.DESCENDANT, AnyNodeTest.getInstance)))
//        }
//
//        /**
//         * Indicate that all the nodes have unknown dependencies
//         */
//        def setHasUnknownDependencies() {
//            set foreach (_.setHasUnknownDependencies())
//        }
//    }
//
//    // ORBEON
//    def setProperties(inScopeVariables: Map[String, PathMap], inScopeAncestorContexts: Map[String, PathMap]) {
//        this.inScopeVariables = inScopeVariables
//        this.inScopeAncestorContexts = inScopeAncestorContexts
//    }
//
//    /**
//     * Make a new root node in the path map. However, if there is already a root for the same
//     * expression, the existing root for that expression is returned.
//     * @param exp the expression represented by this root node
//     * @return the new root node
//     */
//    def makeNewRoot(exp: Expression): PathMapRoot = {
//        pathMapRoots find (_.rootExpression == exp) match {
//            case Some(root) => root
//            case None =>
//                val newRoot = new PathMapRoot(exp)
//                pathMapRoots = newRoot :: pathMapRoots
//                newRoot
//        }
//    }
//
//    /**
//     * Get all the root expressions from the path map
//     * @return an array containing the root expressions
//     */
//    def getPathMapRoots: Array[PathMapRoot] = Array(pathMapRoots: _*)
//
//    /**
//     * Register the path used when evaluating a given variable binding
//     * @param binding the variable binding
//     * @param nodeset the set of PathMap nodes reachable when evaluating that variable
//     */
//    def registerPathForVariable(binding: Binding, nodeset: PathMapNodeSet) {
//        pathsForVariables.put(binding, nodeset)
//    }
//
//    /**
//     * Get the path used when evaluating a given variable binding
//     * @param binding the variable binding
//     * @return the set of PathMap nodes reachable when evaluating that variable
//     */
//    def getPathForVariable(binding: Binding): PathMapNodeSet = {
//        // Check local variables
//        pathsForVariables.get(binding) match {
//            case Some(localResult) => localResult
//            case None =>
//                // ORBEON
//
//                // Check external variables
//                // Clone the PathMap first because the nodes returned must belong to this PathMap
//                inScopeVariables.get(binding.getVariableQName.getDisplayName) match {
//                    case Some(variablePathMap) =>
//                        val newPathMap = new PathMap(variablePathMap)
//                        addRoots(newPathMap.getPathMapRoot)
//                        newPathMap.findFinalNodes
//                    case None =>
//                        // TODO: when can this happen? it does seem to happen, e.g. w/ names like zz:zz1303689357 for internal LetExpression
//
//                        // Only invalidate if this is not a local variable, i.e. if this is a reference to an external variable. In
//                        // this case, there is no local slot number.
//                        if (binding.getLocalSlotNumber == -1)
//                            invalidated = true
//
//                        null
//                }
//        }
//    }
//
//    // ORBEON: this for xxf:context() and similar
//    def getPathForContext(contextStaticId: String): PathMapNodeSet = {
//        // Clone the PathMap first because the nodes returned must belong to this PathMap
//        inScopeAncestorContexts.get(contextStaticId) match {
//            case Some(contextPathMap) =>
//                val newPathMap = new PathMap(contextPathMap)
//                addRoots(newPathMap.getPathMapRoots)
//                newPathMap.findFinalNodes
//            case None =>
//                // Probably the id passed is invalid
//                invalidated = true
//                null
//        }
//    }
//
//    def isInvalidated: Boolean = invalidated
//
//    def setInvalidated(invalidated: Boolean) {
//        this.invalidated = invalidated
//    }
//
//    // Not used by Orbeon
//    def getContextRoot: PathMapRoot = null
//
//    // Not used by Orbeon
//    def getRootForDocument(requiredUri: String): PathMapRoot = null
//
//    // ORBEON START
//
//    def addRoots(roots: Array[PathMapRoot]) {
//        pathMapRoots = roots ::: pathMapRoots
//    }
//
//    def removeRoot(root: PathMapRoot) {
//        pathMapRoots = pathMapRoots - root
//    }
//
//    def findFinalNodes: PathMapNodeSet = {
//
//        val result = new PathMapNodeSet
//
//        def addNodes(node: PathMapNode) {
//            if (node.isReturnable)
//                result.add(node)
//
//            node.arcs foreach (addNodes(_.getTarget))
//        }
//
//        pathMapRoots foreach (addNodes(_))
//
//        result
//    }
//
//    private def clearFinalNodes() {
//        def clearNodes(node: PathMapNode) {
//            node.setReturnable(false)
//            node.arcs foreach (clearNodes(_.getTarget))
//        }
//
//        pathMapRoots foreach (clearNodes(_))
//    }
//
//    def updateFinalNodes(finalNodes: PathMapNodeSet) {
//        // We provide a new set of final nodes so clear the old ones
//        clearFinalNodes()
//        // Simply mark the new ones
//        if (finalNodes != null)
//            finalNodes foreach (_.setReturnable(true))
//    }
//
//    // ORBEON END
//
//    /**
//     * Display a printed representation of the path map
//     * @param out the output stream to which the output will be written
//     */
//    def diagnosticDump(out: PrintStream) {
//
//        def showStep(pad: String, step: Expression, targetNode: PathMapNode) {
//            out.println(pad + step +
//                    (if (targetNode.isAtomized) " @" else "") +
//                    (if (targetNode.isReturnable) " #" else "") +
//                    (if (targetNode.hasUnknownDependencies) " ...??" else ""))
//        }
//
//        def showArcs(node: PathMapNode, indent: Int) {
//            val pad = "                                           ".substring(0, indent)
//            for (arc <- node.arcs) {
//                showStep(pad, arc.step, arc.target)
//                showArcs(arc.target, indent + 2)
//            }
//        }
//
//        for (root <- pathMapRoots) {
//            out.println("\nROOT EXPRESSION " + i)
//            if (rootNode.hasUnknownDependencies)
//                out.println("  -- has unknown dependencies --")
//
//            val rootExpression = rootNode.rootExpression
//            out.println()
//            rootExpression.explain(out)
//            showStep(out, "", rootExpression, rootNode)
//            out.println("\nTREE FOR EXPRESSION " + i)
//            showArcs(out, rootNode, 2)
//        }
//    }
//}
//
////
//// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
//// you may not use this file except in compliance with the License. You may obtain a copy of the
//// License at http://www.mozilla.org/MPL/
////
//// Software distributed under the License is distributed on an "AS IS" basis,
//// WITHOUT WARRANTY OF ANY KIND, either express or implied.
//// See the License for the specific language governing rights and limitations under the License.
////
//// The Original Code is: all this file
////
//// The Initial Developer of the Original Code is Michael H. Kay.
////
//// Contributor(s):
////
//
