package org.orbeon.xbl

import org.orbeon.facades.{ClipboardJS, ClipboardJSConfig}
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom.html

import scala.scalajs.js


object ClipboardCopy {

  XBL.declareCompanion("fr|clipboard-copy", js.constructorOf[ClipboardCopyCompanion])

  private class ClipboardCopyCompanion(containerElem: html.Element) extends XBLCompanion {

    private var clipboard: Option[ClipboardJS] = None

    override def init(): Unit =
      clipboard = Some(
        new ClipboardJS(
          s"#${containerElem.id} .fr-clipboard-copy-button", // ensures one instance of `ClipboardJS` for this instance
          new ClipboardJSConfig {
            override val target = js.defined {
              (_: html.Element) =>
                containerElem.querySelector(".fr-clipboard-copy-wrapper output, .fr-clipboard-copy-wrapper input, .fr-clipboard-copy-wrapper textarea")
            }
          }
        )
      )

    override def destroy(): Unit =
      clipboard.foreach(_.destroy())
  }
}
