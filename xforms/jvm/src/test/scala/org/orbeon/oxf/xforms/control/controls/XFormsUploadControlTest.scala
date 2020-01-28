/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control.controls

import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl._
import org.scalatestplus.junit.AssertionsForJUnit

class XFormsUploadControlTest extends ResourceManagerTestBase with AssertionsForJUnit {
  @Test def hmac(): Unit = {

    val parameters = List(
      "file:/foo/tmp1.tmp",
      "bar & baz.png",
      "image/png",
      "1234"
    )

    def hmacFromList(p: List[String]) =
      hmacURL(p(0), Some(p(1)), Some(p(2)), Some(p(3)))

    val signed = hmacFromList(parameters)

    // Basic asserts
    assert("file:/foo/tmp1.tmp?filename=bar+%26+baz.png&mediatype=image%2Fpng&size=1234&mac=49acb231d3cf572cfce67f09a31d6669a3d0257f" === signed)
    assert(Some("49acb231d3cf572cfce67f09a31d6669a3d0257f") === getMAC(signed))
    assert("file:/foo/tmp1.tmp?filename=bar+%26+baz.png&mediatype=image%2Fpng&size=1234" === removeMAC(signed))

    assert(true === verifyMAC(signed))

    val names = List("filename", "mediatype", "size")

    // Check parameter values
    for ((name, expected) <- names zip parameters.tail)
      assert(Some(expected) === getFirstQueryParameter(signed, name))

    // Modify each parameter in turn and make sure the MAC is different
    for (pos <- parameters.indices) {
      val newParameters = parameters.updated(pos, parameters(pos) + 'x')
      assert(getMAC(signed) != getMAC(hmacFromList(newParameters)))
    }
  }
}
