package org.orbeon.web

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.web.DomEventNames._
import org.scalajs.dom
import org.scalajs.dom.{document, html}

import scala.annotation.tailrec
import scala.concurrent.{Future, Promise}
import scala.scalajs.js


object DomSupport {

  private var lastUsedSuffix: Int = 0

  def atLeastDomInteractiveF(doc: html.Document): Future[Unit] = {

    val promise = Promise[Unit]()

    if (doc.readyState == InteractiveReadyState || doc.readyState == CompleteReadyState) {

      // Because yes, the document is interactive, but JavaScript placed after us might not have run yet.
      // Although if we do everything in an async way, that should be changed.
      // TODO: Review once full order of JavaScript is determined in `App` doc.
      js.timers.setTimeout(0) {
        promise.success(())
      }
    } else {

      lazy val contentLoaded: js.Function1[dom.Event, _] = (_: dom.Event) => {
        doc.removeEventListener(DOMContentLoaded, contentLoaded)
        promise.success(())
      }

      doc.addEventListener(DOMContentLoaded, contentLoaded)
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
