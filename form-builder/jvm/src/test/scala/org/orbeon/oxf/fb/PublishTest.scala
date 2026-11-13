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
import org.orbeon.oxf.fr.{FormRunnerSupport, NodeInfoCell, Version}
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.util.{ContentTypes, XPath}
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets


class PublishTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  private val UnannotatedFormUrl = "oxf:/org/orbeon/oxf/fb/template-with-sections-repeats.xhtml"

  private def loadFormDefinitionBytes(app: String, form: String): Array[Byte] = {
    val doc        = ProcessorUtils.createDocumentFromURL(UnannotatedFormUrl, null)
    val docWrapper = new DocumentWrapper(doc, null, XPath.GlobalConfiguration)

    // Ensure the input form genuinely contains `<fr:grid>` elements without `@id` so that
    // `annotate.xpl` -> `migrateGridsEnclosingElements` -> `addMissingGridIds` -> `nextIds` -> `readXcvFromClipboard`
    // is exercised with `count > 0`.
    val gridsBeforePublish = docWrapper.rootElement descendant NodeInfoCell.GridTest
    assert(gridsBeforePublish.nonEmpty)
    assert(gridsBeforePublish.exists(! _.hasId))

    (docWrapper.rootElement descendant "application-name").head.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element].setText(app)
    (docWrapper.rootElement descendant "form-name")       .head.asInstanceOf[NodeWrapper].getUnderlyingNode.asInstanceOf[Element].setText(form)

    doc.getRootElement.serializeToString().getBytes(StandardCharsets.UTF_8)
  }

  private def readResponseXml(url: String, response: org.orbeon.oxf.http.HttpResponse): NodeInfo =
    useAndClose(response.content.stream) { is =>
      XFormsCrossPlatformSupport.readTinyTree(
        XPath.GlobalConfiguration,
        is,
        url,
        handleXInclude = false,
        handleLexical  = false
      ).rootElement
    }

  private def assertPublishedFormHasGridIds(app: String, form: String): Unit = {
    val crudFormUrl = s"/fr/service/persistence/crud/$app/$form/form/form.xhtml"
    val (_, getResponse, getSessionOpt, _) =
      TestHttpClient.connect(
        url     = crudFormUrl,
        method  = HttpMethod.GET,
        headers = Map.empty,
        content = None
      )

    try {
      assert(getResponse.statusCode == StatusCode.Ok)
      assert(getSessionOpt.isEmpty)

      val publishedRoot  = readResponseXml(crudFormUrl, getResponse)
      val publishedGrids = publishedRoot descendant NodeInfoCell.GridTest
      assert(publishedGrids.nonEmpty)
      assert(publishedGrids.forall(_.hasId))
    } finally {
      getResponse.disconnect()
    }
  }

  private lazy val sqlitePublishOverrides =
    propertiesOverride(
      PropertyParams(Map.empty, "oxf.fr.persistence.provider._.library.form",      XS_STRING_QNAME, "sqlite"),
      PropertyParams(Map.empty, "oxf.fr.persistence.provider.orbeon.builder.data", XS_STRING_QNAME, "sqlite")
    )

  describe("Publish API (#8083)") {

    it("must publish a POSTed form definition without creating a session") {
      withTempSQLite { _ =>
        val app       = "sqlite"
        val form      = "issue-8083-posted"
        val formBytes = loadFormDefinitionBytes(app, form)

        val publishUrl = "/fr/service/publish"
        val (_, response, sessionOpt, _) =
          TestHttpClient.connect(
            url        = publishUrl,
            method     = HttpMethod.POST,
            headers    = Map(Headers.ContentType -> List(ContentTypes.XmlContentType)),
            content    = Some(StreamedContent.fromBytes(formBytes, Some(ContentTypes.XmlContentType))),
            attributes = sqlitePublishOverrides
          )

        try {
          assert(response.statusCode == StatusCode.Ok)
          assert(sessionOpt.isEmpty)

          val responseRoot = readResponseXml(publishUrl, response)
          assert((responseRoot / "app").stringValue               == app)
          assert((responseRoot / "form").stringValue              == form)
          assert((responseRoot / "published-version").stringValue == "1")
        } finally {
          response.disconnect()
        }

        assertPublishedFormHasGridIds(app, form)
      }
    }

    it("must publish a stored Form Builder form definition by document ID without creating a session") {
      withTempSQLite { _ =>
        val app        = "sqlite"
        val form       = "issue-8083-stored"
        val documentId = "8083000000000000000000000000000000000001"
        val formBytes  = loadFormDefinitionBytes(app, form)

        val crudBuilderDataUrl = s"/fr/service/persistence/crud/orbeon/builder/data/$documentId/data.xml"
        val (_, putResponse, putSessionOpt, _) =
          TestHttpClient.connect(
            url        = crudBuilderDataUrl,
            method     = HttpMethod.PUT,
            headers    = Map(
              Headers.ContentType                 -> List(ContentTypes.XmlContentType),
              Version.OrbeonFormDefinitionVersion -> List("1")
            ),
            content    = Some(StreamedContent.fromBytes(formBytes, Some(ContentTypes.XmlContentType))),
            attributes = sqlitePublishOverrides
          )

        try {
          assert(putResponse.statusCode == StatusCode.Created)
          assert(putSessionOpt.isEmpty)
        } finally {
          putResponse.disconnect()
        }

        val publishByIdUrl = s"/fr/service/orbeon/builder/publish/$documentId"
        val (_, publishResponse, publishSessionOpt, _) =
          TestHttpClient.connect(
            url        = publishByIdUrl,
            method     = HttpMethod.POST,
            headers    = Map.empty,
            content    = None,
            attributes = sqlitePublishOverrides
          )

        try {
          assert(publishResponse.statusCode == StatusCode.Ok)
          assert(publishSessionOpt.isEmpty)

          val responseRoot = readResponseXml(publishByIdUrl, publishResponse)
          assert((responseRoot / "app").stringValue               == app)
          assert((responseRoot / "form").stringValue              == form)
          assert((responseRoot / "published-version").stringValue == "1")
        } finally {
          publishResponse.disconnect()
        }

        assertPublishedFormHasGridIds(app, form)
      }
    }
  }
}
