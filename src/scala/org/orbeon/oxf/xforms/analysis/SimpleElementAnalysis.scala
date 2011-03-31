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

import collection.mutable.LinkedHashMap
import org.dom4j.Element
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsConstants}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.xbl.XBLBindings

/**
 * Representation of a common XForms element supporting optional context, binding and value.
 */
class SimpleElementAnalysis(val staticStateContext: StaticStateContext, element: Element, parent: Option[ContainerTrait], preceding: Option[ElementAnalysis], scope: XBLBindings#Scope)
        extends ElementAnalysis(element, parent, preceding) with ContainerTrait {

    // Make this lazy because we don't want the model to be resolved upon construction. Instead, resolve when scopeModel
    // is used the first time. How can we check/enforce that scopeModel is only used at the right time?
    lazy val scopeModel = new ScopeModel(scope, findContainingModel)
    lazy val namespaceMapping = staticStateContext.staticState.getMetadata.getNamespaceMapping(prefixedId)

    lazy val inScopeVariables: Map[String, VariableTrait] = getRootVariables ++ treeInScopeVariables

    protected def getRootVariables: Map[String, VariableTrait] = Map.empty

    lazy val treeInScopeVariables: Map[String, VariableTrait] =
        Map[String, VariableTrait]() ++
            (ElementAnalysis.getClosestPrecedingInScope(this)() match {
                case Some(preceding: VariableAnalysisTrait) => preceding.treeInScopeVariables + (preceding.name -> preceding)
                case Some(preceding: SimpleElementAnalysis) => preceding.treeInScopeVariables
                case Some(_) => Map.empty // shouldn't happen
                case None => Map.empty
            })

    /**
     * Find the model associated with the given element, whether explicitly set with @model, or inherited.
     */
    private def findContainingModel =
        // Check for local @model attribute
        element.attributeValue(XFormsConstants.MODEL_QNAME) match {
            case localModelStaticId: String =>
                // Get model prefixed id and verify it belongs to this scope
                val localModelPrefixedId = scope.getPrefixedIdForStaticId(localModelStaticId)
                val localModel = staticStateContext.staticState.getModel(localModelPrefixedId)
                if (localModel eq null)
                    throw new ValidationException("Reference to non-existing model id: " + localModelStaticId, ElementAnalysis.createLocationData(element))

                Some(localModel)
            case _ =>
                // Use inherited model
                ElementAnalysis.getClosestAncestorInScope(parent, scope) match {
                    case Some(ancestor) => ancestor.scopeModel.containingModel // there is an ancestor control in the same scope, use its model id
                    case None => Option(staticStateContext.staticState.getDefaultModelForScope(scope)) // top-level control in a new scope, use default model id for scope
                }
        }

    protected def computeContextAnalysis = {
        context match {
            case Some(context) =>
                // @context attribute, use the overridden in-scope context
                Some(analyzeXPath(getInScopeContext, context))
            case None =>
                // No @context attribute, use the original in-scope context
                getInScopeContext
        }
    }

    protected def computeBindingAnalysis = {
        bind match {
            case Some(bindStaticId) =>
                // Use @bind analysis directly from model
                staticStateContext.staticState.getModelByScopeAndBind(scopeModel.scope, bindStaticId).bindsById.get(bindStaticId).getBindingAnalysis
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
        ElementAnalysis.getClosestAncestorInScopeModel(parent, scopeModel) match {
            case Some(ancestor: ElementAnalysis) =>
                // There is an ancestor in the same scope with same model, use its analysis as base
                ancestor.getChildrenContext
            case None =>
                // We are top-level in a scope/model combination
                scopeModel.containingModel match {
                    case Some(containingModel) => containingModel.getChildrenContext // ask model
                    case None => None // no model
                }
        }
    }

    def getChildElementScope(childElement: Element) = {
        val childPrefixedId =  XFormsUtils.getRelatedEffectiveId(prefixedId, XFormsUtils.getElementStaticId(childElement))
        staticStateContext.staticState.getXBLBindings.getResolutionScopeByPrefixedId(childPrefixedId)
    }

    protected def analyzeXPath(contextAnalysis: Option[XPathAnalysis], xpathString: String): XPathAnalysis =
        analyzeXPath(contextAnalysis, inScopeVariables, xpathString)

    protected def analyzeXPath(contextAnalysis: Option[XPathAnalysis], inScopeVariables: Map[String, VariableTrait], xpathString: String): XPathAnalysis = {

        def getDefaultInstancePrefixedId = scopeModel.containingModel match { case Some(model) => model.defaultInstancePrefixedId; case None => None }

        PathMapXPathAnalysis(staticStateContext.staticState, xpathString, staticStateContext.staticState.getMetadata.getNamespaceMapping(prefixedId),
            contextAnalysis, inScopeVariables, new SimplePathMapContext, scope, getDefaultInstancePrefixedId, locationData, element)
    }

    class SimplePathMapContext {
        /**
         * Return a map of static id => analysis for all the ancestor-or-self in scope.
         */
        def getInScopeContexts: collection.Map[String, ElementAnalysis] =
            LinkedHashMap(ElementAnalysis.getAllAncestorsOrSelfInScope(SimpleElementAnalysis.this) map (elementAnalysis => (elementAnalysis.staticId -> elementAnalysis)): _*)

        /**
         * Return analysis for closest ancestor-of-self repeat in scope.
         */
        def getInScopeRepeat = closestInScopeRepeat
    }
}
