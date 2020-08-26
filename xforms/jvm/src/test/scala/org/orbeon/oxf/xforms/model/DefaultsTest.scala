/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.dom.QName
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.scalatest.funspec.AnyFunSpecLike

import scala.language.postfixOps

// Test for the `xxf:default` attribute on `xf:bind` and `xf:insert` with the `xxf:defaults` attribute.
class DefaultsTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Default value and insert") {
    it("must pass all assertions") {
      withTestExternalContext { _ =>

        val doc = this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
              <xf:model id="my-model">
                <xf:instance id="my-instance">
                  <data>
                    <value/>
                  </data>
                </xf:instance>
                <xf:bind ref="value" xxf:default="count(//*)"/>
              </xf:model>
            </xh:head>
            <xh:body/>
          </xh:html>

        withActionAndDoc(doc) {

          val instance = doc.defaultModel flatMap (_. defaultInstanceOpt) get

          val expected = List("2", "", "4", "7", "", "7")

          def stringValues =
            instance.rootElement / "value" map (_.stringValue)

          def assertStringValues(i: Int) = {
            val values = stringValues
            assert(i === values.size)
            assert((expected take i) === values)
          }

          def insertOne(requireDefaultValues: Boolean) =
            insert(
              after                = instance.rootElement / "value",
              origin               = NodeInfoFactory.elementInfo(QName("value")),
              requireDefaultValues = requireDefaultValues
            )

          // Initial state has one initial value
          assertStringValues(1)

          // Regular insert doesn't cause new initial values
          withAction {
            insertOne(requireDefaultValues = false)
          }

          assertStringValues(2)

          // But this new element takes its initial value
          withAction {
            insertOne(requireDefaultValues = true)
          }

          assertStringValues(3)

          // Regular recalculate doesn't change anything
          withAction {
            recalculate(instance.model.getId, applyDefaults = false)
          }

          assertStringValues(3)

          // Check only inserts with `requireDefaultValues = true` have an effect
          withAction {

            recalculate(instance.model.getId, applyDefaults = false)

            insertOne(requireDefaultValues = true)
            insertOne(requireDefaultValues = false)
            insertOne(requireDefaultValues = true)

            // Explicit rr also works
            rebuild(instance.model.getId)
            recalculate(instance.model.getId, applyDefaults = false)
          }

          assertStringValues(6)

          // Apply all defaults changes all values
          withAction {
            recalculate(instance.model.getId, applyDefaults = true)
          }

          assert(List.fill(6)("7") === stringValues)
        }
      }
    }
  }

  describe("Default value and submission") {
    it("must pass all assertions") {
      withTestExternalContext { _ =>

        val doc = this setupDocument
          <xh:html
            xmlns:xh="http://www.w3.org/1999/xhtml"
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
            <xh:head>
              <xf:model id="my-model">
                <xf:instance id="my-instance">
                  <data>
                    <oldvalue/>
                  </data>
                </xf:instance>
                <xf:bind ref="*" xxf:default="count((preceding-sibling::*, ancestor::*)) + 1"/>
                <xf:instance id="newvalue-instance">
                  <newvalue/>
                </xf:instance>
                <xf:instance id="new-instance">
                  <data>
                    <some/>
                    <value/>
                  </data>
                </xf:instance>
                <xf:submission
                  id="replace-element-submission"
                  ref="instance('newvalue-instance')"
                  method="post"
                  action="echo:"
                  replace="instance"
                  targetref="instance()/*"
                  xxf:defaults="{event('defaults')}"/>
                <xf:submission
                    id="replace-entire-instance-submission"
                    ref="instance('new-instance')"
                    method="post"
                    action="echo:"
                    replace="instance"
                    instance="my-instance"
                    xxf:defaults="{event('defaults')}"/>
              </xf:model>
            </xh:head>
            <xh:body/>
          </xh:html>

        withActionAndDoc(doc) {

          val instance = doc.defaultModel flatMap (_. defaultInstanceOpt) get

          assert("2" === (instance.rootElement / "oldvalue" stringValue))
          assert(""  === (instance.rootElement / "newvalue" stringValue))

          // Replace `oldvalue` with `newvalue` but do not recompute defaults
          withAction {
            sendThrowOnError("replace-element-submission", Map("defaults" -> Some("false")))
          }

          assert(instance.rootElement / "oldvalue" isEmpty)
          assert(instance.rootElement / "newvalue" nonEmpty)
          assert("" === (instance.rootElement / "newvalue" stringValue))

          // Replace `oldvalue` with `newvalue` and recompute defaults
          withAction {
            sendThrowOnError("replace-element-submission", Map("defaults" -> Some("true")))
          }

          assert("2" === (instance.rootElement / "newvalue" stringValue))

          // Replace entire instance and do not recompute defaults
          withAction {
            sendThrowOnError("replace-entire-instance-submission", Map("defaults" -> Some("false")))
          }

          assert("" === (instance.rootElement / "some" stringValue))
          assert("" === (instance.rootElement / "value" stringValue))

          // Replace entire instance and recompute defaults
          withAction {
            sendThrowOnError("replace-entire-instance-submission", Map("defaults" -> Some("true")))
          }

          assert("2" === (instance.rootElement / "some" stringValue))
          assert("3" === (instance.rootElement / "value" stringValue))
        }
      }
    }
  }
}
