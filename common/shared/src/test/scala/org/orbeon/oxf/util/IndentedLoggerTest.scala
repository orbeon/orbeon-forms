/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.scalatest.funspec.AnyFunSpec


class IndentedLoggerTest extends AnyFunSpec {

  // NOTE: This doesn't test the logging output, although with log4s we could do this on the JVM at least.
  // Instead we just want to make sure we can compile and run on both JVM and JavaScript.
  describe("Compile and run logging utilities") {

    val logger = org.log4s.getLogger(classOf[IndentedLoggerTest])

    implicit val indentedLogger = new IndentedLogger(logger)

    import Logging._

    val result =
      withDebug("around block") {
        debug("within block")
        42
      }

    it("must succeed") {
      assert(42 == result)
    }
  }
}
