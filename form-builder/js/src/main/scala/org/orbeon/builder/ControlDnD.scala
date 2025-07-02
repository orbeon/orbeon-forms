/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.builder

import autowire.*
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.facades.{Dragula, DragulaOptions}
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.rpc.RpcClient
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.UndefOr


private object ControlDnD {

  locally {
    val CopyClass = "fb-dnd-copy"
    val MoveClass = "fb-dnd-move"

    var shiftPressed = false
    def updateShiftPressed(event: dom.KeyboardEvent): Unit = {
      // Surprisingly, `event.shiftKey` can be `undefined`
      val shiftKeyOpt = event.shiftKey.asInstanceOf[js.UndefOr[Boolean]]
      shiftPressed    = shiftKeyOpt.getOrElse(false)
    }
    dom.document.addEventListener("keyup"  , updateShiftPressed _)
    dom.document.addEventListener("keydown", updateShiftPressed _)

    val controlMoverCopier = Dragula(
      js.Array(),
      new DragulaOptions {

        override def isContainer(el: html.Element) =
          el.classList.contains("fr-grid-td")

        override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element) =
          handle.classList.contains("fb-control-handle")

        // Can only drop into an empty cell
        override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) =
          $(target)
            .find("> :not(.gu-mirror, .gu-transit, .fb-control-editor-left)")
            .length == 0

        override def copy(el: html.Element, source: html.Element) = {
          val cursorClass = if (shiftPressed) CopyClass else MoveClass
          $(el).addClass(cursorClass)
          shiftPressed
        }
      }
    )

    controlMoverCopier.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) => {
      // It seems Dragula calls `onDrop` even if the target doesn't accept a drop, but in that case `target` is `null`
      if (target ne null)
        RpcClient[FormBuilderRpcApi].controlDnD(el.id, target.id, el.classList.contains(CopyClass)).call()
    })

    val controlInserter = Dragula(
      js.Array(),
      new DragulaOptions {

        override def isContainer(el: html.Element) =
          (
            (el ne null)                                                 &&
            el.parentElementOpt.exists(_.classList.contains("fb-tools")) &&
            el.classList.contains("xforms-group")
          ) || el.classList.contains("fr-grid-td")

        override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element) =
            (handle ne null)                                   &&
            handle.closestOpt(".btn").nonEmpty                 &&
            handle.closestOpt(".fb-insert-control").nonEmpty

        // Can only drop into an empty cell
        override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) =
          $(target)
            .find("> :not(.gu-mirror, .gu-transit, .fb-control-editor-left)")
            .length == 0

        override def copy(el: html.Element, source: html.Element) = {
          $(el).addClass(CopyClass)
          true
        }
      }
    )

    // Dragula creates the mirror under the body; move it under the same parent as the original so our CSS applies
    List(controlMoverCopier, controlInserter).foreach {
      _.onCloned { (clone, original, cloneType) =>
        if (cloneType == "mirror")
          original.parentElement.appendChild(clone)
      }
    }

    controlInserter.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) => {
      // It seems Dragula calls `onDrop` even if the target doesn't accept a drop, but in that case `target` is `null`
      if (target ne null) {

        // Don't leave toolbox button inside the cell
        target.removeChild(el)

        // Activate target cell
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName = DomEventNames.DOMActivate,
            targetId  = target.id
          )
        )

        // Insert the control into the current cell as if by clicking the toolbox button
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName = DomEventNames.DOMActivate,
            targetId  = el.querySelector(".xforms-trigger").id
          )
        )
      }
    })
  }
}
