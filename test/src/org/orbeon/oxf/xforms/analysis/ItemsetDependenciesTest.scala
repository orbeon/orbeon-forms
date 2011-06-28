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

package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.common.Version
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.test.DocumentTestBase
import org.junit.{Assume, Test}

class ItemsetDependenciesTest extends DocumentTestBase with AssertionsForJUnit {

    // See: [ #315557 ] XPath analysis: Checkbox with both itemset and value changing ends up in incorrect state
    //      http://forge.ow2.org/tracker/?func=detail&atid=350207&aid=315557&group_id=168
    @Test def selectValueDependingOnItemset() {
        Assume.assumeTrue(Version.isPE) // only test this feature if we are the PE version

        this setupDocument
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms">

                <xh:head>
                    <xf:model id="model" xxf:xpath-analysis="true" xxf:encrypt-item-values="false">
                        <xf:instance id="instance">
                            <instance xmlns="">
                                <selection>1 2</selection>
                                <value>1</value>
                                <value>2</value>
                                <index>1</index>
                            </instance>
                        </xf:instance>
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:select id="checkbox" ref="selection" appearance="full">
                        <xf:item>
                            <xf:label/>
                            <xf:value ref="../value[xs:integer(../index)]"/>
                        </xf:item>
                    </xf:select>

                    <xf:select1 id="value-selection" ref="index" appearance="full">
                        <xf:item>
                            <xf:label>1</xf:label>
                            <xf:value>1</xf:value>
                        </xf:item>
                        <xf:item>
                            <xf:label>2</xf:label>
                            <xf:value>2</xf:value>
                        </xf:item>
                    </xf:select1>
                </xh:body>
            </xh:html>            

        assert(getControlExternalValue("checkbox") === "1")
        assert(getControlExternalValue("value-selection") === "1")
        assert(getItemset("checkbox") === """[{"label":"","value":"1"}]""")

        setControlValue("value-selection", "2")

        assert(getControlExternalValue("checkbox") === "2")
        assert(getItemset("checkbox") === """[{"label":"","value":"2"}]""")
    }
}