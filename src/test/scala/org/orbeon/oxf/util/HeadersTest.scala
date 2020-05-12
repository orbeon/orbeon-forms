/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 *  package org.orbeon.oxf.util
 */
package org.orbeon.oxf.util

import org.orbeon.oxf.http.Headers._
import org.scalatest.funspec.AnyFunSpec

import scala.collection.compat._

class HeadersTest extends AnyFunSpec {

  describe("Capitalize headers") {

    val Expected = List(
      "Accept"       -> "aCcEpT",
      "Content-Type" -> "cOnTeNt-tYpE",
      "SOAPAction"   -> "sOaPaCtIoN",
      "TE"           -> "tE",
      "Content-MD5"  -> "cOnTeNt-Md5"
    )

    for ((capitalized, original) <- Expected)
      it(s"must capitalize to `$capitalized") {
        assert(capitalized == capitalizeCommonOrSplitHeader(original))
      }
  }

  describe("Filter and capitalize headers") {

    val arrays = List("Foo" -> Array("foo1", "foo2"), "Bar" -> Array("bar1", "bar2"))
    val lists  = List("Foo" -> List("foo1", "foo2"),  "Bar" -> List("bar1", "bar2"))

    it("must handle `Array` and `List`") {
      assert(lists === (proxyAndCapitalizeHeaders(arrays, request = true) map { case (k, v) => k -> v.to(List)}))
      assert(lists === proxyAndCapitalizeHeaders(lists, request = true))
    }

    it("must filter request headers") {
      val toFilterInRequest  = RequestHeadersToRemove  map (_ -> List("NOT!"))
      assert(lists === proxyAndCapitalizeHeaders(lists ++ toFilterInRequest, request = true))
    }

    it("must filter response headers") {
      val toFilterInResponse = ResponseHeadersToRemove map (_ -> List("NOT!"))
      assert(lists === proxyAndCapitalizeHeaders(lists ++ toFilterInResponse, request = false))
    }
  }
}
