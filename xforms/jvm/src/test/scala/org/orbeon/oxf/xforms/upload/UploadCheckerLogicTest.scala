/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.upload

import org.orbeon.datatypes.MaximumSize
import org.orbeon.datatypes.MaximumSize.{LimitedSize, UnlimitedSize}
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunctionNames
import org.scalatest.funspec.AnyFunSpec


class UploadCheckerLogicTest extends AnyFunSpec {

  case class TestUploadCheckerLogic(
    controlIdToMaxSize                           : Map[String, Long],
    controlIdToMaxSizeControlAggregate           : Map[String, Long],
    controlIdToCurrentUploadSizeControlAggregate : Map[String, Long],
    currentUploadSizeAggregateForForm            : Option[Long],
    uploadMaxSizePerFileProperty                 : MaximumSize,
    uploadMaxSizeAggregatePerControlProperty     : MaximumSize,
    uploadMaxSizeAggregatePerFormProperty        : MaximumSize
  ) extends UploadCheckerLogic {
    def attachmentMaxSizeValidationMipFor(controlEffectiveId: String, validationFunctionName: String): Option[String] =
      validationFunctionName match {
        case ValidationFunctionNames.UploadMaxSize                 => controlIdToMaxSize                .get(controlEffectiveId) map (_.toString)
        case ValidationFunctionNames.UploadMaxSizeControlAggregate => controlIdToMaxSizeControlAggregate.get(controlEffectiveId) map (_.toString)
        case _                                                     => throw new IllegalArgumentException(s"Unexpected validation function name: $validationFunctionName")
      }

    def currentUploadSizeAggregateForControl(controlEffectiveId: String): Option[Long] =
      controlIdToCurrentUploadSizeControlAggregate.get(controlEffectiveId)
  }

  describe("With `upload.max-size-per-file` property only") {
    for (limit <- List(LimitedSize(0L), LimitedSize(1000L), UnlimitedSize))
      it(s"limit = `$limit`") {
        assert(limit === TestUploadCheckerLogic(
          controlIdToMaxSize                           = Map(),
          controlIdToMaxSizeControlAggregate           = Map(),
          controlIdToCurrentUploadSizeControlAggregate = Map(),
          currentUploadSizeAggregateForForm            = None,
          uploadMaxSizePerFileProperty                 = limit,
          uploadMaxSizeAggregatePerControlProperty     = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty        = UnlimitedSize
        ).uploadMaxSizeForControl("dummy"))
      }
  }

