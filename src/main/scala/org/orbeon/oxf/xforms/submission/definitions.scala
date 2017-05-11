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
package org.orbeon.oxf.xforms.submission

import enumeratum._

sealed abstract class ReplaceType extends EnumEntry

object ReplaceType extends Enum[ReplaceType] {

  val values = findValues

  case object All      extends ReplaceType
  case object Instance extends ReplaceType
  case object Text     extends ReplaceType
  case object None     extends ReplaceType

  // For Java callers
  def isReplaceAll     (replaceType: ReplaceType) = replaceType == All
  def isReplaceInstance(replaceType: ReplaceType) = replaceType == Instance
  def isReplaceText    (replaceType: ReplaceType) = replaceType == Text
  def isReplaceNone    (replaceType: ReplaceType) = replaceType == None
}
