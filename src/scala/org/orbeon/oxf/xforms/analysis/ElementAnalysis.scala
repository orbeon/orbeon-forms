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
import model.Model
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsConstants}
import org.dom4j.{QName, Element}
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData, ExtendedLocationData}
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.util.ScalaUtils.stringOptionToSet

/**
 * Abstract representation of a common XForms element supporting optional context, binding and value.
 */
abstract class ElementAnalysis(val element: Element, val parent: Option[ElementAnalysis], val preceding: Option[ElementAnalysis]) {

    self ⇒

    require(element ne null)

    val namespaceMapping: NamespaceMapping

    // Element local name
    def localName = element.getName

    // Scope and model
    val scope: Scope
    val model: Option[Model]
    
    // In-scope variables (for XPath analysis)
    val inScopeVariables: Map[String, VariableTrait]

    def removeFromParent() =
        parent foreach
            { case parent: ChildrenBuilderTrait ⇒ parent.removeChild(self); case _ ⇒ }

    lazy val treeInScopeVariables: Map[String, VariableTrait] = {

        def findPreceding(element: ElementAnalysis): Option[ElementAnalysis] = element.preceding match {
            case Some(preceding) if preceding.scope == self.scope ⇒ Some(preceding)
            case Some(preceding) ⇒ findPreceding(preceding)
            case None ⇒ element.parent match {
                case Some(parent: Model) ⇒
                    None // models are not allowed to see outside variables for now (could lift this restriction later)
                case Some(parent) ⇒ findPreceding(parent)
                case _ ⇒ None
            }
        }

        findPreceding(self) match {
            case Some(preceding: VariableAnalysisTrait) ⇒ preceding.treeInScopeVariables + (preceding.name → preceding)
            case Some(preceding) ⇒ preceding.treeInScopeVariables
            case None ⇒ Map.empty
        }
    }

    // Definition of the various scopes:
    //
    // - Container scope: scope defined by the closest ancestor XBL binding. This scope is directly related to the
    //   prefix of the prefixed id. E.g. <fr:foo id="my-foo"> defines a new scope `my-foo`. All children of `my-foo`,
    //   including directly nested handlers, models, shadow trees, have the `my-foo` prefix.
    //
    // - Inner scope: this is the scope given this control if this control has `xxbl:scope='inner'`. It is usually the
    //   same as the container scope, except for directly nested handlers.
    //
    // - Outer scope: this is the scope given this control if this control has `xxbl:scope='outer'`. It is usually the
    //   actual scope of the closest ancestor XBL bound element, except for directly nested handlers.

    def containerScope: Scope

    // Ids
    val staticId = XFormsUtils.getElementId(element)
    val prefixedId = scope.prefixedIdForStaticId(staticId) // NOTE: we could also pass the prefixed id during construction

    // Location
    val locationData = ElementAnalysis.createLocationData(element)

    // Element attributes: @context, @ref, @bind, @value
    val context = Option(element.attributeValue(XFormsConstants.CONTEXT_QNAME))
    val ref = ElementAnalysis.getBindingExpression(element)
    val bind = Option(element.attributeValue(XFormsConstants.BIND_QNAME))
    val value = Option(element.attributeValue(XFormsConstants.VALUE_QNAME))

    def modelJava = model map (_.staticId) orNull
    def contextJava = context.orNull
    def refJava = ref.orNull
    def bindJava = bind.orNull

    // Other
    def hasBinding = ref.isDefined || bind.isDefined
    val bindingXPathEvaluations = (if (context.isDefined) 1 else 0) + (if (ref.isDefined) 1 else 0)// 0, 1, or 2: number of XPath evaluations used to resolve the binding if no optimization is taking place
    val canHoldValue = false // by default

    // Classes (not used at this time)
    val classes = ""

    // XPath analysis
    private var contextAnalysis: Option[XPathAnalysis] = None
    private var _contextAnalyzed = false
    private var bindingAnalysis: Option[XPathAnalysis] = None
    private var _bindingAnalyzed = false
    private var valueAnalysis: Option[XPathAnalysis] = None
    private var _valueAnalyzed = false
    def valueAnalyzed = _valueAnalyzed

    final def getContextAnalysis = { assert(_contextAnalyzed); contextAnalysis }
    final def getBindingAnalysis = { assert(_bindingAnalyzed); bindingAnalysis }
    final def getValueAnalysis = { assert(_valueAnalyzed); valueAnalysis }

    def analyzeXPath() {
        contextAnalysis = computeContextAnalysis
        _contextAnalyzed = true
        bindingAnalysis = computeBindingAnalysis
        _bindingAnalyzed = true
        valueAnalysis = computeValueAnalysis
        _valueAnalyzed = true
    }

    // To implement in subclasses
    protected def computeContextAnalysis: Option[XPathAnalysis]
    protected def computeBindingAnalysis: Option[XPathAnalysis]
    protected def computeValueAnalysis: Option[XPathAnalysis]

    /**
     * Return the context within which children elements or values evaluate. This is the element binding if any, or the
     * element context if there is no binding.
     */
    def getChildrenContext: Option[XPathAnalysis] = if (hasBinding) getBindingAnalysis else getContextAnalysis

