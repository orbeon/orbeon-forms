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
package org.orbeon.xbl

import cats.syntax.option.*
import org.orbeon.date.{IsoDate, IsoTime}
import org.orbeon.dom.QName
import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.saxon.om

import scala.util.{Failure, Success}


object DateSupportJava {

  import io.circe.generic.auto.*
  import io.circe.parser
  import io.circe.syntax.*

  private val ExcludedDatesQName = QName("excluded-dates")

  //@XPathFunction
  def serializeExternalValueJava(
    binding   : om.Item,
    format    : String,
    weekStart : String
  ): String =
    DateExternalValue(
      isoOrUnrecognizedValue =
        DateUtils
          .tryParseISOLocalDateComponents(binding.getStringValue)
          .map((IsoDate.apply _).tupled)
          .toOption
          .toLeft(binding.getStringValue),
      format                 = IsoDate.parseFormat(format),
      excludedDates          = InstanceData.findCustomMip(binding, ExcludedDatesQName).map(_.splitTo[List]()).getOrElse(Nil),
      weekStart              = weekStart.trimAllToOpt.flatMap(v => if (v == "sunday") 0.some else if (v == "monday") 1.some else None)
    ).asJson.noSpaces

  //@XPathFunction
  def deserializeExternalValueJava(externalValue: String): String =
    parser.decode[DateExternalValue](externalValue)
      .fold(Failure.apply, Success.apply)
      .map(_.isoOrUnrecognizedValue.fold(_.toIsoString, identity))
      .getOrElse("")

  //@XPathFunction
  def formatReadonlyModeDate(
    binding : om.Item,
    format  : String
  ): String =
    IsoDate.parseIsoDate(binding.getStringValue)
      .map(IsoDate.formatDate(_, IsoDate.parseFormat(format)))
      .getOrElse(binding.getStringValue)

  //@XPathFunction
  def generatePlaceholder(formatString: String, hmsEn: String, hms: String): String =
    IsoDate.parseFormat(formatString).generatePlaceholderString(hmsEn, hms)
}

object TimeSupportJava {

  import io.circe.generic.auto.*
  import io.circe.parser
  import io.circe.syntax.*

  //@XPathFunction
  def serializeExternalValueJava(
    binding : om.Item,
    format  : String
  ): String =
    TimeExternalValue(
      isoOrUnrecognizedValue = IsoTime.findMagicTimeAsIsoTime(binding.getStringValue).toLeft(binding.getStringValue),
      format                 = IsoTime.parseFormat(format) // Q: could parse earlier/cache?
    ).asJson.noSpaces

  //@XPathFunction
  def deserializeExternalValueJava(externalValue: String): String =
    parser.decode[TimeExternalValue](externalValue)
      .fold(Failure.apply, Success.apply)
      .map(_.isoOrUnrecognizedValue.fold(_.toIsoString, identity))
      .getOrElse("")

  //@XPathFunction
  def formatReadonlyModeTime(
    binding : om.Item,
    format  : String
  ): String =
    IsoTime.findMagicTimeAsIsoTime(binding.getStringValue)
      .map(IsoTime.formatTime(_, IsoTime.parseFormat(format)))
      .getOrElse(binding.getStringValue)

  //@XPathFunction
  def generatePlaceholder(formatString: String, hmsEn: String, hms: String): String =
    IsoTime.parseFormat(formatString).generatePlaceholder(hmsEn, hms)
}
