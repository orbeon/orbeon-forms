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

import org.junit.Test
import org.orbeon.oxf.http.Headers
import Headers._
import org.scalatestplus.junit.AssertionsForJUnit
import scala.collection.compat._

class HeadersTest extends AssertionsForJUnit {

  @Test def testCapitalizeHeader(): Unit = {
    assert("Accept"       === capitalizeCommonOrSplitHeader("aCcEpT"))
    assert("Content-Type" === capitalizeCommonOrSplitHeader("cOnTeNt-tYpE"))
    assert("SOAPAction"   === capitalizeCommonOrSplitHeader("sOaPaCtIoN"))
    assert("TE"           === capitalizeCommonOrSplitHeader("tE"))
    assert("Content-MD5"  === capitalizeCommonOrSplitHeader("cOnTeNt-Md5"))
  }

  @Test def testFilterAndCapitalizeHeaders(): Unit = {

    val arrays = List("Foo" -> Array("foo1", "foo2"), "Bar" -> Array("bar1", "bar2"))
    val lists  = List("Foo" -> List("foo1", "foo2"),  "Bar" -> List("bar1", "bar2"))

    val toFilterInRequest  = RequestHeadersToRemove  map (_ -> List("NOT!"))
    val toFilterInResponse = ResponseHeadersToRemove map (_ -> List("NOT!"))

    assert(lists === (proxyAndCapitalizeHeaders(arrays, request = true) map { case (k, v) => k -> v.to(List)}))
    assert(lists === proxyAndCapitalizeHeaders(lists, request = true))
    assert(lists === proxyAndCapitalizeHeaders(lists ++ toFilterInRequest, request = true))
    assert(lists === proxyAndCapitalizeHeaders(lists ++ toFilterInResponse, request = false))
  }
}
