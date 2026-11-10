package org.orbeon.xbl

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object Range {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Range")

  XFormsXbl.declareCompanion("xf|range", js.constructorOf[RangeCompanion])

  private class RangeCompanion(containerElem: html.Element) extends XBLCompanion {

    companion =>

    private var inputElemOpt: Option[html.Input] = None
    private var currentValue: String             = ""

    private object EventSupport extends EventListenerSupport

    override def init(): Unit = {

      logger.debug("init")

      companion.inputElemOpt = containerElem.queryNestedElems[html.Input]("input[type = range]").headOption

      updateDom(currentValue)
      xformsUpdateReadonly(isMarkedReadonly)

      companion.inputElemOpt.foreach { inputElem =>
        EventSupport.addListener[dom.Event](inputElem, DomEventNames.Input, e => {
          if (XFormsUI.modalProgressPanelShown || isMarkedReadonly) {
            e.preventDefault()
            updateDom(currentValue)
          } else {
            currentValue = inputElem.value
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName   = EventNames.XXFormsValue,
                targetId    = containerElem.id,
                properties  = Map("value" -> currentValue),
                incremental = true
              )
            )
          }
        })
      }
    }

    override def destroy(): Unit = {

      logger.debug("destroy")

      inputElemOpt.foreach { _ =>
        EventSupport.clearAllListeners()
        companion.inputElemOpt = None
      }
    }

    override def xformsGetValue(): String =
      currentValue

    override def xformsUpdateValue(newValue: String): js.UndefOr[Nothing] = {
      currentValue = Option(newValue).flatMap(_.trimAllToOpt).getOrElse("")
      updateDom(currentValue)
      js.undefined
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      inputElemOpt.foreach(_.disabled = readonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug("xformsFocus")
      inputElemOpt.foreach(_.focus())
    }

    private def updateDom(value: String): Unit =
      inputElemOpt.foreach { inputElem =>
        val domValue =
          if (value.nonAllBlank)
            value
          else
            inputElem.attValueOpt("min").flatMap(_.trimAllToOpt).getOrElse("0")
        if (inputElem.value != domValue)
          inputElem.value = domValue
      }
  }
}
