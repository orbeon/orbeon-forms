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
 */
package org.orbeon.oxf.portlet

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import collection.JavaConverters._

class WSRP2UtilsTest extends AssertionsForJUnit {

    @Test def decodeQueryStringTest() {

        val expected = Seq(
            """filename=data.html&orbeon.path=/fr/service/import-export/serve&uuid=""" →
                Map("filename"    → Seq("data.html"),
                    "orbeon.path" → Seq("/fr/service/import-export/serve"),
                    "uuid"        → Seq("")),
            """p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1=""" →
                Map("p1" → Seq("v11", "v12", ""),
                    "p2" → Seq("v21", "", "v23"))
        )
        
        def decode(s: String) = WSRP2Utils.decodeQueryString(s, true).asScala.mapValues(_.toList)
        
        for ((query, extracted) ← expected) {
            // Test with both separators
            assert(extracted === decode(query))
            assert(extracted === decode(query.replaceAllLiterally("&", "&amp;")))
        }
    }
}
