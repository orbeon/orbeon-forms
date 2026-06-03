package org.orbeon.oxf.fb

import io.circe.parser._
import io.circe.syntax._
import org.orbeon.builder.rpc.CommonConstraint
import org.scalatest.funspec.AnyFunSpecLike

class CommonConstraintJsonTest extends AnyFunSpecLike {

  describe("CommonConstraint JSON codecs") {

    it("should round-trip MinLength") {
      val cc: CommonConstraint = CommonConstraint.MinLength(5)
      val json = cc.asJson
      assert(json.noSpaces == """{"name":"min-length","arg":5}""")
      val decoded = decode[CommonConstraint](json.noSpaces)
      assert(decoded == Right(cc))
    }

    it("should round-trip NonNegative") {
      val cc: CommonConstraint = CommonConstraint.NonNegative
      val json = cc.asJson
      assert(json.noSpaces == """{"name":"non-negative"}""")
      val decoded = decode[CommonConstraint](json.noSpaces)
      assert(decoded == Right(cc))
    }

    it("should round-trip UploadMaxSizePerFile") {
      val cc: CommonConstraint = CommonConstraint.UploadMaxSizePerFile(1024L)
      val json = cc.asJson
      assert(json.noSpaces == """{"name":"upload-max-size-per-file","arg":1024}""")
      val decoded = decode[CommonConstraint](json.noSpaces)
      assert(decoded == Right(cc))
    }

    it("should round-trip ExcludedDates") {
      val cc: CommonConstraint = CommonConstraint.ExcludedDates(List("2018-11-29", "2018-12-02"))
      val json = cc.asJson
      assert(json.noSpaces == """{"name":"excluded-dates","arg":["2018-11-29","2018-12-02"]}""")
      val decoded = decode[CommonConstraint](json.noSpaces)
      assert(decoded == Right(cc))
    }
  }
}
