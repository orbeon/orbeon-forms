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

import org.orbeon.oxf.fr.datamigration.{MigrationSupport, PathElem}
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.{AppForm, DataFormatVersion, FormRunner, FormRunnerPersistence, SearchVersion}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.saxon.om.NodeInfo

import scala.util.Try


object OpenSelect1SearchSupport {

  private val logger = LoggerFactory.createLogger(getClass)

  private implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

  def adjustedControlPath(
    controlPathEdge  : String,
    dataFormatVersion: DataFormatVersion,
    metadataElemOpt  : Option[NodeInfo]
  ): String = {
    val pathElems = controlPathEdge.splitTo[List]("/").map(PathElem.apply)
    val adjustedElems =
      dataFormatVersion match {
        case DataFormatVersion.Edge =>
          pathElems
        case _ =>
          val (_, migrationOpsToApply) =
            MigrationSupport.findMigrationOps(
              srcVersion = DataFormatVersion.Edge,
              dstVersion = dataFormatVersion
            )

          val pathMigrationFunctions =
            for {
              metadataElem <- metadataElemOpt.toList
              ops          <- migrationOpsToApply
              json         <- MigrationSupport.findMigrationForVersion(metadataElem, ops.version)
            } yield
              ops.adjustPathTo40(ops.decodeMigrationSetFromJson(json), _)

          (pathMigrationFunctions.iterator flatMap (_.apply(pathElems))).nextOption() getOrElse pathElems
      }

    adjustedElems.map(_.value).mkString("/")
  }

  def distinctValuesForControl(
    app            : String,
    form           : String,
    formVersion    : String,
    controlPathEdge: String
  ): Seq[String] = {
    val appOpt  = Option(app).flatMap(_.trimAllToOpt)
    val formOpt = Option(form).flatMap(_.trimAllToOpt)
    val pathOpt = Option(controlPathEdge).flatMap(_.trimAllToOpt)

    (appOpt, formOpt, pathOpt) match {
      case (Some(a), Some(f), Some(edgePath)) if ! (a == "orbeon" && f == "builder") =>
        Try {
          implicit val indentedLogger: IndentedLogger = new IndentedLogger(logger)
          implicit val propertySet   : PropertySet    = CoreCrossPlatformSupport.properties

          val appForm = AppForm(a, f)
          val databaseDataFormatVersion =
            FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm)

          val indexedPath =
            adjustedControlPath(
              controlPathEdge   = edgePath,
              dataFormatVersion = databaseDataFormatVersion,
              metadataElemOpt   = FormRunner.metadataInstance.map(_.rootElement)
            )

          val searchVersion =
            Option(formVersion)
              .flatMap(_.trimAllToOpt)
              .flatMap(v => Try(v.toInt).toOption)
              .filter(_ > 0)
              .map(SearchVersion.Specific.apply)
              .getOrElse(SearchVersion.Unspecified)

          val result = PersistenceApi.distinctValues(
            appForm       = appForm,
            searchVersion = searchVersion,
            controlPaths  = List(indexedPath)
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
    controlPathEdge: String
  ): Array[String] =
    OpenSelect1SearchSupport
      .distinctValuesForControl(app, form, formVersion, controlPathEdge)
      .toArray
}
