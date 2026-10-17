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
package org.orbeon.oxf.fr.persistence.test

import cats.syntax.option.*
import org.orbeon.connection.{ConnectionResult, StreamedContent}
import org.orbeon.oxf.externalcontext.{Credentials, SafeRequestContext}
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerPersistence.DataXml
import org.orbeon.oxf.fr.Version.Specific
import org.orbeon.oxf.fr.persistence.api.PersistenceApiTrait
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils.PersistenceBase
import org.orbeon.oxf.http.{HttpMethod, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.PathUtils.PathOps
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, JvmUrlEncoderDecoder, LoggerFactory, PathUtils}
import org.scalatest.funspec.AnyFunSpecLike


class BatchDeleteTest
  extends DocumentTestBase
    with PersistenceApiTrait
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private implicit val logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[BatchDeleteTest]), true)

  private var currentCredentials: Option[Credentials] = None

  override def connectPersistence(
    method                  : HttpMethod,
    pathQuery               : String,
    formVersionOpt          : Option[Either[FormDefinitionVersion, SearchVersion]],
    requestBodyContent      : Option[StreamedContent]   = None,
    customHeaders           : Map[String, List[String]] = Map.empty
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): ConnectionResult =
    HttpCall.connectPersistence(
      method             = method,
      path               = pathQuery,
      requestBodyContent = requestBodyContent,
      formVersionOpt     = formVersionOpt,
      credentialsOpt     = currentCredentials
    )

  describe("Batch delete API (#7961)") {

    it("must delete draft and non-draft data using PersistenceApi.doDelete()") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("batch delete doDelete") { (_, provider) =>

          implicit val safeRequestCtx          : SafeRequestContext            = SafeRequestContext(externalContext)
          implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

          val formName = "test-form-dodelete"
          val testForm = TestForm(
            provider   = provider,
            controls   = Seq(TestForm.Control("control-1")),
            formName   = formName
          )
          val version     = Specific(1)
          val credentials = TestForm.credentials("user").some

          currentCredentials = credentials

          testForm.putFormDefinition(version, credentials)

          val appForm       = testForm.appForm
          val nonDraftDocId = "doc-nondraft-1"
          val draftDocId    = "doc-draft-1"

          val nonDraftURL = s"crud/${appForm.app}/${appForm.form}/data/$nonDraftDocId/data.xml"
          val draftURL    = s"crud/${appForm.app}/${appForm.form}/draft/$draftDocId/data.xml"

          // Create non-draft and draft data
          HttpAssert.put(nonDraftURL, version, HttpCall.XML(testForm.formData(Seq("value1"))), StatusCode.Created, credentials)
          HttpAssert.put(draftURL,    version, HttpCall.XML(testForm.formData(Seq("value2"))), StatusCode.Created, credentials)

          // Verify both documents exist
          HttpAssert.get(nonDraftURL, version, HttpAssert.ExpectedCode(StatusCode.Ok), credentials)
          HttpAssert.get(draftURL,    version, HttpAssert.ExpectedCode(StatusCode.Ok), credentials)

          // Verify search returns both documents
          val searchBefore = search(
            appForm             = appForm,
            searchVersion       = SearchVersion.Specific(1),
            isInternalAdminUser = false,
            searchQueryOpt      = None,
            returnDetails       = false,
            firstPageOnly       = false
          ).toList
          assert(searchBefore.size == 2)
          assert(searchBefore.map(_.documentId).toSet == Set(nonDraftDocId, draftDocId))

          def dataPathQuery(documentId: String, isDraft: Boolean): String =
            PathUtils.recombineQuery(
              PersistenceBase.dropTrailingSlash ::
              "crud"                            ::
              FormRunner.createFormDataBasePathNoPrefix(
                appForm,
                version       = None,
                isDraft       = isDraft,
                documentIdOpt = Some(documentId)
              )                                 ::
              DataXml                           ::
              Nil mkString "/",
              Nil
            )

          // Delete draft data using doDelete with requireLastModificationDate = false
          doDelete(
            path                        = dataPathQuery(draftDocId, isDraft = true),
            modifiedTimeOpt             = None,
            forceDelete                 = false,
            isInternalAdminUser         = false,
            requireLastModificationDate = false
          )

          // Delete non-draft data using doDelete with requireLastModificationDate = false
          doDelete(
            path                        = dataPathQuery(nonDraftDocId, isDraft = false),
            modifiedTimeOpt             = None,
            forceDelete                 = false,
            isInternalAdminUser         = false,
            requireLastModificationDate = false
          )

          // Verify both documents no longer exist: non-draft returns Gone (soft deleted), draft returns NotFound (hard deleted)
          HttpAssert.get(nonDraftURL, version, HttpAssert.ExpectedCode(StatusCode.Gone), credentials)
          HttpAssert.get(draftURL,    version, HttpAssert.ExpectedCode(StatusCode.NotFound), credentials)

          // Verify search now returns 0 documents
          val searchAfter = search(
            appForm             = appForm,
            searchVersion       = SearchVersion.Specific(1),
            isInternalAdminUser = false,
            searchQueryOpt      = None,
            returnDetails       = false,
            firstPageOnly       = false
          ).toList
          assert(searchAfter.isEmpty)
        }
      }
    }

    it("must delete all records including draft and non-draft data when calling CRUD API with DELETE") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("batch delete CRUD DELETE") { (_, provider) =>

          implicit val safeRequestCtx          : SafeRequestContext            = SafeRequestContext(externalContext)
          implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

          val formName = "test-form-batch-delete"
          val testForm = TestForm(
            provider   = provider,
            controls   = Seq(TestForm.Control("control-1")),
            formName   = formName
          )
          val version     = Specific(1)
          val credentials = TestForm.credentials("user").some

          currentCredentials = credentials

          testForm.putFormDefinition(version, credentials)

          val appForm        = testForm.appForm
          val nonDraftDocId1 = "doc-nondraft-1"
          val nonDraftDocId2 = "doc-nondraft-2"
          val draftDocId1    = "doc-draft-1"
          val draftDocId2    = "doc-draft-2"

          val nonDraftURL1 = s"crud/${appForm.app}/${appForm.form}/data/$nonDraftDocId1/data.xml"
          val nonDraftURL2 = s"crud/${appForm.app}/${appForm.form}/data/$nonDraftDocId2/data.xml"
          val draftURL1    = s"crud/${appForm.app}/${appForm.form}/draft/$draftDocId1/data.xml"
          val draftURL2    = s"crud/${appForm.app}/${appForm.form}/draft/$draftDocId2/data.xml"

          // Create non-draft and draft data
          HttpAssert.put(nonDraftURL1, version, HttpCall.XML(testForm.formData(Seq("val1"))), StatusCode.Created, credentials)
          HttpAssert.put(nonDraftURL2, version, HttpCall.XML(testForm.formData(Seq("val2"))), StatusCode.Created, credentials)
          HttpAssert.put(draftURL1,    version, HttpCall.XML(testForm.formData(Seq("val3"))), StatusCode.Created, credentials)
          HttpAssert.put(draftURL2,    version, HttpCall.XML(testForm.formData(Seq("val4"))), StatusCode.Created, credentials)

          // Verify all documents exist
          HttpAssert.get(nonDraftURL1, version, HttpAssert.ExpectedCode(StatusCode.Ok), credentials)
          HttpAssert.get(nonDraftURL2, version, HttpAssert.ExpectedCode(StatusCode.Ok), credentials)
          HttpAssert.get(draftURL1,    version, HttpAssert.ExpectedCode(StatusCode.Ok), credentials)
          HttpAssert.get(draftURL2,    version, HttpAssert.ExpectedCode(StatusCode.Ok), credentials)

          // Verify search returns all 4 documents (drafts and non-drafts)
          val searchBefore = search(
            appForm             = appForm,
            searchVersion       = SearchVersion.Specific(1),
            isInternalAdminUser = false,
            searchQueryOpt      = None,
            returnDetails       = false,
            firstPageOnly       = false
          ).toList
          assert(searchBefore.size == 4)
          assert(searchBefore.map(_.documentId).toSet == Set(nonDraftDocId1, nonDraftDocId2, draftDocId1, draftDocId2))
          assert(searchBefore.filter(_.isDraft).map(_.documentId).toSet == Set(draftDocId1, draftDocId2))
          assert(searchBefore.filter(!_.isDraft).map(_.documentId).toSet == Set(nonDraftDocId1, nonDraftDocId2))

          // Call the CRUD API with DELETE on the data collection
          // Prior to fix #7961, having draft data would cause BatchDelete to enter an infinite loop!
          HttpAssert.del(
            url          = HttpCall.batchDeleteURL(provider, appForm.form),
            version      = version,
            expectedCode = StatusCode.Ok,
            credentials  = credentials
          )

          // Verify all documents, draft and non-draft, have been deleted
          HttpAssert.get(nonDraftURL1, version, HttpAssert.ExpectedCode(StatusCode.Gone), credentials)
          HttpAssert.get(nonDraftURL2, version, HttpAssert.ExpectedCode(StatusCode.Gone), credentials)
          HttpAssert.get(draftURL1,    version, HttpAssert.ExpectedCode(StatusCode.NotFound), credentials)
          HttpAssert.get(draftURL2,    version, HttpAssert.ExpectedCode(StatusCode.NotFound), credentials)

          // Verify search now returns 0 documents
          val searchAfter = search(
            appForm             = appForm,
            searchVersion       = SearchVersion.Specific(1),
            isInternalAdminUser = false,
            searchQueryOpt      = None,
            returnDetails       = false,
            firstPageOnly       = false
          ).toList
          assert(searchAfter.isEmpty)
        }
      }
    }
  }
}
