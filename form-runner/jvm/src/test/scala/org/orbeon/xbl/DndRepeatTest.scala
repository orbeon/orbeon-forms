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
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.event.events.XXFormsDndEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class DndRepeatTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:dnd-repeat with fr-dnd-drop-indicator (xxforms-dnd)") {

    it("must reorder repeat items when dispatching xxforms-dnd") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model>
              <xf:instance id="main">
                <columns>
                  <column control="first-name"/>
                  <column control="last-name"/>
                  <column control="email"/>
                </columns>
              </xf:instance>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:dnd-repeat id="columns-repeat" ref="column" class="fr-dnd-drop-indicator">
              <xh:div class="xforms-dnd-item xforms-dnd-moves">
                <xh:span class="xforms-dnd-handle"/>
                <xf:output id="col-output" value="@control"/>
              </xh:div>
            </fr:dnd-repeat>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { implicit doc =>
          withActionAndDoc(doc) {

            def currentControls: List[String] =
              (1 to 3).map { i =>
                resolveObject[XFormsValueControl]("col-output", indexes = List(i)).get.getValue(EventCollector.Throw)
              }.toList

            def dispatchDnd(start: Int, end: Int): Unit =
              withAction {
                val target =
                  doc.controls.getCurrentControlTree.effectiveIdsToControls.values
                    .collectFirst { case r: XFormsRepeatControl => r }
                    .get
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

            assert(currentControls == List("first-name", "last-name", "email"))

            // Move item 3 ("email") to position 1
            dispatchDnd(start = 3, end = 1)
            assert(currentControls == List("email", "first-name", "last-name"))
            assert(
              (doc.findInstance("main").get.rootElement / "column").map(_.attValue("control")).toList ==
                List("email", "first-name", "last-name")
            )

            // Move item 1 ("email") to position 2
            dispatchDnd(start = 1, end = 2)
            assert(currentControls == List("first-name", "email", "last-name"))
          }
        }
      }
    }
  }
}
