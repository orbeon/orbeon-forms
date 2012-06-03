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
import control.Controls.AncestorIterator
import event.events._
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEventObserver
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.xbl.XBLContainer
import scala.Option
import java.util.{List ⇒ JList, Set ⇒ JSet}
import scala.collection.JavaConverters._

trait ControlEventSupport {

    self: XFormsControl ⇒

    def performDefaultAction(event: XFormsEvent): Unit = event match {
        case ev @ (_: XXFormsRepeatActivateEvent | _: XFormsFocusEvent) ⇒
            // Try to update xforms:repeat indexes based on this

            // Find current path through ancestor xforms:repeat elements, if any
            val repeatIterationsToModify =
                new AncestorIterator(self) collect
                    { case ri: XFormsRepeatIterationControl if ! ri.isCurrentIteration ⇒ ri.getEffectiveId }

            if (repeatIterationsToModify.nonEmpty) {
                val controls = containingDocument.getControls
                // Find all repeat iterations and controls again
                for (repeatIterationEffectiveId ← repeatIterationsToModify) {
                    val repeatIterationControl = controls.getObjectByEffectiveId(repeatIterationEffectiveId).asInstanceOf[XFormsRepeatIterationControl]
                    val newRepeatIndex = repeatIterationControl.iterationIndex

                    val indentedLogger = controls.getIndentedLogger
                    if (indentedLogger.isDebugEnabled)
                        indentedLogger.logDebug("xforms:repeat", "setting index upon focus change", "new index", newRepeatIndex.toString)

                    repeatIterationControl.repeat.setIndex(newRepeatIndex)
                }
            }

            // Focus on current control if possible
            if (XFormsEvents.XFORMS_FOCUS == event.getName)
                setFocus()

        case _: XFormsHelpEvent ⇒
            containingDocument.setClientHelpEffectiveControlId(getEffectiveId)
        case ev: XXFormsBindingErrorEvent ⇒
            XFormsError.handleNonFatalSetvalueError(containingDocument, ev.locationData, ev.reason)
        case _ ⇒
    }

    def performTargetAction(container: XBLContainer, event: XFormsEvent) = ()

    // Check whether this concrete control supports receiving the external event specified
    final def allowExternalEvent(eventName: String) =
        getAllowedExternalEvents.contains(eventName) || Set(XFormsEvents.KEYPRESS)(eventName)

    protected def getAllowedExternalEvents: JSet[String] = Set.empty[String].asJava

    // TODO LATER: should probably return true because most controls could then dispatch relevance events
    def supportsRefreshEvents = false

    // Consider that the parent of top-level controls is the containing document. This allows events to propagate to
    // the top-level.
    final def getParentEventObserver(containingDocument: XFormsContainingDocument): XFormsEventObserver =
        Option(parent) orNull

    final def addListener(eventName: String, listener: org.orbeon.oxf.xforms.event.EventListener): Unit =
        throw new UnsupportedOperationException

    final def removeListener(eventName: String, listener: org.orbeon.oxf.xforms.event.EventListener): Unit =
        throw new UnsupportedOperationException

    final def getListeners(eventName: String): JList[org.orbeon.oxf.xforms.event.EventListener] = null
}