    val closestAncestorInScope = ElementAnalysis.getClosestAncestorInScope(self, scope)

    // Whether this is within a repeat
    // NOTE: it's not ideal that this is known by ElementAnalysis
    val isWithinRepeat = RepeatControl.getAncestorRepeat(self).isDefined

    def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: ⇒ Unit) {

        def getModelPrefixedId = model map (_.prefixedId)

        helper.startElement(localName,
            attributes match {
                case Nil ⇒ Array( // default attributes
                        "scope", scope.scopeId,
                        "prefixed-id", prefixedId,
                        "model-prefixed-id", getModelPrefixedId.orNull,
                        "binding", hasBinding.toString,
                        "value", canHoldValue.toString,
                        "name", element.attributeValue("name") // e.g. variables
                    )
                case _ ⇒ Array(attributes: _*)
            })

        // Control binding and value analysis
        if (_bindingAnalyzed)
            getBindingAnalysis match {
                case Some(bindingAnalysis) if hasBinding ⇒ // NOTE: for now there can be a binding analysis even if there is no binding on the control (hack to simplify determining which controls to update)
                    helper.startElement("binding")
                    bindingAnalysis.toXML(helper)
                    helper.endElement()
                case _ ⇒ // NOP
            }

        if (_valueAnalyzed)
            getValueAnalysis match {
                case Some(valueAnalysis) ⇒
                    helper.startElement("value")
                    valueAnalysis.toXML(helper)
                    helper.endElement()
                case _ ⇒ // NOP
            }

        // Optional content
        content

        helper.endElement()
    }

    def freeTransientState() {
        if (_contextAnalyzed && getContextAnalysis.isDefined)
            getContextAnalysis.get.freeTransientState()
        if (_bindingAnalyzed && getBindingAnalysis.isDefined)
            getBindingAnalysis.get.freeTransientState()
        if (_valueAnalyzed && getValueAnalysis.isDefined)
            getValueAnalysis.get.freeTransientState()
    }
}

object ElementAnalysis {

    /**
     * Return the closest preceding element in the same scope.
     *
     * NOTE: As in XPath, this does not include ancestors of the element.
     */
    def getClosestPrecedingInScope(element: ElementAnalysis)(scope: Scope = element.scope): Option[ElementAnalysis] = element.preceding match {
        case Some(preceding) if preceding.scope == scope ⇒ Some(preceding)
        case Some(preceding) ⇒ getClosestPrecedingInScope(preceding)(scope)
        case None ⇒ element.parent match {
            case Some(parent) ⇒ getClosestPrecedingInScope(parent)(scope)
            case _ ⇒ None
        }
    }

    /**
     * Return an iterator over all the element's ancestors.
     */
    def ancestorsIterator(start: ElementAnalysis) = new Iterator[ElementAnalysis] {

        private var theNext = start.parent

        def hasNext = theNext.isDefined
        def next() = {
            val newResult = theNext.get
            theNext = newResult.parent
            newResult
        }
    }

    /**
     * Return a list of ancestors in the same scope from leaf to root.
     */
    def getAllAncestorsInScope(start: ElementAnalysis, scope: Scope): List[ElementAnalysis] =
        ancestorsIterator(start) filter (_.scope == scope) toList

    /**
     * Return a list of ancestor-or-self in the same scope from leaf to root.
     */
    def getAllAncestorsOrSelfInScope(start: ElementAnalysis): List[ElementAnalysis] =
        start :: getAllAncestorsInScope(start, start.scope)

    /**
     * Get the closest ancestor in the same scope.
     */
    def getClosestAncestorInScope(start: ElementAnalysis, scope: Scope) =
        ancestorsIterator(start) find (_.scope == scope)

    /**
     * Return the first ancestor with a binding analysis that is in the same scope/model.
     */
    def getClosestAncestorInScopeModel(start: ElementAnalysis, scopeModel: ScopeModel) =
        ancestorsIterator(start) find (e ⇒ ScopeModel(e.scope, e.model) == scopeModel)

    /**
     * Get the binding XPath expression from the @ref or (deprecated) @nodeset attribute.
     */
    def getBindingExpression(element: Element): Option[String] =
        Option(element.attributeValue(XFormsConstants.REF_QNAME)) orElse
            Option(element.attributeValue(XFormsConstants.NODESET_QNAME))

    def createLocationData(element: Element): ExtendedLocationData =
        if (element ne null) new ExtendedLocationData(element.getData.asInstanceOf[LocationData], "gathering static information", element) else null
    
    /**
     * Get the value of an attribute containing a space-separated list of tokens as a set.
     */
    def attSet(element: Element, qName: QName) =
        stringOptionToSet(Option(element.attributeValue(qName)))
    
    def attSet(element: Element, name: String) =
        stringOptionToSet(Option(element.attributeValue(name)))

    /**
     * Get the value of an attribute containing a space-separated list of QNames as a set.
     */
    def attQNameSet(element: Element, qName: QName, namespaces: NamespaceMapping) =
        attSet(element, qName) map (Dom4jUtils.extractTextValueQName(namespaces.mapping, _, true))
}