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


import cats.data.NonEmptyList
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms.action.{XFormsAPI, XFormsActionInterpreter, XFormsActions}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis._
import org.orbeon.oxf.xforms.analysis.{ElementAnalysis, EventHandler}
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl}
import org.orbeon.oxf.xforms.event.events.XXFormsActionErrorEvent
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{PartGlobalOps, XFormsContainingDocument, XFormsContextStack}
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.xforms.Constants.{RepeatIndexSeparatorString, RepeatSeparator, RepeatSeparatorString}
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.analysis.{Perform, Phase, Propagate}
import org.orbeon.xforms.runtime.XFormsObject

import scala.collection.mutable
import scala.util.control.{Breaks, NonFatal}


object Dispatch extends Logging {

  // Type of an event listener
  type EventListener = XFormsEvent => Unit

  // Dispatch an event
  def dispatchEvent(event: XFormsEvent): Unit = {

    val containingDocument = event.containingDocument
    val staticOps          = containingDocument.staticOps

    implicit val indentedLogger: IndentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)

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

        staticTarget.handlersForEvent(event.name, handlersForEventImpl(staticOps, staticTarget, _))
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
                  handleEvent(handler, observer, event)
                  statHandleEvent += 1
                }
              }

            // All ancestor observers (not filtered by scope) gathered lazily so that if there is nothing
            // to do for capture and bubbling, we don't compute them.
            lazy val ancestorObservers =
              Iterator.iterate(target.parentEventObserver)(_.parentEventObserver) takeWhile (_ ne null) toList

            // Capture phase
            handlers.get(Phase.Capture) foreach (doPhase(ancestorObservers.reverse, _, Phase.Capture))

            // Target phase
            locally {
              // Perform "action at target" before running event handlers

              // NOTE: As of 2011-03-07, this is used XFormsInstance for xforms-insert/xforms-delete
              // processing, and in XFormsUploadControl for upload processing.
              target.performTargetAction(event)

              handlers.get(Phase.Target) foreach (doPhase(List(target), _, Phase.Target))

              callNativeListeners(target)
            }

            // Bubbling phase, which the event may not support
            if (event.bubbles)
              handlers.get(Phase.Bubbling) foreach (doPhase(ancestorObservers, _, Phase.Bubbling))

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
          event.currentPhase = Phase.Target
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
          XmlExtendedLocationData(
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
      val prefixedId = prefixedOrEffectiveId split RepeatSeparator head

      if (parts.length == 0)
        prefixedId
      else
        prefixedId + RepeatSeparatorString + (parts mkString RepeatIndexSeparatorString)
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

  def handleEvent(
    eventHandler   : EventHandler,
    eventObserver  : XFormsEventTarget,
    event          : XFormsEvent)(implicit
    indentedLogger : IndentedLogger
  ): Unit = {

    assert(eventHandler.observersPrefixedIds ne null)
    assert(eventHandler.targetPrefixedIds ne null)

    val containingDocument = event.containingDocument

    // Find dynamic context within which the event handler runs
    val (container, handlerEffectiveId, xpathContext) =
      eventObserver match {

        // Observer is the XBL component itself but from the "inside"
        case componentControl: XFormsComponentControl if eventHandler.isXBLHandler =>

          if (componentControl.canRunEventHandlers(event)) {

            val xblContainer = componentControl.nestedContainerOpt.get // TODO: What if None?
            xblContainer.getContextStack.resetBindingContext()
            val stack = new XFormsContextStack(xblContainer, xblContainer.getContextStack.getCurrentBindingContext)

            val handlerEffectiveId =
              xblContainer.getFullPrefix + eventHandler.staticId + XFormsId.getEffectiveIdSuffixWithSeparator(componentControl.getEffectiveId)

            (xblContainer, handlerEffectiveId, stack)
          } else {
            debug("ignoring event dispatched to non-relevant component control", List(
              "name"       -> event.name,
              "control id" -> componentControl.effectiveId)
            )
            return
          }

        // Regular observer
        case _ =>

          // Resolve the concrete handler
          resolveHandler(containingDocument, eventHandler, eventObserver, event.targetObject) match {
            case Some(concreteHandler) =>

              val handlerContainer   = concreteHandler.container
              val handlerEffectiveId = concreteHandler.getEffectiveId
              val stack              = new XFormsContextStack(handlerContainer, concreteHandler.bindingContext)

              (handlerContainer, handlerEffectiveId, stack)
            case None =>
              return
          }
      }

    val handlerIsRelevant =
      containingDocument.findControlByEffectiveId(handlerEffectiveId) map (_.isRelevant) getOrElse {
        // Actions in models do not have a dynamic representation and so are not indexed at this time. In such
        // cases, we look at the relevance of the container.
        container.isRelevant
      }

    if (handlerIsRelevant || eventHandler.isIfNonRelevant) {
      try {
        XFormsAPI.withScalaAction(
          new XFormsActionInterpreter(
            container           = container,
            outerActionElement  = eventHandler.element,
            handlerEffectiveId  = handlerEffectiveId,
            event               = event,
            eventObserver       = eventObserver)(
            actionXPathContext  = xpathContext,
            indentedLogger      = containingDocument.getIndentedLogger(XFormsActions.LoggingCategory)
          )
        ) {
          _.runAction(eventHandler)
        }
      } catch {
        case NonFatal(t) =>
          // Something bad happened while running the action: dispatch error event to the root of the current scope
          // NOTE: We used to dispatch the event to XFormsContainingDocument, but that is no longer a event
          // target. We thought about dispatching to the root control of the current scope, BUT in case the action
          // is running within a model before controls are created, that won't be available. SO the answer is to
          // dispatch to what we know exists, and that is the current observer or the target. The observer is
          // "closer" from the action, so we dispatch to that.
          Dispatch.dispatchEvent(new XXFormsActionErrorEvent(eventObserver, t))
      }
    } else {
      debug("skipping non-relevant handler", List(
        "event"        -> event.name,
        "observer"     -> event.targetObject.getEffectiveId,
        "handler name" -> eventHandler.localName,
        "handler id"   -> handlerEffectiveId
      ))
    }
  }

  // Given a static handler, and concrete observer and target, try to find the concrete handler
  private def resolveHandler(
    containingDocument : XFormsContainingDocument,
    handler            : EventHandler,
    eventObserver      : XFormsEventTarget,
    targetObject       : XFormsEventTarget
  ): Option[XFormsEventHandler] = {

    val resolvedObject =
      if (targetObject.scope == handler.scope) {
        // The scopes match so we can resolve the id relative to the target
        targetObject.container.resolveObjectByIdInScope(targetObject.getEffectiveId, handler.staticId)
      } else if (handler.isPhantom && ! handler.isWithinRepeat) {
        // Optimize for non-repeated phantom handler
        containingDocument.findObjectByEffectiveId(handler.prefixedId)
      } else if (handler.isPhantom) {
        // Repeated phantom handler

        val zeroOrOneControl =
          for {
            controls           <- Option(containingDocument.controls).toList
            effectiveControlId <-
              Controls.resolveControlsEffectiveIds(
                containingDocument.staticOps,
                controls.getCurrentControlTree,
                targetObject.getEffectiveId,
                handler.staticId,
                followIndexes = true // so this will return 0 or 1 element
              )
            control            <- controls.findObjectByEffectiveId(effectiveControlId)
          } yield
            control

        zeroOrOneControl.headOption

      } else {
        // See https://github.com/orbeon/orbeon-forms/issues/243
        warn(
          "skipping event in different scope (see issue #243)",
          List(
            "target id"             -> targetObject.getEffectiveId,
            "handler id"            -> handler.prefixedId,
            "observer id"           -> eventObserver.getEffectiveId,
            "target scope"          -> targetObject.scope.scopeId,
            "handler scope"         -> handler.scope.scopeId,
            "observer scope"        -> eventObserver.scope.scopeId
          ))(
          containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
        )
        None
      }

    resolvedObject map (_.asInstanceOf[XFormsEventHandler])
  }

  val propagateBreaks = new Breaks
  import propagateBreaks.{break, breakable}

  // Find all the handlers for the given event name if an event with that name is dispatched to this element.
  // For all relevant observers, find the handlers which match by phase
  private def handlersForEventImpl(ops: PartGlobalOps, e: ElementAnalysis, eventName: String): HandlerAnalysis = {

    // NOTE: For `phase == Target`, `observer eq element`.
    def relevantHandlersForObserverByPhaseAndName(observer: ElementAnalysis, phase: Phase) = {

      // We gather observers with `relevantObserversFromLeafToRoot` and either:
      //
      // - they have the same XBL scope
      // - OR there is at least one phantom handler for that observer
      //
      // So if the scopes are different, we must require that the handler is a phantom handler
      // (and therefore ignore handlers on that observer which are not phantom handlers).
      val requirePhantomHandler = observer.scope != e.scope

      def matchesPhaseNameTarget(eventHandler: EventHandler) =
        (
          eventHandler.isCapturePhaseOnly && phase == Phase.Capture ||
          eventHandler.isTargetPhase      && phase == Phase.Target  ||
          eventHandler.isBubblingPhase    && phase == Phase.Bubbling
        ) &&
          eventHandler.isMatchByNameAndTarget(eventName, e.prefixedId)

      def matches(eventHandler: EventHandler) =
        if (requirePhantomHandler)
          eventHandler.isPhantom && matchesPhaseNameTarget(eventHandler)
        else
          matchesPhaseNameTarget(eventHandler)

      val relevantHandlers = ops.getEventHandlersForObserver(observer.prefixedId) filter matches

      // DOM 3:
      //
      // - `stopPropagation`: "Prevents other event listeners from being triggered but its effect must be deferred
      //   until all event listeners attached on the Event.currentTarget have been triggered."
      // - `preventDefault`: "the event must be canceled, meaning any default actions normally taken by the
      //   implementation as a result of the event must not occur"
      // - NOTE: DOM 3 introduces also stopImmediatePropagation
      val propagate            = relevantHandlers forall (_.propagate == Propagate.Continue)
      val performDefaultAction = relevantHandlers forall (_.isPerformDefaultAction == Perform.Perform)

      // See https://github.com/orbeon/orbeon-forms/issues/3844
      def placeInnerHandlersFirst(handlers: List[EventHandler]): List[EventHandler] = {

        def isHandlerInnerHandlerOfObserver(handler: EventHandler) =
          handler.scope.parent contains observer.containerScope

        val (inner, outer) =
          handlers partition isHandlerInnerHandlerOfObserver

        inner ::: outer
      }

      (propagate, performDefaultAction, placeInnerHandlersFirst(relevantHandlers))
    }

    var propagate = true
    var performDefaultAction = true

    def handlersForPhase(observers: List[ElementAnalysis], phase: Phase): Option[(Phase, Map[String, List[EventHandler]])] = {
      val result = mutable.Map[String, List[EventHandler]]()
      breakable {
        for (observer <- observers) {

          val (localPropagate, localPerformDefaultAction, handlersToRun) =
            relevantHandlersForObserverByPhaseAndName(observer, phase)

          propagate &= localPropagate
          performDefaultAction &= localPerformDefaultAction
          if (handlersToRun.nonEmpty)
            result += observer.prefixedId -> handlersToRun

          // Cancel propagation if requested
          if (! propagate)
            break()
        }
      }

      if (result.nonEmpty)
        Some(phase -> result.toMap)
      else
        None
    }

    val observersFromLeafToRoot = relevantObserversAcrossPartsFromLeafToRoot(ops, e)

    val captureHandlers =
      handlersForPhase(observersFromLeafToRoot.tail.reverse, Phase.Capture)

    val targetHandlers =
      if (propagate)
        handlersForPhase(List(observersFromLeafToRoot.head), Phase.Target)
      else
        None

    val bubblingHandlers =
      if (propagate)
        handlersForPhase(observersFromLeafToRoot.tail, Phase.Bubbling)
      else
        None

    (performDefaultAction, Map.empty ++ captureHandlers ++ targetHandlers ++ bubblingHandlers)
  }

  // Find all observers (including in ancestor parts) which either match the current scope or have a phantom handler
  private def relevantObserversAcrossPartsFromLeafToRoot(ops: PartGlobalOps, e: ElementAnalysis): NonEmptyList[ElementAnalysis] = {

    def hasPhantomHandler(observer: ElementAnalysis) =
      ops.getEventHandlersForObserver(observer.prefixedId) exists (_.isPhantom)

    def relevant(observer: ElementAnalysis) =
      observer.scope == e.scope || hasPhantomHandler(observer)

    NonEmptyList(e, (ancestorsAcrossPartsIterator(e, includeSelf = false) filter relevant).toList)
  }
}
