/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.rest

import java.io.ByteArrayInputStream

import org.junit.Test
import org.orbeon.dom.{Document, DocumentFactory}
import org.orbeon.oxf.fr.persistence._
import org.orbeon.oxf.fr.persistence.db._
import org.orbeon.oxf.fr.persistence.relational.Provider._
import org.orbeon.oxf.fr.persistence.relational._
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, Logging}
import org.orbeon.oxf.xml.Dom4j
import org.orbeon.oxf.xml.Dom4j.elemToDocument
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.scalatest.junit.AssertionsForJUnit

import scala.util.Random
import scala.xml.Elem

/**
 * Test the persistence API (for now specifically the MySQL persistence layer), in particular:
 *      - Versioning
 *      - Drafts (used for autosave)
 *      - Permissions
 *      - Large XML documents and binary attachments
 */
class RestApiTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport with Logging {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[RestApiTest]), true)
  val AllOperations = Set("create", "read", "update", "delete")

  private def crudURLPrefix(provider: Provider) = s"crud/${provider.name}/my-form/"
  private def metadataURL  (provider: Provider) = s"form/${provider.name}/my-form"

  private def withOrbeonTables[T](message: String)(block: (java.sql.Connection, Provider) ⇒ T): Unit = {
    withDebug(message) {
      ProvidersTestedAutomatically.foreach { provider ⇒
        withDebug("on database", List("provider" → provider.name)) {
          Connect.withNewDatabase(provider) { connection ⇒
            val statement = connection.createStatement
            try {
              // Create tables
              val sql = provider match {
                case MySQL      ⇒ "mysql-4_6.sql"
                case PostgreSQL ⇒ "postgresql-4_8.sql"
              }
              val createDDL = SQL.read(sql)
              withDebug("creating tables") { SQL.executeStatements(provider, statement, createDDL) }
              withDebug("run actual test") { block(connection, provider) }
            } finally {
              // Clean-up database dropping tables
              for (tableName ← Connect.getTableNames(provider, connection))
                statement.executeUpdate(s"DROP TABLE $tableName")
            }
          }
        }
      }
    }
  }

  /**
   * Test new form versioning introduced in 4.5, for form definitions.
   */
  @Test def formDefinitionVersionTest(): Unit = {
    withOrbeonTables("form definition") { (connection, provider) ⇒

      val FormURL = crudURLPrefix(provider) + "form/form.xhtml"

      // First time we put with "latest" (AKA unspecified)
      val first = HttpRequest.XML(<gaga1/>)
      HttpAssert.put(FormURL, Unspecified, first, 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody (first, Set.empty, Some(1)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody (first, Set.empty, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedCode(404))
      HttpAssert.del(FormURL, Specific(2), 404)

      // Put again with "latest" (AKA unspecified) updates the current version
      val second = <gaga2/>
      HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(second), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpRequest.XML(second), Set.empty, Some(1)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(second), Set.empty, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedCode(404))

      // Put with "next" to get two versions
      val third = <gaga3/>
      HttpAssert.put(FormURL, Next, HttpRequest.XML(third), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpRequest.XML(second), Set.empty, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedBody(HttpRequest.XML(third),  Set.empty, Some(2)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(third),  Set.empty, Some(2)))
      HttpAssert.get(FormURL, Specific(3), HttpAssert.ExpectedCode(404))

      // Put a specific version
      val fourth = <gaga4/>
      HttpAssert.put(FormURL, Specific(1), HttpRequest.XML(fourth), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpRequest.XML(fourth), Set.empty, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedBody(HttpRequest.XML(third),  Set.empty, Some(2)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(third),  Set.empty, Some(2)))
      HttpAssert.get(FormURL, Specific(3), HttpAssert.ExpectedCode(404))

      // Delete the latest version
      HttpAssert.del(FormURL, Unspecified, 204)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpRequest.XML(fourth), Set.empty, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedCode(410))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(fourth), Set.empty, Some(1)))

      // After a delete the version number is reused
      val fifth = <gaga5/>
      HttpAssert.put(FormURL, Next, HttpRequest.XML(fifth), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpRequest.XML(fourth), Set.empty, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedBody(HttpRequest.XML(fifth),  Set.empty, Some(2)))
      HttpAssert.get(FormURL, Specific(3), HttpAssert.ExpectedCode(404))
    }
  }

  /**
   * Test new form versioning introduced in 4.5, for form data
   */
  @Test def formDataVersionTest(): Unit = {
    withOrbeonTables("form data version") { (connection, provider) ⇒
      val FirstDataURL = crudURLPrefix(provider) + "data/123/data.xml"

      // Storing for specific form version
      val first = <gaga1/>
      HttpAssert.put(FirstDataURL, Specific(1), HttpRequest.XML(first), 201)
      HttpAssert.get(FirstDataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(first), AllOperations, Some(1)))
      HttpAssert.get(FirstDataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(first), AllOperations, Some(1)))
      HttpAssert.del(FirstDataURL, Unspecified, 204)
      HttpAssert.get(FirstDataURL, Unspecified, HttpAssert.ExpectedCode(410))

      // Don't allow unspecified version for create
      HttpAssert.put(FirstDataURL, Unspecified       , HttpRequest.XML(first), 400)
      HttpAssert.put(FirstDataURL, Specific(1)       , HttpRequest.XML(first), 201)

      // Allow unspecified or correct version for update
      HttpAssert.put(FirstDataURL, Unspecified      , HttpRequest.XML(first), 201)
      HttpAssert.put(FirstDataURL, Specific(1)      , HttpRequest.XML(first), 201)

      // But don't allow incorrect version for update
      HttpAssert.put(FirstDataURL, Specific(2)      , HttpRequest.XML(first), 400)

      // Fail with next/for document
      HttpAssert.put(FirstDataURL, Next              , HttpRequest.XML(first), 400)
      HttpAssert.put(FirstDataURL, ForDocument("123"), HttpRequest.XML(first), 400)
    }
  }

  /**
   * Get form definition corresponding to a document
   */
  @Test def formForDataTest(): Unit = {
    withOrbeonTables("form data") { (connection, provider) ⇒
      val FormURL       = crudURLPrefix(provider) + "form/form.xhtml"
      val FirstDataURL  = crudURLPrefix(provider) + "data/123/data.xml"
      val SecondDataURL = crudURLPrefix(provider) + "data/456/data.xml"
      val first         = <gaga1/>
      val second        = <gaga2/>
      val data          = <gaga/>

      HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(first), 201)
      HttpAssert.put(FormURL, Next, HttpRequest.XML(second), 201)
      HttpAssert.put(FirstDataURL, Specific(1), HttpRequest.XML(data), 201)
      HttpAssert.put(SecondDataURL, Specific(2), HttpRequest.XML(data), 201)
      HttpAssert.get(FormURL, ForDocument("123"), HttpAssert.ExpectedBody(HttpRequest.XML(first), Set.empty, Some(1)))
      HttpAssert.get(FormURL, ForDocument("456"), HttpAssert.ExpectedBody(HttpRequest.XML(second), Set.empty, Some(2)))
      HttpAssert.get(FormURL, ForDocument("789"), HttpAssert.ExpectedCode(404))
    }
  }

  import Permissions._

  private def formDefinitionWithPermissions(permissions: Permissions): Elem =
    <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
      <xh:head>
        <xf:model id="fr-form-model">
          <xf:instance id="fr-form-metadata">
            <metadata>
              { serialize(permissions).getOrElse("") }
            </metadata>
          </xf:instance>
        </xf:model>
      </xh:head>
    </xh:html>

  /**
   * Data permissions
   */
  @Test def permissionsTest(): Unit = {

    withOrbeonTables("permissions") { (connection, provider) ⇒

      val FormURL = crudURLPrefix(provider) + "form/form.xhtml"
      val data    = <data/>
      val clerk   = Some(HttpRequest.Credentials("tom", Set("clerk"  ), "clerk"))
      val manager = Some(HttpRequest.Credentials("jim", Set("manager"), "manager"))
      val admin   = Some(HttpRequest.Credentials("tim", Set("admin"  ), "admin"))

      {
        val DataURL = crudURLPrefix(provider) + "data/123/data.xml"

        // Anonymous: no permission defined
        HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(formDefinitionWithPermissions(None)), 201)
        HttpAssert.put(DataURL, Specific(1), HttpRequest.XML(data), 201)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(data), AllOperations, Some(1)))

        // Anonymous: create and read
        HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("read", "create")))))), 201)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(data), Set("create", "read"), Some(1)))

        // Anonymous: just create, then can't read data
        HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("create")))))), 201)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedCode(403))
      }
      {
        val DataURL = crudURLPrefix(provider) + "data/456/data.xml"

        // More complex permissions based on roles
        HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(formDefinitionWithPermissions(Some(Seq(
          Permission(Anyone,          Set("create")),
          Permission(Role("clerk"),   Set("read")),
          Permission(Role("manager"), Set("read update")),
          Permission(Role("admin"),   Set("read update delete"))
        )))), 201)
        HttpAssert.put(DataURL, Specific(1), HttpRequest.XML(data), 201)

        // Everyone can read
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedCode(403))
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(data), Set("create", "read"), Some(1)), clerk)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(data), Set("create", "read", "update"), Some(1)), manager)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpRequest.XML(data), Set("create", "read", "update", "delete"), Some(1)), admin)

        // Only managers and admins can update
        HttpAssert.put(DataURL, Unspecified, HttpRequest.XML(data), 403)
        HttpAssert.put(DataURL, Unspecified, HttpRequest.XML(data), 403, clerk)
        HttpAssert.put(DataURL, Unspecified, HttpRequest.XML(data), 201, manager)
        HttpAssert.put(DataURL, Unspecified, HttpRequest.XML(data), 201, admin)

        // Only admins can delete
        HttpAssert.del(DataURL, Unspecified, 403)
        HttpAssert.del(DataURL, Unspecified, 403, clerk)
        HttpAssert.del(DataURL, Unspecified, 403, manager)
        HttpAssert.del(DataURL, Unspecified, 204, admin)

        // Status code when deleting non-existent data depends on permissions
        HttpAssert.del(DataURL, Unspecified, 403)
        HttpAssert.del(DataURL, Unspecified, 404, clerk)
        HttpAssert.del(DataURL, Unspecified, 404, manager)
        HttpAssert.del(DataURL, Unspecified, 404, admin)
      }
    }
  }

  // Try uploading files of 1 KB, 1 MB
  @Test def attachmentsTest(): Unit = {
    withOrbeonTables("attachments") { (connection, provider) ⇒
      for ((size, position) ← Seq(1024, 1024*1024).zipWithIndex) {
        val bytes =  new Array[Byte](size) |!> Random.nextBytes |> HttpRequest.Binary
        val url = crudURLPrefix(provider) + "data/123/file" + position.toString
        HttpAssert.put(url, Specific(1), bytes, 201)
        HttpAssert.get(url, Unspecified, HttpAssert.ExpectedBody(bytes, AllOperations, Some(1)))
      }
    }
  }

  // Try uploading files of 1 KB, 1 MB
  @Test def largeXMLDocumentsTest(): Unit = {
    withOrbeonTables("large XML documents") { (connection, provider) ⇒
      for ((size, position) ← Seq(1024, 1024*1024).zipWithIndex) {
        val string = new Array[Char](size)
        for (i ← 0 to size - 1) string(i) = Random.nextPrintableChar()
        val text = DocumentFactory.createText(new String(string))
        val element = DocumentFactory.createElement("gaga") |!> (_.add(text))
        val document = DocumentFactory.createDocument |!> (_.add(element)) |> HttpRequest.XML
        val url = crudURLPrefix(provider) + s"data/$position/data.xml"
        HttpAssert.put(url, Specific(1), document, 201)
        HttpAssert.get(url, Unspecified, HttpAssert.ExpectedBody(document, AllOperations, Some(1)))
      }
    }
  }

  @Test def draftsTest(): Unit = {
    withOrbeonTables("drafts") { (connection, provider) ⇒
      // Draft and non-draft are different
      val first  = HttpRequest.XML(<gaga1/>)
      val second = HttpRequest.XML(<gaga2/>)
      val DataURL  = crudURLPrefix(provider) + "data/123/data.xml"
      val DraftURL = crudURLPrefix(provider) + "draft/123/data.xml"
      HttpAssert.put(DataURL,  Specific(1), first, 201)
      HttpAssert.put(DraftURL, Unspecified, second, 201)
      HttpAssert.get(DataURL,  Unspecified, HttpAssert.ExpectedBody(first, AllOperations, Some(1)))
      HttpAssert.get(DraftURL, Unspecified, HttpAssert.ExpectedBody(second, AllOperations, Some(1)))
    }
  }

  @Test def extractMetadata(): Unit =
    withOrbeonTables("extract metadata") { (connection, provider) ⇒

      val FormURL     = crudURLPrefix(provider) + "form/form.xhtml"
      val MetadataURL = metadataURL(provider)

      HttpAssert.put(FormURL, Unspecified, HttpRequest.XML(formDefinitionWithPermissions(Some(Seq(Permission(Anyone, Set("read", "create")))))), 201)

      val expectedBody: Document =
        <forms>
          <form operations="read create">
            <permissions>
              <permission operations="read create"/>
            </permissions>
          </form>
        </forms>

      val (resultCode, _, resultBodyTry) = HttpRequest.get(MetadataURL, Unspecified, None)

      assert(resultCode === 200)

      def filterResultBody(bytes: Array[Byte]) = {

        val doc = Dom4jUtils.readDom4j(new ByteArrayInputStream(bytes))

        for {
          formElem             ← Dom4j.elements(doc.getRootElement, "form")
          lastModifiedTimeElem ← Dom4j.elements(formElem, "last-modified-time")
        } locally {
          lastModifiedTimeElem.detach()
        }

        doc
      }

      assertXMLDocumentsIgnoreNamespacesInScope(filterResultBody(resultBodyTry.get), expectedBody)
    }
}
