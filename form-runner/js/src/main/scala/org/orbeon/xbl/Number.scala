/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.xforms.facade.{XBL, XBLCompanion}
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, Constants, EventNames}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.timers

object Number {

  XBL.declareCompanion(
    "fr|number",
    newXBLCompanion
  )

  XBL.declareCompanion(
    "fr|currency",
    newXBLCompanion
  )

  val ListenerSuffix = ".number"

  private def newXBLCompanion: XBLCompanion =
    new XBLCompanionWithState {

      companion =>

      import Private._
      import io.circe.generic.auto._
      import io.circe.{Decoder, Encoder}

      type State = NumberExternalValue

      val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
      val stateDecoder: Decoder[State] = implicitly[Decoder[State]]

      var visibleInputElemOpt: Option[html.Input] = None

      override def init(): Unit = {

        scribe.debug("init")

        val visibleInputElem = $(containerElem).find(".xbl-fr-number-visible-input")(0).asInstanceOf[html.Input]
        companion.visibleInputElemOpt = Some(visibleInputElem)

        // Switch the input type after cleaning up the value for edition
        $(visibleInputElem).on(s"${EventNames.TouchStart}$ListenerSuffix ${EventNames.FocusIn}$ListenerSuffix", {
          (bound: html.Element, e: JQueryEventObject) => {

            scribe.debug(s"reacting to event ${e.`type`}")

            // Don't set value if not needed, so not to unnecessarily disturb the cursor position
            stateOpt foreach { state =>
              if (visibleInputElem.value != state.editValue)
                visibleInputElem.value = state.editValue
            }

            setInputTypeIfNeeded("number")
          }
        }: js.ThisFunction)

        // Restore input type, send the value to the server, and updates value after server response
        $(visibleInputElem).on(s"${EventNames.FocusOut}$ListenerSuffix", {
          (bound: html.Element, e: JQueryEventObject) => {

            scribe.debug(s"reacting to event ${e.`type`}")

            setInputTypeIfNeeded("text")
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
            val formId = $(containerElem).parents("form").attr("id").get
            AjaxClient.allEventsProcessedF("number") foreach { _ =>
              updateVisibleValue()
            }
          }
        }: js.ThisFunction)

        $(visibleInputElem).on(s"${EventNames.KeyPress}$ListenerSuffix", {
          (_: html.Element, e: JQueryEventObject) => {

            scribe.debug(s"reacting to event ${e.`type`}")

            if (Set(10, 13)(e.which)) {
              updateStateAndSendValueToServer()
              AjaxClient.fireEvent(
                AjaxEvent(
                  eventName = EventNames.DOMActivate,
                  targetId  = containerElem.id
                )
              )
            }
          }
        }: js.ThisFunction)
      }

      override def destroy(): Unit = {

        scribe.debug("destroy")

        visibleInputElemOpt foreach { visibleInputElem =>
          $(visibleInputElem).off()
          companion.visibleInputElemOpt = None
        }
      }

      override def xformsUpdateReadonly(readonly: Boolean): Unit = {
        scribe.debug(s"xformsUpdateReadonly: $readonly")
        updateReadonly(readonly)
      }

      override def xformsUpdateState(previousStateOpt: Option[NumberExternalValue], newState: NumberExternalValue): Unit = {

        val NumberExternalValue(displayValue, editValue, decimalSeparator) = newState

        scribe.debug(s"updateWithServerValues: `$displayValue`, `$editValue`, `$decimalSeparator`")

        updateVisibleValue()

        // Also update disabled because this might be called upon an iteration being moved, in which
        // case all the control properties must be updated.
        updateReadonly(companion.isMarkedReadonly)
      }

      override def xformsFocus(): Unit = {
        scribe.debug(s"xformsFocus")
        companion.visibleInputElemOpt foreach (_.focus())
      }

      private object Private {

        private val TestNum = 1.1

        private val hasToLocaleString =
          ! js.isUndefined(TestNum.asInstanceOf[js.Dynamic].toLocaleString)

        def updateReadonly(readonly: Boolean): Unit =
          visibleInputElemOpt foreach { visibleInputElem =>
            if (readonly)
              visibleInputElem.setAttribute("readonly", "readonly")
            else
              visibleInputElem.removeAttribute("readonly")
          }

        def updateStateAndSendValueToServer(): Unit =
          visibleInputElemOpt foreach { visibleInputElem =>
            stateOpt foreach { state =>

              val visibleInputElemValue = visibleInputElem.value

              val stateUpdated =
                updateStateAndSendValueToServerIfNeeded(
                  newState       = state.copy(displayValue = visibleInputElemValue, editValue = visibleInputElemValue),
                  valueFromState = _.editValue
                )

              if (! stateUpdated)
                updateVisibleValue()
            }
          }

        def updateVisibleValue(): Unit =
          companion.visibleInputElemOpt foreach { visibleInputElem =>

          val hasFocus = visibleInputElem eq dom.document.activeElement

          stateOpt foreach { state =>
            visibleInputElem.value =
              if (hasFocus)
                state.editValue
              else
                state.displayValue
          }
        }

        def setInputTypeIfNeeded(typeValue: String): Unit = {

          // See https://github.com/orbeon/orbeon-forms/issues/2545
          def hasNativeDecimalSeparator(separator: Char): Boolean =
            hasToLocaleString &&
              TestNum.asInstanceOf[js.Dynamic].toLocaleString().substring(1, 2).asInstanceOf[String] == separator.toString

          val changeType =
            dom.document.body.classList.contains(Constants.XFormsMobileClass) &&
              companion.stateOpt.exists(state => hasNativeDecimalSeparator(state.decimalSeparator))

          if (changeType) {
            // With Firefox, changing the type synchronously interferes with the focus
            timers.setTimeout(0.millis) {
              visibleInputElemOpt foreach { visibleInputElem =>
                $(visibleInputElem).attr("type", typeValue)
              }
            }
          }
        }
      }
    }
}
