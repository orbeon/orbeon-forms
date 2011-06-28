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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.common.Version
import org.junit._
import org.scalatest.junit._
import org.orbeon.oxf.test.DocumentTestBase
import collection.mutable.LinkedHashMap
import collection.JavaConverters._

class HeadersTest extends DocumentTestBase with AssertionsForJUnit {

    @Test def outputHeaders() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        this setupDocument
            <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                <xh:head>
                    <xf:model xxf:xpath-analysis="true">
                        <xf:instance id="instance">
                            <value/>
                        </xf:instance>
                        <xf:instance id="headers">
                            <headers>
                                <header name="Header1" value="value1"/>
                                <header name="Header2" value="value2"/>
                                <header name="Header3" value="value3"/>
                            </headers>
                        </xf:instance>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:output id="output1" ref="instance()" mediatype="image/*">
                        <xf:header ref="instance('headers')/header">
                            <xf:name value="@name"/>
                            <xf:value value="@value"/>
                        </xf:header>
                    </xf:output>

                    <xf:output id="output2" ref="instance()" mediatype="image/*">
                        <!-- Original headers -->
                        <xf:header ref="instance('headers')/header">
                            <xf:name value="@name"/>
                            <xf:value value="@value"/>
                        </xf:header>
                        <!-- Prepend to 1 header -->
                        <xf:header combine="prepend">
                            <xf:name>Header1</xf:name>
                            <xf:value>prepend1</xf:value>
                        </xf:header>
                        <!-- Append to 1 header -->
                        <xf:header combine="append">
                            <xf:name>Header2</xf:name>
                            <xf:value>append2</xf:value>
                        </xf:header>
                        <!-- Replace 1 header -->
                        <xf:header combine="replace">
                            <xf:name>Header3</xf:name>
                            <xf:value>replace3</xf:value>
                        </xf:header>
                        <!-- Prepend to 3 headers -->
                        <xf:header combine="prepend" ref="instance('headers')/header">
                            <xf:name value="@name"/>
                            <xf:value>prepend2</xf:value>
                        </xf:header>
                        <!-- Append to 3 headers -->
                        <xf:header combine="append" ref="instance('headers')/header">
                            <xf:name value="@name"/>
                            <xf:value>append2</xf:value>
                        </xf:header>
                        <!-- Additional header -->
                        <xf:header>
                            <xf:name>Header4</xf:name>
                            <xf:value>value4</xf:value>
                        </xf:header>
                    </xf:output>
                </xh:body>
            </xh:html>

        // Expected results per control
        val expected = LinkedHashMap(
            "output1" -> LinkedHashMap(
                "Header1" -> Seq("value1"),
                "Header2" -> Seq("value2"),
                "Header3" -> Seq("value3")
            ),
            "output2" -> LinkedHashMap(
                "Header1" -> Seq("prepend2", "prepend1", "value1", "append2"),
                "Header2" -> Seq("prepend2", "value2", "append2", "append2"),
                "Header3" -> Seq("prepend2", "replace3", "append2"),
                "Header4" -> Seq("value4")
            )
        )

        // Test that everything matches
        for {
            (controlId, expectedHeaders) <- expected
            control = getValueControl(controlId).asInstanceOf[XFormsOutputControl]
            actualHeaders = control.testEvaluateHeaders
            (expectedHeaderName, expectedHeaderValues) <- expectedHeaders
        } yield
            assert(expectedHeaderValues === actualHeaders.asScala(expectedHeaderName).toSeq)
    }
}