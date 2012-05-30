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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test

import XFormsUploadControl._
import org.orbeon.oxf.test.ResourceManagerTestBase

class XFormsUploadControlTest extends ResourceManagerTestBase with AssertionsForJUnit {
    @Test def hmac(): Unit = {

        val parameters = Seq("file:/foo/bar.tmp", "bar.png", "image/png", "1234")

        def hmacFromSeq(p: Seq[String]) =
            hmacURL(p(0), Some(p(1)), Some(p(2)), Some(p(3)))
        
        val signed = hmacFromSeq(parameters)

        // Basic asserts
        assert("file:/foo/bar.tmp?filename=bar.png&mediatype=image%2Fpng&size=1234&signature=2db6140c988970391e8cd1513af3cc3a3dbcf6ff" === signed)
        assert(Some("2db6140c988970391e8cd1513af3cc3a3dbcf6ff") === getSignature(signed))
        assert("file:/foo/bar.tmp?filename=bar.png&mediatype=image%2Fpng&size=1234" === removeSignature(signed))

        assert(true === verifyHmacURL(signed))

        // Modify each parameter in turn and make sure the signature is different
        for (pos ← 0 to parameters.size - 1) {
            val newParameters = parameters.updated(pos, parameters(pos) + 'x')
            assert(getSignature(signed) != getSignature(hmacFromSeq(newParameters)))
        }
    }
}
