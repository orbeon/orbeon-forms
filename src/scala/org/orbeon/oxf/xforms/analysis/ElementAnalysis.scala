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

import org.orbeon.oxf.xforms.analysis.controls.{ValueTrait, RepeatControl}
import model.Model
import org.orbeon.oxf.xforms.{XFormsUtils, XFormsConstants}
import org.dom4j.{QName, Element}
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.{Dom4jUtils, LocationData, ExtendedLocationData}
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.oxf.util.ScalaUtils.stringOptionToSet
import org.orbeon.oxf.xforms.event.XFormsEvent.{Bubbling, Target, Capture, Phase}
import org.orbeon.oxf.xforms.event.EventHandler
import scala.collection.mutable
import scala.util.control.Breaks

/**
 * Abstract representation of a common XForms element supporting optional context, binding and value.
 */
abstract class ElementAnalysis(
        val part: PartAnalysisImpl,
        val element: Element,
        val parent: Option[ElementAnalysis],
        val preceding: Option[ElementAnalysis])
    extends ElementEventHandlers
    with ElementRepeats {

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

    def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: ⇒ Unit) {

        def getModelPrefixedId = model map (_.prefixedId)

        helper.startElement(localName,
            attributes match {
                case Nil ⇒ Array( // default attributes
                        "scope", scope.scopeId,
                        "prefixed-id", prefixedId,
                        "model-prefixed-id", getModelPrefixedId.orNull,
                        "binding", hasBinding.toString,
                        "value", self.isInstanceOf[ValueTrait].toString,
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

trait ElementEventHandlers {

    element: ElementAnalysis ⇒

    import ElementAnalysis._
    import propagateBreaks.{break, breakable}

    // Event handler information as a tuple:
    // - whether the default action needs to run
    // - all event handlers grouped by phase and observer prefixed id
    private type HandlerAnalysis = (Boolean, Map[Phase, Map[String, List[EventHandler]]])

    // Cache for event handlers
    // Use an immutable map and @volatile so that update are published to other threads accessing this static state.
    // NOTE: We could use `AtomicReference` but we just get/set so there is no benefit to it.
    @volatile private var handlersCache: Map[String, HandlerAnalysis] = Map()

    // Return event handler information for the given event name
    // We check the cache first, and if not found we compute the result and cache it.
    //
    // There is a chance that concurrent writers could overwrite each other's latest cache addition, but
    // `handlersForEventImpl` is idempotent so this should not be an issue, especially since a document usually has many
    // `ElementAnalysis` which means the likelihood of writing to the same `ElementAnalysis` concurrently is low. Also,
    // after a while, most handlers will be memoized, which means no more concurrent writes, only concurrent reads.
    // Finally, `handlersForEventImpl` is not quick but also not very costly.
    //
    // Other options include something like `Memoizer` from "Java Concurrency in Practice" (5.6), possibly modified to
    // use Scala 2.10 `TrieMap` and `Future`. However a plain immutable `Map` might be more memory-efficient.
    //
    // Reasoning is great but the only way to know for sure what's best would be to run a solid performance test of the
    // options.
    def handlersForEvent(eventName: String): HandlerAnalysis =
        handlersCache.get(eventName) getOrElse {
            val result = handlersForEventImpl(eventName)
            handlersCache += eventName → result
            result
        }

    private def handlersForObserver(observer: ElementAnalysis) =
        observer.part.getEventHandlers(observer.prefixedId)

    private def hasPhantomHandler(observer: ElementAnalysis) =
        handlersForObserver(observer) exists (_.isPhantom)

    // Find all observers (including in ancestor parts) which either match the current scope or have a phantom handler
    private def relevantObservers: List[ElementAnalysis] = {

        def observersInAncestorParts =
            part.elementInParent.toList flatMap (_.relevantObservers)

        def relevant(observer: ElementAnalysis) =
            observer.scope == element.scope || hasPhantomHandler(observer)

        (ancestorOrSelfIterator(element) filter relevant) ++: observersInAncestorParts
    }

    // Find all the handlers for the given event name
    // For all relevant observers, find the handlers which match by phase
    private def handlersForEventImpl(eventName: String): HandlerAnalysis = {

        def relevantHandlersForObserverByPhaseAndName(observer: ElementAnalysis, phase: Phase) = {

            val isPhantom = observer.scope != element.scope

            def matchesPhaseNameTarget(eventHandler: EventHandler) =
                (eventHandler.isCapturePhaseOnly && phase == Capture ||
                 eventHandler.isTargetPhase      && phase == Target  ||
                 eventHandler.isBubblingPhase    && phase == Bubbling) && eventHandler.isMatchByNameAndTarget(eventName, element.prefixedId)

            def matches(eventHandler: EventHandler) =
                if (isPhantom)
                    eventHandler.isPhantom && matchesPhaseNameTarget(eventHandler)
                else
                    matchesPhaseNameTarget(eventHandler)

            val relevantHandlers = handlersForObserver(observer) filter matches

            // DOM 3:
            //
            // - stopPropagation: "Prevents other event listeners from being triggered but its effect must be deferred
            //   until all event listeners attached on the Event.currentTarget have been triggered."
            // - preventDefault: "the event must be canceled, meaning any default actions normally taken by the
            //   implementation as a result of the event must not occur"
            // - NOTE: DOM 3 introduces also stopImmediatePropagation
            val propagate            = relevantHandlers forall (_.isPropagate)
            val performDefaultAction = relevantHandlers forall (_.isPerformDefaultAction)

            (propagate, performDefaultAction, relevantHandlers)
        }

        var propagate = true
        var performDefaultAction = true

        def handlersForPhase(observers: List[ElementAnalysis], phase: Phase) = {
            val result = mutable.Map[String, List[EventHandler]]()
            breakable {
                for (observer ← observers) {

                    val (localPropagate, localPerformDefaultAction, handlersToRun) =
                        relevantHandlersForObserverByPhaseAndName(observer, phase)

                    propagate &= localPropagate
                    performDefaultAction &= localPerformDefaultAction
                    if (handlersToRun.nonEmpty)
                        result += observer.prefixedId → handlersToRun

                    // Cancel propagation if requested
                    if (! propagate)
                        break()
                }
            }

            if (result.nonEmpty)
                Some(phase → result.toMap)
            else
                None
        }

        val observers = relevantObservers

        val captureHandlers =
            handlersForPhase(observers.reverse.init, Capture)

        val targetHandlers =
            if (propagate)
                handlersForPhase(List(observers.head), Target)
            else
                None

        val bubblingHandlers =
            if (propagate)
                handlersForPhase(observers.tail, Bubbling)
            else
                None

        (performDefaultAction, Map() ++ captureHandlers ++ targetHandlers ++ bubblingHandlers)
    }
}

trait ElementRepeats {

    element: ElementAnalysis ⇒

    // This control's ancestor repeats, computed on demand
    lazy val ancestorRepeats: List[RepeatControl] =
        parent match {
            case Some(parentRepeat: RepeatControl) ⇒ parentRepeat :: parentRepeat.ancestorRepeats
            case Some(parentElement)               ⇒ parentElement.ancestorRepeats
            case None                              ⇒ Nil
        }

    // Same as ancestorRepeats but across parts
    lazy val ancestorRepeatsAcrossParts: List[RepeatControl] =
        part.elementInParent match {
            case Some(elementInParentPart) ⇒ ancestorRepeats ::: elementInParentPart.ancestorRepeatsAcrossParts
            case None                      ⇒ ancestorRepeats
        }

    // This control's closest ancestor in the same scope
    // NOTE: This doesn't need to go across parts, because parts don't share scopes at this time.
    lazy val ancestorRepeatInScope = ancestorRepeats find (_.scope == scope)

    // Whether this is within a repeat
    def isWithinRepeat = ancestorRepeatsAcrossParts.nonEmpty
}

object ElementAnalysis {

    val propagateBreaks = new Breaks

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
    def ancestorIterator(start: ElementAnalysis) = new Iterator[ElementAnalysis] {

        private[this] var theNext = start.parent

        def hasNext = theNext.isDefined
        def next() = {
            val newResult = theNext.get
            theNext = newResult.parent
            newResult
        }
    }

    /**
     * Iterator over the element and all its ancestors.
     */
    def ancestorOrSelfIterator(start: ElementAnalysis) = new Iterator[ElementAnalysis] {

        private[this] var theNext = Option(start)

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
        ancestorIterator(start) filter (_.scope == scope) toList

    /**
     * Return a list of ancestor-or-self in the same scope from leaf to root.
     */
    def getAllAncestorsOrSelfInScope(start: ElementAnalysis): List[ElementAnalysis] =
        start :: getAllAncestorsInScope(start, start.scope)

    /**
     * Get the closest ancestor in the same scope.
     */
    def getClosestAncestorInScope(start: ElementAnalysis, scope: Scope) =
        ancestorIterator(start) find (_.scope == scope)

    /**
     * Return the first ancestor with a binding analysis that is in the same scope/model.
     */
    def getClosestAncestorInScopeModel(start: ElementAnalysis, scopeModel: ScopeModel) =
        ancestorIterator(start) find (e ⇒ ScopeModel(e.scope, e.model) == scopeModel)

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