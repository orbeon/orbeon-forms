package org.orbeon.saxon

import org.orbeon.saxon.number.{AbstractNumberer, Numberer_en}
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

class Numberer_enTest extends AssertionsForJUnit {

  private val N = new Numberer_en

  private def check(number: Long, words: String, ordinalWords: String): Unit = {
    assert(words === N.toWords(number))
    assert(ordinalWords === N.toOrdinalWords("", number, AbstractNumberer.LOWER_CASE))
  }

  @Test def testLowNumbers(): Unit = {
    check(0L, "", "")
    check(1L, "One", "first")
    check(2L, "Two", "second")
    check(3L, "Three", "third")
    check(4L, "Four", "fourth")
    check(5L, "Five", "fifth")
    check(6L, "Six", "sixth")
    check(7L, "Seven", "seventh")
    check(8L, "Eight", "eighth")
    check(9L, "Nine", "ninth")
    check(10L, "Ten", "tenth")
    check(11L, "Eleven", "eleventh")
    check(12L, "Twelve", "twelfth")
    check(13L, "Thirteen", "thirteenth")
    check(14L, "Fourteen", "fourteenth")
    check(15L, "Fifteen", "fifteenth")
    check(16L, "Sixteen", "sixteenth")
    check(17L, "Seventeen", "seventeenth")
    check(18L, "Eighteen", "eighteenth")
    check(19L, "Nineteen", "nineteenth")
  }

  @Test def testEvenTensAndPowersOfTen(): Unit = {
    check(20L, "Twenty", "twentieth")
    check(30L, "Thirty", "thirtieth")
    check(40L, "Forty", "fortieth")
    check(50L, "Fifty", "fiftieth")
    check(60L, "Sixty", "sixtieth")
    check(70L, "Seventy", "seventieth")
    check(80L, "Eighty", "eightieth")
    check(90L, "Ninety", "ninetieth")
    check(100L, "One Hundred", "one hundredth")
    check(1000L, "One Thousand", "one thousandth")
    check(1000000L, "One Million", "one millionth")
    check(1000000000L, "One Billion", "one billionth")
  }

  @Test def testComposites(): Unit = {
    check(21L, "Twenty One", "twenty-first")
    check(32L, "Thirty Two", "thirty-second")
    check(99L, "Ninety Nine", "ninety-ninth")
    check(101L, "One Hundred and One", "one hundred and first")
    check(110L, "One Hundred and Ten", "one hundred and tenth")
    check(115L, "One Hundred and Fifteen", "one hundred and fifteenth")
    check(342L, "Three Hundred and Forty Two", "three hundred and forty-second")
    check(999L, "Nine Hundred and Ninety Nine", "nine hundred and ninety-ninth")
  }

  @Test def testLargeComposites(): Unit = {
    check(1001L, "One Thousand and One", "one thousand and first")
    check(1020L, "One Thousand and Twenty", "one thousand and twentieth")
    check(1100L, "One Thousand One Hundred", "one thousand one hundredth")
    check(1234L, "One Thousand Two Hundred and Thirty Four", "one thousand two hundred and thirty-fourth")
    check(1000001L, "One Million and One", "one million and first")
    check(1000100L, "One Million One Hundred", "one million one hundredth")
    check(1000000001L, "One Billion and One", "one billion and first")
  }

  @Test def testMonthName(): Unit = {
    assert("Jan" === N.monthName(1, 1, 2))
    assert("January" === N.monthName(1, 1, 10))
    assert("Jan " === N.monthName(1, 4, 3))
  }

  @Test def testDayName(): Unit = {
    assert("Monday" === N.dayName(1, 1, 10))
    assert("Mon" === N.dayName(1, 1, 3))
    assert("Mo" === N.dayName(1, 2, 2))
    assert("M" === N.dayName(1, 1, 2))
    assert("Tu" === N.dayName(2, 1, 2))
  }
}

