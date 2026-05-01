package org.orbeon.xbl

import io.udash.wrappers.jquery.JQueryPromise
import org.orbeon.date.{DateTimeFormat, IsoDateTime, TimezoneFormat}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.web.JSDateUtils
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.facade.XBLCompanion
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.|
import scala.util.chaining.scalaUtilChainingOps


object Timestamp {

  XFormsXbl.declareCompanion("fr|timestamp", js.constructorOf[TimestampCompanion])

  private class TimestampCompanion(containerElem: dom.html.Element) extends XBLCompanion {

    companion =>

    private lazy val outputSpan: dom.html.Element =
      containerElem.querySelectorT("span[data-orbeon-output-format]")

    // The format is immutable for now
    private lazy val dateTimeFormat: DateTimeFormat =
        outputSpan.dataset.get("orbeonOutputFormat")
          .map(IsoDateTime.parseFormat)
          .getOrElse(throw new IllegalStateException("Missing `data-orbeon-output-format` attribute on `span`"))

    // Keep the ISO value so we can return it when asked with `xformsGetValue()`.
    private var isoDateTimeValue: Option[String] = None

    override def xformsGetValue(): String = isoDateTimeValue.getOrElse("") // Q: Return `null` instead of ""?

    override def xformsUpdateValue(newValue: String): js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] =
      outputSpan.textContent =
        newValue.trimAllToOpt
          .tap(isoDateTimeValue = _)
          match {
            case Some(IsoDateTime(isoDateTime)) =>
              formatWithOrbeonApi(isoDateTime, dateTimeFormat)
            case someOrNone =>
              someOrNone.getOrElse("")
          }

    private def formatWithOrbeonApi(isoDateTime: IsoDateTime, dateTimeFormat: DateTimeFormat): String = {

      val jsDate = new js.Date(isoDateTime.toIsoString)

      val offsetMinutes =
        JSDateUtils.findDateOffsetInMinutes(jsDate)
          .getOrElse(throw new IllegalArgumentException(isoDateTime.toIsoString))

      val timeZoneName =
        JSDateUtils.findTimezoneShortName(jsDate)
          .getOrElse(throw new IllegalArgumentException(isoDateTime.toIsoString))

        IsoDateTime.formatDateTime(
          dateTime     = isoDateTime.adjustTo(offsetMinutes),
          format       = dateTimeFormat,
          timezoneName = Some(timeZoneName)
        )
    }

    // Experiment with the JavaScript Intl API, which does not allow us to control the format entirely
    private def formatWithJsApi(isoDateTimeString: String, dateTimeFormat: DateTimeFormat): String = {

      // Try to match the options that make sense
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

      new dom.intl.DateTimeFormat(js.undefined, opts)
        .format(new js.Date(isoDateTimeString))
    }
  }
}