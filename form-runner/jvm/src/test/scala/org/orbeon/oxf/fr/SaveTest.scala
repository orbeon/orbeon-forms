package org.orbeon.oxf.fr

import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.XPath
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

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
  }
}

