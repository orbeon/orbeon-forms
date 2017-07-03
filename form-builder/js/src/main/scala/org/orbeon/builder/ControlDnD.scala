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
import org.scalajs.dom.document
import org.scalajs.dom.html.Element
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js

object ControlDnD {

  var shiftPressed = false

  $(document).on("keyup keydown", {event: JQueryEventObject ⇒
    shiftPressed = event.asInstanceOf[js.Dynamic].shiftKey.asInstanceOf[Boolean]
  })

  val drake = Dragula(
    js.Array(),
    new DragulaOptions {
      override def isContainer(el: Element): Boolean =
        el.classList.contains("fr-grid-td")
      override def moves(el: Element, source: Element, handle: Element, sibling: Element): Boolean =
        handle.classList.contains("fb-control-handle")
      override def accepts(el: Element, target: Element, source: Element, sibling: Element): Boolean = {
        // Can only drop into an empty cell
        ! $(target).is(":has(.fr-grid-content > *)")
      }
      override def mirrorContainer: HTMLElement =
        // Create the mirror inside the first container, so the proper CSS applies to the mirror
        $(".fr-body .fr-grid-td").get(0).asInstanceOf[HTMLElement]
    }
  )

  drake.onDrop((el: Element, target: Element, source: Element, sibling: Element) ⇒ {
    DocumentAPI.dispatchEvent(target.id, "DOMActivate")
    DocumentAPI.dispatchEvent(source.id, "fb-dnd-control-from", properties = new js.Object {
      val `fb-do-copy` = shiftPressed
    })
  })

}
