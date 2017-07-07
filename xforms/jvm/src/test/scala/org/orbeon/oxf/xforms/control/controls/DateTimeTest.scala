/**
 * Copyright (C) 2010 Orbeon === Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License === or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful === but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import XFormsInputControl._
import java.util.{Calendar, GregorianCalendar}

class DateTimeTest extends AssertionsForJUnit {

  @Test def testTimeParsing() : Unit = {
    for (pmSuffix ← List("p.m.", "pm", "p")) {
      assert("15:34:56" === testParseTimeForNoscript("3:34:56 " + pmSuffix))
      assert("15:34:00" === testParseTimeForNoscript("3:34 " + pmSuffix))
      assert("12:00:00" === testParseTimeForNoscript("12 " + pmSuffix))
      assert("15:00:00" === testParseTimeForNoscript("3 " + pmSuffix))
      assert("12:00:00" === testParseTimeForNoscript("0 " + pmSuffix))
    }

    for (amSuffix ← List("a.m.", "am", "a")) {
      assert("03:34:56" === testParseTimeForNoscript("3:34:56 " + amSuffix))
      assert("03:34:00" === testParseTimeForNoscript("3:34 " + amSuffix))
      assert("12:00:00" === testParseTimeForNoscript("12 " + amSuffix))
      assert("03:00:00" === testParseTimeForNoscript("3 " + amSuffix))
      assert("00:00:00" === testParseTimeForNoscript("0 " + amSuffix))
    }
  }

  @Test def testDateParsing(): Unit = {

    val Separators = List(".", "-", "/", " ")

    val currentYear = (new GregorianCalendar).get(Calendar.YEAR).toString

    def swapFirstTwoIfNeeded(l: List[String], swap: Boolean) =
      if (swap)
        l.tail.head :: l.head :: l.tail.tail
      else
        l

    for (dayMonth ← List(true, false))
      for (sep ← Separators) {
        assert("2010-02-19" === testParseDateForNoscript(swapFirstTwoIfNeeded(List("2", "19", "2010"), dayMonth) mkString sep, dayMonth))
        assert("2010-02-19" === testParseDateForNoscript(swapFirstTwoIfNeeded(List("02", "19", "2010"), dayMonth) mkString sep, dayMonth))

        assert(currentYear + "-02-19" === testParseDateForNoscript(swapFirstTwoIfNeeded(List("2", "19"), dayMonth) mkString sep, dayMonth))
        assert(currentYear + "-02-19" === testParseDateForNoscript(swapFirstTwoIfNeeded(List("02", "19"), dayMonth) mkString sep, dayMonth))
      }

    // ISO formats
    for (dayMonth ← List(true, false))
      for (sep ← Separators) {
        assert("2010-02-19" === testParseDateForNoscript(List("2010", "02", "19-08:00") mkString sep, dayMonth))
        assert("2010-02-19" === testParseDateForNoscript(List("2010", "02", "19+08:00") mkString sep, dayMonth))
        assert("2010-02-19" === testParseDateForNoscript(List("2010", "02", "19Z") mkString sep, dayMonth))
        assert("2010-02-19" === testParseDateForNoscript(List("2010", "02", "19") mkString sep, dayMonth))
      }
  }
}
