/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.oxf.fr.process.ProcessSyntaxHighlighter
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.AjaxClient
import org.scalajs.dom
import org.scalajs.dom.html

import scala.collection.mutable
import scala.scalajs.js


object ProcessTextEditor {

  private val WrapperSelector    = ".fr-process-text-editor"
  private val CopyButtonSelector = ".fr-process-text-copy-btn"

  private val backdrops = mutable.HashMap.empty[html.Element, HighlightBackdrop]

  locally {
    AjaxClient.ajaxResponseProcessed.add(_ => syncAll())

    dom.document.addEventListener(
      "click",
      (event: dom.MouseEvent) => {
        val targetOpt = Option(event.target).collect { case el: dom.Element => el }
        targetOpt.flatMap(el => Option(el.closest(CopyButtonSelector))).collect { case btn: html.Element =>
          event.preventDefault()
          event.stopPropagation()
          copyTextFromButton(btn)
        }
      }
    )

    syncAll()
  }

  private def syncAll(): Unit = {
    // Prune detached wrappers
    backdrops.keys.toList.foreach { el =>
      if (! dom.document.contains(el)) {
        backdrops.remove(el).foreach(_.destroy())
      }
    }

    // Attach to any newly visible `.fr-process-text-editor` wrappers and refresh highlights
    dom.document.querySelectorAllT(WrapperSelector).foreach { container =>
      val backdrop = backdrops.getOrElseUpdate(
        container, {
          var b: HighlightBackdrop = null
          b = HighlightBackdrop(
            containerElem = container,
            classPrefix   = "fr-process-text",
            onInput       = () => if (b != null) b.updateHighlights(ProcessSyntaxHighlighter.highlightToHtml)
          )
          b
        }
      )
      backdrop.updateHighlights(ProcessSyntaxHighlighter.highlightToHtml)
    }
  }

  private def copyTextFromButton(btn: html.Element): Unit = {
    val textOpt =
      Option(btn.closest(".fr-process-tab-content"))
        .collect { case el: html.Element => el }
        .flatMap(_.querySelectorOpt(".fr-process-text-editor textarea"))
        .collect { case ta: html.TextArea => ta.value }

    textOpt.foreach { text =>
      val clipboard = dom.window.navigator.asInstanceOf[js.Dynamic].clipboard
      if (! js.isUndefined(clipboard) && clipboard != null) {
        clipboard.writeText(text)
      }
      flashCopySuccess(btn)
    }
  }

  private def flashCopySuccess(btn: html.Element): Unit = {
    btn.classList.add("fr-process-copy-success")
    btn.querySelectorOpt("i").foreach { icon =>
      icon.classList.remove("fa-copy")
      icon.classList.add("fa-check")
    }
    dom.window.setTimeout(
      () => {
        btn.classList.remove("fr-process-copy-success")
        btn.querySelectorOpt("i").foreach { icon =>
          icon.classList.remove("fa-check")
          icon.classList.add("fa-copy")
        }
      },
      1500
    )
  }
}
