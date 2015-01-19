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

import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms._
import control.Controls.AncestorOrSelfIterator
import event.events._
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent, XFormsEventObserver}
import Dispatch.EventListener
import org.orbeon.oxf.xforms.control.controls.{XFormsInputControl, XFormsRepeatIterationControl}
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
            // there a use case for it? Events can be dispatched via setIndex(), which means that repeats and relevance
            // can change. But is there a better way?
            if (repeatIterationsToModify.nonEmpty) {
                val controls = containingDocument.getControls
                // Find all repeat iterations and controls again
                for (repeatIterationEffectiveId ← repeatIterationsToModify) {
                    val repeatIterationControl = containingDocument.getControlByEffectiveId(repeatIterationEffectiveId).asInstanceOf[XFormsRepeatIterationControl]
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

                    val allIt =
                        focusableControls filterNot Focus.isHidden

                    val filteredIt =
                        if (focusEvent.inputOnly)
                            allIt collectFirst { case input: XFormsInputControl ⇒ input }
                        else
                            allIt.nextOption()

                    filteredIt foreach Focus.focusWithEvents
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

    def performTargetAction(event: XFormsEvent): Unit = ()

    // Check whether this concrete control supports receiving the external event specified
    final def allowExternalEvent(eventName: String) = staticControl match {
        case viewTrait: ViewTrait ⇒ viewTrait.externalEvents(eventName)
        case _ ⇒ false
    }

    // TODO LATER: should probably return true because most controls could then dispatch relevance events
    def supportsRefreshEvents = false

    // Dispatch creation events
    def dispatchCreationEvents(): Unit = {
        commitCurrentUIState()
        Dispatch.dispatchEvent(new XFormsEnabledEvent(this))
    }

    // Dispatch change events (between the control becoming enabled and disabled)
    def dispatchChangeEvents() = ()

    // Dispatch destruction events
    def dispatchDestructionEvents(): Unit = {
        // Don't test for relevance here
        // - in iteration removal case, control is still relevant
        // - in refresh case, control is non-relevant
        Dispatch.dispatchEvent(new XFormsDisabledEvent(this))
    }

    final def parentEventObserver: XFormsEventObserver = Option(parent) orNull

    final def addListener(eventName: String, listener: EventListener): Unit =
        throw new UnsupportedOperationException

    final def removeListener(eventName: String, listener: EventListener): Unit =
        throw new UnsupportedOperationException

    final def getListeners(eventName: String): Seq[EventListener] = Seq()
}
