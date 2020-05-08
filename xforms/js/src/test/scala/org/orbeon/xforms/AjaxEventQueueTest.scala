/**
 * Copyright (C) 2020 Orbeon, Inc.
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

import cats.data.NonEmptyList
import org.scalatest.funspec.AsyncFunSpec

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Promise}


class AjaxEventQueueTest extends AsyncFunSpec {

  // With Scala.js this is required
  implicit override def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  trait Delays {
    val shortDelay      : FiniteDuration = 20.millis
    val incrementalDelay: FiniteDuration = 500.millis
  }

  describe("AjaxEventQueue") {

    case class MyEventType(name: String)

    it("must eventually dispatch events") {

      val p = Promise[NonEmptyList[MyEventType]]()

      object EventQueue extends AjaxEventQueue[MyEventType] with Delays {
        def eventsReady(events: NonEmptyList[MyEventType]): Unit = {
          p.success(events)
        }
      }

      EventQueue.addEventAndUpdateQueueSchedule(MyEventType("foo"), incremental = false)
      EventQueue.addEventAndUpdateQueueSchedule(MyEventType("bar"), incremental = false)

      p.future map { events =>
        assert(events.size == 2)
        assert(EventQueue.isEmpty)
      }
    }

    it("must schedule an earlier time when non-incremental events are added") {

      val p = Promise[NonEmptyList[MyEventType]]()

      object EventQueue extends AjaxEventQueue[MyEventType] with Delays {
        def eventsReady(events: NonEmptyList[MyEventType]): Unit = {
          p.success(events)
        }
      }

      EventQueue.addEventAndUpdateQueueSchedule(MyEventType("foo"), incremental = true)
      val t1 = EventQueue.debugScheduledTime.get
      EventQueue.addEventAndUpdateQueueSchedule(MyEventType("bar"), incremental = false)
      val t2 = EventQueue.debugScheduledTime.get
      EventQueue.addEventAndUpdateQueueSchedule(MyEventType("baz"), incremental = true)
      val t3 = EventQueue.debugScheduledTime.get

      p.future map { events =>
        assert(t2 < t1)
        assert(t3 == t2)
        assert(events.size == 3)
        assert(EventQueue.isEmpty)
      }
    }
  }
}
