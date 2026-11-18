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
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.analysis.controls.AttributeControl
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls.{XFormsTriggerControl, XXFormsAttributeControl}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class PagerTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  private def buildTestDoc(initialTotal: Int) =
    <xh:html
      xml:lang="en"
      xmlns:xf="http://www.w3.org/2002/xforms"
      xmlns:xh="http://www.w3.org/1999/xhtml"
      xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
      xmlns:xs="http://www.w3.org/2001/XMLSchema"
      xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
      <xh:head>
        <xf:model id="fr-resources-model">
          <xf:instance id="fr-fr-resources">
            <resources>
              <resource xml:lang="en">
                <summary>
                  <paging>
                    <to>to</to>
                    <of>of</of>
                    <page>Page</page>
                  </paging>
                </summary>
                <buttons>
                  <previous>Previous</previous>
                  <next>Next</next>
                </buttons>
              </resource>
            </resources>
          </xf:instance>
        </xf:model>
        <xf:model
          id="fr-form-model"
          xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary"
          xxf:xbl-support="org.orbeon.oxf.fr.xbl.FormRunnerXblSupport"
          xxf:expose-xpath-types="true">
          <xf:instance id="fr-form-instance">
            <state>
              <total>{initialTotal}</total>
              <from>0</from>
              <to>0</to>
              <page-number>1</page-number>
              <changed-count>0</changed-count>
            </state>
          </xf:instance>
          <xf:bind ref="instance('fr-form-instance')/*" type="xs:integer"/>
        </xf:model>
      </xh:head>
      <xh:body>
        <fr:pager
          id="top-pager"
          model="fr-form-model"
          page-size="10"
          total-value="instance('fr-form-instance')/total"
          from-ref="instance('fr-form-instance')/from"
          to-ref="instance('fr-form-instance')/to"
          page-number-ref="instance('fr-form-instance')/page-number">
          <xf:setvalue
            event="fr-pager-changed"
            ref="instance('fr-form-instance')/changed-count"
            value=". + 1"/>
        </fr:pager>

        <xf:group model="fr-form-model" ref=".[instance('fr-form-instance')/total gt 10]">
          <fr:pager
            id="bottom-pager"
            page-size="10"
            total-value="instance('fr-form-instance')/total"
            page-number-ref="instance('fr-form-instance')/page-number">
            <xf:setvalue
              event="fr-pager-changed"
              ref="instance('fr-form-instance')/changed-count"
              value=". + 1"/>
          </fr:pager>
        </xf:group>
      </xh:body>
    </xh:html>.toDocument

  describe("fr:pager single-page and multi-page rendering and two-way synchronization") {

    it("must hide navigation arrows when total <= page-size (including total = 0) and show them when total > page-size") {
      withTestExternalContext { implicit ec =>
        withXFormsDocument(buildTestDoc(initialTotal = 8)) { doc =>
          withActionAndDoc(doc) {

            def classAttributesFor(pagerId: String): List[String] = {
              val comp = doc.getControlByEffectiveId(pagerId).asInstanceOf[XFormsComponentControl]
              ControlsIterator(comp, includeSelf = false)
                .collect {
                  case c: XXFormsAttributeControl
                    if c.isRelevant &&
                       c.staticControl.asInstanceOf[AttributeControl].attributeName == "class" =>
                    c.getExternalValue(EventCollector.Throw)
                }
                .toList
            }

            def rangeTextFor(pagerId: String): Option[String] = {
              val comp = doc.getControlByEffectiveId(pagerId).asInstanceOf[XFormsComponentControl]
              ControlsIterator(comp, includeSelf = false)
                .collect {
                  case c: XFormsValueControl
                    if ! c.isInstanceOf[XXFormsAttributeControl] &&
                       c.isRelevant =>
                    c.getValue(EventCollector.Throw)
                }
                .find(_.contains(" to "))
            }

            // 1. Single page (8 items, page-size = 10)
            val singlePageClasses = classAttributesFor("top-pager")
            assert(singlePageClasses.exists(_.contains("fr-pagination-single-page")))
            assert(singlePageClasses.count(_.contains("xforms-hidden")) == 4)
            assert(rangeTextFor("top-pager").contains("1 to 8 of 8"))
            assert(! doc.getControlByEffectiveId("bottom-pager").isRelevant)

            // 2. Empty state (0 items)
            withAction {
              val totalNode = doc.findInstance("fr-form-instance").get.rootElement / "total"
              XFormsAPI.setvalue(totalNode, "0")
            }
            val zeroClasses = classAttributesFor("top-pager")
            assert(zeroClasses.exists(_.contains("fr-pagination-single-page")))
            assert(zeroClasses.count(_.contains("xforms-hidden")) == 4)
            assert(rangeTextFor("top-pager").isEmpty)
            assert(! doc.getControlByEffectiveId("bottom-pager").isRelevant)

            // 3. Multi-page (25 items, page-size = 10 -> 3 pages)
            withAction {
              val totalNode = doc.findInstance("fr-form-instance").get.rootElement / "total"
              XFormsAPI.setvalue(totalNode, "25")
            }
            val multiPageClasses = classAttributesFor("top-pager")
            assert(! multiPageClasses.exists(_.contains("fr-pagination-single-page")))
            assert(! multiPageClasses.exists(_.contains("xforms-hidden")))
            assert(rangeTextFor("top-pager").contains("1 to 10 of 25"))
            assert(doc.getControlByEffectiveId("bottom-pager").isRelevant)
            assert(rangeTextFor("bottom-pager").contains("1 to 10 of 25"))
          }
        }
      }
    }

    it("must synchronize page-number two-way between top and bottom pagers and external resets") {
      withTestExternalContext { implicit ec =>
        withXFormsDocument(buildTestDoc(initialTotal = 25)) { doc =>
          withActionAndDoc(doc) {

            def findTrigger(pagerId: String, triggerClass: String): XFormsTriggerControl = {
              val comp = doc.getControlByEffectiveId(pagerId).asInstanceOf[XFormsComponentControl]
              ControlsIterator(comp, includeSelf = false)
                .collectFirst {
                  case t: XFormsTriggerControl
                    if Option(t.staticControl.element.attributeValue("class")).exists(_.splitTo[Set]().contains(triggerClass)) =>
                    t
                }
                .getOrElse(sys.error(s"Missing `$triggerClass` in `$pagerId`"))
            }

            def rangeTextFor(pagerId: String): String = {
              val comp = doc.getControlByEffectiveId(pagerId).asInstanceOf[XFormsComponentControl]
              ControlsIterator(comp, includeSelf = false)
                .collect {
                  case c: XFormsValueControl
                    if ! c.isInstanceOf[XXFormsAttributeControl] &&
                       c.isRelevant =>
                    c.getValue(EventCollector.Throw)
                }
                .find(_.contains(" to "))
                .getOrElse("")
            }

            def stateVal(elemName: String): Int =
              (doc.findInstance("fr-form-instance").get.rootElement / elemName).stringValue.toInt

            assert(stateVal("from")          == 1)
            assert(stateVal("to")            == 10)
            assert(stateVal("page-number")   == 1)
            assert(stateVal("changed-count") == 0)
            assert(findTrigger("top-pager",    "fr-navigate-prev").isReadonly)
            assert(! findTrigger("bottom-pager", "fr-navigate-next").isReadonly)

            // Click "Next" on bottom pager -> top pager, from, to, and page-number all advance to page 2 (only 1 changed event!)
            activateControlWithEvent(findTrigger("bottom-pager", "fr-navigate-next").effectiveId)
            assert(stateVal("page-number")   == 2)
            assert(stateVal("from")          == 11)
            assert(stateVal("to")            == 20)
            assert(stateVal("changed-count") == 1)
            assert(rangeTextFor("top-pager")    == "11 to 20 of 25")
            assert(rangeTextFor("bottom-pager") == "11 to 20 of 25")

            // Click "Last" on top pager -> bottom pager syncs to page 3 ("21 to 25 of 25")
            activateControlWithEvent(findTrigger("top-pager", "fr-navigate-last").effectiveId)
            assert(stateVal("page-number")   == 3)
            assert(stateVal("from")          == 21)
            assert(stateVal("to")            == 25)
            assert(stateVal("changed-count") == 2)
            assert(rangeTextFor("top-pager")    == "21 to 25 of 25")
            assert(rangeTextFor("bottom-pager") == "21 to 25 of 25")
            assert(findTrigger("bottom-pager", "fr-navigate-next").isReadonly)

            // External reset of shared `page-number` node (e.g. `fr-reset-search` on Summary page) resets both pagers to page 1
            withAction {
              val pageNode = doc.findInstance("fr-form-instance").get.rootElement / "page-number"
              XFormsAPI.setvalue(pageNode, "1")
            }
            assert(stateVal("page-number")      == 1)
            assert(stateVal("from")             == 1)
            assert(stateVal("to")               == 10)
            assert(rangeTextFor("top-pager")    == "1 to 10 of 25")
            assert(rangeTextFor("bottom-pager") == "1 to 10 of 25")
          }
        }
      }
    }
  }
}
