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

import scala.collection.JavaConverters._
import org.orbeon.oxf.xforms.event.{XFormsEvents, EventHandlerImpl, EventHandler}
import collection.mutable.LinkedHashMap
import org.orbeon.oxf.xforms.{Script, XFormsConstants}
import org.orbeon.oxf.xforms.script.ServerScript

// Part analysis: event handlers information
trait PartEventHandlerAnalysis {

    this: PartAnalysisImpl =>

    private var eventHandlersMap: Map[String, List[EventHandler]] = _
    private var eventNames: Set[String] = _
    private var keyHandlers: List[EventHandler] = _

    // Scripts
    private var _scripts: LinkedHashMap[String, Script] = _
    def scripts = _scripts

    // Register all event handlers
    def registerEventHandlers(eventHandlers: Seq[EventHandlerImpl]) {

        // Make sure this is called only once
        assert(eventHandlersMap eq null)
        
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
        eventHandlersMap = tuples groupBy (_._1) mapValues (_ map (_._2) toList)

        // Gather all event names (NOTE: #all is also included if present)
        eventNames = (eventHandlers flatMap (_.eventNames) toSet)

        // Gather all keypress handlers
        keyHandlers = (eventHandlers filter (_.eventNames(XFormsEvents.KEYPRESS)) toList)

        // Gather all scripts
        def scriptMapping(eventHandlerImpl: ElementAnalysis) = {
            val element = eventHandlerImpl.element
            val isClient = element.attributeValue("runat") != "server"
            val newScript = if (isClient) new Script(_, _, _, _) else new ServerScript(_, _, _, _)
            val script = newScript(eventHandlerImpl.prefixedId, isClient, element.attributeValue("type"), element.getStringValue)

            eventHandlerImpl.prefixedId → script
        }

        // Use LinkedHashMap as we want to output scripts in the HTML in a deterministic order.
        // Could use List + Map to avoid using a mutable collection?
        _scripts = LinkedHashMap(controlTypes.get("script").toSeq flatMap (_.values) map (scriptMapping(_)): _*)
    }

    def getEventHandlers(observerPrefixedId: String) =
        eventHandlersMap.get(observerPrefixedId) map (_.asJava) orNull

    def observerHasHandlerForEvent(observerPrefixedId: String, eventName: String) =
        eventHandlersMap.get(observerPrefixedId) map
            (handlers ⇒ handlers exists (_.isMatchEventName(eventName))) getOrElse false

    def getKeyHandlers = keyHandlers.asJava

    /**
     * Returns whether there is any event handler registered anywhere in the controls for the given event name.
     */
    def hasHandlerForEvent(eventName: String): Boolean = hasHandlerForEvent(eventName, true)

    /**
     * Whether there is any event handler registered anywhere in the controls for the given event name.
     */
    def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean =
        includeAllEvents && eventNames.contains(XFormsConstants.XXFORMS_ALL_EVENTS) || eventNames.contains(eventName)
}