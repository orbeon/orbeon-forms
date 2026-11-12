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

import org.orbeon.facades.{Dragula, DragulaOptions, Drake}
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventNames, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.UndefOr


object Grid {

  private val IsNotGuSelector            = ":not(.gu-mirror):not(.gu-transit)"
  private val IsIterationSelector        = ".fr-grid-repeat-iteration:not(.gu-mirror):not(.xforms-disabled)"
  private val IsMovableIterationSelector = ".fr-grid-repeat-iteration:not(.gu-mirror):not(.xforms-disabled):is(.can-move-up, .can-move-down)"
  private val IsRepeatBeginEndSelector   = ".xforms-repeat-begin-end"

  XFormsXbl.declareCompanion("fr|grid-multiple", js.constructorOf[GridCompanion])

  private class GridCompanion(containerElem: html.Element) extends XBLCompanion {

    private case class DragState(
      currentDragStartPrev     : dom.Element,
      currentDragStartPosition : Int
    )

    private var dragState : Option[DragState] = None
    private var drake     : Option[Drake]     = None

    override def init(): Unit = {
      GridMenus // initialize menus once

      containerElem.querySelectorOpt(":scope > .fr-grid.fr-repeat").foreach { repeatGridElem =>
        val newDrake =
          Dragula(
            js.Array(),
            new DragulaOptions {

              override val mirrorContainer: UndefOr[html.Element] = repeatGridElem

              override def isContainer(el: html.Element): UndefOr[Boolean] =
                el eq repeatGridElem

              override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): UndefOr[Boolean] =
                el.matches(IsMovableIterationSelector) &&
                  (handle ne null) &&
                  handle.closestOpt(".fr-grid-dropdown-menu").isEmpty &&
                  handle
                    .closestOpt(".fr-repeat-column-left")
                    .exists(_.closestOpt(".fr-grid-repeat-iteration").contains(el))

              override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): UndefOr[Boolean] =
                (sibling ne null) &&
                  sibling.matches(IsNotGuSelector) && (
                    sibling.matches(IsMovableIterationSelector) || (
                      ! sibling.matches(IsIterationSelector) &&
                        sibling.previousElementSiblings(IsIterationSelector).nextOption().exists(_.matches(IsMovableIterationSelector))
                    )
                  )
            }
          )

        newDrake.onDrag((el: html.Element, source: html.Element) => {
          GridMenus.onDragStart()
          dragState = Some(
            DragState(
              currentDragStartPrev     = el.previousElementSiblingT,
              currentDragStartPosition = el.previousElementSiblings(IsIterationSelector).length
            )
          )
        })

        newDrake.onDragend((el: html.Element) => {
          dragState = None
          GridMenus.onDragEnd()
        })

        newDrake.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) => {
          dragState.foreach { state =>
            val dndEnd   = el.previousElementSiblings(IsIterationSelector).length
            val repeatId =
              el.previousElementSiblings(IsRepeatBeginEndSelector)
                .nextOption()
                .map(_.id.substring("repeat-begin-".length))
                .getOrElse(throw new IllegalStateException)

            val beforeEl = state.currentDragStartPrev
            val dndStart = state.currentDragStartPosition

            if (dndStart == dndEnd) {
              beforeEl.insertAdjacentElement("afterend", el)
            } else {
              AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("grid-dnd") foreach { _ =>
                beforeEl.insertAdjacentElement("afterend", el)
              }

              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName  = EventNames.XXFormsDnD,
                  targetId   = repeatId,
                  form       = Some(getXFormsFormOrThrow.elem),
                  properties = Map(
                    "dnd-start" -> (dndStart + 1),
                    "dnd-end"   -> (dndEnd + 1)
                  )
                )
              )
            }
          }
        })

        drake = Some(newDrake)
      }
    }

    override def destroy(): Unit = {
      drake.foreach(_.destroy())
      drake     = None
      dragState = None
    }
  }
}

object GridMenus extends GridSectionMenus {
  override def componentName = "grid"
}