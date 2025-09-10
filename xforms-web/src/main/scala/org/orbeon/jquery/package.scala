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
import _root_.io.udash.wrappers.jquery.{JQuery, JQueryEvent, JQueryStatic}

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import org.orbeon.oxf.util.CoreUtils.*


package object jquery {

  implicit class JqueryOps(private val j: JQuery) extends AnyVal {

    @inline private def asJsAny(body: => Any): js.Any = { body; () }

    def elem          : html.Element                   = headElem.get
    def headElem      : Option[html.Element]           = j.get(0).map(_.asInstanceOf[html.Element])
    def elems         : List[html.Element]             = j.get().toList.collect { case e: html.Element => e }
    def headJQuery    : Option[JQuery]                 = j.length > 0 option j.first()
    def headElemJQuery: Option[(html.Element, JQuery)] = j.length > 0 option (headElem.get, j.first())
  }

}
