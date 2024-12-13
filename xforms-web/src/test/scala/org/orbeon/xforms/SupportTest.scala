package org.orbeon.xforms

import org.scalatest.funspec.AsyncFunSpec


class SupportTest extends AsyncFunSpec {

  describe("The `computePercentStringToOneDecimal()` function") {

    val IntMaxValueAsLong = Int.MaxValue.toLong

    val expected = List[(Long, Long, String)](
      (1L,                                   1000L,             ".1"),
      (6110847L,                             99615109L,         "6.1"),
      (IntMaxValueAsLong,                    IntMaxValueAsLong, "100.0"),
      ((IntMaxValueAsLong + 1) / 10 + 1,     IntMaxValueAsLong, "10.0"),
      ((IntMaxValueAsLong + 1) * 9 / 10 + 1, IntMaxValueAsLong, "90.0"),
      (1000L,                                1000L,             "100.0"),
      (0L,                                   1000L,             ".0"),
      (0L,                                   0L,                "100.0"),
    )

    for ((num, denom, expected) <- expected) {
      it(s"must pass for $num / $denom") {
        assert(Support.computePercentStringToOneDecimal(num, denom) == expected)
      }
    }
  }
}
