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


sealed trait Select {
  def string: String
}

object Select {
  case object Local extends Select {
    override val string = "local"
  }

  case class Remote(url: String) extends Select {
    override val string = url
  }

  case object Min extends Select {
    override val string = "min"
  }

  case object Max extends Select {
    override val string = "max"
  }

  case object Or extends Select {
    override val string = "or"
  }

  case object And extends Select {
    override val string = "and"
  }

  def apply(s: String): Select = s.toLowerCase.trim match {
    case Local.string => Local
    case Min.string   => Min
    case Max.string   => Max
    case Or.string    => Or
    case And.string   => And
    case _            => Remote(s)
  }
}

