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
  }
}
