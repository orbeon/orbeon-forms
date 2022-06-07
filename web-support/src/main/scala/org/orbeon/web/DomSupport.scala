package org.orbeon.web

import org.orbeon.web.DomEventNames._
import org.scalajs.dom
import org.scalajs.dom.{document, html}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js


object DomSupport {

  private var lastUsedSuffix: Int = 0

  def atLeastDomInteractiveF(doc: html.Document): Future[Unit] = {

//      scribe.debug(s"document state is `${doc.readyState}`")

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
//          scribe.debug(s"$DOMContentLoaded handler called")
        doc.removeEventListener(DOMContentLoaded, contentLoaded)
        promise.success(())
      }

      doc.addEventListener(DOMContentLoaded, contentLoaded)
    }

    promise.future
  }

  def generateIdIfNeeded(element: html.Element): String = {
    if (element.id == "") {
      def id(suffix: Int)       = s"orbeon-gen-$suffix"
      def isUnused(suffix: Int) = document.getElementById(id(suffix)) == null
      val suffix                = Iterator.from(lastUsedSuffix + 1).find(isUnused).get
      element.id                = id(suffix)
      lastUsedSuffix            = suffix
    }
    element.id
  }
}
