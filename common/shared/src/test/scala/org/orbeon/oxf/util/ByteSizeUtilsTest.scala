package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpec


class ByteSizeUtilsTest extends AnyFunSpec {

  describe("byteCountToCompactDisplaySize") {
    val SizeToExpected = List(
      500L          -> "500 B",
      1230L         -> "1.2 KB",  // Max 1 digit
      10240L        -> "10 KB",
      15892L        -> "15.5 KB",
      3824383L      -> "3.65 MB", // Max 2 digits
      1073741824L   -> "1 GB",
      1273741824L   -> "1.186 GB" // Max 3 digits
    )
    for ((size, expected) <- SizeToExpected)
      it(s"must format $size bytes as `$expected`") {
        assert(ByteSizeUtils.byteCountToCompactDisplaySize(size) == expected)
      }
  }
}
