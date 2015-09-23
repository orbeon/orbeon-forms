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

import java.{util ⇒ ju}

import org.apache.commons.validator.routines.{EmailValidator, RegexValidator}
import org.orbeon.errorified.Exceptions
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{Logging, XPath}
import org.orbeon.oxf.xforms.XFormsModelBinds.BindRunner
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, Model, StaticBind}
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xforms.model.{BindIteration, BindNode, DataModel, RuntimeBind}
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.saxon.dom4j.TypedNodeWrapper
import org.orbeon.saxon.om.{Item, NodeInfo}

import scala.collection.JavaConverters._
import scala.collection.{mutable ⇒ m}
import scala.language.postfixOps
import scala.util.control.NonFatal

abstract class XFormsModelBindsBase(model: XFormsModel) extends Logging {

  type StaticMIP      = StaticBind#MIP
  type StaticXPathMIP = StaticBind#XPathMIP
  type StaticTypeMIP  = StaticBind#TypeMIP

  private val containingDocument = model.containingDocument
  private val dependencies = containingDocument.getXPathDependencies
  private val staticModel = model.getStaticModel

  private implicit val logger = model.getIndentedLogger
  private implicit def reporter: XPath.Reporter = containingDocument.getRequestStats.addXPathStat

  private var _topLevelBinds: Seq[RuntimeBind] = Nil
  final def topLevelBinds = _topLevelBinds

  final val singleNodeContextBinds   = m.HashMap[String, RuntimeBind]()
  final val iterationsForContextItem = m.HashMap[Item, List[BindIteration]]()

  // Whether this is the first rebuild for the associated XForms model
  private var isFirstRebuild = containingDocument.isInitializing

  // Iterate over all binds and for each one call the callback
  protected def iterateBinds(bindRunner: BindRunner): Unit =
    // Iterate over top-level binds
    for (currentBind ← topLevelBinds)
      try currentBind.applyBinds(bindRunner)
      catch {
        case NonFatal(e) ⇒
          throw OrbeonLocationException.wrapException(
            e,
            new ExtendedLocationData(
              currentBind.staticBind.locationData,
              "evaluating XForms binds",
              currentBind.staticBind.element
            )
          )
        }

  // Rebuild all binds, computing all bind nodesets (but not computing the MIPs)
  def rebuild(): Unit =
    withDebug("performing rebuild", List("model id" → model.getEffectiveId)) {

      // NOTE: Assume that model.getContextStack().resetBindingContext(model) was called

      // Clear all instances that might have InstanceData
      // Only need to do this after the first rebuild
      if (! isFirstRebuild)
        for (instance ← model.getInstances.asScala) {
          // Only clear instances that are impacted by xf:bind/(@ref|@nodeset), assuming we were able to
          // figure out the dependencies.
          // The reason is that clearing this state can take quite some time
          val instanceMightBeSchemaValidated =
            model.hasSchema && instance.isSchemaValidation

          val instanceMightHaveMips =
            dependencies.hasAnyCalculationBind(staticModel, instance.getPrefixedId) ||
            dependencies.hasAnyValidationBind(staticModel, instance.getPrefixedId)

          if (instanceMightBeSchemaValidated || instanceMightHaveMips)
            DataModel.visitElement(instance.rootElement, InstanceData.clearStateForRebuild)
        }

      // Not ideal, but this state is updated when the bind tree is updated below
      singleNodeContextBinds.clear()
      iterationsForContextItem.clear()

      // Iterate through all top-level bind elements to create new bind tree
      // TODO: In the future, XPath dependencies must allow for partial rebuild of the tree as is the case with controls
      // Even before that, the bind tree could be modified more dynamically as is the case with controls
      _topLevelBinds =
        for (staticBind ← staticModel.topLevelBinds)
          yield new RuntimeBind(model, staticBind, null, true)

      isFirstRebuild = false
    }

