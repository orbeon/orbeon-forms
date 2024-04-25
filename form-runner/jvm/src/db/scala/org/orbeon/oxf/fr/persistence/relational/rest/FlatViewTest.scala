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
package org.orbeon.oxf.fr.persistence.relational.rest

import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.AppForm
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Version.{Specific, Unspecified}
import org.orbeon.oxf.http.{HttpRanges, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.CollectionUtils.fromIteratorExt
import org.orbeon.oxf.util.StaticXPath.{orbeonDomToTinyTree, tinyTreeToOrbeonDom}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.xforms.XFormsCrossPlatformSupport.readTinyTreeFromUrl
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayInputStream
import java.net.URI
import java.sql.{Connection, ResultSet}

class FlatViewTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private implicit val Logger                   = new IndentedLogger(LoggerFactory.createLogger(classOf[FlatViewTest]), true)
  private implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

  private val UrlPrefix = "oxf:/org/orbeon/oxf/fr/"

  private def xmlBody(resourceFile: String): HttpCall.XML = {
    val formDefinition = readTinyTreeFromUrl(URI.create(UrlPrefix + resourceFile))
    val formDocument   = IOSupport.readOrbeonDom(tinyTreeToOrbeonDom(formDefinition.getRoot).serializeToString())
    HttpCall.XML(formDocument)
  }

  private def documentInfo(url: String, version: Int)(implicit ec: ExternalContext): DocumentInfo = {
    val formDefinitionByteArray = HttpCall.get(url, Specific(version))._3.get
    val formDefinitionDocument  = IOSupport.readOrbeonDom(new ByteArrayInputStream(formDefinitionByteArray))
    orbeonDomToTinyTree(formDefinitionDocument)
  }

  private def crudRequest(provider: Provider, appForm: AppForm, version: Int) = CrudRequest(
    provider        = provider,
    appForm         = appForm,
    version         = Some(version),
    filename        = None,
    dataPart        = None,
    lastModifiedOpt = None,
    username        = None,
    groupname       = None,
    flatView        = true,
    credentials     = None,
    workflowStage   = None,
    ranges          = HttpRanges()
  )

  describe("Flat views") {

    it("expose forms with no repetition") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          if (Provider.FlatViewSupportedProviders.contains(provider)) {
            putDataAndReadBackFromView(provider, connection)
          }
        }
      }
    }

    def putDataAndReadBackFromView(provider: Provider, connection: Connection)(implicit ec: ExternalContext): Unit = {
      val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
      HttpAssert.put(formURL, Unspecified, xmlBody("form-flat-views-no-repetition.xhtml"), StatusCode.Created)

      val dataURL1 = HttpCall.crudURLPrefix(provider) + "data/1/data.xml"
      HttpAssert.put(dataURL1, Specific(1), xmlBody("form-flat-views-no-repetition-data-1.xml"), StatusCode.Created)

      val dataURL2 = HttpCall.crudURLPrefix(provider) + "data/2/data.xml"
      HttpAssert.put(dataURL2, Specific(1), xmlBody("form-flat-views-no-repetition-data-2.xml"), StatusCode.Created)

      val appForm = AppForm(provider.entryName, HttpCall.DefaultFormName)
      val version = 1

      FlatView.createFlatViewForDocument(
        crudRequest(provider, appForm, version),
        version,
        connection,
        Some(documentInfo(formURL, version))
      )

      val viewName           = FlatView.viewName(appForm, version)
      val query              = s"SELECT * FROM $viewName"
      val controlColumnNames = Seq(
        "section_1_control_1",
        "section_1_control_2",
        "section_1_section_2_control_3",
        "section_1_section_2_control_4",
        "sectio_section_section_control",
        "sectio_section_section_contro1",
      )

      val viewRows = useAndClose(connection.createStatement.executeQuery(query)) { resultSet =>
        Iterator.iterateWhile(resultSet.next(), ViewRow(resultSet, controlColumnNames)).toList.sortBy(_.documentId)
      }

      assert(viewRows(0).documentId == "1")
      assert(viewRows(0).controls == Seq("a", "b", "c", "d", "e", "f"))

      assert(viewRows(1).documentId == "2")
      assert(viewRows(1).controls == Seq("g", "h", "i", "j", "k", "l"))

      FlatView.deleteViewIfExists(provider, connection, viewName)
    }
  }

  case class ViewRow(
    documentId      : String,
    created         : String,
    lastModifiedTime: String,
    lastModifiedBy  : String,
    controls        : Seq[String]
 )

  object ViewRow {
    def apply(resultSet: ResultSet, controlColumnNames: Seq[String]): ViewRow = {
      val documentId       = resultSet.getString("metadata_document_id")
      val created          = resultSet.getString("metadata_created")
      val lastModifiedTime = resultSet.getString("metadata_last_modified_time")
      val lastModifiedBy   = resultSet.getString("metadata_last_modified_by")
      val controls         = controlColumnNames.map(resultSet.getString)
      ViewRow(documentId, created, lastModifiedTime, lastModifiedBy, controls)
    }
  }
}
