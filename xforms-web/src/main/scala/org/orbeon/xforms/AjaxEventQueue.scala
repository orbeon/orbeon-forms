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
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._
import scala.scalajs.js.timers
import scala.scalajs.js.timers.SetTimeoutHandle


// Basic event queue
//
// - holds events until they are scheduled
// - sends them in groups via `eventsReady` when the schedule is ready
// - handles two different delays: "incremental" and "non-incremental" events
//
// This implementation removes support for multiple "forms", specifically the ability to
// update the queue with remaining events. Instead, use multiple queues to handle multiple
// forms.

trait AjaxEventQueue[EventType] {

  import Private._

  // The event queue calls this when events are ready, that is when a delay has passed
  // and they are ready to be dispatched as a group. The implementation of this function
  // can return some of the events to the queue.
  def eventsReady(eventsReversed: NonEmptyList[EventType]): Unit

  def canSendEvents: Boolean

  // Configurable delays
  def shortDelay      : FiniteDuration
  def incrementalDelay: FiniteDuration

  def newestEventTime: Long            = state.newestEventTime // used by heartbeat only
  def eventsReversed : List[EventType] = state.events
  def isEmpty        : Boolean         = state.events.isEmpty

  def addEventAndUpdateQueueSchedule(event: EventType, incremental: Boolean): Unit = {
    addEvent(event, incremental)
    updateQueueSchedule()
  }

  def updateQueueSchedule(): Unit =
    state = state.copy(
      schedule = updatedQueueSchedule(state.schedule) match {
        case newScheduleOpt @ Some(EventSchedule(_, _, done)) =>

          done foreach { _ =>

            if (canSendEvents) {
              val events = state.events
              state = emptyState
              NonEmptyList.fromList(events) foreach eventsReady
            } else {
              // We expect that once `canSendEvents` becomes true again, `updateQueueSchedule()`
              // will be called again. A new schedule will be created then.
              state = state.copy(schedule = None)
            }
          }

          newScheduleOpt
        case None =>
          state.schedule
      }
    )

  def debugScheduledTime: Option[Long] =
    state.schedule map (_.time)

  def debugPrintEventQueue(): Unit =
    println(s"Event queue: ${state.events.reverse mkString ", "}")

  object Private {

    case class EventSchedule(
      handle : SetTimeoutHandle,
      time   : Long,
      done   : Future[Unit]
    )

    case class State(
      events           : List[EventType],
      hasNonIncremental: Boolean,
      oldestEventTime  : Long,
      newestEventTime  : Long,
      schedule         : Option[EventSchedule]
    )

    def emptyState: State =
      State(
        events            = Nil,
        hasNonIncremental = false,
        oldestEventTime   = 0,
        newestEventTime   = 0,
        schedule          = None
      )

    var state: State = emptyState

    def addEvent(event: EventType, incremental: Boolean): Unit = {
      val currentTime = System.currentTimeMillis()
      state = state.copy(
        events            = event :: state.events,
        hasNonIncremental = state.hasNonIncremental | ! incremental,
        oldestEventTime   = if (state.events.isEmpty) currentTime else state.oldestEventTime,
        newestEventTime   = currentTime
      )
    }

    // Return `None` if we don't need to create a new schedule
    def updatedQueueSchedule(existingSchedule: Option[EventSchedule]): Option[EventSchedule] = {

      val currentTime = System.currentTimeMillis()

      val newScheduleTimeAtLeastCurrentTime = {

        val newScheduleDelay =
          if (state.hasNonIncremental)
            shortDelay
          else
            incrementalDelay

        (state.oldestEventTime + newScheduleDelay.toMillis) max currentTime
      }

      // There is only *one* timer set at a time at most
      def createNewSchedule = {
        val p = Promise[Unit]()
        EventSchedule(
          handle = timers.setTimeout(newScheduleTimeAtLeastCurrentTime - currentTime) { p.success(())},
          time   = newScheduleTimeAtLeastCurrentTime,
          done   = p.future
        )
      }

      existingSchedule match {
        case Some(existingSchedule) if newScheduleTimeAtLeastCurrentTime < existingSchedule.time =>
          timers.clearTimeout(existingSchedule.handle)
          createNewSchedule.some
        case None if state.events.nonEmpty =>
          createNewSchedule.some
        case _ =>
          None
      }
    }
  }
}
