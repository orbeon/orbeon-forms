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

import org.orbeon.jquery.Offset
import org.orbeon.xforms.facade.Utils
import org.orbeon.xforms.{$, DocumentAPI}
import org.scalajs.dom.{document, html}
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js

object Grid {

  // Keep pointing to menu so we can move it around as needed
  // Old comment: NOTE: When scripts are in the head, this returns undefined. Should be fixed!
  val globalMenuElem: js.UndefOr[html.Element] = $(".fr-grid-dropdown-menu")(0)

  var OpNames = List("move-up", "move-down", "insert-above", "insert-below", "remove")

  // Not very nice: global context for actionFunction.
  var currentGridIdOpt: Option[String]        = None
  var currentGridIterationOpt: Option[String] = None

  // Initialization
  globalMenuElem foreach { _ ⇒
    // Click on our own button moves and shows the menu
    $(document).on("click.orbeon.grid",   ".fr-grid-dropdown-button", moveAndShowMenu _)
    $(document).on("keydown.orbeon.grid", ".fr-grid-dropdown-button", delegateKeyEventToBootstrapButton _)

    // Listeners for all menu actions
    OpNames foreach { opName ⇒
      $(document).on("click.orbeon.grid", s".fr-grid-dropdown-menu .fr-$opName", actionFunction(opName))
    }
  }

  def moveAndShowMenu(e: JQueryEventObject): js.Any = {

        moveMenu(e)

        // NOTE: Don"t use dropdown("toggle") as that registers a new handler further down the DOM!
        $(globalMenuElem).find(".dropdown-toggle").trigger("click")

        // Prevent "propagation". In fact, with jQuery, "delegated" handlers are handled first, and if a delegated
        // event calls stopPropagation(), then "directly-bound" handlers are not called. Yeah. So here, we prevent
        // propagation as Dropdown.toggle() does, which will prevent the catch-all handler for clearMenus() from
        // running.
        false
    }

    // Move the menu just below the button
    def moveMenu(e: JQueryEventObject): Boolean = {
      val dropdown = $(e.target).closest(".dropdown")

      val dropdownOffset = Offset(dropdown.offset())

      $(globalMenuElem).css("position", "absolute")
      Offset.offset($(globalMenuElem), Offset(dropdownOffset.left, dropdownOffset.top + dropdown.height()))

      val iteration = $(gridIteration(e))

      OpNames foreach { opName ⇒
        $(globalMenuElem).find(".dropdown-menu").children(s".fr-$opName").toggleClass("disabled", ! iteration.is(s".can-$opName"))
      }

      currentGridIdOpt = gridId(e)
      currentGridIterationOpt = findGridIterationsForElemWithId(e.target.asInstanceOf[html.Element])

      // Prevent "propagation". In fact, with jQuery, "delegated" handlers are handled first, and if a delegated
      // event calls stopPropagation(), then "directly-bound" handlers are not called. Yeah. So here, we prevent
      // propagation as Dropdown.toggle() does, which will prevent the catch-all handler for clearMenus() from
      // running.
      false
    }

    // Handle keyboard events that arrive on our button and delegate the to the Bootstrap menu button
    def delegateKeyEventToBootstrapButton(e: JQueryEventObject): js.Any = {
      moveMenu(e)
      println(s"xxx delegate: ${e.`type`}, ${e.asInstanceOf[js.Dynamic].keyCode}")
      $(globalMenuElem).find(".dropdown-toggle").trigger(new js.Object {
        val `type`  = e.`type`
        val keyCode = e.asInstanceOf[js.Dynamic].keyCode
      }.asInstanceOf[JQueryEventObject]) // NOTE: Type appears in correct, but this seems to work.
    }

    def gridIteration(e: JQueryEventObject): js.UndefOr[html.Element] =
      $(e.target).closest(".fr-grid-repeat-iteration")(0)

    def findGridIterationsForElemWithId(elemWithId: html.Element): Option[String] =
      $(elemWithId).attr("id").toOption map Utils.getRepeatIndexes flatMap (_.lastOption)

    def grid(e: JQueryEventObject): js.UndefOr[html.Element] =
        $(e.target).closest(".xbl-fr-grid")(0)

    def gridId(e: JQueryEventObject): Option[String] =
        $(grid(e)).attr("id").toOption

    def actionFunction(forEventName: String): JQueryEventObject ⇒ js.Any = e ⇒ {

      currentGridIdOpt.zip(currentGridIterationOpt) foreach {
        case (currentGridId, currentGridIteration) ⇒
          DocumentAPI.dispatchEvent(
            targetId   = currentGridId,
            eventName  = s"fr-$forEventName",
            properties = js.Dictionary("row" → currentGridIteration)
          )
      }
      e.preventDefault()
      true
  }

}
