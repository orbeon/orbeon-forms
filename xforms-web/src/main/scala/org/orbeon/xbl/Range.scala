package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventListenerSupport, XFormsUI}
import org.scalajs.dom.{html, raw}

import scala.scalajs.js


object Range {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Range")

  XBL.declareCompanion("xf|range", js.constructorOf[RangeCompanion])

  private class RangeCompanion(containerElem: html.Element) extends XBLCompanion {

    companion =>

    var inputElemOpt: Option[html.Input] = None
    object EventSupport extends EventListenerSupport

    override def init(): Unit = {

      logger.debug("init")

      val inputElem = containerElem.querySelector("input[type = range]").asInstanceOf[html.Input]
      companion.inputElemOpt = Some(inputElem)

      xformsUpdateReadonly(containerElem.classList.contains("xforms-readonly"))

      EventSupport.addListener[raw.Event](inputElem, DomEventNames.Input, e => {
        if (XFormsUI.modalProgressPanelShown) {
           e.preventDefault()
        } else {
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName   = "xxforms-value",
              targetId    = containerElem.id,
              properties  = Map("value" -> xformsGetValue()),
              incremental = true
            )
          )
        }
      })
    }

    override def destroy(): Unit = {

      logger.debug("destroy")

      inputElemOpt foreach { _ =>
        EventSupport.clearAllListeners()
        companion.inputElemOpt = None
      }
    }

    override def xformsGetValue(): String =
      inputElemOpt map (_.value) getOrElse "" // CHECK: blank? null? undefined?

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      inputElemOpt foreach (_.value = newValue)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      inputElemOpt foreach (_.readOnly = readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug(s"xformsFocus")
      inputElemOpt foreach (_.focus())
    }
  }
}
