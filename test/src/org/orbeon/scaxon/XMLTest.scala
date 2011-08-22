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
package org.orbeon.scaxon

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

class XMLTest extends AssertionsForJUnit {

    @Test def attributes() {
        val foo: NodeInfo = <foo a1="v1" a2="v2" my:a3="v3a" your:a3="v3b" xmlns:my="ns1" xmlns:your="ns2"/>

        assert((foo \@ "a1" stringValue) === "v1")
        assert((foo \@ "a2" stringValue) === "v2")

        // NOTE: We sort values as Scala messes-up source attribute order

        assert((foo \@ "a3" map (_.stringValue) sorted) === Seq("v3a", "v3b"))
        assert((foo \@ "a3" size) === 2)
        assert((foo \@ "*:a3" size) === 2)

        assert((foo \@ @* map (_.stringValue) sorted) === Seq("v1", "v2", "v3a", "v3b"))

        // This fails because resulting tests test on element node type:
//        assert((foo \@ ("a1" || "a2") map (_.stringValue) sorted) === Seq("v1", "v2"))
    }
}