/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.Version
import org.junit._
import org.scalatest.junit._
import org.orbeon.oxf.test.DocumentTestBase

class MIPDependenciesTest extends DocumentTestBase with AssertionsForJUnit {

    @Before def setupDocument(): Unit = setupDocument("oxf:/org/orbeon/oxf/xforms/analysis/mips.xhtml")

    @Test def typeInvalid() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        // Initial state
        assert("150" === getControlValue("line-total·1"))
        assert("2150" === getControlValue("subtotal"))

        // Change first item price to foo
        setControlValue("price·1", "foo")

        assert(!isValid("price·1"))
        assert(!isValid("line-total·1"))
        assert("-" === getControlValue("line-total·1"))
        assert("2000" === getControlValue("subtotal"))

        // Change first item price to 100
        setControlValue("price·1", "100")

        assert(isValid("price·1"))
        assert(isValid("line-total·1"))
        assert("300" === getControlValue("line-total·1"))
        assert("2300" === getControlValue("subtotal"))
    }

    @Test def relevance() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        val units = getControlValue("units·1")

        // Change first item units to foo
        setControlValue("units·1", "foo")

        assert(!isValid("units·1"))
        assert(!isRelevant("line-total·1"))
        assert("2000" === getControlValue("subtotal"))

        // Change back item  units
        setControlValue("units·1", units)

        assert(isValid("units·1"))
        assert(isRelevant("line-total·1"))
        assert(isValid("line-total·1"))
        assert("150" === getControlValue("line-total·1"))
        assert("2150" === getControlValue("subtotal"))
    }

    @Test def typeConstraintInvalid() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        val units = getControlValue("units·1")

        // Change first item units to a constraint-invalid value
        setControlValue("units·1", "0")

        assert(!isValid("units·1"))
        assert(!isRelevant("line-total·1"))
        assert("2000" === getControlValue("subtotal"))

        // Then change to type-invalid too
        setControlValue("units·1", "foo")

        assert(!isValid("units·1"))
        assert(!isRelevant("line-total·1"))
        assert("2000" === getControlValue("subtotal"))

        // Change back item  units
        setControlValue("units·1", units)

        assert(isValid("units·1"))
        assert(isRelevant("line-total·1"))
        assert(isValid("line-total·1"))
        assert("150" === getControlValue("line-total·1"))
        assert("2150" === getControlValue("subtotal"))
    }

    @Test def simpleRequired() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        // NOTE: The value of @required has no dependencies in this sample, so this is a weak test

        assert(isRequired("name·1"))
        assert(isValid("name·1"))

        setControlValue("name·1", "")
        assert(isRequired("name·1"))
        assert(!isValid("name·1"))

        setControlValue("name·1", "100")
        assert(isRequired("name·1"))
        assert(isValid("name·1"))
    }

    @Test def testNormalizeSpaceContextConstraint() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        // NOTE: The value of @required has no dependencies in this sample, so this is a weak test

        assert(isValid("name·1"))

        setControlValue("name·1", "    ") // series of spaces
        assert(!isValid("name·1"))

        setControlValue("name·1", "100")
        assert(isValid("name·1"))
    }

    // See: [ #315733 ] Incorrect MIPs when more than two binds point to the same node
    //      http://forge.ow2.org/tracker/index.php?func=detail&aid=315733&group_id=168&atid=350207
    @Test def multipleBindsOnSameNode() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        this setupDocument
            <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                <xh:head>
                    <xf:model xxf:xpath-analysis="true">
                        <xf:instance id="instance">
                            <value>0</value>
                        </xf:instance>
                        <xf:bind ref="instance()" readonly=". mod 2 = 1"/>
                        <xf:bind ref="instance()" constraint=". idiv 10 mod 2 = 1"/>
                        <xf:bind ref="instance()" required=". idiv 100 mod 2 = 1"/>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:input id="input" ref="instance()"/>
                </xh:body>
            </xh:html>

        // Test all combinations of MIPs
        for {
            required <- Seq(false, true)
            valid <- Seq(false, true)
            readonly <- Seq(false, true)
            value = Seq(required, valid, readonly) map (if (_) "1" else "0") mkString
        } yield {
            // Value looks like "000" to "111"
            setControlValue("input", value)

            // Check all MIPs
            assert(isReadonly("input") === readonly)
            assert(isValid("input") === valid)
            assert(isRequired("input") === required)
        }
    }

    // TODO: more tests
}
