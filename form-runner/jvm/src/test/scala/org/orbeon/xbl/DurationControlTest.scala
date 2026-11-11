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

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.xbl.DurationSupport.DurationUnit.*
import org.orbeon.xbl.DurationSupport.{DurationFieldValues, ParsedDuration}
import org.scalatest.funspec.AnyFunSpecLike


class DurationControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("DurationSupport helper") {

    it("must parse active fields with default fallback") {
      assert(DurationSupport.parseFields("hours minutes")              == List(Hours, Minutes))
      assert(DurationSupport.parseFields("days hours minutes seconds") == List(Days, Hours, Minutes, Seconds))
      assert(DurationSupport.parseFields("")                           == List(Hours, Minutes))
      assert(DurationSupport.parseFields("invalid")                    == List(Hours, Minutes))
    }

    it("must parse valid xs:dayTimeDuration strings and reject invalid ones") {
      assert(DurationSupport.parseIsoDuration("PT2H30M")    .contains(ParsedDuration(negative = false, 0, 2, 30, 0)))
      assert(DurationSupport.parseIsoDuration("P1DT4H")     .contains(ParsedDuration(negative = false, 1, 4, 0, 0)))
      assert(DurationSupport.parseIsoDuration("P3D")        .contains(ParsedDuration(negative = false, 3, 0, 0, 0)))
      assert(DurationSupport.parseIsoDuration("PT45M")      .contains(ParsedDuration(negative = false, 0, 0, 45, 0)))
      assert(DurationSupport.parseIsoDuration("PT0M")       .contains(ParsedDuration(negative = false, 0, 0, 0, 0)))
      assert(DurationSupport.parseIsoDuration("PT0S")       .contains(ParsedDuration(negative = false, 0, 0, 0, 0)))
      assert(DurationSupport.parseIsoDuration("P0D")        .contains(ParsedDuration(negative = false, 0, 0, 0, 0)))
      assert(DurationSupport.parseIsoDuration("-PT1H15M30S").contains(ParsedDuration(negative = true,  0, 1, 15, 30)))
      assert(DurationSupport.parseIsoDuration("PT12.5S")    .contains(ParsedDuration(negative = false, 0, 0, 0, BigDecimal("12.5"))))

      assert(DurationSupport.parseIsoDuration("").isEmpty)
      assert(DurationSupport.parseIsoDuration("P").isEmpty)
      assert(DurationSupport.parseIsoDuration("PT").isEmpty)
      assert(DurationSupport.parseIsoDuration("P1Y2M").isEmpty)
      assert(DurationSupport.parseIsoDuration("invalid").isEmpty)
    }

    it("must decompose and balance durations into the configured active fields") {
      val hmFields  = DurationSupport.parseFields("hours minutes")
      val dhmFields = DurationSupport.parseFields("days hours minutes")
      val mFields   = DurationSupport.parseFields("minutes")

      assert(DurationSupport.toFieldValues("PT2H30M", hmFields)    == DurationFieldValues("", "2", "30", ""))
      assert(DurationSupport.toFieldValues("PT36H30M", hmFields)   == DurationFieldValues("", "36", "30", ""))
      assert(DurationSupport.toFieldValues("P1DT12H30M", hmFields) == DurationFieldValues("", "36", "30", ""))
      assert(DurationSupport.toFieldValues("PT36H30M", dhmFields)  == DurationFieldValues("1", "12", "30", ""))
      assert(DurationSupport.toFieldValues("PT1H30M", mFields)     == DurationFieldValues("", "", "90", ""))
      assert(DurationSupport.toFieldValues("PT0M", hmFields)       == DurationFieldValues("", "0", "0", ""))
    }

    it("must serialize unit inputs to compact ISO-8601 xs:dayTimeDuration") {
      val hmFields  = DurationSupport.parseFields("hours minutes")
      val dhmFields = DurationSupport.parseFields("days hours minutes")
      val dFields   = DurationSupport.parseFields("days")

      assert(DurationSupport.fromFieldValues(DurationFieldValues("", "2", "30", ""), hmFields)   == "PT2H30M")
      assert(DurationSupport.fromFieldValues(DurationFieldValues("", "1", "90", ""), hmFields)   == "PT2H30M")
      assert(DurationSupport.fromFieldValues(DurationFieldValues("", "36", "30", ""), hmFields)  == "PT36H30M")
      assert(DurationSupport.fromFieldValues(DurationFieldValues("1", "26", "0", ""), dhmFields) == "P2DT2H")
      assert(DurationSupport.fromFieldValues(DurationFieldValues("", "0", "0", ""), hmFields)    == "PT0M")
      assert(DurationSupport.fromFieldValues(DurationFieldValues("0", "", "", ""), dFields)      == "P0D")
      assert(DurationSupport.fromFieldValues(DurationFieldValues("", "", "", ""), hmFields)      == "")
    }

