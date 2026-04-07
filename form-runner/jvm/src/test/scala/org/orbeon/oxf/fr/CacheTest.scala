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

    val DocumentId1 = "6578e2e0e7911fd9ba284aefaea671cbfb814851"
    val DocumentId2 = "15c4a18428496faa1212d86f58c62d9d3c51cf0d"

    def runAndAssert(
      app                : String,
      form               : String,
      mode               : String,
      query              : IterableOnce[(String, String)],
      background         : Boolean                        = false,
    )(
      expectedInitialHit : Boolean,
      expectedInitialRead: Boolean,
    ): Unit = {

      def testOne(documentIdOpt: Option[String], expectedInitialHit : Boolean, expectedInitialRead: Boolean): Unit = {

        val (_, _, events) =
          runFormRunner(
            app,
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

        val path = buildFormRunnerPath(app, form, mode, documentIdOpt, query, background)

        describe(s"$path") {
          it(s"initial hit `$expectedInitialHit`") {
            assert(staticStateFoundOpt.contains(expectedInitialHit))
          }

          it(s"initial read `$expectedInitialRead`") {
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

    val Expected = List(
      ("tests" -> "noscript-false-pdf-template-wizard-true") -> List(
        (("new" , Nil,                                    false), false, true ),
        (("edit", Nil,                                    false), true,  false), // no differences in compiled forms between `new` and `edit` unless for example TOC settings are different
        (("view", Nil,                                    false), false, true ), // for `view`, the compiled form is different
        (("pdf" , List("fr-use-pdf-template" -> "false"), true ), true,  true ), // it happens that when not using PDF templates, the compiled form is the same as in `view`
        (("pdf" , Nil,                                    false), false, true ), // when using PDF templates, the compiled form is different
      ),
      ("issue" -> "7519") -> List(
        (("new" , Nil,                                    false), false, true ), // same as other form
        (("edit", Nil,                                    false), true,  false), // same as other form
        (("view", Nil,                                    false), false, true ), // same as other form
        (("pdf" , List("fr-use-pdf-template" -> "false"), true ), false, true ), // PDF automatic is different as there is an `fr:pdf-automatic` binding
        (("pdf" , Nil,                                    false), true,  false), // no PDF template
      )
    )

    for {
      ((app, form), cases) <- Expected
      ((mode, query, background), expectedInitialHit, expectedInitialRead) <- cases
    } locally {
      runAndAssert(app, form, mode, query, background)(
        expectedInitialHit  = expectedInitialHit,
        expectedInitialRead = expectedInitialRead
      )
    }
  }
}
