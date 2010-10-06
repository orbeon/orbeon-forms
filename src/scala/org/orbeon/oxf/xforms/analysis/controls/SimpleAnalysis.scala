/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls

import org.dom4j.Element
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.XPathAnalysis
import org.orbeon.oxf.xforms.analysis.PathMapXPathAnalysis
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.xbl.XBLBindings
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.xml.dom4j.LocationData

import java.util.{Map => JMap}
import collection.mutable.LinkedHashMap

/**
 * Hold the static analysis for an XForms object.
 */
abstract class SimpleAnalysis(val staticState: XFormsStaticState, val scope: XBLBindings#Scope, val element: Element,
                              val parentAnalysis: SimpleAnalysis, val canHoldValue: Boolean, val containingModel: Model) {

    // TODO: get rid of possibility for element to be null
    val locationData = SimpleAnalysis.createLocationData(element)
    val staticId = if (element != null) XFormsUtils.getElementStaticId(element) else "#controls"
    val prefixedId = if (element != null) scope.getPrefixedIdForStaticId(staticId) else staticId

    // Element attributes: @context, @ref, @bind, @value
    val context = if (element != null) element.attributeValue(XFormsConstants.CONTEXT_QNAME) else null
    val ref = if (element != null) SimpleAnalysis.getBindingExpression(element) else null
    val bindStaticId = if (element != null) element.attributeValue(XFormsConstants.BIND_QNAME) else null
    val hasValueXPath = if (element != null) element.attribute(XFormsConstants.VALUE_QNAME) != null else false

    val hasContext = context != null
    val hasBind = bindStaticId != null
    val hasRef = ref != null
    val hasNodeBinding = hasRef || hasBind
    val bindingXPathEvaluations = (if (hasContext) 1 else 0) + (if (hasRef) 1 else 0)// 0, 1, or 2: number of XPath evaluations used to resolve the binding if no optimization is taking place

    private var bindingAnalysis: XPathAnalysis = null
    private var valueAnalysis: XPathAnalysis = null

    def analyzeXPath() {
        bindingAnalysis = computeBindingAnalysis(element)
        valueAnalysis = computeValueAnalysis
    }

    final def getBindingAnalysis: XPathAnalysis = bindingAnalysis
    final def getValueAnalysis: XPathAnalysis = valueAnalysis

    final def getModelPrefixedId = if (containingModel != null) containingModel.prefixedId else null
    final def getDefaultInstancePrefixedId = if (containingModel != null) containingModel.defaultInstancePrefixedId else null

    // TODO: can we just pass this at construction?
    def getInScopeVariables: JMap[String, SimpleAnalysis]

    protected def computeBindingAnalysis(element: Element): XPathAnalysis = {
        if (element != null) {
            if (hasContext) {
                // TODO: handle @context
                PathMapXPathAnalysis.CONSTANT_NEGATIVE_ANALYSIS
            } else if (hasBind) {
                // Use bind analysis
                staticState.getModelByScopeAndBind(scope, bindStaticId).bindsById.get(bindStaticId).refAnalysis
            } else {
                val bindingExpression = SimpleAnalysis.getBindingExpression(element)
                val baseAnalysis: XPathAnalysis = findOrCreateBaseAnalysis(parentAnalysis)
                if ((bindingExpression != null))
                    // New binding expression
                    analyzeXPath(staticState, baseAnalysis, prefixedId, bindingExpression)
                else
                    // TODO: TEMP: Control does not have a binding. But return one anyway so that controls w/o their own binding also get updated.
                    baseAnalysis
            }
        } else
            null
    }

    protected def computeValueAnalysis: XPathAnalysis = {
        if (element != null) {
            if (canHoldValue) {
                // Regular value analysis
                val baseAnalysis = findOrCreateBaseAnalysis(this)
                analyzeValueXPath(baseAnalysis, element, prefixedId)
            } else
                null
        } else
            null
    }

    def findOrCreateBaseAnalysis(startAnalysis: SimpleAnalysis): XPathAnalysis = findOrCreateBaseAnalysis(startAnalysis, scope, containingModel)

    protected def findOrCreateBaseAnalysis(startAnalysis: SimpleAnalysis, scope: XBLBindings#Scope, containingModel: Model): XPathAnalysis = {
        SimpleAnalysis.getAncestorOrSelfBindingAnalysis(startAnalysis, scope, containingModel) match {
            case Some(ancestorOrSelf) =>
                // There is an ancestor in the same scope with same model, use its analysis as base
                ancestorOrSelf
            case None =>
                // We are top-level in a scope/model combination, create analysis
                if (containingModel != null) {
                    if (containingModel.defaultInstancePrefixedId != null) {
                        // Start with instance('defaultInstanceId')
                        new PathMapXPathAnalysis(staticState, PathMapXPathAnalysis.buildInstanceString(containingModel.defaultInstancePrefixedId),
                            null, null, null, Map.empty, scope, containingModel.prefixedId, containingModel.defaultInstancePrefixedId, locationData, element)
                    } else
                        // No instance
                        null
                } else
                    // No model
                    null
        }
    }

    protected def analyzeValueXPath(baseAnalysis: XPathAnalysis, element: Element, prefixedId: String): XPathAnalysis = {
        // Two cases: e.g. xforms:output/@value, or the current item
        val valueAttribute = element.attributeValue(XFormsConstants.VALUE_QNAME)
        val subExpression = if (valueAttribute != null) "(" + valueAttribute + ")" else "."
        return analyzeXPath(staticState, baseAnalysis, prefixedId, "xs:string(" + subExpression + "[1])")
    }

    def analyzeXPath(staticState: XFormsStaticState, baseAnalysis: XPathAnalysis, prefixedId: String, xpathString: String): XPathAnalysis = {
        return new PathMapXPathAnalysis(staticState, xpathString, staticState.getMetadata.getNamespaceMapping(prefixedId),
            baseAnalysis, getInScopeVariables, getInScopeContexts, scope, getModelPrefixedId, getDefaultInstancePrefixedId, locationData, element)
    }

    def getLevel: Int = if (parentAnalysis == null) 0 else parentAnalysis.getLevel + 1

    /**
     * Return a map of static id => analysis for all the ancestor-or-self in scope.
     */
    def getInScopeContexts: collection.Map[String, SimpleAnalysis] =
        LinkedHashMap(SimpleAnalysis.getAllAncestorOrSelfAnalysisInScope(this, scope) map (analysis => (analysis.staticId, analysis)): _*)

    def freeTransientState() {
        if (getBindingAnalysis != null)
            getBindingAnalysis.freeTransientState
        if (getValueAnalysis != null)
            getValueAnalysis.freeTransientState
    }
}

