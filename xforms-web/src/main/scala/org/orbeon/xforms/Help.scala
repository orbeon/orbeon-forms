package org.orbeon.xforms

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.Bootstrap
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.util.chaining.scalaUtilChainingOps


object Help {

  import Private.*

  dom.document.addEventListener("keyup", handleKeyUp _)

  /**
   * We're asked to show the help popover for a control, either because the user clicked on the help icon,
   * or because the server asks us to do so.
   */
  def showHelp(controlEl: html.Element): Unit = {

    val labelText = XFormsUI.getLabelMessage(controlEl)
    val helpText  = XFormsUI.getHelpMessage(controlEl)

    def explicitContainerWithClassOpt: Option[html.Element] = {
      val explicitClassSelector = ".xforms-help-popover-control"
      controlEl.matches(explicitClassSelector).option(controlEl)
        .orElse(controlEl.querySelectorOpt(explicitClassSelector))
    }

    def fieldsCommonAncestorOpt: Option[html.Element] =
      DomSupport.findCommonAncestor(
        controlEl.queryNestedElems[html.Element]("input, textarea, select, button, output")
          .filter(_.isVisible)
          .filterNot(_.classList.contains("xforms-help")) // exclude help button
          .toList
      )

    def labelElementOpt: Option[html.Element] =
      XFormsUI.findControlLHHA(controlEl, "label")

    // We want the arrow to point to the form field, not somewhere between the label and the field,
    // hence here we look for the first element which is not an LHHA. If we don't find any such element
    // we use the container as a fallback (e.g. `xf:group` that only contains the help and a label).
    val containerElem =
      explicitContainerWithClassOpt
        .orElse(fieldsCommonAncestorOpt)
        // If the container has no visible dimensions, point to the label element.
        .flatMap(c => if (c.isVisible) Some(c) else labelElementOpt)
        .getOrElse(controlEl)

    val elPos     = Placement.getPositionDetails(containerElem)
    val placement = Placement.getPlacement(elPos)

    // Bootstrap links the trigger to its tip via aria-describedby
    val popoverAlreadyShown =
      Option(containerElem.getAttribute("aria-describedby"))
        .flatMap(id => Option(dom.document.getElementById(id)))
        .exists(_.classList.contains("xforms-help-popover"))

    // Hide other help popovers before (maybe) showing this one
    hideAllHelpPopovers()

    // We take users asking to show the popover when already shown as an order to hide it
    if (! popoverAlreadyShown) {

      // [1] Using animation unnecessarily complicates things, by creating cases where we have two popovers
      //     in the DOM, when one is being hidden while the other is being shown, so we just disable animations.
      val popoverConfig = js.Dynamic.literal(
        placement = placement.entryName,
        trigger   = "manual",
        title     = labelText,
        content   = helpText,
        html      = true,
        animation = false // [1]
      )
      // Anchor to containerElem: Popper centers the arrow on it, and controlEl would include the LHHA
      val popoverHandle = Bootstrap.newPopover(containerElem, popoverConfig)
      popoverHandle.show()

      // Decorate the popover, but only if one was actually shown: Bootstrap shows nothing for a control with no help
      // content. Positioning is left to Bootstrap (Popper).
      popoverHandle.tipElementOpt.map(_.asInstanceOf[html.Element]).foreach { popover =>
        popover.classList.add("xforms-help-popover")
        addCloseButtonIfNeeded(containerElem, popover)
      }
    }
  }

  private object Private {

    // Hide help when user presses the escape key
    def handleKeyUp(e: dom.Event): js.Any = {
      val keyboardEvent = e.asInstanceOf[dom.KeyboardEvent]
      if (keyboardEvent.keyCode == 27)
        hideAllHelpPopovers()
    }

    def hideAllHelpPopovers(): Unit =
      // Document-wide (not scoped to the form), as Bootstrap appends the popover to <body>.
      dom.document.querySelectorAllT(".xforms-help-popover").foreach(destroyPopoverForTip)

    // Dispose the popover a help tip belongs to: Bootstrap links the control to the tip via aria-describedby (the tip's
    // id).
    def destroyPopoverForTip(popoverElem: html.Element): Unit =
      Option(popoverElem.id)
        .filter(_.nonEmpty)
        .flatMap(id => Option(dom.document.querySelector(s"[aria-describedby='$id']")))
        .foreach(controlEl => Bootstrap.getPopover(controlEl).foreach(_.destroy()))

    /**
     * Adds an "x" at the top right of the popover, so users can close it with a click
     */
    def addCloseButtonIfNeeded(controlElem: html.Element, popoverElem: html.Element): Unit =
      if (popoverElem.childrenT(".close").isEmpty)
        popoverElem.prepend(
          dom.document.createButtonElement
            .tap(_.`type` = "button")
            .tap(_.classList.add("close"))
            .tap(_.dataset += "dismiss" -> "modal")
            .tap(_.setAttribute("aria-hidden", "true"))
            .tap(_.textContent = "×")
            .tap(
              _.addEventListener(
                "click",
                (_: dom.Event) => Bootstrap.getPopover(controlElem).foreach(_.destroy())
              )
            )
        )
  }
}
