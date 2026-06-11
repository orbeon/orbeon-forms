package org.orbeon.oxf.xforms.upload

import cats.data.NonEmptySet
import org.orbeon.datatypes.MediatypeRange
import org.orbeon.datatypes.MediatypeRange.WildcardMediatypeRange
import org.orbeon.oxf.util.StringUtils.*

import scala.collection.immutable.SortedSet


sealed trait AllowedMediatypes
object AllowedMediatypes {

  case object AllowedAnyMediatype                                            extends AllowedMediatypes
  case class  AllowedSomeMediatypes(mediatypes: NonEmptySet[MediatypeRange]) extends AllowedMediatypes {
    require(! mediatypes(WildcardMediatypeRange))
  }

  def unapply(s: String): Option[AllowedMediatypes] = {

    val mediatypeRanges =
      s.splitTo[List](" ,")
        .flatMap(_.trimAllToOpt)
        .flatMap(MediatypeRange.unapply)

    if (mediatypeRanges.isEmpty)
      None
    else if (mediatypeRanges contains WildcardMediatypeRange)
      Some(AllowedAnyMediatype)
    else
      Some(AllowedSomeMediatypes(NonEmptySet.fromSetUnsafe(SortedSet.from(mediatypeRanges))))
  }
}