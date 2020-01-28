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
import org.scalatest.funspec.AnyFunSpec


class UploadCheckerLogicTest extends AnyFunSpec {

  case class TestUploadCheckerLogic(
    controlIdToMaxSize             : Map[String, Long],
    currentUploadSizeAggregate     : Option[Long],
    uploadMaxSizeProperty          : MaximumSize,
    uploadMaxSizeAggregateProperty : MaximumSize
  ) extends UploadCheckerLogic {
    def findAttachmentMaxSizeValidationMipFor(controlEffectiveId: String): Option[String] =
      controlIdToMaxSize.get(controlEffectiveId) map (_.toString)
  }

  describe("With `upload.max-size` property only") {
    for (limit <- List(LimitedSize(0L), LimitedSize(1000L), UnlimitedSize))
      it(s"limit = `$limit`") {
        assert(limit === TestUploadCheckerLogic(
          controlIdToMaxSize             = Map(),
          currentUploadSizeAggregate     = None,
          uploadMaxSizeAggregateProperty = UnlimitedSize,
          uploadMaxSizeProperty          = limit
        ).uploadMaxSizeForControl("dummy"))
      }
  }

  describe("With `upload.max-size` property and limit per control") {

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
          controlIdToMaxSize             = Map("control1" -> 1000L, "control2" -> 2000L),
          currentUploadSizeAggregate     = None,
          uploadMaxSizeAggregateProperty = UnlimitedSize,
          uploadMaxSizeProperty          = limit
        ).uploadMaxSizeForControl(controlId))
      }
    }
  }

  describe("With `upload.max-size` property and aggregate limit") {

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
      (currentAggregateSize, aggregateLimit, controlAndExpected) <- expectations
      (controlId, expectedLimit)  <- controlAndExpected
    } locally {
      it(s"currentAggregateSize = `$currentAggregateSize`, aggregateLimit = `$aggregateLimit`, controlId = `$controlId`") {
        assert(expectedLimit === TestUploadCheckerLogic(
          controlIdToMaxSize             = Map("control1" -> 1000L, "control2" -> 2000L),
          currentUploadSizeAggregate     = Some(currentAggregateSize),
          uploadMaxSizeAggregateProperty = aggregateLimit,
          uploadMaxSizeProperty          = LimitedSize(3000)
        ).uploadMaxSizeForControl(controlId))
      }
    }

    it("must throw if no current aggregate size can be provided") {
      assertThrows[IllegalArgumentException] {
        TestUploadCheckerLogic(
          controlIdToMaxSize             = Map("control1" -> 1000L, "control2" -> 2000L),
          currentUploadSizeAggregate     = None,
          uploadMaxSizeAggregateProperty = LimitedSize(10000),
          uploadMaxSizeProperty          = LimitedSize(3000)
        ).uploadMaxSizeForControl("control1")
      }
    }
  }
}
