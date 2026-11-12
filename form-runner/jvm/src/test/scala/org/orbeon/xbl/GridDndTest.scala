/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.events.XXFormsDndEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector, XFormsEventTarget}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class GridDndTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:grid repeat drag and drop (xxforms-dnd)") {

    it("must reorder grid iterations when dispatching xxforms-dnd") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <items>
                    <items-iteration><name>Alice</name></items-iteration>
                    <items-iteration><name>Bob</name></items-iteration>
                    <items-iteration><name>Carol</name></items-iteration>
                  </items>
                </form>
              </xf:instance>
              <xf:instance id="items-template">
                <items-iteration><name/></items-iteration>
              </xf:instance>
              <xf:bind id="items-bind" ref="items">
                <xf:bind id="items-iteration-bind" ref="items-iteration" name="items-iteration">
                  <xf:bind id="name-bind" ref="name"/>
                </xf:bind>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:grid
              id="items-grid"
              repeat="content"
              bind="items-bind"
              template="instance('items-template')"
              min="1"
              freeze="1"
              number-rows="true">
              <xh:tr>
                <xh:td>
                  <xf:input id="name-control" bind="name-bind">
                    <xf:label>Name</xf:label>
                  </xf:input>
                </xh:td>
              </xh:tr>
            </fr:grid>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { implicit doc =>
          withActionAndDoc(doc) {

            def currentNames: List[String] =
              (1 to 3).map { i =>
                resolveObject[XFormsValueControl]("name-control", indexes = List(i)).get.getValue(EventCollector.Throw)
              }.toList

            def dispatchDnd(start: Int, end: Int): Unit =
              withAction {
                val target = doc.getObjectByEffectiveId("items-grid≡items-grid-repeat").asInstanceOf[XFormsEventTarget]
                Dispatch.dispatchEvent(
                  new XXFormsDndEvent(
                    target,
                    Map(
                      "dnd-start" -> Some(start.toString),
                      "dnd-end"   -> Some(end.toString)
                    )
                  ),
                  EventCollector.Throw
                )
              }

            assert(currentNames == List("Alice", "Bob", "Carol"))

            // Move row 3 ("Carol") to position 2 (before "Bob", after frozen "Alice")
            dispatchDnd(start = 3, end = 2)

            assert(currentNames == List("Alice", "Carol", "Bob"))

            val instanceNamesAfterFirstMove =
              (doc.findInstance("fr-form-instance").get.rootElement / "items" / "items-iteration" / "name").map(_.stringValue).toList
            assert(instanceNamesAfterFirstMove == List("Alice", "Carol", "Bob"))

            // Move row 2 ("Carol") back to position 3
            dispatchDnd(start = 2, end = 3)

            assert(currentNames == List("Alice", "Bob", "Carol"))
          }
        }
      }
    }
  }
}
