/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import controls.{XXFormsRootControl, XFormsRepeatControl, XFormsCaseControl, XXFormsDialogControl}
import org.orbeon.oxf.xforms.event.events.{DOMFocusOutEvent,DOMFocusInEvent}
import org.orbeon.oxf.xforms.control.Controls.AncestorIterator
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.event.events.XFormsUIEvent
import collection.JavaConverters._

import java.util.{ArrayList ⇒ JArrayList, LinkedHashMap ⇒ JLinkedHashMap}
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.XFormsEvents.{DOM_FOCUS_OUT, DOM_FOCUS_IN}

// Handle control focus
object Focus {

    // Focus on the given control and dispatch appropriate focus events
    def focusWithEvents(control: XFormsControl ): Boolean = {

        // Continue only if control is focusable
        if (! isFocusable(control))
            return false

        val doc = control.containingDocument

        // Read previous control
        val previousOption = Option(doc.getControls.getFocusedControl)

        // Focus has not changed so don't do anything
        if (previousOption exists (_ eq control))
            return true

        // Remember first that the new control has focus
        doc.getControls.setFocusedControl(control)

        // Ancestor-or-self chains from root to leaf
        val previousChain = previousOption.toList flatMap (containersAndSelf(_))
        val currentChain = containersAndSelf(control)

        // Number of common ancestor containers, if any
        val commonPrefix = previousChain zip currentChain prefixLength
            { case (previous, current) ⇒ previous eq current }

        // Focus out of the previous control and grouping controls we are leaving
        // Events are dispatched from leaf to root
        (previousChain drop commonPrefix reverse) foreach (focusOut(_))

        // Focus into the grouping controls we are entering and the current control
        // Events are dispatched from root to leaf
        currentChain drop commonPrefix foreach (focusIn(_))

        true
    }

    // Updated focus based on a previously focused control
    def updateFocus(focusedBefore: XFormsControl ) =
        if (focusedBefore ne null) {
            // There was a control with focus before

            // If there was a focused control and nobody has overwritten it with `setFocusedControl()` (NOTE:
            // destruction events can be dispatched upon updating bindings, and in theory change the focused control!),
            // make sure that the control is still able to hold focus. It may not, for example:
            //
            // - it may have become non-relevant or read-only
            // - it may have been in an iteration that has been removed
            //
            // If it's not able to hold focus, remove focus and dispatch focus events

            val doc = focusedBefore.containingDocument
            if (focusedBefore eq doc.getControls.getFocusedControl) {
                // Nobody has changed the focus with `setFocusedControl()`, which means nobody has dispatched
                // xforms-focus (or xforms-focus didn't actually change the focus). We need to validate that the control
                // with focus now is still focusable and is still following repeat indexes. If not, we must adjust the
                // focus accordingly.

                // Obtain a new reference to the control via the index, following repeats
                val newReferenceWithRepeats = XFormsRepeatControl.findControlFollowIndexes(focusedBefore)

                newReferenceWithRepeats match {
                    case None ⇒
                        // Cannot find a reference to the control anymore
                        // Control might be a ghost that has been removed from the tree (iteration removed)
                        removeFocus(doc)

                    case Some(newReference) if ! isFocusable(newReference) ⇒
                        // New reference exists, but is not focusable
                        removeFocus(doc)

                    case Some(newReference) if newReference ne focusedBefore ⇒
                        // Control exists and is focusable, and is not the same as the original control

                        // This covers the case where repeat indexes have been updated
                        // Here we move the focus to the new control
                        focusWithEvents(newReference)

                    case _ ⇒
                        // Control exists, is focusable, and is the same as before, so we do nothing!
                }
            } else {
                // Either there is no focus now, or the focus is different. Either way, the change must have been done
                // via xforms-focus, which means that events have already been dispatched. So here we do nothing.
            }
        } else {
            // Whether there was focus before or not, or whether there is focus now or not the change must have been
            // done via xforms-focus, which means that events have already been dispatched. So here we do nothing.
        }

    // Whether focus is currently within the given container
    def isFocusWithinContainer(container: XFormsContainerControl) =
        Option(container.containingDocument.getControls.getFocusedControl) match {
            case Some(control) if new AncestorIterator(control.parent) exists (_ eq container) ⇒ true
            case _ ⇒ false
        }

    // Completely remove the focus
    def removeFocus(doc: XFormsContainingDocument) {

        // Dispatch DOMFocusOut events to the given focusable control and to all its container ancestors
        def dispatchFocusOutUponNonRelevance(control: XFormsControl) =
            (containersAndSelf(control) reverse) foreach (focusOut(_))

        // Read previous control
        Option(doc.getControls.getFocusedControl) foreach { focusedBefore ⇒
            // Forget focus
            doc.getControls.setFocusedControl(null)
            // Dispatch focus out events to the control and its ancestors
            dispatchFocusOutUponNonRelevance(focusedBefore)
        }
    }

