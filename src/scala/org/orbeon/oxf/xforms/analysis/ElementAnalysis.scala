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

import controls.RepeatControl
import org.dom4j.Element
import org.orbeon.oxf.xml.dom4j.{LocationData, ExtendedLocationData}
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsConstants}
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xforms.xbl.{XBLBindingsBase, XBLBindings}

/**
 * Abstract representation of a common XForms element supporting optional context, binding and value.
 */
abstract class ElementAnalysis(val element: Element, val parent: Option[ContainerTrait], val preceding: Option[ElementAnalysis]) {

    require(element ne null)

    // Scope and model
    val scopeModel: ScopeModel
    // In-scope variables (for XPath analysis)
    val inScopeVariables: Map[String, VariableTrait]

    // Ids
    val staticId = XFormsUtils.getElementStaticId(element)
    lazy val prefixedId = scopeModel.scope.getPrefixedIdForStaticId(staticId) // lazy so that we don't use uninitialized scopeModel

    // Location
    val locationData = ElementAnalysis.createLocationData(element)

    // Element attributes: @context, @ref, @bind, @value
    val context = Option(element.attributeValue(XFormsConstants.CONTEXT_QNAME))
    val ref = ElementAnalysis.getBindingExpression(element)
    val bind = Option(element.attributeValue(XFormsConstants.BIND_QNAME))
    val value = Option(element.attributeValue(XFormsConstants.VALUE_QNAME))

    // Other
    val hasNodeBinding = ref.isDefined || bind.isDefined
    val bindingXPathEvaluations = (if (context.isDefined) 1 else 0) + (if (ref.isDefined) 1 else 0)// 0, 1, or 2: number of XPath evaluations used to resolve the binding if no optimization is taking place
    val canHoldValue = false // by default

    // Classes (not used at this time)
    val classes = ""

    // XPath analysis
    private var contextAnalysis: Option[XPathAnalysis] = None
    private var contextAnalyzed = false
    private var bindingAnalysis: Option[XPathAnalysis] = None
    private var bindingAnalyzed = false
    private var valueAnalysis: Option[XPathAnalysis] = None
    private var valueAnalyzed = false

    final def getContextAnalysis = { assert(contextAnalyzed); contextAnalysis }
    final def getBindingAnalysis = { assert(bindingAnalyzed); bindingAnalysis }
    final def getValueAnalysis = { assert(valueAnalyzed); valueAnalysis }

    def analyzeXPath() {
        contextAnalysis = computeContextAnalysis
        contextAnalyzed = true
        bindingAnalysis = computeBindingAnalysis
        bindingAnalyzed = true
        valueAnalysis = computeValueAnalysis
        valueAnalyzed = true
    }

    // To implement in subclasses
    protected def computeContextAnalysis: Option[XPathAnalysis]
    protected def computeBindingAnalysis: Option[XPathAnalysis]
    protected def computeValueAnalysis: Option[XPathAnalysis]

    /**
     * Return the context within which children elements or values evaluate. This is the element binding if any, or the
     * element context if there is no binding.
     */
    def getChildrenContext: Option[XPathAnalysis] = if (hasNodeBinding) getBindingAnalysis else getContextAnalysis

    /**
     * Return the closest ancestor repeat if any.
     */
    val closestInScopeRepeat: Option[RepeatControl] = parent match {
        case Some(repeat: RepeatControl) => Some(repeat)
        case Some(container: SimpleElementAnalysis) => container.closestInScopeRepeat
        case _ => None
    }

    /**
     * Whether this is within a repeat.
     */
    def isWithinRepeat = closestInScopeRepeat.isDefined

    // API for Java
    def javaToXML(helper: ContentHandlerHelper) = toXML(helper, List())({})

