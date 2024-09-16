package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, KeyboardShortcuts}
import org.scalajs.dom.ext.*
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js


object DropTrigger {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.DropTrigger")

  val ListenerSuffix   = ".drop-trigger"
  val ListenerSelector = "button[data-orbeon-value], a[data-orbeon-value]"

  XBL.declareCompanion("fr|drop-trigger", js.constructorOf[DropTriggerCompanion])

  private class DropTriggerCompanion(containerElem: html.Element) extends XBLCompanion {

    private var registered: List[String] = Nil

    override def init(): Unit = {

      logger.debug("init")

      $(containerElem).on(s"click.$ListenerSuffix", ListenerSelector, {
        (bound: html.Element, e: JQueryEventObject) => {

          logger.debug(s"reacting to event ${e.`type`}")

          AjaxClient.fireEvent(
            AjaxEvent(
              eventName  = "fr-activate",
              targetId   = containerElem.id,
              properties = Map(
                "fr-value" -> bound.dataset("orbeonValue")
              )
            )
          )

          // Avoid navigation to "#"
          e.preventDefault()
        }
      }: js.ThisFunction)

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
      $(containerElem).off(s"click.$ListenerSuffix", ListenerSelector)
    }
  }
}
