/**
 * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.{util => ju}

import org.orbeon.oxf.fr.excel.{ExcelDateUtils, NumberToTextConverter}
import org.orbeon.oxf.fr.excel.ExcelDateUtils.FormatType
import org.orbeon.oxf.util.StringUtils._

import scala.util.Try

object FormRunnerImport {

  private val IsoDateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC)
  private val IsoDateFormatter     = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
  private val IsoTimeFormatter     = DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneOffset.UTC)

  //@XPathFunction
  def findOoxmlCellType(formatIndex: Int, formatString: String): String =
    if (formatString.toLowerCase == "general" || formatString.isEmpty)
      FormatType.Other.entryName
    else
      ExcelDateUtils.analyzeFormatType(formatIndex, formatString).entryName

  //@XPathFunction
  def convertDateTime(value: String, formatTypeString: String, use1904windowing: Boolean): String = {

    val dateOpt =
      Try(value.toDouble).toOption flatMap { double =>
        ExcelDateUtils.getJavaDate(
          date             = double,
          use1904windowing = use1904windowing,
          tz               = ju.TimeZone.getTimeZone("UTC"),
          locale           = ju.Locale.getDefault,
          roundSeconds     = true
        )
      }

    val formatType = FormatType.withNameInsensitive(formatTypeString)

    def formatter =
      formatType match {
        case FormatType.DateTime => IsoDateTimeFormatter
        case FormatType.Date     => IsoDateFormatter
        case FormatType.Time     => IsoTimeFormatter
        case FormatType.Other    => throw new IllegalArgumentException(formatTypeString)
      }

    def removeTrailingZIfPresent(s: String) =
      if (s.last == 'Z') s.init else s

    def removeTrailingMillisIfPresent(s: String) =
      formatType match {
        case FormatType.DateTime | FormatType.Time => s.trimSuffixIfPresent(".000")
        case _ => s
      }

    dateOpt                         map
      (_.getTime)                   map
      Instant.ofEpochMilli          map
      formatter.format              map
      removeTrailingZIfPresent      map
      removeTrailingMillisIfPresent orNull
  }

  // https://github.com/orbeon/orbeon-forms/issues/4452
  //@XPathFunction
  def convertNumber(value: String): String =
    Try(NumberToTextConverter.toText(value.toDouble)).toOption.getOrElse(value)
}
