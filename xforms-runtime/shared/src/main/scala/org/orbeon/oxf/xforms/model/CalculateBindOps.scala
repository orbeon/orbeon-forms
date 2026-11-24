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

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.util.Whitespace.applyPolicy
import org.orbeon.oxf.xforms.analysis.model.MipName.*
import org.orbeon.oxf.xforms.analysis.model.StaticBind.XPathMIP
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, MipName, StaticBind, Types}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.model.XFormsModelBinds.*
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om
import org.orbeon.xforms.NonRelevantPolicy
import org.w3c.dom.Node.{ATTRIBUTE_NODE, ELEMENT_NODE}

import scala.collection.mutable
import scala.util.control.NonFatal


trait CalculateBindOps {

  self: XFormsModelBinds =>

  import Private.*

  def applyDefaultAndCalculateBinds(defaultsStrategy: DefaultsStrategy, collector: ErrorEventCollector): Unit = {
    if (! staticModel.mustRecalculate) {
      debug("skipping bind recalculate", List("model id" -> model.effectiveId, "reason" -> "no recalculation binds"))
    } else {
      withDebug("performing bind recalculate", List("model id" -> model.effectiveId)) {

        if (staticModel.hasNonPreserveWhitespace)
          applyWhitespaceBinds(collector)

        if (staticModel.hasDefaultValueBind)
          (if (isFirstCalculate) DefaultsStrategy.All else defaultsStrategy) match {
            case strategy: DefaultsStrategy.Some =>
              applyCalculatedBindsUseOrderIfNeeded(
                mip                  = MipName.Default,
                orderOpt             = staticModel.defaultValueOrder,
                defaultsStrategy     = strategy,
                collector            = collector,
                skipNonRelevantEmpty = false
              )
            case _ =>
          }

        if (! staticModel.hasEmptyNonRelevantBind) {
          if (staticModel.hasCalculateBind)
            applyCalculatedBindsUseOrderIfNeeded(
              mip                  = MipName.Calculate,
              orderOpt             = staticModel.recalculateOrder,
              defaultsStrategy     = DefaultsStrategy.All,
              collector            = collector,
              skipNonRelevantEmpty = false
            )

          applyComputedExpressionBinds(collector)
        } else {
          var pass         = 0
          var continueLoop = true

          while (continueLoop) {
            if (staticModel.hasCalculateBind)
              applyCalculatedBindsUseOrderIfNeeded(
                mip                  = MipName.Calculate,
                orderOpt             = staticModel.recalculateOrder,
                defaultsStrategy     = DefaultsStrategy.All,
                collector            = collector,
                skipNonRelevantEmpty = pass > 0 || ! isFirstCalculate
              )

            val previouslyClearedCalculateNodes =
              if (staticModel.hasCalculateBind)
                collectNonRelevantEmptyCalculateNodes()
              else
                Set.empty[om.NodeInfo]

            applyComputedExpressionBinds(collector)
            dependencies.recalculateDone(model)

            val needAnotherPass = applyNonRelevantBinds(previouslyClearedCalculateNodes, collector)
            pass += 1

            if (! needAnotherPass) {
              continueLoop = false
            } else if (pass >= MaxNonRelevantClearIterations) {
              warn(
                "maximum iterations reached when clearing non-relevant values",
                List("model id" -> model.effectiveId, "iterations" -> pass.toString)
              )
              continueLoop = false
            }
          }
        }
      }
    }
    isFirstCalculate = false
  }

  protected def evaluateCustomMIP(
    bindNode  : BindNode,
    mip       : XPathMIP,
    collector : ErrorEventCollector
  ): Option[String] = {
    try {
      Option(evaluateStringExpression(model, bindNode, mip))
    } catch {
      case NonFatal(t) =>
        handleMIPXPathException(t, bindNode, mip, mip.name, collector)
        None
    }
  }

  protected def evaluateBooleanMIP(
    bindNode     : BindNode,
    mipName      : MipName.BooleanXPath,
    defaultForMIP: Boolean,
    collector    : ErrorEventCollector
  ): Option[Boolean] = {
    bindNode.staticBind.firstXPathMipByName(mipName) map { mip =>
      try
        evaluateBooleanExpression(model, bindNode, mip)
      catch {
        case NonFatal(t) =>
          handleMIPXPathException(t, bindNode, mip, mipName, collector)

          (! defaultForMIP) // https://github.com/orbeon/orbeon-forms/issues/835
      }
    }
  }

