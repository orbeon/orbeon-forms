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

import java.{util => ju}

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, XPath, XPathCache}
import org.orbeon.oxf.xforms.XFormsContextStackSupport._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ActionTrait
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.xbl.{Scope, XBLContainer}
import org.orbeon.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.xforms.{XFormsNames, XFormsId}

import scala.collection.JavaConverters._
import scala.util.control.{Breaks, NonFatal}

// Execute a top-level XForms action and the included nested actions if any.
class XFormsActionInterpreter(
  val container          : XBLContainer,
  val outerActionElement : Element,
  val handlerEffectiveId : String,
  val event              : XFormsEvent,
  val eventObserver      : XFormsEventTarget)(implicit
  val actionXPathContext : XFormsContextStack,
  val indentedLogger     : IndentedLogger
) {

  val containingDocument: XFormsContainingDocument = container.getContainingDocument

  def getNamespaceMappings(actionElement: Element): NamespaceMapping =
    container.getPartAnalysis.getNamespaceMapping(getActionScope(actionElement), getActionStaticId(actionElement))

  // Return the source against which id resolutions are made for the given action element.
  def getSourceEffectiveId(actionElement: Element): String =
    XFormsId.getRelatedEffectiveId(handlerEffectiveId, getActionStaticId(actionElement))

  def getActionPrefixedId(actionElement: Element): String =
    container.getFullPrefix + getActionStaticId(actionElement)

  private def getActionStaticId(actionElement: Element): String =
    XFormsUtils.getElementId(actionElement) ensuring (_ ne null)

  def getActionScope(actionElement: Element): Scope =
    container.getPartAnalysis.scopeForPrefixedId(getActionPrefixedId(actionElement))

  // TODO: Presence of context is not the right way to decide whether to evaluate AVTs or not
  def mustHonorDeferredUpdateFlags(actionElement: Element): Boolean =
    actionXPathContext.getCurrentBindingContext.singleItemOpt.isEmpty ||
      resolveAVT(actionElement, XFormsNames.XXFORMS_DEFERRED_UPDATES_QNAME) != "false"

  def runAction(staticAction: ActionTrait): Unit =
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
        sourceEffectiveId              = getSourceEffectiveId(staticAction.element),
        scope                          = staticAction.scope,
        handleNonFatal                 = false
      ) {

        def runSingle(hasOverriddenContext: Boolean, contextItem: Item): Unit =
          runSingleIteration(
            actionAnalysis          = staticAction,
            actionQName             = staticAction.element.getQName,
            ifConditionAttribute    = staticAction.ifCondition,
            whileIterationAttribute = staticAction.whileCondition,
            hasOverriddenContext    = hasOverriddenContext,
            contextItem             = contextItem
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
          new ExtendedLocationData(
            locationData = staticAction.element.getData.asInstanceOf[LocationData],
            description  = "running XForms action",
            element      = staticAction.element,
            params       = Array[String]("action name", staticAction.element.getQName.qualifiedName)
          )
        )
    }

  private def runSingleIteration(
    actionAnalysis          : ElementAnalysis,
    actionQName             : QName,
    ifConditionAttribute    : Option[String],
    whileIterationAttribute : Option[String],
    hasOverriddenContext    : Boolean,
    contextItem             : Item
  ): Unit = {

    var whileIteration = 1

    val actionBreaks = new Breaks
    import actionBreaks._

    tryBreakable {
      while (true) {

        def conditionOrBreak(conditionOpt: Option[String], name: String): Unit =
          conditionOpt foreach { condition =>
            if (! evaluateCondition(actionAnalysis.element, actionQName.qualifiedName, condition, name, contextItem))
              break()
          }

        conditionOrBreak(ifConditionAttribute,    "if")
        conditionOrBreak(whileIterationAttribute, "while")

        // We are executing the action
        withDebug(
          "executing",
          ("action name" -> actionQName.qualifiedName) ::
          (whileIterationAttribute.nonEmpty list ("while iteration" -> whileIteration.toString))
        ) {
          // Push binding excluding excluding `@context` and `@model`
          // NOTE: If we repeat, re-evaluate the action binding.
          // For example:
          //
          //     <xf:delete ref="/*/foo[1]" while="/*/foo"/>
          //
          // In this case, in the second iteration, `xf:delete` must find an up-to-date binding!
          withBinding(
            ref                            = actionAnalysis.ref,
            context                        = None,
            modelId                        = None,
            bindId                         = actionAnalysis.bind,
            bindingElement                 = actionAnalysis.element,
            bindingElementNamespaceMapping = actionAnalysis.namespaceMapping,
            sourceEffectiveId              = getSourceEffectiveId(actionAnalysis.element),
            scope                          = actionAnalysis.scope,
            handleNonFatal                 = false,
          ) {
            XFormsActions.getAction(actionQName)
              .execute(DynamicActionContext(this, actionAnalysis, hasOverriddenContext option contextItem))
          }
        }

        // Stop if there is no iteration
        if (whileIterationAttribute.isEmpty)
          break()

        whileIteration += 1
      }
    } catchBreak {

    }
  }

  private def evaluateCondition(
    actionElement      : Element,
    actionName         : String,
    conditionAttribute : String,
    conditionType      : String,
    contextItem        : Item
  ): Boolean = {
    // Execute condition relative to the overridden context if it exists, or the in-scope context if not
    val (contextNodeset, contextPosition) =
      if (contextItem ne null)
        (ju.Collections.singletonList(contextItem), 1)
      else
        (ju.Collections.emptyList[Item], 0)

    // Don't evaluate the condition if the context has gone missing
    if (contextNodeset.size == 0) { //  || containingDocument.getInstanceForNode((NodeInfo) contextNodeset.get(contextPosition - 1)) == null
      debug("not executing", List("action name" -> actionName, "condition type" -> conditionType, "reason" -> "missing context"))
      return false
    }
    val conditionResult =
      evaluateKeepItems(
        actionElement   = actionElement,
        nodeset         = contextNodeset,
        position        = contextPosition,
        xpathExpression = XPath.makeBooleanExpression(conditionAttribute)
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
    actionElement   : Element,
    nodeset         : ju.List[Item],
    position        : Int,
    xpathExpression : String
  ): String = {
    // @ref points to something
    val result =
      XPathCache.evaluateAsString(
        contextItems       = nodeset,
        contextPosition    = position,
        xpathString        = xpathExpression,
        namespaceMapping   = getNamespaceMappings(actionElement),
        variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
        functionLibrary    = containingDocument.functionLibrary,
        functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement)),
        baseURI            = null,
        locationData       = actionElement.getData.asInstanceOf[LocationData],
        reporter           = containingDocument.getRequestStats.getReporter
      )

    if (result ne null) result else ""
  }

  // TODO: Pass `DynamicActionContext`.
  def evaluateKeepItems(
    actionElement   : Element,
    nodeset         : ju.List[Item],
    position        : Int,
    xpathExpression : String
  ): List[Item] =
    XPathCache.evaluateKeepItems(
      contextItems       = nodeset,
      contextPosition    = position,
      xpathString        = xpathExpression,
      namespaceMapping   = getNamespaceMappings(actionElement),
      variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
      functionLibrary    = containingDocument.functionLibrary,
      functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement)),
      baseURI            = null,
      locationData       = actionElement.getData.asInstanceOf[LocationData],
      reporter           = containingDocument.getRequestStats.getReporter
    )

  // TODO: Pass `DynamicActionContext`.
  def evaluateKeepItemsJava(
    actionElement   : Element,
    nodeset         : ju.List[Item],
    position        : Int,
    xpathExpression : String
  ): ju.List[Item] =
    XPathCache.evaluateKeepItemsJava(
      contextItems       = nodeset,
      contextPosition    = position,
      xpathString        = xpathExpression,
      namespaceMapping   = getNamespaceMappings(actionElement),
      variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
      functionLibrary    = containingDocument.functionLibrary,
      functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement)),
      baseURI            = null,
      locationData       = actionElement.getData.asInstanceOf[LocationData],
      reporter           = containingDocument.getRequestStats.getReporter
    )

  // Resolve a value which may be an AVT.
  // Return the resolved attribute value, null if the value is null or if the XPath context item is missing.
  // TODO: Pass `DynamicActionContext`.
  def resolveAVTProvideValue(actionElement: Element, attributeValue: String): String = {

    if (attributeValue eq null)
      return null

    // Whether this can't be an AVT
    if (XFormsUtils.maybeAVT(attributeValue)) {
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
        namespaceMapping   = getNamespaceMappings(actionElement),
        variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
        functionLibrary    = containingDocument.functionLibrary,
        functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement)),
        baseURI            = null,
        locationData       = actionElement.getData.asInstanceOf[LocationData],
        reporter           = containingDocument.getRequestStats.getReporter
      )
    } else {
      // We optimize as this doesn't need AVT evaluation
      attributeValue
    }
  }

  // Resolve the value of an attribute which may be an AVT.
  // TODO: Pass `DynamicActionContext`.
  def resolveAVT(actionElement: Element, attributeName: QName): String =
    resolveAVTProvideValue(actionElement, actionElement.attributeValue(attributeName))

  // Resolve the value of an attribute which may be an AVT.
  // TODO: Pass `DynamicActionContext`.
  def resolveAVT(actionElement: Element, attributeName: String): String =
    actionElement.attributeValueOpt(attributeName) map (resolveAVTProvideValue(actionElement, _)) orNull

  // Find an effective object based on either the xxf:repeat-indexes attribute, or on the current repeat indexes.
  def resolveObject(actionElement: Element, targetStaticOrAbsoluteId: String): XFormsObject = {
    container.resolveObjectByIdInScope(getSourceEffectiveId(actionElement), targetStaticOrAbsoluteId, Option.apply(null)) map { resolvedObject =>
      resolveAVT(actionElement, XFormsNames.XXFORMS_REPEAT_INDEXES_QNAME).trimAllToOpt match {
        case None =>
          // Most common case
          resolvedObject
        case Some(repeatIndexes) =>
          containingDocument.getControlByEffectiveId(
            Dispatch.resolveRepeatIndexes(container, resolvedObject, getActionPrefixedId(actionElement), repeatIndexes)
          )
      }
    } orNull
  }
}