/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.compat._


class URLFinderTest extends AnyFunSpecLike {

  import URLFinder._

  describe("The `findURLs` function") {

    val expected = List(
      """www.google.com"""                                           -> List("""www.google.com"""),
      """http://www.google.com"""                                    -> List("""http://www.google.com"""),
      """https://www.google.com"""                                   -> List("""https://www.google.com"""),
      """http://www.google.com,"""                                   -> List("""http://www.google.com"""),
      """http://www.google.com."""                                   -> List("""http://www.google.com"""),
      """http://www.google.com:"""                                   -> List("""http://www.google.com"""),
      """this (http://www.google.com) works"""                       -> List("""http://www.google.com"""),
      """this http://userid:password@example.com:8080/, works"""     -> List("""http://userid:password@example.com:8080/"""),
      """this http://223.255.255.254, works"""                       -> List("""http://223.255.255.254"""),
      """this http://foo.bar/?q=Test%20URL-encoded%20stuff, works""" -> List("""http://foo.bar/?q=Test%20URL-encoded%20stuff"""),
      """naked google.com URL and another (http://twitter.com/)"""   -> List("""google.com""", """http://twitter.com/"""),
      """trailing / works too for google.com/."""                    -> List("""google.com/"""),
      """this info@orbeon.com works"""                               -> List("""info@orbeon.com"""),
      """this info@orbeon.com@ works"""                              -> Nil,
      """this (info@orbeon.com) works"""                             -> List("""info@orbeon.com"""),
      """this (info@orbeon.com, ada.lovelace@london.uk) works"""     -> List("""info@orbeon.com""", """ada.lovelace@london.uk"""),
      """this (INFO@ORBEON.COM) works"""                             -> List("""INFO@ORBEON.COM"""),
      """this info@orbeon.com/ works"""                              -> List("""info@orbeon.com"""),
      """this mailto:info@orbeon.com works"""                        -> List("""info@orbeon.com""")
    )

    it("must find all the URLs") {
      for ((in, out) <- expected)
        assert(out == findURLs(in).to(List))
    }
  }

  describe("The `replaceURLs` function ") {

    val input =
      """- Music is an art (https://en.wikipedia.org/wiki/Art).
        |- URL with parameters: http://example.org/a=1&b=2.
        |- Dungeons & Dragons
        |- Emails info@orbeon.com, hello@example.com.
        |- if (a < b) 42 else 0
        |- From Wikipedia (https://en.wikipedia.org/wiki/Music).
        |- Some naked domain is www.orbeon.com
        |- For some reason allow naked domain ending with a `/` www.orbeon.com/.
        |- d1api.com/",k
        |- if (a < b) 42 else 0""".stripMargin

    val expected =
      """<span>- Music is an art (<a href="https://en.wikipedia.org/wiki/Art">https://en.wikipedia.org/wiki/Art</a>).
        |- URL with parameters: <a href="http://example.org/a=1&amp;b=2">http://example.org/a=1&amp;b=2</a>.
        |- Dungeons &amp; Dragons
        |- Emails <a href="mailto:info@orbeon.com">info@orbeon.com</a>, <a href="mailto:hello@example.com">hello@example.com</a>.
        |- if (a &lt; b) 42 else 0
        |- From Wikipedia (<a href="https://en.wikipedia.org/wiki/Music">https://en.wikipedia.org/wiki/Music</a>).
        |- Some naked domain is <a href="http://www.orbeon.com">www.orbeon.com</a>
        |- For some reason allow naked domain ending with a `/` <a href="http://www.orbeon.com/">www.orbeon.com/</a>.
        |- <a href="http://d1api.com/">d1api.com/</a>&quot;,k
        |- if (a &lt; b) 42 else 0</span>""".stripMargin

    it("must replace all the URLs") {
      assert(expected == replaceURLs(input, replaceWithHyperlink))
    }
  }

  describe("The `isEmail` function ") {

    val expected = List(
      """www.google.com"""         -> false,
      """info@orbeon.com"""        -> true,
      """info@orbeon.com@"""       -> false,
      """(info@orbeon.com)"""      -> false,
      """ada.lovelace@london.uk""" -> true,
      """INFO@ORBEON.COM"""        -> true,
      """info@orbeon.com/"""       -> false,
      """mailto:info@orbeon.com""" -> false
    )

    it("must pass all") {
      for ((in, out) <- expected)
        assert(out == isEmail(in))
    }
  }
}
