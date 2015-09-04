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

import org.orbeon.oxf.xforms.event.{XFormsEvents, EventHandlerImpl, EventHandler}
import org.orbeon.oxf.xforms.script.ServerScript
import org.orbeon.oxf.xforms.{Script, XFormsConstants}

// Part analysis: event handlers information
trait PartEventHandlerAnalysis {

  self: PartAnalysisImpl ⇒

  private[PartEventHandlerAnalysis] var _handlersForObserver: Map[String, List[EventHandler]] = Map()
  private[PartEventHandlerAnalysis] var _eventNames: Set[String] = Set()
  private[PartEventHandlerAnalysis] var _keypressHandlers: List[EventHandler] = List()

  // Scripts
  private[PartEventHandlerAnalysis] var _scriptsByPrefixedId: Map[String, Script] = Map()
  def scripts = _scriptsByPrefixedId
  private[PartEventHandlerAnalysis] var _uniqueClientScripts: Seq[(String, String)] = Seq()
  def uniqueClientScripts = _uniqueClientScripts

  // Register new event handlers
  def registerEventHandlers(eventHandlers: Seq[EventHandlerImpl]): Unit = {
    
    val tuples =
      for {
        handler ← eventHandlers
        observerPrefixedId ← {
          handler.analyzeEventHandler()
          handler.observersPrefixedIds
        }
      } yield
        (observerPrefixedId, handler)

    // Group event handlers by observer
    val newHandlers = tuples groupBy (_._1) map { case (k, v) ⇒ k → (v map (_._2) toList) }

    // Accumulate new handlers into existing map by combining values for a given observer
    _handlersForObserver = newHandlers.foldLeft(_handlersForObserver) {
      case (existingMap, (observerId, newHandlers)) ⇒
        val existingHandlers = existingMap.getOrElse(observerId, Nil)
        existingMap + (observerId → (existingHandlers ::: newHandlers))
    }

    // Gather all event names (NOTE: #all is also included if present)
    _eventNames ++= eventHandlers flatMap (_.eventNames)

    // Gather all keypress handlers
    _keypressHandlers ++= eventHandlers filter (_.eventNames(XFormsEvents.KEYPRESS))

    // Gather all scripts in deterministic order
    def makeScript(eventHandlerImpl: ElementAnalysis) = {
      val element = eventHandlerImpl.element
      val isClient = element.attributeValue("runat") != "server"

      val make = if (isClient) new Script(_, _, _, _) else new ServerScript(_, _, _, _)
      make(eventHandlerImpl.prefixedId, isClient, element.attributeValue("type"), element.getStringValue)
    }

    val scriptMappings = {
      val scriptHandlers = controlTypes.get("script").toSeq flatMap (_.values)
      scriptHandlers map makeScript
    }

    // Index scripts by prefixed id
    _scriptsByPrefixedId ++= scriptMappings map
      { case script ⇒ script.prefixedId → script }

    // Keep only one script body for a given digest
    val distinctNames = scriptMappings collect
      { case script if script.isClient ⇒ script.clientName → script.digest } distinct

    val scriptBodiesByDigest = scriptMappings map { case script ⇒ script.digest → script.body } toMap

    _uniqueClientScripts ++= distinctNames map
      { case (clientName, digest) ⇒ clientName → scriptBodiesByDigest(digest) }
  }

  // Deregister the given handler
  def deregisterEventHandler(eventHandler: EventHandlerImpl): Unit = {
    eventHandler.observersPrefixedIds foreach (_handlersForObserver -= _)

    if (eventHandler.eventNames(XFormsEvents.KEYPRESS))
      _keypressHandlers = _keypressHandlers filterNot (_ eq eventHandler)

    if (eventHandler.localName == "script")
      _scriptsByPrefixedId -= eventHandler.prefixedId

    // NOTE: Can't update eventNames and _uniqueClientScripts without checking all handlers again, so for now leave that untouched
  }

  def getEventHandlers(observerPrefixedId: String) =
    _handlersForObserver.getOrElse(observerPrefixedId, Nil)

  def observerHasHandlerForEvent(observerPrefixedId: String, eventName: String) =
    _handlersForObserver.get(observerPrefixedId) exists
      (handlers ⇒ handlers exists (_.isMatchByName(eventName)))

  def keypressHandlers = _keypressHandlers

  /**
   * Returns whether there is any event handler registered anywhere in the controls for the given event name.
   */
  def hasHandlerForEvent(eventName: String): Boolean = hasHandlerForEvent(eventName, true)

  /**
   * Whether there is any event handler registered anywhere in the controls for the given event name.
   */
  def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean =
    includeAllEvents && _eventNames.contains(XFormsConstants.XXFORMS_ALL_EVENTS) || _eventNames.contains(eventName)
}