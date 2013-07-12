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
import java.util.{Set ⇒ JSet, List ⇒ JList, HashMap ⇒ JHashMap}
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.{Logging, XPath}
import org.orbeon.oxf.xforms.analysis.model.{StaticBind, Model}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.saxon.dom4j.TypedNodeWrapper
import org.orbeon.saxon.om.{StructuredQName, Item, NodeInfo}
import org.orbeon.saxon.value.SequenceExtent
import scala.util.control.NonFatal
import org.orbeon.oxf.xforms.analysis.model.StaticBind.{ErrorLevel, ValidationLevel}

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

    protected val singleNodeContextBinds = new JHashMap[String, RuntimeBind]
    protected val iterationsForContextNodeInfo = new JHashMap[Item, JList[RuntimeBind#BindIteration]]

    protected def validateConstraint(bind: RuntimeBind, position: Int, invalidInstances: JSet[String]) {

        assert(bind.staticBind.constraintsByLevel.nonEmpty)

        // Don't try to apply constraints if it's not a node (it's set to null in that case)
        val bindNode = bind.getBindNode(position)
        val currentNode = bindNode.node
        if (currentNode eq null)
            return

        // NOTE: 2011-02-03: Decided to allow setting a constraint on an element with children. Handles the case of
        // assigning validity to an enclosing element.
        // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

        if (InstanceData.getTypeValid(currentNode)) {
            // Then bother checking constraints
            for {
                (level, mips) ← bind.staticBind.constraintsByLevel
            } locally {
                if (dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.CONSTRAINT, level)) {
                    // Re-evaluate and set
                    val failedConstraints = failedConstraintMIPs(level, bind, position)
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

    private def evaluateSingleConstraintMIP(bind: RuntimeBind, mip: StaticXPathMIP, position: Int) =
        try {
            //println(s"evaluateConstraintMIPs: ${bind.getStaticId}, ${mip.compiledExpression.string}")
            evaluateBooleanExpression(bind.nodeset, position, mip)
        } catch {
            case NonFatal(e) ⇒
                handleMIPXPathException(e, bind, mip, "evaluating XForms constraint bind")
                ! Model.DEFAULT_VALID
        }

    protected def failedConstraintMIPs(level: ValidationLevel, bind: RuntimeBind, position: Int): List[StaticConstraintXPathMIP] =
        for {
            mips      ← bind.staticBind.constraintsByLevel.get(level).toList
            mip       ← mips
            failed    = ! evaluateSingleConstraintMIP(bind, mip, position)
            if failed
        } yield
            mip

    protected def evaluateBooleanExpression(nodeset: JList[Item], position: Int, xpathExpression: StaticXPathMIP): Boolean = {
        // Setup function context
        // NOTE: When we implement support for allowing binds to receive events, source must be bind id.
        val functionContext = model.getContextStack.getFunctionContext(model.getEffectiveId)
        try XPath.evaluateSingle(nodeset, position, xpathExpression.compiledExpression, functionContext, variableResolver).asInstanceOf[Boolean]
        finally model.getContextStack.returnFunctionContext()
    }

    protected def evaluateStringExpression(nodeset: JList[Item], position: Int, xpathExpression: StaticXPathMIP): String = {
        // Setup function context
        // NOTE: When we implement support for allowing binds to receive events, source must be bind id.
        val functionContext = model.getContextStack.getFunctionContext(model.getEffectiveId)
        try XPath.evaluateAsString(nodeset, position, xpathExpression.compiledExpression, functionContext, variableResolver)
        finally model.getContextStack.returnFunctionContext()
    }

    protected def handleMIPXPathException(throwable: Throwable, bind: RuntimeBind, xpathMIP: StaticXPathMIP, message: String) {
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
                        bind.staticBind.locationData,
                        description = Option(message),
                        params      = List("expression" → xpathMIP.compiledExpression.string),
                        element     = Some(bind.staticBind.element)))

                Dispatch.dispatchEvent(new XXFormsXPathErrorEvent(model, ve.getMessage, ve))
        }
    }

    def resolveBind(bindId: String, contextItem: Item): RuntimeBind = {
        val singleNodeContextBind = singleNodeContextBinds.get(bindId)
        if (singleNodeContextBind ne null) {
            // This bind has a single-node context (incl. top-level bind), so ignore context item and just return the bind nodeset
            singleNodeContextBind
        } else {
            // Nested bind, context item will be used
            if (contextItem.isInstanceOf[NodeInfo]) {
                val iterationsForContextNode = iterationsForContextNodeInfo.get(contextItem)
                if (iterationsForContextNode ne null) {
                    for (currentIteration ← iterationsForContextNode.asScala) {
                        val currentBind = currentIteration.getBind(bindId)
                        if (currentBind ne null) {
                            return currentBind
                        }
                    }
                }
            }
            // "From among the bind objects associated with the target bind element, if there exists a bind object
            // created with the same in-scope evaluation context node as the source object, then that bind object is the
            // desired target bind object. Otherwise, the IDREF resolution produced a null search result."
        }
        null
    }

    /**
     * Return the nodeset for a given bind and context item, as per "4.7.2 References to Elements within a bind
     * Element".
     *
     * @param bindId        id of the bind to handle
     * @param contextItem   context item if necessary
     * @return              bind nodeset
     */
    def getBindNodeset(bindId: String, contextItem: Item): JList[Item] = {
        val bind = resolveBind(bindId, contextItem)
        if (bind ne null) bind.nodeset else XFormsConstants.EMPTY_ITEM_LIST
    }

    protected def evaluateAndSetCustomMIPs(bind: RuntimeBind, position: Int): Unit =
        if (bind.staticBind.customMIPNameToXPathMIP.nonEmpty) // in most cases there are no custom MIPs
            for {
                (name, mips) ← bind.staticBind.customMIPNameToXPathMIP
                result = evaluateCustomMIP(bind, name, position)
                if result ne null
            } bind.setCustom(position, name, result)

    // NOTE: This only evaluates the first custom MIP of the given name associated with the bind. We do store multiple
    // ones statically, but don't have yet a solution to combine them. Should we string-join them?
    protected def evaluateCustomMIP(bind: RuntimeBind, propertyName: String, position: Int): String =
        try evaluateStringExpression(bind.nodeset, position, bind.staticBind.customMIPNameToXPathMIP(propertyName).head)
        catch {
            case NonFatal(e) ⇒
                handleMIPXPathException(e, bind, bind.staticBind.getCalculate, "evaluating XForms custom bind")
                null
        }

    private val variableResolver =
        (variableName: StructuredQName, contextItem: Item) ⇒
            staticModel.bindsByName.get(variableName.getLocalName) match {
                case Some(bind) ⇒
                    // Variable value is the bind nodeset
                    val currentBindNodeset = getBindNodeset(bind.staticId, contextItem)
                    new SequenceExtent(currentBindNodeset)
                case None ⇒
                    // Try top-level model variables
                    val modelVariables = model.getContextStack.getCurrentVariables
                    val result = modelVariables.get(variableName.getLocalName)
                    // NOTE: With XPath analysis on, variable scope has been checked statically
                    if (result eq null)
                        throw new ValidationException("Undeclared variable in XPath expression: $" + variableName.getClarkName, staticModel.locationData)
                    result
            }
}
