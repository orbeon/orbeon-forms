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

import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.{AppForm, SearchVersion}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}

import scala.util.Try


object OpenSelect1SearchSupport {

  private val logger = LoggerFactory.createLogger(getClass)

  private implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

  def distinctValuesForControl(
    app            : String,
    form           : String,
    formVersion    : String,
    controlPathFull: String,
    controlPath400 : String
  ): Seq[String] = {
    val appOpt  = Option(app).flatMap(_.trimAllToOpt)
    val formOpt = Option(form).flatMap(_.trimAllToOpt)

    val candidatePaths =
      List(Option(controlPathFull), Option(controlPath400))
        .flatten
        .flatMap(_.trimAllToOpt)
        .distinct

    (appOpt, formOpt) match {
      case (Some(a), Some(f)) if candidatePaths.nonEmpty && ! (a == "orbeon" && f == "builder") =>
        Try {
          implicit val indentedLogger: IndentedLogger = new IndentedLogger(logger)

          val searchVersion =
            Option(formVersion)
              .flatMap(_.trimAllToOpt)
              .flatMap(v => Try(v.toInt).toOption)
              .filter(_ > 0)
              .map(SearchVersion.Specific.apply)
              .getOrElse(SearchVersion.Unspecified)

          val result = PersistenceApi.distinctValues(
            appForm       = AppForm(a, f),
            searchVersion = searchVersion,
            controlPaths  = candidatePaths
          )

          val rawValues = result.controls.flatMap(_.distinctValues).flatMap(_.trimAllToOpt)

          // Case-insensitive deduplication preserving first occurrence, then sort alphabetically
          val seenLower = scala.collection.mutable.HashSet.empty[String]
          rawValues
            .filter(v => seenLower.add(v.toLowerCase))
            .sortBy(_.toLowerCase)
        }.getOrElse(Nil)
      case _ =>
        Nil
    }
  }
}

object OpenSelect1SearchSupportJava {

  //@XPathFunction
  def distinctValuesForControl(
    app            : String,
    form           : String,
    formVersion    : String,
    controlPathFull: String,
    controlPath400 : String
  ): Array[String] =
    OpenSelect1SearchSupport
      .distinctValuesForControl(app, form, formVersion, controlPathFull, controlPath400)
      .toArray
}