    // Find boundaries for event dispatch
    // Put here temporarily as this deals with focus events, but must be moved to some better place when possible
    def findBoundaries(targetObject: XFormsEventTarget, event: XFormsEvent):
        (JArrayList[XFormsEventObserver], JLinkedHashMap[String, XFormsEvent], JArrayList[XFormsEventObserver]) = {

        val doc = event.getTargetXBLContainer.getContainingDocument

        val boundaries = new JArrayList[XFormsEventObserver]
        val eventsForBoundaries = new JLinkedHashMap[String, XFormsEvent] // Map<String effectiveId, XFormsEvent event>
        val eventObservers = new JArrayList[XFormsEventObserver]

        val startObserver = targetObject match {
            case targetObject: XFormsEventObserver ⇒ targetObject
            case _ ⇒ targetObject.getParentEventObserver(doc) // why this is needed?
        }

        val ignoreObserver = (o: XFormsEventObserver) ⇒ o.isInstanceOf[XFormsRepeatControl] && (o ne targetObject) || o.isInstanceOf[XXFormsRootControl]
        val notReachedComponent = (o: XFormsEventObserver) ⇒ ! (o.isInstanceOf[XFormsComponentControl] && (o ne targetObject))

        // Iterator over all observers except those we always ignore
        val commonIterator = new ObserverIterator(startObserver, doc) filterNot (ignoreObserver)

        // Iterator over all the observers we need to handle
        val observerIterator =
            event match {
                case focusEvent @ (_: DOMFocusInEvent | _: DOMFocusOutEvent) ⇒
                    // Proper event propagation over scopes for focus events

                    val targetScope = targetObject.getScope(doc)
                    commonIterator filter (_.getScope(doc) == targetScope)

                case uiEvent: XFormsUIEvent ⇒
                    // Broken retargeting for other UI events

                    def addRetarget(o: XFormsEventObserver) {
                        boundaries.add(o);
                        eventsForBoundaries.put(o.getEffectiveId, null)
                    }

                    commonIterator map {o ⇒ if (! notReachedComponent(o)) addRetarget(o); o}

                case _ ⇒
                    // For other events, simply stop propagation at the component boundary
                    // This is broken too as it doesn't follow scopes!

                    commonIterator takeWhile (notReachedComponent)
            }

        eventObservers.addAll(observerIterator.toList.asJava)

        (boundaries, eventsForBoundaries, eventObservers)
    }

    // Iterator over a control's ancestors
    class ObserverIterator(start: XFormsEventObserver, doc: XFormsContainingDocument) extends Iterator[XFormsEventObserver] {
        private var _next = start
        def hasNext = _next ne null
        def next() = {
            val result = _next
            _next = _next.getParentEventObserver(doc)
            result
        }
    }

    // Whether the control is hidden within a non-visible case or dialog
    private def isHidden(control: XFormsControl) = new AncestorIterator(control.parent) exists {
        case switchCase: XFormsCaseControl if ! switchCase.isVisible ⇒ true
        case dialog: XXFormsDialogControl if ! dialog.isVisible ⇒ true
        case _ ⇒ false
    }

    // Whether the control is focusable, that is it supports focus, is relevant, not read-only, and is not in a hidden case
    private def isFocusable(control: XFormsControl) = control match {
        case focusable: XFormsSingleNodeControl with FocusableTrait if focusable.isReadonly ⇒ false
        case focusable: FocusableTrait if focusable.isRelevant && ! isHidden(focusable) ⇒ true
        case _ ⇒ false
    }

    // Dispatch DOMFocusOut and DOMFocusIn
    private def focusOut(control: XFormsControl) = dispatch(control, DOM_FOCUS_OUT)
    private def focusIn(control: XFormsControl)  = dispatch(control, DOM_FOCUS_IN)

    private def dispatch(control: XFormsControl, eventName: String) =
        control.container.dispatchEvent(XFormsEventFactory.createEvent(control.containingDocument, eventName, control))

    // Find all ancestor container controls of the given control from leaf to root
    private def containers(control: XFormsControl) =
        new AncestorIterator(control.parent) collect
            { case container: XFormsContainerControl ⇒ container } toList

    // Ancestor controls and control from root to leaf excepting the root control
    private def containersAndSelf(control: XFormsControl) =
        control :: containers(control).init reverse
}
