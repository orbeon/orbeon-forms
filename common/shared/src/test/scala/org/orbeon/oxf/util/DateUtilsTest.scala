package org.orbeon.oxf.util

import cats.syntax.option.*
import org.orbeon.date.{IsoDate, IsoTime}
import org.scalatest.funspec.AnyFunSpec

import java.time.Instant


class DateUtilsTest extends AnyFunSpec {

  describe("RFC1123 date parsing") {
    for (value <- List("Tue, 29 May 2012 19:35:04 GMT")) // "Tuesday, 29-May-12 19:35:04 GMT", "Tue May 29 19:35:04 2012"
      it(s"must parse `$value` in all formats") {
        assert(1338320104000L == DateUtils.parseRFC1123(value))
      }
  }

  describe("Date formatting") {

    val data = List(
      ("1970-01-01T00:00:00.000Z"     , "DateTime",    DateUtils.DateTime   , 0L),
      ("Thu, 01 Jan 1970 00:00:00 GMT", "RFC1123Date", DateUtils.RFC1123Date, 0L),
      ("2012-05-29T19:35:04.123Z"     , "DateTime",    DateUtils.DateTime   , 1338320104123L),
      ("Tue, 29 May 2012 19:35:04 GMT", "RFC1123Date", DateUtils.RFC1123Date, 1338320104123L)
    )

    for ((expected, formatterName, formatter, instant) <- data)
      it(s"must format instant `$instant` using formatter `$formatterName`") {
        assert(expected == formatter.format(Instant.ofEpochMilli(instant)))
      }
  }

  describe("ISO local date parsing") {

    val data = List(
      "1970-01-01"       -> Some(IsoDate(1970, 1, 1)),
      "2012-05-29"       -> Some(IsoDate(2012, 5, 29)),
      "2012-05-29+01:00" -> Some(IsoDate(2012, 5, 29)),
      "2012-05-29+23:00" -> Some(IsoDate(2012, 5, 29)),
      "2012-05-29-23:00" -> Some(IsoDate(2012, 5, 29)),
      "2023-02-29"       -> None,
      "2023-13-01"       -> None,
      // This has different behavior on the JVM and JS. Bug in the JS version of `java.time`? But this is an unlikely
      // input, so for now comment it out.
      // "2012-05-29+25:00" -> None,
    )

    for ((value, expected) <- data)
      it(s"must parse `$value` with no explicit timezone") {
        assert(IsoDate.tryParseLocalIsoDate(value).toOption == expected)
      }
  }

  describe("ISO local time parsing") {

    val data = List(
      "17:44:34"           -> Some(IsoTime(17, 44, 34.some)),
      "17:44"              -> Some(IsoTime(17, 44, 0.some)),
      "17:"                -> None,
      "17"                 -> None,
      "17:44:34+01:00"     -> Some(IsoTime(17, 44, 34.some)),
      "17:44+01:00"        -> Some(IsoTime(17, 44, 0.some)),
      "17:44:34+23:00"     -> Some(IsoTime(17, 44, 34.some)),
      "17:44+23:00"        -> Some(IsoTime(17, 44, 0.some)),
      "17:44:34-23:00"     -> Some(IsoTime(17, 44, 34.some)),
      "17:44-23:00"        -> Some(IsoTime(17, 44, 0.some)),
      "17:44:34.123"       -> Some(IsoTime(17, 44, 34.some)),
      "17:44:34.123+01:00" -> Some(IsoTime(17, 44, 34.some)),
      "17:44:abc"          -> None,
      "17:44:34.abc"       -> None,
    )

    for ((value, expected) <- data)
      it(s"must parse `$value` with no explicit timezone") {
        assert(IsoTime.tryParseLocalIsoTime(value).toOption == expected)
      }
  }
}
