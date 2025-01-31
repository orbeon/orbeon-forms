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
    controlIdToMaxSizePerFile                       : Map[String, Long],
    controlIdToMaxSizeAggregatePerControl           : Map[String, Long],
    controlIdToMaxFilesPerControl                   : Map[String, Int],
    controlIdToCurrentUploadSizeAggregateForControl : Map[String, Long],
    currentUploadSizeAggregateForForm               : Option[Long],
    uploadMaxSizePerFileProperty                    : MaximumSize,
    uploadMaxSizeAggregatePerControlProperty        : MaximumSize,
    uploadMaxSizeAggregatePerFormProperty           : MaximumSize
  ) extends UploadCheckerLogic {
    def attachmentMaxSizeValidationMipFor(controlEffectiveId: String, validationFunctionName: String): Option[String] =
      validationFunctionName match {
        case ValidationFunctionNames.UploadMaxSizePerFile             => controlIdToMaxSizePerFile            .get(controlEffectiveId) map (_.toString)
        case ValidationFunctionNames.UploadMaxSizeAggregatePerControl => controlIdToMaxSizeAggregatePerControl.get(controlEffectiveId) map (_.toString)
        case _                                                        => throw new IllegalArgumentException(s"Unexpected validation function name: $validationFunctionName")
      }

    def currentUploadSizeAggregateForControl(controlEffectiveId: String): Option[Long] =
      controlIdToCurrentUploadSizeAggregateForControl.get(controlEffectiveId)

    def currentUploadFilesForControl(controlEffectiveId: String): Option[Int] =
      controlIdToMaxFilesPerControl.get(controlEffectiveId)
  }

  describe("With `upload.max-size-per-file` property only") {
    for (limit <- List(LimitedSize(0L), LimitedSize(1000L), UnlimitedSize))
      it(s"limit = `$limit`") {
        assert(limit == TestUploadCheckerLogic(
          controlIdToMaxSizePerFile                       = Map(),
          controlIdToMaxSizeAggregatePerControl           = Map(),
          controlIdToMaxFilesPerControl                   = Map(),
          controlIdToCurrentUploadSizeAggregateForControl = Map(),
          currentUploadSizeAggregateForForm               = None,
          uploadMaxSizePerFileProperty                    = limit,
          uploadMaxSizeAggregatePerControlProperty        = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty           = UnlimitedSize
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
        assert(expectedLimit == TestUploadCheckerLogic(
          controlIdToMaxSizePerFile                       = Map("control1" -> 1000L, "control2" -> 2000L),
          controlIdToMaxSizeAggregatePerControl           = Map(),
          controlIdToMaxFilesPerControl                   = Map(),
          controlIdToCurrentUploadSizeAggregateForControl = Map(),
          currentUploadSizeAggregateForForm               = None,
          uploadMaxSizePerFileProperty                    = limit,
          uploadMaxSizeAggregatePerControlProperty        = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty           = UnlimitedSize
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
        assert(expectedLimit == TestUploadCheckerLogic(
          controlIdToMaxSizePerFile                       = Map("control1" -> 1000L, "control2" -> 2000L),
          controlIdToMaxSizeAggregatePerControl           = Map(),
          controlIdToMaxFilesPerControl                   = Map(),
          controlIdToCurrentUploadSizeAggregateForControl = Map(),
          currentUploadSizeAggregateForForm               = Some(currentUploadSizeFormAggregate),
          uploadMaxSizePerFileProperty                    = LimitedSize(3000),
          uploadMaxSizeAggregatePerControlProperty        = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty           = formAggregateLimit
        ).uploadMaxSizeForControl(controlId))
      }
    }

    it("must throw if no current aggregate size can be provided") {
      assertThrows[IllegalArgumentException] {
        TestUploadCheckerLogic(
          controlIdToMaxSizePerFile                       = Map("control1" -> 1000L, "control2" -> 2000L),
          controlIdToMaxSizeAggregatePerControl           = Map(),
          controlIdToMaxFilesPerControl                   = Map(),
          controlIdToCurrentUploadSizeAggregateForControl = Map(),
          currentUploadSizeAggregateForForm               = None,
          uploadMaxSizePerFileProperty                    = LimitedSize(3000),
          uploadMaxSizeAggregatePerControlProperty        = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty           = LimitedSize(10000)
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

        assert(expectedLimit == TestUploadCheckerLogic(
          controlIdToMaxSizePerFile                       = Map(),
          controlIdToMaxSizeAggregatePerControl           = Map("control1" -> 1000L, "control2" -> 1000L, "control3" -> 3000L),
          controlIdToMaxFilesPerControl                   = Map(),
          controlIdToCurrentUploadSizeAggregateForControl = controlIdToCurrentUploadSizeControlAggregate,
          currentUploadSizeAggregateForForm               = Some(currentUploadSizeFormAggregate),
          uploadMaxSizePerFileProperty                    = LimitedSize(1000L),
          uploadMaxSizeAggregatePerControlProperty        = UnlimitedSize,
          uploadMaxSizeAggregatePerFormProperty           = LimitedSize(4000L)
        ).uploadMaxSizeForControl(controlId))
      }
    }
  }
}
