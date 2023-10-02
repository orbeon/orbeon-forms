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
import org.orbeon.oxf.fr.persistence.http.HttpCall.DefaultFormName
import org.orbeon.oxf.fr.persistence.http.{HttpAssert, HttpCall}
import org.orbeon.oxf.fr.persistence.relational.Provider
import org.orbeon.oxf.fr.persistence.relational.Version.{Specific, Unspecified}
import org.orbeon.oxf.http.HttpMethod.POST
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory}
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.scaxon.NodeConversions
import org.scalatest.funspec.AnyFunSpecLike

class SearchTest
    extends DocumentTestBase
     with XFormsSupport
     with ResourceManagerSupport
     with AnyFunSpecLike {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[SearchTest]), true)
  private implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

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
              path = SearchURL,
              version = Specific(1),
              method = POST,
              body = Some(HttpCall.XML(searchRequest))
            ),
            HttpCall.ExpectedResponse(
              code = StatusCode.Ok,
              body = Some(HttpCall.XML(searchResult))
            )
          )
        }
      }
    }

    it("returns correct results with underscores (free-text search)") {
      val searchRequest =
        <search>
          <query>t_st</query>
          <query path="section-1/control-1"/>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      val searchResult =
        <documents search-total="1">
          <document name="3" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">*t_st3*</detail>
            </details>
          </document>
        </documents>.toDocument

      underscoreTest(searchRequest, searchResult, values = Seq("1" -> "*test1*", "2" -> "*test2*", "3" -> "*t_st3*"))
    }

    it("returns correct results with underscores (structured text, substring)") {
      val searchRequest =
        <search>
          <query/>
          <query path="section-1/control-1" match="substring">t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      val searchResult =
        <documents search-total="1">
          <document name="3" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">*t_st3*</detail>
            </details>
          </document>
        </documents>.toDocument

      underscoreTest(searchRequest, searchResult, values = Seq("1" -> "*test1*", "2" -> "*test2*", "3" -> "*t_st3*"))
    }

    it("returns correct results with underscores (structured text, exact)") {
      val searchRequest =
        <search>
          <query/>
          <query path="section-1/control-1" match="exact">t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      val searchResult =
        <documents search-total="1">
          <document name="3" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">t_st</detail>
            </details>
          </document>
        </documents>.toDocument

      underscoreTest(searchRequest, searchResult, values = Seq("1" -> "t1st", "2" -> "t2st", "3" -> "t_st", "4" -> "*t_st*"))
    }

    it("returns correct results with underscores (structured text, token)") {
      val values = Seq(
        "1" -> "t1st",
        "2" -> "t1st t2st",
        "3" -> "t1st t2st t3st",
        "4" -> "t_st t2st t3st",
        "5" -> "t1st t_st t3st",
        "6" -> "t1st t2st t_st",
        "7" -> "t1st t2st *t_st*"
      )

      val searchRequest1 =
        <search>
          <query/>
          <query path="section-1/control-1" match="token">t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      val searchResult1 =
        <documents search-total="3">
          <document name="4" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">t_st t2st t3st</detail>
            </details>
          </document>
          <document name="5" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">t1st t_st t3st</detail>
            </details>
          </document>
          <document name="6" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">t1st t2st t_st</detail>
            </details>
          </document>
        </documents>.toDocument

      underscoreTest(searchRequest1, searchResult1, values)

      val searchRequest2 =
        <search>
          <query/>
          <query path="section-1/control-1" match="token">t3st t_st</query>
          <drafts>include</drafts>
          <page-size>10</page-size>
          <page-number>1</page-number>
          <lang>en</lang>
        </search>.toDocument

      val searchResult2 =
        <documents search-total="2">
          <document name="4" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">t_st t2st t3st</detail>
            </details>
          </document>
          <document name="5" draft="false" operations="*">
            <details>
              <detail path="section-1/control-1">t1st t_st t3st</detail>
            </details>
          </document>
        </documents>.toDocument

      underscoreTest(searchRequest2, searchResult2, values)
    }
  }

  def underscoreTest(searchRequest: Document, searchResult: Document, values: Seq[(String, String)]): Unit =
    withTestExternalContext { implicit externalContext =>
      Connect.withOrbeonTables("form definition") { (connection, provider) =>

        val formURL = HttpCall.crudURLPrefix(provider) + "form/form.xhtml"

        HttpAssert.put(formURL, Unspecified, HttpCall.XML(testFormDefinition(provider)), StatusCode.Created)

        def dataURL(id: String) = HttpCall.crudURLPrefix(provider) + s"data/$id/data.xml"

        values.foreach { case (id, value) =>
          HttpAssert.put(dataURL(id), Specific(1), HttpCall.XML(testFormData(value)), StatusCode.Created)
        }

        HttpCall.assertCall(
          HttpCall.SolicitedRequest(
            path              = HttpCall.searchURLPrefix(provider),
            version           = Unspecified,
            method            = POST,
            body              = Some(HttpCall.XML(searchRequest)),
            xmlResponseFilter = Some(searchResultFilter)
          ),
          HttpCall.ExpectedResponse(
            code = StatusCode.Ok,
            body = Some(HttpCall.XML(searchResult))
          )
        )
      }
    }

  // Filter the search results by removing the date attributes on the 'document' elements and by sorting the 'document'
  // elements by name
  def searchResultFilter(document: Document): Document = {
    import scala.xml._

    def withoutDateAttributes(elem: Elem): Elem = {
      val filteredAttributes = elem.attributes.filter {
        case Attribute(key, _, _) if Set("created", "last-modified").contains(key) => false
        case _                                                                      => true
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
    val filteredResultDocuments = sortedByNameAttribute(rootElem.child.collect { case elem: Elem => elem }.map(withoutDateAttributes))

    NodeConversions.elemToOrbeonDom(rootElem.copy(child = filteredResultDocuments))
  }

  // Make sure there's no whitespace around the value
  def testFormData(value: String): Document =
    <form xmlns:fr="http://orbeon.org/oxf/xml/form-runner" fr:data-format-version="4.0.0">
      <section-1>
        <control-1>{value}</control-1>
      </section-1>
    </form>.toDocument

  def testFormDefinition(provider: Provider, formName: String = DefaultFormName): Document =
    <xh:html
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xxi="http://orbeon.org/oxf/xml/xinclude"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:map="http://www.w3.org/2005/xpath-functions/map"
        xmlns:array="http://www.w3.org/2005/xpath-functions/array"
        xmlns:math="http://www.w3.org/2005/xpath-functions/math"
        xmlns:exf="http://www.exforms.org/exf/1-0"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:fb="http://orbeon.org/oxf/xml/form-builder">
      <xh:head>
        <xh:title>{formName}</xh:title>
        <xf:model id="fr-form-model" xxf:expose-xpath-types="true" xxf:analysis.calculate="true">
          <!-- Main instance -->
          <xf:instance id="fr-form-instance" xxf:exclude-result-prefixes="#all" xxf:index="id">
            <form>
              <section-1>
                <grid-1>
                  <control-1/>
                </grid-1>
              </section-1>
            </form>
          </xf:instance>
          <!-- Bindings -->
          <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')">
            <xf:bind id="section-1-bind" name="section-1" ref="section-1">
              <xf:bind id="grid-1-bind" ref="grid-1" name="grid-1">
                <xf:bind id="control-1-bind" name="control-1" ref="control-1"/>
              </xf:bind>
            </xf:bind>
          </xf:bind>
          <!-- Metadata -->
          <xf:instance id="fr-form-metadata" xxf:readonly="true" xxf:exclude-result-prefixes="#all">
            <metadata>
              <application-name>{provider.entryName}</application-name>
              <form-name>{formName}</form-name>
              <title xml:lang="en">{formName}</title>
              <description xml:lang="en"></description>
              <created-with-version>2022.1-SNAPSHOT PE</created-with-version>
              <email>
                <templates>
                  <template name="default">
                    <form-fields></form-fields>
                  </template>
                </templates>
                <parameters></parameters>
              </email>
              <grid-tab-order>default</grid-tab-order>
              <library-versions></library-versions>
              <updated-with-version>2022.1-SNAPSHOT PE</updated-with-version>
              <migration version="2019.1.0">
                {{"migrations":[ {{"containerPath": [ {{"value": "section-1"}}], "newGridElem": {{"value": "grid-1"}}, "afterElem": null, "content": [ {{"value": "control-1"}}], "topLevel": true}}]}}
              </migration>
            </metadata>
          </xf:instance>
          <!-- Attachments -->
          <xf:instance id="fr-form-attachments" xxf:exclude-result-prefixes="#all">
            <attachments></attachments>
          </xf:instance>
          <!-- All form resources -->
          <xf:instance xxf:readonly="true" id="fr-form-resources" xxf:exclude-result-prefixes="#all">
            <resources>
              <resource xml:lang="en">
                <section-1>
                  <label>Untitled Section</label>
                </section-1>
                <control-1>
                  <label>Text field</label>
                  <hint></hint>
                </control-1>
              </resource>
            </resources>
          </xf:instance>
        </xf:model>
      </xh:head>
      <xh:body>
        <fr:view>
          <fr:body
              xmlns:p="http://www.orbeon.com/oxf/pipeline"
              xmlns:xbl="http://www.w3.org/ns/xbl"
              xmlns:oxf="http://www.orbeon.com/oxf/processors">
            <fr:section id="section-1-section" bind="section-1-bind">
              <xf:label ref="$form-resources/section-1/label"></xf:label>
              <fr:grid id="grid-1-grid" bind="grid-1-bind">
                <fr:c y="1" x="1" w="6">
                  <xf:input id="control-1-control" bind="control-1-bind">
                    <fr:index>
                      <fr:summary-show/>
                    </fr:index>
                    <xf:label ref="$form-resources/control-1/label"></xf:label>
                    <xf:hint ref="$form-resources/control-1/hint"></xf:hint>
                    <xf:alert ref="$fr-resources/detail/labels/alert"></xf:alert>
                  </xf:input>
                </fr:c>
                <fr:c y="1" x="7" w="6"/>
              </fr:grid>
            </fr:section>
          </fr:body>
        </fr:view>
      </xh:body>
    </xh:html>.toDocument
}
