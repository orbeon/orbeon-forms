package org.orbeon.xbl

import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent}
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js


object DropTrigger {

  val ListenerSuffix   = ".drop-trigger"
  val ListenerSelector = "button[data-orbeon-value], a[data-orbeon-value]"

  XBL.declareCompanion("fr|drop-trigger",
    new XBLCompanion {

      override def init(): Unit = {

        scribe.debug("init")

        $(containerElem).on(s"click.$ListenerSuffix", ListenerSelector, {
          (bound: html.Element, e: JQueryEventObject) => {

            scribe.debug(s"reacting to event ${e.`type`}")

            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = "fr-activate",
                targetId   = containerElem.id,
                properties = Map(
                  "fr-value" -> bound.dataset("orbeonValue")
                )
              )
            )
          }
        }: js.ThisFunction)
      }

      override def destroy(): Unit = {
        scribe.debug("destroy")
        $(containerElem).off(s"click.$ListenerSuffix", ListenerSelector)
      }
    }
  )
}
