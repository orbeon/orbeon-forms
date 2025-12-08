package org.orbeon.oxf.fr.persistence.test

import org.orbeon.oxf.fr.Version.Specific
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter.*
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.file.{Files, Paths}
import scala.util.Random


class AttachmentReadFromPersistenceTest
  extends DocumentTestBase
     with XFormsSupport
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[AttachmentReadFromPersistenceTest]), true)

  private val FormName = "maybe-filesystem-attachments-form"

  describe("Attachment read from persistence") {

    it("reads attachments from database even after switching to filesystem provider") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("attachment read from persistence") { (_, provider) =>

          val formURL        = HttpCall.crudURLPrefix(provider, FormName) + "form/form.xhtml"
          val dataURL        = HttpCall.crudURLPrefix(provider, FormName) + "data/123/data.xml"
          val attachment1URL = HttpCall.crudURLPrefix(provider, FormName) + "data/123/attachment1"
          val attachment2URL = HttpCall.crudURLPrefix(provider, FormName) + "data/123/attachment2"

          val filesystemBasePath = Paths.get(
            Properties.instance.getPropertySet.getString(
              "oxf.fr.persistence.filesystem.base-path",
              default = ""
            )
          )

          val form = HttpCall.XML(
            <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
              <xh:head>
                <xf:model id="fr-form-model">
                  <xf:instance id="fr-form-metadata">
                    <metadata>
                      <application-name>{provider.entryName}</application-name>
                      <form-name>{FormName}</form-name>
                    </metadata>
                  </xf:instance>
                </xf:model>
              </xh:head>
            </xh:html>.toDocument
          )

          val dummyData = HttpCall.XML(<_/>.toDocument)

          val attachment1Data = new Array[Byte](1024)
          Random.nextBytes(attachment1Data)
          val attachment1Body = HttpCall.Binary(attachment1Data)

          val attachment2Data = new Array[Byte](2048)
          Random.nextBytes(attachment2Data)
          val attachment2Body = HttpCall.Binary(attachment2Data)

          try {
            // Store form and data
            HttpAssert.put(formURL, Specific(1), form, StatusCode.Created)
            HttpAssert.put(dataURL, Specific(1), dummyData, StatusCode.Created)

            // Store first attachment with database provider
            HttpAssert.put(attachment1URL, Specific(1), attachment1Body, StatusCode.Created)

            // Read first attachment and verify
            HttpAssert.get(
              attachment1URL,
              Specific(1),
              HttpAssert.ExpectedBody(attachment1Body, org.orbeon.oxf.fr.permission.AnyOperation, Some(1))
            )

            // Create filesystem directory before switching to filesystem provider
            if (!Files.exists(filesystemBasePath))
              Files.createDirectory(filesystemBasePath)

            // Switch to filesystem provider
            Properties.initialize("oxf:/ops/unit-tests/properties-local-filesystem.xml")

            // Read the previously stored attachment (should still come from database)
            HttpAssert.get(
              attachment1URL,
              Specific(1),
              HttpAssert.ExpectedBody(attachment1Body, org.orbeon.oxf.fr.permission.AnyOperation, Some(1))
            )

            // Store second attachment with filesystem provider
            HttpAssert.put(attachment2URL, Specific(1), attachment2Body, StatusCode.Created)

            // Read second attachment and verify
            HttpAssert.get(
              attachment2URL,
              Specific(1),
              HttpAssert.ExpectedBody(attachment2Body, org.orbeon.oxf.fr.permission.AnyOperation, Some(1))
            )

            // Check that the second attachment file exists in the filesystem
            val filesystemFiles = Files
              .walk(filesystemBasePath)
              .filter(Files.isRegularFile(_))
              .toArray
              .map(_.asInstanceOf[java.nio.file.Path])

            assert(filesystemFiles.length == 1, s"Expected 1 file in filesystem, found ${filesystemFiles.length}")

            val fileSize = filesystemFiles.head.toFile.length()
            assert(fileSize == 2048, s"Expected file size 2048, found $fileSize")

          } finally {
            // Cleanup filesystem directory if it exists
            if (Files.exists(filesystemBasePath)) {
              Files.walk(filesystemBasePath)                 // `.walk` is depth-first root → leaves
                .sorted(java.util.Comparator.reverseOrder()) // Reverse to delete leaves → root
                .forEach(Files.delete(_))
            }
          }
        }
      }
    }
  }
}
