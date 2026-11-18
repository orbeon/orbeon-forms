/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import org.orbeon.dom
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.processor.ControlsComparator
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class XFormsTriggerControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike {

  describe("Trigger control nested inside XBL with `xxbl:label-for` (#6343)") {

    it("must emit Ajax `hint` updates when the referencing XBL component's hint changes") {

      val formDoc =
        <xh:html
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xbl="http://www.w3.org/ns/xbl"
          xmlns:xxbl="http://orbeon.org/oxf/xml/xbl"
          xmlns:my="http://orbeon.org/oxf/xml/my">
          <xh:head>
            <xf:model id="model">
              <xf:instance id="instance">
                <instance>
                  <undo-trigger/>
                  <action/>
                </instance>
              </xf:instance>
              <xf:bind ref="undo-trigger" readonly="normalize-space(../action) = ''"/>
            </xf:model>
            <xbl:xbl>
              <xbl:binding
                id="my-trigger-binding"
                element="my|trigger"
                xxbl:mode="lhha custom-lhha binding"
                xxbl:label-for="trigger"
                xxbl:container="span">
                <xbl:template>
                  <xf:trigger id="trigger" ref="xxf:binding('my-trigger-binding')" xbl:attr="appearance">
                    <xf:label><xf:output value="xxf:label('my-trigger-binding')"/></xf:label>
                  </xf:trigger>
                </xbl:template>
              </xbl:binding>
            </xbl:xbl>
          </xh:head>
          <xh:body>
            <xf:var
              name="undo-title"
              value="if (not(xxf:readonly(undo-trigger))) then concat('Undo ', action) else ''"/>
            <my:trigger id="undo-trigger" appearance="minimal" ref="undo-trigger">
              <xf:label>Undo</xf:label>
              <xf:hint ref="$undo-title"/>
            </my:trigger>
          </xh:body>
        </xh:html>.toDocument

      withXFormsDocument(formDoc) { doc =>
        withContainingDocument(doc) {

          def collectDiffControls(): List[dom.Element] = {
            val ch = new LocationSAXContentHandler
            implicit val receiver: LocationSAXContentHandler = ch
            implicit val collector: EventCollector.ErrorEventCollector = EventCollector.Throw
            withDocument {
              withElement("control-values") {
                new ControlsComparator(doc, Map.empty, isTestMode = false).diffChildren(
                  left             = doc.controls.getInitialControlTree.children,
                  right            = doc.controls.getCurrentControlTree.children,
                  fullUpdateBuffer = None
                )
              }
            }
            ch.getDocument.getRootElement.elements("control").toList
          }

          def findInnerTriggerDiff(elems: List[dom.Element]): Option[dom.Element] =
            elems.find(_.attributeValue("id") == "undo-trigger≡trigger")

          def actionNode =
            instance("instance").get.rootElement / "action"

          val innerTrigger = getControl("undo-trigger≡trigger").asInstanceOf[XFormsTriggerControl]
          assert(innerTrigger.directOrByLocalLhhaValue(LHHA.Hint).contains(""))

          // 1. Transition from empty ("") to "Undo insert" (readonly: true -> false)
          withAction {
            setvalue(actionNode, "insert")
          }
          val diff1 = findInnerTriggerDiff(collectDiffControls())
          assert(diff1.isDefined)
          assert(diff1.get.attributeValue("hint") == "Undo insert")
          assert(diff1.get.attributeValue("readonly") == "false")
          separateClientEvents()

          // 2. Transition from "Undo insert" to "Undo rename" while `readonly` remains `false`
          withAction {
            setvalue(actionNode, "rename")
          }
          val diff2 = findInnerTriggerDiff(collectDiffControls())
          assert(diff2.isDefined)
          assert(diff2.get.attributeValue("hint") == "Undo rename")
          assert(diff2.get.attributeValue("readonly") eq null)
          separateClientEvents()

          // 3. No change -> no Ajax diff emitted for inner trigger
          withAction {
            setvalue(actionNode, "rename")
          }
          val diff3 = findInnerTriggerDiff(collectDiffControls())
          assert(diff3.isEmpty)
          separateClientEvents()

          // 4. Transition back to empty ("") when nothing left to undo (readonly: false -> true)
          withAction {
            setvalue(actionNode, "")
          }
          val diff4 = findInnerTriggerDiff(collectDiffControls())
          assert(diff4.isDefined)
          assert(diff4.get.attributeValue("hint") == "")
          assert(diff4.get.attributeValue("readonly") == "true")
          separateClientEvents()
        }
      }
    }
  }
}
