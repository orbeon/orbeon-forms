package org.orbeon.oxf.xforms.upload

import org.orbeon.datatypes.MediatypeRange
import org.orbeon.datatypes.MediatypeRange.WildcardMediatypeRange
import org.orbeon.oxf.util.StringUtils._

import scala.collection.compat._


sealed trait AllowedMediatypes
object AllowedMediatypes {

  case object AllowedAnyMediatype                                    extends AllowedMediatypes
  case class  AllowedSomeMediatypes(mediatypes: Set[MediatypeRange]) extends AllowedMediatypes {
    require(! mediatypes(WildcardMediatypeRange))
  }

  def unapply(s: String): Option[AllowedMediatypes] = {

    val mediatypeRanges =
      s.splitTo[List](" ,") flatMap { token =>
        token.trimAllToOpt
      } flatMap { trimmed =>
          MediatypeRange.unapply(trimmed)
      }

    if (mediatypeRanges.isEmpty)
      None
    else if (mediatypeRanges contains WildcardMediatypeRange)
      Some(AllowedAnyMediatype)
    else
      Some(AllowedSomeMediatypes(mediatypeRanges.to(Set)))
  }
}