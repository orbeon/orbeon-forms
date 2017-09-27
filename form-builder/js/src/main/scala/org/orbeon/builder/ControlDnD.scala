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

import org.orbeon.xbl.{Dragula, DragulaOptions}
import org.orbeon.xforms.{$, DocumentAPI}
import org.scalajs.dom.{document, html}
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js
import scala.scalajs.js.Dictionary

object ControlDnD {

  private val CopyClass = "fb-dnd-copy"
  private val MoveClass = "fb-dnd-move"
  private var shiftPressed = false

  $(document).on("keyup keydown", {event: JQueryEventObject ⇒
    shiftPressed = event.asInstanceOf[js.Dynamic].shiftKey.asInstanceOf[Boolean]
  })

  val drake = Dragula(
    js.Array(),
    new DragulaOptions {
      override def isContainer(el: html.Element): Boolean = {
        el.classList.contains("fr-grid-td")
      }
      override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): Boolean = {
        handle.classList.contains("fb-control-handle")
      }
      override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): Boolean = {
        // Can only drop into an empty cell
        $(target)
          .find("> .fb-hover:not(.gu-mirror, .gu-transit) > *, > *")
          .length == 0
      }
      override def mirrorContainer: html.Element =
        // Create the mirror inside the first container, so the proper CSS applies to the mirror
        $(".fr-body .fr-grid-td").get(0).asInstanceOf[html.Element]
      override def copy(el: html.Element, source: html.Element): Boolean = {
        val cursorClass = if (shiftPressed) CopyClass else MoveClass
        $(el).addClass(cursorClass)
        shiftPressed
      }
    }
  )

  drake.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) ⇒ {
    // It seems Dragula calls `onDrop` even if the target doesn't accept a drop, but in that case `target` is `null`
    if (target != null) {
      DocumentAPI.dispatchEvent(target.id, "DOMActivate")
      DocumentAPI.dispatchEvent(source.id, "fb-dnd-control-from",
        properties = js.Dictionary(
          "fb-do-copy" → $(el).hasClass(CopyClass).toString
        )
      )
    }
  })

}
