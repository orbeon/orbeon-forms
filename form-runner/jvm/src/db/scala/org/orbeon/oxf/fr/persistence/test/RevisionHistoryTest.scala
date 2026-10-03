/**
 * Copyright (C) 2025 Orbeon, Inc.
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
import org.orbeon.oxf.fr.Version.Specific
import org.orbeon.oxf.fr.persistence.db.Connect
import org.orbeon.oxf.fr.persistence.http.HttpCall
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport, XFormsSupport}
import org.orbeon.oxf.util.{IndentedLogger, LoggerFactory, XPath}
import org.orbeon.scaxon.SimplePath.{NodeInfoOps, NodeInfoSeqOps}
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.scalatest.funspec.AnyFunSpecLike

import java.nio.charset.StandardCharsets


class RevisionHistoryTest
  extends DocumentTestBase
    with XFormsSupport
    with ResourceManagerSupport
    with AnyFunSpecLike {

  private implicit val Logger: IndentedLogger =
    new IndentedLogger(LoggerFactory.createLogger(classOf[RevisionHistoryTest]), true)

  describe("Revision History API") {

    it("must return revision history with diffs") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (_, provider) =>

          val testForm = TestForm(provider, controls = Seq(TestForm.Control("Control")))
          val version  = Specific(1)

          testForm.putFormDefinition(version)

          val credentials = TestForm.credentials("john@example.org")

          // Initial form data (revision 1)
          val documentId = "test-doc-1"
          testForm.putSingleFormData(
            version        = version,
            id             = documentId,
            values         = Seq("Initial Value"),
            update         = false,
            credentialsOpt = credentials.some
          )

          // Revision 2
          testForm.putSingleFormData(
            version        = version,
            id             = documentId,
            values         = Seq("Updated Value"),
            update         = true,
            credentialsOpt = credentials.some
          )

          // Revision 3
          testForm.putSingleFormData(
            version        = version,
            id             = documentId,
            values         = Seq(""),
            update         = true,
            credentialsOpt = credentials.some
          )

          val historyUrl = s"history/${provider.entryName}/${HttpCall.DefaultFormName}/$documentId"

          // Test 1: get revision history with diffs
          val historyResponse = HttpCall.get(
            url     = historyUrl + "?page-number=1&page-size=10&include-diffs=true&lang=en",
            version = version
          )

          assert(historyResponse._1 == StatusCode.Ok)

          val historyDoc = XFormsCrossPlatformSupport.stringToTinyTree(
            configuration  = XPath.GlobalConfiguration,
            string         = new String(historyResponse._3.get, StandardCharsets.UTF_8),
            handleXInclude = false,
            handleLexical  = false
          )

          val documents = historyDoc.rootElement / "document"
          assert(documents.size == 3)

          // Check value change from "Updated Value" to "" (revision 2 to 3)
          val newestDoc     = documents.head
          val newestDiffs   = newestDoc / "diffs" / "diff"

          assert(newestDiffs.size == 1)

          val newestDiffOpt = newestDiffs.find(_.attValue("type") == "value-changed")

          assert(newestDiffOpt.isDefined)
          assert((newestDiffOpt.get / "from").headOption.map(_.stringValue).contains("Updated Value"))
          assert((newestDiffOpt.get / "to"  ).headOption.map(_.stringValue).contains(""))

          // Test 2: get diff between specific revisions (revision 1 to 2)
          val olderTime = documents(2).attValue("modified-time")
          val newerTime = documents(1).attValue("modified-time")

          val diffResponse = HttpCall.get(
            url     = historyUrl + s"/diff?form-version=1&older-modified-time=$olderTime&newer-modified-time=$newerTime&lang=en",
            version = version
          )

          assert(diffResponse._1 == StatusCode.Ok)

          val diffDoc = XFormsCrossPlatformSupport.stringToTinyTree(
            configuration  = XPath.GlobalConfiguration,
            string         = new String(diffResponse._3.get, StandardCharsets.UTF_8),
            handleXInclude = false,
            handleLexical  = false
          )

          // Check value change from "Initial Value" to "Updated Value" (revision 1 to 2)
          val diffs   = diffDoc.rootElement / "diff"

          assert(diffs.size == 1)

          val diffOpt = diffs.find(_.attValue("type") == "value-changed")

          assert(diffOpt.isDefined)
          assert((diffOpt.get / "from").headOption.map(_.stringValue).contains("Initial Value"))
          assert((diffOpt.get / "to"  ).headOption.map(_.stringValue).contains("Updated Value"))
        }
      }
    }

    it("must return revision history with workflow stage changes") {
      withTestSafeRequestContext { implicit safeRequestCtx =>
        Connect.withOrbeonTables("form definition") { (_, provider) =>

          val testForm = TestForm(provider, controls = Seq(TestForm.Control("Control")))
          val version  = Specific(1)

          testForm.putFormDefinition(version)

          val credentials = TestForm.credentials("john@example.org")
          val documentId  = "test-doc-stage"

          // Revision 1: initial data, stage = "draft"
          testForm.putSingleFormData(
            version          = version,
            id               = documentId,
            values           = Seq("Value 1"),
            update           = false,
            credentialsOpt   = credentials.some,
            workflowStageOpt = "draft".some
          )

          // Revision 2: updated data ("Value 2"), stage = "review" (both value and stage changed)
          testForm.putSingleFormData(
            version          = version,
            id               = documentId,
            values           = Seq("Value 2"),
            update           = true,
            credentialsOpt   = credentials.some,
            workflowStageOpt = "review".some
          )

          // Revision 3: same data ("Value 2"), stage = "approved" (stage changed ONLY, no value change)
          testForm.putSingleFormData(
            version          = version,
            id               = documentId,
            values           = Seq("Value 2"),
            update           = true,
            credentialsOpt   = credentials.some,
            workflowStageOpt = "approved".some
          )

          // Revision 4: updated data ("Value 3"), stage = "approved" (value changed ONLY, no stage change)
          testForm.putSingleFormData(
            version          = version,
            id               = documentId,
            values           = Seq("Value 3"),
            update           = true,
            credentialsOpt   = credentials.some,
            workflowStageOpt = "approved".some
          )

          // Revision 5: same data ("Value 3"), stage cleared to None
          testForm.putSingleFormData(
            version          = version,
            id               = documentId,
            values           = Seq("Value 3"),
            update           = true,
            credentialsOpt   = credentials.some,
            workflowStageOpt = None
          )

          val historyUrl = s"history/${provider.entryName}/${HttpCall.DefaultFormName}/$documentId"

          // Test 1: get revision history with diffs
          val historyResponse = HttpCall.get(
            url     = historyUrl + "?page-number=1&page-size=10&include-diffs=true&lang=en",
            version = version
          )

          assert(historyResponse._1 == StatusCode.Ok)

          val historyDoc = XFormsCrossPlatformSupport.stringToTinyTree(
            configuration  = XPath.GlobalConfiguration,
            string         = new String(historyResponse._3.get, StandardCharsets.UTF_8),
            handleXInclude = false,
            handleLexical  = false
          )

          val documents = historyDoc.rootElement / "document"
          assert(documents.size == 5)

          // Check stages on documents
          assert(documents(0).attValueOpt("stage").isEmpty)
          assert(documents(1).attValueOpt("stage").contains("approved"))
          assert(documents(2).attValueOpt("stage").contains("approved"))
          assert(documents(3).attValueOpt("stage").contains("review"))
          assert(documents(4).attValueOpt("stage").contains("draft"))

          // Revision 5 (documents(0)): stage cleared ("approved" -> "")
          val rev5Diffs = documents(0) / "diffs" / "diff"
          assert(!rev5Diffs.exists(_.attValue("type") == "value-changed"))
          val rev5StageDiffOpt = rev5Diffs.find(_.attValue("type") == "workflow-stage-changed")
          assert(rev5StageDiffOpt.isDefined)
          assert((rev5StageDiffOpt.get / "from").headOption.map(_.stringValue).contains("approved"))
          assert((rev5StageDiffOpt.get / "to"  ).headOption.map(_.stringValue).contains(""))

          // Revision 4 (documents(1)): value changed, no stage changed
          val rev4Diffs = documents(1) / "diffs" / "diff"
          assert(rev4Diffs.exists(_.attValue("type") == "value-changed"))
          assert(!rev4Diffs.exists(_.attValue("type") == "workflow-stage-changed"))

          // Revision 3 (documents(2)): stage changed from "review" to "approved", no value change
          val rev3Diffs = documents(2) / "diffs" / "diff"
          assert(!rev3Diffs.exists(_.attValue("type") == "value-changed"))
          val rev3StageDiffOpt = rev3Diffs.find(_.attValue("type") == "workflow-stage-changed")
          assert(rev3StageDiffOpt.isDefined)
          assert((rev3StageDiffOpt.get / "from").headOption.map(_.stringValue).contains("review"))
          assert((rev3StageDiffOpt.get / "to"  ).headOption.map(_.stringValue).contains("approved"))

          // Revision 2 (documents(3)): both stage changed (draft -> review) and value changed (Value 1 -> Value 2)
          val rev2Diffs = documents(3) / "diffs" / "diff"
          val rev2StageDiffOpt = rev2Diffs.find(_.attValue("type") == "workflow-stage-changed")
          assert(rev2StageDiffOpt.isDefined)
          assert((rev2StageDiffOpt.get / "from").headOption.map(_.stringValue).contains("draft"))
          assert((rev2StageDiffOpt.get / "to"  ).headOption.map(_.stringValue).contains("review"))
          val rev2ValueDiffOpt = rev2Diffs.find(_.attValue("type") == "value-changed")
          assert(rev2ValueDiffOpt.isDefined)
          assert((rev2ValueDiffOpt.get / "from").headOption.map(_.stringValue).contains("Value 1"))
          assert((rev2ValueDiffOpt.get / "to"  ).headOption.map(_.stringValue).contains("Value 2"))

          // Test 2: standalone diff API between revision 2 and revision 3 (stage change only)
          val rev2Time = documents(3).attValue("modified-time")
          val rev3Time = documents(2).attValue("modified-time")

          val diffResponse = HttpCall.get(
            url     = historyUrl + s"/diff?form-version=1&older-modified-time=$rev2Time&newer-modified-time=$rev3Time&lang=en",
            version = version
          )

          assert(diffResponse._1 == StatusCode.Ok)

          val diffDoc = XFormsCrossPlatformSupport.stringToTinyTree(
            configuration  = XPath.GlobalConfiguration,
            string         = new String(diffResponse._3.get, StandardCharsets.UTF_8),
            handleXInclude = false,
            handleLexical  = false
          )

          val diffs = diffDoc.rootElement / "diff"
          assert(diffs.size == 1)
          val stageDiffOpt = diffs.find(_.attValue("type") == "workflow-stage-changed")
          assert(stageDiffOpt.isDefined)
          assert((stageDiffOpt.get / "from").headOption.map(_.stringValue).contains("review"))
          assert((stageDiffOpt.get / "to"  ).headOption.map(_.stringValue).contains("approved"))

          // Test 3: standalone diff API between revision 4 and revision 5 (stage cleared)
          val rev4Time = documents(1).attValue("modified-time")
          val rev5Time = documents(0).attValue("modified-time")

          val diffResponse2 = HttpCall.get(
            url     = historyUrl + s"/diff?form-version=1&older-modified-time=$rev4Time&newer-modified-time=$rev5Time&lang=en",
            version = version
          )

          assert(diffResponse2._1 == StatusCode.Ok)

          val diffDoc2 = XFormsCrossPlatformSupport.stringToTinyTree(
            configuration  = XPath.GlobalConfiguration,
            string         = new String(diffResponse2._3.get, StandardCharsets.UTF_8),
            handleXInclude = false,
            handleLexical  = false
          )

          val diffs2 = diffDoc2.rootElement / "diff"
          assert(diffs2.size == 1)
          val stageDiffOpt2 = diffs2.find(_.attValue("type") == "workflow-stage-changed")
          assert(stageDiffOpt2.isDefined)
          assert((stageDiffOpt2.get / "from").headOption.map(_.stringValue).contains("approved"))
          assert((stageDiffOpt2.get / "to"  ).headOption.map(_.stringValue).contains(""))
        }
      }
    }
  }
}
