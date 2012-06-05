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


import org.orbeon.oxf.xforms.event.events.{DOMFocusOutEvent, DOMFocusInEvent}
import org.orbeon.oxf.xforms.event.events.XFormsUIEvent

import org.orbeon.oxf.xforms.control.controls.XXFormsComponentRootControl
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData
import org.orbeon.oxf.util.DebugLogger._
import collection.mutable.Buffer
import util.control.Breaks

object Dispatch {

    private val propagateBreaks = new Breaks
    import propagateBreaks.{break, breakable}

    // Event dispatching algorithm
    def dispatchEvent(originalEvent: XFormsEvent) {

        val containingDocument = originalEvent.containingDocument
        implicit val indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
        val targetObject = originalEvent.getTargetObject.ensuring(_ ne null)

        def withEvent[T](event: XFormsEvent)(body: ⇒ T): T =
            try {
                containingDocument.startHandleEvent(event)
                body
            } finally
                containingDocument.endHandleEvent()

        // Handle the given event on the given observer
        def handleEvent(event: XFormsEvent, observer: XFormsEventObserver) = {

            var propagate = true
            var performDefaultAction = true

            def handlersForObserver(observer: XFormsEventObserver) = {
                def part = observer.getXBLContainer(containingDocument).getPartAnalysis
                part.getEventHandlers(observer.getPrefixedId)
            }

            def matches(eventHandler: EventHandler) =
                (eventHandler.isCapturePhaseOnly && event.getCurrentPhase == XFormsEvent.Phase.capture ||
                 eventHandler.isTargetPhase      && event.getCurrentPhase == XFormsEvent.Phase.target   ||
                 eventHandler.isBubblingPhase    && event.getCurrentPhase == XFormsEvent.Phase.bubbling && originalEvent.bubbles) && eventHandler.isMatch(event)

            for (handler ← handlersForObserver(observer) filter matches) {
                withDebug("handler", Seq("phase" → event.getCurrentPhase.name(), "observer" → observer.getEffectiveId)) {// TODO logging
                    withEvent(event) {
                        handler.handleEvent(containingDocument, observer, event)
                    }
                }

                // DOM 3: "Prevents other event listeners from being triggered but its effect must be deferred until
                // all event listeners attached on the Event.currentTarget have been triggered."
                // NOTE: DOM 3 introduces also stopImmediatePropagation
                propagate &= handler.isPropagate
                // DOM 3: "the event must be canceled, meaning any default actions normally taken by the
                // implementation as a result of the event must not occur"
                performDefaultAction &= handler.isPerformDefaultAction
            }

            (propagate, performDefaultAction)
        }

        def nextOrNull(i: Iterator[XFormsEventObserver]) = if (i.hasNext) i.next() else null

        try {
            withDebug("dispatching", Seq("name" → originalEvent.getName, "id" → targetObject.getEffectiveId, "location" → (Option(originalEvent.getLocationData) map (_.toString) orNull))) {
                // Find all observers
                val (boundaries, eventObservers) = findObservers(targetObject, originalEvent)
                var propagate = true
                var performDefaultAction = true

                val boundariesFromRoot = boundaries.reverse
                val retargetedEventsFromRoot = (boundariesFromRoot map (originalEvent.retarget(_))) :+ originalEvent

                breakable {
                    // Handle event retargeting
                    val retargetedEvents = retargetedEventsFromRoot.iterator
                    var retargetedEvent = retargetedEvents.next()

                    val boundaryIterator = boundariesFromRoot.iterator
                    var nextBoundary = nextOrNull(boundaryIterator)

                    // Capture phase, from root to leaf, ignoring the last observer which is the target
                    for (observer ← eventObservers.reverse.init) {

                        // Update event with current phase and observer
                        retargetedEvent.setCurrentPhase(XFormsEvent.Phase.capture)
                        retargetedEvent.setCurrentObserver(observer)

                        // Process event handlers
                        val (propagate1, performDefaultAction1) = handleEvent(retargetedEvent, observer)

                        propagate &= propagate1
                        performDefaultAction &= performDefaultAction1

                        // Cancel propagation if requested
                        if (! propagate)
                            break()

                        // Handle event retargeting
                        if (nextBoundary eq observer) {
                            nextBoundary = nextOrNull(boundaryIterator)
                            retargetedEvent = retargetedEvents.next()

                            debug("retargeting", Seq("name" → originalEvent.getName, "original id" → targetObject.getEffectiveId, "new id" → targetObject.getEffectiveId))
                        }
                    }
                }

                // Target and bubbling phases
                if (propagate) {
                    breakable {
                        // Handle event retargeting
                        val retargetedEvents = retargetedEventsFromRoot.reverseIterator
                        var retargetedEvent = retargetedEvents.next()

                        val boundaryIterator = boundaries.iterator
                        var nextBoundary = nextOrNull(boundaryIterator)

                        // Go from leaf to root
                        for (observer ← eventObservers) {
                            // Handle event retargeting
                            if (nextBoundary eq observer) {
                                nextBoundary = nextOrNull(boundaryIterator)
                                retargetedEvent = retargetedEvents.next()

                                debug("retargeting", Seq("name" → originalEvent.getName, "original id" → targetObject.getEffectiveId, "new id" → targetObject.getEffectiveId))
                            }

                            // Process "action at target"
                            // NOTE: As of 2011-03-07, this is used XFormsInstance for xforms-insert/xforms-delete processing,
                            // and in XFormsUploadControl for upload processing.
                            // NOTE: Possibly testing on retargetedEvent.getTargetObject() might be more correct, but we should
                            // fix event retargeting to make more sense in the first place.
                            val isAtTarget = observer eq targetObject
                            if (isAtTarget)
                                withEvent(retargetedEvent) {
                                    observer.performTargetAction(observer.getXBLContainer(containingDocument), retargetedEvent)
                                }

                            // Update event with current phase and observer
                            retargetedEvent.setCurrentPhase(if (isAtTarget) XFormsEvent.Phase.target else XFormsEvent.Phase.bubbling)
                            retargetedEvent.setCurrentObserver(observer)

                            // Process event handlers
                            val (propagate1, performDefaultAction1) = handleEvent(retargetedEvent, observer)

                            propagate &= propagate1
                            performDefaultAction &= performDefaultAction1

                            // Custom event listeners
                            // TODO: Would be nice to have all listeners exposed this way
                            for (listener ← observer.getListeners(originalEvent.getName))
                                withEvent(retargetedEvent) {
                                    listener.handleEvent(retargetedEvent)
                                }

                            // Cancel propagation if requested
                            if (! propagate)
                                break()
                        }
                    }
                }

                // Perform default action if allowed to
                if (performDefaultAction || ! originalEvent.cancelable)
                    withDebug("default action handler") {
                        withEvent(originalEvent) {
                            targetObject.performDefaultAction(originalEvent)
                        }
                    }
            }
        } catch {
            case e: Exception ⇒
                // Add location information if possible
                val locationData = Option(targetObject.getLocationData).orNull
                throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "dispatching XForms event", "event", originalEvent.getName, "target id", targetObject.getEffectiveId))
        }
    }

    // Find observers and boundaries for event dispatch, from leaf to root
    // The first observer will be the target object
    def findObservers(targetObject: XFormsEventTarget, event: XFormsEvent): (Seq[XFormsEventObserver], Seq[XFormsEventObserver]) = {

        val doc = event.containingDocument

        val startObserver = targetObject match {
            case targetObject: XFormsEventObserver ⇒ targetObject
            case _ ⇒ throw new IllegalArgumentException // this must not happen with the current class hierarchy
        }

        // Iterator over a target's ancestor observers
        class ObserverIterator(start: XFormsEventObserver, doc: XFormsContainingDocument) extends Iterator[XFormsEventObserver] {
            private var _next = start

            def hasNext = _next ne null

            def next() = {
                val result = _next
                _next = _next.getParentEventObserver(doc)
                result
            }
        }

        val boundaries = Buffer[XFormsEventObserver]()

        // Iterator over all observers
        val commonIterator = new ObserverIterator(startObserver, doc)

        // Iterator over all the observers we need to handle
        val observerIterator =
            event match {
                case uiEvent: XFormsUIEvent if !(uiEvent.isInstanceOf[DOMFocusInEvent] || uiEvent.isInstanceOf[DOMFocusOutEvent]) ⇒
                    // Broken retargeting for UI events other than focus events
                    // See: https://github.com/orbeon/orbeon-forms/issues/282

                    // Register a retarget boundary
                    def addRetarget(observer: XFormsEventObserver): Unit =
                        boundaries += observer

                    // Algorithm as follows: start with target and go up following scopes. If we reach an XBL root, then
                    // we retarget the event to the containing component.
                    var currentTargetScope = targetObject.getScope(doc)
                    val result = Buffer[XFormsEventObserver]()
                    while (commonIterator.hasNext) {
                        val current = commonIterator.next()

                        if (current.getScope(doc) == currentTargetScope) {
                            if (current.isInstanceOf[XXFormsComponentRootControl]) {
                                val component = current.getParentEventObserver(doc)
                                addRetarget(component)
                                currentTargetScope = component.getScope(doc)
                            }
                            result += current
                        }
                    }

                    result.iterator

                case _ ⇒
                    // For other events, including focus events, just follow scopes
                    val targetScope = targetObject.getScope(doc)
                    commonIterator filter (_.getScope(doc) == targetScope)
            }

        (boundaries.toList, observerIterator.toList.ensuring(_.head eq targetObject))
    }
}
