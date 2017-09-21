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
import org.scalajs.dom.ext._
import org.scalajs.dom.html


object HtmlElementCell {

  private val RowspanName = "data-h"

  implicit object HtmlElementCellOps extends CellOps[html.Element] {

    def children   (u: html.Element, name: String): List[html.Element] = u.children.to[List].asInstanceOf[List[html.Element]]
    def attValueOpt(u: html.Element, name: String): Option[String]     = Option(u.getAttribute(name))

    def x(u: html.Element): Option[Int] = attValueOpt(u, "data-x") map (_.toInt)
    def y(u: html.Element): Option[Int] = attValueOpt(u, "data-y") map (_.toInt)
    def w(u: html.Element): Option[Int] = attValueOpt(u, "data-w") map (_.toInt)
    def h(u: html.Element): Option[Int] = attValueOpt(u, "data-h") map (_.toInt)

    def updateH(u: html.Element, rowspan: Int): Unit =
      if (rowspan > 1)
        u.setAttribute(RowspanName, rowspan.toString)
      else
        u.removeAttribute(RowspanName)
  }

}
