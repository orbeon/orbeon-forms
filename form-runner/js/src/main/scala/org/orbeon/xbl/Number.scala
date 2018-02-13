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

import org.orbeon.xforms.facade.AjaxServerOps._
import org.orbeon.xforms.facade.{AjaxServer, XBL, XBLCompanion}
import org.orbeon.xforms.{$, DocumentAPI}
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

  private def newXBLCompanion: XBLCompanion =
    new XBLCompanion {

      companion ⇒

      import Private._

      var visibleInputElem     : html.Input = null

      case class State(serverValue: String, editValue: String, decimalSeparatorChar: Char)

      var stateOpt: Option[State] = None

      override def init(): Unit = {

        log("init")

        companion.visibleInputElem = $(containerElem).find(".xbl-fr-number-visible-input")(0).asInstanceOf[html.Input]

        // Switch the input type after cleaning up the value for edition
        $(companion.visibleInputElem).on("touchstart.number focusin.number", {
          (bound: html.Element, e: JQueryEventObject) ⇒ {

            log("focusin")

            // Don"t set value if not needed, so not to unnecessarily disturb the cursor position
            stateOpt foreach { state ⇒
              if (companion.visibleInputElem.value != state.editValue)
                companion.visibleInputElem.value = state.editValue
            }

            setInputType("number")
          }
        }: js.ThisFunction)

        // Restore input type, send the value to the server, and updates value after server response
        $(companion.visibleInputElem).on("focusout.number", {
          (bound: html.Element, e: JQueryEventObject) ⇒ {

            log("focusout")

            setInputType("text")
            sendValueToServer()

            val formId = $(containerElem).parents("form").attr("id").get

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
            AjaxServer.ajaxResponseProcessedF(formId) foreach { _ ⇒
              updateVisibleValue()
            }
          }
        }: js.ThisFunction)

        $(companion.visibleInputElem).on("keypress.number", {
          (bound: html.Element, e: JQueryEventObject) ⇒ {

            log("keypress")

            if (e.which == 13)
              sendValueToServer()
          }
        }: js.ThisFunction)

      }

      override def destroy(): Unit = {
        $(companion.visibleInputElem).off()
        companion.visibleInputElem = null

      }

      override def xformsUpdateReadonly(readonly: Boolean): Unit = {
        log(s"xformsUpdateReadonly: $readonly")
        companion.visibleInputElem.disabled = readonly
      }

      override def xformsFocus(): Unit = {
        log(s"xformsFocus")
        companion.visibleInputElem.focus()
      }

      // Callback from `number.xbl`
      def updateWithServerValues(serverValue: String, editValue: String, decimalSeparator: String): Unit = {

        log(s"updateWithServerValues: `$serverValue`, `$editValue`, `$decimalSeparator`")

        stateOpt = Some(State(serverValue, editValue, decimalSeparator.headOption getOrElse '.'))
        updateVisibleValue()

        // Also update disabled because this might be called upon an iteration being moved, in which
        // case all the control properties must be updated.
        visibleInputElem.disabled = $(containerElem).hasClass("xforms-readonly")
      }

      private object Private {

        private val TestNum = 1.1

        private val hasToLocaleString =
          ! js.isUndefined(TestNum.asInstanceOf[js.Dynamic].toLocaleString)

        def log(s: String) =
          println(s"fr:number: $s")

        // TODO: Can we not send if unchanged?
        def sendValueToServer(): Unit = {
          log(s"sendValueToServer: `${companion.visibleInputElem.value}`")
          DocumentAPI.dispatchEvent(
            targetId   = containerElem.id,
            eventName  = "fr-set-client-value",
            properties = js.Dictionary(
              "value" → companion.visibleInputElem.value
            )
          )
        }

        def updateVisibleValue(): Unit = {

          val hasFocus = companion.visibleInputElem eq dom.document.activeElement

          stateOpt foreach { state ⇒
            companion.visibleInputElem.value =
              if (hasFocus)
                state.editValue
              else
                state.serverValue
          }
        }

        def setInputType(typeValue: String): Unit = {

          // See https://github.com/orbeon/orbeon-forms/issues/2545
          def hasNativeDecimalSeparator(separator: Char): Boolean =
            hasToLocaleString &&
              TestNum.asInstanceOf[js.Dynamic].toLocaleString().substring(1, 2).asInstanceOf[String] == separator.toString

          val changeType =
            $("body").is(".xforms-mobile") &&
              companion.stateOpt.exists(state ⇒ hasNativeDecimalSeparator(state.decimalSeparatorChar))

          if (changeType) {
            // With Firefox, changing the type synchronously interferes with the focus
            timers.setTimeout(0.millis) {
              $(companion.visibleInputElem).attr("type", typeValue)
            }
          }
        }
      }
    }
}
