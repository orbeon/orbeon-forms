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
import io.udash.wrappers.jquery.JQueryEvent
import org.orbeon.oxf.util.CoreUtils.asUnit
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.Utils
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent}
import org.scalajs.dom
import org.scalajs.dom.{KeyboardEvent, document, html}

import scala.scalajs.js

trait GridSectionMenus {

  import Util.*

  def componentName: String

  // Keep pointing to menu so we can move it around as needed
  // Old comment: NOTE: When scripts are in the head, this returns undefined. Should be fixed!
  private val globalMenuElem: html.Element = document.querySelectorT(s".fr-$componentName-dropdown-menu")

  sealed trait Operation extends EnumEntry with Hyphencase
  object Operation extends Enum[Operation] {
    val values = findValues
    case object MoveUp      extends Operation
    case object MoveDown    extends Operation
    case object InsertAbove extends Operation
    case object InsertBelow extends Operation
    case object Clear       extends Operation
    case object Remove      extends Operation
  }

  case class CurrentComponent(currentComponentId: String, currentIteration: Int)

  // This transiently holds information between a click on the menu and a subsequent action in the menu.
  // Not nice but we don't have FRP at this point.
  private var currentComponentOpt: Option[CurrentComponent] = None

  // Initialization
  if (globalMenuElem != null) {
    // Click on our own button moves and shows the menu
    document.addEventListener("keydown", (e: dom.Event) =>
      if (e.targetT.matches(s".fr-$componentName-dropdown-button"))
        delegateKeyEventToBootstrapButtonHandler(e)
    )

    document.addEventListener("click", (e: dom.Event) => {

      val target  = e.targetT
      if (target.closestOpt(s".fr-$componentName-dropdown-button").nonEmpty) moveAndShowMenuHandler(e)
      if (target.matches   (s".fr-$componentName-remove-button")           ) removeIterationHandler(e)

      // Menu actions
      Operation.values foreach { op =>
        val liOpOpt = Option(target.closest(s".fr-$componentName-dropdown-menu .fr-${op.entryName}"))
        liOpOpt.foreach(_ => actionHandler(op, e))
      }
    })
  }

  private def removeIterationHandler(e: dom.Event): Unit = {

    val jTarget = $(e.target)

    val jButton = jTarget.closest(s".fr-$componentName-remove-button")
    val button  = jButton.get(0).get.asInstanceOf[html.Element]

    componentId(e).zip(findIterationForElemWithId(button)) foreach {
      case (currentComponentId, currentIteration) =>
        dispatchActionEvent(Operation.Remove, currentComponentId, currentIteration)
    }
  }

  private def moveAndShowMenuHandler(e: dom.Event): Unit = {

    moveMenu(e)

    // NOTE: Don't use dropdown("toggle") as that registers a new handler further down the DOM!
    $(globalMenuElem).find(".dropdown-toggle").trigger("click")

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

    val target    = e.targetT
    val button    = target.closestT(s".fr-$componentName-dropdown-button")
    val dropdown  = target.closestT(".dropdown")

    // Move globalMenuElem in the DOM just below the button (for positioning, dialog)
    dropdown.parentNode.insertBefore(globalMenuElem, dropdown.nextSibling)

    globalMenuElem.style.position = "absolute"

    Operation.values foreach { op =>
      val menuItems = globalMenuElem.querySelectorAllT(s".dropdown-menu .fr-${op.entryName}").toList
      menuItems.foreach { item =>
        val canDo       = iteration(e).classList.contains(s"can-${op.entryName}")
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

    $(globalMenuElem).find(".dropdown-toggle").trigger(
      $.asInstanceOf[js.Dynamic].Event( // `Event` constructor is not present in the jQuery facade
        keyboardEvent.`type`,
        new js.Object {
          val charCode = keyboardEvent.charCode

          // Putting these to be complete, but `charCode` above does the trick for the menu
          val keyCode  = keyboardEvent.keyCode
          val which    = keyboardEvent.asInstanceOf[js.Dynamic].which
          val ctrlKey  = keyboardEvent.ctrlKey
          val shiftKey = keyboardEvent.shiftKey
          val altKey   = keyboardEvent.altKey
          val metaKey  = keyboardEvent.metaKey
        }
      ).asInstanceOf[JQueryEvent]
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

    def iteration(e: dom.Event): html.Element =
      $(e.target).closest(s".fr-$componentName-repeat-iteration").get(0).get.asInstanceOf[html.Element]

    def findIterationForElemWithId(elemWithId: html.Element): Option[Int] =
      $(elemWithId).attr("id") map Utils.getRepeatIndexes flatMap (_.lastOption) map (_.toInt)

    def componentElem(e: dom.Event): js.UndefOr[html.Element] =
      $(e.target).closest(s".xbl-fr-$componentName").get(0).get.asInstanceOf[js.UndefOr[html.Element]]

    def componentId(e: dom.Event): Option[String] =
      $(componentElem(e)).attr("id")
  }
}
