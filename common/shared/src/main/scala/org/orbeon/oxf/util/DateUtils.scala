/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.time.chrono.IsoChronology
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, ResolverStyle}
import java.time.temporal.ChronoField
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import java.util.Locale

import scala.util.Try


object DateUtils {

  def formatIsoDateTimeUtc(instant: Long): String =
    DateTime.format(Instant.ofEpochMilli(instant))

  def formatRfc1123DateTimeGmt(instant: Long): String =
    formatRfc1123DateTimeGmt(Instant.ofEpochMilli(instant))

  def formatRfc1123DateTimeGmt(instant: Instant): String =
    RFC1123Date.format(instant)

  // Parse an RFC 1123 dateTime
  // NOTE: See https://tools.ietf.org/html/rfc2616#page-20
  // Q: Should we still parse format 2 and 3? That's a pain.
  def parseRFC1123(date: String): Long =
    Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(date)).toEpochMilli

  def tryParseRFC1123(date: String): Option[Long] =
    Try(parseRFC1123(date)).toOption

  // Default timezone offset in minutes
  // This is obtained once at the time the current object initializes. This searches `user.timezone`, the JDK timezone,
  // and then UTC in order. This is ony used for setting an absolute point in time before subtracting.
  val DefaultOffsetMinutes: Int =
    OffsetDateTime.now.getOffset.getTotalSeconds / 60

  // Do our own since we want `.000` in all cases
  private val IsoLocalTimeWithMillis =
    new DateTimeFormatterBuilder()
      .appendValue(ChronoField.HOUR_OF_DAY, 2)
      .appendLiteral(':')
      .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
      .optionalStart
      .appendLiteral(':')
      .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
      .optionalStart
      .appendFraction(ChronoField.MILLI_OF_SECOND, 3, 3, true) // this is the difference
      .toFormatter
      .withResolverStyle(ResolverStyle.STRICT)
      .withChronology(IsoChronology.INSTANCE)

  private val IsoLocalDateTimeWithMillis =
    new DateTimeFormatterBuilder()
      .parseCaseInsensitive
      .append(DateTimeFormatter.ISO_LOCAL_DATE)
      .appendLiteral('T')
      .append(IsoLocalTimeWithMillis)
      .toFormatter
      .withResolverStyle(ResolverStyle.STRICT)
      .withChronology(IsoChronology.INSTANCE)

  private val IsoDateTimeWIthMillis =
    new DateTimeFormatterBuilder()
      .append(IsoLocalDateTimeWithMillis)
      .optionalStart()
      .appendOffsetId()
      .optionalStart()
      .appendLiteral('[')
      .parseCaseSensitive()
      .appendZoneRegionId()
      .appendLiteral(']')
      .toFormatter
      .withResolverStyle(ResolverStyle.STRICT)
      .withChronology(IsoChronology.INSTANCE)

  // ISO 8601 `xs:dateTime` formats with timezone, which we should always use when serializing a date
  private[util] val DateTime = IsoDateTimeWIthMillis.withZone(ZoneOffset.UTC)

  // RFC 1123 format
  // RFC 2616 says: "The first format is preferred as an Internet standard and represents a fixed-length subset of
  // that defined by RFC 1123 [8] (an update to RFC 822 [9]). The second format is in common use, but is based on the
  // obsolete RFC 850 [12] date format and lacks a four-digit year. HTTP/1.1 clients and servers that parse the date
  // value MUST accept all three formats (for compatibility with HTTP/1.0), though they MUST only generate the RFC
  // 1123 format for representing HTTP-date values in header fields." Also: "All HTTP date/time stamps MUST be
  // represented in Greenwich Mean Time (GMT), without exception. For the purposes of HTTP, GMT is exactly equal to
  // UTC (Coordinated Universal Time)."

  // NOTE: `Locale.US` doesn't appear to work
  private[util] val RFC1123Date = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'",  Locale.ENGLISH).withZone(ZoneOffset.UTC)
//  private val RFC1123Date2      = DateTimeFormatter.ofPattern("EEEEEE, dd-MMM-yy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)
//  private val RFC1123Date3      = DateTimeFormatter.ofPattern("EEE MMMM d HH:mm:ss yyyy",         Locale.US).withZone(ZoneOffset.UTC)
}
