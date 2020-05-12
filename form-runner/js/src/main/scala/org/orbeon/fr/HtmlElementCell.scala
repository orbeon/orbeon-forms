/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.fr

import org.orbeon.oxf.fr.CellOps
import org.orbeon.oxf.fr.ClientNames._
import org.scalajs.dom.ext._
import org.scalajs.dom.html
import org.scalajs.dom.html.Element
import scala.collection.compat._

// This contains grid/cell operations acting on `html.Element`, which is on the output of the form
// as seen in the browser.
object HtmlElementCell {

  implicit object HtmlElementCellOps extends CellOps[html.Element] {

    def attValueOpt    (u: html.Element, name: String): Option[String]     = Option(u.getAttribute(name))
    def children       (u: html.Element, name: String): List[html.Element] = u.children.to(List).asInstanceOf[List[html.Element]]
    def parent         (u: Element)                   : Element            = u.parentElement
    def hasChildElement(u: Element)                   : Boolean            = u.children.nonEmpty

    def cellsForGrid   (u: Element)                   : List[html.Element] = u.firstElementChild.firstElementChild.children.to(List).asInstanceOf[List[html.Element]]
    def gridForCell    (u: Element)                   : Element            = parent(parent(parent(u)))

    def maxGridWidth(u: Element): Int =
      if (u.firstElementChild.classList.contains("fr-grid-24")) 24 else 12

    def x(u: html.Element): Option[Int] = attValueOpt(u, AttX) map (_.toInt)
    def y(u: html.Element): Option[Int] = attValueOpt(u, AttY) map (_.toInt)
    def w(u: html.Element): Option[Int] = attValueOpt(u, AttW) map (_.toInt)
    def h(u: html.Element): Option[Int] = attValueOpt(u, AttH) map (_.toInt)

    def updateX(u: html.Element, x: Int): Unit =
      u.setAttribute(AttX, x.toString)

    def updateY(u: html.Element, y: Int): Unit =
      u.setAttribute(AttY, y.toString)

    def updateH(u: html.Element, h: Int): Unit =
      if (h > 1)
        u.setAttribute(AttH, h.toString)
      else
        u.removeAttribute(AttH)

    def updateW(u: Element, w: Int): Unit =
      u.setAttribute("data-w", w.toString)
  }

}
