/**
 * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.scalatest.funspec.AnyFunSpecLike

class DelayedEventsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Delayed events") {

    it ("recursive delayed events") {
      withTestExternalContext { _ =>

        val doc = this setupDocument
          <xh:html
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
              <xf:model id="model" xxf:xpath-analysis="true">

                <xf:instance id="instance">
                  <value/>
                </xf:instance>

                <xf:action event="xforms-ready">
                  <xf:dispatch
                    name="event1"
                    targetid="model"
                    delay="0"/>
                </xf:action>

                <xf:action event="event1">
                  <xf:setvalue ref="." value="concat(., 'event1')"/>
                  <xf:dispatch
                    name="event2"
                    targetid="model"
                    delay="0"/>
                </xf:action>

                <xf:action event="event2">
                  <xf:setvalue ref="." value="concat(., ' event2')"/>
                  <xf:dispatch
                    name="event3"
                    targetid="model"
                    delay="{1000 * 60 * 60 * 24 * 365}"/>
                </xf:action>

              </xf:model>
            </xh:head>
            <xh:body>
              <xf:output id="my-output" value="."/>
            </xh:body>
          </xh:html>

        withContainingDocument(doc) {
          assert("event1 event2" === getControlValue("my-output"))
        }
      }
    }

    it("deferred focus") {
        withTestExternalContext { _ =>

        val doc = this setupDocument
          <xh:html
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
              <xf:model id="model" xxf:xpath-analysis="true">

                <xf:instance id="instance">
                  <form>
                    <value1>cat</value1>
                    <value2/>
                  </form>
                </xf:instance>

              </xf:model>
            </xh:head>
            <xh:body>
              <xf:select1 id="my-select1" ref="value1" xxf:encrypt-item-values="false">
                <xf:item>
                    <xf:label>Cat</xf:label>
                    <xf:value>cat</xf:value>
                </xf:item>
                <xf:item>
                    <xf:label>Other</xf:label>
                    <xf:value/>
                </xf:item>
                <xf:dispatch
                  event="xforms-select"
                  if="xxf:is-blank(event('xxf:item-value'))"
                  delay="0"
                  name="xforms-focus"
                  targetid="my-input"/>
              </xf:select1>
              <xf:input id="my-input" ref="value2[../value1 != 'cat']"/>
            </xh:body>
          </xh:html>

        withContainingDocument(doc) {

          assert(isRelevant("my-select1"))
          assert(! isRelevant("my-input"))

          assert(! hasFocus("my-select1"))
          assert(! hasFocus("my-input"))

          setControlValueWithEventSearchNested("my-select1", "")

          assert(isRelevant("my-input"))
          assert(hasFocus("my-input"))
        }
      }
    }
  }
}