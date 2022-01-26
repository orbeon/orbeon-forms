package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpec


class PathUtilsTest extends AnyFunSpec {

  describe("maybeReplaceExtension") {
    val Expected = List(
      ("/path/to/My File"          , "jpg", None),
      ("/path/to/My File.jpg"      , "",    Some("/path/to/My File"         )),
      ("/path/to/My File.jpg"      , "jpg", Some("/path/to/My File.jpg"     )),
      ("C:\\path\\to\\My File.jpg" , "jpg", Some("C:\\path\\to\\My File.jpg")),
      ("My File.jpg"               , "jpg", Some("My File.jpg"              )),
      ("/path/to/My File.jpg"      , "png", Some("/path/to/My File.png"     )),
      ("C:\\path\\to\\My File.jpg" , "png", Some("C:\\path\\to\\My File.png")),
      ("My File.jpg"               , "png", Some("My File.png"              )),
    )
    for ((path, ext, expected) <- Expected)
      it (s"must pass for `$path`/`$ext`") {
        assert(expected == PathUtils.maybeReplaceExtension(path, ext))
      }

  }

  describe("filenameFromPath") {
    val Expected = List(
      "/path/to/My File.jpg"      -> "My File.jpg",
      "C:\\path\\to\\My File.jpg" -> "My File.jpg",
      "My File.jpg"               -> "My File.jpg",
    )
    for ((path, expected) <- Expected)
      it (s"must pass for `$path`") {
        assert(expected == PathUtils.filenameFromPath(path))
      }
  }

}
