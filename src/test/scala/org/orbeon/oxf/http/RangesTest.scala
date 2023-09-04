package org.orbeon.oxf.http

import org.orbeon.oxf.externalcontext.{ExternalContext, RequestAdapter}
import org.scalatest.funspec.AnyFunSpecLike

import java.{util => ju}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class RangesTest extends AnyFunSpecLike {
  private def requestWithHeaders(headers: Seq[(String, String)]): ExternalContext.Request = new RequestAdapter {
    override val getHeaderValuesMap: ju.Map[String, Array[String]] =
      headers.map(kv => kv._1 -> Array(kv._2)).toMap.asJava
  }

  describe("Parsing string ranges") {
    it("must parse valid ranges correctly") {
      assert(Range("0-1").get           == Range(0, Some(1)))
      assert(Range("0-").get            == Range(0, None))
      assert(Range("1234-5678").get     == Range(1234, Some(5678)))
      assert(Range(" 1234 - 5678 ").get == Range(1234, Some(5678)))
      assert(Range(" 1234 - ").get      == Range(1234, None))
    }

    it("must reject invalid ranges") {
      assert(Range("")          .isFailure)
      assert(Range("1234")      .isFailure)
      assert(Range("-1234")     .isFailure)
      assert(Range("-1234-")    .isFailure)
      assert(Range("1234-5678-").isFailure)
      assert(Range("a-5678")    .isFailure)
      assert(Range("1234-b")    .isFailure)
      assert(Range("a-b")       .isFailure)
    }
  }

  def testRangeInRequest(rangeString: String, expectedRanges: Seq[Range]): Unit = {
    val ranges = Ranges(requestWithHeaders(Seq("Range" -> rangeString)))

    assert(ranges.isSuccess, s"Range in request: $rangeString")
    assert(ranges.get.ranges == expectedRanges)
    assert(ranges.get.ifRange.isEmpty)
  }

  describe("Parsing HTTP ranges from an HTTP request") {
    it("must parse headers with no range correctly") {
      val ranges = Ranges(requestWithHeaders(Seq()))

      assert(ranges.isSuccess)
      assert(ranges.get.ranges == Seq())
      assert(ranges.get.ifRange.isEmpty)
    }

    it("must parse headers with a single range correctly") {
      testRangeInRequest("bytes=0-1",           Seq(Range(0, Some(1))))
      testRangeInRequest("bytes=0-",            Seq(Range(0, None)))
      testRangeInRequest("bytes=1234-5678",     Seq(Range(1234, Some(5678))))
      testRangeInRequest("bytes= 1234 - 5678 ", Seq(Range(1234, Some(5678))))
      testRangeInRequest("bytes= 1234 - ",      Seq(Range(1234, None)))
      testRangeInRequest("bytes= 1234 - ",      Seq(Range(1234, None)))
    }

    it("must parse headers with multiple ranges correctly") {
      testRangeInRequest("bytes=12-34,56-78",         Seq(Range(12, Some(34)), Range(56, Some(78))))
      testRangeInRequest("bytes=12-,56-78",           Seq(Range(12, None),     Range(56, Some(78))))
      testRangeInRequest("bytes=12-34,56-",           Seq(Range(12, Some(34)), Range(56, None)))
      testRangeInRequest("bytes=12-,56-",             Seq(Range(12, None),     Range(56, None)))
      testRangeInRequest("bytes= 12- , 56- ",         Seq(Range(12, None),     Range(56, None)))
      testRangeInRequest("bytes= 12- , 56- ,123-456", Seq(Range(12, None),     Range(56, None), Range(123, Some(456))))
    }
  }
}
