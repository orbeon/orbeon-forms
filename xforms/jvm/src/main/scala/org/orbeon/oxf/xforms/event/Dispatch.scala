/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event


import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.Logging
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsObject
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.xforms.XFormsId

import scala.util.control.NonFatal

object Dispatch extends Logging {

  // Type of an event listener
  type EventListener = XFormsEvent => Unit

  // Dispatch an event
  def dispatchEvent(event: XFormsEvent): Unit = {

    val containingDocument = event.containingDocument
    implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)

    // Utility to help make sure we push and pop the event
    def withEvent[T](body: => T): T =
      try {
        containingDocument.startHandleEvent(event)
        body
      } finally
        containingDocument.endHandleEvent()

    val target = event.targetObject

    try {
      var statHandleEvent = 0
      var statNativeHandlers = 0

      def eventLogging = Seq("name" -> event.name, "target" -> target.getEffectiveId, "location" -> (Option(event.locationData) map (_.toString) orNull))

      // Ask the target for the handlers associated with the event name
      val (performDefaultAction, handlers) = {
        val staticTarget = target.container.getPartAnalysis.getControlAnalysis(target.getPrefixedId)

        // https://github.com/orbeon/orbeon-forms/issues/898
        if (staticTarget eq null) {
          debug("ignoring event dispatch to target without static control", eventLogging)
          return
        }

        staticTarget.handlersForEvent(event.name)
      }

      // Call native listeners on target if any
      def callNativeListeners(target: XFormsEventTarget): Unit =
        for (listener <- target.getListeners(event.name)) {
          listener.apply(event)
          statNativeHandlers += 1
        }

      withEvent {
        if (handlers.nonEmpty) {
          withDebug("dispatching", eventLogging) {
            // There is at least one handler to run

            // Run all observers for the given phase
            // One one hand, we have prefixed ids, and on the other hand we need to find concrete controls.
            // The strategy we use here is to traverse all the observers. But we could instead determine the
            // effective id from prefixed id and then lookup the object by effective id. It is not clear at
            // this point which is faster.
            def doPhase(observers: List[XFormsEventTarget], staticHandlers: Map[String, List[EventHandler]], phase: Phase) =
              for {
                observer <- observers
                handlers <- staticHandlers.get(observer.getPrefixedId).toList
                handler  <- handlers
                if event.matches(handler)   // custom filtering by event
              } yield {
                event.currentObserver = observer
                event.currentPhase = phase

                withDebug("handler", Seq("name" -> event.name, "phase" -> phase.name, "observer" -> observer.getEffectiveId)) {
                  handler.handleEvent(observer, event)
                  statHandleEvent += 1
                }
              }

            // All ancestor observers (not filtered by scope) gathered lazily so that if there is nothing
            // to do for capture and bubbling, we don't compute them.
            lazy val ancestorObservers =
              Iterator.iterate(target.parentEventObserver)(_.parentEventObserver) takeWhile (_ ne null) toList

            // Capture phase
            handlers.get(Capture) foreach (doPhase(ancestorObservers.reverse, _, Capture))

            // Target phase
            locally {
              // Perform "action at target" before running event handlers

              // NOTE: As of 2011-03-07, this is used XFormsInstance for xforms-insert/xforms-delete
              // processing, and in XFormsUploadControl for upload processing.
              target.performTargetAction(event)

              handlers.get(Target) foreach (doPhase(List(target), _, Target))

              callNativeListeners(target)
            }

            // Bubbling phase, which the event may not support
            if (event.bubbles)
              handlers.get(Bubbling) foreach (doPhase(ancestorObservers, _, Bubbling))

            // Perform default action
            if (! event.cancelable || performDefaultAction)
              target.performDefaultAction(event)

            debugResults(Seq(
              "regular handlers called" -> statHandleEvent.toString,
              "native handlers called"  -> statNativeHandlers.toString
            ))
          }
        } else {
          // No handlers, try to do as little as possible
          event.currentPhase = Target
          target.performTargetAction(event)
          callNativeListeners(target)
          if (! event.cancelable || performDefaultAction)
            target.performDefaultAction(event)

          // Don't log this as there are too many
          //debug("optimized dispatching", eventLogging ++ Seq("native handlers called" -> statNativeHandlers.toString))
        }
      }
    } catch {
      case NonFatal(t) =>
        // Add location information if possible
        val locationData = Option(target.getLocationData).orNull
        throw OrbeonLocationException.wrapException(t,
          new ExtendedLocationData(
            locationData,
            Some("dispatching XForms event"),
            List(
              "event"     -> event.name,
              "target id" -> target.getEffectiveId)
            ))
    }
  }

  def resolveRepeatIndexes(
    container        : XBLContainer,
    result           : XFormsObject,
    sourcePrefixedId : String,
    repeatIndexes    : String
  ): String = {

    // E.g.:
    // - foo$bar.1-2 and Array(4, 5, 6) => foo$bar.4-5-6
    // - foo$bar.1-2 and Array() => foo$bar
    def replaceIdSuffix(prefixedOrEffectiveId: String , parts: Array[Int]): String = {
      val prefixedId = prefixedOrEffectiveId split REPEAT_SEPARATOR head

      if (parts.length == 0)
        prefixedId
      else
        prefixedId + REPEAT_SEPARATOR + (parts mkString REPEAT_INDEX_SEPARATOR_STRING)
    }

    // Append space-separated suffix indexes to existing indexes
    def appendSuffixes(first: Array[Int], second: String) =
      first ++ (second.trimAllToEmpty split """\s+""" map (_.toInt))

    // Repeat indexes in current scope
    val resolutionScopeContainer = container.findScopeRoot(sourcePrefixedId)
    val containerParts = XFormsId.getEffectiveIdSuffixParts(resolutionScopeContainer.getEffectiveId)

    // Append new indexes
    val newSuffix = appendSuffixes(containerParts, repeatIndexes)

    replaceIdSuffix(result.getEffectiveId, newSuffix)
  }
}
