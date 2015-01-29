/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor

import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit

class XFormsResourceServerTest extends ResourceManagerTestBase with AssertionsForJUnit {

    @Test def proxyURI() {

        val indentedLogger = ResourceManagerTestBase.newIndentedLogger

        assert("/xforms-server/dynamic/cc104a4fe62471ecc02ae13e1233f31275cb68d6" ===
            XFormsResourceServer.proxyURI("/foo/bar.png", None, None, -1, Map(), Set())(indentedLogger))

        assert("/xforms-server/dynamic/1dd011d51abc6f3d9003fabd2f5561943d066c73" ===
            XFormsResourceServer.proxyURI("http://example.org/foo/bar.png", None, None, -1, Map(), Set())(indentedLogger))
    }
}