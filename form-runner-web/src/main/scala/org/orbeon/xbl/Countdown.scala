package org.orbeon.xbl

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, XFormsXbl}
import org.scalajs.dom.html

import scala.collection.mutable
import scala.scalajs.js


object Countdown {

  XFormsXbl.declareCompanion("fr|countdown", js.constructorOf[CountdownCompanion])

  private val companions = mutable.Set[CountdownCompanion]()

  private var lastTimestamp = js.Date.now()

  js.timers.setInterval(100) {
    val newTimestamp = js.Date.now()
    val increment = Math.floor((newTimestamp - lastTimestamp) / 1000).toInt
    if (increment > 0) {
      lastTimestamp = newTimestamp
      // Create a copy to avoid ConcurrentModificationException if destroy is called during iteration
      companions.toList.foreach(_.tick(increment))
    }
  }

  private def parseDuration(text: String): Option[Int] = {
    val minSec = text.split(":")
    if (minSec.length == 1) {
      minSec(0).toIntOption
    } else if (minSec.length == 2) {
      for {
        min <- minSec(0).toIntOption
        sec <- minSec(1).toIntOption
      } yield min * 60 + sec
    } else {
      None
    }
  }

  private def serializeDuration(secTotal: Int): String = {
    val min = Math.floor(secTotal / 60).toInt
    val sec = secTotal % 60
    val secPart = if (sec < 10) s"0$sec" else s"$sec"
    s"$min:$secPart"
  }

  private class CountdownCompanion(containerElem: html.Element) extends XBLCompanion {

    private var outputElOpt: Option[html.Element] = None
    private var alertThreshold: Int = 0

    override def init(): Unit = {
      outputElOpt = containerElem.querySelectorOpt(".xforms-output-output")
      companions += this
    }

    override def destroy(): Unit =
      companions -= this

    def setAlertThreshold(threshold: String): Unit =
      alertThreshold = threshold.toIntOption.getOrElse(0) * 60

    def durationChanged(newValue: String): Unit = {
      outputElOpt.foreach(_.innerText = newValue)
      val sec = parseDuration(newValue).getOrElse(0)
      if (sec > alertThreshold)
        outputElOpt.foreach(_.classList.remove("fr-countdown-alert"))
    }

    def tick(increment: Int): Unit =
      for {
        outputEl <- outputElOpt
        sec      <- parseDuration(outputEl.innerText)
      } locally {
        val newSec = sec - increment
        if (newSec <= alertThreshold && !outputEl.classList.contains("fr-countdown-alert")) {
          outputEl.classList.add("fr-countdown-alert")
          AjaxClient.fireEvent(AjaxEvent("fr-countdown-alert", containerElem.id))
        }
        if (newSec >= 0)
          outputEl.innerText = serializeDuration(newSec)
        if (newSec == 0) {
          AjaxClient.fireEvent(AjaxEvent("fr-countdown-ended", containerElem.id))
        }
      }
  }
}
