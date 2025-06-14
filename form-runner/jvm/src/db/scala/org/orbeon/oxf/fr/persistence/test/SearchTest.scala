/**
 * Copyright (C) 2019 Orbeon, Inc.
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

import org.orbeon.dom.Document
import org.orbeon.io.IOUtils.useAndClose
import org.orbeon.oxf.externalcontext.SafeRequestContext
import org.orbeon.oxf.fr.Version
import org.orbeon.oxf.fr.Version.{Specific, Unspecified}
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.http.HttpCall.{Check, assertCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.http.HttpMethod.POST
import org.orbeon.oxf.http.{HttpMethod, StatusCode}
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.oxf.xml.dom.IOSupport
import org.orbeon.scaxon.NodeConversions
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Futures.{interval, timeout}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Second, Seconds, Span}

import java.io.ByteArrayInputStream
import java.time.Instant


class SearchTest
    extends DocumentTestBase
     with XFormsSupport
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private implicit val Logger: IndentedLogger = new IndentedLogger(LoggerFactory.createLogger(classOf[SearchTest]), true)

  private val controlPath = "section-1/control-1"

  describe("Search API") {
    val baseParams =
      <_>
        <query/>
        <drafts>include</drafts>
        <page-size>10</page-size>
        <page-number>1</page-number>
        <lang>en</lang>
      </_>

    it("returns an empty result when there are no documents") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURL(provider)

          val searchRequest =
            <search>
                <query/>
                <drafts>include</drafts>
                <page-size>10</page-size>
                <page-number>1</page-number>
                <lang>en</lang>
            </search>.toDocument

          val searchResult =
            <documents search-total="0"/>.toDocument

          HttpCall.assertCall(
            HttpCall.SolicitedRequest(
              path    = SearchURL,
              version = Specific(1),
              method  = POST,
              body    = Some(HttpCall.XML(searchRequest))
            ),
            HttpCall.ExpectedResponse(
              code = StatusCode.Ok,
              body = Some(HttpCall.XML(searchResult))
            )
          )
        }
      }
    }

    it("returns correct results with underscores and other special characters (free-text search)") {
      for {
        // TODO: investigate why < and > don't work at all with MySQL
        // TODO: investigate why & and \ don't work at all with SQL Server
        character <- Seq("\\", "%", "_", "*", "'")
      } locally {
        val lookingFor    = s"test$character"
        val notLookingFor = "tests"

        val searchRequest =
          <search>
            <query>{lookingFor}</query>
            <query path={controlPath}/>
            <drafts>include</drafts>
            <page-size>10</page-size>
            <page-number>1</page-number>
            <lang>en</lang>
          </search>.toDocument

        testWithSimpleValues(
          searchRequest         = _ => searchRequest,
          expectedDocumentNames = List("1"),
          testDocumentOrder     = false,
          formData              = Seq(FormData("1", lookingFor), FormData("2", notLookingFor))
        )
      }
    }

    it("returns correct results with underscores and multiple search terms (free-text search)") {
      val searchRequest =
        <search>
          <query>t_st3 *test4*</query>
          <query path={controlPath}/>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      // Test Oracle and SQL Server's AND logic (see Provider.xmlContainsParam)

      testWithSimpleValues(
        searchRequest         = _ => searchRequest,
        expectedDocumentNames = List("3"),
        testDocumentOrder     = false,
        formData              = Seq(
          FormData("1", "*test1*"),
          FormData("2", "*test2*"),
          FormData("3", "*t_st3* *test4* *test5*")
        ),
        // TODO: add test for other databases as well
        providers             = Some(List())
      )
    }

    it("returns correct results with underscores (structured text, substring)") {
      val searchRequest =
        <search>
          <query/>
          <query path={controlPath} match="substring">t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      testWithSimpleValues(
        searchRequest         = _ => searchRequest,
        expectedDocumentNames = List("3"),
        testDocumentOrder     = false,
        formData              = Seq(
          FormData("1", "*test1*"),
          FormData("2", "*test2*"),
          FormData("3", "*t_st3*")
        )
      )
    }

    it("returns correct results with underscores (structured text, exact)") {
      val searchRequest =
        <search>
          <query/>
          <query path={controlPath} match="exact">t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      testWithSimpleValues(
        searchRequest         = _ => searchRequest,
        expectedDocumentNames = List("3"),
        testDocumentOrder     = false,
        formData              = Seq(
          FormData("1", "t1st"),
          FormData("2", "t2st"),
          FormData("3", "t_st"),
          FormData("4", "*t_st*")
        )
      )
    }

    it("returns correct results with underscores (structured text, token)") {
      val formData = Seq(
        FormData("1", "t1st"),
        FormData("2", "t1st t2st"),
        FormData("3", "t1st t2st t3st"),
        FormData("4", "t_st t2st t3st"),
        FormData("5", "t1st t_st t3st"),
        FormData("6", "t1st t2st t_st"),
        FormData("7", "t1st t2st *t_st*")
      )

      val searchRequest1 =
        <search>
          <query/>
          <query path={controlPath} match="token">t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      testWithSimpleValues(
        searchRequest         = _ => searchRequest1,
        expectedDocumentNames = List("4", "5", "6"),
        testDocumentOrder     = false,
        formData              = formData
      )

      val searchRequest2 =
        <search>
          <query/>
          <query path={controlPath} match="token">t3st t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      testWithSimpleValues(
        searchRequest         = _ => searchRequest2,
        expectedDocumentNames = List("4", "5"),
        testDocumentOrder     = false,
        formData              = formData
      )
    }

    it("returns correct results with created by, last modified by, and workflow stage") {

      def searchRequest(
        createdByOpt     : Option[String] = None,
        lastModifiedByOpt: Option[String] = None,
        workflowStageOpt : Option[String] = None
      ): Document =
        <search>{
          baseParams.child ++
            <query path={controlPath} match="substring">test</query> ++
            createdByOpt     .map(createdBy      => <query metadata="created-by"       match="exact">{createdBy     }</query>).getOrElse(Seq.empty) ++
            lastModifiedByOpt.map(lastModifiedBy => <query metadata="last-modified-by" match="exact">{lastModifiedBy}</query>).getOrElse(Seq.empty) ++
            workflowStageOpt .map(workflowStage  => <query metadata="workflow-stage"   match="exact">{workflowStage }</query>).getOrElse(Seq.empty)
        }</search>.toDocument

      val formData = Seq(
        FormData("1", "test1", createdByOpt = Some("created-by-1"), lastModifiedByOpt = Some("last-modified-by-1"), workflowStageOpt = Some("stage-1")),
        FormData("2", "test2", createdByOpt = Some("created-by-2"), lastModifiedByOpt = Some("last-modified-by-2"), workflowStageOpt = Some("stage-2")),
        FormData("3", "test3", createdByOpt = Some("created-by-3"), lastModifiedByOpt = Some("last-modified-by-3"), workflowStageOpt = Some("stage-3")),
        FormData("4", "test4", createdByOpt = Some("created-by-4"), lastModifiedByOpt = Some("last-modified-by-4"), workflowStageOpt = Some("stage-4"))
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(createdByOpt = Some("created-by-1")),
        expectedDocumentNames = List("1"),
        testDocumentOrder     = false,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(lastModifiedByOpt = Some("last-modified-by-2")),
        expectedDocumentNames = List("2"),
        testDocumentOrder     = false,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(workflowStageOpt = Some("stage-3")),
        expectedDocumentNames = List("3"),
        testDocumentOrder     = false,
        formData              = formData
      )
    }

    it("returns correct results with creation and modification dates") {
      def searchRequest(
         createdGteOpt     : Option[Instant] = None,
         createdLtOpt      : Option[Instant] = None,
         lastModifiedGteOpt: Option[Instant] = None,
         lastModifiedLtOpt : Option[Instant] = None
       ) =
        <search>{
          baseParams.child ++
            <query path={controlPath} match="substring">test</query> ++
            createdGteOpt     .map(createdGte      => <query metadata="created"       match="gte">{createdGte     }</query>).getOrElse(Seq.empty) ++
            createdLtOpt      .map(createdLt       => <query metadata="created"       match="lt" >{createdLt      }</query>).getOrElse(Seq.empty) ++
            lastModifiedGteOpt.map(lastModifiedGte => <query metadata="last-modified" match="gte">{lastModifiedGte}</query>).getOrElse(Seq.empty) ++
            lastModifiedLtOpt .map(lastModifiedLt  => <query metadata="last-modified" match="lt" >{lastModifiedLt }</query>).getOrElse(Seq.empty)
          }</search>.toDocument

      val formData = Seq(
        FormData("1", "test1", createdByOpt = Some("created-by-1")),
        FormData("2", "test2", createdByOpt = Some("created-by-2"), lastModifiedByOpt = Some("last-modified-by-2")),
        FormData("3", "test3", createdByOpt = Some("created-by-3")),
        FormData("4", "test4", createdByOpt = Some("created-by-4"), lastModifiedByOpt = Some("last-modified-by-4"))
      )

      testWithSimpleValues(
        // >= creation date of document 3
        searchRequest         = formDataAndDates => searchRequest(createdGteOpt = Some(formDataAndDates.map(_._2).apply(2).createdBy)),
        expectedDocumentNames = List("3", "4"),
        testDocumentOrder     = false,
        formData              = formData
      )

      testWithSimpleValues(
        // < creation date of document 3
        searchRequest         = formDataAndDates => searchRequest(createdLtOpt = Some(formDataAndDates.map(_._2).apply(2).createdBy)),
        expectedDocumentNames = List("1", "2"),
        testDocumentOrder     = false,
        formData              = formData
      )

      testWithSimpleValues(
        // >= modification date of document 2
        searchRequest         = formDataAndDates => searchRequest(lastModifiedGteOpt = Some(formDataAndDates.map(_._2).apply(1).lastModifiedBy)),
        expectedDocumentNames = List("2", "3", "4"),
        testDocumentOrder     = false,
        formData              = formData
      )

      testWithSimpleValues(
        // < modification date of document 4
        searchRequest         = formDataAndDates => searchRequest(lastModifiedLtOpt = Some(formDataAndDates.map(_._2).apply(3).lastModifiedBy)),
        expectedDocumentNames = List("1", "2", "3"),
        testDocumentOrder     = false,
        formData              = formData
      )
    }

    it("returns correct results when sorting") {
      def searchRequest(columnToSort: String, direction: String) =
        <search>{
            if (columnToSort == controlPath)
              baseParams.child ++
                <query path={controlPath} match="substring" sort={direction}>test</query>
            else
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata={columnToSort} sort={direction}/>
        }</search>.toDocument

      val formData = Seq(
        FormData("1", "test2", createdByOpt = Some("created-by-4"), lastModifiedByOpt = Some("last-modified-by-4"), workflowStageOpt = Some("workflow-stage-3")),
        FormData("2", "test3", createdByOpt = Some("created-by-2"), lastModifiedByOpt = Some("last-modified-by-1"), workflowStageOpt = Some("workflow-stage-4")),
        FormData("3", "test4", createdByOpt = Some("created-by-1"), lastModifiedByOpt = Some("last-modified-by-3"), workflowStageOpt = Some("workflow-stage-2")),
        FormData("4", "test1", createdByOpt = Some("created-by-3"), lastModifiedByOpt = Some("last-modified-by-2"), workflowStageOpt = Some("workflow-stage-1"))
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("created", "asc"),
        expectedDocumentNames = List("1", "2", "3", "4"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("created", "desc"),
        expectedDocumentNames = List("4", "3", "2", "1"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("last-modified", "asc"),
        expectedDocumentNames = List("1", "2", "3", "4"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("last-modified", "desc"),
        expectedDocumentNames = List("4", "3", "2", "1"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("created-by", "asc"),
        expectedDocumentNames = List("3", "2", "4", "1"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("created-by", "desc"),
        expectedDocumentNames = List("1", "4", "2", "3"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("last-modified-by", "asc"),
        expectedDocumentNames = List("2", "4", "3", "1"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("last-modified-by", "desc"),
        expectedDocumentNames = List("1", "3", "4", "2"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("workflow-stage", "asc"),
        expectedDocumentNames = List("4", "3", "1", "2"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest("workflow-stage", "desc"),
        expectedDocumentNames = List("2", "1", "3", "4"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(controlPath, "asc"),
        expectedDocumentNames = List("4", "1", "2", "3"),
        testDocumentOrder     = true,
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(controlPath, "desc"),
        expectedDocumentNames = List("3", "2", "1", "4"),
        testDocumentOrder     = true,
        formData              = formData
      )
    }

    def expectStatusCode(
      path          : String,
      searchRequest : Document,
      code          : Int,
      contentType   : Check[String] = Check.Ignore
    )(implicit
      safeRequestCtx: SafeRequestContext
    ): Unit =
      HttpCall.assertCall(
        HttpCall.SolicitedRequest(
          path    = path,
          version = Specific(1),
          method  = POST,
          body    = Some(HttpCall.XML(searchRequest))
        ),
        HttpCall.ExpectedResponse(code = code, contentType = contentType)
      )

    it("returns an error when specifying an invalid metadata attribute") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURL(provider)

          val searchRequest =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata="invalid-metadata" match="exact">test</query>
            }</search>.toDocument

          expectStatusCode(SearchURL, searchRequest, StatusCode.BadRequest)
        }
      }
    }

    it("returns an error when specifying an invalid match attribute") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURL(provider)

          def searchRequest(metadata: String, `match`: String, value: String) =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata={metadata} match={`match`}>{value}</query>
              }</search>.toDocument

          val TestString = "string"

          for (metadata <- Seq("created-by", "last-modified-by", "workflow-stage")) {
            expectStatusCode(SearchURL, searchRequest(metadata, "gte",   TestString), code = StatusCode.BadRequest)
            expectStatusCode(SearchURL, searchRequest(metadata, "lt",    TestString), code = StatusCode.BadRequest)
            expectStatusCode(SearchURL, searchRequest(metadata, "exact", TestString), code = StatusCode.Ok, contentType = Check.Some(ContentTypes.XmlContentType))
          }

          val TestDateTime = "2024-12-31T01:30:00.000Z"

          for (metadata <- Seq("created", "last-modified")) {
            expectStatusCode(SearchURL, searchRequest(metadata, "gte",   TestDateTime), code = StatusCode.Ok, contentType = Check.Some(ContentTypes.XmlContentType))
            expectStatusCode(SearchURL, searchRequest(metadata, "lt",    TestDateTime), code = StatusCode.Ok, contentType = Check.Some(ContentTypes.XmlContentType))
            expectStatusCode(SearchURL, searchRequest(metadata, "exact", TestDateTime), code = StatusCode.BadRequest)
          }
        }
      }
    }

    it("returns an error when specifying an invalid sort attribute") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURL(provider)

          val searchRequest =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring" sort="sort">test</query>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequest, code = StatusCode.BadRequest)
        }
      }
    }

    it("returns an error when specifying more than one sort attributes") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURL(provider)

          val searchRequestSortByControl =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring" sort="desc">test</query> ++
                <query metadata="created-by" match="exact">test</query>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequestSortByControl, code = StatusCode.Ok, contentType = Check.Some(ContentTypes.XmlContentType))

          val searchRequestSortByMetadata =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata="created-by" match="exact" sort="asc">test</query>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequestSortByMetadata, code = StatusCode.Ok, contentType = Check.Some(ContentTypes.XmlContentType))

          val searchRequestSortByBoth =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring" sort="asc">test</query> ++
                <query metadata="created-by" match="exact" sort="desc">test</query>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequestSortByBoth, code = StatusCode.BadRequest)
        }
      }
    }

    it("returns an error when specifying a metadata query value without a match operator") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURL(provider)

          val searchRequestSortOnly =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata="created-by" sort="asc"/>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequestSortOnly, code = StatusCode.Ok, contentType = Check.Some(ContentTypes.XmlContentType))

          val searchRequestValueOnly =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata="created-by">test</query>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequestValueOnly, code = StatusCode.BadRequest)

          val searchRequestBoth =
            <search>{
              baseParams.child ++
                <query path={controlPath} match="substring">test</query> ++
                <query metadata="created-by" sort="desc">test</query>
              }</search>.toDocument

          expectStatusCode(SearchURL, searchRequestBoth, code = StatusCode.BadRequest)
        }
      }
    }
  }

  describe("Indexing") {

    it("must clear or rebuild the index when the form definition is deleted or recreated (#6915)") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val testForm  = TestForm(provider, controls = Seq(TestForm.Control("control label")))
          val version   = Version.Specific(1)

          def readCount(tableName: String, deleted: Option[Boolean] = None): Int = {
            val sql =
              s"""SELECT count(*) count
                 |  FROM $tableName
                 | WHERE app          = '${testForm.appForm.app}'
                 |   AND form         = '${testForm.appForm.form}'
                 |   AND form_version = ${version.version}
                 |   ${deleted match {
                   case Some(d) => s"AND deleted = '${if (d) "Y" else "N"}'"
                   case None    => ""
                 }}
               """.stripMargin
            useAndClose(connection.prepareStatement(sql)) { ps =>
              useAndClose(ps.executeQuery()) { rs =>
                rs.next()
                rs.getInt("count")
              }
            }
          }

          def readCountFromIndex: Int = {
            val sql =
              s"""SELECT count(*) count
                 |  FROM orbeon_i_control_text
                 | WHERE data_id in (
                 |   SELECT data_id FROM orbeon_i_current
                 |   WHERE app          = '${testForm.appForm.app}'
                 |     AND form         = '${testForm.appForm.form}'
                 |     AND form_version = ${version.version}
                 | )
               """.stripMargin
            useAndClose(connection.prepareStatement(sql)) { ps =>
              useAndClose(ps.executeQuery()) { rs =>
                rs.next()
                rs.getInt("count")
              }
            }
          }

          val DataCount = 225

          testForm.putFormDefinition(version)
          testForm.putFormData(version, 1 to DataCount map (i => FormData(i.toString, s"v$i")))

          // 1. Test that form data is indexed
          assert(readCount("orbeon_i_current") == DataCount)
          assert(readCount("orbeon_form_data") == DataCount)
          assert(readCountFromIndex == DataCount)

          // 2. Delete form definition
          val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"
          HttpAssert.del(formURL, version, StatusCode.NoContent)

          // 3. Test that index tables are now empty, but the data is still there
          assert(readCount("orbeon_i_current") == 0)
          assert(readCount("orbeon_form_data") == DataCount)
          assert(readCountFromIndex == 0)

          // 4. Put form definition again and test that data is indexed again
          testForm.putFormDefinition(version)

          assert(readCount("orbeon_i_current") == DataCount)
          assert(readCount("orbeon_form_data") == DataCount)
          assert(readCountFromIndex == DataCount)

          // 5. Call indexing API to rebuild the index
          assertCall(
            actualRequest = HttpCall.SolicitedRequest(
              path    = HttpCall.reindexURL(provider, testForm.appForm.form),
              version = version,
              method  = HttpMethod.POST
            ),
            assertResponse = actualResponse => {
              assert(actualResponse.code == StatusCode.Ok)
            }
          )

          // 6. Test that data is still indexed (should not change, would be nice to have a way to check that the index was rebuilt)
          assert(readCount("orbeon_i_current") == DataCount)
          assert(readCount("orbeon_form_data") == DataCount)
          assert(readCountFromIndex == DataCount)

          // 7. Batch delete data
          assertCall(
            actualRequest = HttpCall.SolicitedRequest(
              path    = HttpCall.batchDeleteURL(provider, testForm.appForm.form),
              version = version,
              method  = HttpMethod.DELETE
            ),
            assertResponse = actualResponse => {
              assert(actualResponse.code == StatusCode.Ok)
            }
          )

          assert(readCount("orbeon_i_current") == 0)
          assert(readCount("orbeon_form_data", deleted = Some(false)) == DataCount)
          assert(readCountFromIndex == 0)
        }
      }
    }
  }

  def testWithSimpleValues(
    searchRequest        : Seq[(FormData, FormDataDates)] => Document,
    expectedDocumentNames: List[String],
    testDocumentOrder    : Boolean,
    formData             : Seq[FormData],
    providers            : Option[List[Provider]] = None
  ): Unit =
    withTestSafeRequestContext { implicit safeRequestCtx =>
      Connect.withOrbeonTables("form definition") { (connection, provider) =>
        if (providers.forall(_.contains(provider))) {
          val testForm  = TestForm(provider, controls = Seq(TestForm.Control("control label")))
          val version   = Specific(1)

          testForm.putFormDefinition(version)

          // Create the form data and retrieve creation/modification dates
          val formDataDates = testForm.putFormData(version, formData, returnDates = true)

          // Use `eventually` clause mainly for SQL Server, which doesn't update its indexes during several seconds after
          // the form data has been created
          eventually(timeout(Span(10, Seconds)), interval(Span(1, Second))) {
            assertCall(
              actualRequest = HttpCall.SolicitedRequest(
                path              = HttpCall.searchURL(provider),
                version           = Unspecified,
                method            = POST,
                body              = Some(HttpCall.XML(searchRequest(formData.zip(formDataDates)))),
                xmlResponseFilter = Some(searchResultFilter)
              ),
              assertResponse = actualResponse => {
                // Extract document names from the response
                val resultDoc     = IOSupport.readOrbeonDom(new ByteArrayInputStream(actualResponse.body))
                val root          = resultDoc.getRootElement
                val documentNames = root.elements("document").map(_.attribute("name").getValue).toList

                if (testDocumentOrder) {
                  assert(documentNames == expectedDocumentNames)
                } else {
                  assert(documentNames.toSet == expectedDocumentNames.toSet)
                }

                for {
                  document <- root.elements("document")
                  name      = document.attribute("name").getValue
                  details  <- document.elements("details")
                  detail   <- details.elements("detail")
                  value     = detail.getText
                } {
                  // Make sure the returned values are correct
                  assert(formData.find(_.id == name).get.singleControlValue == value)
                }
              }
            )
          }
        }
      }
    }

  // Filter the search results by removing the date attributes on the 'document' elements and by sorting the 'document'
  // elements by name
  def searchResultFilter(document: Document): Document = {
    import scala.xml.*

    def withFilteredAttributes(elem: Elem): Elem = {
      val attributesToRemove =
        Set("created", "last-modified", "operations", "created-by", "last-modified-by", "workflow-stage")

      val filteredAttributes = elem.attributes.filter {
        case Attribute(key, _, _) if attributesToRemove.contains(key) => false
        case _                                                        => true
      }
      elem.copy(attributes = filteredAttributes)
    }

    def sortedByNameAttribute(elems: Seq[Elem]): Seq[Elem] =
      elems.sortBy {
        _.attribute("name") match {
          case Some(attributeSeq) if attributeSeq.nonEmpty => attributeSeq.head.text
          case _                                           => ""
        }
      }

    val rootElem                = XML.loadString(document.getRootElement.serializeToString())
    val filteredResultDocuments = sortedByNameAttribute(rootElem.child.collect { case elem: Elem => elem }.map(withFilteredAttributes))

    NodeConversions.elemToOrbeonDom(rootElem.copy(child = filteredResultDocuments))
  }
}
