package org.orbeon.xbl

import org.orbeon.facades.{ClipboardJS, ClipboardJSConfig}
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object ClipboardCopy {

  XFormsXbl.declareCompanion("fr|clipboard-copy", js.constructorOf[ClipboardCopyCompanion])

  private class ClipboardCopyCompanion(containerElem: html.Element) extends XBLCompanion {

    private var clipboard: Option[ClipboardJS] = None

    override def init(): Unit = {
      val copyButtonOpt = containerElem.querySelectorOpt(".fr-clipboard-copy-button")

      copyButtonOpt.foreach { button =>
        button.addEventListener("mousedown", (event: dom.MouseEvent) => event.preventDefault())
      }

      val cb = new ClipboardJS(
        s"#${containerElem.id} .fr-clipboard-copy-button", // ensures one instance of `ClipboardJS` for this instance
        new ClipboardJSConfig {
          override val target = js.defined {
            (_: html.Element) =>
              containerElem.querySelector(".fr-clipboard-copy-wrapper output, .fr-clipboard-copy-wrapper input, .fr-clipboard-copy-wrapper textarea, .fr-clipboard-copy-wrapper .xforms-output-output")
          }
        }
      )

      cb.on("success", (_: js.Dynamic) => {
        copyButtonOpt.foreach(flashCopySuccess)
      })

      cb.on("error", (_: js.Dynamic) => {
        copyButtonOpt.foreach(flashCopyError)
      })

      clipboard = Some(cb)
    }

    private def flashCopySuccess(button: html.Element): Unit = {
      button.classList.add("fr-clipboard-copy-success")
      val iconElemOpt = Option(button.querySelector("i"))
      iconElemOpt.foreach(_.setAttribute("class", "fa fa-fw fa-check"))
      dom.window.setTimeout(() => {
        button.classList.remove("fr-clipboard-copy-success")
        iconElemOpt.foreach(_.setAttribute("class", "fa fa-fw fa-copy"))
      }, 1500)
    }

    private def flashCopyError(button: html.Element): Unit = {
      button.classList.add("fr-clipboard-copy-error")
      dom.window.setTimeout(() => {
        button.classList.remove("fr-clipboard-copy-error")
      }, 1000)
    }

    override def destroy(): Unit =
      clipboard.foreach(_.destroy())
  }
}
