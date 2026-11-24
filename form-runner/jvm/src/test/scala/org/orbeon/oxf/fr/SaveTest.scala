package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.apache.commons.io.IOUtils
import org.orbeon.connection.BufferedContent
import org.orbeon.io.CharsetNames
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.SimpleSession
import org.orbeon.oxf.fr.process.SimpleProcess
import org.orbeon.oxf.http.{Headers, HttpMethod, StatusCode}
import org.orbeon.oxf.properties.PropertySet.PropertyParams
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, TestHttpClient}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.{SecureUtils, XPath}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.event.events.XXFormsUploadStoreEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.oxf.xforms.state.XFormsStateManager
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import scala.jdk.CollectionConverters.*


class SaveTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Save") {

    val DocumentId         = "b1a724db78a99a060adfd169db1ce31e0eb41f4f"
    val AttachmentFileName = "1ff179d471542b27f110757b44227c6efa816add.bin"

    it("must not duplicate attachments when saving #7343") {

      withTempSQLite { connection =>

        val AddFileSql =
          s"""|INSERT INTO orbeon_form_data_attach (
              |    created, last_modified_time,
              |    app, form, form_version, document_id, draft,
              |    deleted, file_name, file_content
              |) VALUES (
              |    ?, ?,
              |    ?, ?, ?, ?, ?,
              |    ?, ?, ?
              |)""".stripMargin
        useAndClose(connection.prepareStatement(AddFileSql)) { ps =>
          val now = new java.sql.Timestamp(System.currentTimeMillis())
          ps.setTimestamp(1, now)
          ps.setTimestamp(2, now)
          ps.setString   (3, "issue")
          ps.setString   (4, "7343")
          ps.setInt      (5, 1)
          ps.setString   (6, DocumentId)
          ps.setString   (7, "N")
          ps.setString   (8, "N")
          ps.setString   (9, AttachmentFileName)
          ps.setBytes    (10, Array[Byte](1, 2, 3, 4))
          ps.executeUpdate()
        }

        val attachmentUrl = s"/fr/service/persistence/crud/issue/7343/data/$DocumentId/$AttachmentFileName"
        val formDataXml   =
          s"""
             |<form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
             |  <file filename="preexisting.bin" mediatype="application/octet-stream" size="4">$attachmentUrl</file>
             |</form>
             |""".stripMargin
        val formDataDoc =
          XFormsCrossPlatformSupport.stringToTinyTree(
            XPath.GlobalConfiguration,
            formDataXml,
            handleXInclude = false,
            handleLexical  = true
          )

        val (processorService, docOpt, _) =
          runFormRunner(
            app        = "issue",
            form       = "7343",
            mode       = "new",
            documentId = Some(DocumentId),
            attributes = Map("fr-form-data" -> formDataDoc)
          )

        val doc = docOpt.get

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            SimpleProcess.runProcessByName("oxf.fr.detail.process", "my-save")
          }
        }

        val CountFilesSql =
          """|SELECT COUNT(*)
             |FROM orbeon_form_data_attach
             |WHERE app = ? AND form = ? AND document_id = ?""".stripMargin
        useAndClose(connection.prepareStatement(CountFilesSql)) { ps =>
          ps.setString(1, "issue")
          ps.setString(2, "7343")
          ps.setString(3, DocumentId)
          useAndClose(ps.executeQuery()) { rs =>
            rs.next()
            val count = rs.getInt(1)
            assert(count == 1)
          }
        }

        val CountDataSql =
          """|SELECT COUNT(*)
             |FROM orbeon_form_data
             |WHERE app = ? AND form = ? AND document_id = ?""".stripMargin
        useAndClose(connection.prepareStatement(CountDataSql)) { ps =>
          ps.setString(1, "issue")
          ps.setString(2, "7343")
          ps.setString(3, DocumentId)
          useAndClose(ps.executeQuery()) { rs =>
            rs.next()
            val count = rs.getInt(1)
            assert(count == 2)
          }
        }
      }
    }

    it("must update draft state and remove `draft` URL parameter after saving explicitly") {

      withTempSQLite { connection =>

        val AddDataSql =
          """|INSERT INTO orbeon_form_data (
             |    created, last_modified_time,
             |    app, form, form_version, document_id, draft,
             |    deleted, xml
             |) VALUES (
             |    ?, ?,
             |    ?, ?, ?, ?, ?,
             |    ?, ?
             |)""".stripMargin
        useAndClose(connection.prepareStatement(AddDataSql)) { ps =>
          val now = new java.sql.Timestamp(System.currentTimeMillis())
          ps.setTimestamp(1, now)
          ps.setTimestamp(2, now)
          ps.setString   (3, "issue")
          ps.setString   (4, "7343")
          ps.setInt      (5, 1)
          ps.setString   (6, DocumentId)
          ps.setString   (7, "Y")
          ps.setString   (8, "N")
          ps.setString   (9, """<form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0"><file/></form>""")
          ps.executeUpdate()
        }

        val (processorService, docOpt, _) =
          runFormRunner(
            app        = "issue",
            form       = "7343",
            mode       = "edit",
            documentId = Some(DocumentId),
            query      = List("draft" -> "true")
          )

        val doc = docOpt.get

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert(FormRunnerParams().isDraft.contains(true))

            SimpleProcess.runProcessByName("oxf.fr.detail.process", "save-final")
            doc.afterExternalEvents(None)

            assert(FormRunnerParams().isDraft.contains(false))
            assert(
              doc.getScriptsToRun.exists {
                case Right(Left(si)) => si.script.shared.body.contains("""ORBEON.fr.private.API.removeUrlParameter("draft")""")
                case _               => false
              }
            )
          }
        }

        // Loading the document without `draft=true` must now succeed
        val (_, reloadedDocOpt, _) =
          runFormRunner(
            app        = "issue",
            form       = "7343",
            mode       = "edit",
            documentId = Some(DocumentId)
          )

        assert(reloadedDocOpt.isDefined)
      }
    }

    it("must save and download filesystem attachment with spaces and special characters in custom filename") {

      withTempSQLite { connection =>
        withTempDir { fsDir =>

          val attrs = propertiesOverride(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.issue.7343.data.attachments", XS_STRING_QNAME, "filesystem"),
            PropertyParams(Map.empty, "oxf.fr.persistence.filesystem.base-path", XS_STRING_QNAME, fsDir.toAbsolutePath.toString),
            PropertyParams(
              Map(XMLNames.FRPrefix -> XMLNames.FR),
              "oxf.fr.persistence.attachments.filename.issue.7343",
              XS_STRING_QNAME,
              "concat(format-date(current-date(), '[Y]-[M01]-[D01]'), '-', fr:attachment-id(), '-', fr:attachment-filename())"
            )
          )

          val originalFilename   = "Numérisation 2026 #1 50% [final] (2)+a;b.pdf"
          val expectedContentStr = "Hello attachment with spaces"

          val (processorService, docOpt, _) =
            runFormRunner(
              app        = "issue",
              form       = "7343",
              mode       = "new",
              attributes = attrs
            )

          val doc = docOpt.get

          val docId =
            withTestExternalContext { ec =>
              attrs.foreach { case (k, v) => ec.getRequest.getAttributesMap.put(k, v) }
              withFormRunnerDocument(processorService, doc) {
                simulateUpload(
                  controlId = "file-control",
                  filename  = originalFilename,
                  mediatype = "application/pdf",
                  content   = expectedContentStr
                )
                SimpleProcess.runProcessByName("oxf.fr.detail.process", "save-final")
                FormRunnerParams().document.get
              }
            }

          val SelectAttachSql =
            """|SELECT file_name, file_content
               |FROM orbeon_form_data_attach
               |WHERE app = ? AND form = ? AND document_id = ? AND draft = 'N' AND deleted = 'N'""".stripMargin

          val dbFileName =
            useAndClose(connection.prepareStatement(SelectAttachSql)) { ps =>
              ps.setString(1, "issue")
              ps.setString(2, "7343")
              ps.setString(3, docId)
              useAndClose(ps.executeQuery()) { rs =>
                assert(rs.next())
                val fileName    = rs.getString("file_name")
                val fileContent = Option(rs.getBytes("file_content"))
                assert(! rs.next())
                assert(fileName.endsWith(s"-$originalFilename"))
                assert(! fileName.contains("%20"))
                assert(fileContent.forall(_.isEmpty))
                fileName
              }
            }

          val files =
            useAndClose(Files.walk(fsDir)) { stream =>
              stream.iterator().asScala.filter(Files.isRegularFile(_)).toList
            }

          assert(files.size == 1)
          assert(files.head.getFileName.toString == dbFileName)
          assert(Files.readString(files.head, StandardCharsets.UTF_8) == expectedContentStr)

          val session =
            new SimpleSession(SecureUtils.randomHexId) |!>
              XFormsStateManager.sessionCreated

          val (_, _, _, editResponse) =
            runFormRunnerReturnAll(
              app             = "issue",
              form            = "7343",
              mode            = "edit",
              documentId      = Some(docId),
              attributes      = attrs,
              providedSession = session.some
            )

          val editHtml        = new String(BufferedContent(editResponse.content)(IOUtils.toByteArray).body, CharsetNames.Utf8)
          val downloadHrefOpt = extractDynamicDownloadHref(editHtml)
          assert(downloadHrefOpt.isDefined)

          val (_, downloadResponse, _, _) =
            TestHttpClient.connect(
              url             = downloadHrefOpt.get,
              method          = HttpMethod.GET,
              headers         = Map.empty,
              content         = None,
              attributes      = attrs,
              providedSession = session.some
            )

          assert(downloadResponse.statusCode == StatusCode.Ok)
          val downloadedContent = new String(BufferedContent(downloadResponse.content)(IOUtils.toByteArray).body, CharsetNames.Utf8)
          assert(downloadedContent == expectedContentStr)
        }
      }
    }

    it("must download previously saved filesystem attachment with unencoded spaces in XML data") {

      withTempSQLite { connection =>
        withTempDir { fsDir =>

          val attrs = propertiesOverride(
            PropertyParams(Map.empty, "oxf.fr.persistence.provider.issue.7343.data.attachments", XS_STRING_QNAME, "filesystem"),
            PropertyParams(Map.empty, "oxf.fr.persistence.filesystem.base-path", XS_STRING_QNAME, fsDir.toAbsolutePath.toString)
          )

          val docId             = "d3c946fd90cb1c282cf0f381fd3e053020d63f6b"
          val legacyFileName    = "2026-11-22-52d4b062094a06b4cc28d86dde6be22590fc833d-CI ARES (2).pdf"
          val expectedLegacyStr = "Legacy file content"

          val targetFile =
            fsDir
              .resolve("issue")
              .resolve("7343")
              .resolve("data")
              .resolve(docId)
              .resolve("1")
              .resolve(legacyFileName)

          Files.createDirectories(targetFile.getParent)
          Files.writeString(targetFile, expectedLegacyStr, StandardCharsets.UTF_8)

          val now = new java.sql.Timestamp(System.currentTimeMillis())

          val InsertAttachSql =
            """|INSERT INTO orbeon_form_data_attach (
               |    created, last_modified_time,
               |    app, form, form_version, document_id, draft,
               |    deleted, file_name, file_content
               |) VALUES (
               |    ?, ?,
               |    ?, ?, ?, ?, ?,
               |    ?, ?, ?
               |)""".stripMargin

          useAndClose(connection.prepareStatement(InsertAttachSql)) { ps =>
            ps.setTimestamp(1, now)
            ps.setTimestamp(2, now)
            ps.setString   (3, "issue")
            ps.setString   (4, "7343")
            ps.setInt      (5, 1)
            ps.setString   (6, docId)
            ps.setString   (7, "N")
            ps.setString   (8, "N")
            ps.setString   (9, legacyFileName)
            ps.setBytes    (10, Array.emptyByteArray)
            ps.executeUpdate()
          }

          val InsertDataSql =
            """|INSERT INTO orbeon_form_data (
               |    created, last_modified_time,
               |    app, form, form_version, document_id, draft,
               |    deleted, xml
               |) VALUES (
               |    ?, ?,
               |    ?, ?, ?, ?, ?,
               |    ?, ?
               |)""".stripMargin

          val attachmentPath = s"/fr/service/persistence/crud/issue/7343/data/$docId/$legacyFileName"
          val xmlData =
            s"""<form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0"><file filename="CI ARES (2).pdf" mediatype="application/pdf" size="${expectedLegacyStr.length}">$attachmentPath</file></form>"""

          useAndClose(connection.prepareStatement(InsertDataSql)) { ps =>
            ps.setTimestamp(1, now)
            ps.setTimestamp(2, now)
            ps.setString   (3, "issue")
            ps.setString   (4, "7343")
            ps.setInt      (5, 1)
            ps.setString   (6, docId)
            ps.setString   (7, "N")
            ps.setString   (8, "N")
            ps.setString   (9, xmlData)
            ps.executeUpdate()
          }

          val session =
            new SimpleSession(SecureUtils.randomHexId) |!>
              XFormsStateManager.sessionCreated

          val (_, _, _, editResponse) =
            runFormRunnerReturnAll(
              app             = "issue",
              form            = "7343",
              mode            = "edit",
              documentId      = Some(docId),
              attributes      = attrs,
              providedSession = session.some
            )

          val editHtml        = new String(BufferedContent(editResponse.content)(IOUtils.toByteArray).body, CharsetNames.Utf8)
          val downloadHrefOpt = extractDynamicDownloadHref(editHtml)
          assert(downloadHrefOpt.isDefined)

          val (_, downloadResponse, _, _) =
            TestHttpClient.connect(
              url             = downloadHrefOpt.get,
              method          = HttpMethod.GET,
              headers         = Map.empty,
              content         = None,
              attributes      = attrs,
              providedSession = session.some
            )

          assert(downloadResponse.statusCode == StatusCode.Ok)
          val downloadedContent = new String(BufferedContent(downloadResponse.content)(IOUtils.toByteArray).body, CharsetNames.Utf8)
          assert(downloadedContent == expectedLegacyStr)
        }
      }
    }
  }

  private def withTempDir[T](thunk: Path => T): T = {
    val dir = Files.createTempDirectory("orbeon-fs-attach-test-")
    try {
      thunk(dir)
    } finally {
      useAndClose(Files.walk(dir)) { stream =>
        stream.sorted(Comparator.reverseOrder()).forEach(Files.deleteIfExists(_))
      }
    }
  }

  private def simulateUpload(
    controlId: String,
    filename : String,
    mediatype: String,
    content  : String
  )(implicit
    doc      : XFormsContainingDocument
  ): Unit = {
    val bytes    = content.getBytes(StandardCharsets.UTF_8)
    val tempPath = Files.createTempFile("orbeon-upload-test-", ".tmp")
    tempPath.toFile.deleteOnExit()
    Files.write(tempPath, bytes)

    val outerPrefix = resolveObject[XFormsControl](controlId).get.effectiveId + "≡"

    val uploadControl =
      doc.controls.getCurrentControlTree.getRelevantUploadControls
        .find(_.effectiveId.startsWith(outerPrefix))
        .get

    doc.withOutermostActionHandler {
      Dispatch.dispatchEvent(
        new XXFormsUploadStoreEvent(
          uploadControl,
          Map(
            "file"                     -> Some(tempPath.toUri.toString),
            "filename"                 -> Some(filename),
            Headers.ContentTypeLower   -> Some(mediatype),
            Headers.ContentLengthLower -> Some(bytes.length.toString)
          )
        ),
        EventCollector.Throw
      )
    }
  }

  private val DynamicHrefRegex = """href="(?:[^"]*)?(/xforms-server/dynamic/[^"]+)"""".r

  private def extractDynamicDownloadHref(html: String): Option[String] =
    DynamicHrefRegex.findFirstMatchIn(html).map(_.group(1))
}

