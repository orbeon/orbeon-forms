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
package org.orbeon.datatypes

import enumeratum.EnumEntry.Lowercase
import enumeratum._

sealed abstract class Direction extends EnumEntry with Lowercase

object Direction extends Enum[Direction] {

  val values = findValues

  case object Up    extends Direction
  case object Down  extends Direction
  case object Left  extends Direction
  case object Right extends Direction
}

sealed abstract class AboveBelow extends EnumEntry with Lowercase

object AboveBelow extends Enum[AboveBelow] {

  val values = findValues

  case object Above extends AboveBelow
  case object Below extends AboveBelow
}