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

import io.udash.wrappers.jquery.JQueryPromise
import org.log4s.Logger
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.web.DomEventNames
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsXbl
import org.orbeon.xforms.*
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.{Promise, UndefOr, timers, |}


object Number {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xbl.Number")
  private val TestNum = 1.1

  XFormsXbl.declareCompanion("fr|number",         js.constructorOf[NumberCompanion])
  XFormsXbl.declareCompanion("fr|number-stepper", js.constructorOf[NumberCompanion])
  XFormsXbl.declareCompanion("fr|currency",       js.constructorOf[NumberCompanion])
  XFormsXbl.declareCompanion("fr|percentage",     js.constructorOf[NumberCompanion])

  private class NumberCompanion(containerElem: html.Element) extends XBLCompanionWithState(containerElem) {

    companion =>

    import Private.*
    import io.circe.generic.auto.*
    import io.circe.{Decoder, Encoder}

    type State = NumberExternalValue

    val stateEncoder: Encoder[State] = implicitly[Encoder[State]]
    val stateDecoder: Decoder[State] = implicitly[Decoder[State]]
    private object EventSupport extends EventListenerSupport

    var visibleInputElemOpt: Option[html.Input]  = None
    var downButtonOpt      : Option[html.Button] = None
    var upButtonOpt        : Option[html.Button] = None

    override def init(): Unit = {

      logger.debug("init")

      val visibleInputElem = containerElem.querySelector(".xbl-fr-number-visible-input").asInstanceOf[html.Input]
      companion.visibleInputElemOpt = Some(visibleInputElem)

      companion.downButtonOpt =
        containerElem
          .querySelectorOpt(".xbl-fr-number-stepper-down:not(.xforms-hidden)")
          .map(_.asInstanceOf[html.Button])
      companion.upButtonOpt =
        containerElem
          .querySelectorOpt(".xbl-fr-number-stepper-up:not(.xforms-hidden)")
          .map(_.asInstanceOf[html.Button])

      companion.downButtonOpt.foreach { downButton =>
        EventSupport.addListener(downButton, DomEventNames.Click, (_: dom.Event) => {
          if (! isMarkedReadonly)
            stepBy(-1)
        })
      }

      companion.upButtonOpt.foreach { upButton =>
        EventSupport.addListener(upButton, DomEventNames.Click, (_: dom.Event) => {
          if (! isMarkedReadonly)
            stepBy(1)
        })
      }

      if (companion.downButtonOpt.isDefined || companion.upButtonOpt.isDefined) {
        EventSupport.addListener(visibleInputElem, DomEventNames.KeyDown, (e: dom.KeyboardEvent) => {
          if (! isMarkedReadonly && visibleInputElem.`type` != "number") {
            e.key match {
              case "ArrowUp" =>
                e.preventDefault()
                stepBy(1)
              case "ArrowDown" =>
                e.preventDefault()
                stepBy(-1)
              case _ =>
            }
          }
        })

        EventSupport.addListener(visibleInputElem, "input", (_: dom.Event) => {
          updateStepperButtons()
        })
      }

      // Switch the input type after cleaning up the value for edition
      EventSupport.addListeners(visibleInputElem, List(DomEventNames.FocusIn), (e: dom.Event) => {
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
      EventSupport.addListener(visibleInputElem, DomEventNames.FocusOut, (e: dom.Event) => {
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

      EventSupport.addListener(visibleInputElem, DomEventNames.KeyPress, (e: dom.KeyboardEvent) => {
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

      updateStepperButtons()
    }

    override def destroy(): Unit = {
      logger.debug("destroy")
      EventSupport.clearAllListeners()
      companion.visibleInputElemOpt = None
      companion.downButtonOpt       = None
      companion.upButtonOpt         = None
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

      def updateReadonly(readonly: Boolean): Unit = {
        visibleInputElemOpt foreach { visibleInputElem =>
          if (readonly)
            visibleInputElem.setAttribute("readonly", "readonly")
          else
            visibleInputElem.removeAttribute("readonly")
        }
        updateStepperButtons()
      }

      def stepperBounds: (BigDecimal, Option[BigDecimal], Option[BigDecimal]) = {
        val step =
          downButtonOpt
            .flatMap(_.dataset.get("step"))
            .map(NumberStepperSupport.effectiveStep)
            .getOrElse(NumberStepperSupport.DefaultStep)
        val minOpt =
          downButtonOpt
            .flatMap(_.dataset.get("min"))
            .flatMap(NumberStepperSupport.parseParamDecimal)
        val maxOpt =
          downButtonOpt
            .flatMap(_.dataset.get("max"))
            .flatMap(NumberStepperSupport.parseParamDecimal)
        (step, minOpt, maxOpt)
      }

      def updateStepperButtons(): Unit =
        if (downButtonOpt.isDefined || upButtonOpt.isDefined) {
          if (companion.isMarkedReadonly) {
            downButtonOpt.foreach(_.disabled = true)
            upButtonOpt.foreach(_.disabled   = true)
          } else {
            val (_, minOpt, maxOpt) = stepperBounds
            val decimalSep          = stateOpt.map(_.decimalSeparator).getOrElse('.')
            val currentVal          =
              visibleInputElemOpt match {
                case Some(visibleInputElem) if visibleInputElem eq dom.document.activeElement =>
                  readValue(visibleInputElem, decimalSep)
                case _ =>
                  stateOpt.map(_.editValue).orElse(visibleInputElemOpt.map(_.value)).getOrElse("")
              }
            downButtonOpt.foreach(_.disabled = ! NumberStepperSupport.canStep(currentVal, decimalSep, -1, minOpt, maxOpt))
            upButtonOpt.foreach(_.disabled   = ! NumberStepperSupport.canStep(currentVal, decimalSep,  1, minOpt, maxOpt))
          }
        }

      def stepBy(direction: Int): Unit =
        visibleInputElemOpt.foreach { visibleInputElem =>
          stateOpt.foreach { state =>
            val (step, minOpt, maxOpt) = stepperBounds
            val hasFocus               = visibleInputElem eq dom.document.activeElement
            val currentEditValue       =
              if (hasFocus)
                readValue(visibleInputElem, state.decimalSeparator)
              else
                state.editValue

            NumberStepperSupport.stepEditValue(
              currentEditValue = currentEditValue,
              decimalSeparator = state.decimalSeparator,
              direction        = direction,
              step             = step,
              minOpt           = minOpt,
              maxOpt           = maxOpt
            ).foreach { nextEditValue =>
              writeValue(visibleInputElem, state.decimalSeparator, nextEditValue)
              updateStateAndSendValueToServer((_, _) => nextEditValue)
              updateStepperButtons()
              AjaxClient.allEventsProcessedF("number") foreach { _ =>
                updateVisibleValue()
                updateStepperButtons()
              }
            }
          }
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

            updateStepperButtons()
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
          dom.document.body.hasClass(Constants.XFormsMobileClass) &&
            companion.stateOpt.exists(state => hasNativeDecimalSeparator(state.decimalSeparator))

        if (changeType) {
          // With Firefox, changing the type synchronously interferes with the focus
          timers.setTimeout(0.millis) {
            visibleInputElemOpt foreach { visibleInputElem =>
              visibleInputElem.`type` = typeValue
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
