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
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import FormRunner._

class FormRunnerTest extends DocumentTestBase with AssertionsForJUnit {

    @Test def persistenceHeaders() {

        val obf = getPersistenceHeadersAsXML("cities", "form1", "form")
        assert(TransformerUtils.tinyTreeToString(obf) ===
            """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/Mexico_City</value></header><header><name>Orbeon-City-Name</name><value>Mexico City</value></header><header><name>Orbeon-Population</name><value>8851080</value></header></headers>""")

        val obd = getPersistenceHeadersAsXML("cities", "form1", "data")
        assert(TransformerUtils.tinyTreeToString(obd) ===
            """<headers><header><name>Orbeon-City-Uri</name><value>http://en.wikipedia.org/wiki/S%C3%A3o_Paulo</value></header><header><name>Orbeon-City-Name</name><value>São Paulo</value></header><header><name>Orbeon-Population</name><value>11244369</value></header></headers>""")
    }

    @Test def formBuilderPermissions() {

        val frRoles: NodeInfo =
            <roles>
                <role name="*" app="app-always" form="form-always"/>
                <role name="all-forms-role" app="*" form="*"/>
                <role name="all-foo-forms-role" app="foo" form="*"/>
                <role name="foo-baz-role" app="foo" form="baz"/>
                <role name="bar-baz-role" app="bar" form="baz"/>
                <role name="bar-baz2-role" app="bar" form="baz2"/>
            </roles>

        // Test inclusion of form that is always permitted
        val always = Map("app-always" -> Set("form-always"))

        assert(getFormBuilderPermissions(frRoles, Set("some", "other"))                         === always)
        assert(getFormBuilderPermissions(frRoles, Set("all-foo-forms-role"))                    === always + ("foo" -> Set("*")))
        assert(getFormBuilderPermissions(frRoles, Set("bar-baz-role"))                          === always + ("bar" -> Set("baz")))
        assert(getFormBuilderPermissions(frRoles, Set("all-foo-forms-role", "bar-baz-role"))    === always + ("foo" -> Set("*")) + ("bar" -> Set("baz")))

        // Test match for all roles
        val all = Map("*" -> Set("*"))

        assert(getFormBuilderPermissions(frRoles, Set("all-forms-role"))                        === all)
        assert(getFormBuilderPermissions(frRoles, Set("all-forms-role", "some", "other"))       === all)
        assert(getFormBuilderPermissions(frRoles, Set("all-forms-role", "all-foo-forms-role"))  === all)
        assert(getFormBuilderPermissions(frRoles, Set("all-forms-role", "bar-baz-role"))        === all)

        // Combine roles with wildcard and specific app
        assert(getFormBuilderPermissions(frRoles, Set("all-foo-forms-role", "foo-baz-role"))    === always + ("foo" -> Set("*")))

        // Different baz forms
        assert(getFormBuilderPermissions(frRoles, Set("foo-baz-role", "bar-baz-role"))          === always + ("foo" -> Set("baz")) + ("bar" -> Set("baz")))

        // Multiple forms per app
        assert(getFormBuilderPermissions(frRoles, Set("bar-baz-role", "bar-baz2-role"))         === always + ("bar" -> Set("baz", "baz2")))

        // Empty roles
        val emptyRoles: NodeInfo = <roles/>
        assert(getFormBuilderPermissions(emptyRoles, Set("some")) === Map())
    }
}