  protected def validateConstraint(bindNode: BindNode, invalidInstances: ju.Set[String]): Unit = {

    assert(bindNode.staticBind.constraintsByLevel.nonEmpty)

    // Don't try to apply constraints if it's not a node (it's set to null in that case)
    val currentNode = bindNode.node
    if (currentNode eq null)
      return

    // NOTE: 2011-02-03: Decided to allow setting a constraint on an element with children. Handles the case of
    // assigning validity to an enclosing element.
    // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

    // NOTE: 2015-05-27: We used to not run constraints if the datatype was not valid. This could cause a bug
    // when the type would switch from valid to invalid and back. In addition, we do want to run constraints so
    // that validation properties such as `max-length` are computed even when the datatype is not valid. So now we
    // keep the list of constraints up to date even when the datatype is not valid.

    for {
      (level, mips) ← bindNode.staticBind.constraintsByLevel
    } locally {
      if (dependencies.requireModelMIPUpdate(staticModel, bindNode.staticBind, Model.CONSTRAINT, level)) {
        // Re-evaluate and set
        val failedConstraints = failedConstraintMIPs(mips, bindNode)
        if (failedConstraints.nonEmpty)
          bindNode.failedConstraints += level → failedConstraints
        else
          bindNode.failedConstraints -= level
      } else {
        // Don't change list of failed constraints for this level
      }
    }

    // Remember invalid instances
    if (! bindNode.constraintsSatisfiedForLevel(ErrorLevel)) {
      val instanceForNodeInfo = containingDocument.getInstanceForNode(currentNode)
      invalidInstances.add(instanceForNodeInfo.getEffectiveId)
    }
  }

  protected def failedConstraintMIPs(mips: List[StaticXPathMIP], bindNode: BindNode): List[StaticXPathMIP] =
    for {
      mip       ← mips
      succeeded = evaluateBooleanExpressionStoreProperties(bindNode, mip)
      if ! succeeded
    } yield
      mip

  protected def hasFailedErrorConstraintMIPs(bindNode: BindNode): Boolean =
    bindNode.staticBind.constraintsByLevel.get(ErrorLevel).to[List] flatMap { mips ⇒
      failedConstraintMIPs(mips, bindNode)
    } nonEmpty

