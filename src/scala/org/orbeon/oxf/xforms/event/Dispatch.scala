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


import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.util.Logging
import util.control.Breaks
import org.orbeon.oxf.xforms.event.XFormsEvent._

object Dispatch extends Logging {

    private val propagateBreaks = new Breaks
    import propagateBreaks.{break, breakable}

    // Event dispatching algorithm
    def dispatchEvent(event: XFormsEvent) {

        val containingDocument = event.containingDocument
        implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
        val targetObject = event.targetObject

        // Utility to help make sure we push and pop the event
        def withEvent[T](body: ⇒ T): T =
            try {
                containingDocument.startHandleEvent(event)
                body
            } finally
                containingDocument.endHandleEvent()

        var statHandleEvent = 0
        var statNativeHandlers = 0

        // Call all the handlers relevant for the given event on the given observer
        def callHandlers() = {

            val phase = event.currentPhase
            val observer = event.currentObserver
            val isPhantom = observer.scope != targetObject.scope

            def matchesPhaseAndCustom(eventHandler: EventHandler) =
                (eventHandler.isCapturePhaseOnly && phase == Capture ||
                 eventHandler.isTargetPhase      && phase == Target  ||
                 eventHandler.isBubblingPhase    && phase == Bubbling && event.bubbles) && eventHandler.isMatch(event)

            def matches(eventHandler: EventHandler) =
                if (isPhantom)
                    eventHandler.isPhantom && matchesPhaseAndCustom(eventHandler)
                else
                    matchesPhaseAndCustom(eventHandler)

            var propagate = true
            var performDefaultAction = true

            // For target phase, also perform "action at target" before running event handler
            // NOTE: As of 2011-03-07, this is used XFormsInstance for xforms-insert/xforms-delete processing,
            // and in XFormsUploadControl for upload processing.
            // withDebug("performing action at target")
            if (phase == Target)
                observer.performTargetAction(event)

            for (handler ← handlersForObserver(observer) filter matches) {

                withDebug("handler", Seq("name" → event.name, "phase" → phase.name, "observer" → observer.getEffectiveId)) {
                    handler.handleEvent(containingDocument, observer, event)
                    statHandleEvent += 1
                }

                // DOM 3: "Prevents other event listeners from being triggered but its effect must be deferred until
                // all event listeners attached on the Event.currentTarget have been triggered."
                // NOTE: DOM 3 introduces also stopImmediatePropagation
                propagate &= handler.isPropagate
                // DOM 3: "the event must be canceled, meaning any default actions normally taken by the
                // implementation as a result of the event must not occur"
                performDefaultAction &= handler.isPerformDefaultAction
            }

            // For target or bubbling phase, also call native listeners
            // NOTE: It would be nice to have all listeners exposed this way
            if (Set(Target, Bubbling)(phase))
                for (listener ← observer.getListeners(event.name)) {
                    listener.handleEvent(event)
                    statNativeHandlers += 1
                }

            (propagate, performDefaultAction)
        }

        try {
            withDebug("dispatching", Seq("name" → event.name, "target" → targetObject.getEffectiveId, "location" → (Option(event.locationData) map (_.toString) orNull))) {

                var propagate = true
                var performDefaultAction = true

                // Run all observers for the given phase
                def doPhase(observers: Seq[XFormsEventObserver], phase: Phase) =
                    breakable {
                        for (observer ← observers) {

                            event.currentObserver = observer
                            event.currentPhase = phase

                            // Call event handlers
                            val (localPropagate, localPerformDefaultAction) = callHandlers()

                            propagate &= localPropagate
                            performDefaultAction &= localPerformDefaultAction

                            // Cancel propagation if requested
                            if (! propagate)
                                break()
                        }
                    }

                // Find all observers
                val eventObservers = findObservers(targetObject, event)

                withEvent {
                    // Capture phase
                    doPhase(eventObservers.reverse.init, Capture)

                    // Target phase
                    if (propagate)
                        doPhase(Seq(eventObservers.head), Target)

                    // Bubbling phase
                    if (propagate)
                        doPhase(eventObservers.tail, Bubbling)

                    // Perform default action if allowed to
                    if (performDefaultAction || ! event.cancelable)
                        withDebug("performing default action") {
                            targetObject.performDefaultAction(event)
                        }

                    debugResults(Seq(
                        "regular handlers called" → statHandleEvent.toString,
                        "native handlers called"  → statNativeHandlers.toString
                    ))
                }
            }
        } catch {
            case e: Exception ⇒
                // Add location information if possible
                val locationData = Option(targetObject.getLocationData).orNull
                throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "dispatching XForms event", "event", event.name, "target id", targetObject.getEffectiveId))
        }
    }

    // Find observers for event dispatch, from leaf to root
    // The first observer will be the target object
    private def findObservers(targetObject: XFormsEventTarget, event: XFormsEvent): Seq[XFormsEventObserver] = {

        val targetObserver = targetObject match {
            case targetObject: XFormsEventObserver ⇒ targetObject
            case _ ⇒ throw new IllegalArgumentException // this must not happen with the current class hierarchy
        }

        // TODO: Since we are looking at handlers here, we could collect them so we don't have to collect them again later?
        def hasPhantomHandler(observer: XFormsEventObserver) =
            handlersForObserver(observer) exists (_.isPhantom)

        // Iterator over a target's ancestor observers
        val allObserversIterator =
            Iterator.iterate(targetObserver)(_.parentEventObserver) takeWhile (_ ne null)

        // Iterator over all the observers we need to handle
        val targetScope = targetObject.scope
        val observerIterator =
            allObserversIterator filter (observer ⇒ observer.scope == targetScope || hasPhantomHandler(observer))

        observerIterator.toList.ensuring(_.head eq targetObject)
    }

    private def handlersForObserver(observer: XFormsEventObserver) = {
        val part = observer.container.getPartAnalysis
        part.getEventHandlers(observer.getPrefixedId)
    }
}
