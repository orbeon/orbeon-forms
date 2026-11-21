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

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.*
import org.scalatest.funspec.AnyFunSpecLike


class QuantityControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("fr:quantity XBL control") {

    it("must stamp default @unit on value entry, keep value on unit change by default, auto-convert when enabled, and evaluate fr:quantity-convert") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:expose-xpath-types="true" xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <weight-keep unit=""/>
                  <weight-auto unit="lb">10</weight-auto>
                  <weight-kg/>
                  <weight-unit/>
                </form>
              </xf:instance>
              <xf:bind ref="weight-keep" type="xf:decimal"/>
              <xf:bind ref="weight-auto" type="xf:decimal"/>
              <xf:bind ref="weight-kg"   calculate="round-half-to-even(fr:quantity-convert(../weight-auto, 'kg'), 2)"/>
              <xf:bind ref="weight-unit" calculate="fr:quantity-unit(../weight-auto)"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:quantity
              id="weight-keep-control"
              ref="weight-keep"
              unit-preset="mass"
              units="lb oz kg g"
              default-unit="lb"
              digits-after-decimal="2">
              <xf:label>Weight (Keep)</xf:label>
            </fr:quantity>
            <fr:quantity
              id="weight-auto-control"
              ref="weight-auto"
              unit-preset="mass"
              units="lb kg"
              default-unit="lb"
              convert-on-unit-change="true"
              digits-after-decimal="2">
              <xf:label>Weight (Auto Convert)</xf:label>
            </fr:quantity>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          def dataRoot = doc.findInstance("fr-form-instance").get.rootElement

          // Initial state: weight-auto has 10 lb -> 4.54 kg via fr:quantity-convert
          assert((dataRoot / "weight-kg").stringValue == "4.54")
          assert((dataRoot / "weight-unit").stringValue == "lb")

          // Enter numeric value into weight-keep -> stamps @unit="lb"
          def numberJson(v: String): String =
            NumberExternalValue(v, v, '.').asJson.noSpaces

          withActionAndDoc(doc) {
            setControlValueWithEventSearchNested("weight-keep-control≡number-input", numberJson("19.8"))
          }
          assert((dataRoot / "weight-keep").stringValue == "19.8")
          assert((dataRoot / "weight-keep" /@ "unit").stringValue == "lb")

          // Switch weight-keep unit to kg with convert-on-unit-change="false" -> keeps 19.8, updates @unit="kg"
          withActionAndDoc(doc) {
            setControlValueWithEventSearchNested("weight-keep-control≡unit-select", "kg")
          }
          assert((dataRoot / "weight-keep").stringValue == "19.8")
          assert((dataRoot / "weight-keep" /@ "unit").stringValue == "kg")

          // Switch weight-auto unit from lb to kg with convert-on-unit-change="true" -> converts 10 lb to 4.54 kg
          withActionAndDoc(doc) {
            setControlValueWithEventSearchNested("weight-auto-control≡unit-select", "kg")
          }
          assert((dataRoot / "weight-auto").stringValue == "4.54")
          assert((dataRoot / "weight-auto" /@ "unit").stringValue == "kg")
          assert((dataRoot / "weight-unit").stringValue == "kg")
        }
      }
    }

    it("must format value with unit symbol in static-readonly (View) and Summary modes") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:expose-xpath-types="true" xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <weight unit="lb">19.8</weight>
                  <temp unit="C">21</temp>
                  <summary-weight>19.8 lb</summary-weight>
                </form>
              </xf:instance>
              <xf:bind ref="*" readonly="true()"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:quantity id="weight-view" ref="weight" unit-preset="mass" digits-after-decimal="2" fr:static-readonly="true">
              <xf:label>Weight</xf:label>
            </fr:quantity>
            <fr:quantity id="temp-view" ref="temp" unit-preset="temperature" fr:static-readonly="true">
              <xf:label>Temp</xf:label>
            </fr:quantity>
            <fr:quantity id="summary-view" ref="summary-weight" unit-preset="mass" fr:summary="true">
              <xf:label>Summary Weight</xf:label>
            </fr:quantity>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val weightOut  = doc.getControlByEffectiveId("weight-view≡output").asInstanceOf[XFormsValueControl]
          val tempOut    = doc.getControlByEffectiveId("temp-view≡output").asInstanceOf[XFormsValueControl]
          val summaryOut = doc.getControlByEffectiveId("summary-view≡output").asInstanceOf[XFormsValueControl]

          assert(weightOut.getFormattedValue(EventCollector.Throw).contains("19.80 lb"))
          assert(tempOut.getFormattedValue(EventCollector.Throw).contains("21 °C"))
          assert(summaryOut.getFormattedValue(EventCollector.Throw).contains("19.8 lb"))
        }
      }
    }
  }
}
