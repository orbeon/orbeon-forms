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

import org.orbeon.datatypes.{Coordinate1, Direction}
import org.orbeon.fr.HtmlElementCell._
import org.orbeon.oxf.fr.Cell
import org.scalajs.dom.html

// NOTE: This is currently in the `fr` package and project, but probably should be in the `builder` package and project.
// We put it in
object Grid {

 def canDeleteRow(cellElem: html.Element): Boolean = {

    val cells = Cell.analyze12ColumnGridAndFillHoles(cellElem.parentElement, simplify = false)

    ???
  }

  def canMoveToCoordinate(cellElem: html.Element, coordinate: Coordinate1): Boolean = {

    val cells = Cell.analyze12ColumnGridAndFillHoles(cellElem.parentElement, simplify = false)

    ???
  }

  def spaceToExtendCell(cellElem: html.Element, direction: Direction): Int = {
    val cells = Cell.analyze12ColumnGridAndFillHoles(cellElem.parentElement, simplify = false)
    Cell.spaceToExtendCell(cells, cellElem, direction)
  }

}
