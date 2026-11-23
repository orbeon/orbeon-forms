/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.connection.StreamedContent
import org.orbeon.dom.Element
import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.fb.FormBuilderSummarySupport.PublishedVersionSummary
import org.orbeon.oxf.fr.library.FormRunnerFunctionLibrary
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.persistence.relational.index.Index
import org.orbeon.oxf.fr.{AppForm, DataFormatVersion, FormRunnerSummary, FormRunnerSupport, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, StaticXPath, XPath}
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URI
import java.nio.charset.StandardCharsets
import java.util as ju
import scala.util.Success


class PublishedVersionsSummaryTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  private implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

  private val SampleFormUrl = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"

  private def buildFormDefinitionBytes(app: String, form: String): Array[Byte] = {
    val doc        = ProcessorUtils.createDocumentFromURL(SampleFormUrl, null)
    val docWrapper = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)

    (docWrapper.rootElement descendant "application-name").head.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element].setText(app)
    (docWrapper.rootElement descendant "form-name")       .head.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element].setText(form)

    doc.getRootElement.serializeToString().getBytes(StandardCharsets.UTF_8)
  }

  private def saveInFormBuilder(documentId: String, formBytes: Array[Byte], attributes: Map[String, AnyRef]): Unit = {
    val (_, response, _, _) =
      TestHttpClient.connect(
        url        = s"/fr/service/persistence/crud/orbeon/builder/data/$documentId/data.xml",
        method     = HttpMethod.PUT,
        headers    = Map(
          Headers.ContentType                 -> List(ContentTypes.XmlContentType),
          Version.OrbeonFormDefinitionVersion -> List("1")
        ),
        content    = Some(StreamedContent.fromBytes(formBytes, Some(ContentTypes.XmlContentType))),
        attributes = attributes
      )
    try {
      assert(response.statusCode == StatusCode.Created)
    } finally {
      response.disconnect()
    }
  }

  private def publishFormDefinition(
    documentId: String,
    formBytes : Array[Byte],
    version   : String,
    available : Boolean,
    attributes: Map[String, AnyRef]
  ): Unit = {
    val (_, response, _, _) =
      TestHttpClient.connect(
        url        = s"/fr/service/publish?document-id=$documentId&form-definition-version=$version&available=$available",
        method     = HttpMethod.POST,
        headers    = Map(Headers.ContentType -> List(ContentTypes.XmlContentType)),
        content    = Some(StreamedContent.fromBytes(formBytes, Some(ContentTypes.XmlContentType))),
        attributes = attributes
      )
    try {
      assert(response.statusCode == StatusCode.Ok)
    } finally {
      response.disconnect()
    }
  }

  describe("FormBuilderSummarySupport") {

    it("must format unpublished, available, and unavailable version pills with accessible text and escaped tooltips") {
      val unpublishedHtml =
        FormBuilderSummarySupport.formatPublishedVersionsHtml(
          versions         = Nil,
          versionLabel     = "Version",
          availableLabel   = "Available",
          unavailableLabel = "Unavailable"
        )
      assert(unpublishedHtml == """<span class="fr-version-empty" aria-hidden="true">—</span>""")

      // Unsorted input (v2 unavailable, v1 available) must render sorted ascending (v1 then v2)
      val multiHtml =
        FormBuilderSummarySupport.formatPublishedVersionsHtml(
          versions         = List(
            PublishedVersionSummary(version = 2, available = false),
            PublishedVersionSummary(version = 1, available = true)
          ),
          versionLabel     = """Version "A&B"""",
          availableLabel   = "Available",
          unavailableLabel = "Unavailable <Draft>"
        )

      assert(multiHtml.startsWith("""<span class="fr-status-pills">"""))
      val v1Pos = multiHtml.indexOf(">v1<")
      val v2Pos = multiHtml.indexOf(">v2<")
      assert(v1Pos > 0 && v2Pos > v1Pos)
      assert(multiHtml.contains("""class="fr-table-pill fr-version-badge fr-pill-success""""))
      assert(multiHtml.contains("""class="fr-table-pill fr-version-badge fr-pill-muted fr-pill-unavailable""""))
      assert(multiHtml.contains("""title="Version &quot;A&amp;B&quot;: v1 — Available""""))
      assert(multiHtml.contains("""<span class="visually-hidden"> (Available)</span>"""))
      assert(multiHtml.contains("""<span class="visually-hidden"> (Unavailable &lt;Draft&gt;)</span>"""))
    }

    it("must extract distinct (app, form) pairs and cache batched metadata lookups per HTTP request") {
      val docsXml =
        <documents>
          <document name="doc-1">
            <details>
              <detail path="xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*/application-name">acme</detail>
              <detail path="xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*/form-name">order</detail>
            </details>
          </document>
          <document name="doc-2">
            <details>
              <detail>acme</detail>
              <detail>library</detail>
            </details>
          </document>
          <document name="doc-3">
            <details>
              <detail>acme</detail>
              <detail>order</detail>
            </details>
          </document>
        </documents>

      val docNodes = (elemToDocumentInfo(docsXml).rootElement / "document").toList
      val extracted = FormBuilderSummarySupport.extractPageAppForms(docNodes)
      assert(extracted == Set(AppForm("acme", "order"), AppForm("acme", "library")))

      val sampleMetadataResponse =
        elemToDocumentInfo(
          <forms search-total="3">
            <form operations="*">
              <application-name>acme</application-name>
              <form-name>order</form-name>
              <form-version>2</form-version>
              <available>false</available>
            </form>
            <form operations="*">
              <application-name>acme</application-name>
              <form-name>order</form-name>
              <form-version>1</form-version>
              <available>true</available>
            </form>
            <form operations="">
              <application-name>acme</application-name>
              <form-name>remote-only</form-name>
              <form-version>1</form-version>
              <remote-server url="https://remote.example.org"><available>true</available></remote-server>
            </form>
          </forms>
        )

      var fetchCount = 0
      val requestAtts = new ju.HashMap[String, AnyRef]()

      val r1 =
        FormBuilderSummarySupport.resolveVersionsForPage(
          appForms             = extracted,
          requestAttributesOpt = Some(requestAtts),
          fetchMetadata        = _ => { fetchCount += 1; Success(sampleMetadataResponse) }
        )
      val r2 =
        FormBuilderSummarySupport.resolveVersionsForPage(
          appForms             = extracted,
          requestAttributesOpt = Some(requestAtts),
          fetchMetadata        = _ => { fetchCount += 1; Success(sampleMetadataResponse) }
        )

      assert(fetchCount == 1)
      assert(r1 == r2)
      assert(r1.get(AppForm("acme", "order")).contains(List(
        PublishedVersionSummary(1, available = true),
        PublishedVersionSummary(2, available = false)
      )))
      assert(! r1.contains(AppForm("acme", "remote-only")))
      assert(! r1.contains(AppForm("acme", "library")))
    }

    it("must place the non-sortable Version column right after Form Name in Form Builder's Summary dynamic form") {
      val builderFormDoc =
        XFormsCrossPlatformSupport.readTinyTreeFromUrl(
          URI.create("oxf:/forms/orbeon/builder/form/form.xhtml"),
          handleXInclude = true
        )
      val builderResourcesDoc =
        XFormsCrossPlatformSupport.readTinyTreeFromUrl(
          URI.create("oxf:/forms/orbeon/builder/form/resources.xml"),
          handleXInclude = true
        )
      val summaryViewDoc =
        XFormsCrossPlatformSupport.readTinyTreeFromUrl(
          URI.create("oxf:/apps/fr/summary/view.xhtml"),
          handleXInclude = false
        )
      val templateNode =
        (summaryViewDoc.rootElement descendant "*:instance")
          .find(_.id == "fr-dynamic-instance-template")
          .flatMap(_.firstChildOpt(*))
          .get

      // `published-version` must NOT be indexed in `orbeon_i_control_text`
      val searchable =
        Index.searchableValues(
          formDoc                      = builderFormDoc,
          appForm                      = AppForm.FormBuilder,
          searchVersionOpt             = None,
          databaseDataFormatVersion    = DataFormatVersion.V20191,
          providedResourcesRootElemOpt = Some(builderResourcesDoc.rootElement)
        )
      assert(! searchable.controls.map(_.name).contains("published-version"))

      val builtDynamic =
        FormRunnerSummary.buildSummaryDynamicFormImpl(
          formDoc                   = builderFormDoc,
          templateNode              = templateNode,
          appForm                   = AppForm.FormBuilder,
          databaseDataFormatVersion = DataFormatVersion.V20191,
          resourcesRootElemOpt      = Some(builderResourcesDoc.rootElement),
          functionLibrary           = FormRunnerFunctionLibrary
        )

      val domRoot = StaticXPath.tinyTreeToOrbeonDom(builtDynamic).getRootElement
      val columns =
        domRoot.descendantElementIterator(includeSelf = true)
          .filter(_.getName == "sortable-th")
          .filter(_.attributeValue("column") != "{$column}")
          .map(th => th.attributeValue("column") -> th.attributeValue("sort-supported"))
          .toList

      assert(columns.map(_._1) == List(
        "xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*/application-name",
        "xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*/form-name",
        "published-version",
        "xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*/title",
        "xh:head/xf:model[@id = 'fr-form-model']/xf:instance[@id = 'fr-form-metadata']/*/description"
      ))
      assert(columns(2)._2 == "false")
    }

    it("must render live published versions (including unavailable and library forms) on `/fr/orbeon/builder/summary` (#1795)") {
      withTempSQLite { _ =>
        val sqliteOverrides =
          propertiesOverride(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider._.library.form",      XS_STRING_QNAME, "sqlite"),
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.orbeon.builder.data", XS_STRING_QNAME, "sqlite")
          )

        val unpubBytes = buildFormDefinitionBytes("sqlite", "form-unpublished")
        val multiBytes = buildFormDefinitionBytes("sqlite", "form-multi")
        val libBytes   = buildFormDefinitionBytes("sqlite", "library")

        saveInFormBuilder("1795000000000000000000000000000000000001", unpubBytes, sqliteOverrides)
        saveInFormBuilder("1795000000000000000000000000000000000002", multiBytes, sqliteOverrides)
        saveInFormBuilder("1795000000000000000000000000000000000003", libBytes,   sqliteOverrides)

        publishFormDefinition("1795000000000000000000000000000000000002", multiBytes, version = "next", available = true,  sqliteOverrides)
        publishFormDefinition("1795000000000000000000000000000000000002", multiBytes, version = "next", available = false, sqliteOverrides)
        publishFormDefinition("1795000000000000000000000000000000000003", libBytes,   version = "next", available = true,  sqliteOverrides)

        val (_, summaryResponse, _, _) =
          TestHttpClient.connect(
            url        = "/fr/orbeon/builder/summary",
            method     = HttpMethod.GET,
            headers    = Map.empty,
            content    = None,
            attributes = sqliteOverrides
          )

        try {
          assert(summaryResponse.statusCode == StatusCode.Ok)
          val html = useAndClose(summaryResponse.content.stream) { is =>
            new String(is.readAllBytes(), StandardCharsets.UTF_8)
          }

          assert(html.contains("form-unpublished"))
          assert(html.contains("form-multi"))
          assert(html.contains("fr-version-empty"))
          assert(html.contains("fr-pill-success"))
          assert(html.contains("fr-pill-unavailable"))
          assert(html.contains(">v1<"))
          assert(html.contains(">v2<"))
        } finally {
          summaryResponse.disconnect()
        }
      }
    }
  }
}
