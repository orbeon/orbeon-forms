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

import org.log4s.Logger
import org.orbeon.date.IsoTime
import org.orbeon.date.IsoTime._
import org.orbeon.date.JSDateUtils.nowAsIsoTime
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.xforms._
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.scalajs.js


object Time {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Time")
  private val ListenerSuffix = ".time"

  XBL.declareCompanion("fr|time", js.constructorOf[TimeCompanion])

  private class TimeCompanion(containerElem: html.Element) extends XBLCompanionWithState(containerElem) {

    companion =>

    import Private._
    import io.circe.generic.auto._
    import io.circe.{Decoder, Encoder}

    type State = TimeExternalValue

    val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
    val stateDecoder: Decoder[State] = implicitly[Decoder[State]]

    var visibleInputElemOpt: Option[html.Input] = None

    override def init(): Unit = {

      logger.debug("init")

      val visibleInputElem = containerElem.querySelector("input").asInstanceOf[html.Input]
      companion.visibleInputElemOpt = Some(visibleInputElem)

      $(visibleInputElem).on(s"${DomEventNames.TouchStart}$ListenerSuffix ${DomEventNames.FocusIn}$ListenerSuffix", {
        (_: html.Element, e: JQueryEventObject) => {
          if (! isMarkedReadonly) {

            logger.debug(s"reacting to event ${e.`type`}")

            stateOpt foreach { state =>
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
      }: js.ThisFunction)

      $(visibleInputElem).on(s"${DomEventNames.FocusOut}$ListenerSuffix", {
        (_: html.Element, e: JQueryEventObject) => {
          if (! isMarkedReadonly) {
            logger.debug(s"reacting to event ${e.`type`}")

            updateStateAndSendValueToServer()

            // Always update visible value with XForms value:
            //
            // - relying just value change event from server is not enough
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
              stateOpt foreach updateVisibleValue
            }
          }
        }
      }: js.ThisFunction)

      $(visibleInputElem).on(s"${DomEventNames.KeyPress}$ListenerSuffix", {
        (_: html.Element, e: JQueryEventObject) => {
          if (! isMarkedReadonly) {

            logger.debug(s"reacting to event ${e.`type`}")

            if (Set(10, 13)(e.which)) {
              e.preventDefault()
              updateStateAndSendValueToServer()
              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName = DomEventNames.DOMActivate,
                  targetId  = containerElem.id
                )
              )
            }
          }
        }
      }: js.ThisFunction)
    }

    override def destroy(): Unit = {

      logger.debug("destroy")

      visibleInputElemOpt foreach { visibleInputElem =>
        $(visibleInputElem).off()
        companion.visibleInputElemOpt = None
      }
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      updateReadonly(readonly)
    }

    def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

      val TimeExternalValue(isoValue, format) = newState

      logger.debug(s"updateWithServerValues: `$isoValue`, `$format`")

      updateVisibleValue(newState)

      // Also update disabled because this might be called upon an iteration being moved, in which
      // case all the control properties must be updated.
      updateReadonly(companion.isMarkedReadonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug(s"xformsFocus")
      companion.visibleInputElemOpt foreach (_.focus())
    }

    private object Private {

      def updateReadonly(readonly: Boolean): Unit =
        visibleInputElemOpt foreach { visibleInputElem =>
          if (readonly)
            visibleInputElem.setAttribute("readonly", "readonly")
          else
            visibleInputElem.removeAttribute("readonly")
        }

      def updateStateAndSendValueToServer(): Unit =
        visibleInputElemOpt foreach { visibleInputElem =>
          logger.debug(s"xxx $stateOpt")
          stateOpt foreach { state =>

            val visibleInputElemValue = readValue(visibleInputElem)

            println(s"xxx visibleInputElemValue $visibleInputElemValue")

            val newState =
              state.copy(
                isoOrUnrecognizedValue =
                  findMagicTimeAsIsoTimeWithNow(visibleInputElemValue, nowAsIsoTime).toLeft(visibleInputElemValue)
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
        companion.visibleInputElemOpt foreach { visibleInputElem =>

//          val hasFocus = visibleInputElem eq dom.document.activeElement

//            val newValue =
//              if (hasFocus) state.editValue
//              else          state.displayValue

          val newStringValue =
            state.isoOrUnrecognizedValue.fold(formatTime(_, state.format), identity)

          writeValue(visibleInputElem, newStringValue)
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
