package org.orbeon.web

import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.web.DomEventNames.*
import org.scalajs.dom
import org.scalajs.dom.{DocumentReadyState, document, html}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.scalajs.js


object DomSupport {

  implicit class DomElemOps[T <: dom.Element](private val elem: T) extends AnyVal {

    def querySelectorAllT(selectors: String): dom.NodeList[T] =
      elem.querySelectorAll(selectors).asInstanceOf[dom.NodeList[T]]

    def querySelectorT(selectors: String): T =
      elem.querySelector(selectors).asInstanceOf[T]

    def querySelectorOpt(selectors: String): Option[T] =
      Option(elem.querySelector(selectors).asInstanceOf[T])
  }

  implicit class DomDocOps(private val doc: html.Document) extends AnyVal {

    def getElementByIdT(elementId: String): html.Element =
      doc.getElementById(elementId).asInstanceOf[html.Element]

    def getElementByIdOpt(elementId: String): Option[html.Element] =
      Option(doc.getElementById(elementId).asInstanceOf[html.Element])

    def createElementT(tagName: String): html.Element =
      doc.createElement(tagName).asInstanceOf[html.Element]

    def querySelectorAllT(selectors: String): dom.NodeList[html.Element] =
      doc.querySelectorAll(selectors).asInstanceOf[dom.NodeList[html.Element]]

    def querySelectorT(selectors: String): html.Element =
      doc.querySelector(selectors).asInstanceOf[html.Element]

    def querySelectorOpt(selectors: String): Option[html.Element] =
      Option(querySelectorT(selectors))
  }

  private var lastUsedSuffix: Int = 0

  val AtLeastDomInteractiveStates = Set(DocumentReadyState.interactive, DocumentReadyState.complete)
  val DomCompleteStates           = Set(DocumentReadyState.complete)

  sealed trait DomReadyState
  case object DomReadyState {
    case object Interactive extends DomReadyState // doc parsed but scripts, images, stylesheets and frames are still loading
    case object Complete    extends DomReadyState // doc and all sub-resources have finished loading, `load` about to fire
  }

  def interactiveReadyState(doc: html.Document, state: DomReadyState): Boolean =
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

  def ancestorOrSelfElem(elem: html.Element): Iterator[html.Element] =
    Iterator.iterate(elem)(_.parentElement).takeWhile(_ ne null)

  def findCommonAncestor(elems: List[html.Element]): Option[html.Element] = {

    def findFirstCommonAncestorForPair(elem1: html.Element, elem2: html.Element): Option[html.Element] =
      ancestorOrSelfElem(elem1).toList.reverseIterator
        .zip(ancestorOrSelfElem(elem2).toList.reverseIterator)
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
}
