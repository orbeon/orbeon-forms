package org.orbeon.web

import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.web.DomEventNames.*
import org.scalajs.dom
import org.scalajs.dom.{DocumentReadyState, Event, HTMLCollection, MutationObserver, MutationObserverInit, document, html}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.scalajs.js


object DomSupport {

  implicit class DomElemOps[T >: html.Element <: dom.Element](private val elem: T) extends AnyVal {

    def querySelectorAllT(selectors: String): collection.Seq[T] =
      elem.querySelectorAll(selectors).asInstanceOf[dom.NodeList[T]]

    def querySelectorT(selectors: String): T =
      elem.querySelector(selectors).asInstanceOf[T]

    def querySelectorOpt(selectors: String): Option[T] =
      Option(elem.querySelector(selectors).asInstanceOf[T])

    def previousElementSiblings: Iterator[T] =
      Iterator.iterate(elem.previousElementSibling.asInstanceOf[T])(_.previousElementSibling.asInstanceOf[T]).takeWhile(_ ne null)

    def previousElementSiblings(selector: String): Iterator[T] =
      elem.previousElementSiblings.filter(_.matches(selector))

    def previousElementOpt: Option[T] =
      elem.previousElementSiblings.nextOption()

    def nextElementSiblings: Iterator[T] =
      Iterator.iterate(elem.nextElementSibling.asInstanceOf[T])(_.nextElementSibling.asInstanceOf[T]).takeWhile(_ ne null)

    def nextElementSiblings(selector: String): Iterator[T] =
      elem.nextElementSiblings.filter(_.matches(selector))

    def nextElementOpt: Option[T] =
      elem.nextElementSiblings.nextOption()

    def nextSiblings: Iterator[dom.Node] =
      Iterator.iterate(elem.nextSibling)(_.nextSibling).takeWhile(_ ne null)

    def closestT(selector: String): T =
      elem.closest(selector).asInstanceOf[T]

    def closestOpt(selector: String): Option[T] =
      Option(elem.closestT(selector))

    def childrenT: collection.Seq[T] =
      elem.children.asInstanceOf[HTMLCollection[T]]

    def childrenT(selector: String): collection.Seq[T] =
      elem.childrenT.filter(_.matches(selector))

    def parentElementOpt: Option[T] =
      Option(elem.asInstanceOf[js.Dynamic].parentElement.asInstanceOf[T])

    def ancestorOrSelfElem: Iterator[T] =
      Iterator.iterate(elem)(_.asInstanceOf[js.Dynamic].parentElement.asInstanceOf[T]).takeWhile(_ ne null)

    def ancestorOrSelfElem(selector: String): Iterator[T] =
      ancestorOrSelfElem.filter(_.matches(selector))

    def appendChildT[U <: dom.Node](newChild: U): U =
      elem.appendChild(newChild).asInstanceOf[U]
  }

  implicit class DomDocOps(private val doc: html.Document) extends AnyVal {

    def documentElementT: html.Element =
      doc.documentElement.asInstanceOf[html.Element]

    def activeElementT: html.Element =
      doc.activeElement.asInstanceOf[html.Element]

    def getElementByIdT(elementId: String): html.Element =
      doc.getElementById(elementId).asInstanceOf[html.Element]

    def getElementByIdOpt(elementId: String): Option[html.Element] =
      Option(doc.getElementById(elementId).asInstanceOf[html.Element])

    def createElementT(tagName: String): html.Element =
      doc.createElement(tagName).asInstanceOf[html.Element]

    def querySelectorAllT(selectors: String): collection.Seq[html.Element] =
      doc.querySelectorAll(selectors).asInstanceOf[dom.NodeList[html.Element]]

    def querySelectorT(selectors: String): html.Element =
      doc.querySelector(selectors).asInstanceOf[html.Element]

    def querySelectorOpt(selectors: String): Option[html.Element] =
      Option(querySelectorT(selectors))

    def createScriptElement: html.Script =
      doc.createElement("script").asInstanceOf[html.Script]

    def createLinkElement: html.Link =
      doc.createElement("link").asInstanceOf[html.Link]

    def createFormElement: html.Form =
      doc.createElement("form").asInstanceOf[html.Form]

    def createInputElement: html.Input =
      doc.createElement("input").asInstanceOf[html.Input]

    def createOptionElement: html.Option =
      doc.createElement("option").asInstanceOf[html.Option]

    def createOptGroupElement: html.OptGroup =
      doc.createElement("optgroup").asInstanceOf[html.OptGroup]
  }

  implicit class DomEventOps(private val event: dom.Event) extends AnyVal {

    def targetT: html.Element =
      event.target.asInstanceOf[html.Element]

    def targetOpt: Option[html.Element] =
      Option(event.targetT)

  }

  private var lastUsedSuffix: Int = 0

  private val AtLeastDomInteractiveStates = Set(DocumentReadyState.interactive, DocumentReadyState.complete)
  private val DomCompleteStates           = Set(DocumentReadyState.complete)

