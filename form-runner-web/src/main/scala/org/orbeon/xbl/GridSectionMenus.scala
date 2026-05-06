/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import enumeratum.*
import enumeratum.EnumEntry.Hyphencase
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, AjaxEvent, XFormsId}
import org.scalajs.dom
import org.scalajs.dom.{KeyboardEvent, KeyboardEventInit, html}


object GridSectionMenus {
  sealed trait Operation extends EnumEntry with Hyphencase
  object Operation extends Enum[Operation] {
    val values: IndexedSeq[Operation] = findValues
    case object MoveUp      extends Operation
    case object MoveDown    extends Operation
    case object InsertAbove extends Operation
    case object InsertBelow extends Operation
    case object Clear       extends Operation
    case object Remove      extends Operation
  }
}

trait GridSectionMenus {

  import GridSectionMenus.*
  import Util.*

  def componentName: String

  // Keep pointing to menu so we can move it around as needed
  // Old comment: NOTE: When scripts are in the head, this returns undefined. Should be fixed!
  private val globalMenuElem: html.Element = dom.document.querySelectorT(s".fr-$componentName-dropdown-menu")

  case class CurrentComponent(currentComponentId: String, currentIteration: Int)

  // This transiently holds information between a click on the menu and a subsequent action in the menu.
  // Not nice but we don't have FRP at this point.
  private var currentComponentOpt: Option[CurrentComponent] = None

  // Initialization
  if (globalMenuElem != null) {
    // Click on our own button moves and shows the menu
    dom.document.addEventListener("keydown", (e: dom.Event) =>
      if (e.targetT.matches(s".fr-$componentName-dropdown-button"))
        delegateKeyEventToBootstrapButtonHandler(e)
    )

    dom.document.addEventListener(
      "click",
      (event: dom.Event) => {

        val target  = event.targetT
        if (target.closestOpt(s".fr-$componentName-dropdown-button").nonEmpty) moveAndShowMenuHandler(event)
        if (target.closestOpt(s".fr-$componentName-remove-button").nonEmpty  ) removeIterationHandler(event)

        // Menu actions
        Operation.values.foreach { op =>
          target
            .closestOpt(s".fr-$componentName-dropdown-menu .fr-${op.entryName}")
            .foreach(_ => actionHandler(op, event))
        }
      },
      // Use capture to run before Bootstrap's document-level `clearMenus()` handler, otherwise on click
      // `clearMenus()` closes the menu, and we reopen it immediately afterward.
      useCapture = true
    )
  }

  private def removeIterationHandler(event: dom.Event): Unit = {

    val buttonOpt =
      event.targetT.closestOpt(s".fr-$componentName-remove-button")

    componentId(event)
      .zip(buttonOpt.flatMap(findIterationForElemWithId))
      .foreach {
        case (currentComponentId, currentIteration) =>
          dispatchActionEvent(Operation.Remove, currentComponentId, currentIteration)
      }
  }

  private def moveAndShowMenuHandler(e: dom.Event): Unit = {

    moveMenu(e)

    // NOTE: Don't use dropdown("toggle") as that registers a new handler further down the DOM!
    globalMenuElem.querySelectorT(".dropdown-toggle").click()

    // Prevent "propagation". In fact, with jQuery, "delegated" handlers are handled first, and if a delegated
    // event calls stopPropagation(), then "directly-bound" handlers are not called. Yeah. So here, we prevent
    // propagation as Dropdown.toggle() does, which will prevent the catch-all handler for clearMenus() from
    // running.
    // NOTE: Updated not to use just `false` for clarity and compatibility with DOM event handlers. See:
    // https://stackoverflow.com/questions/1357118/event-preventdefault-vs-return-false
    e.preventDefault()
    e.stopPropagation()
  }

