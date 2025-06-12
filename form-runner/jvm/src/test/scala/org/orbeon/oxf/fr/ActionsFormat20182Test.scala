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
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.control.{XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.event.EventCollector
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
        runFormRunner("issue", "4067", "new", initialize = true)

      val doc = docOpt.get

      it("must pass all service result checks") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            for {
              (expectedSizeOpt, i) <- List(Some(386), None, Some(400), None).zipWithIndex
              control              = resolveObject[XFormsValueControl]("my-attachment-control", indexes = List(i + 1)).get
              value                = control.getValue(EventCollector.Throw)
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
        runFormRunner("issue", "4075", "new", initialize = true)

      val doc = docOpt.get

      it("must pass all service result checks") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert("First" == resolveObject[XFormsValueControl]("my-result-control").get.getValue(EventCollector.Throw))
          }
        }
      }
    }

    describe("#4116: explicit iteration context with `fr:data-iterate`") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "4116", "new", initialize = true)

      val doc = docOpt.get

      it("must pass all service result checks") {
        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {
            assert("21" == resolveObject[XFormsValueControl]("my-result-1-control").get.getValue(EventCollector.Throw))
            assert("22" == resolveObject[XFormsValueControl]("my-result-2-control").get.getValue(EventCollector.Throw))
          }
        }
      }
    }

    describe("#4204: nested `fr:data-iterate`") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "4204", "new", initialize = true)

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

    describe("#5484: `fr:copy-content`") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "5484", "new", initialize = true)

      val doc = docOpt.get

      it(s"must check that control values are initially copied") {

        val Expected = List(
          List(
            "control-3-control" -> "value 1-1",
            "control-4-control" -> "value 2-1",
            "control-5-control" -> "42",
          ),
          List(
            "control-3-control" -> "value 1-2",
            "control-4-control" -> "value 2-2",
            "control-5-control" -> "42",
          )
        )

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {

            for {
              (values, index)    <- Expected.zipWithIndex
              (controlId, value) <- values
            } locally {
              assert(value == resolveObject[XFormsValueControl](controlId, indexes = List(index + 1)).get.getValue(EventCollector.Throw))
            }
          }
        }
      }

      it(s"must check that new control values are subsequently copied") {

        val Expected = List(
          List(
            "control-3-control" -> "1-1",
            "control-4-control" -> "2-1",
            "control-5-control" -> "43",
          ),
          List(
            "control-3-control" -> "1-2",
            "control-4-control" -> "2-2",
            "control-5-control" -> "43",
          )
        )

        withTestExternalContext { _ =>
          withFormRunnerDocument(processorService, doc) {

            for {
              index         <- 1 to 2
              controlNumber <- 1 to 2
              controlId     = s"control-$controlNumber-control"
              newValue      = s"$controlNumber-$index"
            } locally {
              document.withOutermostActionHandler {
                setControlValue(resolveObject[XFormsValueControl](controlId, indexes = List(index)).get.effectiveId, newValue)
              }
            }

            document.withOutermostActionHandler {
              setControlValue(resolveObject[XFormsValueControl]("non-repeated-control-control").get.effectiveId, "43")
            }

            document.withOutermostActionHandler {
              dispatch(name = "DOMActivate", effectiveId = resolveObject[XFormsControl]("copy-data-button-no-warning-control").get.effectiveId)
            }

            for {
              (values, index)    <- Expected.zipWithIndex
              (controlId, value) <- values
            } locally {
              assert(value == resolveObject[XFormsValueControl](controlId, indexes = List(index + 1)).get.getValue(EventCollector.Throw))
            }
          }
        }
      }
    }

    describe("#6837/#6943: control resolution using variables") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "6837", "new", initialize = true)

      val doc = docOpt.get

      it("must run actions with the correct action source") {
        withTestExternalContext { implicit ec =>
          withFormRunnerDocument(processorService, doc) {

            activateControlWithEvent(resolveObject[XFormsControl]("navigate-control", indexes = List(1)).get.effectiveId)
            activateControlWithEvent(resolveObject[XFormsControl]("navigate-control", indexes = List(2)).get.effectiveId)

            assert("https://www.orbeon.com/?p=42" == resolveObject[XFormsValueControl]("out-control", indexes = List(1)).get.getValue(EventCollector.Throw))
            assert("https://doc.orbeon.com/?p=42" == resolveObject[XFormsValueControl]("out-control", indexes = List(2)).get.getValue(EventCollector.Throw))
          }
        }
      }
    }

    describe("#7083: initial value depending on service running before initial values are calculated") {

      val (processorService, docOpt, _) =
        runFormRunner("issue", "7083", "new", initialize = true)

      val doc = docOpt.get

      it("must have initialized the control value") {
        withTestExternalContext { implicit ec =>
          withFormRunnerDocument(processorService, doc) {
            assert("Overview" == resolveObject[XFormsValueControl]("foo-c-control").get.getValue(EventCollector.Throw))
          }
        }
      }
    }
  }
}