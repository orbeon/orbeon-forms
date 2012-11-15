/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.xforms._
import control.Controls.AncestorOrSelfIterator
import event.events._
import event.{EventListener, XFormsEvent, XFormsEventObserver}
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl
import org.orbeon.oxf.xforms.analysis.controls.ViewTrait

trait ControlEventSupport {

    self: XFormsControl ⇒

    def performDefaultAction(event: XFormsEvent): Unit = event match {
        case ev @ (_: XXFormsRepeatActivateEvent | _: XFormsFocusEvent) ⇒
            // Try to update xf:repeat indexes based on this

            // Find current path through ancestor xf:repeat elements, if any
            val repeatIterationsToModify =
                new AncestorOrSelfIterator(self) collect
                    { case ri: XFormsRepeatIterationControl if ! ri.isCurrentIteration ⇒ ri.getEffectiveId }

            // NOTE: It would be nice to review whether it makes sense to re-obtain controls by id in the code below. Is
            // there a use case for it? Events canbe dispatched via setIndex(), which means that repeats and relevance
            // can change. But is there a better way?
            if (repeatIterationsToModify.nonEmpty) {
                val controls = containingDocument.getControls
                // Find all repeat iterations and controls again
                for (repeatIterationEffectiveId ← repeatIterationsToModify) {
                    val repeatIterationControl = controls.getObjectByEffectiveId(repeatIterationEffectiveId).asInstanceOf[XFormsRepeatIterationControl]
                    val newRepeatIndex = repeatIterationControl.iterationIndex

                    val indentedLogger = controls.getIndentedLogger
                    if (indentedLogger.isDebugEnabled)
                        indentedLogger.logDebug("xf:repeat", "setting index upon focus change", "new index", newRepeatIndex.toString)

                    repeatIterationControl.repeat.setIndex(newRepeatIndex)
                }
            }

            // Focus on current control if possible
            event match {
                case focusEvent: XFormsFocusEvent ⇒
                    
                    // Try to update hidden xf:case controls
                    // NOTE: We don't allow this behavior when events come from the client in ClientEvents
                    // NOTE: See note above on re-obtaining controls by id. Do we need to do this here as well?
                    Focus.hiddenCases(this) foreach (_.toggle())
                    
                    setFocus(focusEvent.inputOnly)
                case _ ⇒
            }

        case _: XFormsHelpEvent ⇒
            containingDocument.setClientHelpEffectiveControlId(getEffectiveId)
        case ev: XXFormsBindingErrorEvent ⇒
            XFormsError.handleNonFatalSetvalueError(this, ev.locationData, ev.reason)
        case ev: XXFormsActionErrorEvent ⇒
            XFormsError.handleNonFatalActionError(this, ev.throwable)
        case _ ⇒
    }

    def performTargetAction(event: XFormsEvent): Unit = event match {
        case _: DOMFocusOutEvent ⇒
            // Mark control visited upon focus out event. This applies to any control, including grouping controls. We
            // do this upon the event reaching the target, so that by the time a regular event listener makes use of the
            // visited property, it is up to date. This seems reasonable since DOMFocusOut indicates that the focus has
            // already left the control.
            self.visited = true
        case _ ⇒
    }

    // Check whether this concrete control supports receiving the external event specified
    final def allowExternalEvent(eventName: String) = staticControl match {
        case viewTrait: ViewTrait ⇒ viewTrait.externalEvents(eventName)
        case _ ⇒ false
    }

    // TODO LATER: should probably return true because most controls could then dispatch relevance events
    def supportsRefreshEvents = false

    final def parentEventObserver: XFormsEventObserver = Option(parent) orNull

    final def addListener(eventName: String, listener: EventListener): Unit =
        throw new UnsupportedOperationException

    final def removeListener(eventName: String, listener: EventListener): Unit =
        throw new UnsupportedOperationException

    final def getListeners(eventName: String): Seq[EventListener] = Seq()
}