  protected def evaluateBooleanExpressionStoreProperties(
    bindNode : BindNode,
    xpathMIP : StaticXPathMIP
  ): Boolean =
    try {
      // LATER: If we implement support for allowing binds to receive events, source must be bind id.
      val functionContext =
        model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode))

      val result =
        XPath.evaluateSingle(
          contextItems        = bindNode.parentBind.items,
          contextPosition     = bindNode.position,
          compiledExpression  = xpathMIP.compiledExpression,
          functionContext     = functionContext,
          variableResolver    = model.variableResolver
        ).asInstanceOf[Boolean]

      functionContext.properties foreach { propertiesMap ⇒
        propertiesMap foreach {
          case (name, Some(l: Long)) ⇒ bindNode.setCustom(name, l.toString)
          case (name, None)          ⇒ bindNode.clearCustom(name)
          case _ ⇒
        }
      }

      result
    } catch {
      case NonFatal(e) ⇒
        handleMIPXPathException(e, bindNode, xpathMIP, "evaluating XForms constraint bind")
        ! Model.DEFAULT_VALID
    }

  protected def evaluateBooleanExpression(bindNode: BindNode, xpathMIP: StaticXPathMIP): Boolean =
    // LATER: If we implement support for allowing binds to receive events, source must be bind id.
    XPath.evaluateSingle(
      contextItems        = bindNode.parentBind.items,
      contextPosition     = bindNode.position,
      compiledExpression  = xpathMIP.compiledExpression,
      functionContext     = model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode)),
      variableResolver    = model.variableResolver
    ).asInstanceOf[Boolean]

  protected def evaluateStringExpression(bindNode: BindNode, xpathMIP: StaticXPathMIP): String =
    // NOTE: When we implement support for allowing binds to receive events, source must be bind id.
    XPath.evaluateAsString(
      contextItems       = bindNode.parentBind.items,
      contextPosition    = bindNode.position,
      compiledExpression = xpathMIP.compiledExpression,
      functionContext    = model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode)),
      variableResolver   = model.variableResolver
    )

  protected def handleMIPXPathException(
    throwable : Throwable,
    bindNode  : BindNode,
    xpathMIP  : StaticXPathMIP,
    message   : String
  ): Unit = {
    Exceptions.getRootThrowable(throwable) match {
      case e: TypedNodeWrapper.TypedValueException ⇒
        // Consider validation errors as ignorable. The rationale is that if the function (the XPath
        // expression) works on inputs that are not valid (hence the validation error), then the function cannot
        // produce a meaningful result. We think that it is worth handling this condition slightly differently
        // from other dynamic and static errors, so that users can just write expression without constant checks
        // with `castable as` or `instance of`.
        debug("typed value exception", List(
          "node name"     → e.nodeName,
          "expected type" → e.typeName,
          "actual value"  → e.nodeValue
        ))
      case t ⇒
        // All other errors dispatch an event and will cause the usual fatal-or-not behavior
        val ve = OrbeonLocationException.wrapException(t,
          new ExtendedLocationData(
            bindNode.locationData,
            description = Option(message),
            params      = List("expression" → xpathMIP.compiledExpression.string),
            element     = Some(bindNode.staticBind.element)
          )
        )

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
        // This bind has a single-node context (incl. top-level bind), so ignore context item and just return
        // the bind nodeset
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

        it.nextOption().orNull
    }

  protected def evaluateAndSetCustomMIPs(bindNode: BindNode): Unit =
    if (bindNode.staticBind.customMIPNameToXPathMIP.nonEmpty) // in most cases there are no custom MIPs
      for {
        (name, mips) ← bindNode.staticBind.customMIPNameToXPathMIP
        mip          ← mips.headOption
        result       = evaluateCustomMIP(bindNode, mip)
        if result ne null
      } locally {
        bindNode.setCustom(name, result)
      }

  // NOTE: This only evaluates the first custom MIP of the given name associated with the bind. We do store multiple
  // ones statically, but don't have yet a solution to combine them. Should we string-join them?
  protected def evaluateCustomMIPByName(bindNode: BindNode, propertyName: String): String =
    evaluateCustomMIP(bindNode, bindNode.staticBind.customMIPNameToXPathMIP(propertyName).head)

  protected def evaluateCustomMIP(bindNode: BindNode, mip: StaticXPathMIP): String = {
    try evaluateStringExpression(bindNode, mip)
    catch {
      case NonFatal(e) ⇒
        handleMIPXPathException(e, bindNode, mip, "evaluating XForms custom bind")
        null
    }
  }

  def applyDefaultValueBindsIfNeeded(defaultsStrategy: SomeDefaultsStrategy): Unit =
    if (staticModel.hasDefaultValueBind)
      applyCalculatedBindsUseOrderIfNeeded(Model.Default, staticModel.defaultValueOrder, defaultsStrategy)

  def applyCalculateBindsIfNeeded(): Unit =
    if (staticModel.hasCalculateBind)
      applyCalculatedBindsUseOrderIfNeeded(Model.Calculate, staticModel.recalculateOrder, AllDefaultsStrategy)

  // Q: Can bindNode.node ever be null here?
  private def mustEvaluateNode(node: NodeInfo, defaultsStrategy: SomeDefaultsStrategy) =
    defaultsStrategy == AllDefaultsStrategy || (node ne null) && InstanceData.getRequireDefaultValue(node)

  def applyCalculatedBindsUseOrderIfNeeded(
    mip              : Model.StringMIP,
    orderOpt         : Option[List[StaticBind]],
    defaultsStrategy : SomeDefaultsStrategy
  ): Unit = {
    orderOpt match {
      case Some(order) ⇒
        applyCalculatedBindsFollowDependencies(order, mip, defaultsStrategy)
      case None ⇒
        iterateBinds(new XFormsModelBinds.BindRunner() {
          def applyBind(bindNode: BindNode) =
            if (
              bindNode.staticBind.hasXPathMIP(mip)                                                 &&
              dependencies.requireModelMIPUpdate(staticModel, bindNode.staticBind, mip.name, null) &&
              mustEvaluateNode(bindNode.node, defaultsStrategy)
            ) {
              evaluateAndSetCalculatedBind(bindNode, mip)
            }
        })
    }
  }

  def applyCalculatedBindsFollowDependencies(
    order            : List[StaticBind],
    mip              : Model.StringMIP,
    defaultsStrategy : SomeDefaultsStrategy
  ): Unit = {
    order foreach { staticBind ⇒
      val logger = DependencyAnalyzer.Logger
      val isDebug = logger.isDebugEnabled
      if (dependencies.requireModelMIPUpdate(staticModel, staticBind, mip.name, null)) {
        var evaluationCount = 0
        BindVariableResolver.resolveNotAncestorOrSelf(this, None, staticBind) foreach { runtimeBindIt ⇒
          runtimeBindIt flatMap (_.bindNodes) foreach { bindNode ⇒

            // Skip if we must process only flagged nodes and the node is not flagged
            if (mustEvaluateNode(bindNode.node, defaultsStrategy)) {
              evaluationCount += 1
              evaluateAndSetCalculatedBind(bindNode, mip)
            }
          }
        }
        if (isDebug) logger.debug(s"run  ${mip.name} for ${staticBind.staticId} ($evaluationCount total)")
      } else {
        if (isDebug) logger.debug(s"skip ${mip.name} for ${staticBind.staticId}")
      }
    }
  }

  def evaluateAndSetCalculatedBind(bindNode: BindNode, mip: Model.StringMIP): Unit =
    evaluateCalculatedBind(bindNode, mip) foreach { stringResult ⇒
      DataModel.jSetValueIfChanged(
        containingDocument = containingDocument,
        eventTarget        = model,
        locationData       = bindNode.locationData,
        nodeInfo           = bindNode.node,
        valueToSet         = stringResult,
        source             = mip.name,
        isCalculate        = true
      )
    }

  def evaluateCalculatedBind(bindNode: BindNode, mip: Model.StringMIP): Option[String] =
    bindNode.staticBind.firstXPathMIP(mip) flatMap { xpathMIP ⇒
      try Option(evaluateStringExpression(bindNode, xpathMIP))
      catch {
        case e: Exception ⇒
          handleMIPXPathException(e, bindNode, xpathMIP, s"evaluating XForms ${xpathMIP.name} MIP")
          // Blank value so we don't have stale calculated values
          Some("")
      }
    }

  def jEvaluateCalculatedBind(bindNode: BindNode, mipName: String): String = {
    val mip = mipName match {
      case Model.Calculate.name ⇒ Model.Calculate
      case Model.Default.name   ⇒ Model.Default
      case _                    ⇒ throw new IllegalArgumentException(mipName)
    }
    evaluateCalculatedBind(bindNode, mip).orNull
  }
}

object XFormsModelBindsBase {

  // Modified email validator which:
  //
  // 1. Doesn't check whether a TLD is known, as there are two many of those now (2015) and changing constantly.
  // 2. Doesn't support IP addresses (we probably could, but we don't care right now).
  //
  object EmailValidatorNoDomainValidation extends EmailValidator(false) {

    private val DomainLabelRegex = "\\p{Alnum}(?>[\\p{Alnum}-]*\\p{Alnum})*"
    private val TopLabelRegex    = "\\p{Alpha}{2,}"
    private val DomainNameRegex  = "^(?:" + DomainLabelRegex + "\\.)+" + "(" + TopLabelRegex + ")$"

    private val DomainRegex = new RegexValidator(DomainNameRegex)

    override def isValidDomain(domain: String) =
      Option(DomainRegex.`match`(domain)) exists (_.nonEmpty)
  }
}