package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.KeyboardShortcuts
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.js


object Trigger {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Trigger")

  XBL.declareCompanion(s"fr|trigger", js.constructorOf[TriggerCompanion])

  private class TriggerCompanion(containerElem: html.Element) extends XBLCompanion {

    private var registered: Option[String] = None

    private val isMinimal = containerElem.classList.contains("xforms-trigger-appearance-minimal")

    private def buttonElem: Element =
      containerElem.querySelectorT("button")

    override def init(): Unit = {

      logger.debug("init")

      def updateDisplay(shortcut: String, kbd: html.Element): Unit =
        if (isMinimal) {
          val suffix = s"($shortcut)"
          if (! buttonElem.title.endsWith(suffix))
            buttonElem.title = s"${buttonElem.title} $suffix"
        } else
          kbd.innerHTML = shortcut

      containerElem.querySelectorOpt("kbd[data-orbeon-keyboard-shortcut]")
        .foreach { kbd =>
          registered = KeyboardShortcuts.bindShortcutFromKbd(
            clickElem     = kbd,
            rawShortcut   = kbd.dataset("orbeonKeyboardShortcut"),
            updateDisplay = updateDisplay(_, kbd),
            condition     = kbd.dataset.get("orbeonKeyboardShortcutCondition")
                              .contains("clipboard-empty").option(() => dom.window.getSelection().toString.isEmpty)
          )
        }
      }

      override def destroy(): Unit = {
        logger.debug("destroy")
        registered.foreach(KeyboardShortcuts.unbindShortcutFromKbd)
      }
  }
}
