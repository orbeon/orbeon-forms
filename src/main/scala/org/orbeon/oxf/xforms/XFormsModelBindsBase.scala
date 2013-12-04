/**
* Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms

import collection.JavaConverters._
import collection.{mutable ⇒ m}
import java.{util ⇒ ju}
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.{Logging, XPath}
import org.orbeon.oxf.xforms.analysis.model.StaticBind.{ErrorLevel, ValidationLevel}
import org.orbeon.oxf.xforms.analysis.model.{StaticBind, Model}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.{BindIteration, BindNode, RuntimeBind}
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.saxon.dom4j.TypedNodeWrapper
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{ValueRepresentation, StructuredQName, Item}
import org.orbeon.saxon.value.SequenceExtent
import scala.util.control.NonFatal
import org.orbeon.oxf.util.ScalaUtils._

abstract class XFormsModelBindsBase(model: XFormsModel) extends Logging {

    type StaticMIP                = StaticBind#MIP
    type StaticXPathMIP           = StaticBind#XPathMIP
    type StaticConstraintXPathMIP = StaticBind#ConstraintXPathMIP
    type StaticTypeMIP            = StaticBind#TypeMIP

    private val containingDocument = model.containingDocument
    private val dependencies = containingDocument.getXPathDependencies
    private val staticModel = model.getStaticModel

    private implicit val logger = model.getIndentedLogger
    private implicit def reporter: XPath.Reporter = containingDocument.getRequestStats.addXPathStat

    protected val topLevelBinds  = new ju.ArrayList[RuntimeBind]
    val singleNodeContextBinds   = m.HashMap[String, RuntimeBind]()
    val iterationsForContextItem = m.HashMap[Item, List[BindIteration]]()

    protected def validateConstraint(bindNode: BindNode, invalidInstances: ju.Set[String]) {

        assert(bindNode.staticBind.constraintsByLevel.nonEmpty)

        // Don't try to apply constraints if it's not a node (it's set to null in that case)
        val currentNode = bindNode.node
        if (currentNode eq null)
            return

        // NOTE: 2011-02-03: Decided to allow setting a constraint on an element with children. Handles the case of
        // assigning validity to an enclosing element.
        // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

        if (InstanceData.getTypeValid(currentNode)) {
            // Then bother checking constraints
            for {
                (level, mips) ← bindNode.staticBind.constraintsByLevel
            } locally {
                if (dependencies.requireModelMIPUpdate(staticModel, bindNode.staticBind, Model.CONSTRAINT, level)) {
                    // Re-evaluate and set
                    val failedConstraints = failedConstraintMIPs(level, bindNode)
                    if (failedConstraints.nonEmpty)
                        bindNode.failedConstraints += level → failedConstraints
                    else
                        bindNode.failedConstraints -= level
                } else {
                    // Don't change list of failed constraints for this level
                }
            }
        } else {
            // Type is invalid and we don't want to risk running an XPath expression against an invalid node type
            // This is a common scenario, e.g. <xf:bind type="xs:integer" constraint=". > 0"/>
            // We clear all constraints in this case

            // TODO XXX: what about scenario:
            // 1. value type valid
            // 2. constraint fails
            // 3. value no longer type valid
            // 4. value type valid again but would cause constraint to succeed
            // 5. dependency not recomputed?

            bindNode.failedConstraints = BindNode.EmptyConstraints
        }

        // Remember invalid instances
        if (! bindNode.constraintsSatisfiedForLevel(ErrorLevel)) {
            val instanceForNodeInfo = containingDocument.getInstanceForNode(currentNode)
            invalidInstances.add(instanceForNodeInfo.getEffectiveId)
        }
    }

    private def evaluateSingleConstraintMIP(bindNode: BindNode, mip: StaticXPathMIP) =
        try {
            //println(s"evaluateConstraintMIPs: ${bind.getStaticId}, ${mip.compiledExpression.string}")
            evaluateBooleanExpression(bindNode, mip)
        } catch {
            case NonFatal(e) ⇒
                handleMIPXPathException(e, bindNode, mip, "evaluating XForms constraint bind")
                ! Model.DEFAULT_VALID
        }

    protected def failedConstraintMIPs(level: ValidationLevel, bindNode: BindNode): List[StaticConstraintXPathMIP] =
        for {
            mips      ← bindNode.staticBind.constraintsByLevel.get(level).toList
            mip       ← mips
            failed    = ! evaluateSingleConstraintMIP(bindNode, mip)
            if failed
        } yield
            mip

    protected def evaluateBooleanExpression(bindNode: BindNode, xpathExpression: StaticXPathMIP): Boolean = {
        // Setup function context
        // NOTE: When we implement support for allowing binds to receive events, source must be bind id.
        val functionContext = model.getContextStack.getFunctionContext(model.getEffectiveId, bindNode)
        XPath.evaluateSingle(bindNode.parentBind.items, bindNode.position, xpathExpression.compiledExpression, functionContext, variableResolver).asInstanceOf[Boolean]
    }

    protected def evaluateStringExpression(bindNode: BindNode, xpathExpression: StaticXPathMIP): String = {
        // Setup function context
        // NOTE: When we implement support for allowing binds to receive events, source must be bind id.
        val functionContext = model.getContextStack.getFunctionContext(model.getEffectiveId, bindNode)
        XPath.evaluateAsString(bindNode.parentBind.items, bindNode.position, xpathExpression.compiledExpression, functionContext, variableResolver)
    }

    protected def handleMIPXPathException(throwable: Throwable, bindNode: BindNode, xpathMIP: StaticXPathMIP, message: String) {
        Exceptions.getRootThrowable(throwable) match {
            case e: TypedNodeWrapper.TypedValueException ⇒
                // Consider validation errors as ignorable. The rationale is that if the function (the XPath
                // expression) works on inputs that are not valid (hence the validation error), then the function cannot
                // produce a meaningful result. We think that it is worth handling this condition slightly differently from
                // other dynamic and static errors, so that users can just write expression without constant checks with
                // `castable as` or `instance of`.
                debug("typed value exception", Seq("node name" → e.nodeName, "expected type" → e.typeName, "actual value" → e.nodeValue))
            case t ⇒
                // All other errors dispatch an event and will cause the usual fatal-or-not behavior
                val ve = OrbeonLocationException.wrapException(t,
                    new ExtendedLocationData(
                        bindNode.locationData,
                        description = Option(message),
                        params      = List("expression" → xpathMIP.compiledExpression.string),
                        element     = Some(bindNode.staticBind.element)))

                Dispatch.dispatchEvent(new XXFormsXPathErrorEvent(model, ve.getMessage, ve))
        }
    }

    // Implement "4.7.2 References to Elements within a bind Element":
    //
    // "When a source object expresses a Single Node Binding or Node Set Binding with a bind attribute, the IDREF of the
    // bind attribute is resolved to a target bind object whose associated nodeset is used by the Single Node Binding or
    // Node Set Binding. However, if the target bind element has one or more bind element ancestors, then the identified
    // bind may be a target element that is associated with more than one target bind object.
    //
    // If a target bind element is outermost, or if all of its ancestor bind elements have nodeset attributes that
    // select only one node, then the target bind only has one associated bind object, so this is the desired target
    // bind object whose nodeset is used in the Single Node Binding or Node Set Binding. Otherwise, the in-scope
    // evaluation context node of the source object containing the bind attribute is used to help select the appropriate
    // target bind object from among those associated with the target bind element.
    //
    // From among the bind objects associated with the target bind element, if there exists a bind object created with
    // the same in-scope evaluation context node as the source object, then that bind object is the desired target bind
    // object. Otherwise, the IDREF resolution produced a null search result."
    def resolveBind(bindId: String, contextItem: Item): RuntimeBind =
        singleNodeContextBinds.get(bindId) match {
            case Some(singleNodeContextBind) ⇒
                // This bind has a single-node context (incl. top-level bind), so ignore context item and just return the bind nodeset
                singleNodeContextBind
            case None ⇒
                // Nested bind: use context item

                // "From among the bind objects associated with the target bind element, if there exists a bind object
                // created with the same in-scope evaluation context node as the source object, then that bind object is
                // the desired target bind object. Otherwise, the IDREF resolution produced a null search result."
                val it =
                    for {
                        iterations ← iterationsForContextItem.get(contextItem).iterator
                        iteration  ← iterations
                        childBind  ← iteration.findChildBindByStaticId(bindId)
                    } yield
                        childBind

                it.headOption.orNull
        }

    protected def evaluateAndSetCustomMIPs(bindNode: BindNode): Unit =
        if (bindNode.staticBind.customMIPNameToXPathMIP.nonEmpty) // in most cases there are no custom MIPs
            for {
                (name, mips) ← bindNode.staticBind.customMIPNameToXPathMIP
                result = evaluateCustomMIP(bindNode, name)
                if result ne null
            } locally {
                bindNode.setCustom(name, result)
            }

    // NOTE: This only evaluates the first custom MIP of the given name associated with the bind. We do store multiple
    // ones statically, but don't have yet a solution to combine them. Should we string-join them?
    protected def evaluateCustomMIP(bindNode: BindNode, propertyName: String): String =
        try evaluateStringExpression(bindNode, bindNode.staticBind.customMIPNameToXPathMIP(propertyName).head)
        catch {
            case NonFatal(e) ⇒
                handleMIPXPathException(e, bindNode, bindNode.staticBind.getCalculate, "evaluating XForms custom bind")
                null
        }

    private val variableResolver =
        (variableName: StructuredQName, xpathContext: XPathContext) ⇒
            staticModel.bindsByName.get(variableName.getLocalName) match {
                case Some(targetStaticBind) ⇒
                    // Variable value is a bind nodeset to resolve

                    val localname = variableName.getLocalName

                    // Retrieve context
                    val contextBindNode   = XFormsFunction.context(xpathContext).data.asInstanceOf[BindNode]
                    val contextStaticBind = contextBindNode.staticBind

                    contextBindNode.ancestorOrSelfBindNodes find (_.staticBind.name == localname) match {
                        case Some(matchingBindNode) ⇒
                            // Optimization: The variable refers to the current bind node or an ancestor, in which case
                            // return as "closest" node the node for the current iteration.
                            matchingBindNode.node
                        case None ⇒
                            // General case where the target Not in the direct line of ancestors
                            // FIXME: can avoid reversing every time?
                            val staticContextAncestorOrSelf = contextStaticBind.ancestorOrSelfBinds.reverse
                            val staticTargetAncestorOrSelf  = targetStaticBind.ancestorOrSelfBinds.reverse

                            staticContextAncestorOrSelf.iterator zip staticTargetAncestorOrSelf.iterator collectFirst {
                                case (bindOnContextBranch, bindOnTargetBranch) if bindOnContextBranch ne bindOnTargetBranch ⇒
                                    // We found, from the root, the first static binds which are different. If both of
                                    // them are nested binds, they have a common parent. If at least one of them is a
                                    // top-level bind, then they don't have a common parent.
                                    
                                    // If they are nested binds and therefore have a common parent, we start search at
                                    // the closest common bind iteration. Otherwise, we start search at the top-level.
                                    
                                    // Then we search recursively toward bind leaves, following the path of target ids,
                                    // and for each concrete target return all the items found.

                                    def searchDescendantRuntimeBinds(binds: Seq[RuntimeBind], rootId: String, name: String): ValueRepresentation = {

                                        def nextNodes(binds: Iterator[RuntimeBind], path: List[String]): Iterator[Item] = {

                                            require(path.nonEmpty)

                                            val nextBind = {
                                                val nextId = path.head
                                                binds find (_.staticId == nextId) get
                                            }

                                            path.tail match {
                                                case Nil ⇒
                                                    // We are at a target: return all items
                                                    nextBind.items.asScala.iterator
                                                case pathTail ⇒
                                                    // We need to dig deeper to reach the target
                                                    for {
                                                        nextBindNode ← nextBind.bindNodes.iterator.asInstanceOf[Iterator[BindIteration]]
                                                        targetItem   ← nextNodes(nextBindNode.childrenBinds.iterator, pathTail)
                                                    } yield
                                                        targetItem
                                            }
                                        }

                                        val pathList      = staticTargetAncestorOrSelf map (_.staticId) dropWhile (rootId !=)
                                        val itemsIterator = nextNodes(binds.iterator, pathList).toArray

                                        new SequenceExtent(itemsIterator)
                                    }

                                    val startingRuntimeBinds =
                                        bindOnTargetBranch.parentBind match {
                                            case Some(commonParentBind) ⇒

                                                // Find corresponding runtime bind node
                                                contextBindNode.ancestorOrSelfBindNodes collectFirst {
                                                    case iteration: BindIteration if iteration.parentBind.staticId == commonParentBind.staticId ⇒
                                                        iteration.childrenBinds
                                                } get // this has to be there

                                            case None ⇒
                                                // No common parent, use top-level binds
                                                topLevelBinds.asScala
                                        }

                                    searchDescendantRuntimeBinds(startingRuntimeBinds, bindOnTargetBranch.staticId, localname)
                            } getOrElse {
                                throw new IllegalStateException
                            }
                    }

                case None ⇒
                    // Try top-level model variables
                    val modelVariables = model.getContextStack.getCurrentBindingContext.getInScopeVariables
                    val result = modelVariables.get(variableName.getLocalName)
                    // NOTE: With XPath analysis on, variable scope has been checked statically
                    if (result eq null)
                        throw new ValidationException("Undeclared variable in XPath expression: $" + variableName.getClarkName, staticModel.locationData)
                    result
            }
}
