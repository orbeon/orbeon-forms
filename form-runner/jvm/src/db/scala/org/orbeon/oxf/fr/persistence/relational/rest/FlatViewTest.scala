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
import org.orbeon.oxf.fr.Version.Specific
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
import scala.util.Try


class FlatViewTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private object Private {

    implicit val Logger                  : IndentedLogger                = new IndentedLogger(LoggerFactory.createLogger(classOf[FlatViewTest]), true)
    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupport.type = CoreCrossPlatformSupport

    val UrlPrefix = "oxf:/org/orbeon/oxf/fr/"

    def xmlBody(resourceFile: String): HttpCall.XML = {
      val formDefinition = readTinyTreeFromUrl(URI.create(UrlPrefix + resourceFile))
      val formDocument   = IOSupport.readOrbeonDom(tinyTreeToOrbeonDom(formDefinition.getRoot).serializeToString())
      HttpCall.XML(formDocument)
    }

    def documentInfo(url: String, version: Int)(implicit ec: ExternalContext): DocumentInfo = {
      val formDefinitionByteArray = HttpCall.get(url, Specific(version))._3.get
      val formDefinitionDocument  = IOSupport.readOrbeonDom(new ByteArrayInputStream(formDefinitionByteArray))
      orbeonDomToTinyTree(formDefinitionDocument)
    }

    def crudRequest(provider: Provider, appForm: AppForm, version: Int) = CrudRequest(
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
      ranges          = HttpRanges(),
      existingRow     = None
    )

    case class Form(
      definitionFile : String,
      name           : String,
      version        : Int,
      data           : Seq[Data],
      expectedResults: Seq[ExpectedResult]
    )

    case class Data(doc: String, dataFile: String)

    case class ExpectedResult(prefixesInMainViewColumnNames: Boolean, maxIdentifierLength: Int, views: Seq[View])

    case class View(
      providers             : Set[Provider] = Provider.FlatViewSupportedProviders,
      name                  : Provider => String,
      createdColumn         : Boolean,
      lastModifiedTimeColumn: Boolean,
      lastModifiedByColumn  : Boolean,
      columns               : Seq[String],
      values                : Seq[Seq[String]]
    )

