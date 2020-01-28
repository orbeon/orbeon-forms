/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.datatypes

import org.orbeon.oxf.util.NumericUtils
import org.orbeon.oxf.util.StringUtils._

sealed trait MaximumSize

object MaximumSize {

  // Ideally we would have a [`ULong` type](http://docs.scala-lang.org/sips/rejected/unsigned-integers.html)
  case class  LimitedSize(size: Long) extends MaximumSize { require(size >= 0) }
  case object UnlimitedSize           extends MaximumSize

  // Return `None` if blank, not a long number, or lower than -1
  def unapply(s: String): Option[MaximumSize] =
    s.trimAllToOpt flatMap NumericUtils.parseLong match {
      case Some(l) if l >= 0  => Some(LimitedSize(l))
      case Some(l) if l == -1 => Some(UnlimitedSize)
      case _                  => None
    }

  def convertToLong(maximumSize: MaximumSize): Long = maximumSize match {
    case LimitedSize(l) => l
    case UnlimitedSize  => -1L
  }
}