  protected def evaluateCalculatedBind(
    bindNode : BindNode,
    mipName  : MipName.StringXPath,
    collector: ErrorEventCollector
  ): Option[String] =
    bindNode.staticBind.firstXPathMipByName(mipName) flatMap { xpathMIP =>
      try
        Option(evaluateStringExpression(model, bindNode, xpathMIP))
      catch {
        case NonFatal(t) =>
          handleMIPXPathException(t, bindNode, xpathMIP, xpathMIP.name, collector)
          // Blank value so we don't have stale calculated values
          Some("")
      }
    }

  private object Private {

    val MaxNonRelevantClearIterations = 10

    // Whether this is the first recalculate for the associated XForms model
    var isFirstCalculate = model.containingDocument.initializing

    def isNonRelevantAndEmpty(bindNode: BindNode): Boolean =
      (bindNode.node ne null)                                      &&
      ! InstanceData.getInheritedRelevant(bindNode.node)           &&
      InstanceData.getInheritedNonRelevantPolicy(bindNode.node) == NonRelevantPolicy.Empty

    def collectNonRelevantEmptyCalculateNodes(): Set[om.NodeInfo] = {
      val result = mutable.LinkedHashSet.empty[om.NodeInfo]
      iterateBinds(
        topLevelBinds,
        bindNode =>
          if (bindNode.staticBind.hasXPathMIP(MipName.Calculate) && isNonRelevantAndEmpty(bindNode))
            result += bindNode.node
      )
      result.toSet
    }

    def applyNonRelevantBinds(
      previouslyClearedCalculateNodes: Set[om.NodeInfo],
      collector                      : ErrorEventCollector
    ): Boolean = {
      var changed = false

      def setEmptyIfChanged(node: om.NodeInfo, locationData: LocationData): Boolean =
        DataModel.setValueIfChangedHandleErrors(
          eventTarget  = model,
          locationData = locationData,
          nodeInfo     = node,
          valueToSet   = "",
          source       = MipName.NonRelevant.name,
          isCalculate  = true,
          collector    = collector
        )

      def clearElementAndUnboundDescendants(elem: om.NodeInfo, locationData: LocationData): Boolean = {
        var elemChanged = false

        locally {
          val attIt = elem.iterateAxis(SaxonUtils.AttributeAxisInfo)
          var next  = attIt.next()
          while (next ne null) {
            val att = next.asInstanceOf[om.NodeInfo]
            if (att.getURI.isEmpty && ! InstanceData.hasBindNodes(att))
              elemChanged = setEmptyIfChanged(att, locationData) || elemChanged
            next = attIt.next()
          }
        }

        var hasChildElement = false
        locally {
          val childIt = elem.iterateAxis(SaxonUtils.ChildAxisInfo)
          var next    = childIt.next()
          while (next ne null) {
            next match {
              case child: om.NodeInfo if child.getNodeKind == ELEMENT_NODE =>
                hasChildElement = true
                if (! InstanceData.hasBindNodes(child))
                  elemChanged = clearElementAndUnboundDescendants(child, locationData) || elemChanged
              case _ =>
            }
            next = childIt.next()
          }
        }

        if (! hasChildElement)
          elemChanged = setEmptyIfChanged(elem, locationData) || elemChanged

        elemChanged
      }

      iterateBinds(
        topLevelBinds,
        bindNode =>
          if (isNonRelevantAndEmpty(bindNode)) {
            val node = bindNode.node
            node.getNodeKind match {
              case ATTRIBUTE_NODE =>
                changed = setEmptyIfChanged(node, bindNode.locationData) || changed
              case ELEMENT_NODE =>
                changed = clearElementAndUnboundDescendants(node, bindNode.locationData) || changed
              case _ =>
            }
          }
      )

      if (previouslyClearedCalculateNodes.nonEmpty) {
        val it = previouslyClearedCalculateNodes.iterator
        while (it.hasNext) {
          val calcNode = it.next()
          if (InstanceData.getInheritedRelevant(calcNode)) {
            model.markValueChange(Some(calcNode), isCalculate = true)
            changed = true
          }
        }
      }

      changed
    }

    private def evaluateAndSetCustomMIPs(
      bindNode  : BindNode,
      collector : ErrorEventCollector
    ): Unit =
      if (bindNode.staticBind.customMipNameToXPathMIP.nonEmpty) // in most cases there are no custom MIPs
        for {
          (name, mips) <- bindNode.staticBind.customMipNameToXPathMIP
          mip          <- mips.headOption
          result       <- evaluateCustomMIP(bindNode, mip, collector)
        } locally {
          bindNode.setCustom(name.name, result)
        }

    def applyComputedExpressionBinds(collector: ErrorEventCollector): Unit = {
      // Reset context stack just to re-evaluate the variables as instance values may have changed with @calculate
      model.resetAndEvaluateVariables(collector)
      iterateBinds(topLevelBinds, bindNode =>
        if (bindNode.staticBind.hasCalculateComputedMIPs || bindNode.staticBind.hasCustomMIPs)
          handleComputedExpressionBind(bindNode, collector)
      )
    }

