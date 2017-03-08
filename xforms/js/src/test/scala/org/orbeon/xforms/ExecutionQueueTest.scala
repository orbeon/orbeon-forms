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
package org.orbeon.xforms

import org.scalatest.AsyncFunSpec

import scala.concurrent.{ExecutionContext, Future, Promise}

class ExecutionQueueTest extends AsyncFunSpec {

  // With Scala.js this is required
  implicit override def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  describe("ExecutionQueue") {

    it("must eventually execute") {

      val result = Promise[List[String]]()

      def execute(events: List[String]): Future[Unit] = {
        result.success(events)
        Future(())
      }

      val eq = new ExecutionQueue(execute)

      eq.add("foo", 1, ExecutionWait.MaxWait)
      eq.add("bar", 10, ExecutionWait.MaxWait)

      result.future map (r ⇒ assert("bar" :: "foo" :: Nil === r))
    }
  }

}