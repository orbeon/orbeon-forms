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
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.analysis.controls.{AttributeControl, RepeatControl, RootControl, VariableAnalysisTrait, VariableTrait}
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom.{Extensions, XmlExtendedLocationData}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.Phase
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.orbeon.xml.NamespaceMapping

import scala.annotation.tailrec


// xml:lang reference
sealed trait LangRef
object LangRef {
  case object Undefined                  extends LangRef
  case object None                       extends LangRef
  case class  Literal(lang: String)      extends LangRef
  case class  AVT(att: AttributeControl) extends LangRef
}

// Represent a single-item binding. As of 2023-11-15, this is only used by `UploadControl` but the intent would be to
// use it for all controls that support single-item bindings.

// We keep the `model` for descendant bindings, so we know whether we need to switch models. We don't quite do what
// the spec says for switching models: we do it statically instead of dynamically. Later, we might be able to get rid
// of the `model`.
sealed trait SingleItemBinding {
  val scope: Scope
  val model: Option[String]
}

// `bind` resolves independently from `model`, `context`, and `ref`.
case class BindSingleItemBinding(scope: Scope, model: Option[String], bind: String)                                               extends SingleItemBinding
case class RefSingleItemBinding (scope: Scope, model: Option[String], context: Option[String], ns: NamespaceMapping, ref: String) extends SingleItemBinding

/**
 * Abstract representation of a common XForms element supporting optional context, binding and value.
 */
abstract class ElementAnalysis(
  val index             : Int, // index of the element in the view
  val element           : Element,
  val parent            : Option[ElementAnalysis],
  val preceding         : Option[ElementAnalysis],
  val staticId          : String,
  val prefixedId        : String,
  val namespaceMapping  : NamespaceMapping,
  val scope             : Scope,
  val containerScope    : Scope
) extends ElementEventHandlers
     with ElementRepeats {

  selfElement =>

  import ElementAnalysis._

  require(element ne null)

  var model: Option[Model] = None
  var lang: LangRef = LangRef.Undefined

  def getLangUpdateIfUndefined: LangRef = lang match {
    case LangRef.Undefined =>
      val updated = parent map (_.getLangUpdateIfUndefined) getOrElse LangRef.None
      lang = updated
      updated
    case existing =>
      existing
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

  val closestAncestorInScope: Option[ElementAnalysis] = ElementAnalysis.getClosestAncestorInScope(selfElement, scope)

  // XPath analysis
  final var contextAnalysis: Option[XPathAnalysis] = None // only used during construction of `bindingAnalysis` and `valueAnalysis`
  final var bindingAnalysis: Option[XPathAnalysis] = None
  final var valueAnalysis  : Option[XPathAnalysis] = None // TODO: Shouldn't this go to special nested traits only?
  // LHHAAnalysis, StaticBind, VariableAnalysisTrait, ValueTrait

  def freeTransientState(): Unit = {
    contextAnalysis foreach (_.freeTransientState()) // TODO: Could also set to `None`, right?
    bindingAnalysis foreach (_.freeTransientState())
    valueAnalysis   foreach (_.freeTransientState())
  }
}

trait ElementEventHandlers {

  selfElement: ElementAnalysis =>

  import ElementAnalysis.HandlerAnalysis

  // Cache for event handlers
  // Use an immutable map and @volatile so that update are published to other threads accessing this static state.
  // NOTE: We could use `AtomicReference` but we just get/set so there is no benefit to it.
  @volatile private var handlersCache: Map[String, HandlerAnalysis] = Map.empty

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
  def handlersForEvent(eventName: String, findHandler: String => HandlerAnalysis): HandlerAnalysis =
    handlersCache.getOrElse(eventName, {
      val result = findHandler(eventName)
      handlersCache += eventName -> result
      result
    })
}

trait ElementRepeats {

  element: ElementAnalysis =>

  private def findElementInParentPart: Option[ElementAnalysis] =
    (ElementAnalysis.ancestorsIterator(element, includeSelf = false) collectFirst { case r: RootControl => r.elementInParent }).flatten

  // This control's ancestor repeats, computed on demand
  lazy val ancestorRepeats: List[RepeatControl] =
    parent match {
      case Some(parentRepeat: RepeatControl) => parentRepeat :: parentRepeat.ancestorRepeats
      case Some(parentElement)               => parentElement.ancestorRepeats
      case None                              => Nil
    }

  // Same as ancestorRepeats but across parts
  lazy val ancestorRepeatsAcrossParts: List[RepeatControl] =
    findElementInParentPart match {
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

  // Event handler information as a tuple:
  // - whether the default action needs to run
  // - all event handlers grouped by phase and observer prefixed id
  type HandlerAnalysis = (Boolean, Map[Phase, Map[String, List[EventHandler]]])

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

  class IteratorBase(
    start    : Option[ElementAnalysis],
    nextElem : ElementAnalysis => Option[ElementAnalysis]
  ) extends Iterator[ElementAnalysis] {

    private var theNext = start

    def hasNext: Boolean = theNext.isDefined
    def next(): ElementAnalysis = {
      val newResult = theNext.get
      theNext = nextElem(newResult)
      newResult
    }
  }

  // Iterator over the given control and its descendants
  // See also `ControlsIterator` which has the same logic! Should abstract that.
  class ElemIterator(
    private val start         : ElementAnalysis,
    private val includeSelf   : Boolean
  ) extends Iterator[ElementAnalysis] {

    private val children = start match {
      case c: WithChildrenTrait => c.children.iterator
      case _                    => Iterator.empty
    }

    private var descendants: Iterator[ElementAnalysis] = Iterator.empty

    private def findNext(): ElementAnalysis =
      if (descendants.hasNext)
        // Descendants of current child
        descendants.next()
      else if (children.hasNext) {
        // Move to next child
        val next = children.next()
        if (next.isInstanceOf[WithChildrenTrait])
          descendants = new ElemIterator(next, includeSelf = false)
        next
      } else
        null

    private var current =
      if (includeSelf)
        start
      else
        findNext()

    def next(): ElementAnalysis = {
      val result = current
      current = findNext()
      result
    }

    def hasNext: Boolean = current ne null
  }

  def iterateDescendants(
    start         : ElementAnalysis,
    includeSelf   : Boolean
  ): Iterator[ElementAnalysis] =
    new ElemIterator(start, includeSelf)

  /**
   * Return an iterator over all the element's ancestors.
   */
  def ancestorsIterator(start: ElementAnalysis, includeSelf: Boolean): Iterator[ElementAnalysis] =
    new IteratorBase(if (includeSelf) start.some else start.parent, _.parent)

  def ancestorsAcrossPartsIterator(start: ElementAnalysis, includeSelf: Boolean): Iterator[ElementAnalysis] =
    new IteratorBase(if (includeSelf) start.some else start.parent, _.parent) flatMap {
      case r: RootControl =>
        Iterator(r) ++ (r.elementInParent.iterator flatMap (ancestorsAcrossPartsIterator(_, includeSelf = true)))
      case e =>
        Iterator(e)
    }

  /**
   * Iterator over the element's preceding siblings.
   */
  def precedingSiblingIterator(start: ElementAnalysis): Iterator[ElementAnalysis] =
    new IteratorBase(start.preceding, _.preceding)

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
    attSet(element, qName) flatMap (Extensions.resolveQName(namespaces.mapping.get, _, unprefixedIsNoNamespace = true))

  def findChildElem(elem: ElementAnalysis, name: QName): Option[ElementAnalysis] =
    elem match {
      case wct: WithChildrenTrait => wct.children collectFirst { case c if c.element.getQName == name => c }
      case _                      => None
    }
}