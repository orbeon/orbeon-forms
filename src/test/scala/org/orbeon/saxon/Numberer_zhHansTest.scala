package org.orbeon.saxon

import org.orbeon.saxon.number.{AbstractNumberer, Numberer_zhHans}
import org.junit.Test
import org.scalatestplus.junit.AssertionsForJUnit

/**
 * Tests for numberer class for Simplified Chinese (BCP 47: {@code zh-Hans}).
 */
class Numberer_zhHansTest extends AssertionsForJUnit {

  private val N = new Numberer_zhHans

  private def check(number: Long, words: String, ordinalWords: String): Unit = {
    assert(words === N.toWords(number))
    assert(ordinalWords === N.toOrdinalWords("", number, AbstractNumberer.LOWER_CASE))
  }

  @Test def testLowNumbers(): Unit = {
    check(0L, "零", "第零")
    check(1L, "一", "第一")
    check(2L, "二", "第二")
    check(3L, "三", "第三")
    check(4L, "四", "第四")
    check(5L, "五", "第五")
    check(6L, "六", "第六")
    check(7L, "七", "第七")
    check(8L, "八", "第八")
    check(9L, "九", "第九")
    check(10L, "十", "第十")
    check(11L, "十一", "第十一")
    check(12L, "十二", "第十二")
    check(19L, "十九", "第十九")
    check(20L, "二十", "第二十")
  }

  @Test def testCompositesWithinGroup(): Unit = {
    check(21L, "二十一", "第二十一")
    check(99L, "九十九", "第九十九")
    check(100L, "一百", "第一百")
    check(101L, "一百零一", "第一百零一")
    check(105L, "一百零五", "第一百零五")
    check(110L, "一百一十", "第一百一十")
    check(111L, "一百一十一", "第一百一十一")
    check(1000L, "一千", "第一千")
    check(1001L, "一千零一", "第一千零一")
    check(1010L, "一千零一十", "第一千零一十")
    check(1100L, "一千一百", "第一千一百")
    check(1111L, "一千一百一十一", "第一千一百一十一")
  }

  @Test def testLargeUnits(): Unit = {
    check(10000L, "一万", "第一万")
    check(10001L, "一万一", "第一万一")
    check(10010L, "一万十", "第一万十")
    check(12345L, "一万二千三百四十五", "第一万二千三百四十五")
    check(100000L, "十万", "第十万")
    check(1000000L, "一百万", "第一百万")
    check(1000001L, "一百万一", "第一百万一")
    check(100000000L, "一亿", "第一亿")
    check(100000001L, "一亿零一", "第一亿零一")
    check(100000010L, "一亿零十", "第一亿零十")
  }

  @Test def testNegativeNumbers(): Unit = {
    check(-12L, "负十二", "第负十二")
  }

  @Test def testMonthName(): Unit = {
    assert("一月" === N.monthName(1, 1, 2))
    assert("十一" === N.monthName(11, 1, 2))
    assert("一月 " === N.monthName(1, 3, 3))
  }

  @Test def testDayName(): Unit = {
    assert("星期一" === N.dayName(1, 3, 3))
    assert("周一" === N.dayName(1, 2, 2))
    assert("周" === N.dayName(1, 1, 2))
    assert("周一  " === N.dayName(1, 4, 2))
  }
}

