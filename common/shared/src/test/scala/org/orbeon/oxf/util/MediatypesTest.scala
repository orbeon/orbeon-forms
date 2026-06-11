package org.orbeon.oxf.util

import org.orbeon.datatypes.Mediatype.TypeOrSubtype.SpecificType
import org.scalatest.funspec.AnyFunSpec


class MediatypesTest extends AnyFunSpec {

  describe("`findExtensionsForSpecificType`") {

    it("must return well-known image extensions for the `image` type") {
      val result = Mediatypes.findExtensionsForSpecificType(SpecificType("image"))
      assert(result.contains("jpg"))
      assert(result.contains("png"))
      assert(result.contains("gif"))
      assert(result.contains("svg"))
    }

    it("must return well-known video extensions for the `video` type") {
      val result = Mediatypes.findExtensionsForSpecificType(SpecificType("video"))
      assert(result.contains("mp4"))
      assert(result.contains("avi"))
      assert(result.contains("mov"))
    }

    it("must return well-known audio extensions for the `audio` type") {
      val result = Mediatypes.findExtensionsForSpecificType(SpecificType("audio"))
      assert(result.contains("mp3"))
      assert(result.contains("wav"))
    }

    it("must return well-known font extensions for the `font` type") {
      val result = Mediatypes.findExtensionsForSpecificType(SpecificType("font"))
      assert(result.contains("ttf"))
      assert(result.contains("otf"))
    }

    it("must return an empty list for an unknown type") {
      assert(Mediatypes.findExtensionsForSpecificType(SpecificType("nonexistent")) == Nil)
    }

    it("must include all extensions of known mediatypes with the matching type") {
      val cases = List(
        "image" -> List("image/jpeg", "image/png", "image/gif"),
        "video" -> List("video/mp4", "video/mpeg"),
        "audio" -> List("audio/mpeg", "audio/wav"),
        "font"  -> List("font/ttf", "font/otf"),
      )
      for ((typ, mediatypes) <- cases) {
        val specificType = SpecificType(typ)
        val result       = Mediatypes.findExtensionsForSpecificType(specificType)
        for (mt <- mediatypes) {
          val mtExtensions = Mediatypes.findExtensionsForMediatype(mt)
          assert(
            mtExtensions.forall(result.contains),
            s"Extensions for `$mt` ($mtExtensions) are not all present in the result for type `$typ`"
          )
        }
      }
    }
  }
}
