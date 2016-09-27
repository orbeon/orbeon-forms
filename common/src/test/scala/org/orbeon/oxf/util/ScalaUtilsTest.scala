/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.junit.Test
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IOUtils._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.StringUtils._
import org.scalatest.junit.AssertionsForJUnit

import scala.collection.mutable

class ScalaUtilsTest extends AssertionsForJUnit {

  @Test def testUseAndClose(): Unit = {

    val closable = new {
      var closed = false
      def close() = closed = true
      def value = 42
    }

    assert(42 === useAndClose(closable)(_.value))
    assert(closable.closed)

    assert(null eq useAndClose(null: {def close()})(identity))
  }

  @Test def testRunQuietly(): Unit = {
    assert(() === runQuietly(throw new RuntimeException))
  }

  @Test def testDropTrailingSlash(): Unit = {
    assert("/a" === dropTrailingSlash("/a/"))
    assert("/a" === dropTrailingSlash("/a"))
    assert(""   === dropTrailingSlash("/"))
    assert("/"  === dropTrailingSlash("//"))
  }

  @Test def testDropStartingSlash(): Unit = {
    assert("a/" === dropStartingSlash("/a/"))
    assert("a/" === dropStartingSlash("a/"))
    assert(""   === dropStartingSlash("/"))
    assert("/"  === dropStartingSlash("//"))
  }

  @Test def testAppendStartingSlash(): Unit = {
    assert("/a/" === appendStartingSlash("/a/"))
    assert("/a/" === appendStartingSlash("a/"))
    assert("/"   === appendStartingSlash("/"))
    assert("//"  === appendStartingSlash("//"))
  }

  @Test def testPipeOps(): Unit = {

    def inc(i: Int) = i + 1

    assert("43" === (42 |> inc |> (_.toString)))
  }

  @Test def testStringToSet(): Unit = {
    assert(Set()                     == stringToSet(""))
    assert(Set()                     == stringToSet("  "))
    assert(Set("GET")                == stringToSet(" GET "))
    assert(Set("GET", "POST", "PUT") == stringToSet(" GET  POST  PUT "))
  }

  @Test def testSplitQuery(): Unit = {
    assert(("", None)                === splitQuery(""))
    assert(("", Some("bar"))         === splitQuery("?bar"))
    assert(("/foo", None)            === splitQuery("/foo"))
    assert(("/foo", Some("bar"))     === splitQuery("/foo?bar"))
    assert(("/foo", Some("bar?baz")) === splitQuery("/foo?bar?baz"))
  }

  @Test def testDecodeSimpleQuery(): Unit = {

    val query    = """p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1="""
    val expected = Seq("p1" → "v11", "p2" → "v21", "p1" → "v12", "p2" → "", "p2" → "v23", "p1" → "")

    assert(expected === decodeSimpleQuery(query))
  }

  @Test def testGetFirstQueryParameter(): Unit = {

    val pathQuery = """/foo?p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1="""

    assert(Some("v11") === getFirstQueryParameter(pathQuery, "p1"))
    assert(Some("v21") === getFirstQueryParameter(pathQuery, "p2"))
    assert(None        === getFirstQueryParameter(pathQuery, "p3"))
  }

  @Test def testEncodeSimpleQuery(): Unit = {

    val query1 = """p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1="""
    assert(query1 === encodeSimpleQuery(decodeSimpleQuery(query1)))

    assert("name=%C3%89rik" === encodeSimpleQuery(Seq("name" → "Érik")))
  }

  @Test def testCombineValues(): Unit = {

    val parameters = Seq("p1" → "v11", "p2" → "v21", "p1" → "v12", "p2" → "", "p2" → "v23", "p1" → "")
    val expectedAsList   = Seq(("p1", List("v11", "v12", "")), ("p2", List("v21", "", "v23")))
    val expectedAsVector = Seq(("p1", Vector("v11", "v12", "")), ("p2", Vector("v21", "", "v23")))
    val expectedAsSet    = Seq(("p1", Set("v11", "v12", "")), ("p2", Set("v21", "", "v23")))

    assert(expectedAsList   === combineValues[String, String, List](parameters))
    assert(expectedAsVector === combineValues[String, String, Vector](parameters))
    assert(expectedAsSet    === combineValues[String, String, Set](parameters))
    assert(expectedAsList   === (combineValues[String, AnyRef, Array](parameters) map { case (k, v) ⇒ k → v.to[List]}))
  }

