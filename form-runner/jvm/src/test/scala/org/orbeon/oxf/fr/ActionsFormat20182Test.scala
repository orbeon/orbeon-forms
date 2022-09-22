/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import org.orbeon.oxf.test.{DocumentTestBase, ResourceManagerSupport}
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.xforms.XFormsId
import org.scalatest.funspec.AnyFunSpecLike

class ActionsFormat20182Test
  extends DocumentTestBase
     with ResourceManagerSupport
     with AnyFunSpecLike
     with FormRunnerSupport {

  describe("Form Runner actions in 2018.2 format") {

    describe("Binary HTTP service within `fr:data-iterate` conditionally sets attachment") {

      val (processorService, docOpt, _) =
        runFormRunner("tests", "actions-format-20182", "new", document = "", initialize = true)

      val doc = docOpt.get

      it("must pass all service result checks") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            for {
              (expectedSizeOpt, i) <- List(Some(392), None, Some(408), None).zipWithIndex
              control              = resolveObject[XFormsValueControl]("my-attachment-control", indexes = List(i + 1)).get
              value                = control.getValue
            } locally {
              expectedSizeOpt match {
                case Some(expectedSize) =>
                  assert(PathUtils.getFirstQueryParameter(value, "mediatype") contains "image/png")
                  assert(PathUtils.getFirstQueryParameter(value, "size")      contains expectedSize.toString) // I suppose that sizes can change if the service changes…
                case None =>
                  assert(value.isAllBlank)
              }
            }
          }
        }
      }
    }

    describe("HTTP service error within `fr:data-iterate`") {

      val (processorService, docOpt, _) =
        runFormRunner("tests", "actions-format-20182-error", "new", document = "", initialize = true)

      val doc = docOpt.get

      it("must pass all service result checks") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert("First" == resolveObject[XFormsValueControl]("my-result-control").get.getValue)
          }
        }
      }
    }

    describe("#4116: explicit iteration context with `fr:data-iterate`") {

      val (processorService, docOpt, _) =
        runFormRunner("tests", "actions-format-20182-context", "new", document = "", initialize = true)

      val doc = docOpt.get

      it("must pass all service result checks") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert("21" == resolveObject[XFormsValueControl]("my-result-1-control").get.getValue)
            assert("22" == resolveObject[XFormsValueControl]("my-result-2-control").get.getValue)
          }
        }
      }
    }

    describe("#4204: nested `fr:data-iterate`") {

      val (processorService, docOpt, _) =
        runFormRunner("tests", "actions-format-20182-data-iterate", "new", document = "", initialize = true)

      val doc = docOpt.get

      it("must check the resulting iteration sizes") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {

            def assertCount(controlName: String, size: Int): Unit = {
              val controlNodes =
                FormRunner.resolveTargetRelativeToActionSourceFromControlsOpt(
                  container              = doc,
                  actionSourceAbsoluteId = XFormsId.effectiveIdToAbsoluteId(Names.FormModel),
                  targetControlName      = controlName,
                  followIndexes          = false
                ).toList.flatten

              assert(size == controlNodes.size)
            }

            assertCount("year", 6)
            assertCount("firstname", 13)
          }
        }
      }
    }
  }
}