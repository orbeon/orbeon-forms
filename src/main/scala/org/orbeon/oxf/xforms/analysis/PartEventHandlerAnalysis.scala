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

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.{EventHandler, EventHandlerImpl, XFormsEvents}
import org.orbeon.oxf.xml.Dom4j

import scala.collection.mutable

// Part analysis: event handlers information
trait PartEventHandlerAnalysis {

  self: PartAnalysisImpl ⇒

  private[PartEventHandlerAnalysis] var _handlersForObserver: Map[String, List[EventHandler]] = Map()
  private[PartEventHandlerAnalysis] var _eventNames: Set[String] = Set()
  private[PartEventHandlerAnalysis] var _keypressHandlers: List[EventHandler] = List()

  // Scripts
  private[PartEventHandlerAnalysis] var _scriptsByPrefixedId: Map[String, StaticScript] = Map()
  def scriptsByPrefixedId = _scriptsByPrefixedId
  private[PartEventHandlerAnalysis] var _uniqueJsScripts: List[ShareableScript] = Nil
  def uniqueJsScripts = _uniqueJsScripts

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

    gatherScripts()
  }

  private def gatherScripts(): Unit = {

    import StaticScript._

    val shareableByDigest = mutable.LinkedHashMap[String, ShareableScript]()

    def extractStaticScript(analysis: ElementAnalysis, scriptType: ScriptType) = {

      val elem     = analysis.element
      val isClient = elem.attributeValue("runat") != "server"

      if (! isClient)
        throw new NotImplementedError(s"""`runat="server"` is not supported""")

      val params =
        Dom4j.elements(elem, XXFORMS_PARAM_QNAME) map (p ⇒ p.attributeValue("name") → p.attributeValue("value"))

      val body =
        if (params.nonEmpty)
          Dom4j.elements(elem, XXFORMS_BODY_QNAME).headOption map (_.getStringValue) getOrElse ""
        else
          elem.getStringValue

      StaticScript(
        analysis.prefixedId,
        scriptType,
        body,
        params.to[List],
        shareableByDigest
      )
    }

    def elemHasScriptType(e: ElementAnalysis, scriptType: ScriptType, default: Option[ScriptType]) =
      scriptTypeFromElem(e, default) contains scriptType

    def findForAction(action: String, scriptType: ScriptType, default: Option[ScriptType]) =
      controlTypes.get(action).to[Iterator].flatMap(_.values).filter(elemHasScriptType(_, scriptType, default))

    def findForScriptType(scriptType: ScriptType) =
      findForAction("action", scriptType, None) ++
      findForAction("script", scriptType, Some(JavaScriptScriptType)) map
      (extractStaticScript(_, scriptType)) toList

    val jsScripts      = findForScriptType(JavaScriptScriptType)
    val xpathScriptsIt = findForScriptType(XPathScriptType)

    _scriptsByPrefixedId ++=
      jsScripts.iterator ++
      xpathScriptsIt     map
      (script ⇒ script.prefixedId → script)

    // Keep only one script body for a given digest
    _uniqueJsScripts ++= jsScripts.keepDistinctBy(_.shared.digest, _.shared)
  }

  // Deregister the given handler
  def deregisterEventHandler(eventHandler: EventHandlerImpl): Unit = {
    eventHandler.observersPrefixedIds foreach (_handlersForObserver -= _)

    if (eventHandler.eventNames(XFormsEvents.KEYPRESS))
      _keypressHandlers = _keypressHandlers filterNot (_ eq eventHandler)

    if (eventHandler.localName == "script" || eventHandler.localName == "action")
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
  def hasHandlerForEvent(eventName: String): Boolean = hasHandlerForEvent(eventName, includeAllEvents = true)

  /**
   * Whether there is any event handler registered anywhere in the controls for the given event name.
   */
  def hasHandlerForEvent(eventName: String, includeAllEvents: Boolean): Boolean =
    includeAllEvents && _eventNames.contains(XXFORMS_ALL_EVENTS) || _eventNames.contains(eventName)
}