    it("must format readonly duration strings with unit suffixes") {
      assert(DurationSupport.formatReadonly("PT2H30M",    "hours minutes",      "d", "h", "min", "s") == "2 h 30 min")
      assert(DurationSupport.formatReadonly("PT2H",       "hours minutes",      "d", "h", "min", "s") == "2 h")
      assert(DurationSupport.formatReadonly("PT36H30M",   "hours minutes",      "d", "h", "min", "s") == "36 h 30 min")
      assert(DurationSupport.formatReadonly("PT36H30M",   "days hours minutes", "d", "h", "min", "s") == "1 d 12 h 30 min")
      assert(DurationSupport.formatReadonly("PT0M",       "hours minutes",      "d", "h", "min", "s") == "0 min")
      assert(DurationSupport.formatReadonly("",           "hours minutes",      "d", "h", "min", "s") == "")
    }

    it("must step unit values with non-negative floor") {
      assert(DurationSupport.stepUnitValue("",  1)  == "1")
      assert(DurationSupport.stepUnitValue("", -1)  == "0")
      assert(DurationSupport.stepUnitValue("0", -1) == "0")
      assert(DurationSupport.stepUnitValue("5",  1) == "6")
      assert(DurationSupport.stepUnitValue("5", -1) == "4")
    }
  }

  describe("fr:duration control") {

    it("must bind to xf:dayTimeDuration, synchronize values, and validate ISO-8601 durations") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <duration-hm>PT2H30M</duration-hm>
                  <duration-dhm>P1DT4H15M</duration-dhm>
                </form>
              </xf:instance>
              <xf:bind ref="duration-hm"  type="xf:dayTimeDuration"/>
              <xf:bind ref="duration-dhm" type="xs:dayTimeDuration"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:duration id="duration-hm-control" ref="duration-hm">
              <xf:label>Estimated Duration</xf:label>
            </fr:duration>
            <fr:duration id="duration-dhm-control" ref="duration-dhm" fields="days hours minutes">
              <xf:label>Elapsed Time</xf:label>
            </fr:duration>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          val hmControl  = doc.resolveObjectByIdInScope("#document", "duration-hm-control", None).get.asInstanceOf[XFormsValueComponentControl]
          val dhmControl = doc.resolveObjectByIdInScope("#document", "duration-dhm-control", None).get.asInstanceOf[XFormsValueComponentControl]

          assert(hmControl.getValue(EventCollector.Throw)  == "PT2H30M")
          assert(hmControl.isValid)
          assert(dhmControl.getValue(EventCollector.Throw) == "P1DT4H15M")
          assert(dhmControl.isValid)

          setControlValueWithEventSearchNested(hmControl.effectiveId, "PT45M")
          assert(hmControl.getValue(EventCollector.Throw) == "PT45M")
          assert(hmControl.isValid)

          setControlValueWithEventSearchNested(hmControl.effectiveId, "PT0M")
          assert(hmControl.getValue(EventCollector.Throw) == "PT0M")
          assert(hmControl.isValid)

          setControlValueWithEventSearchNested(hmControl.effectiveId, "")
          assert(hmControl.getValue(EventCollector.Throw) == "")
          assert(hmControl.isValid)

          setControlValueWithEventSearchNested(hmControl.effectiveId, "not-a-duration")
          assert(! hmControl.isValid)
        }
      }
    }

    it("must format values in static-readonly mode according to configured fields") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model
              xxf:readonly-appearance="static"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <duration-hm>PT36H30M</duration-hm>
                  <duration-dhm>PT36H30M</duration-dhm>
                  <duration-zero>PT0M</duration-zero>
                </form>
              </xf:instance>
              <xf:bind ref="duration-hm"   type="xf:dayTimeDuration" readonly="true()"/>
              <xf:bind ref="duration-dhm"  type="xf:dayTimeDuration" readonly="true()"/>
              <xf:bind ref="duration-zero" type="xf:dayTimeDuration" readonly="true()"/>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:duration id="duration-hm-control" ref="duration-hm" fields="hours minutes" fr:static-readonly="true">
              <xf:label>HM Duration</xf:label>
            </fr:duration>
            <fr:duration id="duration-dhm-control" ref="duration-dhm" fields="days hours minutes" fr:static-readonly="true">
              <xf:label>DHM Duration</xf:label>
            </fr:duration>
            <fr:duration id="duration-zero-control" ref="duration-zero" fields="hours minutes" fr:static-readonly="true">
              <xf:label>Zero Duration</xf:label>
            </fr:duration>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          def formattedValue(controlId: String): Option[String] = {
            val component = doc.resolveObjectByIdInScope("#document", controlId, None).get.asInstanceOf[XFormsValueComponentControl]
            component.getFormattedValue(EventCollector.Throw)
          }

          assert(formattedValue("duration-hm-control")   .contains("36 h 30 min"))
          assert(formattedValue("duration-dhm-control")  .contains("1 d 12 h 30 min"))
          assert(formattedValue("duration-zero-control") .contains("0 min"))
        }
      }
    }
  }
}
