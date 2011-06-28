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
package org.orbeon.oxf.xforms

import event.{XFormsEvents, ClientEvents}
import org.orbeon.oxf.common.Version
import org.scalatest.junit.AssertionsForJUnit
import org.junit.{Assume, Test}
import org.orbeon.oxf.test.DocumentTestBase

class ClientEventsTest extends DocumentTestBase with AssertionsForJUnit {

    @Test def noscriptEventReordering = {

        // TODO: Disabled for now as XML comparison doesn't work yet (attribute order is changed along the way)
        Assume.assumeTrue(false)

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
                    </xf:model>
                </xh:head>
                <xh:body>
                    <xf:input id="input" ref="instance()"/>
                    <xf:trigger id="trigger"><xf:label/></xf:trigger>
                </xh:body>
            </xh:html>

        val eventElements = List(
            <xxf:event xmlns:xxf="http://orbeon.org/oxf/xml/xforms" name={XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE} source-control-id="trigger"/>,
            <xxf:event xmlns:xxf="http://orbeon.org/oxf/xml/xforms" name={XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE} source-control-id="input">42</xxf:event>)

        val expected = List(
            <xxf:event xmlns:xxf="http://orbeon.org/oxf/xml/xforms" name={XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE} source-control-id="input">42</xxf:event>,
            <xxf:event xmlns:xxf="http://orbeon.org/oxf/xml/xforms" name={XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE} source-control-id="trigger"/>)

        val result = ClientEvents.reorderNoscriptEvents(eventElements, getDocument)

        assert(expected === result)
    }
}