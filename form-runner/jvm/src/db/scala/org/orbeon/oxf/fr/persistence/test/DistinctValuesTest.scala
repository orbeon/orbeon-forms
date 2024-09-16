/**
 * Copyright (C) 2023 Orbeon, Inc.
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

import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.fr.Version.Specific
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, LoggerFactory, XPath}
import org.orbeon.oxf.xml.dom.Converter.*
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.concurrent.Futures.{interval, timeout}
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.time.{Second, Seconds, Span}


class DistinctValuesTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[DistinctValuesTest]), true)
  private implicit val coreCrossPlatformSupport = CoreCrossPlatformSupport

  object Metadata {
    val CreatedBy      = "created-by"
    val LastModifiedBy = "last-modified-by"
    val WorkflowStage  = "workflow-stage"

    val values: Seq[String] = Seq(CreatedBy, LastModifiedBy, WorkflowStage)
  }

  describe("Distinct Values API") {

    it("must work with no form data") {
      testWithControlValues(
        formData              = Seq(),
        expectedControlValues = Seq()
      )
    }

    it("must work with multiple, distinct control values") {
      testWithControlValues(
        formData              = Seq(FormData("1", "a"), FormData("2", "b"), FormData("3", "c")),
        expectedControlValues = Seq("a", "b", "c")
      )
    }

    it("must work with multiple, redundant control values") {
      testWithControlValues(
        formData              = Seq(
          FormData("1", "a"),
          FormData("2", "b"),
          FormData("3", "c"),
          FormData("4", "a"),
          FormData("5", "b"),
          FormData("6", "c")
        ),
        expectedControlValues = Seq("a", "b", "c")
      )
    }

    it("must work with created by values") {
      val formData = Seq(
        FormData("1", "a", createdByOpt = Some("user3")),
        FormData("2", "b", createdByOpt = Some("user2")),
        FormData("3", "c", createdByOpt = Some("user1"))
      )

      testWithControlValues(
        formData               = formData,
        expectedControlValues  = Seq("a", "b", "c"),
        expectedMetadataValues = Map(Metadata.CreatedBy -> Seq("user1", "user2", "user3"))
      )

      // Test that distinct created by values are not returned if not required
      testWithControlValues(
        formData               = formData,
        expectedControlValues  = Seq("a", "b", "c"),
        expectedMetadataValues = Map()
      )
    }

    it("must work with last modified by values") {
      val formData = Seq(
        FormData("1", "a", createdByOpt = Some("user3"), lastModifiedByOpt = Some("user6")),
        FormData("2", "b", createdByOpt = Some("user2"), lastModifiedByOpt = Some("user5")),
        FormData("3", "c", createdByOpt = Some("user1"), lastModifiedByOpt = Some("user4"))
      )

      testWithControlValues(
        formData               = formData,
        expectedControlValues  = Seq("a", "b", "c"),
        expectedMetadataValues = Map(
          Metadata.CreatedBy      -> Seq("user1", "user2", "user3"),
          Metadata.LastModifiedBy -> Seq("user4", "user5", "user6")
        )
      )

      // Test that distinct last modified by values are not returned if not required
      testWithControlValues(
        formData               = formData,
        expectedControlValues  = Seq("a", "b", "c"),
        expectedMetadataValues = Map()
      )
    }

    it("must work with workflow stage values") {
      val formData = Seq(
        FormData("1", "a", workflowStageOpt = Some("stage-1")),
        FormData("2", "b", workflowStageOpt = Some("stage-2")),
        FormData("3", "c", workflowStageOpt = Some("stage-3"))
      )

      testWithControlValues(
        formData               = formData,
        expectedControlValues  = Seq("a", "b", "c"),
        expectedMetadataValues = Map(Metadata.WorkflowStage -> Seq("stage-1", "stage-2", "stage-3"))
      )

      // Test that distinct workflow stage values are not returned if not required
      testWithControlValues(
        formData               = formData,
        expectedControlValues  = Seq("a", "b", "c"),
        expectedMetadataValues = Map()
      )
    }

    def testWithControlValues(
      formData              : Seq[FormData],
      expectedControlValues : Seq[String],
      expectedMetadataValues: Map[String, Seq[String]] = Map()
    ): Unit = withTestExternalContext { implicit externalContext =>
      Connect.withOrbeonTables("form definition") { (connection, provider) =>

        val testForm    = TestForm(provider, controls = Seq(TestForm.Control("control label")))
        val controlPath = testForm.controlPath(0)

        val request =
          <distinct-values>{
            <query path={controlPath}/> ++ expectedMetadataValues.keys.map { metadata =>
              <query metadata={metadata}/>
            }
          }</distinct-values>.toDocument

        val version = Specific(1)

        testForm.putFormDefinition(version)

        // TODO: we might want to publish the form and remove the `migration` metadata from the form definition
        /*val publishUrl = "/fr/service/publish"
        HttpCall.post(publishUrl, Unspecified, HttpCall.XML(testForm.formDefinition(provider)))*/

        testForm.putFormData(version, formData)

        val url = HttpCall.distinctValueURLPrefix(provider)

        eventually(timeout(Span(10, Seconds)), interval(Span(1, Second))) {

          val httpResponse = HttpCall.post(
            url     = url,
            version = version,
            body    = HttpCall.XML(request)
          )

          val result = XFormsCrossPlatformSupport.readTinyTree(
            XPath.GlobalConfiguration,
            httpResponse.content.stream,
            url,
            handleXInclude = false,
            handleLexical  = false
          )

          val distinctControlValues =
            for {
              control <- result.rootElement / "query"
              if control.attValue("path") == controlPath
              value   <- control / "value"
            } yield value.stringValue

          // Ignore order
          assert(distinctControlValues.sorted == expectedControlValues.sorted)

          expectedMetadataValues.keys.foreach { metadata =>
            val metadataValues =
              for {
                metadataElement <- result.rootElement / "query"
                if metadataElement.attValue("metadata") == metadata
                value           <- metadataElement / "value"
              } yield value.stringValue

            // Ignore order
            assert(metadataValues.sorted == expectedMetadataValues(metadata).sorted)
          }
        }
      }
    }
  }
}
