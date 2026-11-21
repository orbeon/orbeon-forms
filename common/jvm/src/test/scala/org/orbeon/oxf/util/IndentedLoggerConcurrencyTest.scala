/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import java.util.concurrent.{Callable, Executors}
import scala.jdk.CollectionConverters.*


class IndentedLoggerConcurrencyTest extends AnyFunSpec {

  describe("Concurrent IndentedLogger usage") {

    it("must not fail with head of empty list under concurrent withDebug and setDebugResults") {
      val logger = org.log4s.getLogger(classOf[IndentedLoggerConcurrencyTest])
      implicit val sharedLogger: IndentedLogger = new IndentedLogger(logger, isDebugEnabled = true)

      import Logging.*

      val threads    = 8
      val iterations = 500
      val executor   = Executors.newFixedThreadPool(threads)

      try {
        val tasks =
          (1 to threads).map { threadIdx =>
            new Callable[Unit] {
              def call(): Unit =
                for (i <- 1 to iterations) {
                  withDebug(s"op-$threadIdx-$i") {
                    debugResults(List("thread" -> threadIdx.toString, "iter" -> i.toString))
                  }
                }
            }
          }

        val futures = executor.invokeAll(tasks.asJava)
        futures.asScala.foreach(_.get())
        assert(sharedLogger.indentation.indentation == 0)
      } finally {
        executor.shutdownNow()
      }
    }
  }
}
