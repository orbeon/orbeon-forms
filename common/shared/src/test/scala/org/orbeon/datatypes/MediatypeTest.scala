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

import org.orbeon.datatypes.Mediatype.TypeOrSubtype.SpecificType
import org.orbeon.datatypes.MediatypeRange.{SingletonMediatypeRange, WildcardMediatypeRange, WildcardTypeMediatypeRange}
import org.scalatest.funspec.AnyFunSpec
import scala.collection.compat._


class MediatypeTest extends AnyFunSpec {

  val DisallowedChars               = """()<>@,;:\"/[]?=é天\u007F\u0000\u0010 """.to(List)
  val MediatypesWithDisallowedChars = DisallowedChars map (char => s"application/fo${char}o")

  val InvalidMediatypesOrMediatypeRanges = List("*/jpeg", "image/", "image") ::: MediatypesWithDisallowedChars

  describe("Invalid mediatypes") {
    for (mediatypeString <- List("*/*", "image/*") ::: InvalidMediatypesOrMediatypeRanges)
      it(s"must fail with `$mediatypeString`") {
        assert(Mediatype.unapply(mediatypeString).isEmpty)
      }
  }

  describe("Valid mediatypes") {

    val expectations = List(
      "application/pdf"                         -> Mediatype(SpecificType("application"), SpecificType("pdf")),
      "image/jpeg"                              -> Mediatype(SpecificType("image"),       SpecificType("jpeg")),
      "application/atom+xml"                    -> Mediatype(SpecificType("application"), SpecificType("atom+xml")),
      "application/vnd.oasis.opendocument.text" -> Mediatype(SpecificType("application"), SpecificType("vnd.oasis.opendocument.text")),
      "video/mp4"                               -> Mediatype(SpecificType("video"),       SpecificType("mp4"))
    )

    for ((mediatypeString, mediatype) <- expectations)
      it(s"must succeed with `$mediatypeString`") {
        assert(Mediatype.unapply(mediatypeString).contains(mediatype))
      }
  }

  describe("Invalid mediatype ranges") {
    for (mediatypeString <- InvalidMediatypesOrMediatypeRanges)
      it(s"must fail with `$mediatypeString`") {
        assert(MediatypeRange.unapply(mediatypeString).isEmpty)
      }
  }

  describe("Valid mediatype ranges") {

    val expectations = List(
      "*/*"        -> WildcardMediatypeRange,
      "image/*"    -> WildcardTypeMediatypeRange(SpecificType("image")),
      "image/jpeg" -> SingletonMediatypeRange(Mediatype(SpecificType("image"), SpecificType("jpeg")))
    )

    for ((mediatypeString, mediatypeRange) <- expectations)
      it(s"must succeed with`$mediatypeString`") {
        assert(MediatypeRange.unapply(mediatypeString).contains(mediatypeRange))
      }
  }

}
