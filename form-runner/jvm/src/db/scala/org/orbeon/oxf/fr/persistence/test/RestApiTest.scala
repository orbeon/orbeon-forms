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

import cats.implicits.catsSyntaxOptionId
import org.orbeon.dom
import org.orbeon.dom.Document
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.externalcontext.*
import org.orbeon.oxf.fr.Version.*
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.permission.Operation.{Create, Delete, Read, Update}
import org.orbeon.oxf.fr.persistence.attachments.FilesystemCRUD
import org.orbeon.oxf.fr.persistence.attachments.S3CRUD.providerProperty
import org.orbeon.oxf.fr.persistence.db.*
import org.orbeon.oxf.fr.persistence.http.HttpCall.DefaultFormName
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Provider.{MySQL, PostgreSQL, SQLite}
import org.orbeon.oxf.fr.s3.S3
import org.orbeon.oxf.fr.workflow.definitions20201.Stage
import org.orbeon.oxf.fr.{AppForm, FormOrData, S3Tag, S3Test}
import org.orbeon.oxf.http.{Headers, HttpRange, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport, XMLSupport}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, SecureUtils}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.io.IOUtils.useAndClose
import org.scalatest.funspec.AnyFunSpecLike

import java.io.ByteArrayInputStream
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Random


/**
 * Test the persistence API (for now specifically the MySQL persistence layer), in particular:
 *      - Versioning
 *      - Drafts (used for autosave)
 *      - Permissions
 *      - Large XML documents and binary attachments
 */
