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

import org.orbeon.oxf.test.DocumentTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xforms.event.events.XFormsCustomEvent
import org.orbeon.oxf.xforms.event.{XFormsEventTarget, EventListener, XFormsEvent}
import org.orbeon.oxf.xforms.control.controls.InstanceMirror._

class InstanceMirrorTest extends DocumentTestBase with AssertionsForJUnit {

    implicit def toEventListener(f: XFormsEvent => Any) = new EventListener {
        def handleEvent(event: XFormsEvent) { f(event) }
    }

    @Test def mirrorInSamePart() {
        this setupDocument
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml"
                     xmlns:xf="http://www.w3.org/2002/xforms"
                     xmlns:ev="http://www.w3.org/2001/xml-events"
                     xmlns:xxf="http://orbeon.org/oxf/xml/xforms">

                <xh:head>
                    <xf:model id="model">
                        <xf:instance id="form-instance" xxf:exclude-result-prefixes="#all">
                            <xh:html>
                                <xh:head>
                                    <xf:model id="my-model">
                                        <xf:instance id="my-instance">
                                            <instance/>
                                        </xf:instance>
                                    </xf:model>
                                </xh:head>
                                <xh:body/>
                            </xh:html>
                        </xf:instance>
                        
                        <xf:instance id="my-instance" xxf:exclude-result-prefixes="#all">
                            <instance xmlns=""/>
                        </xf:instance>

                        <xf:insert   ev:event="update1" context="//xf:instance/instance" origin="xxf:element('first', 'Arthur')"/>
                        <xf:insert   ev:event="update2" ref="//xf:instance/instance/first" origin="xxf:element('last', 'Clark')" position="after"/>
                        <xf:insert   ev:event="update3" ref="//xf:instance/instance/last" origin="xxf:element('middle', 'C.')" position="before"/>
                        <xf:setvalue ev:event="update4" ref="//xf:instance/instance/last">Clarke</xf:setvalue>
                        <xf:delete   ev:event="update5" ref="//xf:instance/instance/*"/>
                        <xf:insert   ev:event="update6" context="//xf:instance/instance" origin="xxf:attribute('first', 'Arthur')"/>
                        <xf:insert   ev:event="update7" ref="//xf:instance/instance/@*" origin="xxf:attribute('last', 'Clarke')" position="after"/>
                        <xf:insert   ev:event="update8" context="/*/xh:body" origin="xxf:element('xh:div')"/>
                        <xf:setvalue ev:event="update9" ref="/*/xh:body/xh:div">Hello!</xf:setvalue>

                        <xf:delete   ev:event="update10" ref="//xf:instance/instance"/>
                        <xf:insert   ev:event="update11" context="//xf:instance" origin="xxf:element('root')"/>

                    </xf:model>
                </xh:head>
                <xh:body/>
            </xh:html>

        val document = getDocument
        val logger = document.getIndentedLogger

        val outerInstance = getDocument.findInstance("form-instance")
        val innerInstance = getDocument.findInstance("my-instance")

        var nonInstanceChanges = 0

        // Attach listeners
        val outerListener = mirrorListener(document, logger,
            toInnerNode(outerInstance.getDocumentInfo, document.getStaticState.topLevelPart, document), () => nonInstanceChanges += 1) _
        for (eventName <- mutationEvents)
            outerInstance.addListener(eventName, outerListener)

        // Local helpers
        def dispatch(name: String) = document.dispatchEvent(new XFormsCustomEvent(document, name, document.getObjectByEffectiveId("model").asInstanceOf[XFormsEventTarget], true, true))
        def innerInstanceValue = TransformerUtils.tinyTreeToString(innerInstance.getDocumentInfo)

        // Test insert, value change and delete
        Seq(
            ("""<instance><first>Arthur</first></instance>""", 0),
            ("""<instance><first>Arthur</first><last>Clark</last></instance>""" , 0),
            ("""<instance><first>Arthur</first><middle>C.</middle><last>Clark</last></instance>""" , 0),
            ("""<instance><first>Arthur</first><middle>C.</middle><last>Clarke</last></instance>""" , 0),
            ("""<instance/>""" , 0),
            ("""<instance first="Arthur"/>""" , 0),
            ("""<instance first="Arthur" last="Clarke"/>""" , 0),
            ("""<instance first="Arthur" last="Clarke"/>""" , 1),
            ("""<instance first="Arthur" last="Clarke"/>""" , 2)
            // NOTE: Removing root element doesn't work right now, maybe because doDelete() doesn't support it
//            ("""""" , 2),
//            ("""<root/>""" , 2)
        ).zipWithIndex foreach { case ((expectedInnerInstanceValue, expectedNonInstanceChanges), index) =>
            // Dispatch event and assert result
            dispatch("update" + (index + 1))

            assert(innerInstanceValue === expectedInnerInstanceValue)
            assert(nonInstanceChanges === expectedNonInstanceChanges)
        }
    }
}