  describe("With `upload.max-size-per-file` property and limit per control") {

    val expectations = List(
      LimitedSize(0L) -> List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(2000L),
        "control3" -> LimitedSize(0L)

      ),
      LimitedSize(1000L) -> List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(2000L),
        "control3" -> LimitedSize(1000L)

      ),
      UnlimitedSize -> List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(2000L),
        "control3" -> UnlimitedSize
      )
    )

    for {
      (limit, controlAndExpected) <- expectations
      (controlId, expectedLimit)  <- controlAndExpected
    } locally {
      it(s"limit = `$limit`, controlId = `$controlId`") {
        assert(expectedLimit === TestUploadCheckerLogic(
          controlIdToMaxSize                           = Map("control1" -> 1000L, "control2" -> 2000L),
          controlIdToMaxSizeControlAggregate           = Map(),
          controlIdToCurrentUploadSizeControlAggregate = Map(),
          currentUploadSizeAggregateForForm            = None,
          uploadMaxSizePerFileProperty                 = limit,
          uploadMaxSizeAggregatePerControlProperty     = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty        = UnlimitedSize
        ).uploadMaxSizeForControl(controlId))
      }
    }
  }

  describe("With `upload.max-size-per-file` property and form aggregate limit") {

    val expectations = List(
      (1000L, LimitedSize(0L), List(
          "control1" -> LimitedSize(0L),
          "control2" -> LimitedSize(0L),
          "control3" -> LimitedSize(0L)
        )
      ),
      (1000L, LimitedSize(1000L), List(
          "control1" -> LimitedSize(0L),
          "control2" -> LimitedSize(0L),
          "control3" -> LimitedSize(0L)
        )
      ),
      (1000L, LimitedSize(2000L), List(
          "control1" -> LimitedSize(1000L),
          "control2" -> LimitedSize(1000L),
          "control3" -> LimitedSize(1000L)
        )
      ),
      (1000L, LimitedSize(4000L), List(
          "control1" -> LimitedSize(1000L),
          "control2" -> LimitedSize(2000L),
          "control3" -> LimitedSize(3000L)
        )
      ),
      (1000L, UnlimitedSize, List(
          "control1" -> LimitedSize(1000L),
          "control2" -> LimitedSize(2000L),
          "control3" -> LimitedSize(3000L)
        )
      )
    )

    for {
      (currentUploadSizeFormAggregate, formAggregateLimit, controlAndExpected) <- expectations
      (controlId, expectedLimit)                                               <- controlAndExpected
    } locally {
      it(s"currentUploadSizeFormAggregate = `$currentUploadSizeFormAggregate`, formAggregateLimit = `$formAggregateLimit`, controlId = `$controlId`") {
        assert(expectedLimit === TestUploadCheckerLogic(
          controlIdToMaxSize                           = Map("control1" -> 1000L, "control2" -> 2000L),
          controlIdToMaxSizeControlAggregate           = Map(),
          controlIdToCurrentUploadSizeControlAggregate = Map(),
          currentUploadSizeAggregateForForm            = Some(currentUploadSizeFormAggregate),
          uploadMaxSizePerFileProperty                 = LimitedSize(3000),
          uploadMaxSizeAggregatePerControlProperty     = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty        = formAggregateLimit
        ).uploadMaxSizeForControl(controlId))
      }
    }

    it("must throw if no current aggregate size can be provided") {
      assertThrows[IllegalArgumentException] {
        TestUploadCheckerLogic(
          controlIdToMaxSize                           = Map("control1" -> 1000L, "control2" -> 2000L),
          controlIdToMaxSizeControlAggregate           = Map(),
          controlIdToCurrentUploadSizeControlAggregate = Map(),
          currentUploadSizeAggregateForForm            = None,
          uploadMaxSizePerFileProperty                 = LimitedSize(3000),
          uploadMaxSizeAggregatePerControlProperty     = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty        = LimitedSize(10000)
        ).uploadMaxSizeForControl("control1")
      }
    }
  }

  describe("With control aggregate limit") {

    val expectations = List(
      (Map("control1" -> 0L, "control2" -> 0L, "control3" -> 0L), List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(1000L),
        "control3" -> LimitedSize(1000L)
      )),
      (Map("control1" -> 1000L, "control2" -> 0L, "control3" -> 0L), List(
        "control1" -> LimitedSize(0L),
        "control2" -> LimitedSize(1000L),
        "control3" -> LimitedSize(1000L)
      )),
      (Map("control1" -> 0L, "control2" -> 1000L, "control3" -> 0L), List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(0L),
        "control3" -> LimitedSize(1000L)
      )),
      (Map("control1" -> 0L, "control2" -> 0L, "control3" -> 2000L), List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(1000L),
        "control3" -> LimitedSize(1000L)
      )),
      (Map("control1" -> 0L, "control2" -> 0L, "control3" -> 3000L), List(
        "control1" -> LimitedSize(1000L),
        "control2" -> LimitedSize(1000L),
        "control3" -> LimitedSize(0L)
      )),
      (Map("control1" -> 1000L, "control2" -> 0L, "control3" -> 2000L), List(
        "control1" -> LimitedSize(0L),
        "control2" -> LimitedSize(1000L),
        "control3" -> LimitedSize(1000L)
      )),
      (Map("control1" -> 1000L, "control2" -> 0L, "control3" -> 3000L), List(
        "control1" -> LimitedSize(0L),
        "control2" -> LimitedSize(0L),
        "control3" -> LimitedSize(0L)
      ))
    )

    for {
      (controlIdToCurrentUploadSizeControlAggregate, controlAndExpected) <- expectations
      (controlId, expectedLimit)                                         <- controlAndExpected
    } locally {
      it(s"controlIdToCurrentUploadSizeControlAggregate = `$controlIdToCurrentUploadSizeControlAggregate`, controlId = `$controlId`") {
        val currentUploadSizeFormAggregate = controlIdToCurrentUploadSizeControlAggregate.values.sum

        assert(expectedLimit === TestUploadCheckerLogic(
          controlIdToMaxSize                           = Map(),
          controlIdToMaxSizeControlAggregate           = Map("control1" -> 1000L, "control2" -> 1000L, "control3" -> 3000L),
          controlIdToCurrentUploadSizeControlAggregate = controlIdToCurrentUploadSizeControlAggregate,
          currentUploadSizeAggregateForForm            = Some(currentUploadSizeFormAggregate),
          uploadMaxSizePerFileProperty                 = LimitedSize(1000L),
          uploadMaxSizeAggregatePerControlProperty     = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty        = LimitedSize(4000L)
        ).uploadMaxSizeForControl(controlId))
      }
    }
  }
}