class RestApiTest
  extends DocumentTestBase
     with AnyFunSpecLike
     with ResourceManagerSupport
     with XMLSupport
     with XFormsSupport {

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[RestApiTest]), true)

  private val CanCreate           = SpecificOperations(Set(Create))
  private val CanRead             = SpecificOperations(Set(Read))
  private val CanUpdate           = SpecificOperations(Set(Update))
  private val CanCreateRead       = Operations.combine(CanCreate, CanRead)
  private val CanCreateReadUpdate = Operations.combine(CanCreateRead, CanUpdate)

  private val FilesystemAttachmentsFormName = "filesystem-attachments-form"
  private val S3AttachmentsFormName         = "s3-attachments-form"

  private val AnyoneCanCreateAndRead = Permissions.Defined(List(Permission(Nil, SpecificOperations(Set(Read, Create)))))
  private val AnyoneCanCreate        = Permissions.Defined(List(Permission(Nil, SpecificOperations(Set(Create)))))

  private def computeHash(data: Array[Byte]): (String, String) = {
    val hashAlgorithm = SecureUtils.getHashAlgorithm
    val hashValue     = useAndClose(new ByteArrayInputStream(data))(SecureUtils.digestStream(_))
    (hashAlgorithm, hashValue)
  }

  private def createForm(provider: Provider, formName: String = DefaultFormName)(implicit safeRequestCtx: SafeRequestContext): Unit = {
    val form = HttpCall.XML(buildFormDefinition(provider, permissions = Permissions.Undefined, formName, title = Some("first")))
    val formURL = HttpCall.crudURLPrefix(provider, formName) + "form/form.xhtml"
    HttpAssert.put(formURL, Unspecified, form, StatusCode.Created)
  }

  private def buildFormDefinition(
    provider    : Provider,
    permissions : Permissions,
    formName    : String = DefaultFormName,
    title       : Option[String] = None
  ): Document =
    <xh:html xmlns:xh="http://www.w3.org/1999/xhtml" xmlns:xf="http://www.w3.org/2002/xforms">
      <xh:head>
        <xf:model id="fr-form-model">
          <xf:instance id="fr-form-metadata">
            <metadata>
              <application-name>{provider.entryName}</application-name>
              <form-name>{formName}</form-name>
              <title xml:lang="en">{title.getOrElse("")}</title>
              { PermissionsXML.serialize(permissions, normalized = false).getOrElse("") }
            </metadata>
          </xf:instance>
        </xf:model>
      </xh:head>
    </xh:html>.toDocument

  describe("Form definition version") {
    it("must pass basic CRUD operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (_, provider) =>

          val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"

          // First time we put with "latest" (AKA unspecified)
          val first = HttpCall.XML(<gaga1/>.toDocument)
          HttpAssert.put(formURL, Unspecified, first, StatusCode.Created)
          HttpAssert.get(formURL, Specific(1), HttpAssert.ExpectedBody(first, Operations.None, Some(1)))
          HttpAssert.get(formURL, Unspecified, HttpAssert.ExpectedBody(first, Operations.None, Some(1)))
          HttpAssert.get(formURL, Specific(2), HttpAssert.ExpectedCode(StatusCode.NotFound))
          HttpAssert.del(formURL, Specific(2), StatusCode.NotFound)

          // Put again with "latest" (AKA unspecified) updates the current version
          val second = <gaga2/>.toDocument
          HttpAssert.put(formURL, Unspecified, HttpCall.XML(second), StatusCode.NoContent)
          HttpAssert.get(formURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(1)))
          HttpAssert.get(formURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(1)))
          HttpAssert.get(formURL, Specific(2), HttpAssert.ExpectedCode(StatusCode.NotFound))

          // Put with "next" to get two versions
          val third = <gaga3/>.toDocument
          HttpAssert.put(formURL, Next, HttpCall.XML(third), StatusCode.Created)
          HttpAssert.get(formURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(1)))
          HttpAssert.get(formURL, Specific(2), HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
          HttpAssert.get(formURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
          HttpAssert.get(formURL, Specific(3), HttpAssert.ExpectedCode(StatusCode.NotFound))

          // Put a specific version
          val fourth = <gaga4/>.toDocument
          HttpAssert.put(formURL, Specific(1), HttpCall.XML(fourth), StatusCode.NoContent)
          HttpAssert.get(formURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))
          HttpAssert.get(formURL, Specific(2), HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
          HttpAssert.get(formURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(third),  Operations.None, Some(2)))
          HttpAssert.get(formURL, Specific(3), HttpAssert.ExpectedCode(StatusCode.NotFound))

          // Delete the latest version
          HttpAssert.del(formURL, Unspecified, StatusCode.NoContent)
          HttpAssert.get(formURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))
          HttpAssert.get(formURL, Specific(2), HttpAssert.ExpectedCode(StatusCode.Gone))
          HttpAssert.get(formURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))

          // After a delete the version number is reused
          val fifth = <gaga5/>.toDocument
          HttpAssert.put(formURL, Next, HttpCall.XML(fifth), StatusCode.Created)
          HttpAssert.get(formURL, Specific(1), HttpAssert.ExpectedBody(HttpCall.XML(fourth), Operations.None, Some(1)))
          HttpAssert.get(formURL, Specific(2), HttpAssert.ExpectedBody(HttpCall.XML(fifth),  Operations.None, Some(2)))
          HttpAssert.get(formURL, Specific(3), HttpAssert.ExpectedCode(StatusCode.NotFound))
        }
      }
    }
  }

  describe("Form data version and stage") {
    it("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form data version") { (_, provider) =>

          val dataURL = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
          val data    = HttpCall.XML(<gaga1/>.toDocument)

          // In PE, encryptDataIfNecessary is implemented and tries to access the form definition, which causes
          // StatusCode.NotFound. In CE, encryptDataIfNecessary is not implemented, so StatusCode.Created is returned.
          HttpAssert.put(dataURL, Specific(1), data, if (Version.isPE) StatusCode.NotFound else StatusCode.Created)
          // TODO: return `StatusCode.Forbidden` instead as reason if missing permissions!

          // 2023-04-18: Following changes to the persistence proxy: `PUT`ting data for a non-existing form definition
          // used to not fail, for some reason. Now we enforce the existence of a form definition so we can check
          // permissions. Hopefully, this is reasonable.
          createForm(provider)

          // Storing for specific form version
          val myStage = Some(Stage("my-stage", ""))
          // During the previous PUT, in PE, encryptDataIfNecessary didn't find the form definition and didn't store the
          // form data, so this time the PUT returns Created. In CE, encryptDataIfNecessary didn't fail (no
          // implementation) and the form data was stored, so the current PUT returns NoContent.
          HttpAssert.put(dataURL, Specific(1), data, if (Version.isPE) StatusCode.Created else StatusCode.NoContent)
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(data, AnyOperation, Some(1)))
          HttpAssert.put(dataURL, Specific(1), data, StatusCode.NoContent, stage = myStage)
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(data, AnyOperation, Some(1), stage = myStage))
          HttpAssert.del(dataURL, Unspecified, StatusCode.NoContent)
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedCode(StatusCode.Gone))

          // Don't allow unspecified version for create
          HttpAssert.put(dataURL, Unspecified       , data, StatusCode.BadRequest)
          HttpAssert.put(dataURL, Specific(1)       , data, StatusCode.Created)

          // Allow unspecified or correct version for update
          HttpAssert.put(dataURL, Unspecified      , data, StatusCode.NoContent)
          HttpAssert.put(dataURL, Specific(1)      , data, StatusCode.NoContent)

          // But don't allow incorrect version for update
          HttpAssert.put(dataURL, Specific(2)      , data, StatusCode.BadRequest)

          // Fail with next/for document
          HttpAssert.put(dataURL, Next                               , data, StatusCode.BadRequest)
          HttpAssert.put(dataURL, ForDocument("123", isDraft = false), data, StatusCode.BadRequest)
        }
      }
    }
  }

  describe("Form definition corresponding to a document") {
    it("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form data") { (_, provider) =>

          val formURL       = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
          val firstDataURL  = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
          val secondDataURL = HttpCall.crudURLPrefix(provider) + "data/456/data.xml"
          val first         = buildFormDefinition(provider, permissions = Permissions.Undefined, title = Some("first"))
          val second        = buildFormDefinition(provider, permissions = Permissions.Undefined, title = Some("second"))
          val data          = <gaga/>.toDocument

          HttpAssert.put(formURL      , Unspecified, HttpCall.XML(first) , StatusCode.Created)
          HttpAssert.put(formURL      , Next       , HttpCall.XML(second), StatusCode.Created)
          HttpAssert.put(firstDataURL , Specific(1), HttpCall.XML(data)  , StatusCode.Created)
          HttpAssert.put(secondDataURL, Specific(2), HttpCall.XML(data)  , StatusCode.Created)

          HttpAssert.get(formURL, ForDocument("123", isDraft = false), HttpAssert.ExpectedBody(HttpCall.XML(first) , Operations.None, Some(1)))
          HttpAssert.get(formURL, ForDocument("456", isDraft = false), HttpAssert.ExpectedBody(HttpCall.XML(second), Operations.None, Some(2)))
          HttpAssert.get(formURL, ForDocument("789", isDraft = false), HttpAssert.ExpectedCode(StatusCode.NotFound))
        }
      }
    }
  }

  describe("Permissions") {
    it("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("permissions") { (_, provider) =>

          val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
          val data    = <data/>.toDocument
          val guest   = None
          val clerk   = Some(Credentials(UserAndGroup("tom", Some("clerk")  ), List(SimpleRole("clerk"  )), Nil))
          val manager = Some(Credentials(UserAndGroup("jim", Some("manager")), List(SimpleRole("manager")), Nil))
          val admin   = Some(Credentials(UserAndGroup("tim", Some("admin")  ), List(SimpleRole("admin"  )), Nil))

          locally {
            val DataURL = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"

            // Anonymous: no permission defined
            HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, Permissions.Undefined)), StatusCode.Created)
            HttpAssert.put(DataURL, Specific(1), HttpCall.XML(data), StatusCode.Created)
            HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), AnyOperation, Some(1)))

            // Anonymous: create and read
            HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, AnyoneCanCreateAndRead)), StatusCode.NoContent)
            HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(Set(Create, Read)), Some(1)))

            // Anonymous: just create, then can't read data
            HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, AnyoneCanCreate)), StatusCode.NoContent)
            HttpAssert.get(DataURL, Unspecified, HttpAssert.ExpectedCode(StatusCode.Forbidden))
          }

          locally {
            val dataURL = HttpCall.crudURLPrefix(provider) + "data/456/data.xml"

            // More complex permissions based on roles
            HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, Permissions.Defined(List(
              Permission(Nil                              , SpecificOperations(Set(Create))),
              Permission(List(Condition.RolesAnyOf(List("clerk"  ))), SpecificOperations(Set(Read))),
              Permission(List(Condition.RolesAnyOf(List("manager"))), SpecificOperations(Set(Read, Update))),
              Permission(List(Condition.RolesAnyOf(List("admin"  ))), SpecificOperations(Set(Read, Update, Delete)))
            )))), StatusCode.NoContent)
            HttpAssert.put(dataURL, Specific(1), HttpCall.XML(data), StatusCode.Created)

            // Check who can read
            HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedCode(StatusCode.Forbidden)                                                              , guest)
            HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(Set(Create, Read))                , Some(1)), clerk)
            HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(Set(Create, Read, Update))        , Some(1)), manager)
            HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(HttpCall.XML(data), SpecificOperations(Set(Create, Read, Update, Delete)), Some(1)), admin)

            // Only managers and admins can update
            HttpAssert.put(dataURL, Unspecified, HttpCall.XML(data), StatusCode.Forbidden, guest)
            HttpAssert.put(dataURL, Unspecified, HttpCall.XML(data), StatusCode.Forbidden, clerk)
            HttpAssert.put(dataURL, Unspecified, HttpCall.XML(data), StatusCode.NoContent, manager)
            HttpAssert.put(dataURL, Unspecified, HttpCall.XML(data), StatusCode.NoContent, admin)

            // Only admins can delete
            HttpAssert.del(dataURL, Unspecified, StatusCode.Forbidden, guest)
            HttpAssert.del(dataURL, Unspecified, StatusCode.Forbidden, clerk)
            HttpAssert.del(dataURL, Unspecified, StatusCode.Forbidden, manager)
            HttpAssert.del(dataURL, Unspecified, StatusCode.NoContent, admin)

            // Always return a StatusCode.NotFound if the data doesn't exist, irrelevant of the permissions
            // 2023-04-18: Following changes to the persistence proxy: this is now a `StatusCode.Gone`.
            // https://github.com/orbeon/orbeon-forms/issues/4979#issuecomment-912742633
            HttpAssert.del(dataURL, Unspecified, StatusCode.Gone, guest)
            HttpAssert.del(dataURL, Unspecified, StatusCode.Gone, clerk)
            HttpAssert.del(dataURL, Unspecified, StatusCode.Gone, manager)
            HttpAssert.del(dataURL, Unspecified, StatusCode.Gone, admin)
          }
        }
      }
    }

    it("must allow attachment creation with update permission because of role (#7057)") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("attachment-permissions") { (_, provider) =>
          val formURL            = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
          val mainDataURL        = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
          val otherDataURL       = HttpCall.crudURLPrefix(provider) + "data/456/data.xml"
          val mainAttachmentURL  = HttpCall.crudURLPrefix(provider) + "data/123/attachment"
          val data               = <data/>.toDocument
          val createReadUpdate   = SpecificOperations(Set(Create, Read, Update))
          val attachmentBody     = HttpCall.Binary(Array[Byte](1, 2, 3, 4, 5))
          val attachmentExpected = HttpAssert.ExpectedBody(attachmentBody, createReadUpdate, Some(1))
          val creatorUser        = Some(Credentials(UserAndGroup("creator", None), List(SimpleRole("creator")), Nil))
          val updaterUser        = Some(Credentials(UserAndGroup("updater", None), List(SimpleRole("updater")), Nil))

          val formPermissions = Permissions.Defined(List(
            Permission(List(Condition.RolesAnyOf(List("creator"))), createReadUpdate),
            Permission(List(Condition.RolesAnyOf(List("updater"))), SpecificOperations(Set(Update)))
          ))

          HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, formPermissions)), StatusCode.Created)

          HttpAssert.put(mainDataURL      , Specific(1), HttpCall.XML(data), StatusCode.Created  , creatorUser) // Creator creates initial data
          HttpAssert.put(otherDataURL     , Specific(1), HttpCall.XML(data), StatusCode.Forbidden, updaterUser) // Updater can't create data
          HttpAssert.put(mainDataURL      , Unspecified, HttpCall.XML(data), StatusCode.NoContent, updaterUser) // Updater can update data
          HttpAssert.put(mainAttachmentURL, Specific(1), attachmentBody    , StatusCode.Created  , updaterUser) // Updater can attach
          HttpAssert.get(mainAttachmentURL, Unspecified, attachmentExpected                      , creatorUser) // Attachment was stored
        }
      }
    }

    it("must allow attachment creation with update permission because owner (#7145)") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("attachment-permissions") { (_, provider) =>
          val formURL            = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
          val dataURL            = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
          val attachment1URL     = HttpCall.crudURLPrefix(provider) + "data/123/attachment1"
          val attachment2URL     = HttpCall.crudURLPrefix(provider) + "data/123/attachment2"
          val data               = <data/>.toDocument
          val createReadUpdate   = SpecificOperations(Set(Create, Read, Update))
          val readUpdate         = SpecificOperations(Set(        Read, Update))
          val attachmentBody     = HttpCall.Binary(Array[Byte](1, 2, 3, 4, 5))
          val user               = Some(Credentials(UserAndGroup("user", None), Nil, Nil))

          def storeForm(formPermissions: Permissions, isUpdate: Boolean = false): Unit =
            HttpAssert.put(formURL, Specific(1), HttpCall.XML(buildFormDefinition(provider, formPermissions)), if (isUpdate) StatusCode.NoContent else StatusCode.Created)

          // Anyone can create, owners can read and update
          storeForm(Permissions.Defined(List(
            Permission(Nil, SpecificOperations(Set(Create))),
            Permission(List(Condition.Owner), SpecificOperations(Set(Read, Update)))
          )))
          HttpAssert.put(dataURL       , Specific(1), HttpCall.XML(data), StatusCode.Created  , user) // Create initial data
          HttpAssert.put(attachment1URL, Specific(1), attachmentBody    , StatusCode.Created, user) // Can attach
          HttpAssert.get(attachment1URL, Unspecified, HttpAssert.ExpectedBody(attachmentBody, createReadUpdate, Some(1)), user)

          // Now we change the permissions, and only owners can read and update
          storeForm(Permissions.Defined(List(
            Permission(List(Condition.Owner), SpecificOperations(Set(Read, Update)))
          )), isUpdate = true)
          HttpAssert.put(attachment2URL, Specific(1), attachmentBody    , StatusCode.Created, user) // Can still attach
          HttpAssert.get(attachment2URL, Unspecified, HttpAssert.ExpectedBody(attachmentBody, readUpdate, Some(1)), user)
        }
      }
    }
  }

  describe("Organizations") {
    ignore("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("Organization-based permissions") { (_, provider) =>

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
          HttpAssert.put(formURL, Unspecified, HttpCall.XML(buildFormDefinition(provider, Permissions.Defined(List(
            Permission(Nil                                        , SpecificOperations(Set(Create))),
            Permission(List(Condition.Owner)                      , SpecificOperations(Set(Read, Update))),
            Permission(List(Condition.RolesAnyOf(List("clerk")))  , SpecificOperations(Set(Read))),
            Permission(List(Condition.RolesAnyOf(List("manager"))), SpecificOperations(Set(Read, Update)))
          )))), StatusCode.Created)

          // Data initially created by sfUserA
          HttpAssert.put(dataURL, Specific(1), dataBody, StatusCode.Created, c1User)
          // Owner can read their own data
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(dataBody, CanCreateReadUpdate, Some(1)), c1User)
          // Other users can't read the data
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedCode(StatusCode.Forbidden)                  , c2User)
          // Managers of the user up the organization structure can read the data
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(dataBody, CanCreateReadUpdate, Some(1)), cManager) //TODO: getting StatusCode.Forbidden
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(dataBody, CanCreateReadUpdate, Some(1)), bManager)
          // Other managers can't read the data
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedCode(StatusCode.Forbidden)                  , dManager)
        }
      }
    }
  }

  describe("Attachments") {
    def basicOperationsWithForm(
      formName : String,
      preTest  : (AppForm, FormOrData)            => Unit = (_, _)    => (),
      postTest : (AppForm, FormOrData, List[Int]) => Unit = (_, _, _) => ()
    ): Unit = {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("attachments") { (_, provider) =>
          val appForm = AppForm(provider.entryName, formName)
          val formOrData = FormOrData.Data

          preTest(appForm, formOrData)

          createForm(provider, formName)

          val MiB = 1024 * 1024

          val largestWorkingSize =
            if (formName == FilesystemAttachmentsFormName) {
              // Filesystem attachment
              256 * MiB
            } else if (formName ==  S3AttachmentsFormName) {
              // S3 attachment
              1 * MiB
            } else {
              // Database attachment
              provider match {
                // Some of these sizes could probably be made higher by changing the default database configurations
                case MySQL      =>   2 * MiB - 256
                case PostgreSQL => 247 * MiB
                case SQLite     => 256 * MiB
              }
            }

          // To avoid OutOfMemoryError exceptions without changing the default memory configuration
          val largestSize = math.min(128 * MiB, largestWorkingSize)

          val sizesToTest = List(0, 1, 1024, largestSize)

          for ((size, position) <- sizesToTest.zipWithIndex) {
            val bodyArray = new Array[Byte](size) |!> Random.nextBytes
            val body      = bodyArray |> HttpCall.Binary.apply
            val url       = HttpCall.crudURLPrefix(provider, formName) + s"data/123/file$position"
            val (hashAlgorithm, hashValue) = computeHash(bodyArray)

            HttpAssert.put(url, Specific(1), body, StatusCode.Created, hashAlgorithm = Some(hashAlgorithm), hashValue = Some(hashValue))

            // No range request
            HttpAssert.get(
              url       = url,
              version   = Unspecified,
              httpRange = None,
              expected  = HttpAssert.ExpectedBody(
                body          = body,
                operations    = AnyOperation,
                formVersion   = Some(1),
                hashAlgorithm = Some(hashAlgorithm),
                hashValue     = Some(hashValue),
                statusCode    = StatusCode.Ok
              )
            )

            if (size > 2) {
              // Fully-defined range request
              HttpAssert.get(
                url       = url,
                version   = Unspecified,
                httpRange = Some(HttpRange(0, Some(1))),
                expected  = HttpAssert.ExpectedBody(
                  body               = HttpCall.Binary(bodyArray.slice(0, 2)),
                  operations         = AnyOperation,
                  formVersion        = Some(1),
                  contentRangeHeader = Some(s"bytes 0-1/$size"),
                  hashAlgorithm      = Some(hashAlgorithm),
                  hashValue          = Some(hashValue),
                  statusCode         = StatusCode.PartialContent
                )
              )
            }

            if (size > 128) {
              // Range request with only start offset
              HttpAssert.get(
                url       = url,
                version   = Unspecified,
                httpRange = Some(HttpRange(128, None)),
                expected  = HttpAssert.ExpectedBody(
                  body               = HttpCall.Binary(bodyArray.drop(128)),
                  operations         = AnyOperation,
                  formVersion        = Some(1),
                  contentRangeHeader = Some(s"bytes 128-${size-1}/$size"),
                  hashAlgorithm      = Some(hashAlgorithm),
                  hashValue          = Some(hashValue),
                  statusCode         = StatusCode.PartialContent
                )
              )
            }

            if (size > 32770) {
              // Large range (1)
              HttpAssert.get(
                url = url,
                version = Unspecified,
                httpRange = Some(HttpRange(1, Some(32770))),
                expected = HttpAssert.ExpectedBody(
                  body = HttpCall.Binary(bodyArray.slice(1, 32771)),
                  operations = AnyOperation,
                  formVersion = Some(1),
                  contentRangeHeader = Some(s"bytes 1-32770/$size"),
                  hashAlgorithm = Some(hashAlgorithm),
                  hashValue = Some(hashValue),
                  statusCode = StatusCode.PartialContent
                )
              )
            }

            if (size > 65538) {
              // Large range (2)
              HttpAssert.get(
                url = url,
                version = Unspecified,
                httpRange = Some(HttpRange(1, Some(65540))),
                expected = HttpAssert.ExpectedBody(
                  body = HttpCall.Binary(bodyArray.slice(1, 65541)),
                  operations = AnyOperation,
                  formVersion = Some(1),
                  contentRangeHeader = Some(s"bytes 1-65540/$size"),
                  hashAlgorithm = Some(hashAlgorithm),
                  hashValue = Some(hashValue),
                  statusCode = StatusCode.PartialContent
                )
              )
            }
          }

          postTest(appForm, formOrData, sizesToTest)
        }
      }
    }

    it("must pass basic operations") {
      basicOperationsWithForm(formName = DefaultFormName)
    }

    it("must pass basic operations (filesystem attachments) (#4146)") {
      def basePath(appForm: AppForm, formOrData: FormOrData): Path =
        Paths.get(FilesystemCRUD.config(appForm, formOrData).basePath)

      def fileSizes(directory: Path): List[Long] =
        Files
          .walk(directory)
          .filter(Files.isRegularFile(_))
          .map(_.toFile.length())
          .collect(java.util.stream.Collectors.toList())
          .asScala
          .toList

      var directoryToCleanAfterTest: Option[Path] = None

      def preTest(appForm: AppForm, formOrData: FormOrData): Unit = {
        val directory = basePath(appForm, formOrData)

        // Create the attachments base directory if needed, assuming that its parent directory exists

        if (!Files.exists(directory)) {
          Files.createDirectory(directory)
          directoryToCleanAfterTest = Some(directory)
        }
      }

      def postTest(appForm: AppForm, formOrData: FormOrData, testSizes: List[Int]): Unit = {
        val directory     = basePath(appForm, formOrData)
        val actualSizes   = fileSizes(directory).sorted
        val expectedSizes = testSizes.map(_.toLong).sorted

        // Compare local files count and sizes
        assert(actualSizes == expectedSizes)
      }

      try {
        basicOperationsWithForm(formName = FilesystemAttachmentsFormName, preTest = preTest, postTest = postTest)
      } finally {
        // Delete the base directory only if it was created by the test
        directoryToCleanAfterTest.foreach { directory =>
          // Delete all sub-directories and files in reverse order, so that children are deleted before parents
          Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
        }
      }
    }

    it("must pass basic operations (S3 attachments) (#6948)", S3Tag) {
      // Read S3 base path from oxf.fr.persistence.[provider].base-path test property
      val s3BasePath = providerProperty(provider = "s3", "base-path", defaultOpt = None)

      S3Test.withTestS3ConfigAndPath(
        configName = "test-s3-config",
        pathOpt    = s3BasePath.some
      ) { implicit s3Config => _ =>

        S3.withS3Client { implicit s3Client =>

          def postTest(appForm: AppForm, formOrData: FormOrData, testSizes: List[Int]): Unit = {

            val actualSizes   = S3.objects(bucketName = s3Config.bucket, prefix = s3BasePath).get.map(_.size()).sorted
            val expectedSizes = testSizes.map(_.toLong).sorted

            // Compare S3 objects count and sizes
            assert(actualSizes == expectedSizes)
          }

          basicOperationsWithForm(formName = S3AttachmentsFormName, postTest = postTest)
        }
      }
    }

    it("must support AVTs in 'directory' base directory property") {
      // No AVT
      val directory1 = FilesystemCRUD.config(AppForm("fs-app", "fs-form-1"), FormOrData.Data).basePath
      assert(directory1 == "test1")

      // Simple AVT (constant string)
      val directory2 = FilesystemCRUD.config(AppForm("fs-app", "fs-form-2"), FormOrData.Data).basePath
      assert(directory2 == "test2")

      // Simple AVT (basic arithmetic operation)
      val directory3 = FilesystemCRUD.config(AppForm("fs-app", "fs-form-3"), FormOrData.Data).basePath
      assert(directory3 == "3")

      // environment-variable function
      val directory4 = FilesystemCRUD.config(AppForm("fs-app", "fs-form-4"), FormOrData.Data).basePath
      assert(directory4.nonEmpty)
    }
  }

  // Try uploading files of 1 KB, 1 MB
  describe("Large XML documents") {
    it("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("large XML documents") { (_, provider) =>

          createForm(provider)

          for ((size, position) <- Seq(1024, 1024 * 1024).zipWithIndex) {

            val charArray = new Array[Char](size)
            for (i <- 0 until size)
              charArray(i) = Random.nextPrintableChar()

            val text    = dom.Text(new String(charArray))
            val element = dom.Element("gaga")   |!> (_.add(text))
            val xmlBody = dom.Document(element) |> HttpCall.XML.apply

            val dataUrl = HttpCall.crudURLPrefix(provider) + s"data/$position/data.xml"

            HttpAssert.put(dataUrl, Specific(1), xmlBody, StatusCode.Created)
            HttpAssert.get(dataUrl, Unspecified, HttpAssert.ExpectedBody(xmlBody, AnyOperation, Some(1)))
          }
        }
      }
    }
  }

  describe("Drafts") {
    it("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("drafts") { (_, provider) =>

          createForm(provider)

          // Draft and non-draft are different
          val first    = HttpCall.XML(<gaga1/>.toDocument)
          val second   = HttpCall.XML(<gaga2/>.toDocument)
          val dataURL  = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
          val draftURL = HttpCall.crudURLPrefix(provider) + "draft/123/data.xml"

          HttpAssert.put(dataURL,  Specific(1), first, StatusCode.Created)
          HttpAssert.put(dataURL,  Specific(1), first, StatusCode.NoContent)

          // 2023-04-18: Following changes to the persistence proxy: we now get a `StatusCode.BadRequest` instead of a
          // `StatusCode.Created`. It seems unreasonable that storing a draft would succeed without passing a version,
          // if it's the first time we are storing the draft for the given document id.
          HttpAssert.put(draftURL, Unspecified, second, StatusCode.BadRequest)
          HttpAssert.put(draftURL, Specific(1), second, StatusCode.Created)
          HttpAssert.put(draftURL, Unspecified, second, StatusCode.NoContent)
          HttpAssert.get(dataURL,  Unspecified, HttpAssert.ExpectedBody(first, AnyOperation, Some(1)))
          HttpAssert.get(draftURL, Unspecified, HttpAssert.ExpectedBody(second, AnyOperation, Some(1)))
        }
      }
    }
  }

  describe("Metadata extraction") {
    it("must pass basic operations") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("extract metadata") { (_, provider) =>

          val currentFormURL     = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
          val currentMetadataURL = HttpCall.formMetadataURL(provider)
          val formDefinition     = buildFormDefinition(provider, AnyoneCanCreateAndRead)

          HttpAssert.put(currentFormURL, Unspecified, HttpCall.XML(formDefinition), StatusCode.Created)

          val expectedBody =
            <forms search-total="1">
                <form operations="create read">
                    <application-name>{provider.entryName}</application-name>
                    <form-name>{DefaultFormName}</form-name>
                    <form-version>1</form-version>
                    <title xml:lang="en"/>
                    <available>true</available>
                    <permissions>
                        <permission operations="create read -list"/>
                    </permissions>
                </form>
            </forms>.toDocument

          val (resultCode, _, resultBodyTry) = HttpCall.get(currentMetadataURL, Unspecified, None)

          assert(resultCode == StatusCode.Ok)

          def filterResultBody(bytes: Array[Byte]) = {

            val doc = IOSupport.readOrbeonDom(new ByteArrayInputStream(bytes))

            for {
              formElem     <- doc.getRootElement.elements("form")
              elemToDetach <- formElem.elements("last-modified-time") ++ formElem.elements("created")
            } locally {
              elemToDetach.detach()
            }

            doc
          }

          assertXMLDocumentsIgnoreNamespacesInScope(expectedBody, filterResultBody(resultBodyTry.get))
        }
      }
    }
  }

  describe("ETag and If-Match") {

    it("must return different `ETag`, support `If-Match`") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("etag") { (_, provider) =>

          createForm(provider)

          val dataURL = HttpCall.crudURLPrefix(provider) + "data/123/data.xml"
          val data    = HttpCall.XML(<gaga/>.toDocument)

          def getETag: String = {
            val headers = HttpCall.get(dataURL, Unspecified, None)._2
            headers(Headers.ETag).head
          }

          // We get an ETag
          HttpAssert.put(dataURL, Specific(1), data, StatusCode.Created, expectedETag = Some("*"))
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(data, AnyOperation, Some(1), etag = Some("*")))

          // The ETag is different after a subsequent PUT
          val firstETag = getETag
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(data, AnyOperation, Some(1), etag = Some(firstETag)))
          HttpAssert.put(dataURL, Specific(1), data, StatusCode.NoContent, expectedETag = Some("*"))
          HttpAssert.get(dataURL, Unspecified, HttpAssert.ExpectedBody(data, AnyOperation, Some(1), etag = Some("*")))
          val secondETag = getETag
          assert(firstETag != secondETag)

          // If-Match with a non-matching (fails), matching (succeeds), and wildcard "*" ETag (succeeds)
          HttpAssert.put(dataURL, Specific(1), data, StatusCode.PreconditionFailed, ifMatch = Some("123")     , expectedETag = None)
          HttpAssert.put(dataURL, Specific(1), data, StatusCode.NoContent         , ifMatch = Some(secondETag), expectedETag = Some("*"))
          HttpAssert.put(dataURL, Specific(1), data, StatusCode.NoContent         , ifMatch = Some("*")       , expectedETag = Some("*"))
        }
      }
    }
  }
}
