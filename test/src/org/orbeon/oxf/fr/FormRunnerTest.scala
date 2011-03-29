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
package org.orbeon.oxf.fr

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.test.DocumentTestBase

class FormRunnerTest extends DocumentTestBase with AssertionsForJUnit {
    @Test def testPersistenceHeaders {

        val obf = FormRunner.getHeaders("cities", "form1", "form")
        assert(TransformerUtils.tinyTreeToString(obf) ===
            """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/Mexico_City</value></header><header><name>Orbeon-City-Name</name><value>Mexico City</value></header><header><name>Orbeon-Population</name><value>8851080</value></header></headers>""")

        val obd = FormRunner.getHeaders("cities", "form1", "data")
        assert(TransformerUtils.tinyTreeToString(obd) ===
            """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/S%C3%A3o_Paulo</value></header><header><name>Orbeon-City-Name</name><value>São Paulo</value></header><header><name>Orbeon-Population</name><value>11244369</value></header></headers>""")
    }
}