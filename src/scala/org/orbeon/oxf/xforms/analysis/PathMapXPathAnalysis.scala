    /**
 *  Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.saxon.expr._
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.dom4j.Element
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.oxf.xforms.function.Instance
import org.orbeon.oxf.xforms.function.xxforms.XXFormsInstance
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsContainingDocument, XFormsStaticState}
import org.orbeon.oxf.util.{PropertyContext, XPathCache}
import org.orbeon.oxf.xml.{XMLUtils, ContentHandlerHelper, NamespaceMapping}
import org.orbeon.saxon.om.Axis
import org.orbeon.saxon.expr.PathMap.PathMapArc
import collection.mutable.{LinkedHashSet, Stack}

import java.util.{Map => JMap, HashMap => JHashMap}
import org.orbeon.oxf.common.{OXFException, ValidationException}

class PathMapXPathAnalysis(val xpathString: String,
                           var pathmap: Option[PathMap], // this is used when used as variables and context and can be freed afterwards
                           val figuredOutDependencies: Boolean,
                           val valueDependentPaths: collection.Set[String],
                           val returnablePaths: collection.Set[String],
                           val dependentModels: collection.Set[String],
                           val dependentInstances: collection.Set[String],
                           val returnableInstances: collection.Set[String])
        extends XPathAnalysis {

    def combine(other: XPathAnalysis): XPathAnalysis = {

        if (!figuredOutDependencies || !other.figuredOutDependencies)
            // Either side is negative, return a constant negative with the combined expression
            NegativeAnalysis(XPathAnalysis.combineXPathStrings(xpathString, other.xpathString))
        else
            other match {
                case other: XPathAnalysis.ConstantXPathAnalysis =>
                    // Other is constant positive analysis, so just return this
                    this
                case other: PathMapXPathAnalysis =>
                    // Both are PathMap analysis so actually combine
                    new PathMapXPathAnalysis(XPathAnalysis.combineXPathStrings(xpathString, other.xpathString),
                        {
                            val newPathmap = pathmap.get.clone
                            newPathmap.addRoots(other.pathmap.get.clone.getPathMapRoots)
                            Some(newPathmap)
                        },
                        true,
                        valueDependentPaths ++ other.valueDependentPaths,
                        returnablePaths ++ other.returnablePaths,
                        dependentModels ++ other.dependentModels,
                        dependentInstances ++ other.dependentInstances,
                        returnableInstances ++ other.returnableInstances)
                case _ =>
                    throw new IllegalStateException // should not happen
            }
    }

    def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) {

        helper.startElement("analysis", Array("expression", xpathString, "analyzed", figuredOutDependencies.toString))

        def setToXML(set: collection.Set[String], enclosingElementName: String, elementName: String) {
            if (set.nonEmpty) {
                helper.startElement(enclosingElementName)
                for (value <- set)
                    helper.element(elementName, PathMapXPathAnalysis.getDisplayPath(value))
                helper.endElement()
            }
        }

        setToXML(valueDependentPaths, "value-dependent", "path")
        setToXML(returnablePaths, "returnable", "path")
        setToXML(dependentModels, "dependent-models", "model")
        setToXML(dependentInstances, "dependent-instances", "instance")
        setToXML(returnableInstances, "returnable-instances", "instance")

        helper.endElement()
    }

    override def freeTransientState(): Unit = pathmap = None
}

object PathMapXPathAnalysis {

    /**
     * Create a new XPathAnalysis based on an initial XPath expression.
     */
    def apply(staticState: XFormsStaticState, xpathString: String, namespaceMapping: NamespaceMapping,
              baseAnalysis: Option[XPathAnalysis], inScopeVariables: Map[String, VariableAnalysisTrait],
              pathMapContext: AnyRef, scope: XBLBindings#Scope, defaultInstancePrefixedId: Option[String],
              locationData: LocationData, element: Element): XPathAnalysis = {

        try {
            // Create expression
            val expression = XPathCache.createExpression(staticState.getXPathConfiguration, xpathString, namespaceMapping, XFormsContainingDocument.getFunctionLibrary)

            val stringPathmap = new PathMap(new StringLiteral(""))

            // In-scope variables
            val variablePathMaps = new JHashMap[String, PathMap]
            for ((name, variableControl) <- inScopeVariables; valueAnalysis = variableControl.getValueAnalysis; if valueAnalysis.isDefined && valueAnalysis.get.figuredOutDependencies)
                variablePathMaps.put(name, valueAnalysis match {
                    // Valid PathMap
                    case Some(analysis: PathMapXPathAnalysis) if analysis.figuredOutDependencies => analysis.pathmap.get
                    // Constant string
                    case Some(analysis) if analysis.figuredOutDependencies => stringPathmap
                    // Can't handle the other cases
                    case _ => null
                })

            def dependsOnFocus = (expression.getDependencies & StaticProperty.DEPENDS_ON_FOCUS) != 0

            val pathmap: Option[PathMap] =
                baseAnalysis match {
                    case Some(baseAnalysis: PathMapXPathAnalysis) if dependsOnFocus =>
                        // Expression depends on the context and has a context which has a pathmap

                        // We clone the base analysis and add to an existing PathMap
                        val clonedPathmap = baseAnalysis.pathmap.get.clone
                        clonedPathmap.setInScopeVariables(variablePathMaps)
                        clonedPathmap.setPathMapContext(pathMapContext)

                        val newNodeset = expression.addToPathMap(clonedPathmap, clonedPathmap.findFinalNodes)

                        if (!clonedPathmap.isInvalidated)
                            clonedPathmap.updateFinalNodes(newNodeset)

                        Some(clonedPathmap)
                    case Some(baseAnalysis) if dependsOnFocus =>
                        // Expression depends on the context but the context doesn't have a pathmap
                        //
                        // o if context is constant positive, context is a constant string
                        // o if context is constant negative, we can't handle this
                        if (baseAnalysis.figuredOutDependencies) Some(stringPathmap) else None
                    case _ =>
                        // Start with a new PathMap if:
                        // o we are at the top (i.e. does not have a context)
                        // o or the expression does not depend on the focus
                        // NOTE: We used to test on DEPENDS_ON_CONTEXT_ITEM above, but any use of the focus would otherwise create
                        // a root context expression in PathMap, which is not right.
                        Some(new PathMap(expression, variablePathMaps, pathMapContext))
                }

            pathmap match {
                case Some(pathmap) if !pathmap.isInvalidated =>

                    //            println("Expression:")
                    //            println(expression.toString())
                    //            println("PathMap before")
                    //            pathmap.diagnosticDump(System.out)

                    // Try to reduce ancestor axis before anything else
                    reduceAncestorAxis(pathmap)

                    //            println("PathMap after")
                    //            pathmap.diagnosticDump(System.out)

                    // We use LinkedHashSet to keep e.g. unit tests reproducible
                    val valueDependentPaths = new LinkedHashSet[String]
                    val returnablePaths = new LinkedHashSet[String]

                    val dependentModels = new LinkedHashSet[String]
                    val dependentInstances = new LinkedHashSet[String]
                    val returnableInstances = new LinkedHashSet[String]

                    // Process the pathmap to extract paths and other information useful for handling dependencies.
                    def processPaths(): Boolean = {
                        val stack = new Stack[Expression]

                        def createPath(node: PathMap.PathMapNode): Option[String] = {

                            // Resulting path
                            val sb = new StringBuilder

                            // Local class used as marker for a rewritten StringLiteral in an expression
                            class PrefixedIdStringLiteral(value: CharSequence) extends StringLiteral(value)

                            def addInstance(instancePrefixedId: String) {
                                sb.append(PathMapXPathAnalysis.buildInstanceString(instancePrefixedId))

                                val model = staticState.getModelByInstancePrefixedId(instancePrefixedId)
                                if (model eq null)
                                    throw new OXFException("Reference to invalid instance: " + instancePrefixedId)
                                dependentModels.add(model.prefixedId)
                                dependentInstances.add(instancePrefixedId)

                                if (node.isReturnable)
                                    returnableInstances.add(instancePrefixedId)
                            }

                            for (expression <- stack reverse) expression match {
                                case instanceExpression: FunctionCall
                                        if instanceExpression.isInstanceOf[Instance] || instanceExpression.isInstanceOf[XXFormsInstance] => {

                                    val hasParameter = instanceExpression.getArguments.nonEmpty
                                    if (!hasParameter) {
                                        // instance() resolves to default instance for scope
                                        defaultInstancePrefixedId match {
                                            case Some(defaultInstancePrefixedId) =>
                                                addInstance(defaultInstancePrefixedId)

                                                // Rewrite expression to add/replace its argument with a prefixed instance id
                                                instanceExpression.setArguments(Array(new PrefixedIdStringLiteral(defaultInstancePrefixedId)))
                                            case None =>
                                                // Model does not have a default instance
                                                // This is successful, but the path must not be added
                                                return Some("")
                                        }
                                    } else {
                                        val instanceNameExpression = instanceExpression.getArguments()(0)
                                        instanceNameExpression match {
                                            case stringLiteral: StringLiteral =>
                                                val originalInstanceId = stringLiteral.getStringValue
                                                val searchAncestors = expression.isInstanceOf[XXFormsInstance]

                                                // This is a trick: we use RewrittenStringLiteral as a marker so we don't rewrite an instance() StringLiteral parameter twice
                                                val alreadyRewritten = instanceNameExpression.isInstanceOf[PrefixedIdStringLiteral]

                                                val prefixedInstanceId =
                                                    if (alreadyRewritten)
                                                        // Parameter is already a prefixed id
                                                        originalInstanceId
                                                    else if (searchAncestors)
                                                        // xxforms:instance()
                                                        staticState.findInstancePrefixedId(scope, originalInstanceId)
                                                    else if (originalInstanceId.indexOf(XFormsConstants.COMPONENT_SEPARATOR) != -1)
                                                        // HACK: datatable e.g. uses instance(prefixedId)!
                                                        originalInstanceId
                                                    else
                                                        // Normal use of instance()
                                                        scope.getPrefixedIdForStaticId(originalInstanceId)

                                                if (prefixedInstanceId ne null) {
                                                    // Instance found
                                                    addInstance(prefixedInstanceId)

                                                    // If needed, rewrite expression to replace its argument with a prefixed instance id
                                                    if (!alreadyRewritten)
                                                        instanceExpression.setArguments(Array(new PrefixedIdStringLiteral(prefixedInstanceId)))
                                                } else {
                                                    // Instance not found (could be reference to unknown instance e.g. author typo!)
                                                    // TODO: must also catch case where id is found but does not correspond to instance
                                                    // TODO: warn in log
                                                    // This is successful, but the path must not be added
                                                    return Some("")
                                                }
                                            case _ => return None // non-literal instance name
                                        }
                                    }
                                }
                                case axisExpression: AxisExpression => axisExpression.getAxis match {
                                    case Axis.SELF => // NOP
                                    case axis @ (Axis.CHILD | Axis.ATTRIBUTE) =>
                                        // Child or attribute axis
                                        if (sb.nonEmpty)
                                            sb.append('/')
                                        val fingerprint = axisExpression.getNodeTest.getFingerprint
                                        if (fingerprint != -1) {
                                            if (axis == Axis.ATTRIBUTE)
                                                sb.append("@")
                                            sb.append(fingerprint)
                                        } else {
                                            // Unnamed node
                                            return None
                                        }
                                    case _ => return None // unhandled axis
                                }
                                case _ => return None // unhandled expression
                            }
                            Some(sb.toString)
                        }

                        def processNode(node: PathMap.PathMapNode, ancestorAtomized: Boolean = false): Boolean = {

                            if (node.getArcs.isEmpty || node.isReturnable || node.isAtomized || ancestorAtomized)
                                createPath(node) match {
                                    case Some(path) if path.nonEmpty =>
                                        // A path was created
                                        // NOTE: A same node can be both returnable AND atomized in a given expression
                                        if (node.isReturnable)
                                            returnablePaths.add(path)
                                        if (node.isAtomized)
                                            valueDependentPaths.add(path)
                                    case Some(_) => // NOP: don't add the path as this is not considered a dependency
                                    case None => return false // we can't deal with this path so stop here
                                }

                            // Process children nodes if any
                            for (arc <- node.getArcs) {
                                stack.push(arc.getStep)
                                if (!processNode(arc.getTarget, node.isAtomized))
                                    return false
                                stack.pop()
                            }

                            // We managed to deal with this path
                            true
                        }

                        for (root <- pathmap.getPathMapRoots) {
                            stack.push(root.getRootExpression)
                            if (!processNode(root))
                                return false
                            stack.pop()
                        }
                        true
                    }

                    if (processPaths())
                        // Success
                        new PathMapXPathAnalysis(xpathString, Some(pathmap), true, valueDependentPaths, returnablePaths, dependentModels, dependentInstances, returnableInstances)
                    else
                        // Failure
                        NegativeAnalysis(xpathString)

                case _ =>
                    // Failure
                    NegativeAnalysis(xpathString)
            }

        } catch {
            case e: Exception =>
                throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "analysing XPath expression",
                        element, "expression", xpathString))
        }
    }

    /**
     * Given a raw PathMap, try to reduce ancestor and other axes.
     */
    private def reduceAncestorAxis(pathmap: PathMap): Boolean = {

        // Utility class to hold node and ark as otherwise we can't go back to the node from an arc
        class NodeArc(val node: PathMap.PathMapNode, val arc: PathMap.PathMapArc)

        val stack = new Stack[NodeArc]

        // Return true if we moved an arc
        def reduceAncestorAxis(node: PathMap.PathMapNode): Boolean = {

            def moveArc(nodeArc: NodeArc, ancestorNode: PathMap.PathMapNode) {
                if (ancestorNode ne null) {

                    // Move arcs
                    ancestorNode.addArcs(nodeArc.arc.getTarget.getArcs)

                    // Remove current arc from its node as it's been moved
                    nodeArc.node.removeArc(nodeArc.arc)

                    if (nodeArc.arc.getTarget.isReturnable)
                        ancestorNode.setReturnable(true)

                    if (nodeArc.arc.getTarget.isAtomized)
                        ancestorNode.setAtomized()
                } else {
                    // Ignore for now
                }
            }

            def ancestorsWithFingerprint(nodeName: Int): Seq[PathMapArc] = {
                for {
                    nodeArc <- stack.elems.tail   // go from parent to root
                    e = nodeArc.arc.getStep
                    if e.getAxis == Axis.CHILD && e.getNodeTest.getFingerprint == nodeName
                } yield nodeArc.arc
            }

            // Process children nodes
            for (arc <- node.getArcs) {

                val newNodeArc = new NodeArc(node, arc)
                val step = arc.getStep

                // TODO: handle ANCESTOR_OR_SELF axis
                if (stack.nonEmpty) // all tests below assume at least a parent
                    step.getAxis match {
                        case Axis.ANCESTOR if step.getNodeTest.getFingerprint != -1 =>
                            // Found ancestor::foobar
                            val nodeName = step.getNodeTest.getFingerprint
                            val ancestorArcs = ancestorsWithFingerprint(nodeName)
                            if (ancestorArcs.nonEmpty) {
                                // There can be more than one ancestor with that fingerprint
                                for (ancestorArc <- ancestorArcs)
                                    moveArc(newNodeArc, ancestorArc.getTarget)
                                return true
                            } else {
                                // E.g.: /a/b/ancestor::c
                                // TODO
                            }
                        case Axis.PARENT => // don't test fingerprint as we could handle /a/*/..
                            // Parent axis
                            if (stack.size >= 1) {
                                val parentNodeArc = stack.top
                                moveArc(newNodeArc, parentNodeArc.node)
                                return true
                            } else {
                                // TODO: is this possible?
                            }
                        case Axis.FOLLOWING_SIBLING | Axis.PRECEDING_SIBLING =>
                            // Simplify preceding-sibling::foobar / following-sibling::foobar
                            val parentNodeArc = stack.top
                            if (stack.size > 2) {
                                val grandparentNodeArc = stack.tail.head

                                val newStep = new AxisExpression(parentNodeArc.arc.getStep.getAxis, step.getNodeTest)
                                newStep.setContainer(step.getContainer)
                                grandparentNodeArc.node.createArc(newStep, arc.getTarget)
                                node.removeArc(arc)
                            } else {
                                val newStep = new AxisExpression(Axis.CHILD, step.getNodeTest)
                                newStep.setContainer(step.getContainer)
                                parentNodeArc.node.createArc(newStep, arc.getTarget)
                                node.removeArc(arc)
                            }
                        case _ => // NOP
                    }

                stack.push(newNodeArc)
                if (reduceAncestorAxis(arc.getTarget))
                    return true
                stack.pop()
            }

            // We did not find a match
            false
        }

        for (root <- pathmap.getPathMapRoots) {
            // Apply as long as we find matches
            while (reduceAncestorAxis(root))
                stack.clear()
            stack.clear()
        }
        true
    }

    /**
     * Given an internal path, get a display path (for debugging/logging).
     */
    def getDisplayPath(path: String): String = {
        val pool = XPathCache.getGlobalConfiguration.getNamePool

        {
            for (token <- path split '/') yield {
                val (optionalAt, number) = if (token.startsWith("@")) ("@", token.substring(1)) else ("", token)

                optionalAt + {
                    try {
                        // Obtain QName
                        pool.getDisplayName(number.toInt)
                    } catch {
                        // Shouldn't happen, right? But since this is for debugging we output the token.
                        case e: NumberFormatException => token
                    }
                }
            }
        } mkString "/"
    }

    /**
     * Given a display path, get an internal path (for unit tests).
     */
    def getInternalPath(namespaces: JMap[String, String], path: String): String = {
        val pool = XPathCache.getGlobalConfiguration.getNamePool

        {
            for (token <- path split '/') yield {
                if (token.startsWith("instance(")) {
                    // instance(...)
                    token
                } else {
                    val (optionalAt, qName) = if (token.startsWith("@")) ("@", token.substring(1)) else ("", token)

                    optionalAt + {

                        val prefix = XMLUtils.prefixFromQName(qName)
                        val localname = XMLUtils.localNameFromQName(qName)

                        // Get number from pool based on QName
                        pool.allocate(prefix, namespaces.get(prefix), localname)
                    }
                }
            }
        } mkString "/"
    }

    def buildInstanceString(instanceId: String) = "instance('" + instanceId.replaceAll("'", "''") + "')"

