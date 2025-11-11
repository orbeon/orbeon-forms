/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.xforms

import io.udash.wrappers.jquery.JQueryPromise
import org.log4s.Logger
import org.orbeon.facades.HTMLDialogElement
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.{Controls, Events}
import org.scalajs.dom
import org.scalajs.dom.{MouseEvent, html}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import shapeless.syntax.typeable.*

import scala.collection.immutable
import scala.concurrent.duration.{span as _, *}
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.timers.SetTimeoutHandle
import scala.scalajs.js.{timers, |}


@JSExportTopLevel("OrbeonXFormsUi")
object XFormsUI {

  val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.XFormsUI")

  import Private.*

  // Algorithm for a single repeated checkbox click:
  //
  // - always remember the last clicked checkbox, whether with shift or not or checked or not
  // - if an unchecked box is clicked with shift, ensure all the checkboxes in the range are unchecked
  // - if a checked box is clicked with shift, ensure all the checkboxes in the range are checked
  //
  // This matches what GMail does AFAICT.
  @JSExport
  def handleShiftSelection(clickEvent: dom.MouseEvent, controlElem: html.Element): Unit =
    if (controlElem.classList.contains("xforms-select-appearance-full")) {
      // Only for "checkbox" controls
      val checkboxInputs = controlElem.queryNestedElems[html.Input]("input", includeSelf = false)
      if (XFormsId.hasEffectiveIdSuffix(controlElem.id) && checkboxInputs.length == 1) {
        // Click on a single repeated checkbox

        val checkboxElem = checkboxInputs.head

        if (clickEvent.getModifierState("Shift")) {
          // Just got shift-selected

          val newCheckboxChecked = checkboxElem.checked

          def findControlIdsToUpdate(leftId: XFormsId, rightId: XFormsId): Option[immutable.IndexedSeq[String]] =
            if (leftId.isRepeatNeighbor(rightId) && leftId.iterations.last != rightId.iterations.last) {

              val indexes =
                if (leftId.iterations.last > rightId.iterations.last)
                  rightId.iterations.last + 1 to leftId.iterations.last
                else
                  leftId.iterations.last until rightId.iterations.last

              Some(indexes map (index => leftId.copy(iterations = leftId.iterations.init :+ index).toEffectiveId))
            } else {
              None
            }

          for {
            (lastControlElem, _) <- lastCheckboxChecked
            targetId             = XFormsId.fromEffectiveId(controlElem.id)
            controlIds           <- findControlIdsToUpdate(XFormsId.fromEffectiveId(lastControlElem.id), targetId)
            controlId            <- controlIds
            controlElem          <- dom.document.getElementByIdOpt(controlId)
            checkboxValue        <-
              if (newCheckboxChecked)
                controlElem.queryNestedElems[html.Input]("input", includeSelf = false).headOption.map(_.value)
              else
                Some("")
          } locally {
            DocumentAPI.setValue(controlId, checkboxValue)
          }
        }

        // Update selection no matter what
        lastCheckboxChecked = Some(controlElem -> checkboxElem)

      } else if (checkboxInputs.nonEmpty) {
        // Within a single group of checkboxes

        // LATER: could support click on `<span>` inside `<label>`
        clickEvent.target.narrowTo[html.Input] foreach { currentCheckboxElem =>
          if (clickEvent.getModifierState("Shift"))
            for {
              (lastControlElem, lastCheckboxElem) <- lastCheckboxChecked
              if lastControlElem.id == controlElem.id
              allCheckboxes                       = checkboxInputs.toList
              lastIndex                           = allCheckboxes.indexWhere(_.value == lastCheckboxElem.value)
              if lastIndex >= 0
              currentIndex                        = allCheckboxes.indexWhere(_.value == currentCheckboxElem.value)
              if currentIndex >= 0 && currentIndex != lastIndex
              curentIndex                         <- if (currentIndex < lastIndex) currentIndex + 1 to lastIndex else lastIndex until currentIndex
              checkboxToUpdate                    = allCheckboxes(curentIndex)
            } locally {
              checkboxToUpdate.checked = currentCheckboxElem.checked
            }

          // Update selection no matter what
          lastCheckboxChecked = Some(controlElem -> currentCheckboxElem)
        }
      } else {
        lastCheckboxChecked = None
      }
    } else {
      lastCheckboxChecked = None
    }

  // TODO: remove once removed from xforms.js.
  // Update `xforms-selected`/`xforms-deselected` classes on the parent `<span>` element
  @JSExport
  def setRadioCheckboxClasses(target: html.Element): Unit =
    for (checkboxInput <- target.queryNestedElems[html.Input]("input[type = 'checkbox'], input[type = 'radio']", includeSelf = false))
      setOneRadioCheckboxClasses(checkboxInput)

