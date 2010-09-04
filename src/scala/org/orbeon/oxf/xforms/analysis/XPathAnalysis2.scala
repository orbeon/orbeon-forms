/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import controls.SimpleAnalysis
import org.orbeon.saxon.expr._
import scala.collection.JavaConversions._
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.dom4j.Element
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.function.Instance
import org.orbeon.oxf.xforms.function.xxforms.XXFormsInstance
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsContainingDocument, XFormsStaticState}
import org.orbeon.oxf.util.{PropertyContext, XPathCache}
import org.orbeon.oxf.xml.{XMLUtils, ContentHandlerHelper, NamespaceMapping}
import org.orbeon.saxon.om.Axis
import org.orbeon.saxon.expr.PathMap.PathMapArc
import collection.mutable.{LinkedHashSet, Stack, Set}

class XPathAnalysis2(staticState: XFormsStaticState, val xpathString: String, namespaceMapping: NamespaceMapping,
                     baseAnalysis: XPathAnalysis, inScopeVariables: java.util.Map[String, SimpleAnalysis],
                     inScopeAncestorContexts: java.util.Map[String, SimpleAnalysis],
                     scope: XBLBindings#Scope, containingModelPrefixedId: String, defaultInstancePrefixedId: String,
                     locationData: LocationData, element: Element) extends XPathAnalysis {

    
    private val expression = XPathCache.createExpression(staticState.getXPathConfiguration, xpathString, namespaceMapping, XFormsContainingDocument.getFunctionLibrary)

    // TODO: Figure out way to make all fields vals

    // This is used during analysis and can be freed afterwards
    var pathmap: PathMap = _

    var figuredOutDependencies: Boolean = _

    // We use LinkedHashSet to keep e.g. unit tests reproducible
    var valueDependentPaths = new LinkedHashSet[String]
    var returnablePaths = new LinkedHashSet[String]

    var dependentModels = new LinkedHashSet[String]
    var dependentInstances = new LinkedHashSet[String]
    var returnableInstances = new LinkedHashSet[String]
    
    try {
        // In-scope variables
        val variables = new java.util.HashMap[String, PathMap]
        if (inScopeVariables != null) {
            for (entry <- inScopeVariables.entrySet) {
                val valueAnalysis = entry.getValue.getValueAnalysis
                variables.put(entry.getKey, if (valueAnalysis != null) valueAnalysis.pathmap else null)
            }
        }

        // In-scope ancestor contexts for xxf:context(), etc.
        val contexts = new java.util.HashMap[String, PathMap]
        if (inScopeAncestorContexts != null) {
            for (entry <- inScopeAncestorContexts.entrySet) {
                val bindingAnalysis = entry.getValue.getBindingAnalysis
                contexts.put(entry.getKey, if (bindingAnalysis != null && bindingAnalysis.figuredOutDependencies) bindingAnalysis.pathmap else null)
            }
        }

        val pathmap =
            if (baseAnalysis == null) {
                // We are at the top, start with a new PathMap
                new PathMap(expression, variables, contexts)
            } else {
                if ((expression.getDependencies & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
                    // Expression depends on the context item
    
                    if (baseAnalysis.figuredOutDependencies) {
                        // We clone the base analysis and add to an existing PathMap
                        val clonedPathmap = baseAnalysis.pathmap.clone
                        clonedPathmap.setProperties(variables, contexts)
    
                        val newNodeset = expression.addToPathMap(clonedPathmap, clonedPathmap.findFinalNodes)
    
                        if (!clonedPathmap.isInvalidated)
                            clonedPathmap.updateFinalNodes(newNodeset)

                        clonedPathmap
                    } else {
                        // Base analysis failed, we fail analysis too
                        this.pathmap = null
                        this.figuredOutDependencies = false
    
                        null
                    }
                } else {
                    // Expression does not depend on the context item
                    new PathMap(expression, variables, contexts)
                }
            }

        if (pathmap == null || pathmap.isInvalidated) {
            this.pathmap = null
            this.figuredOutDependencies = false
        } else {


//                System.out.println("xxx expressions")
//                System.out.println(expression.toString())
//                pathmap.diagnosticDump(System.out)

            // Try to reduce ancestor axis
            reduceAncestorAxis(pathmap)

//            System.out.println("xxx after")
//            pathmap.diagnosticDump(System.out)// xxx

            this.pathmap = pathmap

            // Produce resulting paths
            this.figuredOutDependencies = processPaths(scope, containingModelPrefixedId, defaultInstancePrefixedId)

            if (!this.figuredOutDependencies) {
                // This really shouldn't be touched if we can't figure out dependencies, but they are so clear them here
                valueDependentPaths.clear()
                returnablePaths.clear()
                dependentModels.clear()
                dependentInstances.clear()
                returnableInstances.clear()
            }
        }

    } catch {
        case e: Exception =>
            throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "analysing XPath expression",
                    element, "expression", xpathString))
    }

    def combine(other: XPathAnalysis) {

        assert (figuredOutDependencies && other.figuredOutDependencies)

        pathmap.addRoots(other.pathmap.getPathMapRoots)
        valueDependentPaths ++= other.valueDependentPaths
        returnablePaths ++= other.returnablePaths
        dependentModels ++= other.dependentModels
        dependentInstances ++= other.dependentInstances
        returnableInstances ++= other.returnableInstances
    }

    /**
     * Process the pathmap to extract paths and other information useful for handling dependencies.
     */
    private def processPaths(scope: XBLBindings#Scope, containingModelPrefixedId: String, defaultInstancePrefixedId: String): Boolean = {
        val stack = new Stack[Expression]

        def processNode(node: PathMap.PathMapNode, scope: XBLBindings#Scope, containingModelPrefixedId: String, defaultInstancePrefixedId: String, ancestorAtomized: Boolean): Boolean = {
            var success = true
            if (node.getArcs.length == 0 || node.isReturnable || node.isAtomized || ancestorAtomized) {

                val sb = new StringBuilder
                success &= createPath(sb, stack, scope, containingModelPrefixedId, defaultInstancePrefixedId, node)
                if (success) {
//                    System.out.println("yyy path " + sb.toString)

                    if (sb.nonEmpty) {
                        // A path was created

                        // NOTE: A same node can be both returnable AND atomized in a given expression
                        val s = sb.toString
                        if (node.isReturnable)
                            returnablePaths.add(s)
                        if (node.isAtomized)
                            valueDependentPaths.add(s)
                    } else {
                        // NOP: don't add the path as this is not considered a dependency
                    }
                } else {
                    // We can't deal with this path so stop here
                    return false
                }
            }

            // Process children nodes
            if (node.getArcs.nonEmpty) {
                for (arc <- node.getArcs) {
                    stack.push(arc.getStep)
                    success &= processNode(arc.getTarget, scope, containingModelPrefixedId, defaultInstancePrefixedId, node.isAtomized)
                    if (!success) {
                        return false
                    }
                    stack.pop()
                }
            }

            // We managed to deal with this path
            true
        }

        for (root <- pathmap.getPathMapRoots) {
            stack.push(root.getRootExpression)
            val success = processNode(root, scope, containingModelPrefixedId, defaultInstancePrefixedId, false)
            if (!success)
                return false
            stack.pop()
        }
        true
    }

    private def createPath(sb: StringBuilder, stack: Stack[Expression], scope: XBLBindings#Scope, containingModelPrefixedId: String,
                           defaultInstancePrefixedId: String, node: PathMap.PathMapNode ): Boolean = {

        // Local class used as marker for a rewritten StringLiteral in an expression
        class PrefixedIdStringLiteral(value: CharSequence) extends StringLiteral(value)

        for (expression <- stack reverse) {
            expression match {
                case instanceExpression: FunctionCall
                        if instanceExpression.isInstanceOf[Instance] || instanceExpression.isInstanceOf[XXFormsInstance] => {

                    val hasParameter = instanceExpression.getArguments.nonEmpty
                    if (!hasParameter) {
                        // instance() resolves to default instance for scope
                        if (defaultInstancePrefixedId != null) {
                            sb.append(XPathAnalysis2.buildInstanceString(defaultInstancePrefixedId))

                                // Static state doesn't yet know about the model if this expression is within the model. In this case, use containingModelPrefixedId
                                // TODO: not needed anymore because model now set before it is analyzed
                                val defaultModelForInstance = staticState.getModelByInstancePrefixedId(defaultInstancePrefixedId)
                                val dependentModelPrefixedId = if (defaultModelForInstance != null) defaultModelForInstance.prefixedId else containingModelPrefixedId
                                dependentModels.add(dependentModelPrefixedId)


                            dependentInstances.add(defaultInstancePrefixedId)
                            if (node.isReturnable)
                                returnableInstances.add(defaultInstancePrefixedId)

                            // Rewrite expression to add/replace its argument with a prefixed instance id
                            instanceExpression.setArguments(Array(new StringLiteral(defaultInstancePrefixedId)))
                        } else {
                            // Model does not have a default instance
                            // This is successful, but the path must not be added
                            sb.setLength(0)
                            return true
                        }
                    } else {
                        val instanceNameExpression = instanceExpression.getArguments()(0)
                        instanceNameExpression match {
                            case stringLiteral: StringLiteral => {
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

                                if (prefixedInstanceId != null) {
                                    // Instance found
                                    sb.append(XPathAnalysis2.buildInstanceString(prefixedInstanceId))

                                        // Static state doesn't yet know about the model if this expression is within the model. In this case, use containingModelPrefixedId
                                        // TODO: not needed anymore because model now set before it is analyzed
                                        val modelForInstance = staticState.getModelByInstancePrefixedId(prefixedInstanceId)
                                        val dependentModelPrefixedId = if (modelForInstance != null) modelForInstance.prefixedId else containingModelPrefixedId
                                        dependentModels.add(dependentModelPrefixedId)

                                    dependentInstances.add(prefixedInstanceId)
                                    if (node.isReturnable)
                                        returnableInstances.add(prefixedInstanceId)
                                    // If needed, rewrite expression to replace its argument with a prefixed instance id
                                    if (!alreadyRewritten)
                                        instanceExpression.setArguments(Array(new PrefixedIdStringLiteral(prefixedInstanceId)))
                                } else {
                                    // Instance not found (could be reference to unknown instance e.g. author typo!)
                                    // TODO: must also catch case where id is found but does not correspond to instance
                                    // TODO: warn in log
                                    // This is successful, but the path must not be added
                                    sb.setLength(0)
                                    return true
                                }
                            }
                            case _ => {
                                // Non-literal instance name
                                return false
                            }
                        }
                    }
                }
                case axisExpression: AxisExpression => {
                    axisExpression.getAxis match {
                        case Axis.SELF => // NOP
                        case axis @ (Axis.CHILD | Axis.ATTRIBUTE) => {
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
                                return false
                            }
                        }
                        case _ => return false // Unhandled axis
                    }
                }
                case _ => return false // unhandled expression
            }
        }
        true
    }

    def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) {

        helper.startElement("analysis", Array("expression", xpathString, "analyzed", figuredOutDependencies.toString))

        def setToXML(set: Set[String], enclosingElementName: String, elementName: String) {
            if (set.nonEmpty) {
                helper.startElement(enclosingElementName)
                for (value <- set)
                    helper.element(elementName, XPathAnalysis2.getDisplayPath(value))
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

    private def reduceAncestorAxis(pathmap: PathMap): Boolean = {

        // Utility class to hold node and ark as otherwise we can't go back to the node from an arc
        class NodeArc(val node: PathMap.PathMapNode, val arc: PathMap.PathMapArc)

        val stack = new Stack[NodeArc]

        // Return true if we moved an arc
        def reduceAncestorAxis(node: PathMap.PathMapNode): Boolean = {

            def moveArc(nodeArc: NodeArc, ancestorNode: PathMap.PathMapNode) {
                if (ancestorNode != null) {

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
                step.getAxis match {
                    case Axis.ANCESTOR if step.getNodeTest.getFingerprint != -1 => {
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
                    }
                    case Axis.PARENT => {// don't test fingerprint as we could handle /a/*/..
                        // Parent axis
                        if (stack.size >= 1) {
                            val parentNodeArc = stack.top
                            moveArc(newNodeArc, parentNodeArc.node)
                            return true
                        } else {
                            // TODO: is this possible?
                        }
                    }
                    case Axis.FOLLOWING_SIBLING | Axis.PRECEDING_SIBLING => {
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
                    }
                    case _ => // NOP
                }

                stack.push(newNodeArc)
                if (reduceAncestorAxis(arc.getTarget)) {
                    return true
                }
                stack.pop()
            }

            // We did not find a match
            false
        }

        for (root <- pathmap.getPathMapRoots) {
            // Apply as long as we find matches
            while (reduceAncestorAxis(root)) {
                stack.clear()
            }
            stack.clear()
        }
        true
    }

    def freeTransientState() {
        this.pathmap = null
    }
}

object XPathAnalysis2 {

    class ConstantXPathAnalysis(positive: Boolean) extends XPathAnalysis {

        def xpathString = null
        def pathmap = null

        def returnableInstances = Set.empty[String]
        def dependentInstances = Set.empty[String]
        def dependentModels = Set.empty[String]
        def returnablePaths = Set.empty[String]
        def valueDependentPaths = Set.empty[String]
        def figuredOutDependencies = positive

        def combine(other: XPathAnalysis) { throw new UnsupportedOperationException() }

        def freeTransientState = {}//NOP

        def toXML(propertyContext: PropertyContext, helper: ContentHandlerHelper) = {
            helper.element("analysis", Array("expression", xpathString, "analyzed", figuredOutDependencies.toString))
        }
    }

    val CONSTANT_ANALYSIS = new ConstantXPathAnalysis(true)
    val CONSTANT_NEGATIVE_ANALYSIS = new ConstantXPathAnalysis(false)

    // For debugging/logging
    def getDisplayPath(path: String): String = {
        val pool = XPathCache.getGlobalConfiguration.getNamePool

        {
            for (token <- path split '/') yield {
                val (optionalAt, number) = if (token.startsWith("@")) ("@", token.substring(1)) else ("", token)

                optionalAt + {
                    try {
                        // Obtain QName
                        pool.getDisplayName(token.toInt)
                    } catch {
                        // Shouldn't happen, right? But since this is for debugging we output the token.
                        case e: NumberFormatException => token
                    }
                }
            }
        } mkString "/"
    }

    // For unit tests
    def getInternalPath(namespaces: java.util.Map[String, String], path: String): String = {
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
}