  sealed trait DomReadyState
  case object DomReadyState {
    case object Interactive extends DomReadyState // doc parsed but scripts, images, stylesheets and frames are still loading
    case object Complete    extends DomReadyState // doc and all sub-resources have finished loading, `load` about to fire
  }

  private def interactiveReadyState(doc: html.Document, state: DomReadyState): Boolean =
    state == DomReadyState.Interactive && AtLeastDomInteractiveStates(doc.readyState) ||
    state == DomReadyState.Complete    && DomCompleteStates(doc.readyState)

  def atLeastDomReadyStateF(doc: html.Document, state: DomReadyState): Future[Unit] = {

    val promise = Promise[Unit]()

    if (interactiveReadyState(doc, state)) {

      // Because yes, even if the document is interactive, JavaScript placed after us might not have run yet.
      // Although if we do everything in an async way, that should be changed.
      // TODO: Review once full order of JavaScript is determined in `App` doc.
      js.timers.setTimeout(0) {
        promise.success(())
      }: Unit
    } else {

      lazy val readyStateChanged: js.Function1[dom.Event, _] = (_: dom.Event) =>
        if (interactiveReadyState(doc, state)) {
          doc.removeEventListener(ReadystateChange, readyStateChanged)
          promise.success(())
        }

      doc.addEventListener(ReadystateChange, readyStateChanged)
    }

    promise.future
  }

  def findCommonAncestor(elems: List[html.Element]): Option[html.Element] = {

    def findFirstCommonAncestorForPair(elem1: html.Element, elem2: html.Element): Option[html.Element] =
      elem1.ancestorOrSelfElem.toList.reverseIterator
        .zip(elem2.ancestorOrSelfElem.toList.reverseIterator)
        .takeWhile { case (e1, e2) => e1.isSameNode(e2) }
        .lastOption()
        .map(_._1)

    @tailrec
    def recurse(elems: List[html.Element]): Option[html.Element] = {
      elems match {
        case Nil =>
          None
        case elem1 :: Nil =>
          Some(elem1)
        case elem1 :: elem2 :: rest =>
          findFirstCommonAncestorForPair(elem1, elem2) match {
            case Some(elem) => recurse(elem :: rest)
            case None       => None
          }
        case _ =>
          None
      }
    }

    recurse(elems)
  }

  def generateIdIfNeeded(element: dom.Element): String = {
    if (element.id == "") {
      def id(suffix: Int)       = s"xf-client-$suffix"
      def isUnused(suffix: Int) = document.getElementById(id(suffix)) == null
      val suffix                = Iterator.from(lastUsedSuffix + 1).find(isUnused).get
      element.id                = id(suffix)
      lastUsedSuffix            = suffix
    }
    element.id
  }

  def moveIntoViewIfNeeded(
    containerElem : html.Element,
    innerContainer: html.Element,
    itemElem      : html.Element,
    margin        : Int
  ): Unit = {
    val containerRect       = containerElem.getBoundingClientRect()
    val itemRect            = itemElem.getBoundingClientRect()
    val isEntirelyContained =
      itemRect.left   >= containerRect.left   &&
      itemRect.top    >= containerRect.top    &&
      itemRect.bottom <= containerRect.bottom &&
      itemRect.right  <= containerRect.right
    if (! isEntirelyContained) {

      val overflowsBelow = itemRect.bottom > containerRect.bottom

      val mainInnerRect = innerContainer.getBoundingClientRect()
      val scrollTop =
        if (overflowsBelow)
          containerRect.top - mainInnerRect.top + itemRect.bottom - containerRect.bottom + margin
        else
          containerRect.top - mainInnerRect.top - (containerRect.top - itemRect.top + margin)

      containerElem.asInstanceOf[js.Dynamic].scrollTo(
        js.Dynamic.literal(top = scrollTop, behavior = "smooth")
      )
    }
  }

  def onAttributeChange(
    element       : dom.Element,
    attributeName : String,
    listener      : () => Unit
  ): MutationObserver = {
    val observer = new MutationObserver((_, _) => listener())
    observer.observe(element, new MutationObserverInit {
      attributes      = true
      attributeFilter = js.Array(attributeName)
    })
    observer
  }

  def onElementFoundOrAdded(
    container : html.Element,
    selector  : String,
    listener  : html.Element => Unit
  ): MutationObserver = {
    container.querySelectorAllT(selector).foreach(listener)
    val observer = new MutationObserver((mutations, _) => {
      mutations.foreach { mutation =>
        mutation.addedNodes.foreach { node =>
          if (node.nodeType == dom.Node.ELEMENT_NODE) {
            val element = node.asInstanceOf[html.Element]
            if (element.matches(selector))
              listener(element)
          }
        }
      }
    })
    val config = new MutationObserverInit { childList = true ; subtree = true }
    observer.observe(container, config)
    observer
  }
}
