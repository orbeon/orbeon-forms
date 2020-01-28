/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon

import org.scalajs.dom.html
import org.scalajs.jquery.{JQuery, JQueryEventObject, JQueryStatic}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import org.orbeon.oxf.util.CoreUtils._


package object jquery {

  implicit class JqueryOps(private val j: JQuery) extends AnyVal {

    @inline private def asJsAny(body: => Any): js.Any = { body; () }

    def onWithSelector(events: String, selector: String, handler: JQueryEventObject => _): Unit =
      j.on(
        events   = events,
        selector = selector,
        handler  = ((e: JQueryEventObject) => asJsAny(handler(e))): js.Function1[JQueryEventObject, js.Any]
      )

    def headElem      : Option[html.Element]           = j.length > 0 option j(0)
    def headJQuery    : Option[JQuery]                 = j.length > 0 option j.first()
    def headElemJQuery: Option[(html.Element, JQuery)] = j.length > 0 option (j(0), j.first())
  }

  implicit class JqueryStaticOps(private val j: JQueryStatic) extends AnyVal {

    // Expose jQuery's `$(function)` as a `Future`
    def readyF(implicit executor: ExecutionContext): Future[Unit] =
      j.when(j.asInstanceOf[js.Dynamic].ready).asInstanceOf[js.Thenable[js.Any]].toFuture map (_ => ())
  }
}
