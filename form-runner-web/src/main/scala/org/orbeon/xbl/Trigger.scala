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

    private var observerOpt: Option[dom.MutationObserver] = None

    private val isMinimal = containerElem.classList.contains("xforms-trigger-appearance-minimal")

    private def buttonOrAnchor: Element =
      containerElem.querySelectorT("button, a")

    override def init(): Unit = {

      logger.debug("init")

      def updateDisplay(shortcut: String, kbd: html.Element): Unit =
        if (isMinimal) {
          val suffix = s"($shortcut)"
          if (! buttonOrAnchor.title.endsWith(suffix))
            buttonOrAnchor.title = s"${buttonOrAnchor.title} $suffix"
        } else
          kbd.innerHTML = shortcut

      observerOpt = Some(
        KeyboardShortcuts.bindShortcutFromKbd(
          buttonOrAnchor = buttonOrAnchor,
          updateDisplay  = updateDisplay
        )
      )
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      observerOpt.foreach(_.disconnect())
    }
  }
}
