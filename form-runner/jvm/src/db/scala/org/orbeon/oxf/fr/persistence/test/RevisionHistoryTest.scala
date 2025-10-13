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
  }
}
