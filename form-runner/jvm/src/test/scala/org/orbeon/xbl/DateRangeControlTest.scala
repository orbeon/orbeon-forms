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

import org.orbeon.date.IsoDate
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.xforms.control.{XFormsValueComponentControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike


class DateRangeControlTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with XFormsSupport {

  describe("DateRangeSupport helper") {

    it("must combine and split valid and invalid date endpoints losslessly") {
      assert(DateRangeSupport.combineStartEnd("", "")                       == "")
      assert(DateRangeSupport.combineStartEnd("2026-07-01", "2026-07-14")   == "2026-07-01/2026-07-14")
      assert(DateRangeSupport.combineStartEnd("2026-07-01", "")             == "2026-07-01/")
      assert(DateRangeSupport.combineStartEnd("", "2026-07-14")             == "/2026-07-14")

      // Unrecognized user input containing slashes or percent signs
      val combinedWithSlashes = DateRangeSupport.combineStartEnd("07/32/2026", "08/15/2026")
      assert(combinedWithSlashes == "07%2F32%2F2026/08%2F15%2F2026")
      assert(DateRangeSupport.splitStartEnd(combinedWithSlashes) == ("07/32/2026", "08/15/2026"))

      val combinedWithPercent = DateRangeSupport.combineStartEnd("foo%2Fbar", "baz/qux")
      assert(DateRangeSupport.splitStartEnd(combinedWithPercent) == ("foo%2Fbar", "baz/qux"))

      assert(DateRangeSupport.splitStartEnd("") == ("", ""))
      assert(DateRangeSupport.splitStartEnd("2026-07-01/2026-07-14") == ("2026-07-01", "2026-07-14"))
    }

    it("must parse valid ISO-8601 date intervals and compute days between endpoints") {
      assert(
        DateRangeSupport.parseDateRange("2026-07-01/2026-07-14")
          .contains((IsoDate(2026, 7, 1), IsoDate(2026, 7, 14)))
      )
      assert(DateRangeSupport.daysBetweenOpt("2026-07-01/2026-07-14").contains(13L))
      assert(DateRangeSupport.daysBetweenOpt("2026-07-01/2026-07-01").contains(0L))
      assert(DateRangeSupport.daysBetweenOpt("2026-07-14/2026-07-01").contains(-13L))
      assert(DateRangeSupport.daysBetweenOpt("2026-07-01/").isEmpty)
      assert(DateRangeSupport.daysBetweenOpt("/2026-07-14").isEmpty)
      assert(DateRangeSupport.daysBetweenOpt("").isEmpty)
    }

    it("must validate chronological order and optional min-days / max-days constraints") {
      // Blank is valid (required validation is separate)
      assert(DateRangeSupport.isValidDateRange("", "", ""))

      // Valid range (start <= end)
      assert(DateRangeSupport.isValidDateRange("2026-07-01/2026-07-14", "", ""))
      assert(DateRangeSupport.isValidDateRange("2026-07-01/2026-07-01", "", ""))

      // Invalid when end < start or partial/malformed
      assert(! DateRangeSupport.isValidDateRange("2026-07-14/2026-07-01", "", ""))
      assert(! DateRangeSupport.isValidDateRange("2026-07-01/", "", ""))
      assert(! DateRangeSupport.isValidDateRange("/2026-07-14", "", ""))
      assert(! DateRangeSupport.isValidDateRange("not-a-date/2026-07-14", "", ""))

      // min-days and max-days
      assert(DateRangeSupport.isValidDateRange("2026-07-01/2026-07-14", "7", "14"))
      assert(! DateRangeSupport.isValidDateRange("2026-07-01/2026-07-05", "7", "14"))
      assert(! DateRangeSupport.isValidDateRange("2026-07-01/2026-07-20", "7", "14"))
    }

    it("must format readonly date ranges using the specified date format") {
      assert(DateRangeSupport.formatReadonlyModeDateRange("", "[M]/[D]/[Y]") == "")
      assert(
        DateRangeSupport.formatReadonlyModeDateRange("2026-07-01/2026-07-14", "[M]/[D]/[Y]") ==
          "7/1/2026 – 7/14/2026"
      )
      assert(
        DateRangeSupport.formatReadonlyModeDateRange("2026-07-01/2026-07-14", "[D01].[M01].[Y]") ==
          "01.07.2026 – 14.07.2026"
      )
    }
  }

  describe("fr:date-range XBL control and XPath helper functions") {

    it("must synchronize start/end dates, validate chronological and span constraints, and evaluate XPath helper functions") {

      val xhtml =
        <xh:html
          xmlns:xf="http://www.w3.org/2002/xforms"
          xmlns:xh="http://www.w3.org/1999/xhtml"
          xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <xh:head>
            <xf:model
              xxf:expose-xpath-types="true"
              xxf:function-library="org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary">
              <xf:instance id="fr-form-instance">
                <form>
                  <trip-dates/>
                  <calc-start/>
                  <calc-end/>
                  <calc-days/>
                </form>
              </xf:instance>
              <xf:bind ref="instance('fr-form-instance')">
                <xf:bind id="trip-dates-bind" ref="trip-dates" type="fr:date-range"/>
                <xf:bind ref="calc-start" calculate="string(fr:date-range-start(../trip-dates))"/>
                <xf:bind ref="calc-end"   calculate="string(fr:date-range-end(../trip-dates))"/>
                <xf:bind ref="calc-days"  calculate="string(fr:date-range-days(../trip-dates))"/>
              </xf:bind>
            </xf:model>
          </xh:head>
          <xh:body>
            <fr:date-range id="trip-dates-control" bind="trip-dates-bind" min-days="3" max-days="30">
              <xf:label>Trip Dates</xf:label>
            </fr:date-range>
            <xf:output id="calc-start-control" ref="calc-start"/>
            <xf:output id="calc-end-control"   ref="calc-end"/>
            <xf:output id="calc-days-control"  ref="calc-days"/>
          </xh:body>
        </xh:html>.toDocument

      withTestExternalContext { _ =>
        withXFormsDocument(xhtml) { doc =>
          withActionAndDoc(doc) {

            val dateRangeControl = doc.getControlByEffectiveId("trip-dates-control").asInstanceOf[XFormsValueComponentControl]
            val startDateControl = doc.getControlByEffectiveId("trip-dates-control≡start-date").asInstanceOf[XFormsValueControl]
            val endDateControl   = doc.getControlByEffectiveId("trip-dates-control≡end-date").asInstanceOf[XFormsValueControl]
            val calcStartControl = doc.getControlByEffectiveId("calc-start-control").asInstanceOf[XFormsValueControl]
            val calcEndControl   = doc.getControlByEffectiveId("calc-end-control").asInstanceOf[XFormsValueControl]
            val calcDaysControl  = doc.getControlByEffectiveId("calc-days-control").asInstanceOf[XFormsValueControl]

            def dateExternalValueJson(isoDateOpt: Option[IsoDate], unrecognized: String = ""): String = {
              import io.circe.generic.auto.*
              import io.circe.syntax.*
              DateExternalValue(
                isoOrUnrecognizedValue = isoDateOpt.toLeft(unrecognized),
                format                 = IsoDate.parseFormat("[M]/[D]/[Y]"),
                excludedDates          = Nil,
                weekStart              = None
              ).asJson.noSpaces
            }

            // Initially empty with xxf:expose-xpath-types="true"
            assert(dateRangeControl.getValue(EventCollector.Throw) == "")
            assert(dateRangeControl.isValid)
            assert(startDateControl.getValue(EventCollector.Throw) == "")
            assert(endDateControl.getValue(EventCollector.Throw)   == "")
            assert(calcStartControl.getValue(EventCollector.Throw) == "")
            assert(calcEndControl.getValue(EventCollector.Throw)   == "")
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "")

            // Setting start-date and end-date
            setControlValueWithEventSearchNested("trip-dates-control≡start-date", dateExternalValueJson(Some(IsoDate(2026, 7, 1))))
            setControlValueWithEventSearchNested("trip-dates-control≡end-date",   dateExternalValueJson(Some(IsoDate(2026, 7, 14))))
            assert(dateRangeControl.getValue(EventCollector.Throw) == "2026-07-01/2026-07-14")
            assert(dateRangeControl.isValid)
            assert(startDateControl.getValue(EventCollector.Throw) == "2026-07-01")
            assert(endDateControl.getValue(EventCollector.Throw)   == "2026-07-14")
            assert(calcStartControl.getValue(EventCollector.Throw) == "2026-07-01")
            assert(calcEndControl.getValue(EventCollector.Throw)   == "2026-07-14")
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "13")

            // Updating inner end-date to a valid date updates bound node and dependent formulas immediately
            setControlValueWithEventSearchNested("trip-dates-control≡end-date", dateExternalValueJson(Some(IsoDate(2026, 7, 10))))
            assert(endDateControl.getValue(EventCollector.Throw)   == "2026-07-10")
            assert(dateRangeControl.getValue(EventCollector.Throw) == "2026-07-01/2026-07-10")
            assert(dateRangeControl.isValid)
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "9")

            // Setting end-date earlier than start-date marks both end-date and outer date-range invalid
            setControlValueWithEventSearchNested("trip-dates-control≡end-date", dateExternalValueJson(Some(IsoDate(2026, 6, 25))))
            assert(! endDateControl.isValid)
            assert(dateRangeControl.getValue(EventCollector.Throw) == "2026-07-01/2026-06-25")
            assert(! dateRangeControl.isValid)
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "-6")

            // Violating min-days (3) marks outer date-range invalid while inner end-date stays valid
            setControlValueWithEventSearchNested("trip-dates-control≡end-date", dateExternalValueJson(Some(IsoDate(2026, 7, 2))))
            assert(endDateControl.isValid)
            assert(dateRangeControl.getValue(EventCollector.Throw) == "2026-07-01/2026-07-02")
            assert(! dateRangeControl.isValid)
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "1")

            // Violating max-days (30) marks outer date-range invalid
            setControlValueWithEventSearchNested("trip-dates-control≡end-date", dateExternalValueJson(Some(IsoDate(2026, 8, 15))))
            assert(endDateControl.isValid)
            assert(dateRangeControl.getValue(EventCollector.Throw) == "2026-07-01/2026-08-15")
            assert(! dateRangeControl.isValid)
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "45")

            // Clearing both dates clears the bound node and is valid
            setControlValueWithEventSearchNested("trip-dates-control≡start-date", dateExternalValueJson(None))
            setControlValueWithEventSearchNested("trip-dates-control≡end-date",   dateExternalValueJson(None))
            assert(dateRangeControl.getValue(EventCollector.Throw) == "")
            assert(dateRangeControl.isValid)
            assert(calcStartControl.getValue(EventCollector.Throw) == "")
            assert(calcEndControl.getValue(EventCollector.Throw)   == "")
            assert(calcDaysControl.getValue(EventCollector.Throw)  == "")
          }
        }
      }
    }
  }
}
