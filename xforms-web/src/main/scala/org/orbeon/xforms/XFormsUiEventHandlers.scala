package org.orbeon.xforms

import cats.implicits.*
import org.orbeon.oxf.util.CollectionUtils.fromIteratorExt
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsUI.ClassNameToId
import org.scalajs.dom
import org.scalajs.dom.html.Element
import org.scalajs.dom.{KeyboardEvent, MouseEvent, UIEvent, html}

import scala.scalajs.js


object XFormsUiEventHandlers {

  def input(event: UIEvent): Unit =
    if (XFormsUI.modalProgressPanelShown)
      event.preventDefault()
    else
      XFormsUiEvents.findParentXFormsControl(event.target).foreach { target =>

        XFormsUI.fieldValueChanged(target)

        // Incremental control: treat keypress as a value change event
        if (target.hasClass("xforms-incremental"))
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName   = EventNames.XXFormsValue,
              targetId    = target.id,
              properties  = Map("value" -> XFormsUI.getCurrentValue(target)), // Q: What if `getCurrentValue` is undefined?
              incremental = true
            )
          )
      }

  def change(event: UIEvent): Unit =
    XFormsUiEvents.findParentXFormsControl(event.target).foreach { target =>
      if (target.hasAllClasses("xbl-component", "xbl-javascript-lifecycle")) {
        // We might exclude *all* changes under `.xbl-component` but for now, to be conservative, we
        // exclude those that support the JavaScript lifecycle.
        // https://github.com/orbeon/orbeon-forms/issues/4169
      } else if (target.hasClass("xforms-upload")) {
        // Dispatch change event to upload control
        Page.getUploadControl(target).change()
      } else {

        if (target.hasClass("xforms-select1-appearance-compact")) {
          // For select1 list, make sure we have exactly one value selected
          val select = target.queryNestedElems[html.Select]("select").head
          if (select.value == "") {
            // Stop end-user from deselecting last selected value
            select.options.head.selected = true
          } else {
            // Deselect options other than the first one
            var foundSelected = false
            for (option <- select.options)
              if (option.selected) {
                if (foundSelected)
                  option.selected = false
                else
                  foundSelected = true
              }
          }
        }

        if (! target.hasClass("xforms-static") && (
            target.hasAnyClass("xforms-select1-appearance-full", "xforms-select-appearance-full") ||
            target.hasAllClasses("xforms-input", "xforms-type-boolean")
          )) {
          // Update classes right away to give user visual feedback
          XFormsUI.setRadioCheckboxClasses(target)
        }

        // Fire change event if the control has a value
        dispatchValueChange(target)
      }
    }

  def dispatchValueChange(control: html.Element): Unit =
    XFormsUI.getCurrentValue(control).foreach { controlCurrentValue =>
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName  = EventNames.XXFormsValue,
          targetId   = control.id,
          properties = Map("value" -> controlCurrentValue)
        )
      )
    }

  private def isFocusableControl(elem: html.Element) =
    ! elem.hasAnyClass("xforms-group", "xforms-repeat", "xforms-switch", "xforms-case", "xforms-dialog") &&
    ! (elem.hasClass("xbl-component") && ! elem.hasClass("xbl-focusable"))

  private def findAncestorFocusableControl(target: dom.EventTarget): Option[html.Element] =
    XFormsUiEvents.findParentXFormsControl(target).filter(isFocusableControl)

  def focus(event: dom.FocusEvent): Unit =
    if (XFormsUI.modalProgressPanelShown) {
      event.preventDefault()
    } else if (! Globals.maskFocusEvents) {
      findAncestorFocusableControl(event.target).foreach { newFocusControlElement =>

        val currentFocusControlElementOpt =
          Option(Globals.currentFocusControlId)
            .flatMap(dom.document.getElementByIdOpt)

        // 2023-01-12: Don't do this for static readonly controls.
        // Store initial value of control if we don't have a server value already, and if this is not a list
        // Initial value for lists is set up initialization, as when we receive the focus event the new value is already set.
        if (! newFocusControlElement.hasClass("xforms-static")         &&
            ServerValueStore.getOpt(newFocusControlElement.id).isEmpty &&
            ! newFocusControlElement.hasAnyClass(
              "xforms-select-appearance-compact",
              "xforms-select1-appearance-compact"
            )
        ) {
          XFormsUI.getCurrentValue(newFocusControlElement).foreach { controlCurrentValue =>
            ServerValueStore.set(newFocusControlElement.id, controlCurrentValue)
          }
        }

        // The idea here is that we only register focus changes when focus moves between XForms controls. If focus
        // goes out to nothing, we don't handle it at this point but wait until focus comes back to a control.
        if (! currentFocusControlElementOpt.contains(newFocusControlElement)) {
          // Keep track of the id of the last known control which has focus
          Globals.currentFocusControlId      = newFocusControlElement.id
          Globals.currentFocusControlElement = newFocusControlElement

          // Fire events
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName   = EventNames.XFormsFocus,
              targetId    = newFocusControlElement.id,
              incremental = true
            )
          )
        }
      }
    } else {
      Globals.maskFocusEvents = false
    }

  // blurEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
  def blur(event: dom.FocusEvent): Unit =
    if (XFormsUI.modalProgressPanelShown) {
      event.preventDefault()
    } else if (! Globals.maskFocusEvents) {
      findAncestorFocusableControl(event.target).foreach { control =>

        Globals.currentFocusControlId      = control.id
        Globals.currentFocusControlElement = control

        // Dispatch `xxforms-blur` event if we're not going to another XForms control (see issue #619)
        val relatedTarget = Option(event.relatedTarget).getOrElse(dom.document.activeElement)
        if (findAncestorFocusableControl(relatedTarget).isEmpty) {
          Globals.currentFocusControlId      = null
          Globals.currentFocusControlElement = null
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName = EventNames.XXFormsBlur,
              targetId  = control.id
            )
          )
        }
      }
    }

