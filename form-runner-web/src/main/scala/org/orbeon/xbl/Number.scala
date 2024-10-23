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

import org.log4s.Logger
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.xforms.facade.XBL
import org.orbeon.xforms.{$, AjaxClient, AjaxEvent, Constants, EventListenerSupport}
import org.scalajs.dom
import org.scalajs.dom.html
import io.udash.wrappers.jquery.{JQueryEvent, JQueryPromise}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.{Promise, UndefOr, timers, |}


object Number {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Number")
  private val TestNum = 1.1

  XBL.declareCompanion("fr|number",   js.constructorOf[NumberCompanion])
  XBL.declareCompanion("fr|currency", js.constructorOf[NumberCompanion])

  private class NumberCompanion(containerElem: html.Element) extends XBLCompanionWithState(containerElem) {

    companion =>

    import Private._
    import io.circe.generic.auto._
    import io.circe.{Decoder, Encoder}

    type State = NumberExternalValue

    val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
    val stateDecoder: Decoder[State] = implicitly[Decoder[State]]
    private val eventListenerSupport = new EventListenerSupport {}

    var visibleInputElemOpt: Option[html.Input] = None

    override def init(): Unit = {

      logger.debug("init")

      val visibleInputElem = containerElem.querySelector(".xbl-fr-number-visible-input").asInstanceOf[html.Input]
      companion.visibleInputElemOpt = Some(visibleInputElem)

      // Switch the input type after cleaning up the value for edition
      eventListenerSupport.addListeners(visibleInputElem, List(DomEventNames.FocusIn, DomEventNames.TouchStart), (e: dom.Event) => {
        if (! isMarkedReadonly) {

          logger.debug(s"reacting to event ${e.`type`}")

          stateOpt foreach { state =>
            // Don't set value if not needed, so not to unnecessarily disturb the cursor position
            if (readValue(visibleInputElem, state.decimalSeparator) != state.editValue)
              writeValue(visibleInputElem, state.decimalSeparator, state.editValue)
          }

          setInputTypeIfNeeded("number")
        }
      })

      // Restore input type, send the value to the server, and updates value after server response
      eventListenerSupport.addListener(visibleInputElem, DomEventNames.FocusOut, (e: dom.Event) => {
        if (! isMarkedReadonly) {
          logger.debug(s"reacting to event ${e.`type`}")

          updateStateAndSendValueToServer(readValue)
          setInputTypeIfNeeded("text")

          // Always update visible value with XForms value:
          //
          // - relying just on value change event from server is not enough
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
          AjaxClient.allEventsProcessedF("number") foreach { _ =>
            updateVisibleValue()
          }
        }
      })

      eventListenerSupport.addListener(visibleInputElem, DomEventNames.KeyPress, (e: dom.KeyboardEvent) => {
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
      })
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      eventListenerSupport.clearAllListeners()
      companion.visibleInputElemOpt = None
    }

    override def xformsUpdateReadonly(readonly: Boolean): Unit = {
      logger.debug(s"xformsUpdateReadonly: $readonly")
      updateReadonly(readonly)
    }

    def xformsUpdateState(previousStateOpt: Option[State], newState: State): Unit = {

      val NumberExternalValue(displayValue, editValue, decimalSeparator) = newState

      logger.debug(s"updateWithServerValues: `$displayValue`, `$editValue`, `$decimalSeparator`")

      updateVisibleValue()

      // Also update disabled because this might be called upon an iteration being moved, in which
      // case all the control properties must be updated.
      updateReadonly(companion.isMarkedReadonly)
    }

    override def xformsFocus(): Unit = {
      logger.debug(s"xformsFocus")
      companion.visibleInputElemOpt foreach (_.focus())
    }

    override def setUserValue(newValue: String): UndefOr[Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] =
      updateStateAndSendValueToServer((_, _) => newValue)

    private object Private {

      private val hasToLocaleString =
        ! js.isUndefined(TestNum.asInstanceOf[js.Dynamic].toLocaleString)

      // On mobile, we set the field to `type="number"`, so the format of `value` always uses a `.`
      // as decimal separator
      private def mobileConversionNeeded(input: html.Input, decimalSeparator: Char): Boolean =
        input.`type` == "number" && decimalSeparator != '.'

      def updateReadonly(readonly: Boolean): Unit =
        visibleInputElemOpt foreach { visibleInputElem =>
          if (readonly)
            visibleInputElem.setAttribute("readonly", "readonly")
          else
            visibleInputElem.removeAttribute("readonly")
        }

      def updateStateAndSendValueToServer(read: (html.Input, Char) => String): Unit =
        visibleInputElemOpt foreach { visibleInputElem =>
          stateOpt foreach { state =>

            val visibleInputElemValue = read(visibleInputElem, state.decimalSeparator)

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

          def hasFocus = visibleInputElem eq dom.document.activeElement

          stateOpt foreach { state =>
            val newValue =
              if (! companion.isMarkedReadonly && hasFocus)
                state.editValue
              else
                state.displayValue
            writeValue(visibleInputElem, state.decimalSeparator, newValue)
          }
        }

      def setInputTypeIfNeeded(typeValue: String): Unit = {

        // Only switch to `type="number"` if the OS decimal separator is the same as the field decimal separator
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
              // Set again the `input.value`, as otherwise we might loose the value (switching the type to `number`
              // if the current value is `1,2`), or be incorrect (switching the type to `text` if the current value
              // is `1.2` and the decimal separator `,`)
              updateVisibleValue()
            }
          }
        }
      }

      def readValue(input: html.Input, decimalSeparator: Char): String = {
        val convertFromMobile = mobileConversionNeeded(input, decimalSeparator)
        input.value.pipeIf(convertFromMobile, _.replace('.', decimalSeparator))
      }

      def writeValue(input: html.Input, decimalSeparator: Char, value: String): Unit = {
        val convertForMobile = mobileConversionNeeded(input, decimalSeparator)
        input.value = value.pipeIf(convertForMobile, _.replace(decimalSeparator, '.'))
      }
    }
  }
}
