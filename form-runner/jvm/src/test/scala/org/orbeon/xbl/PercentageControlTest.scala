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
package org.orbeon.xbl

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class PercentageControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:percentage control") {

    it("must format with % suffix, store fractional decimal values, and validate") {

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
                  <my-percentage>0.255</my-percentage>
                </form>
              </xf:instance>
              <xf:bind ref="my-percentage" type="xf:decimal"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:percentage id="my-percentage-control" ref="my-percentage">
              <xf:label>Percentage</xf:label>
            </fr:percentage>
          </xh:body>
        </xh:html>.toDocument

      def externalJson(v: String): String =
        NumberExternalValue(v, v, '.').asJson.noSpaces

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val percentageComponent = doc.resolveObjectByIdInScope("#document", "my-percentage-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val suffixOutput        = percentageComponent.nestedContainerOpt.get.resolveObjectByIdInScope(percentageComponent.innerRootControl.effectiveId, "suffix", None).get.asInstanceOf[XFormsValueControl]

          assert(percentageComponent.getValue(EventCollector.Throw) == "0.255")
          assert(percentageComponent.getFormattedValue(EventCollector.Throw).contains("25.5 %"))
          assert(percentageComponent.getExternalValue(EventCollector.Throw) == NumberExternalValue("25.5", "25.5", '.').asJson.noSpaces)
          assert(suffixOutput.getValue(EventCollector.Throw) == "%")
          assert(percentageComponent.isValid)

          setControlValueWithEventSearchNested(percentageComponent.effectiveId, externalJson("25"))
          assert(percentageComponent.getValue(EventCollector.Throw) == "0.25")
          assert(percentageComponent.getFormattedValue(EventCollector.Throw).contains("25 %"))
          assert(percentageComponent.getExternalValue(EventCollector.Throw) == NumberExternalValue("25", "25", '.').asJson.noSpaces)
          assert(percentageComponent.isValid)

          setControlValueWithEventSearchNested(percentageComponent.effectiveId, externalJson("1,234.75"))
          assert(percentageComponent.getValue(EventCollector.Throw) == "12.3475")
          assert(percentageComponent.getFormattedValue(EventCollector.Throw).contains("1,234.75 %"))
          assert(percentageComponent.getExternalValue(EventCollector.Throw) == NumberExternalValue("1,234.75", "1234.75", '.').asJson.noSpaces)
          assert(percentageComponent.isValid)

          setControlValueWithEventSearchNested(percentageComponent.effectiveId, externalJson("abc"))
          assert(percentageComponent.getValue(EventCollector.Throw) == "abc")
          assert(! percentageComponent.isValid)

          setControlValueWithEventSearchNested(percentageComponent.effectiveId, externalJson(""))
          assert(percentageComponent.getValue(EventCollector.Throw) == "")
          assert(percentageComponent.isValid)
        }
      }
    }

    it("must bind fr:number-stepper and fr:number[appearance ~= stepper] and format values") {

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
                  <qty>3</qty>
                  <guests>2</guests>
                </form>
              </xf:instance>
              <xf:bind ref="qty" type="xf:integer" xxf:non-negative="true()"/>
              <xf:bind ref="guests" type="xf:decimal"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:number-stepper id="qty-control" ref="qty" suffix="items">
              <xf:label>Quantity</xf:label>
            </fr:number-stepper>
            <fr:number id="guests-control" ref="guests" appearance="stepper" step="0.5" min="0" max="10">
              <xf:label>Guests</xf:label>
            </fr:number>
          </xh:body>
        </xh:html>.toDocument

      def externalJson(v: String): String =
        NumberExternalValue(v, v, '.').asJson.noSpaces

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val qtyComponent    = doc.resolveObjectByIdInScope("#document", "qty-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val guestsComponent = doc.resolveObjectByIdInScope("#document", "guests-control", None).get.asInstanceOf[XFormsValueComponentControl]

          assert(qtyComponent.getValue(EventCollector.Throw) == "3")
          assert(qtyComponent.getFormattedValue(EventCollector.Throw).contains("3 items"))
          assert(qtyComponent.getExternalValue(EventCollector.Throw) == NumberExternalValue("3", "3", '.').asJson.noSpaces)

          setControlValueWithEventSearchNested(qtyComponent.effectiveId, externalJson("4"))
          assert(qtyComponent.getValue(EventCollector.Throw) == "4")
          assert(qtyComponent.getFormattedValue(EventCollector.Throw).contains("4 items"))

          assert(guestsComponent.getValue(EventCollector.Throw) == "2")
          setControlValueWithEventSearchNested(guestsComponent.effectiveId, externalJson("2.5"))
          assert(guestsComponent.getValue(EventCollector.Throw) == "2.5")
          assert(guestsComponent.getExternalValue(EventCollector.Throw) == NumberExternalValue("2.5", "2.5", '.').asJson.noSpaces)
        }
      }
    }
  }
}
