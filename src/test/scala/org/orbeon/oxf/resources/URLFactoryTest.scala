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
package org.orbeon.oxf.resources

import org.orbeon.oxf.test.ResourceManagerTestBase
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit


class URLFactoryTest extends ResourceManagerTestBase with AssertionsForJUnit {

  @Test def basic(): Unit = {

    val expected = Seq(
      ("oxf",    null,          "/foo/bar.txt", null)   -> "oxf:/foo/bar.txt?a=42",
      ("system", null,          "out",          null)   -> "system:out?a=42",
      ("http",   "example.org", "/foo/bar.txt", "a=42") -> "http://example.org/foo/bar.txt?a=42",
      ("https",  "example.org", "/foo/bar.txt", "a=42") -> "https://example.org/foo/bar.txt?a=42",
      ("file",   "",            "/foo/bar.txt", null)   -> "file:/foo/bar.txt?a=42"
    )

    for ((parts, urlString) <- expected) {
      val url = URLFactory.createURL(urlString)
      assert(parts === (url.getProtocol, url.getHost, url.getPath, url.getQuery))
    }
  }
}
