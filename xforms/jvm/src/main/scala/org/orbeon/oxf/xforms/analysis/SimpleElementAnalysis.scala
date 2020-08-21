/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.dom.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.XPath.CompiledExpression
import org.orbeon.oxf.xforms.analysis.controls.RepeatControl
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.xforms.{XFormsConstants, XFormsUtils}
import org.orbeon.xml.NamespaceMapping
import org.orbeon.xforms.XFormsId

import scala.collection.{mutable => m}

/**
 * Representation of a common XForms element supporting optional context, binding and value.
 */
class SimpleElementAnalysis(
   val staticStateContext : StaticStateContext,
   element                : Element,
   parent                 : Option[ElementAnalysis],
   preceding              : Option[ElementAnalysis],
   val scope              : Scope
) extends ElementAnalysis(
  staticStateContext.partAnalysis,
  element,
  parent,
  preceding
) {

  self =>

  require(scope ne null)

  // Index of the element in the view
  def index: Int = staticStateContext.index

  // Make this lazy because we don't want the model to be resolved upon construction. Instead, resolve when scopeModel
  // is used the first time. How can we check/enforce that scopeModel is only used at the right time?
  lazy val model: Option[Model] = findContainingModel

  val namespaceMapping: NamespaceMapping = part.metadata.getNamespaceMapping(prefixedId).orNull

  lazy val inScopeVariables: Map[String, VariableTrait] = getRootVariables ++ treeInScopeVariables

  protected def getRootVariables: Map[String, VariableTrait] = Map.empty

  def containerScope: Scope = part.containingScope(prefixedId)

  /**
   * Find the model associated with the given element, whether explicitly set with @model, or inherited.
   */
  private def findContainingModel =
    // Check for local @model attribute
    element.attributeValue(XFormsConstants.MODEL_QNAME) match {
      case localModelStaticId: String =>
        // Get model prefixed id and verify it belongs to this scope
        val localModelPrefixedId = scope.prefixedIdForStaticId(localModelStaticId)
        val localModel = part.getModel(localModelPrefixedId)
        if (localModel eq null)
          throw new ValidationException("Reference to non-existing model id: " + localModelStaticId, ElementAnalysis.createLocationData(element))

        Some(localModel)
      case _ =>
        // Use inherited model
        closestAncestorInScope match {
          case Some(ancestor) => ancestor.model // there is an ancestor control in the same scope, use its model id
          case None           => part.getDefaultModelForScope(scope) // top-level control in a new scope, use default model id for scope
        }
    }

  protected def computeContextAnalysis: Option[XPathAnalysis] = {
    context match {
      case Some(context) =>
        // @context attribute, use the overridden in-scope context
        Some(analyzeXPath(getInScopeContext, context))
      case None =>
        // No @context attribute, use the original in-scope context
        getInScopeContext
    }
  }

  protected def computeBindingAnalysis: Option[XPathAnalysis] = {
    bind match {
      case Some(bindStaticId) =>
        // Use @bind analysis directly from model
        val model = part.getModelByScopeAndBind(scope, bindStaticId)
        if (model eq null)
          throw new ValidationException("Reference to non-existing bind id: " + bindStaticId, ElementAnalysis.createLocationData(element))
        model.bindsById.get(bindStaticId) map (_.getBindingAnalysis) orNull
      case None =>
        // No @bind
        ref match {
          case Some(ref) =>
            // New binding expression
            Some(analyzeXPath(getContextAnalysis, ref))
          case None =>
            // TODO: TEMP: Control does not have a binding. But return one anyway so that controls w/o their own binding also get updated.
            getContextAnalysis
        }
    }
  }

  // No value defined, leave this to subclasses
  protected def computeValueAnalysis: Option[XPathAnalysis] = None

  private def getInScopeContext: Option[XPathAnalysis] = {
    ElementAnalysis.getClosestAncestorInScopeModel(self, ScopeModel(scope, model)) match {
      case Some(ancestor: ElementAnalysis) =>
        // There is an ancestor in the same scope with same model, use its analysis as base
        ancestor.getChildrenContext
      case None =>
        // We are top-level in a scope/model combination
        model match {
          case Some(containingModel) => containingModel.getChildrenContext // ask model
          case None => None // no model
        }
    }
  }

  def getChildElementScope(childElement: Element): Scope = {
    val childPrefixedId =  XFormsId.getRelatedEffectiveId(prefixedId, XFormsUtils.getElementId(childElement))
    part.scopeForPrefixedId(childPrefixedId)
  }

  protected def analyzeXPath(contextAnalysis: Option[XPathAnalysis], xpathString: String, avt: Boolean = false): XPathAnalysis =
    analyzeXPath(contextAnalysis, inScopeVariables, xpathString, avt)

  // For callers without a CompiledExpression
  protected def analyzeXPath(contextAnalysis: Option[XPathAnalysis], inScopeVariables: Map[String, VariableTrait], xpathString: String, avt: Boolean): XPathAnalysis = {

    val defaultInstancePrefixedId = model flatMap (_.defaultInstancePrefixedId)

    PathMapXPathAnalysis(part, xpathString, part.metadata.getNamespaceMapping(prefixedId).orNull,
      contextAnalysis, inScopeVariables, new SimplePathMapContext, scope, defaultInstancePrefixedId, locationData, element, avt)
  }

  // For callers with a CompiledExpression
  protected def analyzeXPath(contextAnalysis: Option[XPathAnalysis], inScopeVariables: Map[String, VariableTrait], expression: CompiledExpression): XPathAnalysis = {
    val defaultInstancePrefixedId = model flatMap (_.defaultInstancePrefixedId)
    PathMapXPathAnalysis(part, expression, contextAnalysis, inScopeVariables, new SimplePathMapContext, scope, defaultInstancePrefixedId, element)
  }

  class SimplePathMapContext {

    // Current element
    def element: SimpleElementAnalysis = self

    // Return the analysis for the context in scope
    def context: Option[ElementAnalysis] = ElementAnalysis.getClosestAncestorInScope(self, self.scope)

    // Return a map of static id => analysis for all the ancestor-or-self in scope.
    def getInScopeContexts: collection.Map[String, ElementAnalysis] =
      m.LinkedHashMap(ElementAnalysis.getAllAncestorsOrSelfInScope(self) map (elementAnalysis => elementAnalysis.staticId -> elementAnalysis): _*)

    // Return analysis for closest ancestor repeat in scope.
    def getInScopeRepeat: Option[RepeatControl] = self.ancestorRepeatInScope
  }
}
