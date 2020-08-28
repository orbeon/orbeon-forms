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

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.event.{EventHandler, EventHandlerImpl}
import org.orbeon.oxf.xml.dom.Comparator
import org.orbeon.xforms.EventNames

import scala.collection.mutable
import scala.collection.compat._


// Part analysis: event handlers information
trait PartEventHandlerAnalysis {

  self: PartAnalysisImpl =>

  import PartEventHandlerAnalysis._

  private[PartEventHandlerAnalysis] var _handlersForObserver: Map[String, List[EventHandler]] = Map()
  private[PartEventHandlerAnalysis] var _eventNames: Set[String] = Set()
  private[PartEventHandlerAnalysis] var _keyboardHandlers: List[EventHandler] = List()

  // Scripts
  private[PartEventHandlerAnalysis] var _scriptsByPrefixedId: Map[String, StaticScript] = Map()
  def scriptsByPrefixedId: Map[String, StaticScript] = _scriptsByPrefixedId
  private[PartEventHandlerAnalysis] var _uniqueJsScripts: List[ShareableScript] = Nil
  def uniqueJsScripts: List[ShareableScript] = _uniqueJsScripts

  // Register new event handlers
  def registerEventHandlers(eventHandlers: Seq[EventHandlerImpl]): Unit = {

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

    gatherScripts()
  }

  private def gatherScripts(): Unit = {

    import StaticScript._

    // Used to eliminate duplicates: we store a single copy of each ShareableScript per digest
    val shareableByDigest = mutable.LinkedHashMap[String, ShareableScript]()

    def extractStaticScript(analysis: ElementAnalysis, scriptType: ScriptType) = {

      val elem     = analysis.element
      val isClient = elem.attributeValue("runat") != "server"

      if (! isClient)
        throw new NotImplementedError(s"""`runat="server"` is not supported""")

      val params =
        elem.elements(XFORMS_PARAM_QNAME) map (p => p.attributeValue("name") -> p.attributeValue("value"))

      val body =
        if (params.nonEmpty)
          elem.elements(XFORMS_BODY_QNAME).headOption map (_.getStringValue) getOrElse ""
        else
          elem.getStringValue

      StaticScript(
        prefixedId        = analysis.prefixedId,
        scriptType        = scriptType,
        body              = body,
        params            = params.to(List),
        shareableByDigest = shareableByDigest
      )
    }

    def elemHasScriptType(e: ElementAnalysis, scriptType: ScriptType, default: Option[ScriptType]) =
      scriptTypeFromElem(e, default) contains scriptType

    def findForActionIt(action: String, scriptType: ScriptType, default: Option[ScriptType]) =
      controlTypes.get(action).toIterator.flatMap(_.values).filter(elemHasScriptType(_, scriptType, default))

    def findForScriptTypeIt(scriptType: ScriptType) =
      findForActionIt(ActionActionName,  scriptType, None) ++
      findForActionIt(HandlerActionName, scriptType, None) ++
      findForActionIt(ScriptActionName,  scriptType, Some(JavaScriptScriptType)) map
      (extractStaticScript(_, scriptType))

    val jsScripts      = findForScriptTypeIt(JavaScriptScriptType).to(List)
    val xpathScriptsIt = findForScriptTypeIt(XPathScriptType)

    _scriptsByPrefixedId ++=
      jsScripts.iterator ++
      xpathScriptsIt     map
      (script => script.prefixedId -> script)

    // Keep only one script body for a given digest
    _uniqueJsScripts ++= jsScripts.keepDistinctBy(_.shared.digest) map (_.shared)
  }

  // Deregister the given handler
  def deregisterEventHandler(eventHandler: EventHandlerImpl): Unit = {
    eventHandler.observersPrefixedIds foreach (_handlersForObserver -= _)

    if (eventHandler.eventNames intersect EventNames.KeyboardEvents nonEmpty)
      _keyboardHandlers = _keyboardHandlers filterNot (_ eq eventHandler)

    if (ActionNames(eventHandler.localName))
      _scriptsByPrefixedId -= eventHandler.prefixedId

    // NOTE: Can't update eventNames and _uniqueClientScripts without checking all handlers again, so for now leave that untouched
  }

  def getEventHandlers(observerPrefixedId: String): List[EventHandler] =
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

private object PartEventHandlerAnalysis {
  val ActionActionName  = "action"
  val ScriptActionName  = "script"
  val HandlerActionName = "handler"

  val ActionNames = Set(ActionActionName, ScriptActionName, HandlerActionName)
}