//  // TODO: 2020-06-04: Only used by the IE 11 `preventDefault()` below.
//  //   2022-06-14: Unclear, check again!
//  keydownEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
  def keydown(event: KeyboardEvent): Unit =
    if (XFormsUI.modalProgressPanelShown)
      event.preventDefault()

//  //TODO: MDN: "Since this event has been deprecated, you should look to use beforeinput or keydown instead."
//  keypressEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
  def keypress(event: KeyboardEvent): Unit =
    if (XFormsUI.modalProgressPanelShown) {
      event.preventDefault()
    } else {
      val target = event.target.asInstanceOf[html.Element]
      XFormsUiEvents.findParentXFormsControl(target).foreach { control =>
        // Input field and auto-complete: trigger DOMActive when when enter is pressed
        val isXFormsInputField = (control.hasClass("xforms-input") && ! control.hasClass("xforms-type-boolean")) ||
                                  control.hasClass("xforms-secret")
        val isFocusInOnInput   = target.tagName.toLowerCase == "input"

        if (isXFormsInputField && isFocusInOnInput) {
          if (event.keyCode == 10 || event.keyCode == 13) {
            // Force a change event if the value has changed, creating a new "change point", which the
            // browser will use to dispatch a `change` event in the future. Also see issue #1207.
            target.blur()
            target.focus()

            // Send a value change and DOM activate
            dispatchValueChange(control)
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName = "DOMActivate",
                targetId  = control.id
              )
            )

            // This prevents Chrome/Firefox from dispatching a 'change' event on event, making them more
            // like IE, which in this case is more compliant to the spec.
            event.preventDefault()
          }
        }
      }
    }

  // clickEvent: new YAHOO.util.CustomEvent(null, null, false, YAHOO.util.CustomEvent.FLAT),
  def click(event: MouseEvent): Unit = {

    if (XFormsUI.modalProgressPanelShown) {
      event.preventDefault()
      return
    }

    // Stop processing if the mouse button that was clicked is not the left button
    // See: http://www.quirksmode.org/js/events_properties.html#button
    if (event.button != 0 && event.button != 1)
      return

    val originalTarget = event.target.asInstanceOf[html.Element]
    val controlTargetOpt = XFormsUiEvents.findParentXFormsControl(event.target)

    // Check if the target is disabled.
    if (originalTarget.hasAttribute("disabled") && originalTarget.getAttribute("disabled") != "false")
      return

    var handled = false

    controlTargetOpt.foreach { controlTarget =>
      if (originalTarget != null && originalTarget.hasClass("xforms-help")) {
        if (Page.getXFormsFormFromHtmlElemOrThrow(controlTarget).helpHandler)
          AjaxClient.fireEvent(AjaxEvent(eventName = "xforms-help", targetId = controlTarget.id))
        else
          Help.showHelp(controlTarget)
        handled = true
      } else {
        if (controlTarget.hasAnyClass("xforms-trigger", "xforms-submit")) {
          // Click on trigger
          event.preventDefault()
          if (! controlTarget.hasClass("xforms-readonly")) {
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName = "DOMActivate",
                targetId  = controlTarget.id
              )
            )

            if (controlTarget.closest(".xforms-trigger-appearance-modal, .xforms-submit-appearance-modal") != null)
              XFormsUI.displayModalProgressPanel()
            handled = true
          }
        } else if (
          ! controlTarget.hasClass("xforms-static") &&
          controlTarget.hasAnyClass(
            "xforms-select1-appearance-full",
            "xforms-select-appearance-full",
            "xforms-type-boolean"
          )
        ) {
          // Update classes right away to give user visual feedback
          XFormsUI.handleShiftSelection(event, controlTarget)
          handled = true
        } else if (controlTarget.hasClass("xforms-upload") && originalTarget.hasClass("xforms-upload-remove")) {
          // Click on remove icon in upload control
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName  = EventNames.XXFormsValue,
              targetId   = controlTarget.id,
              properties = Map("value" -> "")
            )
          )
          handled = true
        }
      }
    }

    if (handled)
      return

    var node: dom.Node = originalTarget

    // Iterate on ancestors, stop when we don't find ancestors anymore or we arrive at the form element
    while (node != null && ! (node.isInstanceOf[html.Element] && node.asInstanceOf[html.Element].tagName.toLowerCase == "form")) {

      // First check clickable group
      node match {
        case nodeElem: Element if nodeElem.hasClass("xforms-activable") =>
          val form = Page.getAncestorOrSelfHtmlFormFromHtmlElemOrThrow(nodeElem)
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName = EventNames.DOMActivate,
              targetId  = nodeElem.id,
              form      = Some(form)
            )
          )
          // break from loop
          node = null
        case _ =>

          // Iterate on previous siblings
          var delimiterCount = 0
          var foundRepeatBegin = false
          var sibling: dom.Node = node

          while (sibling != null) {
            sibling match {
              case siblingElem: Element if siblingElem.id.startsWith("repeat-begin-") =>

                val form = Page.getAncestorOrSelfHtmlFormFromHtmlElemOrThrow(siblingElem)

                var targetId = siblingElem.id.substring("repeat-begin-".length)
                targetId += (
                  if (targetId.indexOf(Constants.RepeatSeparatorString) == -1)
                    Constants.RepeatSeparatorString
                  else
                    Constants.RepeatIndexSeparator
                  )
                targetId += delimiterCount
                AjaxClient.fireEvent(
                  AjaxEvent(
                    eventName = EventNames.XXFormsRepeatActivate,
                    targetId = targetId,
                    form = Some(form)
                  )
                )
                foundRepeatBegin = true
                sibling = null // break

              case siblingElem: Element if siblingElem.hasClass("xforms-repeat-delimiter") =>
                delimiterCount += 1
                sibling = siblingElem.previousSibling
              case _ =>
                sibling = sibling.previousSibling
            }
          }

          // We found what we were looking for, no need to go to parents
          if (foundRepeatBegin)
            node = null // break from outer loop
          else
            // Explore parent
            node = node.parentNode
      }
    }
  }

  private def getControlForLHHA(element: html.Element, lhhaType: String): Option[html.Element] = {
    val suffix = ClassNameToId(lhhaType)
    if (element.hasClass("xforms-control"))
      element.some
    else if (element.id.contains(suffix))
      dom.document.getElementByIdOpt(element.id.replace(suffix, ""))
    else
      element.parentElement.some
  }

  private def isTooltipDisabled(elem: html.Element, lhha: String) =
    elem
      .ancestorOrSelfElem(s".xforms-disable-$lhha-as-tooltip, .xforms-enable-$lhha-as-tooltip")
      .nextOption()
      .exists(e => e.hasClass(s"xforms-disable-$lhha-as-tooltip"))

  def mouseover(event: MouseEvent): Unit =
    event.targetOpt.foreach { target =>

      val controlOpt = XFormsUiEvents.findParentXFormsControl(target)

      if (target.hasAllClasses("xforms-alert", "xforms-active"))
        // Alert tooltip
        // NOTE: control may be `null` if we have `<div for="">`. Using `control.getAttribute("for")` returns a proper
        // for, but then tooltips sometimes fail later with Ajax portlets in particular. So for now, just don't
        // do anything if there is no control found.
        getControlForLHHA(target, "alert").foreach { formField =>
          if (! isTooltipDisabled(target, "alert")) {
            // The 'for' typically points to a form field which is inside the element representing the control
            XFormsUiEvents.findParentXFormsControl(formField).foreach { control2 =>
              val message = XFormsUI.getAlertMessage(control2)
              XFormsUiEvents.showToolTip(Globals.alertTooltipForControl, control2, target, "-orbeon-alert-tooltip", message, event)
            }
          }
        }
      else if (target.hasClass("xforms-help"))
        // Help tooltip
        controlOpt.foreach { control =>
          if (Page.getXFormsFormFromHtmlElemOrThrow(control).helpTooltip)
            XFormsUiEvents.showToolTip(
              tooltipForControl = Globals.helpTooltipForControl,
              control           = control,
              target            = target,
              toolTipSuffix     = "-orbeon-help-tooltip",
              message           = XFormsUI.getHelpMessage(control),
              event             = event
            )
        }
      else
        // Hint tooltip
        controlOpt.foreach { control =>

          // Only show hint if the mouse is over a child of the control, so we don't show both the hint and the alert or help
          if (! isTooltipDisabled(control, "hint") && control != target) {

            // Find closest ancestor-or-self control with a non-empty hint, for compound control like the datetime
            val candidateMessage =
              Iterator
                .iterateOpt(control)(c => XFormsUiEvents.findParentXFormsControl(c.parentElement))
                .collectFirst {
                  case c if XFormsUI.findNonAllBlankHintMessage(c).isDefined => XFormsUI.getNonAllBlankHintMessage(c)
                }
                .getOrElse("")

            // Clear any `title`, to avoid having both the YUI tooltip and the browser tooltip based on the title showing up
            if (control.hasAnyClass("xforms-trigger", "xforms-submit"))
              control.querySelectorAllT("a, button").foreach(_.title = "")

            XFormsUiEvents.showToolTip(
              tooltipForControl = Globals.hintTooltipForControl,
              control           = control,
              target            = target,
              toolTipSuffix     = "-orbeon-hint-tooltip",
              message           = candidateMessage,
              event             = event
            )
          }
        }
    }

  def mouseout(event: MouseEvent): Unit =
    event.targetOpt.foreach { target =>
      XFormsUiEvents.findParentXFormsControl(target).foreach { control =>
        // Send the `mouseout` event to the YUI tooltip to handle the case where: (1) we get the `mouseover` event, (2) we
        // create a YUI tooltip, (3) the `mouseout` happens before the YUI dialog got a chance to register its listener
        // on `mouseout`, (4) the YUI dialog is only dismissed after `autodismissdelay` (5 seconds) leaving a trail.
        Globals.hintTooltipForControl.get(control.id).foreach { yuiTooltip =>
          if (! isTooltipDisabled(control, "hint"))
            yuiTooltip.asInstanceOf[js.Dynamic].onContextMouseOut.call(control.id, event, yuiTooltip)
        }
      }
    }
}
