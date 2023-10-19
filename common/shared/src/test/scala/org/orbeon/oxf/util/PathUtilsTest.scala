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

  describe("recombineQuery") {
    val Expected = List(
      ("http://orbeon.com",         List(),                       false, "http://orbeon.com"),
      ("http://orbeon.com",         List("a" -> "b"),             false, "http://orbeon.com?a=b"),
      ("http://orbeon.com",         List("a" -> "b", "c" -> "d"), false, "http://orbeon.com?a=b&c=d"),
      ("http://orbeon.com?a=a",     List(),                       false, "http://orbeon.com?a=a"),
      ("http://orbeon.com?a=a",     List("a" -> "b"),             false, "http://orbeon.com?a=a&a=b"),
      ("http://orbeon.com?a=a",     List("a" -> "b", "c" -> "d"), false, "http://orbeon.com?a=a&a=b&c=d"),
      ("http://orbeon.com",         List(),                       true,  "http://orbeon.com"),
      ("http://orbeon.com",         List("a" -> "b"),             true,  "http://orbeon.com?a=b"),
      ("http://orbeon.com",         List("a" -> "b", "c" -> "d"), true,  "http://orbeon.com?a=b&c=d"),
      ("http://orbeon.com?a=a",     List(),                       true,  "http://orbeon.com?a=a"),
      ("http://orbeon.com?a=a",     List("a" -> "b"),             true,  "http://orbeon.com?a=b"),
      ("http://orbeon.com?a=a",     List("a" -> "b", "c" -> "d"), true,  "http://orbeon.com?a=b&c=d"),
      ("http://orbeon.com?a=a&c=c", List(),                       true,  "http://orbeon.com?a=a&c=c"),
      ("http://orbeon.com?a=a&c=c", List("a" -> "b"),             true,  "http://orbeon.com?c=c&a=b"),
      ("http://orbeon.com?a=a&c=c", List("a" -> "b", "c" -> "d"), true,  "http://orbeon.com?a=b&c=d"),
    )
    for ((path, params, overwrite, expected) <- Expected)
      it (s"must pass for `$path`/`$params` / `$overwrite`") {
        assert(expected == PathUtils.recombineQuery(path, params, overwrite))
      }
  }
}