    private def handleComputedExpressionBind(
      bindNode  : BindNode,
      collector : ErrorEventCollector
    ): Unit = {
      val staticBind = bindNode.staticBind

      if (staticBind.hasXPathMIP(MipName.Relevant) && dependencies.requireModelMIPUpdate(model, staticBind, MipName.Relevant, null))
        evaluateBooleanMIP(bindNode, MipName.Relevant, Types.DEFAULT_RELEVANT, collector) foreach bindNode.setRelevant

      if (staticBind.hasXPathMIP(MipName.Readonly) && dependencies.requireModelMIPUpdate(model, staticBind, MipName.Readonly, null) ||
          staticBind.hasXPathMIP(MipName.Calculate))
        evaluateBooleanMIP(bindNode, MipName.Readonly, Types.DEFAULT_READONLY, collector) match {
          case Some(value) =>
            bindNode.setReadonly(value)
          case None if bindNode.staticBind.hasXPathMIP(MipName.Calculate) =>
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            bindNode.setReadonly(true)
          case None =>
        }

      if (staticBind.hasXPathMIP(MipName.Required) && dependencies.requireModelMIPUpdate(model, staticBind, MipName.Required, null))
        evaluateBooleanMIP(bindNode, MipName.Required, Types.DEFAULT_REQUIRED, collector) foreach bindNode.setRequired

      evaluateAndSetCustomMIPs(bindNode, collector)
    }

    def applyWhitespaceBinds(collector: ErrorEventCollector): Unit = {
      iterateBinds(topLevelBinds, bindNode =>
        if (! bindNode.hasChildrenElements) { // quick test to rule out containing elements
          bindNode.staticBind.nonPreserveWhitespaceMIPOpt foreach { mip =>
            if (dependencies.requireModelMIPUpdate(model, bindNode.staticBind, MipName.Whitespace, null)) {
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
    private def mustEvaluateNode(node: om.NodeInfo, defaultsStrategy: DefaultsStrategy.Some): Boolean =
      defaultsStrategy == DefaultsStrategy.All || (node ne null) && InstanceData.getRequireDefaultValue(node)

    def applyCalculatedBindsUseOrderIfNeeded(
      mip                  : StringXPath,
      orderOpt             : Option[List[StaticBind]],
      defaultsStrategy     : DefaultsStrategy.Some,
      collector            : ErrorEventCollector,
      skipNonRelevantEmpty : Boolean
    ): Unit = {
      orderOpt match {
        case Some(order) =>
          applyCalculatedBindsFollowDependencies(order, mip, defaultsStrategy, collector, skipNonRelevantEmpty)
        case None =>
          iterateBinds(
            topLevelBinds,
            bindNode => {
              val staticBind = bindNode.staticBind
              if (
                staticBind.hasXPathMIP(mip)                                      &&
                dependencies.requireModelMIPUpdate(model, staticBind, mip, null) &&
                mustEvaluateNode(bindNode.node, defaultsStrategy)                &&
                ! (skipNonRelevantEmpty && isNonRelevantAndEmpty(bindNode))
              ) {
                evaluateAndSetCalculatedBind(bindNode, mip, collector)
              }
            }
          )
      }
    }

    private def applyCalculatedBindsFollowDependencies(
      order                : List[StaticBind],
      mip                  : StringXPath,
      defaultsStrategy     : DefaultsStrategy.Some,
      collector            : ErrorEventCollector,
      skipNonRelevantEmpty : Boolean
    ): Unit =
      order.foreach { staticBind =>

        val logger  = DependencyAnalyzer.Logger
        val isDebug = logger.isDebugEnabled

        if (dependencies.requireModelMIPUpdate(model, staticBind, mip, null)) {
          var evaluationCount = 0
          BindVariableResolver.resolveNotAncestorOrSelf(self, None, staticBind) foreach { runtimeBindIt =>
            runtimeBindIt flatMap (_.bindNodes) foreach { bindNode =>

              // Skip if we must process only flagged nodes and the node is not flagged
              if (
                mustEvaluateNode(bindNode.node, defaultsStrategy) &&
                ! (skipNonRelevantEmpty && isNonRelevantAndEmpty(bindNode))
              ) {
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

    private def evaluateAndSetCalculatedBind(
      bindNode  : BindNode,
      mip       : StringXPath,
      collector : ErrorEventCollector
    ): Unit =
      evaluateCalculatedBind(bindNode, mip, collector).foreach { stringResult =>

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