  def setOneRadioCheckboxClasses(checkboxInput: html.Input): Unit = {
    var parentSpan = checkboxInput.parentElement // boolean checkboxes are directly inside a `span`
    if (parentSpan.tagName.equalsIgnoreCase("label"))
      parentSpan = parentSpan.parentElement      // while `xf:select` checkboxes have a `label` in between

    if (checkboxInput.checked) {
      parentSpan.classList.add("xforms-selected")
      parentSpan.classList.remove("xforms-deselected")
    } else {
      parentSpan.classList.add("xforms-deselected")
      parentSpan.classList.remove("xforms-selected")
    }
  }

  @JSExport // 2020-04-27: 6 JavaScript usages from xforms.js
  var modalProgressPanelShown: Boolean = false

  @JSExport
  def fieldValueChanged(targetElem: html.Element): Unit =
    // https://github.com/orbeon/orbeon-forms/issues/6960
    Page.findXFormsFormFromHtmlElem(targetElem)
      .filter(_.allowClientDataStatus)
      .foreach(_.formDataSafe = false)

  @JSExport // 2020-04-27: 1 JavaScript usage
  def displayModalProgressPanel(): Unit =
    if (! modalProgressPanelShown) {

      modalProgressPanelShown = true

      // Take out the focus from the current control
      // See https://github.com/orbeon/orbeon-forms/issues/4511
      val focusControlIdOpt =
        Option(Globals.currentFocusControlId) map { focusControlId =>
          Controls.removeFocus(focusControlId)
          focusControlId
        }

      val timerIdOpt =
        if (isIOS && getZoomLevel != 1.0) {
          resetIOSZoom()
            Some(
              timers.setTimeout(200.milliseconds) {
                showModalProgressPanelRaw()
              }
            )
        } else {
          showModalProgressPanelRaw()
          None
        }

      AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("modal panel") foreach { details =>

        // Hide the modal progress panel, unless the server tells us to do a submission or load, so we don't want
        // to remove it otherwise users could start interacting with a page which is going to be replaced shortly.
        //
        // We remove the modal progress panel before handling DOM response, as script actions may dispatch
        // events and we don't want them to be filtered. If there are server events, we don't remove the
        // panel until they have been processed, i.e. the request sending the server events returns.
        val mustHideModalPanel =
          ! (
            // `exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])`
            details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "submission").iterator ++
              details.responseXML.getElementsByTagNameNS(Namespaces.XXF, "load").iterator exists
              (e => ! e.hasAttribute("target") && e.getAttribute("show-progress") != "false")
          )

        if (mustHideModalPanel)
          hideModalProgressPanel(timerIdOpt, focusControlIdOpt)
      }
    }

  private def isTooltipDisabled(elem: html.Element, lhha: String) =
    elem
      .ancestorOrSelfElem(s".xforms-disable-$lhha-as-tooltip")
      .nonEmpty

  def mouseover(event: MouseEvent): Unit =
    event.targetOpt.foreach { target =>

      val controlOpt = Option(Events._findParentXFormsControl(target))

      if (target.classList.contains("xforms-alert") && target.classList.contains("xforms-active")) {
        // Alert tooltip

        // NOTE: control may be `null` if we have `<div for="">`. Using `control.getAttribute("for")` returns a proper
        // for, but then tooltips sometimes fail later with Ajax portlets in particular. So for now, just don't
        // do anything if there is no control found.
        Option(Controls.getControlForLHHA(target, "alert")).foreach { formField =>
          if (! isTooltipDisabled(target, "alert")) {
            // The 'for' typically points to a form field which is inside the element representing the control
            Option(Events._findParentXFormsControl(formField)).foreach { control2 =>
              val message = Controls.getAlertMessage(control2)
              Events._showToolTip(Globals.alertTooltipForControl, control2, target, "-orbeon-alert-tooltip", message, event)
            }
          }
        }
      } else if (target.classList.contains("xforms-help")) {
        // Help tooltip
        controlOpt.foreach { control =>
          if (Page.getXFormsFormFromHtmlElemOrThrow(control).helpTooltip()) {
            val message = Controls.getHelpMessage(control)
            Events._showToolTip(Globals.helpTooltipForControl, control, target, "-orbeon-help-tooltip", message, event)
          }
        }
      } else {
        // Hint tooltip
        controlOpt.foreach { control =>

          // Only show hint if the mouse is over a child of the control, so we don't show both the hint and the alert or help
          if (! isTooltipDisabled(control, "hint") && control != target) {

            // Find closest ancestor-or-self control with a non-empty hint, for compound control like the datetime
            var candidateMessageHolder: html.Element = control
            var candidateMessage      : String       = ""
            while (candidateMessageHolder != null && candidateMessage == "") {
              candidateMessage       = Controls.getHintMessage(candidateMessageHolder)
              candidateMessageHolder = Events._findParentXFormsControl(candidateMessageHolder.parentNode)
            }

            // Clear any `title`, to avoid having both the YUI tooltip and the browser tooltip based on the title showing up
            if (control.classList.contains("xforms-trigger") || control.classList.contains("xforms-submit"))
              control.querySelectorAllT("a, button").foreach(_.title = "")
            Events._showToolTip(Globals.hintTooltipForControl, control, target, "-orbeon-hint-tooltip", candidateMessage, event)
          }
        }
      }
    }

  def mouseout(event: MouseEvent): Unit =
    event.targetOpt.foreach { target =>
      Option(Events._findParentXFormsControl(target)).foreach { control =>
        // Send the `mouseout` event to the YUI tooltip to handle the case where: (1) we get the `mouseover` event, (2) we
        // create a YUI tooltip, (3) the `mouseout` happens before the YUI dialog got a chance to register its listener
        // on `mouseout`, (4) the YUI dialog is only dismissed after `autodismissdelay` (5 seconds) leaving a trail.
        Globals.hintTooltipForControl.get(control.id).foreach { yuiTooltip =>
          if (! isTooltipDisabled(control, "hint"))
            yuiTooltip.asInstanceOf[js.Dynamic].onContextMouseOut.call(control.id, event, yuiTooltip)
        }
      }
    }

  def isIOS: Boolean =
    dom.document.body.classList.contains("xforms-ios")

  def getZoomLevel: Double =
    dom.document.documentElement.clientWidth.toDouble / dom.window.innerWidth

  def resetIOSZoom(): Unit = {
    Option(dom.document.querySelector("meta[name = 'viewport']")).foreach { viewPortMeta =>
      viewPortMeta.attValueOpt("content").foreach { contentAttribute =>

        val MaximumScale = "maximum-scale"

        val pairsNoMaximumScale =
          contentAttribute
            .splitTo[List](",;")
            .map(_.splitTo[List]("="))
            .collect {
              case List(NonAllBlank(key), NonAllBlank(value)) if key != MaximumScale => key -> value
            }

        def makeParamString(p: List[(String, String)]) =
          p.map(kv => s"${kv._1}=${kv._2}").mkString(",")

        // 2024-09-20: Not sure why we did things this way
        viewPortMeta.setAttribute("content", makeParamString((MaximumScale -> "1.0") :: pairsNoMaximumScale))
        viewPortMeta.setAttribute("content", makeParamString(pairsNoMaximumScale))
      }
    }
  }

  def showDialogForInit(dialogId: String, neighborIdOpt: Option[String]): Unit =
    showDialog(dialogId, neighborIdOpt, "showDialogForInitWithNeighbor")

  // Server telling us to show the dialog
  def showDialog(controlId: String, neighborIdOpt: Option[String], reason: String): Unit = {
    val dialogElem = dom.document.getElementById(controlId).asInstanceOf[HTMLDialogElement]
    dialogElem.showModal()
    dialogElem.addEventListener("cancel" , dialogCancelListener )
    dialogElem.addEventListener("keydown", dialogKeydownListener)
    dom.document.getElementByIdOpt("orbeon-inspector").foreach(dialogElem.appendChild)
  }

  // Server telling us to hide the dialog
  def hideDialog(id: String, formId: String): Unit = {
    val dialogElem = dom.document.getElementById(id).asInstanceOf[HTMLDialogElement]
    dialogElem.removeEventListener("cancel" , dialogCancelListener )
    dialogElem.removeEventListener("keydown", dialogKeydownListener)
    dialogElem.close()

    val inspectorElemOpt = dialogElem.querySelectorOpt("#orbeon-inspector")
    val formElemOpt      = dom.document.getElementByIdOpt(formId)
    inspectorElemOpt.foreach(inspectorElem => formElemOpt.foreach(_.appendChild(inspectorElem)))
  }

  def showModalProgressPanelImmediate(): Unit =
    showModalProgressPanelRaw()

  def hideModalProgressPanelImmediate(): Unit =
    hideModalProgressPanelRaw()

  def maybeFutureToScalaFuture(promiseOrUndef: js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]]): Future[Unit] = {

    val promiseOrUndefDyn = promiseOrUndef.asInstanceOf[js.Dynamic]

    if (XFormsXbl.isObjectWithMethod(promiseOrUndefDyn, "done")) {
      // JQuery future or similar
      val promise = Promise[Unit]()
      promiseOrUndef
        .asInstanceOf[JQueryPromise[js.Function1[js.Any, js.Any], js.Any]]
        .done((_: js.Any) => { promise.success(()); () })
      promise.future
    } else if (XFormsXbl.isObjectWithMethod(promiseOrUndefDyn, "then")) {
      // JavaScript future
      promiseOrUndef.asInstanceOf[js.Promise[Unit]].toFuture
    } else {
      // Not a future
      Future.unit
    }
  }

  private object Private {

    // Global information about the last checkbox to check
    // TODO: This be by form.
    var lastCheckboxChecked: Option[(html.Element, html.Input)] = None

    private def findLoaderElem: Option[dom.Element] =
      Option(dom.document.querySelector(".orbeon-loader"))

    private def createLoaderElem: dom.Element = {
      val newDiv = dom.document.createElement("div")
      Seq("orbeon-loader", "loader", "loader-default").foreach(newDiv.classList.add)
      dom.document.body.appendChild(newDiv)
      newDiv
    }

    // Users closed the dialog (Esc): the browser dispatches the `cancel` event, we inform the server
    // Declared as `val` to ensure function identity, for the `removeEventListener` to work
    val dialogCancelListener: js.Function1[dom.Event, Unit] = (event: dom.Event) => {
      val dialogElem = event.target.asInstanceOf[html.Element]

      // So any `blur` on a control inside the dialog comes before the dialog close event
      dom.document.activeElementOpt.foreach(activeElement =>
        if (dialogElem.contains(activeElement))
          activeElement.blur())

      AjaxClient.fireEvent(
        AjaxEvent(
          eventName = "xxforms-dialog-close",
          targetId  = dialogElem.id,
        )
      )
    }

    val dialogKeydownListener: js.Function1[dom.KeyboardEvent, Unit] = (event: dom.KeyboardEvent) => {
      event.targetT.closestOpt("dialog").foreach { dialogElem =>

        // Prevent Esc from closing the dialog if the `xxf:dialog` has `close="false"` or if a help popover is open
        val supportsClose      = dialogElem.classList.contains("xforms-dialog-close-true")
        val hasHelpPopoverOpen = dialogElem.querySelectorOpt(".xforms-help-popover").isDefined
        if (event.key == "Escape" && (! supportsClose || hasHelpPopoverOpen)) {
          event.preventDefault()
          event.stopPropagation()
        }

        // Cmd-Enter or Ctrl-Enter is equivalent to clicking the primary button
        if (event.key == "Enter" && (event.metaKey || event.ctrlKey)) {
          val allButtons            = dialogElem.querySelectorAll("button.btn-primary")
          val firstVisibleButtonOpt = allButtons.toList
            .map(_.asInstanceOf[html.Button])
            .find { button =>
              val computedStyle = dom.window.getComputedStyle(button)
              computedStyle.visibility != "hidden" &&
              computedStyle.display    != "none"
            }
          firstVisibleButtonOpt.foreach { button =>
            // Blur active element so to send a possible value change before closing the dialog
            Option(dom.document.activeElement).foreach(_.asInstanceOf[html.Element].blur())
            button.click()
          }
        }
      }
    }

    def showModalProgressPanelRaw(): Unit = {
      val loaderElem = findLoaderElem.getOrElse(createLoaderElem)
      dom.document.querySelectorAllT("dialog") // Move inside first open dialog, if any, for the loader to show above the dialog
        .collectFirst { case dialog: dom.html.Dialog if dialog.open => dialog }
        .getOrElse(dom.document.body)
        .appendChild(loaderElem)
      loaderElem.classList.add("is-active")
    }

    def hideModalProgressPanelRaw(): Unit =
      findLoaderElem foreach { elem =>
        val cl = elem.classList
        cl.remove("is-active")
      }

    def hideModalProgressPanel(
      timerIdOpt        : Option[SetTimeoutHandle],
      focusControlIdOpt : Option[String]
    ): Unit =
      if (modalProgressPanelShown) {

        modalProgressPanelShown = false

        // So that the modal progress panel doesn't show just after we try to hide it
        timerIdOpt foreach timers.clearTimeout

        hideModalProgressPanelRaw()

        // Restore focus
        // See https://github.com/orbeon/orbeon-forms/issues/4511
        focusControlIdOpt foreach Controls.setFocus
      }
  }
}