  @Test def testTrimAllToOpt(): Unit = {
    assert(None        === "".trimAllToOpt)
    assert(None        === "  ".trimAllToOpt)
    assert(Some("foo") === "foo".trimAllToOpt)
    assert(Some("foo") === "  foo  ".trimAllToOpt)
  }

  @Test def testBooleanOption(): Unit = {
    locally {
      var invoked = false
      assert(Some("foo") === true.option({invoked = true; "foo"}))
      assert(invoked)
    }

    locally {
      var invoked = false
      assert(None === false.option({invoked = true; "foo"}))
      assert(! invoked)
    }
  }

  @Test def testCollectByType(): Unit = {

    class Foo
    class Bar extends Foo

    assert(collectByErasedType[Foo](new Foo).isDefined)
    assert(collectByErasedType[Foo](new Bar).isDefined)
    assert(collectByErasedType[Bar](new Bar).isDefined)
    assert(collectByErasedType[Bar](new Foo).isEmpty)
    assert(collectByErasedType[Foo](new String).isEmpty)

    assert(collectByErasedType[Seq[String]](Seq("a")).isDefined)
    assert(collectByErasedType[Seq[String]](Seq(42)).isDefined) // erasure!
  }

  @Test def testSplit(): Unit = {

    val expected = Seq(
      ""                    → Seq(),
      "  "                  → Seq(),
      " GET "               → Seq("GET"),
      " GET  POST  PUT "    → Seq("GET", "POST", "PUT"),
      " GET  POST  PUT GET" → Seq("GET", "POST", "PUT", "GET")
    )

    for ((in, out) ← expected) {
      assert(out === split[List](in))
      assert(out === split[Array](in).to[List])
      assert(out.to[Set] === split[Set](in))
      assert(out.to[mutable.LinkedHashSet].to[List] === split[mutable.LinkedHashSet](in).to[List])
    }
  }

  @Test def testTruncateWithEllipsis(): Unit = {

    val expected = Seq(
      ("abcdef",        3,  0) → "abc…",
      ("abcdef",        3, 10) → "abcdef",
      ("abcdef",       10,  0) → "abcdef",
      ("abcd ",         3,  1) → "abcd…",
      ("abcde ",        3,  1) → "abc…",
      ("abc d",         3,  1) → "abc…",
      ("abc d e f",     3,  3) → "abc d…",
      ("abc d e f",     3,  4) → "abc d e…",
      ("abc d e f",     3,  5) → "abc d e…",
      ("abc de fg hi",  3,  3) → "abc de…",
      ("abc de fg hi",  3,  4) → "abc de…",
      ("abc de fg hi",  3,  5) → "abc de…",
      ("abc de fg hi",  3,  6) → "abc de fg…"
    )

    val truncateWithEllipsisTupled = (truncateWithEllipsis _).tupled

    for ((in, out) ← expected)
      assert(out === truncateWithEllipsisTupled(in))
  }

  @Test def testTrim(): Unit = {

    val zeroWidthSpaces   = "\u200b\u200c\u200d\ufeff" // last one is the BOM
    val nonBreakingSpaces = "\u00A0\u2007\u202F"
    val otherSpaces       = "\u0020\u00a0\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000"
    val isoControls       = new String(Array((0x00 to 0x1f) ++ (0x7f to 0x9f) map (_.toChar): _*))

    val allToTrim         = zeroWidthSpaces + nonBreakingSpaces + otherSpaces + isoControls

    // NOTE: All spaces and control characters are in the BMP and cannot collide with surrogates. We we are not testing much here.
    val rareChineseChar1 = "\ud860\udee2" // '(Cant.) an elevator (from the British 'lift')'
    val rareChineseChar2 = "\ud84a\udf43" // '(Cant.) to touch; to bump into; to take, get, receive; to lightly support something with the hand'
    val funnyCharsString = allToTrim + rareChineseChar1 + allToTrim + rareChineseChar2 + allToTrim

    val expected = Seq(
      ""               → "",
      (null: String)   → "",
      "  "             → "",
      "\t"             → "",
      "  a b c  "      → "a b c",
      allToTrim        → "",
      funnyCharsString → (rareChineseChar1 + allToTrim + rareChineseChar2)
    )

    for ((in, out) ← expected)
      assert(out === in.trimAllToEmpty)
  }
}
