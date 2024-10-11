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

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.NodeInfoOps


sealed trait LocalRemoteOrCombinator {
  def string: String
}

object LocalRemoteOrCombinator {
  sealed trait LocalOrRemote extends LocalRemoteOrCombinator
  sealed trait Combinator    extends LocalRemoteOrCombinator

  case object Local extends LocalOrRemote {
    override val string = "local"
  }

  case class Remote(url: String) extends LocalOrRemote {
    override val string = url
  }

  case object Min extends Combinator {
    override val string = "min"
  }

  case object Max extends Combinator {
    override val string = "max"
  }

  case object Or extends Combinator {
    override val string = "or"
  }

  case object And extends Combinator {
    override val string = "and"
  }

  def apply(xml: NodeInfo): LocalRemoteOrCombinator = {
    val urlOpt        = xml.attValueOpt("url")
    val combinatorOpt = xml.attValueOpt("combinator")

    urlOpt match {
      case Some(url)          => combinatorOpt match {
        case None             => Remote(url)
        case Some(_)          => throw new IllegalArgumentException("Query cannot have both a URL and a combinator")
      }
      case None               => combinatorOpt match {
        case None             => Local // No URL, no combinator
        case Some(combinator) => combinator.toLowerCase.trim match {
          case Min.string     => Min
          case Max.string     => Max
          case Or.string      => Or
          case And.string     => And
          case _              => throw new IllegalArgumentException(s"Unknown combinator: $combinator")
        }
      }
    }
  }
}

