package org.orbeon.xbl

import io.udash.wrappers.jquery.JQueryPromise
import org.orbeon.date.{DateTimeFormat, IsoDateTime, TimezoneFormat}
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.|


object Timestamp {

  XBL.declareCompanion("fr|timestamp", js.constructorOf[TimestampCompanion])

  private class TimestampCompanion(containerElem: dom.html.Element) extends XBLCompanion {

    companion =>

    private lazy val outputSpan: Option[dom.html.Element] =
      containerElem.querySelectorOpt("span[data-orbeon-output-format]")

    override def init(): Unit = ()

    override def xformsGetValue(): String = findNonBlankValue.getOrElse("")

    override def xformsUpdateValue(newValue: String): js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] =
      findFormat.foreach(updateVisibleValue(newValue, _))

    private def findFormat: Option[DateTimeFormat] =
      outputSpan
        .flatMap(_.dataset.get("orbeonOutputFormat"))
        .map(IsoDateTime.parseFormat)

    // Store and retrieve the ISO value or blank in a dataset. This is just so we can return it when asked with
    // `xformsGetValue()`. We could also decide to remove the value when it is blank.
    private def storeValue(isoDateTime: String): Unit =
      outputSpan
        .foreach(_.dataset += "orbeonValue" -> isoDateTime)

    private def findNonBlankValue: Option[String] =
      outputSpan
        .flatMap(_.dataset.get("orbeonValue"))
        .flatMap(_.trimAllToOpt)

    private def updateVisibleValue(isoDateTime: String, dateTimeFormat: DateTimeFormat): Unit =
      isoDateTime.trimAllToOpt match {
        case Some(isoDateTime) =>
          // There is no format/pattern string, so we cannot actually use this to control the entire format. Instead, we
          // use the options we can.
          val opts =
            new dom.intl.DateTimeFormatOptions {
              hour12       = ! dateTimeFormat.timeFormat.is24Hour
              year         = "numeric"
              month        = if (dateTimeFormat.dateFormat.isPadDayMonthDigits) "2-digit" else "numeric"
              day          = if (dateTimeFormat.dateFormat.isPadDayMonthDigits) "2-digit" else "numeric"
              hour         = if (dateTimeFormat.timeFormat.isPadHourDigits)     "2-digit" else "numeric"
              minute       = "2-digit"
              second       = if (dateTimeFormat.timeFormat.hasSeconds)          "2-digit" else js.undefined
              timeZoneName =
                dateTimeFormat.timezoneFormat match {
                  case Some(TimezoneFormat.OffsetWithGmt) => "longOffset"
                  case Some(TimezoneFormat.ShortName)     => "short"
                  case Some(TimezoneFormat.Offset)        => "longOffset"
                  case _                                  => js.undefined
                }
            }

          val formattedDateTime = new dom.intl.DateTimeFormat(js.undefined, opts).format(new js.Date(isoDateTime))
          outputSpan.foreach(_.textContent = formattedDateTime)
          storeValue(isoDateTime)
        case None =>
          outputSpan.foreach(_.textContent = "")
          storeValue("")
      }
  }
}