    val forms = Seq(
      Form(
        "form-flat-views-no-repetition.xhtml",
        "my-form-1",
        version = 2,
        Seq(
          Data("1", "form-flat-views-no-repetition-data-1.xml"),
          Data("2", "form-flat-views-no-repetition-data-2.xml")
        ),
        Seq(
          ExpectedResult(
            prefixesInMainViewColumnNames = true,
            maxIdentifierLength = FlatView.CompatibilityMaxIdentifierLength,
            Seq(
              View(
                name                   = (provider: Provider) => s"orbeon_f_${provider.entryName.take(9)}_my_form_1_2",
                createdColumn          = true,
                lastModifiedTimeColumn = true,
                lastModifiedByColumn   = true,
                columns                = Seq(
                  "section_1_control_1",
                  "section_1_control_2",
                  "section_1_section_2_control_3",
                  "section_1_section_2_control_4",
                  "sectio_section_section_control",
                  "sectio_section_section_contro1",
                ),
                values                = Seq(
                  Seq("1", "a", "b", "c", "d", "e", "f"),
                  Seq("2", "g", "h", "i", "j", "k", "l")
                )
              )
            )
          )
        )
      ),
      Form(
        "form-flat-views-repetitions.xhtml",
        "my-form-2",
        version = 3,
        Seq(
          Data("1", "form-flat-views-repetitions-data-1.xml"),
          Data("2", "form-flat-views-repetitions-data-2.xml")
        ),
        Seq(
          ExpectedResult(
            prefixesInMainViewColumnNames = false,
            maxIdentifierLength = 63,
            Seq(
              View(
                name                   = (provider: Provider) => s"orbeon_f_${provider.entryName}_my_form_2_3",
                createdColumn          = true,
                lastModifiedTimeColumn = true,
                lastModifiedByColumn   = true,
                columns                = Seq("control_1", "control_2"),
                values                 = Seq(
                  Seq("1", "Control 1.1", "Control 2.1"),
                  Seq("2", "Control 1.2", "Control 2.2")
                )
              ),
              View(
                providers              = Provider.FlatViewSupportedProviders - Provider.MySQL,
                name                   = (provider: Provider) => s"orbeon_f_${provider.entryName}_my_form_2_3_company_section",
                createdColumn          = false,
                lastModifiedTimeColumn = false,
                lastModifiedByColumn   = false,
                columns                = Seq("company_section_repetition", "company_name"),
                values                 = Seq(
                  Seq("1", "1", "Acme 1"),
                  Seq("1", "2", "Wayne Industries 1"),
                  Seq("2", "1", "Acme 2"),
                  Seq("2", "2", "Wayne Industries 2")
                )
              ),
              View(
                providers              = Provider.FlatViewSupportedProviders - Provider.MySQL,
                name                   = (provider: Provider) => s"orbeon_f_${provider.entryName}_my_form_2_3_office_section",
                createdColumn          = false,
                lastModifiedTimeColumn = false,
                lastModifiedByColumn   = false,
                columns                = Seq("company_section_repetition", "office_section_repetition", "office_name"),
                values                 = Seq(
                  Seq("1", "1", "1", "Lausanne 1"),
                  Seq("1", "1", "2", "Geneva 1"),
                  Seq("1", "2", "1", "Gotham City 1"),
                  Seq("1", "2", "2", "New York City 1"),
                  Seq("2", "1", "1", "Lausanne 2"),
                  Seq("2", "1", "2", "Geneva 2"),
                  Seq("2", "2", "1", "Gotham City 2"),
                  Seq("2", "2", "2", "New York City 2")
                )
              ),
              View(
                providers              = Provider.FlatViewSupportedProviders - Provider.MySQL,
                name                   = (provider: Provider) => s"orbeon_f_${provider.entryName}_my_form_2_3_employee_section",
                createdColumn          = false,
                lastModifiedTimeColumn = false,
                lastModifiedByColumn   = false,
                columns                = Seq("company_section_repetition", "office_section_repetition", "employee_section_repetition", "employee_name"),
                values                 = Seq(
                  Seq("1", "1", "1", "1", "John 1"),
                  Seq("1", "1", "1", "2", "Bob 1"),
                  Seq("1", "1", "2", "1", "Ada 1"),
                  Seq("1", "1", "2", "2", "Clara 1"),
                  Seq("1", "2", "1", "1", "Batman 1"),
                  Seq("1", "2", "1", "2", "Robin 1"),
                  Seq("1", "2", "2", "1", "Cat Woman 1"),
                  Seq("1", "2", "2", "2", "Joker 1"),
                  Seq("2", "1", "1", "1", "John 2"),
                  Seq("2", "1", "1", "2", "Bob 2"),
                  Seq("2", "1", "2", "1", "Ada 2"),
                  Seq("2", "1", "2", "2", "Clara 2"),
                  Seq("2", "2", "1", "1", "Batman 2"),
                  Seq("2", "2", "1", "2", "Robin 2"),
                  Seq("2", "2", "2", "1", "Cat Woman 2"),
                  Seq("2", "2", "2", "2", "Joker 2")
                )
              )
            )
          )
        )
      ),
      Form(
        "form-flat-views-long-name.xhtml",
        "my-form-3",
        version = 1,
        Seq(Data("1", "form-flat-views-long-name-data-1.xml")),
        Seq(
          ExpectedResult(
            prefixesInMainViewColumnNames = false,
            maxIdentifierLength = 64,
            Seq(
              View(
                providers              = Set(Provider.MySQL),
                name                   = (_: Provider) => s"orbeon_f_mysql_my_form_3_1",
                createdColumn          = true,
                lastModifiedTimeColumn = true,
                lastModifiedByColumn   = true,
                columns                = Seq("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz01"),
                values                 = Seq(Seq("1", "Value"))
              )
            )
          ),
          ExpectedResult(
            prefixesInMainViewColumnNames = false,
            maxIdentifierLength = 63,
            Seq(
              View(
                providers              = Set(Provider.PostgreSQL),
                name                   = (_: Provider) => s"orbeon_f_postgresql_my_form_3_1",
                createdColumn          = true,
                lastModifiedTimeColumn = true,
                lastModifiedByColumn   = true,
                columns                = Seq("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0"),
                values                 = Seq(Seq("1", "Value"))
              )
            )
          )
        )
      ),
    )

