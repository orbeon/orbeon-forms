package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent}
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js


object DropTrigger {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.DropTrigger")

  val ListenerSuffix   = ".drop-trigger"
  val ListenerSelector = "button[data-orbeon-value], a[data-orbeon-value]"

  XBL.declareCompanion("fr|drop-trigger", js.constructorOf[DropTriggerCompanion])

  private class DropTriggerCompanion extends XBLCompanion {

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
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      $(containerElem).off(s"click.$ListenerSuffix", ListenerSelector)
    }
  }
}
