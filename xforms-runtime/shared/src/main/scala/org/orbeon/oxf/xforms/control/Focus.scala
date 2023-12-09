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

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.control.Controls.AncestorOrSelfIterator
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.event.XFormsEvents.{DOM_FOCUS_IN, DOM_FOCUS_OUT}
import org.orbeon.oxf.xforms.event._

// Handle control focus
object Focus {

  // Focus on the given control and dispatch appropriate focus events
  def focusWithEvents(control: XFormsControl): Unit =
    if (control.isDirectlyFocusable) {

      val doc = control.containingDocument

      // Read previous control
      val previousOption = doc.controls.getFocusedControl

      // Focus has not changed so don't do anything
      if (! (previousOption exists (_ eq control))) {
        // Remember first that the new control has focus
        doc.controls.setFocusedControl(Some(control))

        // Ancestor-or-self chains from root to leaf
        val previousChain = previousOption.toList flatMap (containersAndSelf(_).reverse)
        val currentChain = containersAndSelf(control).reverse

        // Number of common ancestor containers, if any
        val commonPrefix = previousChain zip currentChain prefixLength { case (previous, current) => previous eq current}

        // Focus out of the previous control and grouping controls we are leaving
        // Events are dispatched from leaf to root
        (previousChain drop commonPrefix reverse) foreach focusOut

        // Focus into the grouping controls we are entering and the current control
        // Events are dispatched from root to leaf
        currentChain drop commonPrefix foreach focusIn
      }
    }

  // Update focus based on a previously focused control
  def updateFocusWithEvents(
    focusedBeforeOpt : Option[XFormsControl],
    repeatOpt        : Option[XFormsRepeatControl] = None)(
    doc              : XFormsContainingDocument
  ): Unit =
    repeatOpt match {
      case Some(repeat) =>
        // Update the focus based on a previously focused control for which focus out events up to a repeat have
        // already been dispatched when a repeat iteration has been removed

        // Do as if focus hadn't been removed yet, as updateFocus expects it
        doc.controls.setFocusedControl(focusedBeforeOpt)

        // Called if the focus is fully removed
        // Focus out events have been dispatched up to the iteration already, so just dispatch from the repeat to the root
        def removeFocus(): Unit =
          containersAndSelf(repeat) foreach focusOut

        // Called if the focus is changing to a new control
        // This will dispatch focus events from the new repeat iteration to the control
        def focus(control: XFormsControl): Unit =
          setFocusPartially(control, Some(repeat))

        updateFocus(focusedBeforeOpt, _ => removeFocus(), focus)
      case None =>
        updateFocus(focusedBeforeOpt, focusedBefore => removeFocus(focusedBefore.containingDocument), focusWithEvents)
    }

  // Update focus based on a previously focused control
  private def updateFocus(
    focusedBeforeOpt : Option[XFormsControl],
    onRemoveFocus    : XFormsControl => Any,
    onFocus          : XFormsControl => Any
  ): Unit =
    focusedBeforeOpt match {
      case Some(focusedBefore) =>
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
        if (doc.controls.getFocusedControl exists (_ eq focusedBefore)) {
          // Nobody has changed the focus with `setFocusedControl()`, which means nobody has dispatched
          // xforms-focus (or xforms-focus didn't actually change the focus). We need to validate that the control
          // with focus now is still focusable and is still following repeat indexes. If not, we must adjust the
          // focus accordingly.

          // Obtain a new reference to the control via the index, following repeats
          val newReferenceWithRepeats = XFormsRepeatControl.findControlFollowIndexes(focusedBefore)

          newReferenceWithRepeats match {
            case None =>
              // Cannot find a reference to the control anymore
              // Control might be a ghost that has been removed from the tree (iteration removed)
              onRemoveFocus(focusedBefore)

            case Some(newReference) if ! (newReference.isDirectlyFocusable) =>
              // New reference exists, but is not focusable
              onRemoveFocus(focusedBefore)

            case Some(newReference) if newReference ne focusedBefore =>
              // Control exists and is focusable, and is not the same as the original control

              // This covers the case where repeat indexes have been updated
              // Here we move the focus to the new control
              onFocus(newReference)

            case _ =>
              // Control exists, is focusable, and is the same as before, so we do nothing!
          }
        } else {
          // The focus is different or has been removed
          //
          // - if different, the change must have been done via xforms-focus
          // - if removed, the change might have been done in XFormsRepeatControl upon removing an iteration
          //
          // Either way events must have already been dispatched, so here we do nothing.
        }
      case None =>
        // There was no focus before. If there is focus now, the change must have been done via xforms-focus, which
        // means that events have already been dispatched. If there is no focus now, nothing has changed. So here we
        // do nothing.
    }

