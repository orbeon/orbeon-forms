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
import org.orbeon.facades.{Bowser, HTMLDialogElement}
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBL
import org.scalajs.dom
import org.scalajs.dom.html.Element
import org.scalajs.dom.{DocumentReadyState, html}
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

  val ClassNameToId: Map[String, String] = Map(
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
    _setTooltipMessage(control, escapedMessage, Globals.helpTooltipForControl)
  }

  @JSExport
  def getAlertMessage(control: html.Element): String =
    findControlLHHA(control, "alert").map(_.innerHTML).getOrElse("")

  @JSExport
  def setAlertMessage(control: html.Element, message: String): Unit = {
    setMessage(control, "alert", message)
    _setTooltipMessage(control, message, Globals.alertTooltipForControl)
  }

  @JSExport
  def getNonAllBlankHintMessage(control: html.Element): String =
    findNonAllBlankHintMessage(control).getOrElse("")

  def findNonAllBlankHintMessage(control: html.Element): Option[String] =
    if (control.hasAnyClass("xforms-trigger", "xforms-submit"))
      control
        .queryNestedElems[html.Element]("button, a")
        .headOption
        .map(_.title)
        .flatMap(_.trimAllToOpt)
    else
      findControlLHHA(control, "hint")
        .map(_.innerHTML)
        .flatMap(_.trimAllToOpt)

  @JSExport
  def setHintMessage(control: html.Element, message: String): Unit = {
    val tooltips = Globals.hintTooltipForControl
    tooltips.get(control.id).foreach { tooltipDyn =>
      if (tooltipDyn != null && ! js.isUndefined(tooltipDyn)) {
        val tooltip = tooltipDyn.asInstanceOf[js.Dynamic]
        if (tooltip.cfg.getProperty("context").asInstanceOf[js.Array[html.Element]](0) != control)
          tooltips.put(control.id, null)
      }
    }
    if (control.hasAnyClass("xforms-trigger", "xforms-submit")) {
      val tooltipDyn = tooltips.get(control.id).orNull
      if (tooltipDyn == null || js.isUndefined(tooltipDyn)) {
        control
          .queryNestedElems[html.Element]("button, a")
          .headOption
          .foreach(_.title = message)
      }
    } else {
      setMessage(control, "hint", message)
    }
    _setTooltipMessage(control, message, tooltips)
  }

  @JSExport
  def _setTooltipMessage(control: html.Element, message: String, tooltipForControl: js.Dictionary[js.Any]): Unit =
    tooltipForControl.get(control.id).foreach { currentTooltipDyn =>
      if (currentTooltipDyn != null && ! js.isUndefined(currentTooltipDyn) && currentTooltipDyn.toString != "true") {
        val currentTooltip = currentTooltipDyn.asInstanceOf[js.Dynamic]
        if (message == "") {
          currentTooltip.cfg.setProperty("disabled", true)
        } else {
          currentTooltip.cfg.setProperty("text", message)
          currentTooltip.cfg.setProperty("disabled", false)
        }
      }
    }

  @JSExport
  def updateVisited(control: html.Element, newVisited: Boolean): Unit = {
    if (newVisited)
      control.classList.add("xforms-visited")
    else
      control.classList.remove("xforms-visited")
    findControlLHHA(control, "alert").foreach { alertElement =>
      if (alertElement.id.nonAllBlank) {
        if (newVisited)
          alertElement.classList.add("xforms-visited")
        else
          alertElement.classList.remove("xforms-visited")
      }
    }
  }

  @JSExport
  def updateRequiredEmpty(control: html.Element, emptyAttr: String): Unit = {
    val isRequired = control.hasClass("xforms-required")
    if (isRequired && emptyAttr == "true")  control.classList.add("xforms-empty")  else control.classList.remove("xforms-empty")
    if (isRequired && emptyAttr == "false") control.classList.add("xforms-filled") else control.classList.remove("xforms-filled")
  }

  @JSExport
  def toggleCase(controlId: String, visible: Boolean): Unit = {

    val caseBeginId = s"xforms-case-begin-$controlId"
    val caseEndId   = s"xforms-case-end-$controlId"

    def updateClasses(el: html.Element): Unit =
      if (visible) {
        el.classList.add("xforms-case-selected")
        el.classList.remove("xforms-case-deselected")
      } else {
        el.classList.add("xforms-case-deselected")
        el.classList.remove("xforms-case-selected")
      }

    val caseBegin = dom.document.getElementByIdT(caseBeginId)

    (Iterator(caseBegin) ++ caseBegin.nextElementSiblings.takeWhile(_.id != caseEndId))
      .foreach { el =>
        if (el.id != caseBeginId && el.hasClass("xxforms-animate")) {
          if (visible) {
            updateClasses(el)
            el.asInstanceOf[js.Dynamic].animate(
              js.Dynamic.literal(height = "show"),
              js.Dynamic.literal(duration = 200)
            )
          } else {
            el.asInstanceOf[js.Dynamic].animate(
              js.Dynamic.literal(height = "hide"),
              js.Dynamic.literal(duration = 200, complete = (() => updateClasses(el)): js.Function0[Unit])
            )
          }
        } else {
          updateClasses(el)
        }
      }
  }

  @JSExport
  def setFocus(controlId: String): Unit = {

    // Wait until `load` event to set focus, as dialog in control the control is present might not be visible until then
    // And on mobile, don't set the focus on page load, as a heuristic to avoid showing the soft keyboard on page load
    if (dom.document.readyState != DocumentReadyState.complete) {
      if (! Bowser.mobile.contains(true))
        dom.window.addEventListener("load", (_: dom.Event) => setFocus(controlId))
      return
    }

    // Don't bother focusing if the control is already focused. This also prevents issues with `maskFocusEvents`,
    // whereby `maskFocusEvents` could be set to `true` below, but then not cleared back to false if no focus event
    // is actually dispatched.
    if (Globals.currentFocusControlId == controlId)
      return

    val control = dom.document.getElementByIdT(controlId)

    Globals.currentFocusControlId      = controlId
    Globals.currentFocusControlElement = control
    Globals.maskFocusEvents            = true

    if (
      control.hasAnyClass("xforms-select-appearance-full", "xforms-select1-appearance-full") ||
      control.hasAllClasses("xforms-input", "xforms-type-boolean")
    ) {
      val nestedInputs = control.queryNestedElems[html.Input]("input")
      nestedInputs
        .find(_.checked)
        .orElse(nestedInputs.headOption)
        .foreach(_.focus())
    } else if (XFormsXbl.isFocusable(control)) {
      val instance = XBL.instanceForControl(control)
      if (instance != null && ! js.isUndefined(instance)) {
        val dyn = instance.asInstanceOf[js.Dynamic]
        if (XFormsXbl.isObjectWithMethod(instance, "xformsFocus"))
          dyn.xformsFocus()
        else if (XFormsXbl.isObjectWithMethod(instance, "setFocus"))
          dyn.setFocus()
      }
    } else {

      def isVisible(elem: html.Element): Boolean =
        elem.offsetWidth != 0 || elem.offsetHeight != 0 || elem.getClientRects().length != 0

      def fromVisibleHtmlFormField: Option[html.Element] =
        control.queryNestedElems[html.Element](
          "input, textarea, select, button:not(.xforms-help), a"
        ).find(isVisible)

      def fromVisibleTabindex: Option[Element] =
        control.queryNestedElems[html.Element](
          "[tabindex]:not([tabindex = '-1'])"
        ).find(isVisible)

      fromVisibleHtmlFormField.orElse(fromVisibleTabindex) match {
        case Some(elem) => elem.focus()
        case None       => Globals.maskFocusEvents = false // don't mask the focus event since we won't receive it
      }
    }

    // 2023-01-12: Don't do this for static readonly controls.
    // Save current value as server value. We usually do this on focus, but for control where we set the focus
    // with `xf:setfocus`, we still receive the focus event when the value changes, but after the change event
    // (which means we then don't send the new value to the server).
    if (! control.classList.contains("xforms-static") && ServerValueStore.getOpt(controlId).isEmpty)
      ServerValueStore.set(controlId, getCurrentValue(control))
  }

  @JSExport
  def removeFocus(controlId: String): Unit = {

    if (Globals.currentFocusControlId == null)
      return

    val control = dom.document.getElementByIdT(controlId)

    if (
      control.hasAnyClass("xforms-select-appearance-full", "xforms-select1-appearance-full") ||
      control.hasAllClasses("xforms-input", "xforms-type-boolean")
    ) {
      control
        .queryNestedElems[html.Input]("input")
        .foreach(_.blur())
    } else if (XFormsXbl.isFocusable(control)) {
      val instance = XBL.instanceForControl(control)
      if (instance != null && ! js.isUndefined(instance) && XFormsXbl.isObjectWithMethod(instance, "xformsBlur"))
        instance.asInstanceOf[js.Dynamic].xformsBlur()
    } else {
      control
        .queryNestedElems[html.Element]("input textarea select button a")
        .headOption
        .foreach(_.blur())
    }

    Globals.currentFocusControlId      = null
    Globals.currentFocusControlElement = null
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
      XFormsUiFlatNesting.setRelevant(control, isRelevant)
    } else {

      val elementsToUpdate =
        control ::
        findControlLHHA(control, "label").toList :::
        findControlLHHA(control, "alert").toList :::
        (! isRelevant || (isRelevant && getHelpMessage(control).nonAllBlank))
          .flatList(findControlLHHA(control, "help").toList) :::
        (! isRelevant || (isRelevant && findNonAllBlankHintMessage(control).isDefined))
          .flatList(findControlLHHA(control, "hint").toList)

      elementsToUpdate.foreach { element =>
        if (isRelevant)
          element.classList.remove("xforms-disabled")
        else
          element.classList.add("xforms-disabled")
      }
    }

  def findRepeatDelimiter(formId: String, repeatId: String, iteration: Int): html.Element = {
    val form                  = Page.getXFormsFormFromNamespacedIdOrThrow(formId)
    var parentRepeatIndexes   = ""
    var currentId             = repeatId
    var continue              = true
    while (continue) {
      form.repeatTreeChildToParent.get(currentId) match {
        case None         => continue = false
        case Some(parent) =>
          val separator =
            if (form.repeatTreeChildToParent.get(parent).isEmpty)
              Constants.RepeatSeparatorString
            else
              Constants.RepeatIndexSeparatorString
          parentRepeatIndexes = separator + form.repeatIndexes.getOrElse(parent, 0) + parentRepeatIndexes
          currentId = parent
      }
    }
    var cursor: dom.Node = dom.document.getElementById(s"repeat-begin-$repeatId$parentRepeatIndexes")
    if (cursor eq null) return null
    var cursorPosition = 0
    var done = false
    while (!done) {
      while (!(cursor.isInstanceOf[html.Element] && cursor.asInstanceOf[html.Element].hasClass("xforms-repeat-delimiter"))) {
        cursor = cursor.nextSibling
        if (cursor eq null)
          return null
      }
      cursorPosition += 1
      if (cursorPosition == iteration)
        done = true
      else {
        cursor = cursor.nextSibling
        if (cursor eq null)
          return null
      }
    }
    cursor.asInstanceOf[html.Element]
  }

  @JSExport
  def setRepeatIterationRelevance(formID: String, repeatID: String, iteration: String, relevant: Boolean): Unit =
    XFormsUiFlatNesting.setRelevant(
      node       = findRepeatDelimiter(formID, repeatID, iteration.toInt),
      isRelevant = relevant
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
          removeFocus(focusControlId)
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
        focusControlIdOpt foreach setFocus
      }
  }
}
