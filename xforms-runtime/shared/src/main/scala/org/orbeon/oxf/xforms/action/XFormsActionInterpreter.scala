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
package org.orbeon.oxf.xforms.action

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsContextStackSupport.*
import org.orbeon.oxf.xforms.*
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ActionTrait
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.om
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.xforms.runtime.XFormsObject
import org.orbeon.xforms.{XFormsId, XFormsNames}

import java.util as ju
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal


// Execute a top-level XForms action and the included nested actions if any.
class XFormsActionInterpreter(
  val container          : XBLContainer,
  val outerAction        : ActionTrait,
  val handlerEffectiveId : String,
  val event              : XFormsEvent,
  val eventObserver      : XFormsEventTarget
)(implicit
  val actionXPathContext : XFormsContextStack,
  val indentedLogger     : IndentedLogger
) {

  val containingDocument: XFormsContainingDocument = container.containingDocument

  // Return the source against which id resolutions are made for the given action element.
  def getSourceEffectiveId(actionAnalysis: ElementAnalysis): String =
    XFormsId.getRelatedEffectiveId(handlerEffectiveId, actionAnalysis.staticId)

  // TODO: Presence of context is not the right way to decide whether to evaluate AVTs or not
  def mustHonorDeferredUpdateFlags(actionAnalysis: ActionTrait): Boolean =
    actionXPathContext.getCurrentBindingContext.singleItemOpt.isEmpty ||
      resolveAVT(actionAnalysis, XFormsNames.XXFORMS_DEFERRED_UPDATES_QNAME) != "false"

  def runAction(
    staticAction: ActionTrait,
    eventTarget : XFormsEventTarget,
    collector   : ErrorEventCollector
  ): Unit =
    try {

      val iterateIterationAttribute = staticAction.iterate

      // Push `@iterate` (if present) within the `@model` and `@context` context
      // TODO: function context
      withBinding(
        ref                            = iterateIterationAttribute,
        context                        = staticAction.context,
        modelId                        = staticAction.model map (_.staticId),
        bindId                         = None,
        bindingElement                 = staticAction.element,
        bindingElementNamespaceMapping = staticAction.namespaceMapping,
        sourceEffectiveId              = getSourceEffectiveId(staticAction),
        scope                          = staticAction.scope,
        eventTarget                    = eventTarget,
        collector                      = collector
      ) {

        def runSingle(hasOverriddenContext: Boolean, contextItem: om.Item): Unit =
          runSingleIteration(
            actionAnalysis          = staticAction,
            actionQName             = staticAction.element.getQName,
            ifConditionAttribute    = staticAction.ifCondition,
            whileIterationAttribute = staticAction.whileCondition,
            hasOverriddenContext    = hasOverriddenContext,
            contextItem             = contextItem,
            eventTarget             = eventTarget,
            collector               = collector
          )

        // NOTE: At this point, the context has already been set to the current action element.
        iterateIterationAttribute match {
          case Some(_) =>
            // Gotta iterate

            // It's not 100% clear how `@context` and `@iterate` should interact here. Right now `@iterate` overrides `@context`,
            // i.e. `@context` is evaluated first, and `@iterate` sets a new context for each iteration.
            val currentNodeset = actionXPathContext.getCurrentBindingContext.nodeset.asScala
            for ((overriddenContextNodeInfo, zeroBasedIndex) <- currentNodeset.iterator.zipWithIndex)
              withIteration(zeroBasedIndex + 1) { _ =>
                runSingle(
                  hasOverriddenContext = true,
                  contextItem          = overriddenContextNodeInfo
                )
              }
          case None =>
            // Do a single iteration run (but this may repeat over the `@while` condition!)
            runSingle(
              hasOverriddenContext = actionXPathContext.getCurrentBindingContext.hasOverriddenContext,
              contextItem          = actionXPathContext.getCurrentBindingContext.contextItem
            )
        }
      }
    } catch {
      case NonFatal(t) =>
        throw OrbeonLocationException.wrapException(
          t,
          XmlExtendedLocationData(
            locationData = staticAction.element.getData.asInstanceOf[LocationData],
            description  = "running XForms action",
            element      = staticAction.element,
            params       = Array[String]("action name", staticAction.element.getQName.qualifiedName)
          )
        )
    }

  private def runSingleIteration(
    actionAnalysis          : ActionTrait,
    actionQName             : QName,
    ifConditionAttribute    : Option[String],
    whileIterationAttribute : Option[String],
    hasOverriddenContext    : Boolean,
    contextItem             : om.Item,
    eventTarget             : XFormsEventTarget,
    collector               : ErrorEventCollector
  ): Unit = {

    var whileIteration = 1

    // ORBEON: Optimize loop with `while` as the non-local return showed in the JavaScript profiler.
    var exitLoop = false
    while (! exitLoop) {

      def conditionRequiresExitLoop(conditionOpt: Option[String], name: String): Boolean =
        conditionOpt match {
          case Some(condition) =>
            ! evaluateCondition(actionAnalysis, actionQName.qualifiedName, condition, name, contextItem)
          case None =>
            false
        }

      if (
        conditionRequiresExitLoop(ifConditionAttribute,    "if") ||
        conditionRequiresExitLoop(whileIterationAttribute, "while")
      ) {
        exitLoop = true
      } else {
        // We are executing the action
        withDebug(
          "executing",
          ("action name" -> actionQName.qualifiedName) ::
          (whileIterationAttribute.nonEmpty list ("while iteration" -> whileIteration.toString))
        ) {
          // Push binding excluding `@context` and `@model`
          // NOTE: If we repeat, re-evaluate the action binding.
          // For example:
          //
          //     <xf:delete ref="/*/foo[1]" while="/*/foo"/>
          //
          // In this case, in the second iteration, `xf:delete` must find an up-to-date binding!

          val actionImpl = XFormsActions.getAction(actionQName)
          if (actionImpl.pushBinding)
            withBinding(
              ref                            = actionAnalysis.ref,
              context                        = None,
              modelId                        = None,
              bindId                         = actionAnalysis.bind,
              bindingElement                 = actionAnalysis.element,
              bindingElementNamespaceMapping = actionAnalysis.namespaceMapping,
              sourceEffectiveId              = getSourceEffectiveId(actionAnalysis),
              scope                          = actionAnalysis.scope,
              eventTarget                    = eventTarget,
              collector                      = collector,
            ) {
              actionImpl.execute(DynamicActionContext(this, actionAnalysis, hasOverriddenContext option contextItem, collector))
            }
          else
            actionImpl.execute(DynamicActionContext(this, actionAnalysis, hasOverriddenContext option contextItem, collector))
        }

        // Stop if there is no iteration
        if (whileIterationAttribute.isEmpty)
          exitLoop = true
        else
          whileIteration += 1
      }
    }
  }

  private def evaluateCondition(
    actionAnalysis     : ActionTrait,
    actionName         : String,
    conditionAttribute : String,
    conditionType      : String,
    contextItem        : om.Item
  ): Boolean = {
    // Execute condition relative to the overridden context if it exists, or the in-scope context if not
    val (contextNodeset, contextPosition) =
      if (contextItem ne null)
        (ju.Collections.singletonList(contextItem), 1)
      else
        (ju.Collections.emptyList[om.Item], 0)

    // Don't evaluate the condition if the context has gone missing
    if (contextNodeset.size == 0) { //  || containingDocument.getInstanceForNode((NodeInfo) contextNodeset.get(contextPosition - 1)) == null
      debug("not executing", List("action name" -> actionName, "condition type" -> conditionType, "reason" -> "missing context"))
      return false
    }
    val conditionResult =
      evaluateKeepItems(
        actionAnalysis  = actionAnalysis,
        nodeset         = contextNodeset,
        position        = contextPosition,
        xpathExpression = StaticXPath.makeBooleanExpression(conditionAttribute)
      )

    if (! conditionResult.head.asInstanceOf[BooleanValue].effectiveBooleanValue) {
      // Don't execute action
      debug(
        "not executing",
        List(
          "action name"    -> actionName,
          "condition type" -> conditionType,
          "reason"         -> "condition evaluated to `false`",
          "condition"      -> conditionAttribute
        )
      )
      false
    } else {
       true
    }
  }

  // Evaluate an expression as a string. This returns "" if the result is an empty sequence.
  // TODO: Pass `DynamicActionContext`.
  def evaluateAsString(
    actionAnalysis  : ActionTrait,
    actionElement   : Element, // only used for `LocationData`; useful at all?
    nodeset         : ju.List[om.Item],
    position        : Int,
    xpathExpression : String
  ): String =
    XPathCache.evaluateAsStringOpt(
      contextItems       = nodeset,
      contextPosition    = position,
      xpathString        = xpathExpression,
      namespaceMapping   = actionAnalysis.namespaceMapping,
      variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
      functionLibrary    = containingDocument.functionLibrary,
      functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionAnalysis)),
      baseURI            = null,
      locationData       = actionElement.getData.asInstanceOf[LocationData],
      reporter           = containingDocument.getRequestStats.getReporter
    ) getOrElse ""

  // TODO: Pass `DynamicActionContext`.
  def evaluateKeepItems(
    actionAnalysis  : ActionTrait,
    nodeset         : ju.List[om.Item],
    position        : Int,
    xpathExpression : String
  ): List[om.Item] =
    XPathCache.evaluateKeepItems(
      contextItems       = nodeset,
      contextPosition    = position,
      xpathString        = xpathExpression,
      namespaceMapping   = actionAnalysis.namespaceMapping,
      variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
      functionLibrary    = containingDocument.functionLibrary,
      functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionAnalysis)),
      baseURI            = null,
      locationData       = actionAnalysis.element.getData.asInstanceOf[LocationData],
      reporter           = containingDocument.getRequestStats.getReporter
    )

  // Resolve a value which may be an AVT.
  // Return the resolved attribute value, null if the value is null or if the XPath context item is missing.
  // TODO: Pass `DynamicActionContext`.
  def resolveAVTProvideValue(
    actionAnalysis : ActionTrait,
    attributeValue : String
  ): String = {

    if (attributeValue eq null)
      return null

    // Whether this can't be an AVT
    if (XMLUtils.maybeAVT(attributeValue)) {
      // We have to go through AVT evaluation

      val bindingContext = actionXPathContext.getCurrentBindingContext

      // We don't have an evaluation context so return
      // CHECK: In the future we want to allow an empty evaluation context so do we really want this check?
      if (bindingContext.singleItemOpt.isEmpty)
        return null

      XPathCache.evaluateAsAvt(
        contextItems       = bindingContext.nodeset,
        contextPosition    = bindingContext.position,
        xpathString        = attributeValue,
        namespaceMapping   = actionAnalysis.namespaceMapping,
        variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
        functionLibrary    = containingDocument.functionLibrary,
        functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionAnalysis)),
        baseURI            = null,
        locationData       = actionAnalysis.element.getData.asInstanceOf[LocationData],
        reporter           = containingDocument.getRequestStats.getReporter
      )
    } else {
      // We optimize as this doesn't need AVT evaluation
      attributeValue
    }
  }

  // Resolve the value of an attribute which may be an AVT.
  // TODO: Pass `DynamicActionContext`.
  def resolveAVT(actionAnalysis: ActionTrait, attributeName: QName): String =
    resolveAVTProvideValue(actionAnalysis, actionAnalysis.element.attributeValue(attributeName))

  // Resolve the value of an attribute which may be an AVT.
  // TODO: Pass `DynamicActionContext`.
  def resolveAVT(actionAnalysis: ActionTrait, attributeName: String): String =
    actionAnalysis.element.attributeValueOpt(attributeName) map (resolveAVTProvideValue(actionAnalysis, _)) orNull

  // Find an effective object based on either the `xxf:repeat-indexes` attribute, or on the current repeat indexes.
  def resolveObject(actionAnalysis: ActionTrait, targetStaticOrAbsoluteId: String): XFormsObject = {
    container.resolveObjectByIdInScope(getSourceEffectiveId(actionAnalysis), targetStaticOrAbsoluteId, None) map { resolvedObject =>
      resolveAVT(actionAnalysis, XFormsNames.XXFORMS_REPEAT_INDEXES_QNAME).trimAllToOpt match {
        case None =>
          // Most common case
          resolvedObject
        case Some(repeatIndexes) =>
          containingDocument.getControlByEffectiveId(
            Dispatch.resolveRepeatIndexes(container, resolvedObject, actionAnalysis.prefixedId, repeatIndexes)
          )
      }
    } orNull
  }
}