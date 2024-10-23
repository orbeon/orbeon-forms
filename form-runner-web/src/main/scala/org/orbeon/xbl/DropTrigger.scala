package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, KeyboardShortcuts}
import org.scalajs.dom.ext.*
import org.scalajs.dom.html
import io.udash.wrappers.jquery.JQueryEvent
import org.scalajs.dom

import scala.scalajs.js


object DropTrigger {

  XBL.declareCompanion("fr|drop-trigger", js.constructorOf[DropTriggerCompanion])

  private val logger: Logger   = LoggerFactory.createLogger("org.orbeon.xbl.DropTrigger")
  private val ListenerSelector = "button[data-orbeon-value], a[data-orbeon-value]"
  private class DropTriggerCompanion(containerElem: html.Element) extends XBLCompanion {

    private var registered: List[String] = Nil

    private val onActivate: dom.Event => Unit = (e: dom.Event) => {
      if (e.target.asInstanceOf[html.Element].matches(ListenerSelector)) {

        logger.debug(s"reacting to event ${e.`type`}")

        AjaxClient.fireEvent(
          AjaxEvent(
            eventName  = "fr-activate",
            targetId   = containerElem.id,
            properties = Map(
              "fr-value" -> e.currentTarget.asInstanceOf[html.Element].dataset("orbeonValue")
            )
          )
        )

        // Avoid navigation to "#"
        e.preventDefault()
      }
    }

    override def init(): Unit = {
      logger.debug("init")
      containerElem.addEventListener("click", onActivate)
      registered =
        containerElem.querySelectorAll("kbd[data-orbeon-keyboard-shortcut]")
          .flatMap { e =>
            val kbd = e.asInstanceOf[html.Element]
            KeyboardShortcuts.bindShortcutFromKbd(
              clickElem     = kbd,
              rawShortcut   = kbd.dataset("orbeonKeyboardShortcut"),
              updateDisplay = shortcut => kbd.innerHTML = shortcut
            )
          }
          .toList
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      registered.foreach(KeyboardShortcuts.unbindShortcutFromKbd)
      containerElem.removeEventListener("click", onActivate)
    }
  }
}
