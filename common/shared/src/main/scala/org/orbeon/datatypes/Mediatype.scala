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

import org.orbeon.datatypes.Mediatype.TypeOrSubtype._
import org.orbeon.datatypes.Mediatype._
import org.orbeon.datatypes.MediatypeRange._
import scala.collection.compat._


// Concrete mediatype, as opposed to `MediatypeRange` below
case class Mediatype(typ: SpecificType, subtype: SpecificType) {
  def is(range: MediatypeRange): Boolean = range match {
    case WildcardMediatypeRange             => true
    case WildcardTypeMediatypeRange(typ)    => typ == this.typ
    case SingletonMediatypeRange(mediatype) => mediatype == this
  }

  override def toString = s"${typ.value}/${subtype.value}"
}

object Mediatype {

  def unapply(s: String): Option[Mediatype] =
    MediatypeRange.unapply(s) collect { case SingletonMediatypeRange(mediatype) => mediatype }

  sealed trait TypeOrSubtype
  object TypeOrSubtype {

    // Try to follow IANA syntax
    private val TokenMatch = """^((?:(?![()<>@,;:\\"/\[\]?=])[\u0021-\u007E])+)$""".r

    case object WildcardType                extends TypeOrSubtype { override def toString = "*" }
    case class  SpecificType(value: String) extends TypeOrSubtype {
      require(TokenMatch.findFirstIn(value).nonEmpty)
      override def toString = value
    }

    def unapply(s: String): Option[TypeOrSubtype] = s match {
      case "*"                  => Some(WildcardType)
      case TokenMatch(specific) => Some(SpecificType(specific))
      case _                    => None
    }
  }
}

sealed trait MediatypeRange
object MediatypeRange {

  case object WildcardMediatypeRange                           extends MediatypeRange { override def toString = "*/*" }
  case class  WildcardTypeMediatypeRange(typ: SpecificType)    extends MediatypeRange { override def toString = s"$typ/*" }
  case class  SingletonMediatypeRange   (mediatype: Mediatype) extends MediatypeRange { override def toString = mediatype.toString }

  def unapply(s: String): Option[MediatypeRange] = {
    s.split("/", -1).to(List) match {
      case TypeOrSubtype(WildcardType)      :: TypeOrSubtype(WildcardType)          :: Nil => Some(WildcardMediatypeRange)
      case TypeOrSubtype(typ: SpecificType) :: TypeOrSubtype(WildcardType)          :: Nil => Some(WildcardTypeMediatypeRange(typ))
      case TypeOrSubtype(typ: SpecificType) :: TypeOrSubtype(subtype: SpecificType) :: Nil => Some(SingletonMediatypeRange(Mediatype(typ, subtype)))
      case _ => None
    }
  }
}
