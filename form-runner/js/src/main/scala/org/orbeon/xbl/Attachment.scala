package org.orbeon.xbl

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.KeyboardEvent
import org.orbeon.polyfills.HTMLPolyfills.HTMLElementOps

object Attachment {

  // On click on label, simulate click on the input, so users can press enter or space to select a file
  dom.window.document.addEventListener("keypress", (event: KeyboardEvent) => {
    if (event.key == "Enter" || event.key == " ")
      event.target match {
        case targetElement: html.Element =>
          targetElement.closest(".xbl-fr-attachment, .xbl-fr-attachment-multiple").foreach { attachmentControl =>
            event.preventDefault() // so that the page doesn't scroll
            val inputElement = attachmentControl.querySelector("input").asInstanceOf[html.Input]
            inputElement.click()
          }
        case _ =>
          // Ignore key presses on other objects
      }
  })

}