    def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: => Unit) {

        def getModelPrefixedId = scopeModel.containingModel match { case Some(model) => Some(model.prefixedId); case None => None }

        helper.startElement(element.getName,
            attributes match {
                case Nil => Array( // default attributes
                        "scope", scopeModel.scope.scopeId,
                        "prefixed-id", prefixedId,
                        "model-prefixed-id", getModelPrefixedId.orNull,
                        "binding", hasNodeBinding.toString,
                        "value", canHoldValue.toString,
                        "name", element.attributeValue("name") // e.g. variables
                    )
                case _ => Array(attributes: _*)
            })

        // Control binding and value analysis
        getBindingAnalysis match {
            case Some(bindingAnalysis) if hasNodeBinding => // NOTE: for now there can be a binding analysis even if there is no binding on the control (hack to simplify determining which controls to update)
                helper.startElement("binding")
                bindingAnalysis.toXML(helper)
                helper.endElement()
            case _ => // NOP
        }
        getValueAnalysis match {
            case Some(valueAnalysis) =>
                helper.startElement("value")
                valueAnalysis.toXML(helper)
                helper.endElement()
            case _ => // NOP
        }

        // Optional content
        content

        helper.endElement()
    }

    def freeTransientState() {
        if (contextAnalyzed && getContextAnalysis.isDefined)
            getContextAnalysis.get.freeTransientState()
        if (bindingAnalyzed && getBindingAnalysis.isDefined)
            getBindingAnalysis.get.freeTransientState()
        if (valueAnalyzed && getValueAnalysis.isDefined)
            getValueAnalysis.get.freeTransientState()
    }
}

object ElementAnalysis {

    /**
     * Return a list of preceding elements in the same scope, from root to leaf.
     */
    def getPrecedingInScope(element: ElementAnalysis)(scope: XBLBindingsBase.Scope = element.scopeModel.scope): List[ElementAnalysis] = element.preceding match {
        case Some(preceding) if preceding.scopeModel.scope == scope => preceding :: getPrecedingInScope(preceding)(scope)
        case Some(preceding) => getPrecedingInScope(preceding)(scope)
        case None => element.parent match {
            case Some(parent: ElementAnalysis) => getPrecedingInScope(parent)(scope)
            case _ => Nil
        }
    }

    /**
     * Return the closest preceding element in the same scope.
     */
    def getClosestPrecedingInScope(element: ElementAnalysis)(scope: XBLBindingsBase.Scope = element.scopeModel.scope): Option[ElementAnalysis] = element.preceding match {
        case Some(preceding) if preceding.scopeModel.scope == scope => Some(preceding)
        case Some(preceding) => getClosestPrecedingInScope(preceding)(scope)
        case None => element.parent match {
            case Some(parent: ElementAnalysis) => getClosestPrecedingInScope(parent)(scope)
            case _ => None
        }
    }

    /**
     * Return a list of ancestors from leaf to root.
     */
    def getAllAncestors(container: Option[ContainerTrait]): List[ElementAnalysis] = container match {
        case Some(container: ElementAnalysis) => container :: getAllAncestors(container.parent)
        case _ => Nil
    }

    /**
     * Return a list of ancestors in the same scope from leaf to root.
     */
    def getAllAncestorsInScope(parent: Option[ContainerTrait], scope: XBLBindingsBase.Scope): List[ElementAnalysis] =
        getAllAncestors(parent) filter (_.scopeModel.scope == scope)

    /**
     * Return a list of ancestor-or-self in the same scope from leaf to root.
     */

    def getAllAncestorsOrSelfInScope(elementAnalysis: ElementAnalysis): List[ElementAnalysis] =
        elementAnalysis :: getAllAncestorsInScope(elementAnalysis.parent, elementAnalysis.scopeModel.scope)

    /**
     * Get the closest ancestor in the same scope.
     */
    def getClosestAncestorInScope(parent: Option[ContainerTrait], scope: XBLBindingsBase.Scope): Option[ElementAnalysis] =
        getAllAncestorsInScope(parent, scope) match {
            case Nil => None
            case ancestorsList => Some(ancestorsList.head)
        }

    /**
     * Return the first ancestor with a binding analysis that is in the same scope/model.
     */
    def getClosestAncestorInScopeModel(parent: Option[ContainerTrait], scopeModel: ScopeModel): Option[ElementAnalysis] =
        getAllAncestors(parent) find (_.scopeModel == scopeModel)

    /**
     * Get the binding XPath expression from the @ref or (deprecated) @nodeset attribute.
     */
    def getBindingExpression(element: Element): Option[String] = {
        val ref = element.attributeValue(XFormsConstants.REF_QNAME)
        Option(if (ref ne null) ref else element.attributeValue(XFormsConstants.NODESET_QNAME))
    }

    def createLocationData(element: Element): ExtendedLocationData =
        if (element ne null) new ExtendedLocationData(element.getData.asInstanceOf[LocationData], "gathering static information", element) else null
}