/**
 * Copyright (C) 2018 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xbl

import io.udash.wrappers.jquery.JQueryPromise
import org.log4s.Logger
import org.orbeon.date.IsoTime
import org.orbeon.date.JSDateUtils.nowAsIsoTime
import org.orbeon.facades.Bowser
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.DomElemOps
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.{Promise, UndefOr, |}


object Time {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Time")

  XBL.declareCompanion("fr|time", js.constructorOf[TimeCompanion])

  private class TimeCompanion(containerElem: html.Element) extends XBLCompanionWithState(containerElem) {

    companion =>

    import Private.*
    import io.circe.generic.auto.*
    import io.circe.{Decoder, Encoder}

    type State = TimeExternalValue

    val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
    val stateDecoder: Decoder[State] = implicitly[Decoder[State]]

    private object EventSupport extends EventListenerSupport

    private var visibleInputElemOpt: Option[html.Input] = None

    override def init(): Unit = {

      logger.debug("init")

      val visibleInputElem = containerElem.querySelector("input").asInstanceOf[html.Input]
      companion.visibleInputElemOpt = Some(visibleInputElem)

      // Add `readonly` attribute on the input if the control is readonly
      val isReadonly = containerElem.classList.contains("xforms-readonly")
      updateReadonly(isReadonly)

      if (isNativePicker) {
        visibleInputElem.`type` = "time"
        EventSupport.addListener(visibleInputElem, DomEventNames.Change, (_: dom.Event) => updateStateAndSendValueToServer(readValue))
      } else {

        EventSupport.addListeners(visibleInputElem, List(DomEventNames.TouchStart, DomEventNames.FocusIn),
          (e: dom.Event) => {
            if (! isMarkedReadonly) {

              logger.debug(s"reacting to event ${e.`type`}")

              stateOpt.foreach { state =>
                // Upon `focusin`, if the format omits seconds, but the value has non-zero seconds, then we want to show
                // the value with seconds, so that the value is not lost.
                if (! state.format.hasSeconds)
                  state.isoOrUnrecognizedValue match {
                    case Left(t @ IsoTime(_, _, Some(s))) if s != 0 =>
                      writeValue(visibleInputElem, IsoTime.formatTime(t, state.format.copy(hasSeconds = true)))
                    case _ =>
                  }
              }
            }
          }
        )

        EventSupport.addListener(visibleInputElem, DomEventNames.FocusOut,
          (e: dom.Event) => {
            if (! isMarkedReadonly) {
              logger.debug(s"reacting to event ${e.`type`}")

              updateStateAndSendValueToServer(readValue)

              // Always update visible value with XForms value:
              //
              // - relying on just value change event from server is not enough
              // - value change is not dispatched if the server value hasn't changed
              // - if the visible changed, but XForms hasn't, we still need to show XForms value
              // - see: https://github.com/orbeon/orbeon-forms/issues/1026
              //
              // So either:
              //
              // - the Ajax response called `updateWithServerValues()` and then `updateVisibleValue()`
              // - or it didn't, and we force `updateVisibleValue()` after the response is processed
              //
              // We also call `updateVisibleValue()` immediately in `updateStateAndSendValueToServer()` if the
              // edit value hasn't changed.
              //
  //            val formId = $(containerElem).parents("form").attr("id").get
              AjaxClient.allEventsProcessedF("time") foreach { _ =>
                stateOpt.foreach(updateVisibleValue)
              }
            }
          }
        )

        EventSupport.addListener(visibleInputElem, DomEventNames.KeyPress,
          (e: dom.KeyboardEvent) => {
            if (! isMarkedReadonly) {

              logger.debug(s"reacting to event ${e.`type`}")

              if (Set(10, 13)(e.keyCode)) {
                e.preventDefault()
                updateStateAndSendValueToServer(readValue)
                AjaxClient.fireEvent(
                  AjaxEvent(
                    eventName = DomEventNames.DOMActivate,
                    targetId  = containerElem.id
                  )
                )
              }
            }
          }
        )
      }
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      EventSupport.clearAllListeners()
      companion.visibleInputElemOpt = None
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      updateReadonly(readonly)
    }

    def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

      val TimeExternalValue(isoValue, format) = newState

      logger.debug(s"updateWithServerValues: `$isoValue`, `$format`")

      if (isNativePicker) {
        if (! previousStateOpt.map(_.stringValue).contains(newState.stringValue)) // test probably not needed as we just set the input value? might avoid focus issues?
          updateVisibleValue(newState)
      } else {

        updateVisibleValue(newState)
      }

      updateReadonly(companion.isMarkedReadonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug(s"xformsFocus")
      companion.visibleInputElemOpt.foreach(_.focus())
    }

    override def setUserValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] =
      updateStateAndSendValueToServer(_ => newValue)

    private object Private {

      val isNativePicker: Boolean = {
        val always = containerElem.querySelectorOpt(":scope > .fr-native-picker-always").isDefined
        val iOS    = Bowser.ios.contains(true)
        always || iOS
      }

      def updateReadonly(readonly: Boolean): Unit =
        visibleInputElemOpt.foreach { visibleInputElem =>
          visibleInputElem.readOnly = readonly
          if (isNativePicker)
            // Also set `disabled` on iOS (see #5376)
            visibleInputElem.disabled = readonly
        }

      def updateStateAndSendValueToServer(read: html.Input => String): Unit =
        visibleInputElemOpt.foreach { visibleInputElem =>
          stateOpt.foreach { state =>

            val visibleInputElemValue = read(visibleInputElem)

            val newState =
              state.copy(
                isoOrUnrecognizedValue =
                  IsoTime.findMagicTimeAsIsoTimeWithNow(visibleInputElemValue, nowAsIsoTime).toLeft(visibleInputElemValue)
              )

            val stateUpdated =
              updateStateAndSendValueToServerIfNeeded(
                newState       = newState,
                valueFromState = _.isoOrUnrecognizedValue,
              )

            if (! stateUpdated)
              updateVisibleValue(newState)
          }
        }

      def updateVisibleValue(state: State): Unit =
        companion.visibleInputElemOpt.foreach { visibleInputElem =>

//          val hasFocus = visibleInputElem eq dom.document.activeElement

//            val newValue =
//              if (hasFocus) state.editValue
//              else          state.displayValue

          writeValue(
            visibleInputElem,
            if (isNativePicker) {
              // The native time picker will clear the field if the date is not an HTML "time string" (ISO-like), but
              // we still set it to whatever string value we have if it's not an ISO date. Alternatively, we could set
              // it to a blank string directly.
              state.isoOrUnrecognizedValue.fold(_.toIsoString, identity)
            } else
              state.stringValue
          )
        }

      def readValue(input: html.Input): String =
        input.value

      def writeValue(input: html.Input, value: String): Unit =
        input.value = value

//      // On mobile, we set the field to `type="number"`, so the format of `value` always uses a `.`
//      // as decimal separator
//      def mobileConversionNeeded(input: html.Input): Boolean =
//        input.`type` == "time"
    }
  }
}
