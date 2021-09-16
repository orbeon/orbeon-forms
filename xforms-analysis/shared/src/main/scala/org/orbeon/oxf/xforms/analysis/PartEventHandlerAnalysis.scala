/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.xforms.EventNames
import org.orbeon.xforms.XFormsNames._

import scala.collection.mutable


// Part analysis: event handlers information
trait PartEventHandlerAnalysis {

  self =>

  def controlTypes: mutable.HashMap[String, mutable.LinkedHashMap[String, ElementAnalysis]]

  private[PartEventHandlerAnalysis] var _handlersForObserver: Map[String, List[EventHandler]] = Map.empty
  private[PartEventHandlerAnalysis] var _eventNames: Set[String] = Set.empty
  private[PartEventHandlerAnalysis] var _keyboardHandlers: List[EventHandler] = Nil

  // Register new event handlers
  def registerEventHandlers(eventHandlers: Iterable[EventHandler])(implicit logger: IndentedLogger): Unit = {

    val tuples =
      for {
        handler <- eventHandlers
        observerPrefixedId <- {
          handler.analyzeEventHandler()
          handler.observersPrefixedIds
        }
      } yield
        (observerPrefixedId, handler)

    // Group event handlers by observer
    val newHandlers = tuples groupBy (_._1) map { case (k, v) => k -> (v map (_._2) toList) }

    // Accumulate new handlers into existing map by combining values for a given observer
    _handlersForObserver = newHandlers.foldLeft(_handlersForObserver) {
      case (existingMap, (observerId, newHandlers)) =>
        val existingHandlers = existingMap.getOrElse(observerId, Nil)
        existingMap + (observerId -> (existingHandlers ::: newHandlers))
    }

    // Gather all event names (NOTE: #all is also included if present)
    _eventNames ++= eventHandlers flatMap (_.eventNames)

    // Gather all keyboard handlers
    _keyboardHandlers ++= eventHandlers filter (_.eventNames intersect EventNames.KeyboardEvents nonEmpty)
  }

  // Deregister the given handler
  def deregisterEventHandler(eventHandler: EventHandler): Unit = {

    eventHandler.observersPrefixedIds foreach (_handlersForObserver -= _)

    if (eventHandler.eventNames intersect EventNames.KeyboardEvents nonEmpty)
      _keyboardHandlers = _keyboardHandlers filterNot (_ eq eventHandler)
  }

  def getEventHandlersForObserver(observerPrefixedId: String): List[EventHandler] =
    _handlersForObserver.getOrElse(observerPrefixedId, Nil)

  def observerHasHandlerForEvent(observerPrefixedId: String, eventName: String): Boolean =
    _handlersForObserver.get(observerPrefixedId) exists
      (handlers => handlers exists (_.isMatchByName(eventName)))

  def keyboardHandlers: List[EventHandler] = _keyboardHandlers

  /**
   * Returns whether there is any event handler registered anywhere in the controls for the given event name.
   */
  def hasHandlerForEvent(eventName: String): Boolean = hasHandlerForEvent(eventName, includeAllEvents = true)

  /**
   * Whether there is any event handler registered anywhere in the controls for the given event name.
   */
  def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean =
    includeAllEvents && _eventNames.contains(XXFORMS_ALL_EVENTS) || _eventNames.contains(eventName)
}
