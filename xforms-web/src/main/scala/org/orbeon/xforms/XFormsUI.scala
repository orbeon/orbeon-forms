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
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.{Controls, Events, FlatNesting, XBL}
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

  private val ClassNameToId: Map[String, String] = Map(
    "label"   -> (Constants.LhhacSeparator + "l"),
    "hint"    -> (Constants.LhhacSeparator + "t"),
    "help"    -> (Constants.LhhacSeparator + "p"),
    "alert"   -> (Constants.LhhacSeparator + "a"),
    "control" -> (Constants.LhhacSeparator + "c")
  )

  @JSExport
  def getControlLHHA(control: html.Element, lhhaType: String): html.Element =
    findControlLHHA(control: html.Element, lhhaType: String).orNull

  def findControlLHHA(control: html.Element, lhhaType: String): Option[html.Element] = {

    // Search by id first
    // See https://github.com/orbeon/orbeon-forms/issues/793
    def byIdOpt: Option[html.Element] =
      dom
        .document
        .getElementByIdOpt(XFormsId.appendToEffectiveId(control.id, ClassNameToId(lhhaType)))

    // Search just under the control element, excluding elements with an LHHA id, as they might be for a nested
    // control if we are a grouping control. Test on `LhhacSeparator` as e.g. portals might add their own id.
    // See: https://github.com/orbeon/orbeon-forms/issues/1206
    def directChildOpt: Option[html.Element] =
      control
        .childrenT
        .find(c => c.hasClass("xforms-" + lhhaType) && ! c.id.contains(Constants.LhhacSeparator))

    // If the control is an LHHA, which happens for LHHA outside of the control, when the control is in a repeat
    def isLhhaOpt: Option[html.Element] =
      control.hasClass("xforms-" + lhhaType).option(control)

    byIdOpt
      .orElse(directChildOpt)
      .orElse(isLhhaOpt)
  }

  private def getControlForLHHA(element: html.Element, lhhaType: String): html.Element = {
    val suffix = ClassNameToId(lhhaType)
    if (element.hasClass("xforms-control"))
      element
    else if (element.id.contains(suffix))
      dom.document.getElementByIdT(element.id.replace(suffix, ""))
    else
      element.parentElement
  }

  // lhhaChangeEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
  @JSExport
  def setMessage(control: html.Element, lhhaType: String, message: String): Unit =
    findControlLHHA(control, lhhaType).foreach { lhhaElement =>
      lhhaElement.innerHTML = message
      // https://github.com/orbeon/orbeon-forms/issues/4062
      if (lhhaType == "help") {
        if (message.isAllBlank)
          lhhaElement.classList.add("xforms-disabled")
        else
          lhhaElement.classList.remove("xforms-disabled")
      }
    }

  @JSExport
  def getLabelMessage(control: html.Element): String =
    if (control.hasAnyClass("xforms-trigger", "xforms-submit"))
      findControlLHHA(control, "control").map(_.innerHTML).getOrElse("")
    else if (control.hasClass("xforms-dialog"))
      control.childrenT.headOption.map(_.innerHTML).getOrElse("")
    else if (control.hasClass("xforms-group-appearance-xxforms-fieldset"))
      control.childrenT.headOption.map(_.innerHTML).getOrElse("")
    else
      findControlLHHA(control, "label").map(_.innerHTML).getOrElse("")

  @JSExport
  def setLabelMessage(control: html.Element, message: String): Unit =
    if (control.hasAnyClass("xforms-trigger", "xforms-submit"))
      setMessage(control, "control", message)
    else if (control.hasClass("xforms-dialog"))
      control.childrenT.headOption.foreach(_.innerHTML = message)
    else if (control.hasClass("xforms-group-appearance-xxforms-fieldset"))
      control.childrenT.headOption.foreach(_.innerHTML = message)
    else if (control.hasClass("xforms-output-appearance-xxforms-download"))
      control.childrenT.headOption.foreach(_.innerHTML = message)
    else
      setMessage(control, "label", message)

  @JSExport
  def getHelpMessage(control: html.Element): String =
    findControlLHHA(control, "help") match {
      case None => ""
      case Some(helpElement) =>
        if (helpElement.hasClass("xforms-control"))
          helpElement.innerHTML
        else
          helpElement.textContent
    }

  @JSExport
  def setHelpMessage(control: html.Element, message: String): Unit = {
    val escapedMessage = message.escapeXmlMinimal
    setMessage(control, "help", escapedMessage)
    Controls._setTooltipMessage(control, escapedMessage, Globals.helpTooltipForControl)
  }

  @JSExport
  def setConstraintLevel(control: html.Element, newLevel: String): Unit = {

    val alertActive = newLevel != ""

    def toggleCommonClasses(element: html.Element): Unit = {
      if (newLevel == "error")   element.classList.add("xforms-invalid") else element.classList.remove("xforms-invalid")
      if (newLevel == "warning") element.classList.add("xforms-warning") else element.classList.remove("xforms-warning")
      if (newLevel == "info")    element.classList.add("xforms-info")    else element.classList.remove("xforms-info")
    }

    toggleCommonClasses(control)

    findControlLHHA(control, "alert").foreach { alertElement =>
      if (alertActive)
        alertElement.classList.add("xforms-active") else alertElement.classList.remove("xforms-active")
      if (alertElement.id.nonAllBlank)
        toggleCommonClasses(alertElement)
    }

    Globals.alertTooltipForControl.get(control.id).foreach { alertTooltipDyn =>
      if (alertTooltipDyn != null && ! js.isUndefined(alertTooltipDyn) && alertTooltipDyn.toString != "true") {
        val alertTooltip = alertTooltipDyn.asInstanceOf[js.Dynamic]
        if (! alertActive) {
          // Prevent the tooltip from becoming visible on mouseover
          alertTooltip.cfg.setProperty("disabled", true)
          // If visible, hide the tooltip right away, otherwise it will only be hidden a few seconds later
          if (js.typeOf(alertTooltip.hide) == "function")
            alertTooltip.hide()
        } else {
          alertTooltip.cfg.setProperty("disabled", false)
        }
      }
    }
  }

  @JSExport
  def setDisabledOnFormElement(element: html.Element, disabled: Boolean): Unit =
    if (disabled)
      element.setAttribute("disabled", "disabled") // Q: Could use `element.disabled = disabled`?
    else
      element.removeAttribute("disabled")

  @JSExport
  def setReadonlyOnFormElement(element: html.Element, readonly: Boolean): Unit =
    if (readonly)
      element.setAttribute("readonly", "readonly") // Q: Could use `element.readonly = readonly`?
    else
      element.removeAttribute("readonly")

  @JSExport
  def setRelevant(control: html.Element, isRelevant: Boolean): Unit =
    if (control.hasClass("xforms-group-begin-end")) {
      FlatNesting.setRelevant(control, isRelevant)
    } else {

      val elementsToUpdate =
        control ::
        findControlLHHA(control, "label").toList :::
        findControlLHHA(control, "alert").toList :::
        (! isRelevant || (isRelevant && getHelpMessage(control).nonAllBlank))
          .flatList(findControlLHHA(control, "help").toList) :::
        (! isRelevant || (isRelevant && Controls.getHintMessage(control).nonAllBlank))
          .flatList(findControlLHHA(control, "hint").toList)

      elementsToUpdate.foreach { element =>
        if (isRelevant)
          element.classList.remove("xforms-disabled")
        else
          element.classList.add("xforms-disabled")
      }
    }

  @JSExport
  def setRepeatIterationRelevance(formID: String, repeatID: String, iteration: String, relevant: Boolean): Unit =
    FlatNesting.setRelevant(
      element  = org.orbeon.xforms.facade.Utils.findRepeatDelimiter(formID, repeatID, iteration.toInt),
      relevant = relevant
    )

  @JSExport
  def setReadonly(control: html.Element, isReadonly: Boolean): Unit = {

    if (isReadonly)
      control.classList.add("xforms-readonly")
    else
      control.classList.remove("xforms-readonly")

    if (control.hasClass("xforms-group-begin-end")) {
      // Case of group delimiters
      // Readonliness is not inherited by controls inside the group, so we are just updating the class on the begin-marker
    } else if (control.hasAnyClass("xforms-input", "xforms-secret")) {
      control.queryNestedElems[html.Input]("input", includeSelf = true)
        .foreach(setReadonlyOnFormElement(_, isReadonly))
    } else if (control.hasAnyClass("xforms-select1-appearance-full", "xforms-select-appearance-full")) {
      // Radio buttons or checkboxes
      // Update disabled on input fields
      // See:
      // - https://github.com/orbeon/orbeon-forms/issues/5595
      // - https://github.com/orbeon/orbeon-forms/issues/5427
      control.queryNestedElems[html.Input]("input", includeSelf = false)
        .foreach(setDisabledOnFormElement(_, isReadonly))
    } else if (
      control.hasAnyClass(
        "xforms-select-appearance-compact",
        "xforms-select1-appearance-minimal",
        "xforms-select1-appearance-compact"
      )) {
        control.queryNestedElems[html.Select]("select", includeSelf = false).headOption
          .foreach(setDisabledOnFormElement(_, isReadonly))
    } else if (control.hasAnyClass("xforms-output", "xforms-group")) {
      // NOP
    } else if (control.hasClass("xforms-upload")) {
      control.queryNestedElems[html.Element](".xforms-upload-select", includeSelf = false).headOption
        .foreach(setDisabledOnFormElement(_, isReadonly))
    } else if (control.hasClass("xforms-textarea")) {
      control.queryNestedElems[html.TextArea]("textarea", includeSelf = false).headOption
        .foreach(setReadonlyOnFormElement(_, isReadonly))
    } else if (control.hasAnyClass("xforms-trigger", "xforms-submit")) {
      control.queryNestedElems[html.Button]("button", includeSelf = false).headOption
        .foreach(setDisabledOnFormElement(_, isReadonly))
    }
  }

  @JSExport
  def getCurrentValue(control: html.Element): js.UndefOr[String] =
    if ((
      control.hasClass("xforms-input") && ! control.hasAnyClass("xforms-type-boolean", "xforms-static")) ||
      control.hasClass("xforms-secret")
    ) {
      // Simple input
      control.queryNestedElems[html.Input]("input", includeSelf = true).head.value
    } else if (
      control.hasAnyClass(
        "xforms-select-appearance-full",
        "xforms-select1-appearance-full",
        "xforms-type-boolean"
      ) ||
        control.hasAllClasses("xforms-input", "xforms-type-boolean")
    ) {
      // Checkboxes, radio buttons, boolean input
      val spanValue =
        control.queryNestedElems[html.Input]("input", includeSelf = false)
          .filter(_.checked)
          .map(_.value)
          .mkString(" ")

      // For boolean inputs, if the checkbox isn't checked, then the value is false
      if (spanValue.isEmpty && control.hasAllClasses("xforms-input", "xforms-type-boolean"))
        false.toString
      else
        spanValue
    } else if (
      control.hasAnyClass(
        "xforms-select-appearance-compact",
        "xforms-select1-appearance-minimal",
        "xforms-select1-appearance-compact"
      )
    ) {
      // Drop-down and list
      val selectOpt = control.queryNestedElems[html.Select]("select", includeSelf = false).headOption
      selectOpt match {
        case Some(select) =>
          val selectedValues =
            for {
              option <- select.options
              if option.selected
            } yield
              option.value
          selectedValues.mkString(" ")
        case None => ""
      }
    } else if (control.hasClass("xforms-textarea") && ! control.hasClass("xforms-static")) {
      // Text area
      control.queryNestedElems[html.TextArea]("textarea", includeSelf = true).head.value
    } else if (
      control.hasClass("xforms-output")                      ||
      control.hasAllClasses("xforms-input", "xforms-static") ||
      control.hasAllClasses("xforms-textarea", "xforms-static")
    ) {
      // Output and static input
      control.querySelectorOpt(".xforms-output-output, .xforms-field") match {
        case Some(output: html.Image) if control.hasClass("xforms-mediatype-image")        => output.src
        case Some(output: html.Video) if control.hasClass("xforms-mediatype-video")        => output.childrenT.head.getAttribute("src")
        case Some(_)      if control.hasClass("xforms-output-appearance-xxforms-download") => null
        case Some(output) if control.hasClass("xforms-mediatype-text-html")                => output.innerHTML
        case Some(output)                                                                  => output.textContent
        case None                                                                          => js.undefined
      }
    } else if (XFormsXbl.isComponent(control)) {
      val instance = XBL.instanceForControl(control) // can return `null` but should not be undefined
      if (! js.isUndefined(instance) && instance != null && js.typeOf(instance.asInstanceOf[js.Dynamic].xformsGetValue) == "function")
        instance.xformsGetValue()
      else
        js.undefined
    } else {
      js.undefined
    }

  @JSExport
  def handleShiftSelection(clickEvent: dom.MouseEvent, controlElem: html.Element): Unit =
    if (controlElem.hasClass("xforms-select-appearance-full")) {
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
              case (lastControlElem, lastCheckboxElem) <- lastCheckboxChecked
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
      .ancestorOrSelfElem(s".xforms-disable-$lhha-as-tooltip, .xforms-enable-$lhha-as-tooltip")
      .nextOption()
      .exists(e => e.hasClass(s"xforms-disable-$lhha-as-tooltip"))

  def mouseover(event: MouseEvent): Unit =
    event.targetOpt.foreach { target =>

      val controlOpt = Option(Events._findParentXFormsControl(target))

      if (target.hasAllClasses("xforms-alert", "xforms-active")) {
        // Alert tooltip

        // NOTE: control may be `null` if we have `<div for="">`. Using `control.getAttribute("for")` returns a proper
        // for, but then tooltips sometimes fail later with Ajax portlets in particular. So for now, just don't
        // do anything if there is no control found.
        Option(XFormsUI.getControlForLHHA(target, "alert")).foreach { formField =>
          if (! isTooltipDisabled(target, "alert")) {
            // The 'for' typically points to a form field which is inside the element representing the control
            Option(Events._findParentXFormsControl(formField)).foreach { control2 =>
              val message = Controls.getAlertMessage(control2)
              Events._showToolTip(Globals.alertTooltipForControl, control2, target, "-orbeon-alert-tooltip", message, event)
            }
          }
        }
      } else if (target.hasClass("xforms-help")) {
        // Help tooltip
        controlOpt.foreach { control =>
          if (Page.getXFormsFormFromHtmlElemOrThrow(control).helpTooltip()) {
            val message = XFormsUI.getHelpMessage(control)
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
            if (control.hasAnyClass("xforms-trigger", "xforms-submit"))
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
    dom.document.body.hasClass("xforms-ios")

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
        val supportsClose      = dialogElem.hasClass("xforms-dialog-close-true")
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
        elem.classList.remove("is-active")
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
