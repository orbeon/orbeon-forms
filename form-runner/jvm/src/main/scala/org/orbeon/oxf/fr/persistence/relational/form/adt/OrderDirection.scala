/**
 * Copyright (C) 2024 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.relational.form.adt


sealed trait OrderDirection {
  val string: String
  val sql: String
}

object OrderDirection {
  case object Ascending  extends OrderDirection {
    override val string = "asc"
    override val sql    = "ASC"
  }

  case object Descending extends OrderDirection {
    override val string = "desc"
    override val sql    = "DESC"
  }

  def apply(s: String): OrderDirection = s.toLowerCase.trim match {
    case Ascending .string => Ascending
    case Descending.string => Descending
    case _                 => throw new IllegalArgumentException(s"Invalid order direction: $s")
  }
}
