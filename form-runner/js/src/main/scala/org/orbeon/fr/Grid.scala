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

import org.orbeon.datatypes.Direction
import org.orbeon.fr.HtmlElementCell._
import org.orbeon.oxf.fr.Cell
import org.orbeon.oxf.util.CoreUtils._
import org.scalajs.dom.html

object Grid {

  def spaceToExtendCell(cellElem: html.Element, direction: Direction): Int = {

    val cells = Cell.analyze12ColumnGridAndFillHoles(cellElem.parentElement, mergeHoles = true, simplify = false)

    cells.iterator.flatten find (_.u exists (_ eq cellElem)) flatMap { cell ⇒

      direction match {
        case Direction.Right ⇒

          val cellToTheRightX = cell.x + cell.w

          cellToTheRightX <= Cell.StandardGridWidth option {

            val cellsToTheRight =
              cell.y until cell.y + cell.h map (iy ⇒ cells(iy - 1)(cellToTheRightX - 1))

            cellsToTheRight.iterator map (c ⇒ if (c.u.nonEmpty) 0 else c.w) min

          }

        case Direction.Down ⇒

          val cellUnderRightY = cell.y + cell.h

          cellUnderRightY <= cells.size option {

            val cellsUnder =
              cell.x until cell.x + cell.w map (ix ⇒ cells(cellUnderRightY - 1)(ix - 1))

            cellsUnder.iterator map (c ⇒ if (c.u.nonEmpty) 0 else c.h) min
          }

        case Direction.Left ⇒ ???
        case Direction.Up   ⇒ ???
      }
    } getOrElse 0
  }

}
