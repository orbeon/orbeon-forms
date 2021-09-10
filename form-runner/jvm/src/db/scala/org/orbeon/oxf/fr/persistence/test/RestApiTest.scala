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
package org.orbeon.oxf.fr.persistence.test

import java.io.ByteArrayInputStream

import org.junit.Test
import org.orbeon.dom
import org.orbeon.dom.Document
import org.orbeon.oxf.externalcontext.{Credentials, Organization, ParametrizedRole, SimpleRole, UserAndGroup}
import org.orbeon.oxf.fr.permission.Operation.{Create, Delete, Read, Update}
import org.orbeon.oxf.fr.permission._
import org.orbeon.oxf.fr.persistence.db._
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Version._
import org.orbeon.oxf.fr.workflow.definitions20201.Stage
import org.orbeon.oxf.test.{ResourceManagerTestBase, XMLSupport}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory, Logging, NetUtils}
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.oxf.xml.dom.IOSupport
import org.scalatestplus.junit.AssertionsForJUnit

import scala.util.Random


/**
 * Test the persistence API (for now specifically the MySQL persistence layer), in particular:
 *      - Versioning
 *      - Drafts (used for autosave)
 *      - Permissions
 *      - Large XML documents and binary attachments
 */
class RestApiTest extends ResourceManagerTestBase with AssertionsForJUnit with XMLSupport with Logging {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[RestApiTest]), true)
  private implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

  val AllOperations       = SpecificOperations(List(Create, Read, Update, Delete))
  val CanCreate           = SpecificOperations(List(Create))
  val CanRead             = SpecificOperations(List(Read))
  val CanUpdate           = SpecificOperations(List(Update))
  val CanCreateRead       = Operations.combine(CanCreate, CanRead)
  val CanCreateReadUpdate = Operations.combine(CanCreateRead, CanUpdate)
  val FormName = "my-form"

  val AnyoneCanCreateAndRead = DefinedPermissions(List(Permission(Nil, SpecificOperations(List(Read, Create)))))
  val AnyoneCanCreate        = DefinedPermissions(List(Permission(Nil, SpecificOperations(List(Create)))))

  /**
   * Test form versioning for form definitions.
   */
  @Test def formDefinitionVersionTest(): Unit =
    Connect.withOrbeonTables("form definition") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val FormURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"

      // First time we put with "latest" (AKA unspecified)
      val first = HttpCall.XML(<gaga1/>.toDocument)
      HttpAssert.put(FormURL, Unspecified, first, 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody (first, Operations.None, Some(1)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody (first, Operations.None, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedCode(404))
      HttpAssert.del(FormURL, Specific(2), 404)

      // Put again with "latest" (AKA unspecified) updates the current version
      val second = <gaga2/>.toDocument
      HttpAssert.put(FormURL, Unspecified, HttpCall.XML(second), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(1)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedCode(404))

      // Put with "next" to get two versions
      val third = <gaga3/>.toDocument
      HttpAssert.put(FormURL, Next, HttpCall.XML(third), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
      HttpAssert.get(FormURL, Specific(3), HttpAssert.ExpectedCode(404))

      // Put a specific version
      val fourth = <gaga4/>.toDocument
      HttpAssert.put(FormURL, Specific(1), HttpCall.XML(fourth), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
      HttpAssert.get(FormURL, Specific(3), HttpAssert.ExpectedCode(404))

      // Delete the latest version
      HttpAssert.del(FormURL, Unspecified, 204)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedCode(410))
      HttpAssert.get(FormURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))

      // After a delete the version number is reused
      val fifth = <gaga5/>.toDocument
      HttpAssert.put(FormURL, Next, HttpCall.XML(fifth), 201)
      HttpAssert.get(FormURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))
      HttpAssert.get(FormURL, Specific(2), HttpAssert.ExpectedBody(HttpCall.XML(fifth),  Operations.None, Some(2)))
      HttpAssert.get(FormURL, Specific(3), HttpAssert.ExpectedCode(404))
    }

  /**
   * Test form versioning for form data
   */
  @Test def formDataVersionStageTest(): Unit = {
    Connect.withOrbeonTables("form data version") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val FirstDataURL = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"

      // Storing for specific form version
      val first = <gaga1/>.toDocument
      val myStage = Some(Stage("my-stage", ""))
      HttpAssert.put(FirstDataURL, Specific(1), HttpCall.XML(first), 201)
      HttpAssert.get(FirstDataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(first), AllOperations, Some(1)))
      HttpAssert.put(FirstDataURL, Specific(1), HttpCall.XML(first), expectedCode = 201, stage = myStage)
      HttpAssert.get(FirstDataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(first), AllOperations, Some(1), stage = myStage))
      HttpAssert.del(FirstDataURL, Unspecified, 204)
      HttpAssert.get(FirstDataURL, Unspecified, HttpAssert.ExpectedCode(410))

      // Don't allow unspecified version for create
      HttpAssert.put(FirstDataURL, Unspecified       , HttpCall.XML(first), 400)
      HttpAssert.put(FirstDataURL, Specific(1)       , HttpCall.XML(first), 201)

      // Allow unspecified or correct version for update
      HttpAssert.put(FirstDataURL, Unspecified      , HttpCall.XML(first), 201)
      HttpAssert.put(FirstDataURL, Specific(1)      , HttpCall.XML(first), 201)

      // But don't allow incorrect version for update
      HttpAssert.put(FirstDataURL, Specific(2)      , HttpCall.XML(first), 400)

      // Fail with next/for document
      HttpAssert.put(FirstDataURL, Next              , HttpCall.XML(first), 400)
      HttpAssert.put(FirstDataURL, ForDocument("123"), HttpCall.XML(first), 400)
    }
  }

  /**
   * Get form definition corresponding to a document
   */
  @Test def formForDataTest(): Unit = {
    Connect.withOrbeonTables("form data") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val FormURL       = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
      val FirstDataURL  = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
      val SecondDataURL = HttpCall.crudURLPrefix(provider) + "data/456/data.xml"
      val first         = buildFormDefinition(provider, permissions = UndefinedPermissions, title = Some("first"))
      val second        = buildFormDefinition(provider, permissions = UndefinedPermissions, title = Some("second"))
      val data          = <gaga/>.toDocument

      HttpAssert.put(FormURL      , Unspecified, HttpCall.XML(first) , 201)
      HttpAssert.put(FormURL      , Next       , HttpCall.XML(second), 201)
      HttpAssert.put(FirstDataURL , Specific(1), HttpCall.XML(data)  , 201)
      HttpAssert.put(SecondDataURL, Specific(2), HttpCall.XML(data)  , 201)
      HttpAssert.get(FormURL, ForDocument("123"), HttpAssert.ExpectedBody(HttpCall.XML(first) , Operations.None, Some(1)))
      HttpAssert.get(FormURL, ForDocument("456"), HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(2)))
      HttpAssert.get(FormURL, ForDocument("789"), HttpAssert.ExpectedCode(404))
    }
  }


  private def buildFormDefinition(
    provider     : Provider,
    permissions  : Permissions,
    title        : Option[String] = None
  ): Document =
    <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
      <xh:head>
        <xf:model id="fr-form-model">
          <xf:instance id="fr-form-metadata">
            <metadata>
              <application-name>{provider.entryName}</application-name>
              <form-name>{FormName}</form-name>
              <title xml:lang="en">{title.getOrElse("")}</title>
              { PermissionsXML.serialize(permissions).getOrElse("") }
            </metadata>
          </xf:instance>
        </xf:model>
      </xh:head>
    </xh:html>.toDocument

  /**
   * Data permissions
   */
  @Test def permissionsTest(): Unit = {

    Connect.withOrbeonTables("permissions") { (connection, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
      val data    = <data/>.toDocument
      val guest   = None
      val clerk   = Some(Credentials(UserAndGroup("tom", Some("clerk")  ), List(SimpleRole("clerk"  )), Nil))
      val manager = Some(Credentials(UserAndGroup("jim", Some("manager")), List(SimpleRole("manager")), Nil))
      val admin   = Some(Credentials(UserAndGroup("tim", Some("admin")  ), List(SimpleRole("admin"  )), Nil))

      {
        val DataURL = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"

        // Anonymous: no permission defined
        HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, UndefinedPermissions)), 201)
        HttpAssert.put(DataURL, Specific(1), HttpCall.XML(data), 201)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), AllOperations, Some(1)))

        // Anonymous: create and read
        HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, AnyoneCanCreateAndRead)), 201)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(List(Create, Read)), Some(1)))

        // Anonymous: just create, then can't read data
        HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, AnyoneCanCreate)), 201)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedCode(403))
      }
      {
        val DataURL = HttpCall.crudURLPrefix(provider) + "data/456/data.xml"

        // More complex permissions based on roles
        HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, DefinedPermissions(List(
          Permission(Nil                              , SpecificOperations(List(Create))),
          Permission(List(RolesAnyOf(List("clerk"  ))), SpecificOperations(List(Read))),
          Permission(List(RolesAnyOf(List("manager"))), SpecificOperations(List(Read, Update))),
          Permission(List(RolesAnyOf(List("admin"  ))), SpecificOperations(List(Read, Update, Delete)))
        )))), 201)
        HttpAssert.put(DataURL, Specific(1), HttpCall.XML(data), 201)

        // Check who can read
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedCode(403)                                                                         , guest)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(List(Create, Read))                , Some(1)), clerk)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(List(Create, Read, Update))        , Some(1)), manager)
        HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(List(Create, Read, Update, Delete)), Some(1)), admin)

        // Only managers and admins can update
        HttpAssert.put(DataURL, Unspecified, HttpCall.XML(data), 403, guest)
        HttpAssert.put(DataURL, Unspecified, HttpCall.XML(data), 403, clerk)
        HttpAssert.put(DataURL, Unspecified, HttpCall.XML(data), 201, manager)
        HttpAssert.put(DataURL, Unspecified, HttpCall.XML(data), 201, admin)

        // Only admins can delete
        HttpAssert.del(DataURL, Unspecified, 403, guest)
        HttpAssert.del(DataURL, Unspecified, 403, clerk)
        HttpAssert.del(DataURL, Unspecified, 403, manager)
        HttpAssert.del(DataURL, Unspecified, 204, admin)

        // Status code when deleting non-existent data depends on permissions
        HttpAssert.del(DataURL, Unspecified, 403, guest)
        HttpAssert.del(DataURL, Unspecified, 404, clerk)
        HttpAssert.del(DataURL, Unspecified, 404, manager)
        HttpAssert.del(DataURL, Unspecified, 404, admin)
      }
    }
  }

  @Test def organizationPermissions(): Unit =
    Connect.withOrbeonTables("Organization-based permissions") { (connection, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val formURL   = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"

      // Users
      val c1User   = Some(Credentials(UserAndGroup("c1User"  , None), Nil, List(Organization(List("a", "b", "c")))))
      val c2User   = Some(Credentials(UserAndGroup("c2User"  , None), Nil, List(Organization(List("a", "b", "c")))))
      val cManager = Some(Credentials(UserAndGroup("cManager", None), List(ParametrizedRole("manager", "c")), Nil))
      val bManager = Some(Credentials(UserAndGroup("cManager", None), List(ParametrizedRole("manager", "b")), Nil))
      val dManager = Some(Credentials(UserAndGroup("cManager", None), List(ParametrizedRole("manager", "d")), Nil))

      val dataURL   = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
      val dataBody  = HttpCall.XML(<gaga/>.toDocument)

      // User can read their own data, as well as their managers
      HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, DefinedPermissions(List(
        Permission(Nil                              , SpecificOperations(List(Create))),
        Permission(List(Owner)                      , SpecificOperations(List(Read, Update))),
        Permission(List(RolesAnyOf(List("clerk")))  , SpecificOperations(List(Read))),
        Permission(List(RolesAnyOf(List("manager"))), SpecificOperations(List(Read, Update)))
      )))), 201)

      // Data initially created by sfUserA
      HttpAssert.put(dataURL, Specific(1), dataBody, 201, c1User)
      // Owner can read their own data
      HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(dataBody, CanCreateReadUpdate, Some(1)), c1User)
      // Other users can't read the data
      HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedCode(403)                                   , c2User)
      // Managers of the user up the organization structure can read the data
      HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(dataBody, CanCreateReadUpdate, Some(1)), cManager)
      HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(dataBody, CanCreateReadUpdate, Some(1)), bManager)
      // Other managers can't read the data
      HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedCode(403)                                   , dManager)
    }

  // Try uploading files of 1 KB, 1 MB
  @Test def attachmentsTest(): Unit = {
    Connect.withOrbeonTables("attachments") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      for ((size, position) <- Seq(1024, 1024*1024).zipWithIndex) {
        val bytes =  new Array[Byte](size) |!> Random.nextBytes |> HttpCall.Binary
        val url = HttpCall.crudURLPrefix(provider) + "data/123/file" + position.toString
        HttpAssert.put(url, Specific(1), bytes, 201)
        HttpAssert.get(url, Unspecified, HttpAssert.ExpectedBody(bytes, AllOperations, Some(1)))
      }
    }
  }

  // Try uploading files of 1 KB, 1 MB
  @Test def largeXMLDocumentsTest(): Unit = {
    Connect.withOrbeonTables("large XML documents") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      for ((size, position) <- Seq(1024, 1024*1024).zipWithIndex) {

        val charArray = new Array[Char](size)
        for (i <- 0 until size)
          charArray(i) = Random.nextPrintableChar()

        val text    = dom.Text(new String(charArray))
        val element = dom.Element("gaga")   |!> (_.add(text))
        val xmlBody = dom.Document(element) |> HttpCall.XML

        val url = HttpCall.crudURLPrefix(provider) + s"data/$position/data.xml"

        HttpAssert.put(url, Specific(1), xmlBody, 201)
        HttpAssert.get(url, Unspecified, HttpAssert.ExpectedBody(xmlBody, AllOperations, Some(1)))
      }
    }
  }

  @Test def draftsTest(): Unit = {
    Connect.withOrbeonTables("drafts") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      // Draft and non-draft are different
      val first  = HttpCall.XML(<gaga1/>.toDocument)
      val second = HttpCall.XML(<gaga2/>.toDocument)
      val DataURL  = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
      val DraftURL = HttpCall.crudURLPrefix(provider) + "draft/123/data.xml"
      HttpAssert.put(DataURL,  Specific(1), first, 201)
      HttpAssert.put(DraftURL, Unspecified, second, 201)
      HttpAssert.get(DataURL,  Unspecified, HttpAssert.ExpectedBody(first, AllOperations, Some(1)))
      HttpAssert.get(DraftURL, Unspecified, HttpAssert.ExpectedBody(second, AllOperations, Some(1)))
    }
  }

  @Test def extractMetadata(): Unit =
    Connect.withOrbeonTables("extract metadata") { (_, provider) =>

      implicit val externalContext = NetUtils.getExternalContext

      val currentFormURL        = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
      val currentMetadataURL    = HttpCall.metadataURL(provider)
      val formDefinition        = buildFormDefinition(provider, AnyoneCanCreateAndRead)

      HttpAssert.put(currentFormURL, Unspecified, HttpCall.XML(formDefinition), 201)

      val expectedBody =
        <forms>
            <form operations="read create">
                <application-name>{provider.entryName}</application-name>
                <form-name>my-form</form-name>
                <form-version>1</form-version>
                <title xml:lang="en"/>
                <permissions>
                    <permission operations="read create"/>
                </permissions>
            </form>
        </forms>.toDocument

      val (resultCode, _, resultBodyTry) = HttpCall.get(currentMetadataURL, Unspecified, None)

      assert(resultCode === 200)

      def filterResultBody(bytes: Array[Byte]) = {

        val doc = IOSupport.readDom4j(new ByteArrayInputStream(bytes))

        for {
          formElem             <- doc.getRootElement.elements("form")
          lastModifiedTimeElem <- formElem.elements("last-modified-time")
        } locally {
          lastModifiedTimeElem.detach()
        }

        doc
      }

      assertXMLDocumentsIgnoreNamespacesInScope(filterResultBody(resultBodyTry.get), expectedBody)
    }
}
