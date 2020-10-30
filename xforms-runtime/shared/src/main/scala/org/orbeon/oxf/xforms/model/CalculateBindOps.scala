/**
  * Copyright (C) 2007 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.model

import org.orbeon.oxf.util.Whitespace.applyPolicy
import org.orbeon.oxf.xforms.analysis.model.ModelDefs._
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, ModelDefs, StaticBind}
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.model.XFormsModelBinds._
import org.orbeon.saxon.om.NodeInfo

import scala.util.control.NonFatal


trait CalculateBindOps {

  self: XFormsModelBinds =>

  import Private._

  def applyDefaultAndCalculateBinds(defaultsStrategy: DefaultsStrategy, collector: XFormsEvent => Unit): Unit = {
    if (! staticModel.mustRecalculate) {
      debug("skipping bind recalculate", List("model id" -> model.getEffectiveId, "reason" -> "no recalculation binds"))
    } else {
      withDebug("performing bind recalculate", List("model id" -> model.getEffectiveId)) {

        if (staticModel.hasNonPreserveWhitespace)
          applyWhitespaceBinds(collector)

        if (staticModel.hasDefaultValueBind)
          (if (isFirstCalculate) AllDefaultsStrategy else defaultsStrategy) match {
            case strategy: SomeDefaultsStrategy =>
              applyCalculatedBindsUseOrderIfNeeded(
                ModelDefs.Default,
                staticModel.defaultValueOrder,
                strategy,
                collector
              )
            case _ =>
          }

        if (staticModel.hasCalculateBind)
          applyCalculatedBindsUseOrderIfNeeded(
            ModelDefs.Calculate,
            staticModel.recalculateOrder,
            AllDefaultsStrategy,
            collector
          )

        applyComputedExpressionBinds(collector)
      }
    }
    isFirstCalculate = false
  }

  protected def evaluateCustomMIP(
    bindNode  : BindNode,
    mip       : StaticXPathMIP,
    collector : XFormsEvent => Unit
  ): Option[String] = {
    try {
      Option(evaluateStringExpression(model, bindNode, mip))
    } catch {
      case NonFatal(t) =>
        handleMIPXPathException(t, bindNode, mip, "evaluating XForms custom bind", collector)
        None
    }
  }

  protected def evaluateBooleanMIP(
    bindNode      : BindNode,
    mipType       : BooleanMIP,
    defaultForMIP : Boolean,
    collector     : XFormsEvent => Unit
  ): Option[Boolean] = {
    bindNode.staticBind.firstXPathMIP(mipType) map { mip =>
      try {
        evaluateBooleanExpression(model, bindNode, mip)
      } catch {
        case NonFatal(t) =>
          handleMIPXPathException(t, bindNode, mip, s"evaluating XForms ${mipType.name} bind", collector)
          ! defaultForMIP // https://github.com/orbeon/orbeon-forms/issues/835
      }
    }
  }

  protected def evaluateCalculatedBind(
    bindNode  : BindNode,
    mip       : StringMIP,
    collector : XFormsEvent => Unit
  ): Option[String] =
    bindNode.staticBind.firstXPathMIP(mip) flatMap { xpathMIP =>
      try Option(evaluateStringExpression(model, bindNode, xpathMIP))
      catch {
        case NonFatal(t) =>
          handleMIPXPathException(t, bindNode, xpathMIP, s"evaluating XForms ${xpathMIP.name} MIP", collector)
          // Blank value so we don't have stale calculated values
          Some("")
      }
    }

  private object Private {

    // Whether this is the first recalculate for the associated XForms model
    var isFirstCalculate = model.containingDocument.initializing

    def evaluateAndSetCustomMIPs(
      bindNode  : BindNode,
      collector : XFormsEvent => Unit
    ): Unit =
      if (bindNode.staticBind.customMIPNameToXPathMIP.nonEmpty) // in most cases there are no custom MIPs
        for {
          (name, mips) <- bindNode.staticBind.customMIPNameToXPathMIP
          mip          <- mips.headOption
          result       <- evaluateCustomMIP(bindNode, mip, collector)
        } locally {
          bindNode.setCustom(name, result)
        }

    def applyComputedExpressionBinds(collector: XFormsEvent => Unit): Unit = {
      // Reset context stack just to re-evaluate the variables as instance values may have changed with @calculate
      model.resetAndEvaluateVariables()
      iterateBinds(topLevelBinds, bindNode =>
        if (bindNode.staticBind.hasCalculateComputedMIPs || bindNode.staticBind.hasCustomMIPs)
          handleComputedExpressionBind(bindNode, collector)
      )
    }

    def handleComputedExpressionBind(
      bindNode  : BindNode,
      collector : XFormsEvent => Unit
    ): Unit = {
      val staticBind = bindNode.staticBind

      if (staticBind.hasXPathMIP(Relevant) && dependencies.requireModelMIPUpdate(model, staticBind, Relevant, null))
        evaluateBooleanMIP(bindNode, Relevant, DEFAULT_RELEVANT, collector) foreach bindNode.setRelevant

      if (staticBind.hasXPathMIP(Readonly) && dependencies.requireModelMIPUpdate(model, staticBind, Readonly, null) ||
          staticBind.hasXPathMIP(Calculate))
        evaluateBooleanMIP(bindNode, Readonly, DEFAULT_READONLY, collector) match {
          case Some(value) =>
            bindNode.setReadonly(value)
          case None if bindNode.staticBind.hasXPathMIP(Calculate) =>
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            bindNode.setReadonly(true)
          case None =>
        }

      if (staticBind.hasXPathMIP(Required) && dependencies.requireModelMIPUpdate(model, staticBind, Required, null))
        evaluateBooleanMIP(bindNode, Required, DEFAULT_REQUIRED, collector) foreach bindNode.setRequired

      evaluateAndSetCustomMIPs(bindNode, collector)
    }

    def applyWhitespaceBinds(collector: XFormsEvent => Unit): Unit = {
      iterateBinds(topLevelBinds, bindNode =>
        if (! bindNode.hasChildrenElements) { // quick test to rule out containing elements
          bindNode.staticBind.nonPreserveWhitespaceMIPOpt foreach { mip =>
            if (dependencies.requireModelMIPUpdate(model, bindNode.staticBind, ModelDefs.Whitespace, null)) {
              DataModel.setValueIfChangedHandleErrors(
                eventTarget  = model,
                locationData = bindNode.locationData,
                nodeInfo     = bindNode.node,
                valueToSet   = applyPolicy(DataModel.getValue(bindNode.item), mip.policy),
                source       = "whitespace",
                isCalculate  = true,
                collector    = collector
              )
            }
          }
        }
      )
    }

    // Q: Can bindNode.node ever be null here?
    def mustEvaluateNode(node: NodeInfo, defaultsStrategy: SomeDefaultsStrategy): Boolean =
      defaultsStrategy == AllDefaultsStrategy || (node ne null) && InstanceData.getRequireDefaultValue(node)

    def applyCalculatedBindsUseOrderIfNeeded(
      mip              : StringMIP,
      orderOpt         : Option[List[StaticBind]],
      defaultsStrategy : SomeDefaultsStrategy,
      collector        : XFormsEvent => Unit
    ): Unit = {
      orderOpt match {
        case Some(order) =>
          applyCalculatedBindsFollowDependencies(order, mip, defaultsStrategy, collector)
        case None =>
          iterateBinds(topLevelBinds, bindNode =>
            if (
              bindNode.staticBind.hasXPathMIP(mip)                                            &&
              dependencies.requireModelMIPUpdate(model, bindNode.staticBind, mip, null) &&
              mustEvaluateNode(bindNode.node, defaultsStrategy)
            ) {
              evaluateAndSetCalculatedBind(bindNode, mip, collector)
            }
          )
      }
    }

    def applyCalculatedBindsFollowDependencies(
      order            : List[StaticBind],
      mip              : StringMIP,
      defaultsStrategy : SomeDefaultsStrategy,
      collector        : XFormsEvent => Unit
    ): Unit = {
      order foreach { staticBind =>
        val logger = DependencyAnalyzer.Logger
        val isDebug = logger.isDebugEnabled
        if (dependencies.requireModelMIPUpdate(model, staticBind, mip, null)) {
          var evaluationCount = 0
          BindVariableResolver.resolveNotAncestorOrSelf(self, None, staticBind) foreach { runtimeBindIt =>
            runtimeBindIt flatMap (_.bindNodes) foreach { bindNode =>

              // Skip if we must process only flagged nodes and the node is not flagged
              if (mustEvaluateNode(bindNode.node, defaultsStrategy)) {
                evaluationCount += 1
                evaluateAndSetCalculatedBind(bindNode, mip, collector)
              }
            }
          }
          if (isDebug) logger.debug(s"run  ${mip.name} for ${staticBind.staticId} ($evaluationCount total)")
        } else {
          if (isDebug) logger.debug(s"skip ${mip.name} for ${staticBind.staticId}")
        }
      }
    }

    def evaluateAndSetCalculatedBind(
      bindNode  : BindNode,
      mip       : StringMIP,
      collector : XFormsEvent => Unit
    ): Unit =
      evaluateCalculatedBind(bindNode, mip, collector) foreach { stringResult =>

        val valueToSet =
          bindNode.staticBind.nonPreserveWhitespaceMIPOpt match {
            case Some(mip) => applyPolicy(stringResult, mip.policy)
            case None      => stringResult
          }

        DataModel.setValueIfChangedHandleErrors(
          eventTarget  = model,
          locationData = bindNode.locationData,
          nodeInfo     = bindNode.node,
          valueToSet   = valueToSet,
          source       = mip.name,
          isCalculate  = true,
          collector    = collector
        )
      }
  }
}