  // Move the menu just below the button
  // Both callers are in response to  events flowing through `.fr-$componentName-dropdown-button`.
  private def moveMenu(e: dom.Event): Unit = {

    val target           = e.targetT
    val button           = target.closestT(s".fr-$componentName-dropdown-button")
    val buttonContainer  = target.closestT(".dropdown")

    // Move the menu after the button so it doesn't show behind the dialog (not for positioning)
    buttonContainer.parentNode.insertBefore(globalMenuElem, buttonContainer.nextSibling)

    // Position the menu just below the button
    val buttonRect                = button.getBoundingClientRect()
    val offsetParent              = globalMenuElem.offsetParent
    val offsetParentRect          = offsetParent.getBoundingClientRect()
    globalMenuElem.style.position = "absolute"
    globalMenuElem.style.left     = s"${buttonRect.left   - offsetParentRect.left + offsetParent.scrollLeft }px"
    globalMenuElem.style.top      = s"${buttonRect.bottom - offsetParentRect.top  + offsetParent.scrollTop  }px"

    Operation.values foreach { op =>
      val menuItems = globalMenuElem.querySelectorAllT(s".dropdown-menu .fr-${op.entryName}").toList
      menuItems.foreach { item =>
        val canDo       = iteration(e).hasClass(s"can-${op.entryName}")
        val toggleClass = if (canDo) item.classList.remove _ else item.classList.add _
        toggleClass("disabled")
      }
    }

    componentId(e).zip(findIterationForElemWithId(button)) foreach {
      case (currentComponentId, currentIteration) => currentComponentOpt = Some(CurrentComponent(currentComponentId, currentIteration))
    }
  }

  // Handle `keydown` events that arrive at our button and delegate the to the Bootstrap menu button
  private def delegateKeyEventToBootstrapButtonHandler(e: dom.Event): Unit = {

    moveMenu(e)

    val keyboardEvent = e.asInstanceOf[KeyboardEvent]

    // Avoid page scrolling upon cursor keys, in particular cursor down
    if (keyboardEvent.keyCode >= 37 && keyboardEvent.keyCode <= 40)
      e.preventDefault()

    globalMenuElem.querySelectorT(".dropdown-toggle").dispatchEvent(
      new KeyboardEvent(keyboardEvent.`type`, new KeyboardEventInit {
        bubbles    = true
        cancelable = true

        key      = keyboardEvent.key      // "modern" properties
        code     = keyboardEvent.code     // "modern" properties
        charCode = keyboardEvent.charCode // deprecated but seems still needed
        keyCode  = keyboardEvent.keyCode  // deprecated
        ctrlKey  = keyboardEvent.ctrlKey
        shiftKey = keyboardEvent.shiftKey
        altKey   = keyboardEvent.altKey
        metaKey  = keyboardEvent.metaKey
      })
    )
  }

  private def actionHandler(op: Operation, e: dom.Event): Unit = {
    currentComponentOpt foreach {
      case CurrentComponent(currentComponentId, currentIteration) =>
        dispatchActionEvent(op, currentComponentId, currentIteration)
    }
    e.preventDefault()
  }

  private def dispatchActionEvent(op: Operation, currentComponentId: String, currentIteration: Int): Unit =
    AjaxClient.fireEvent(
      AjaxEvent(
        eventName  = s"fr-${op.entryName}",
        targetId   = currentComponentId,
        properties = Map("row" -> currentIteration.toString)
      )
    )

  private object Util {

    def iteration(event: dom.Event): html.Element =
      event.targetT.closestT(s".fr-$componentName-repeat-iteration")

    def findIterationForElemWithId(elemWithId: html.Element): Option[Int] =
      XFormsId.fromEffectiveId(elemWithId.id).iterations.lastOption

    private def componentElem(event: dom.Event): Option[html.Element] =
      event.targetT.closestOpt(s".xbl-fr-$componentName")

    def componentId(event: dom.Event): Option[String] =
      componentElem(event).flatMap(e => Option(e.id))
  }
}