//    def externalAnalysisExperiment(expression: Expression, pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
//
//        expression match {
//
//            case other =>
//                val dependsOnFocus = (other.getDependencies & StaticProperty.DEPENDS_ON_FOCUS) != 0
//                val attachmentPoint = pathMapNodeSet match {
//                    case null if dependsOnFocus =>
//                        // Result is new ContextItemExpression
//                        val contextItemExpression = new ContextItemExpression
//                        contextItemExpression.setContainer(expression.getContainer)
//                        new PathMap.PathMapNodeSet(pathMap.makeNewRoot(contextItemExpression))
//                    case _ =>
//                        // All other cases
//                        if (dependsOnFocus) pathMapNodeSet else null
//                }
//
//                val resultNodeSet = new PathMap.PathMapNodeSet
//                for (child <- other.iterateSubExpressions)
//                    resultNodeSet.addNodeSet(externalAnalysisExperiment(child.asInstanceOf[Expression], pathMap, attachmentPoint))
//
//                // Handle result differently if result type is atomic or not
//                other.getItemType(other.getExecutable.getConfiguration.getTypeHierarchy) match {
//                    case atomicType: AtomicType =>
//                        // NOTE: Thought it would be right to call setAtomized(), but it isn't! E.g. count() returns an atomic type,
//                        // but it doesn't mean the result of its argument expression is atomized. sum() does, but that's handled by
//                        // the atomization of the argument to sum().
//        //                resultNodeSet.setAtomized()
//                        // If expression returns an atomic value then any nodes accessed don't contribute to the result
//                        null
//                    case _ => resultNodeSet
//                }
//        }
//    }
}