    // Assumption: we're comparing sequences of same length (i.e. view rows)
    implicit val seqStringOrdering: Ordering[Seq[String]] =
      (x: Seq[String], y: Seq[String]) => x.view.zip(y).map { case (a, b) => a.compareTo(b) }.find(_ != 0).getOrElse(0)
  }

  import Private._

  describe("Flat views") {
    it("expose forms with or without repetitions") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("flat views") { (connection, provider) =>
          if (Provider.FlatViewSupportedProviders.contains(provider)) {
            forms.foreach(testForm(provider, connection, _))
          }
        }
      }
    }

    def testForm(
      provider  : Provider,
      connection: Connection,
      form      : Form)(implicit
      ec        : ExternalContext
    ): Unit = {
      // Create form definition
      val formURL = HttpCall.crudURLPrefix(provider, form.name) + "form/form.xhtml"
      HttpAssert.put(formURL, Specific(form.version), xmlBody(form.definitionFile), StatusCode.Created)

      // Create form data
      form.data.foreach { data =>
        val dataURL = HttpCall.crudURLPrefix(provider, form.name) + s"data/${data.doc}/data.xml"
        HttpAssert.put(dataURL, Specific(form.version), xmlBody(data.dataFile), StatusCode.Created)
      }

      val appForm = AppForm(provider.entryName, form.name)

      for {
        expectedResult <- form.expectedResults
        providers = expectedResult.views.flatMap(_.providers)
        if providers.contains(provider)
      } {
        // Create views
        FlatView.createFlatViewsForDocument(
          crudRequest(provider, appForm, form.version),
          form.version,
          connection,
          documentInfo(formURL, form.version),
          expectedResult.prefixesInMainViewColumnNames,
          expectedResult.maxIdentifierLength
        )

        // Parse all views
        expectedResult.views.filter(_.providers.contains(provider)).foreach { view =>
          val viewName = view.name(provider)

          val query = s"SELECT * FROM $viewName"

          // Retrieve view rows
          val viewRows = useAndClose(connection.createStatement.executeQuery(query)) { resultSet =>
            Iterator.iterateWhile(resultSet.next(), ViewRow(resultSet, view.columns))
              .toList
              .sortBy(row => row.documentId +: row.values)
          }

          assert(viewRows.size == view.values.size)

          // Check view row values
          viewRows.zip(view.values).foreach { case (viewRow, expectedValues) =>

            // Check presence of metadata columns
            assert(viewRow.createdOpt.isDefined          == view.createdColumn)
            assert(viewRow.lastModifiedTimeOpt.isDefined == view.lastModifiedTimeColumn)
            assert(viewRow.lastModifiedByOpt.isDefined   == view.lastModifiedByColumn)

            // Compare document ID, repetition values, and control values
            assert(viewRow.documentId +: viewRow.values == expectedValues)
          }

          FlatView.deleteViewIfExists(provider, connection, viewName)
        }
      }
    }
  }

  case class ViewRow(
    documentId      : String,
    createdOpt         : Option[String],
    lastModifiedTimeOpt: Option[String],
    lastModifiedByOpt  : Option[String],
    values          : Seq[String]
 )

  object ViewRow {
    def apply(resultSet: ResultSet, columnNames: Seq[String]): ViewRow = {
      val documentId          = resultSet.getString("metadata_document_id")
      val createdOpt          = Try(resultSet.getString("metadata_created")).toOption
      val lastModifiedTimeOpt = Try(resultSet.getString("metadata_last_modified_time")).toOption
      val lastModifiedByOpt   = Try(resultSet.getString("metadata_last_modified_by")).toOption
      val values              = columnNames.map(resultSet.getString)

      ViewRow(documentId, createdOpt, lastModifiedTimeOpt, lastModifiedByOpt, values)
    }
  }
}
