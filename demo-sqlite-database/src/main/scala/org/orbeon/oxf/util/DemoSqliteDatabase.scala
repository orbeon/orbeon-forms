/**
 * Copyright (C) 2024 Orbeon, Inc.
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

import org.apache.commons.io.FilenameUtils
import org.log4s.Logger
import org.orbeon.connection.StreamedContent
import org.orbeon.oxf.fr.FormRunnerPersistence
import org.orbeon.oxf.fr.persistence.db.{Connect, DatasourceDescriptor}
import org.orbeon.oxf.fr.persistence.relational.{Provider, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.test.{PipelineSupport, TestHttpClient, WithResourceManagerSupport}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.state.XFormsStateManager

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

object DemoSqliteDatabase {
  object ResourceManagerSupportInitializer extends WithResourceManagerSupport {
    override lazy val logger: Logger        = LoggerFactory.createLogger(ResourceManagerSupportInitializer.getClass)
    override lazy val propertiesUrl: String = "oxf:/properties.xml"
  }

  def main(args: Array[String]): Unit = {
    if (args.length == 2) {
      // ResourceManager initialization
      ResourceManagerSupportInitializer

      importFiles(Paths.get(args(0)), Paths.get(args(1)))
    } else {
      println("Usage: DemoSqliteDatabase <data-files> <sqlite-file>")
    }
  }

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(DemoSqliteDatabase.getClass), true)

  def importFiles(dataFiles: Path, sqliteFile: Path): Unit = {
    // Delete any pre-existing SQLite file and make sure the target directory exists
    Files.deleteIfExists(sqliteFile)
    Files.createDirectories(sqliteFile.getParent)

    // TODO: rename this method?
    PipelineSupport.withTestExternalContext(XFormsStateManager.sessionCreated, XFormsStateManager.sessionDestroyed) { _ =>

      val datasourceDescriptor = DatasourceDescriptor.sqliteDatasourceDescriptor(sqliteFile)

      Connect.withNewDatabase(Provider.SQLite, datasourceDescriptor, dropDatabase = false) { connection =>

        Connect.createTables(Provider.SQLite, connection)

        val unsortedFilesToImport = Files.walk(dataFiles)
          .filter(_.toFile.isFile)
          .filter { file =>
            val baseName  = FilenameUtils.getBaseName(file.toString)
            val extension = FilenameUtils.getExtension(file.toString)

            !(baseName.contains("copy") || extension == "DS_Store" || extension.endsWith("~"))
          }
          .iterator()
          .asScala
          .toSeq

        // Import form definitions first, then data files
        val (formFilesToImport, dataFilesToImport) = unsortedFilesToImport.partition(_.toString.endsWith(FormRunnerPersistence.FormXhtml))
        val sortedFilesToImport                    = formFilesToImport ++ dataFilesToImport

        for (filesToImport <- sortedFilesToImport) {
          importFile(dataFiles, filesToImport)
        }
      }
    }
  }

  def importFile(dataFiles: Path, fileToImport: Path): Unit = {
    val relativePath = fileToImport.toString.trimPrefixIfPresent(dataFiles.toString)
    val url          = FormRunnerPersistence.CRUDBasePath + relativePath

    val mediaType = FilenameUtils.getExtension(fileToImport.toString) match {
      case "jpg"           => "image/jpeg"
      case "xml" | "xhtml" => "application/xml"
      case _               => "application/octet-stream"
    }

    // Version 2 temporarily as on demo we have this and have a /new bug
    val formVersion = if (fileToImport.toString.contains("orbeon/controls")) 2 else 1

    val streamedContent = StreamedContent.fromBytes(Files.readAllBytes(fileToImport), Some(mediaType))

    val httpResponse = TestHttpClient.connect(
      url     = url,
      method  = HttpMethod.PUT,
      headers = Map(
        Headers.ContentType                 -> List(mediaType),
        Version.OrbeonFormDefinitionVersion -> List(formVersion.toString)
      ),
      content = Some(streamedContent)
    )._2

    assert(Set[Int](StatusCode.Created, StatusCode.NoContent).contains(httpResponse.statusCode))
  }
}
