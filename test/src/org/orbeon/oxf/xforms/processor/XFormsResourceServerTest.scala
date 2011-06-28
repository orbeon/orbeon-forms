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

import org.scalatest.junit.AssertionsForJUnit
import java.util.Collections
import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase

class XFormsResourceServerTest extends ResourceManagerTestBase with AssertionsForJUnit {

    @Test def proxyURI() {

        assert("/xforms-server/dynamic/87c938edbc170d5038192ca5ab9add97" ===
            XFormsResourceServer.proxyURI("/foo/bar.png", null, null, -1, Collections.emptyMap()));

        assert("/xforms-server/dynamic/674c2ff956348155ff60c01c0c0ec2e0" ===
            XFormsResourceServer.proxyURI("http://example.org/foo/bar.png", null, null, -1, Collections.emptyMap()));
    }
}