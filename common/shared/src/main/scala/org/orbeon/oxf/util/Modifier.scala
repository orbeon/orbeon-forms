/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.util

import enumeratum.EnumEntry.Lowercase
import enumeratum.{CirceEnum, Enum, EnumEntry}

import StringUtils._

// NOTE: We place this in a separate module also to help with Circe issues, see:
// https://github.com/circe/circe/issues/639
sealed trait Modifier extends EnumEntry with Lowercase

object Modifier extends Enum[Modifier] with CirceEnum[Modifier] {

  val values = findValues

  case object Shift extends Modifier
  case object Ctrl  extends Modifier
  case object Alt   extends Modifier
  case object Meta  extends Modifier

  def parseStringToSet(s: String): Set[Modifier] = {
    s.splitTo[Set]() map (_.toLowerCase) map {
        case "control" => "ctrl"
        case "option"  => "alt"
        case "command" => "meta"
        case other     => other
      } map
        withNameLowercaseOnly
  }
}
