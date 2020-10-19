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

import cats.syntax.option._
import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, RepeatControl, VariableAnalysisTrait}
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xml.XMLConstants.XML_LANG_QNAME
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.dom.{Extensions, XmlExtendedLocationData}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.{Perform, Phase, Propagate}
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.orbeon.xml.NamespaceMapping

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.control.Breaks

// xml:lang reference
sealed trait LangRef
case class LiteralLangRef(lang: String)      extends LangRef
case class AVTLangRef(att: AttributeControl) extends LangRef

// TODO: What's needed for construction?
trait Metadata {
  def getNamespaceMapping(prefixedId: String): Option[NamespaceMapping]
}

// TODO: What's needed for construction vs other?
trait PartAnalysisImpl {

  def isTopLevel: Boolean

  def getIndentedLogger: IndentedLogger // in `PartAnalysis`
  def getModel: Model // PartGlobalOps
  def getModel(prefixedId: String): Model // PartModelAnalysis
  def getDefaultModelForScope(scope: Scope): Option[Model] // PartModelAnalysis
  def getAttributeControl(prefixedForAttribute: String, attributeName: String): AttributeControl // PartGlobalOps
  def containingScope(prefixedId: String): Scope // PartGlobalOps
  def scopeForPrefixedId(prefixedId: String): Scope // PartGlobalOps
  def metadata: Metadata // in `PartAnalysis`
  def elementInParent: Option[ElementAnalysis] // in `PartAnalysis`
  def getEventHandlers(observerPrefixedId: String): List[EventHandler]
}

/**
 * Abstract representation of a common XForms element supporting optional context, binding and value.
 */