  // Whether focus is currently within the given container
  def isFocusWithinContainer(container: XFormsContainerControl): Boolean =
    container.containingDocument.controls.getFocusedControl match {
      case Some(control) if new AncestorOrSelfIterator(control.parent) exists (_ eq container) => true
      case _ => false
    }

  private def isNotBoundary(control: XFormsControl, boundary: Option[XFormsContainerControl]) =
    boundary.isEmpty || (control ne boundary.get)

  // Partially remove the focus until the given boundary if any
  // The boundary is used by XFormsRepeatControl when an iteration is removed if the focus is within that iteration
  def removeFocusPartially(doc: XFormsContainingDocument, boundary: Option[XFormsContainerControl]): Unit = {

    // Dispatch DOMFocusOut events to the given control and to its container ancestors
    def dispatchFocusOuts(control: XFormsControl): Unit =
      containersAndSelf(control) takeWhile (isNotBoundary(_, boundary)) foreach focusOut

    // Dispatch focus out events if needed
    doc.controls.getFocusedControl foreach { focused =>
      doc.controls.setFocusedControl(None)
      dispatchFocusOuts(focused)
    }
  }

  // Partially set the focus to the control, dispatching events from the given boundary if any
  def setFocusPartially(control: XFormsControl, boundary: Option[XFormsContainerControl]): Unit = {

    require(control ne null)

    val doc = control.containingDocument

    // Remember first that the new control has focus
    doc.controls.setFocusedControl(Some(control))

    // Dispatch DOMFocusOut events to the given control and to its container ancestors
    def dispatchFocusIns(control: XFormsControl): Unit =
      (containersAndSelf(control) takeWhile (isNotBoundary(_, boundary)) reverse) foreach focusIn

    // Dispatch focus in events
    dispatchFocusIns(control)
  }

  // Remove the focus entirely and dispatch the appropriate events
  def removeFocus(doc: XFormsContainingDocument): Unit =
    removeFocusPartially(doc, boundary = None)

  // Whether the control is hidden within a non-visible case or dialog
  def isHidden(control: XFormsControl): Boolean = new AncestorOrSelfIterator(control.parent) exists {
    case c: XFormsCaseControl     if ! c.isCaseVisible   => true
    case c: XXFormsDialogControl  if ! c.isDialogVisible => true
    case _                                               => false
  }

  // Return all the ancestor-or-self hidden cases
  def ancestorOrSelfHiddenCases(control: XFormsControl): Iterator[XFormsCaseControl] =
    new AncestorOrSelfIterator(control) collect {
      case switchCase: XFormsCaseControl if ! switchCase.isCaseVisible => switchCase
    }

  // Dispatch DOMFocusOut and DOMFocusIn
  private def focusOut(control: XFormsControl): Unit = dispatch(control, DOM_FOCUS_OUT)
  private def focusIn(control: XFormsControl) : Unit = dispatch(control, DOM_FOCUS_IN)

  private def dispatch(control: XFormsControl, eventName: String): Unit =
    Dispatch.dispatchEvent(XFormsEventFactory.createEvent(eventName, control), EventCollector.ToReview)

  // Find all ancestor container controls of the given control from leaf to root
  private def containers(control: XFormsControl) =
    new AncestorOrSelfIterator(control.parent) collect
      { case container: XFormsContainerControl => container } toList

  // Ancestor controls and control from leaf to root excepting the root control
  private def containersAndSelf(control: XFormsControl) =
    control :: containers(control).init
}
