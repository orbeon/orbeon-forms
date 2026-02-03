/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.apache.commons.io.IOUtils
import org.orbeon.connection.{BufferedContent, StreamedContent}
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.Credentials
import org.orbeon.oxf.fr.FormRunnerSupport.*
import org.orbeon.oxf.fr.persistence.relational.SqlReader
import org.orbeon.oxf.http.HttpMethod.{GET, POST}
import org.orbeon.oxf.http.HttpResponse
import org.orbeon.oxf.test.TestHttpClient.CacheEvent
import org.orbeon.oxf.test.{DocumentTestBase, TestHttpClient}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.webapp.ProcessorService
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.state.XFormsDocumentCache

import java.sql.{Connection, DriverManager}


object FormRunnerSupport {
  private val FindUUIDInHTMLBodyRE = """<script src="\/orbeon\/xforms-server\/form\/dynamic\/([^.]+)\.js"""".r
}

trait FormRunnerSupport extends DocumentTestBase {

  def withFormRunnerDocument[T](processorService: ProcessorService, doc: XFormsContainingDocument)(thunk: => T): T =
    ProcessorService.withProcessorService(processorService) {
      setupDocument(doc) // FIXME: to make it available to XFormsSupport
      withContainingDocument(doc) {
        try {
          thunk
        } finally {
          disposeDocument()
        }
      }
    }

  def withTempSQLite[T](thunk: Connection => T): T = {
    val sqliteFile = java.nio.file.Files.createTempFile("orbeon-bg-sqlite-", ".sqlite")
    System.setProperty("orbeon.test.sqlite.path", sqliteFile.toAbsolutePath.toString)
    Class.forName("org.sqlite.JDBC")
    useAndClose(DriverManager.getConnection(s"jdbc:sqlite:${sqliteFile.toAbsolutePath}")) { connection =>
      val stringStatements = SqlReader.read("2026.1/sqlite-2026_1.sql")
      useAndClose(connection.createStatement()) { sqlStatement =>
        stringStatements.foreach(sqlStatement.executeUpdate)
      }
      thunk(connection)
    }
  }

  def performSectionAction(sectionControl: XFormsControl, action: String): Unit = {
    // NOTE: We can't yet just dispatch `fr-insert-below` to the section, so find the nested repeater.
    val repeater =
      ControlsIterator(sectionControl, includeSelf = false) collectFirst {
        case c: XFormsComponentControl if c.localName == "repeater" =>  c
      } get

    dispatch(name = action, effectiveId = repeater.effectiveId)
  }

  def performGridAction(gridControl: XFormsControl, action: String): Unit =
    dispatch(name = action, effectiveId = gridControl.effectiveId)

  def setFormRunnerLanguage(lang: String): Unit =
    setControlValueWithEventSearchNested("fr-language-selector-select", lang)

  def runFormRunner(
    app        : String,
    form       : String,
    mode       : String,
    formVersion: String                         = "", // not used yet
    documentId : Option[String]                 = None,
    query      : IterableOnce[(String, String)] = Nil,
    initialize : Boolean                        = true,
    content    : Option[StreamedContent]        = None,
    attributes : Map[String, AnyRef]            = Map.empty,
    background : Boolean                        = false

  ): (ProcessorService, Option[XFormsContainingDocument], List[CacheEvent]) = {

    val (processorService, docOpt, events, _) =
      runFormRunnerReturnAll(
        app,
        form,
        mode,
        formVersion,
        documentId,
        query,
        initialize,
        content,
        attributes,
        background = background
      )

    (processorService, docOpt, events)
  }

  def runFormRunnerReturnContent(
    app        : String,
    form       : String,
    mode       : String,
    formVersion: String                         = "", // not used yet
    documentId : Option[String]                 = None,
    query      : IterableOnce[(String, String)] = Nil,
    initialize : Boolean                        = true,
    content    : Option[StreamedContent]        = None,
    attributes : Map[String, AnyRef]            = Map.empty,
    headers    : Map[String, List[String]]      = Map.empty,
    background : Boolean                        = false
  ): (ProcessorService, BufferedContent, HttpResponse) = {

    val (processorService, _, _, response) =
      runFormRunnerReturnAll(
        app,
        form,
        mode,
        formVersion,
        documentId,
        query,
        initialize,
        content,
        attributes,
        headers,
        background = background
      )

    (processorService, BufferedContent(response.content)(IOUtils.toByteArray), response)
  }

  def runFormRunnerReturnAll(
    app        : String,
    form       : String,
    mode       : String,
    formVersion: String                         = "", // not used yet
    documentId : Option[String]                 = None,
    query      : IterableOnce[(String, String)] = Nil,
    initialize : Boolean                        = true,
    content    : Option[StreamedContent]        = None,
    attributes : Map[String, AnyRef]            = Map.empty,
    headers    : Map[String, List[String]]      = Map.empty,
    credentials: Option[Credentials]            = None,
    background : Boolean                        = false
  ): (ProcessorService, Option[XFormsContainingDocument], List[CacheEvent], HttpResponse) = {

    val (processorService, response, _, events) =
      TestHttpClient.connect(
        url         = buildFormRunnerPath(app, form, mode, documentId, query, background),
        method      = if (content.isDefined) POST else GET,
        headers     = headers,
        content     = content,
        credentials = credentials,
        attributes  = attributes
      )

    val responseContent = BufferedContent(response.content)(IOUtils.toByteArray)

    val uuidOpt = FindUUIDInHTMLBodyRE.findFirstMatchIn(new String(responseContent.body, CharsetNames.Utf8)) map (_.group(1))
    val docOpt = uuidOpt flatMap XFormsDocumentCache.peekForTests

    (
      processorService,
      docOpt,
      events,
      // Recreate a response with the buffered content for further processing
      new HttpResponse {
        def statusCode  : Int                       = response.statusCode
        def headers     : Map[String, List[String]] = response.headers
        def lastModified: Option[Long]              = response.lastModified
        def content     : StreamedContent           = StreamedContent(responseContent)
        def disconnect(): Unit                      = response.disconnect()
      }
    )
  }

  // TODO: add form version parameter
  def buildFormRunnerPath(
    app       : String,
    form      : String,
    mode      : String,
    documentId: Option[String]                 = None,
    query     : IterableOnce[(String, String)] = Nil,
    background: Boolean                        = false
  ): String =
    PathUtils.recombineQuery(s"/fr/${if (background) "service/" else ""}$app/$form/$mode${documentId.map("/" +).getOrElse("")}", query)
}