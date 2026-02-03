/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import cats.syntax.option.*
import org.orbeon.oxf.test.TestHttpClient.StaticState
import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.xforms.state.XFormsStaticStateCache
import org.scalatest.funspec.AnyFunSpecLike


class CacheTest
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport{

  describe("Form Runner static cache") {

    val AppName  = "tests"
    val FormName = "noscript-false-pdf-template-wizard-true"

    val DocumentId1 = "6578e2e0e7911fd9ba284aefaea671cbfb814851"
    val DocumentId2 = "15c4a18428496faa1212d86f58c62d9d3c51cf0d"

    def runAndAssert(
      form               : String,
      mode               : String,
      query              : IterableOnce[(String, String)] = Nil,
      background         : Boolean                        = false,
    )(
      expectedInitialHit : Boolean,
      expectedInitialRead: Boolean,
    ): Unit = {

      def testOne(documentIdOpt: Option[String], expectedInitialHit : Boolean, expectedInitialRead: Boolean): Unit = {

        val (_, _, events) =
          runFormRunner(
            AppName,
            form,
            mode,
            documentId = documentIdOpt,
            query      = query,
            background = background
          )

        def staticStateFoundOpt: Option[Boolean] =
          events.collectFirst { case StaticState(_, found, _) => found }

        def documentInputRead: Boolean =
          events.exists {
            case StaticState(read, _, _) => read
            case _                       => false
          }

        def staticStateHasTemplateOpt: Option[Boolean] =
          events
            .collectFirst { case StaticState(_, _, digest) => digest }
            .flatMap(XFormsStaticStateCache.findDocument)
            .map(_._1.template.isDefined)

        val path = buildFormRunnerPath(AppName, form, mode, documentIdOpt, query, background)

        describe(s"$path") {
          it(s"initial hit `$expectedInitialHit`") {
            assert(staticStateFoundOpt.contains(expectedInitialHit))
          }

          it(s"initial read `$expectedInitialRead`") {
            if (documentInputRead != expectedInitialRead) {
              pprint.pprintln(s"xxx failed, events:")
              pprint.pprintln(events)
            }
            assert(documentInputRead == expectedInitialRead)
          }

          it(s"template exists") {
            assert(staticStateHasTemplateOpt.contains(true))
          }
        }
      }

      if (mode == "new") {
        testOne(None, expectedInitialHit, expectedInitialRead)
      } else {
        testOne(DocumentId1.some, expectedInitialHit,        expectedInitialRead)
        testOne(DocumentId2.some, expectedInitialHit = true, expectedInitialRead = false)
      }
    }

    runAndAssert(FormName, "new")(
      expectedInitialHit = false,
      expectedInitialRead = true
    )
    runAndAssert(FormName, "edit")(
      expectedInitialHit = true,  // there should not be any differences in compiled forms between `new` and `edit`
      expectedInitialRead = false // and this is also reflected in `FormRunnerConfig`, unless for example TOC settings are different
    )
    runAndAssert(FormName, "view")(
      expectedInitialHit = false, // for `view`, the compiled form is different
      expectedInitialRead = true
    )
    runAndAssert(FormName, "pdf", query = List("fr-use-pdf-template" -> "false"), background = true)(
      expectedInitialHit = true,  // it happens that when not using PDF templates, the compiled form is the same as in `view`
      expectedInitialRead = true  // but `FormRunnerConfig` is different, so we read the input again
    )
    runAndAssert(FormName, "pdf")(
      expectedInitialHit = false, // when using PDF templates, the compiled form is different
      expectedInitialRead = true
    )
  }
}