object SimpleAnalysis {

    protected def createLocationData(element: Element): ExtendedLocationData =
        if (element != null) new ExtendedLocationData(element.getData.asInstanceOf[LocationData], "gathering static XPath information", element) else null

    def getBindingExpression(element: Element): String = {
        val ref = element.attributeValue(XFormsConstants.REF_QNAME)
        if (ref != null) ref else element.attributeValue(XFormsConstants.NODESET_QNAME)
    }

    /**
     * Return a list of ancestor-or-self analyses in the same scope from leaf to root.
     */
    def getAllAncestorOrSelfAnalysisInScope(startAnalysis: SimpleAnalysis, scope: XBLBindings#Scope): List[SimpleAnalysis] =
        getAllAncestorOrSelf(startAnalysis) filter (_.scope == scope)

    /**
     * Get the closest ancestor-or-self analysis in the same scope.
     */
    def getAncestorAnalysisInScope(startAnalysis: SimpleAnalysis, scope: XBLBindings#Scope): Option[SimpleAnalysis] =
        getAllAncestorOrSelfAnalysisInScope(startAnalysis, scope) match {
            case Nil => None
            case analysisList => Some(analysisList.head)
        }

    /**
     * Return all ancestor-or-self that have a binding analysis and are in the same scope/model.
     */
    def getAncestorOrSelfBindingAnalysis(startAnalysis: SimpleAnalysis, scope: XBLBindings#Scope, containingModel: Model): Option[XPathAnalysis] = {

        // Local function to test a match
        def matchScopeAndModel(analysis: SimpleAnalysis) =
            analysis.getBindingAnalysis != null &&
                analysis.scope == scope &&
                XFormsUtils.compareStrings(analysis.getModelPrefixedId, if (containingModel != null) containingModel.prefixedId else null) // support null models

        // Match condition on all ancestor-of-self
        getAllAncestorOrSelf(startAnalysis) find (matchScopeAndModel(_)) match {
            case None => None
            case Some(analysis) => Some(analysis.getBindingAnalysis)
        }
    }

    /**
     * Return a list of ancestor-or-self analyses from leaf to root.
     */
    def getAllAncestorOrSelf(startAnalysis: SimpleAnalysis): List[SimpleAnalysis] =
        startAnalysis match {
            case null => Nil
            case _ => startAnalysis :: getAllAncestorOrSelf(startAnalysis.parentAnalysis)
        }

    def findContainingModel(staticState: XFormsStaticState, element: Element, parentAnalysis: SimpleAnalysis, scope: XBLBindings#Scope): Model = {
        if (element != null) { // TODO: remove case where element can be null
            // Find inherited model
            val inheritedContainingModel =
                getAncestorAnalysisInScope(parentAnalysis, scope) match {
                    case None => staticState.getDefaultModelForScope(scope) // top-level control in a new scope, use default model id for scope
                    case Some(ancestor) =>  ancestor.containingModel // there is an ancestor control in the same scope, use its model id
                }
            // Check for @model attribute
            val localModelId = element.attributeValue(XFormsConstants.MODEL_QNAME)
            if (localModelId != null) {
                // Get model prefixed id and verify it belongs to this scope
                val localModelPrefixedId = scope.getPrefixedIdForStaticId(localModelId)
                if (staticState.getModel(localModelPrefixedId) == null)
                    throw new ValidationException("Reference to non-existing model id: " + localModelId, createLocationData(element))
                staticState.getModel(localModelPrefixedId)
            } else
                // Just use inherited model
                inheritedContainingModel
        } else
            null
    }
}