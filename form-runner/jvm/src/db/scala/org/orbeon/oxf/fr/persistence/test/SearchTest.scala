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
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.fr.persistence.http.HttpCall.assertCall
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Version.{Specific, Unspecified}
import org.orbeon.oxf.http.HttpMethod.POST
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter._
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

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[SearchTest]), true)
  private implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

  private val controlPath = "section-1/control-1"

  describe("Search API") {

    it("returns an empty result when there are no documents") {
      withTestExternalContext { implicit externalContext =>
        Connect.withOrbeonTables("form definition") { (connection, provider) =>

          val SearchURL = HttpCall.searchURLPrefix(provider)

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
          expectedDocumentNames = Set("1"),
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
        expectedDocumentNames = Set("3"),
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
        expectedDocumentNames = Set("3"),
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
        expectedDocumentNames = Set("3"),
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
        expectedDocumentNames = Set("4", "5", "6"),
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
        expectedDocumentNames = Set("4", "5"),
        formData              = formData
      )
    }

    it("returns correct results with created by, last modified by, and workflow stage") {
      def searchRequest(
        createdByOpt     : Option[String] = None,
        lastModifiedByOpt: Option[String] = None,
        workflowStageOpt : Option[String] = None
      ) =
        <search>{
          val baseParams =
            <_>
              <query/>
              <query path={controlPath} match="substring">test</query>
              <drafts>include</drafts>
              <page-size>10</page-size>
              <page-number>1</page-number>
              <lang>en</lang>
            </_>

          baseParams.child ++
            createdByOpt     .map(createdBy      => <created-by>{createdBy}</created-by>                 ).getOrElse(Seq.empty) ++
            lastModifiedByOpt.map(lastModifiedBy => <last-modified-by>{lastModifiedBy}</last-modified-by>).getOrElse(Seq.empty) ++
            workflowStageOpt .map(workflowStage  => <workflow-stage>{workflowStage}</workflow-stage>     ).getOrElse(Seq.empty)
        }</search>.toDocument

      val formData = Seq(
        FormData("1", "test1", createdByOpt = Some("created-by-1"), lastModifiedByOpt = Some("last-modified-by-1"), workflowStageOpt = Some("stage-1")),
        FormData("2", "test2", createdByOpt = Some("created-by-2"), lastModifiedByOpt = Some("last-modified-by-2"), workflowStageOpt = Some("stage-2")),
        FormData("3", "test3", createdByOpt = Some("created-by-3"), lastModifiedByOpt = Some("last-modified-by-3"), workflowStageOpt = Some("stage-3")),
        FormData("4", "test4", createdByOpt = Some("created-by-4"), lastModifiedByOpt = Some("last-modified-by-4"), workflowStageOpt = Some("stage-4"))
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(createdByOpt = Some("created-by-1")),
        expectedDocumentNames = Set("1"),
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(lastModifiedByOpt = Some("last-modified-by-2")),
        expectedDocumentNames = Set("2"),
        formData              = formData
      )

      testWithSimpleValues(
        searchRequest         = _ => searchRequest(workflowStageOpt = Some("stage-3")),
        expectedDocumentNames = Set("3"),
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
          val baseParams =
            <_>
              <query/>
              <query path={controlPath} match="substring">test</query>
              <drafts>include</drafts>
              <page-size>10</page-size>
              <page-number>1</page-number>
              <lang>en</lang>
            </_>

          baseParams.child ++
            createdGteOpt     .map(createdGte      => <created-gte>{createdGte}</created-gte>                 ).getOrElse(Seq.empty) ++
            createdLtOpt      .map(createdLt       => <created-lt>{createdLt}</created-lt>                    ).getOrElse(Seq.empty) ++
            lastModifiedGteOpt.map(lastModifiedGte => <last-modified-gte>{lastModifiedGte}</last-modified-gte>).getOrElse(Seq.empty) ++
            lastModifiedLtOpt .map(lastModifiedLt  => <last-modified-lt>{lastModifiedLt}</last-modified-lt>   ).getOrElse(Seq.empty)
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
        expectedDocumentNames = Set("3", "4"),
        formData              = formData
      )

      testWithSimpleValues(
        // < creation date of document 3
        searchRequest         = formDataAndDates => searchRequest(createdLtOpt = Some(formDataAndDates.map(_._2).apply(2).createdBy)),
        expectedDocumentNames = Set("1", "2"),
        formData              = formData
      )

      testWithSimpleValues(
        // >= modification date of document 2
        searchRequest         = formDataAndDates => searchRequest(lastModifiedGteOpt = Some(formDataAndDates.map(_._2).apply(1).lastModifiedBy)),
        expectedDocumentNames = Set("2", "3", "4"),
        formData              = formData
      )

      testWithSimpleValues(
        // < modification date of document 4
        searchRequest         = formDataAndDates => searchRequest(lastModifiedLtOpt = Some(formDataAndDates.map(_._2).apply(3).lastModifiedBy)),
        expectedDocumentNames = Set("1", "2", "3"),
        formData              = formData
      )
    }
  }

  def testWithSimpleValues(
    searchRequest        : Seq[(FormData, FormDataDates)] => Document,
    expectedDocumentNames: Set[String],
    formData             : Seq[FormData],
    providers            : Option[List[Provider]] = None
  ): Unit =
    withTestExternalContext { implicit externalContext =>
      Connect.withOrbeonTables("form definition") { (connection, provider) =>
        if (providers.forall(_.contains(provider))) {
          val testForm  = TestForm(provider, controls = Seq(TestForm.Control("control label")))
          val version   = Specific(1)

          testForm.putFormDefinition(version)

          // Create the form data and retrieve creation/modification dates
          val formDataDates = testForm.putFormData(version, formData, returnDates = true)

          // Use eventually clause mainly for SQL Server, which doesn't update its indexes during several seconds after
          // the form data has been created
          eventually(timeout(Span(10, Seconds)), interval(Span(1, Second))) {
            assertCall(
              actualRequest  = HttpCall.SolicitedRequest(
                path              = HttpCall.searchURLPrefix(provider),
                version           = Unspecified,
                method            = POST,
                body              = Some(HttpCall.XML(searchRequest(formData.zip(formDataDates)))),
                xmlResponseFilter = Some(searchResultFilter)
              ),
              assertResponse = actualResponse => {
                // Extract document names from the response
                val resultDoc     = IOSupport.readOrbeonDom(new ByteArrayInputStream(actualResponse.body))
                val root          = resultDoc.getRootElement
                val documentNames = root.elements("document").map(_.attribute("name").getValue).toSet

                assert(documentNames == expectedDocumentNames)

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
    import scala.xml._

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