abstract class ElementAnalysis(
  val part      : PartAnalysisImpl,
  val index     : Int, // index of the element in the view
  val element   : Element,
  val parent    : Option[ElementAnalysis],
  val preceding : Option[ElementAnalysis],
  val scope     : Scope
) extends ElementEventHandlers
     with ElementRepeats {

  selfElement =>

  import ElementAnalysis._

  require(element ne null)

  implicit def logger: IndentedLogger = part.getIndentedLogger

  // Make this lazy because we don't want the model to be resolved upon construction. Instead, resolve when scopeModel
  // is used the first time. How can we check/enforce that scopeModel is only used at the right time?
  // Find the model associated with the given element, whether explicitly set with `@model`, or inherited.
  lazy val model: Option[Model] =
    // Check for local @model attribute
    element.attributeValue(XFormsNames.MODEL_QNAME) match {
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

  // xml:lang, inherited from parent unless overridden locally
  lazy val lang: Option[LangRef] =
    element.attributeValueOpt(XML_LANG_QNAME) match {
      case Some(v) => extractXMLLang(v)
      case None    => parent flatMap (_.lang)
    }

  protected def extractXMLLang(lang: String): Some[LangRef] =
    if (! lang.startsWith("#"))
      Some(LiteralLangRef(lang))
    else {
      val staticId   = lang.substring(1)
      val prefixedId = XFormsId.getRelatedEffectiveId(selfElement.prefixedId, staticId)
      Some(AVTLangRef(part.getAttributeControl(prefixedId, "xml:lang")))
    }

  // Element local name
  def localName: String = element.getName

  // In-scope variables (for XPath analysis)
  // Only overridden anonymously in `VariableAnalysisTrait` where it says "This is bad architecture"
  // FIXME
  lazy val inScopeVariables: Map[String, VariableTrait] = getRootVariables ++ treeInScopeVariables

  protected def getRootVariables: Map[String, VariableTrait] = Map.empty

  def removeFromParent(): Unit =
    parent foreach {
      case parent: WithChildrenTrait => parent.removeChild(selfElement)
      case _ =>
    }

  lazy val treeInScopeVariables: Map[String, VariableTrait] = {

    @tailrec
    def findPreceding(element: ElementAnalysis): Option[ElementAnalysis] = element.preceding match {
      case Some(preceding) if preceding.scope == selfElement.scope => Some(preceding)
      case Some(preceding) => findPreceding(preceding)
      case None => element.parent match {
        case Some(_: Model) =>
          None // models are not allowed to see outside variables for now (could lift this restriction later)
        case Some(parent) => findPreceding(parent)
        case _ => None
      }
    }

    findPreceding(selfElement) match {
      case Some(preceding: VariableAnalysisTrait) => preceding.treeInScopeVariables + (preceding.name -> preceding)
      case Some(preceding) => preceding.treeInScopeVariables
      case None => Map.empty
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

  // Only overridden by `RootControl`
  // TODO: pass during construction?
  def containerScope: Scope = part.containingScope(prefixedId)

  final def getChildElementScope(childElement: Element): Scope = {
    val childPrefixedId =  XFormsId.getRelatedEffectiveId(prefixedId, childElement.idOrNull)
    part.scopeForPrefixedId(childPrefixedId)
  }

  // Ids
  val staticId  : String = element.idOrNull
  val prefixedId: String = scope.prefixedIdForStaticId(staticId) // NOTE: we could also pass the prefixed id during construction

  final val namespaceMapping: NamespaceMapping = part.metadata.getNamespaceMapping(prefixedId).orNull

  // Location
  val locationData: ExtendedLocationData = ElementAnalysis.createLocationData(element)

  // Element attributes: @context, @ref, @bind, @value
  val context: Option[String] = element.attributeValueOpt(XFormsNames.CONTEXT_QNAME)
  val ref    : Option[String] = ElementAnalysis.getBindingExpression(element)
  val bind   : Option[String] = element.attributeValueOpt(XFormsNames.BIND_QNAME)
  val value  : Option[String] = element.attributeValueOpt(XFormsNames.VALUE_QNAME)

  // Other
  def hasBinding: Boolean = ref.isDefined || bind.isDefined
  val bindingXPathEvaluations: Int =
    (if (context.isDefined) 1 else 0) + (if (ref.isDefined) 1 else 0)// 0, 1, or 2: number of XPath evaluations used to resolve the binding if no optimization is taking place

  // Classes (not used at this time)
  val classes = ""

  // Extension attributes
  protected def allowedExtensionAttributes = Set.empty[QName]

  final lazy val extensionAttributes: Map[QName, String] =
    Map.empty ++ (
      CommonExtensionAttributes ++
      (element.attributeIterator collect { case att if att.getName.startsWith("data-") => att.getQName }) ++
      allowedExtensionAttributes map (qName => (qName, element.attributeValue(qName))) filter (_._2 ne null)
    )

  final lazy val nonRelevantExtensionAttributes =
    extensionAttributes map { case (k, v) => k -> (if (XMLUtils.maybeAVT(v)) "" else v) } // all blank values for AVTs

  // XPath analysis
  final var contextAnalysis: Option[XPathAnalysis] = None
  final var bindingAnalysis: Option[XPathAnalysis] = None
  final var valueAnalysis  : Option[XPathAnalysis] = None // TODO: Shouldn't this go to special nested traits only?
  // LHHAAnalysis, StaticBind, VariableAnalysisTrait, ValueTrait

  val closestAncestorInScope: Option[ElementAnalysis] = ElementAnalysis.getClosestAncestorInScope(selfElement, scope)

  def freeTransientState(): Unit = {
    contextAnalysis foreach (_.freeTransientState())
    bindingAnalysis foreach (_.freeTransientState())
    valueAnalysis   foreach (_.freeTransientState())
  }
}

trait ElementEventHandlers {

  selfElement: ElementAnalysis =>

  import ElementAnalysis._
  import ElementAnalysis.propagateBreaks.{break, breakable}

  // Event handler information as a tuple:
  // - whether the default action needs to run
  // - all event handlers grouped by phase and observer prefixed id
  type HandlerAnalysis = (Boolean, Map[Phase, Map[String, List[EventHandler]]])

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
    handlersCache.getOrElse(eventName, {
      val result = handlersForEventImpl(eventName)
      handlersCache += eventName -> result
      result
    })

  private def handlersForObserver(observer: ElementAnalysis) =
    observer.part.getEventHandlers(observer.prefixedId)

  private def hasPhantomHandler(observer: ElementAnalysis) =
    handlersForObserver(observer) exists (_.isPhantom)

  // Find all observers (including in ancestor parts) which either match the current scope or have a phantom handler
  protected def relevantObserversFromLeafToRoot: List[ElementAnalysis] = {

    def observersInAncestorParts =
      part.elementInParent.toList flatMap (_.relevantObserversFromLeafToRoot)

    def relevant(observer: ElementAnalysis) =
      observer.scope == selfElement.scope || hasPhantomHandler(observer)

    (ancestorsIterator(selfElement, includeSelf = true) filter relevant) ++: observersInAncestorParts
  }

  // Find all the handlers for the given event name if an event with that name is dispatched to this element.
  // For all relevant observers, find the handlers which match by phase
  private def handlersForEventImpl(eventName: String): HandlerAnalysis = {

    // NOTE: For `phase == Target`, `observer eq element`.
    def relevantHandlersForObserverByPhaseAndName(observer: ElementAnalysis, phase: Phase) = {

      // We gather observers with `relevantObserversFromLeafToRoot` and either:
      //
      // - they have the same XBL scope
      // - OR there is at least one phantom handler for that observer
      //
      // So if the scopes are different, we must require that the handler is a phantom handler
      // (and therefore ignore handlers on that observer which are not phantom handlers).
      val requirePhantomHandler = observer.scope != selfElement.scope

      def matchesPhaseNameTarget(eventHandler: EventHandler) =
        (
          eventHandler.isCapturePhaseOnly && phase == Phase.Capture ||
          eventHandler.isTargetPhase      && phase == Phase.Target  ||
          eventHandler.isBubblingPhase    && phase == Phase.Bubbling
        ) &&
          eventHandler.isMatchByNameAndTarget(eventName, selfElement.prefixedId)

      def matches(eventHandler: EventHandler) =
        if (requirePhantomHandler)
          eventHandler.isPhantom && matchesPhaseNameTarget(eventHandler)
        else
          matchesPhaseNameTarget(eventHandler)

      val relevantHandlers = handlersForObserver(observer) filter matches

      // DOM 3:
      //
      // - `stopPropagation`: "Prevents other event listeners from being triggered but its effect must be deferred
      //   until all event listeners attached on the Event.currentTarget have been triggered."
      // - `preventDefault`: "the event must be canceled, meaning any default actions normally taken by the
      //   implementation as a result of the event must not occur"
      // - NOTE: DOM 3 introduces also stopImmediatePropagation
      val propagate            = relevantHandlers forall (_.propagate == Propagate.Continue)
      val performDefaultAction = relevantHandlers forall (_.isPerformDefaultAction == Perform.Perform)

      // See https://github.com/orbeon/orbeon-forms/issues/3844
      def placeInnerHandlersFirst(handlers: List[EventHandler]): List[EventHandler] = {

        def isHandlerInnerHandlerOfObserver(handler: EventHandler) =
          handler.scope.parent contains observer.containerScope

        val (inner, outer) =
          handlers partition isHandlerInnerHandlerOfObserver

        inner ::: outer
      }

      (propagate, performDefaultAction, placeInnerHandlersFirst(relevantHandlers))
    }

    var propagate = true
    var performDefaultAction = true

    def handlersForPhase(observers: List[ElementAnalysis], phase: Phase): Option[(Phase, Map[String, List[EventHandler]])] = {
      val result = mutable.Map[String, List[EventHandler]]()
      breakable {
        for (observer <- observers) {

          val (localPropagate, localPerformDefaultAction, handlersToRun) =
            relevantHandlersForObserverByPhaseAndName(observer, phase)

          propagate &= localPropagate
          performDefaultAction &= localPerformDefaultAction
          if (handlersToRun.nonEmpty)
            result += observer.prefixedId -> handlersToRun

          // Cancel propagation if requested
          if (! propagate)
            break()
        }
      }

      if (result.nonEmpty)
        Some(phase -> result.toMap)
      else
        None
    }

    val observersFromLeafToRoot = relevantObserversFromLeafToRoot

    val captureHandlers =
      handlersForPhase(observersFromLeafToRoot.reverse.init, Phase.Capture)

    val targetHandlers =
      if (propagate)
        handlersForPhase(List(observersFromLeafToRoot.head), Phase.Target)
      else
        None

    val bubblingHandlers =
      if (propagate)
        handlersForPhase(observersFromLeafToRoot.tail, Phase.Bubbling)
      else
        None

    (performDefaultAction, Map.empty ++ captureHandlers ++ targetHandlers ++ bubblingHandlers)
  }
}

trait ElementRepeats {

  element: ElementAnalysis =>

  // This control's ancestor repeats, computed on demand
  lazy val ancestorRepeats: List[RepeatControl] =
    parent match {
      case Some(parentRepeat: RepeatControl) => parentRepeat :: parentRepeat.ancestorRepeats
      case Some(parentElement)               => parentElement.ancestorRepeats
      case None                              => Nil
    }

  // Same as ancestorRepeats but across parts
  lazy val ancestorRepeatsAcrossParts: List[RepeatControl] =
    part.elementInParent match {
      case Some(elementInParentPart) => ancestorRepeats ::: elementInParentPart.ancestorRepeatsAcrossParts
      case None                      => ancestorRepeats
    }

  // This control's closest ancestor in the same scope
  // NOTE: This doesn't need to go across parts, because parts don't share scopes at this time.
  lazy val ancestorRepeatInScope: Option[RepeatControl] = ancestorRepeats find (_.scope == scope)

  // Whether this is within a repeat
  def isWithinRepeat: Boolean = ancestorRepeatsAcrossParts.nonEmpty
}

object ElementAnalysis {

  val CommonExtensionAttributes = Set(STYLE_QNAME, CLASS_QNAME, ROLE_QNAME)

  val propagateBreaks = new Breaks

  /**
   * Return the closest preceding element in the same scope.
   *
   * NOTE: As in XPath, this does not include ancestors of the element.
   */
  @tailrec
  def getClosestPrecedingInScope(element: ElementAnalysis)(scope: Scope = element.scope): Option[ElementAnalysis] =
    element.preceding match {
      case Some(preceding) if preceding.scope == scope => Some(preceding)
      case Some(preceding) => getClosestPrecedingInScope(preceding)(scope)
      case None => element.parent match {
        case Some(parent) => getClosestPrecedingInScope(parent)(scope)
        case _ => None
      }
    }

  abstract class IteratorBase(
    start    : Option[ElementAnalysis],
    nextElem : ElementAnalysis => Option[ElementAnalysis]
  ) extends Iterator[ElementAnalysis] {

    private[this] var theNext = start

    def hasNext: Boolean = theNext.isDefined
    def next(): ElementAnalysis = {
      val newResult = theNext.get
      theNext = nextElem(newResult)
      newResult
    }
  }

  /**
   * Return an iterator over all the element's ancestors.
   */
  def ancestorsIterator(start: ElementAnalysis, includeSelf: Boolean): Iterator[ElementAnalysis] =
    new IteratorBase(if (includeSelf) start.some else start.parent, _.parent) {}

  def ancestorsAcrossPartsIterator(start: ElementAnalysis, includeSelf: Boolean): Iterator[ElementAnalysis] =
    (new IteratorBase(if (includeSelf) start.some else start.parent, _.parent) {}) ++
      (start.part.elementInParent.iterator flatMap (ancestorsAcrossPartsIterator(_, includeSelf = true)))

  /**
   * Iterator over the element's preceding siblings.
   */
  def precedingSiblingIterator(start: ElementAnalysis): Iterator[ElementAnalysis] =
    new IteratorBase(start.preceding, _.preceding) {}

  /**
   * Return a list of ancestors in the same scope from leaf to root.
   */
  def getAllAncestorsInScope(start: ElementAnalysis, scope: Scope, includeSelf: Boolean): List[ElementAnalysis] =
    ancestorsIterator(start, includeSelf = includeSelf) filter (_.scope == scope) toList

  /**
   * Get the closest ancestor in the same scope.
   */
  def getClosestAncestorInScope(start: ElementAnalysis, scope: Scope): Option[ElementAnalysis] =
    ancestorsIterator(start, includeSelf = false) find (_.scope == scope)

  /**
   * Return the first ancestor with a binding analysis that is in the same scope/model.
   */
  def getClosestAncestorInScopeModel(start: ElementAnalysis, scopeModel: (Scope, Option[Model])): Option[ElementAnalysis] =
    ancestorsIterator(start, includeSelf = false) find (e => (e.scope, e.model) == scopeModel)

  /**
   * Get the binding XPath expression from the @ref or (deprecated) @nodeset attribute.
   */
  def getBindingExpression(element: Element): Option[String] =
    element.attributeValueOpt(XFormsNames.REF_QNAME) orElse
      element.attributeValueOpt(XFormsNames.NODESET_QNAME)

  def createLocationData(element: Element): ExtendedLocationData =
    element.getData match {
      case data: LocationData if (element ne null) && (data.file ne null) && data.line != -1 =>
        XmlExtendedLocationData(data, "gathering static information".some, element = element.some)
      case _ => null
    }

  /**
   * Get the value of an attribute containing a space-separated list of tokens as a set.
   */
  def attSet(element: Element, qName: QName): Set[String] =
    element.attributeValue(qName).tokenizeToSet

  def attSet(element: Element, name: String): Set[String] =
    element.attributeValue(name).tokenizeToSet

  /**
   * Get the value of an attribute containing a space-separated list of QNames as a set.
   */
  def attQNameSet(element: Element, qName: QName, namespaces: NamespaceMapping): Set[QName] =
    attSet(element, qName) flatMap (Extensions.resolveQName(namespaces.mapping, _, unprefixedIsNoNamespace = true))
}