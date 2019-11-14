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

import java.{util ⇒ ju}

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, XPath, XPathCache}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.ActionTrait
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventTarget}
import org.orbeon.oxf.xforms.xbl.{Scope, XBLContainer}
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.BooleanValue
import org.orbeon.xforms.XFormsId

import scala.collection.JavaConverters._
import scala.util.control.{Breaks, NonFatal}

// Execute a top-level XForms action and the included nested actions if any.
class XFormsActionInterpreter(
  val container          : XBLContainer,
  val actionXPathContext : XFormsContextStack,
  val outerActionElement : Element,
  handlerEffectiveId : String,
  val event              : XFormsEvent,
  val eventObserver      : XFormsEventTarget
) {

  val containingDocument: XFormsContainingDocument = container.getContainingDocument
  val indentedLogger    : IndentedLogger           = containingDocument.getIndentedLogger(XFormsActions.LOGGING_CATEGORY)

  def getNamespaceMappings(actionElement: Element): NamespaceMapping =
    container.getNamespaceMappings(actionElement)

  // Return the source against which id resolutions are made for the given action element.
  def getSourceEffectiveId(actionElement: Element): String =
    XFormsId.getRelatedEffectiveId(handlerEffectiveId, getActionStaticId(actionElement))

  def getActionPrefixedId(actionElement: Element): String =
    container.getFullPrefix + getActionStaticId(actionElement)

  def getActionScope(actionElement: Element): Scope =
    container.getPartAnalysis.scopeForPrefixedId(getActionPrefixedId(actionElement))

  // TODO: Presence of context is not the right way to decide whether to evaluate AVTs or not
  def mustHonorDeferredUpdateFlags(actionElement: Element): Boolean =
    if (actionXPathContext.getCurrentBindingContext.singleItemOpt.isDefined)
      !("false" == resolveAVT(actionElement, XFormsConstants.XXFORMS_DEFERRED_UPDATES_QNAME))
    else
      true

  def runAction(actionAnalysis: ElementAnalysis): Unit = {

    val actionElement = actionAnalysis.element
    val actionTrait = actionAnalysis.asInstanceOf[ActionTrait]

    try {

      val ifConditionAttribute      = actionTrait.ifCondition
      val whileIterationAttribute   = actionTrait.whileCondition
      val iterateIterationAttribute = actionTrait.iterate

      // Push `@iterate` (if present) within the `@model` and `@context` context
      // TODO: function context
      actionXPathContext.pushBinding(
        iterateIterationAttribute.orNull,
        actionAnalysis.contextJava,
        null,
        actionAnalysis.modelJava,
        null,
        actionElement,
        actionAnalysis.namespaceMapping,
        getSourceEffectiveId(actionElement),
        actionAnalysis.scope,
        false
      )

      // NOTE: At this point, the context has already been set to the current action element
      iterateIterationAttribute match {
        case Some(_) ⇒
          // Gotta iterate

          // NOTE: It's not 100% how @context and @iterate should interact here. Right now @iterate overrides @context,
          // i.e. @context is evaluated first, and @iterate sets a new context for each iteration
          val currentNodeset = actionXPathContext.getCurrentBindingContext.nodeset.asScala
          for ((overriddenContextNodeInfo, zeroBasedIndex) ← currentNodeset.iterator.zipWithIndex) {

            actionXPathContext.pushIteration(zeroBasedIndex + 1)

            runSingleIteration(
              actionAnalysis          = actionAnalysis,
              actionQName             = actionElement.getQName,
              ifConditionAttribute    = ifConditionAttribute,
              whileIterationAttribute = whileIterationAttribute,
              hasOverriddenContext    = true,
              contextItem             = overriddenContextNodeInfo
            )

            actionXPathContext.popBinding()
          }
        case None ⇒
          // Do a single iteration run (but this may repeat over the @while condition!)
          runSingleIteration(
            actionAnalysis          = actionAnalysis,
            actionQName             = actionElement.getQName,
            ifConditionAttribute    = ifConditionAttribute,
            whileIterationAttribute = whileIterationAttribute,
            hasOverriddenContext    = actionXPathContext.getCurrentBindingContext.hasOverriddenContext,
            contextItem             = actionXPathContext.getCurrentBindingContext.contextItem)
      }

      // Restore
      actionXPathContext.popBinding
    } catch {
      case NonFatal(t) ⇒
        throw OrbeonLocationException.wrapException(
          t,
          new ExtendedLocationData(
            actionElement.getData.asInstanceOf[LocationData],
            "running XForms action",
            actionElement,
            Array[String]("action name", actionElement.getQName.qualifiedName)
          )
        )
    }
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

    val processBreaks = new Breaks
    import processBreaks._

    tryBreakable {
      while (true) {

        def conditionOrBreak(conditionOpt: Option[String], name: String): Unit =
          conditionOpt foreach { condition ⇒
            if (! evaluateCondition(actionAnalysis.element, actionQName.qualifiedName, condition, name, contextItem))
              break()
          }

        conditionOrBreak(ifConditionAttribute,    "if")
        conditionOrBreak(whileIterationAttribute, "while")

        // We are executing the action
        if (indentedLogger.isDebugEnabled)
          indentedLogger.startHandleOperation(
            "interpreter",
            "executing",
            "action name", actionQName.qualifiedName,
            "while iteration", whileIterationAttribute map (_ ⇒ whileIteration.toString) orNull
          )

        // Get action and execute it
        val dynamicActionContext = DynamicActionContext(this, actionAnalysis, hasOverriddenContext option contextItem)

        // Push binding excluding excluding @context and @model
        // NOTE: If we repeat, re-evaluate the action binding.
        // For example:
        //
        //   <xf:delete ref="/*/foo[1]" while="/*/foo"/>
        //
        // In this case, in the second iteration, xf:repeat must find an up-to-date nodeset!
        actionXPathContext.pushBinding(
          actionAnalysis.refJava,
          null,
          null,
          null,
          actionAnalysis.bindJava,
          actionAnalysis.element,
          actionAnalysis.namespaceMapping,
          getSourceEffectiveId(actionAnalysis.element),
          actionAnalysis.scope,
          false
        )

        XFormsActions.getAction(actionQName).execute(dynamicActionContext)

        actionXPathContext.popBinding()

        if (indentedLogger.isDebugEnabled)
          indentedLogger.endHandleOperation(
            "action name",
            actionQName.qualifiedName,
            "while iteration", whileIterationAttribute map (_ ⇒ whileIteration.toString) orNull
          )
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
      if (indentedLogger.isDebugEnabled)
        indentedLogger.logDebug("interpreter", "not executing", "action name", actionName, "condition type", conditionType, "reason", "missing context")
      return false
    }
    val conditionResult =
      evaluateKeepItems(
        actionElement   = actionElement,
        nodeset         = contextNodeset,
        position        = contextPosition,
        xpathExpression = XPath.makeBooleanExpression(conditionAttribute)
      )

    if (! conditionResult.get(0).asInstanceOf[BooleanValue].effectiveBooleanValue) {
      // Don't execute action
      if (indentedLogger.isDebugEnabled)
        indentedLogger.logDebug(
          "interpreter",
          "not executing",
          "action name", actionName,
          "condition type", conditionType,
          "reason", "condition evaluated to `false`",
          "condition", conditionAttribute
        )
      false
    } else {
       true
    }
  }

  // Evaluate an expression as a string. This returns "" if the result is an empty sequence.
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
        functionLibrary    = containingDocument.getFunctionLibrary,
        functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement)),
        baseURI            = null,
        locationData       = actionElement.getData.asInstanceOf[LocationData],
        reporter           = containingDocument.getRequestStats.getReporter
      )

    if (result ne null) result else ""
  }

  def evaluateKeepItems(
    actionElement   : Element,
    nodeset         : ju.List[Item],
    position        : Int,
    xpathExpression : String
  ): ju.List[Item] =
    XPathCache.evaluateKeepItems(
      contextItems       = nodeset,
      contextPosition    = position,
      xpathString        = xpathExpression,
      namespaceMapping   = getNamespaceMappings(actionElement),
      variableToValueMap = actionXPathContext.getCurrentBindingContext.getInScopeVariables,
      functionLibrary    = containingDocument.getFunctionLibrary,
      functionContext    = actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement)),
      baseURI            = null,
      locationData       = actionElement.getData.asInstanceOf[LocationData],
      reporter           = containingDocument.getRequestStats.getReporter
    )

  // Resolve a value which may be an AVT.
  // Return the resolved attribute value, null if the value is null or if the XPath context item is missing.
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
        functionLibrary    = containingDocument.getFunctionLibrary,
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
  def resolveAVT(actionElement: Element, attributeName: QName): String =
    resolveAVTProvideValue(actionElement, actionElement.attributeValue(attributeName))

  // Resolve the value of an attribute which may be an AVT.
  def resolveAVT(actionElement: Element, attributeName: String): String =
    actionElement.attributeValueOpt(attributeName) map (resolveAVTProvideValue(actionElement, _)) orNull

  // Find an effective object based on either the xxf:repeat-indexes attribute, or on the current repeat indexes.
  def resolveObject(actionElement: Element, targetStaticOrAbsoluteId: String): XFormsObject = {
    container.resolveObjectByIdInScope(getSourceEffectiveId(actionElement), targetStaticOrAbsoluteId, Option.apply(null)) map { resolvedObject ⇒
      resolveAVT(actionElement, XFormsConstants.XXFORMS_REPEAT_INDEXES_QNAME).trimAllToOpt match {
        case None ⇒
          // Most common case
          resolvedObject
        case Some(repeatIndexes) ⇒
          containingDocument.getControlByEffectiveId(
            Dispatch.resolveRepeatIndexes(container, resolvedObject, getActionPrefixedId(actionElement), repeatIndexes)
          )
      }
    } orNull
  }

  private def getActionStaticId(actionElement: Element): String =
    XFormsUtils.getElementId(actionElement) ensuring (_ ne null)
}