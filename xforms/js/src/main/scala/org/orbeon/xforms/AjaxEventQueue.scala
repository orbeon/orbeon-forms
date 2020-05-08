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
import cats.syntax.option._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle


// Basic event queue
//
// - holds events until they are scheduled
// - sends them in groups via `eventsReady` when the schedule is ready
// - handles two different delays: "incremental" and "non-incremental" events
//
// The `eventsReady` function supports returning events to the queue. This was designed so that
// events for multiple forms could be supported:
//
// - send events for the first event's form
// - return events for other forms to the queue
// - next time the schedule is updated, events for the other form are handled, etc.
//
// But this part doesn't work 100% correctly right now. In particular, the oldest/newest event times
// are not updated as events are returned to the queue as we dont have individual time information for
// each event.
//
// Q: Would it make more sense to have one event queue for each form?

trait AjaxEventQueue[EventType <: { val incremental: Boolean }] {

  import Private._

  // The event queue calls this when events are ready, that is when a delay has passed
  // and they are ready to be dispatched as a group. The implementation of this function
  // can return some of the events to the queue.
  def eventsReady(events: NonEmptyList[EventType]): Option[List[EventType]]

  // Configurable delays
  val shortDelay                                  : FiniteDuration
  val incrementalDelay                            : FiniteDuration

  def newestEventTime: Long            = _newestEventTime // used by heartbeat only
  def events         : List[EventType] = eventQueue
  def isEmpty        : Boolean         = eventQueue.isEmpty

  def addEventAndUpdateQueueSchedule(event: EventType): Unit = {
    addEvent(event: EventType)
    updateQueueSchedule()
  }

  def updateQueueSchedule(): Unit =
    schedule = updatedQueueSchedule(schedule) match {
      case newScheduleOpt @ Some(EventSchedule(_, _, done)) =>

        done foreach { _ =>
          schedule   = None
          eventQueue = NonEmptyList.fromList(eventQueue) flatMap eventsReady getOrElse eventQueue
        }

        newScheduleOpt
      case None =>
        schedule
    }

  def debugScheduledTime: Option[Long] =
    schedule map (_.time)

  def debugPrintEventQueue(): Unit =
    println(s"Event queue: ${eventQueue mkString ", "}")

  object Private {

    case class EventSchedule(
      handle : SetTimeoutHandle,
      time   : Long,
      done   : Future[Unit]
    )

    var eventQueue       : List[EventType]       = Nil
    var oldestEventTime  : Long                  = 0
    var _newestEventTime : Long                  = 0
    var schedule         : Option[EventSchedule] = None

    def addEvent(event: EventType): Unit = {

      val currentTime = System.currentTimeMillis()

      if (eventQueue.isEmpty)
        oldestEventTime = currentTime

      _newestEventTime = currentTime
      eventQueue +:= event
    }

    // Return `None` if we don't need to create a new schedule
    def updatedQueueSchedule(existingSchedule: Option[EventSchedule]): Option[EventSchedule] = {

      val newScheduleDelay =
        if (eventQueue exists (! _.incremental))
          shortDelay
        else
          incrementalDelay

      val newScheduleTime =
        oldestEventTime + newScheduleDelay.toMillis

      // There is only *one* timer set at a time at most
      def createNewSchedule = {
        val p = Promise[Unit]()
        EventSchedule(
          handle = timers.setTimeout(newScheduleDelay) { p.success(())},
          time   = newScheduleTime,
          done   = p.future
        )
      }

      existingSchedule match {
        case Some(existingSchedule) if newScheduleTime < existingSchedule.time =>
          timers.clearTimeout(existingSchedule.handle)
          createNewSchedule.some
        case None =>
          createNewSchedule.some
        case Some(_) =>
          None
      }
    }
  }
}
