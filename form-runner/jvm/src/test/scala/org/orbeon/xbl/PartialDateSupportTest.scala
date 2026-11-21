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

import org.orbeon.date.{DateFormat, DateFormatComponent, IsoDate}
import org.scalatest.funspec.AnyFunSpecLike


class PartialDateSupportTest extends AnyFunSpecLike {

  private val UsFormat  = DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = true)
  private val EuFormat  = DateFormat(DateFormatComponent.Day,   ".", isPadDayMonthDigits = true)
  private val IsoFormat = DateFormat(DateFormatComponent.Year,  "-", isPadDayMonthDigits = true)

  describe("PartialDateSupport EDTF parsing and normalization") {

    it("must normalize exact, month-year, year, decade, qualifier, and interval inputs") {
      def norm(raw: String, fmt: DateFormat = UsFormat): String =
        PartialDateSupport.normalizeUserInput(
          raw             = raw,
          dateFormat      = fmt,
          precisions      = EdtfPrecision.AllPrecisions,
          allowQualifiers = true,
          allowIntervals  = true
        )

      // Exact dates
      assert(norm("07/31/2026", UsFormat)  == "2026-07-31")
      assert(norm("31.07.2026", EuFormat)  == "2026-07-31")
      assert(norm("2026-07-31", UsFormat)  == "2026-07-31")
      assert(norm("2024-02-29", IsoFormat) == "2024-02-29")

      // Year + month
      assert(norm("2021-11")       == "2021-11")
      assert(norm("11/2021")       == "2021-11")
      assert(norm("7/2021")        == "2021-07")
      assert(norm("11.2021")       == "2021-11")
      assert(norm("Nov 2021")      == "2021-11")
      assert(norm("November 2021") == "2021-11")
      assert(norm("2021 Nov")      == "2021-11")

      // Year only
      assert(norm("1961") == "1961")

      // Unspecified digits & decades
      assert(norm("192x")       == "192X")
      assert(norm("192X")       == "192X")
      assert(norm("1920s")      == "192X")
      assert(norm("1920's")     == "192X")
      assert(norm("19xx")       == "19XX")
      assert(norm("2020-08-2x") == "2020-08-2X")
      assert(norm("2020-08-xx") == "2020-08-XX")
      assert(norm("2020-xx")    == "2020-XX")

      // Qualifiers
      assert(norm("1899?")        == "1899?")
      assert(norm("1972~")        == "1972~")
      assert(norm("1972%")        == "1972%")
      assert(norm("c. 1899")      == "1899~")
      assert(norm("ca. 1899")     == "1899~")
      assert(norm("circa 1899")   == "1899~")
      assert(norm("~1899")        == "1899~")
      assert(norm("c. 1899?")     == "1899%")
      assert(norm("1950s?")       == "195X?")
      assert(norm("c. Nov 2021")  == "2021-11~")

      // Intervals
      assert(norm("1926/1937")         == "1926/1937")
      assert(norm("1926–1937")         == "1926/1937")
      assert(norm("1894-1896")         == "1894/1896")
      assert(norm("2020-05/2020-08")   == "2020-05/2020-08")
      assert(norm("1920s–1930s")       == "192X/193X")
      assert(norm("c. 1894–1896?")     == "1894~/1896?")

      // Invalid values pass through trimmed unchanged
      assert(norm("2023-02-29") == "2023-02-29")
      assert(norm("2026-02-3X") == "2026-02-3X")
      assert(norm("1937/1926")  == "1937/1926")
      assert(norm("not-a-date") == "not-a-date")
      assert(norm("   ")        == "")
    }

    it("must validate calendar bounds, precisions, qualifiers, intervals, and year bounds") {
      // Blank is always valid
      assert(PartialDateSupport.isValidPartialDate("", "day month year", "false", "false", "", ""))

      // Default settings: day month year, no qualifiers, no intervals
      assert(PartialDateSupport.isValidPartialDate("2026-07-31", "day month year", "false", "false", "", ""))
      assert(PartialDateSupport.isValidPartialDate("2021-11",    "day month year", "false", "false", "", ""))
      assert(PartialDateSupport.isValidPartialDate("1961",       "day month year", "false", "false", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("192X",      "day month year", "false", "false", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("1899?",     "day month year", "false", "false", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("1926/1937", "day month year", "false", "false", "", ""))

      // Invalid calendar dates
      assert(! PartialDateSupport.isValidPartialDate("2023-02-29", "day month year", "false", "false", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("2026-13-01", "day month year", "false", "false", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("2026-02-3X", "day month year decade", "true", "true", "", ""))
      assert(PartialDateSupport.isValidPartialDate("2026-08-3X",   "day month year decade", "true", "true", "", ""))

      // Precision gating
      assert(PartialDateSupport.isValidPartialDate("2021-11", "month year", "false", "false", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("2021-11-05", "month year", "false", "false", "", ""))
      assert(PartialDateSupport.isValidPartialDate("192X", "year decade", "false", "false", "", ""))

      // Qualifiers & intervals enabled
      assert(PartialDateSupport.isValidPartialDate("1899?",     "year", "true", "false", "", ""))
      assert(PartialDateSupport.isValidPartialDate("1926/1937", "year", "false", "true", "", ""))
      assert(! PartialDateSupport.isValidPartialDate("1937/1926", "year", "false", "true", "", ""))
      assert(PartialDateSupport.isValidPartialDate("192X/1925", "year decade", "false", "true", "", ""))

      // Min/max year constraints
      assert(PartialDateSupport.isValidPartialDate("1961",      "year", "false", "false", "1850", "2026"))
      assert(! PartialDateSupport.isValidPartialDate("1840",    "year", "false", "false", "1850", "2026"))
      assert(! PartialDateSupport.isValidPartialDate("2030",    "year", "false", "false", "1850", "2026"))
      assert(! PartialDateSupport.isValidPartialDate("184X",    "year decade", "false", "false", "1850", "2026"))
      assert(PartialDateSupport.isValidPartialDate("185X",      "year decade", "false", "false", "1850", "2026"))
      assert(! PartialDateSupport.isValidPartialDate("1900/2030", "year", "false", "true", "1850", "2026"))
    }

    it("must compute earliestIsoDateOpt, latestIsoDateOpt, and exactYearOpt") {
      assert(PartialDateSupport.earliestIsoDateOpt("2026-07-31").contains(IsoDate(2026, 7, 31)))
      assert(PartialDateSupport.latestIsoDateOpt("2026-07-31").contains(IsoDate(2026, 7, 31)))
      assert(PartialDateSupport.exactYearOpt("2026-07-31").contains(2026))

      assert(PartialDateSupport.earliestIsoDateOpt("2024-02").contains(IsoDate(2024, 2, 1)))
      assert(PartialDateSupport.latestIsoDateOpt("2024-02").contains(IsoDate(2024, 2, 29)))
      assert(PartialDateSupport.exactYearOpt("2024-02").contains(2024))

      assert(PartialDateSupport.earliestIsoDateOpt("1961").contains(IsoDate(1961, 1, 1)))
      assert(PartialDateSupport.latestIsoDateOpt("1961").contains(IsoDate(1961, 12, 31)))
      assert(PartialDateSupport.exactYearOpt("1961").contains(1961))

      assert(PartialDateSupport.earliestIsoDateOpt("192X").contains(IsoDate(1920, 1, 1)))
      assert(PartialDateSupport.latestIsoDateOpt("192X").contains(IsoDate(1929, 12, 31)))
      assert(PartialDateSupport.exactYearOpt("192X").isEmpty)

      assert(PartialDateSupport.earliestIsoDateOpt("2020-08-2X").contains(IsoDate(2020, 8, 20)))
      assert(PartialDateSupport.latestIsoDateOpt("2020-08-2X").contains(IsoDate(2020, 8, 29)))
      assert(PartialDateSupport.exactYearOpt("2020-08-2X").contains(2020))

      assert(PartialDateSupport.earliestIsoDateOpt("1926/1937").contains(IsoDate(1926, 1, 1)))
      assert(PartialDateSupport.latestIsoDateOpt("1926/1937").contains(IsoDate(1937, 12, 31)))
      assert(PartialDateSupport.exactYearOpt("1926/1937").isEmpty)
    }

    it("must format edit and readonly display strings according to date format and locale") {
      // Edit format
      assert(PartialDateSupport.formatForEdit("2026-07-31", UsFormat)  == "07/31/2026")
      assert(PartialDateSupport.formatForEdit("2021-11",    UsFormat)  == "11/2021")
      assert(PartialDateSupport.formatForEdit("2021-11",    EuFormat)  == "11.2021")
      assert(PartialDateSupport.formatForEdit("2021-11",    IsoFormat) == "2021-11")
      assert(PartialDateSupport.formatForEdit("1961",       UsFormat)  == "1961")
      assert(PartialDateSupport.formatForEdit("192X",       UsFormat)  == "192X")
      assert(PartialDateSupport.formatForEdit("1899?",      UsFormat)  == "1899?")
      assert(PartialDateSupport.formatForEdit("1926/1937",  UsFormat)  == "1926/1937")

      // Readonly format (English)
      assert(PartialDateSupport.formatReadonly("2026-07-31", "[M01]/[D01]/[Y]", "c.", "{decade}s") == "07/31/2026")
      assert(PartialDateSupport.formatReadonly("2021-11",    "[M01]/[D01]/[Y]", "c.", "{decade}s") == "11/2021")
      assert(PartialDateSupport.formatReadonly("2021-11",    "[D01].[M01].[Y]", "c.", "{decade}s") == "11.2021")
      assert(PartialDateSupport.formatReadonly("2021-11",    "[Y]-[M01]-[D01]", "c.", "{decade}s") == "2021-11")
      assert(PartialDateSupport.formatReadonly("192X",       "[M01]/[D01]/[Y]", "c.", "{decade}s") == "1920s")
      assert(PartialDateSupport.formatReadonly("1899~",      "[M01]/[D01]/[Y]", "c.", "{decade}s") == "c. 1899")
      assert(PartialDateSupport.formatReadonly("1899?",      "[M01]/[D01]/[Y]", "c.", "{decade}s") == "1899?")
      assert(PartialDateSupport.formatReadonly("1899%",      "[M01]/[D01]/[Y]", "c.", "{decade}s") == "c. 1899?")
      assert(PartialDateSupport.formatReadonly("1926/1937",  "[M01]/[D01]/[Y]", "c.", "{decade}s") == "1926 – 1937")

      // Readonly format (French)
      assert(PartialDateSupport.formatReadonly("192X",  "[D01]/[M01]/[Y]", "v.", "années {decade}") == "années 1920")
      assert(PartialDateSupport.formatReadonly("1899~", "[D01]/[M01]/[Y]", "v.", "années {decade}") == "v. 1899")
    }
  }
}
