package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{AjaxClient, AjaxEvent, KeyboardShortcuts}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps


object DropTrigger {

  XBL.declareCompanion("fr|drop-trigger", js.constructorOf[DropTriggerCompanion])

  private val logger: Logger   = LoggerFactory.createLogger("org.orbeon.xbl.DropTrigger")
  private val ListenerSelector = "button[data-orbeon-value], a[data-orbeon-value]"
  private class DropTriggerCompanion(containerElem: html.Element) extends XBLCompanion {

    private var registered: List[String] = Nil

    private val onActivate: dom.Event => Unit = (e: dom.Event) =>
      e.target.asInstanceOf[html.Element]
        .closest(ListenerSelector)
        .asInstanceOf[html.Element]
        .pipe(Option(_))
        .foreach { buttonOrA =>
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName  = "fr-activate",
              targetId   = containerElem.id,
              properties = Map(
                "fr-value" -> buttonOrA.dataset("orbeonValue")
              )
            )
          )
          // Avoid navigation to "#"
          e.preventDefault()
        }

    override def init(): Unit = {
      logger.debug("init")
      containerElem.addEventListener("click", onActivate)
      registered =
        containerElem.querySelectorAllT("kbd[data-orbeon-keyboard-shortcut]")
          .flatMap { kbd =>
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
