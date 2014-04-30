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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test

class URLFinderTest extends AssertionsForJUnit {

    import URLFinder._

    @Test def testFindURLs(): Unit = {

        val expected = List(
            """www.google.com"""                                           → List("www.google.com"),
            """http://www.google.com"""                                    → List("http://www.google.com"),
            """https://www.google.com"""                                   → List("https://www.google.com"),
            """http://www.google.com,"""                                   → List("http://www.google.com"),
            """http://www.google.com."""                                   → List("http://www.google.com"),
            """http://www.google.com:"""                                   → List("http://www.google.com"),
            """this (http://www.google.com) works"""                       → List("http://www.google.com"),
            """this http://userid:password@example.com:8080/, works"""     → List("""http://userid:password@example.com:8080/"""),
            """this http://223.255.255.254, works"""                       → List("""http://223.255.255.254"""),
            """this http://foo.bar/?q=Test%20URL-encoded%20stuff, works""" → List("""http://foo.bar/?q=Test%20URL-encoded%20stuff"""),
            """some google.com URL and another (http://twitter.com/)"""    → List("""google.com""", """http://twitter.com/""")
        )

        for ((in, out) ← expected)
            assert(out === findAllLinks(in).to[List])
    }

    @Test def testHyperlinkURLs(): Unit = {

        val input =
            """Music is an art (https://en.wikipedia.org/wiki/Art).
              |
              |URL with parameters: http://example.org/a=1&b=2.
              |
              |From Wikipedia (https://en.wikipedia.org/wiki/Music).""".stripMargin

        val expected =
            """<span>Music is an art (<a href="https://en.wikipedia.org/wiki/Art">https://en.wikipedia.org/wiki/Art</a>).
              |
              |URL with parameters: <a href="http://example.org/a=1&amp;b=2">http://example.org/a=1&amp;b=2</a>.
              |
              |From Wikipedia (<a href="https://en.wikipedia.org/wiki/Music">https://en.wikipedia.org/wiki/Music</a>).</span>""".stripMargin

        assert(expected === replaceURLs(input, insertHyperlink))
    }
}
