/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.test.DocumentTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.junit._

class ComplexContentDependenciesTest extends DocumentTestBase with AssertionsForJUnit {

    // See: [ #315535 ] XPath analysis: xxf:serialize() support: detect changes to nested elements
    //      http://forge.ow2.org/tracker/index.php?func=detail&aid=315535&group_id=168&atid=350207
    @Test def serializeFunction() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        this setupDocument
            <xh:html xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                     xmlns:saxon="http://saxon.sf.net/">
                <xh:head>
                    <xf:model xxf:xpath-analysis="true">
                        <xf:instance id="instance" xxf:exclude-result-prefixes="xf xh xxf saxon">
                            <div><p>This is <b>complex</b> content.</p></div>
                        </xf:instance>
                        <xf:instance id="serialization">
                            <xsl:output method="xml" omit-xml-declaration="yes" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"/>
                        </xf:instance>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <!-- This is just here to allow modifying the nested <b> -->
                    <xf:input id="input" ref="instance()/p/b"/>
                    <!-- Test both xxf:serialize() and saxon:serialize() -->
                    <xf:output id="output1" value="xxf:serialize(instance(), instance('serialization'))"/>
                    <xf:output id="output2" value="saxon:serialize(instance(), instance('serialization'))"/>
                </xh:body>
            </xh:html>

        assert(getControlValue("input") === "complex")
        assert(getControlValue("output1") === "<div><p>This is <b>complex</b> content.</p></div>")
        assert(getControlValue("output2") === "<div><p>This is <b>complex</b> content.</p></div>")

        setControlValue("input", "bold")

        assert(getControlValue("input") === "bold")
        assert(getControlValue("output1") === "<div><p>This is <b>bold</b> content.</p></div>")
        assert(getControlValue("output2") === "<div><p>This is <b>bold</b> content.</p></div>")
    }

    // See: [ #315525 ] XPath analysis: bug with xf:output pointing to complex content
    //      http://forge.ow2.org/tracker/index.php?func=detail&aid=315525&group_id=168&atid=350207
    @Test def outputComplexContent() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        this setupDocument
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
                <xh:head>
                    <xf:model xxf:xpath-analysis="true">
                        <xf:instance>
                            <name><first>Teddy</first><last>Bear</last></name>
                        </xf:instance>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:input id="input" ref="first"/>
                    <xf:output id="output" value="instance()"/>
                </xh:body>
            </xh:html>

        assert(getControlValue("input") === "Teddy")
        assert(getControlValue("output") === "TeddyBear")

        setControlValue("input", "Theodore")

        assert(getControlValue("input") === "Theodore")
        assert(getControlValue("output") === "TheodoreBear")
    }
}
