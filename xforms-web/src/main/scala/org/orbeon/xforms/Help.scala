package org.orbeon.xforms

import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.Placement.PositionDetails
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

    val popoverAlreadyShown = controlEl.nextElementOpt.exists(_.matches(".xforms-help-popover"))

    // Hide other help popovers before (maybe) showing this one
    hideAllHelpPopovers()

    // We take users asking to show the popover when already shown as an order to hide it
    if (! popoverAlreadyShown) {

      // For top placement, popover must be above the label
      val newElPos =
        if (placement == Placement.Top)
          Placement.getPositionDetails(controlEl)
        else
          elPos

      // [1] Using animation unnecessarily complicates things, by creating cases where we have two popovers
      //     in the DOM, when one is being hidden while the other is being shown, so we just disable animations.
      $(controlEl).asInstanceOf[js.Dynamic].popover(js.Dynamic.literal(
        placement = placement.entryName,
        trigger   = "manual",
        title     = labelText,
        content   = helpText,
        html      = true,
        animation = false // [1]
      )).popover("show")

      // Decorate and position popover
      val popover = controlEl.nextElementOrThrow
      popover.classList.add("xforms-help-popover")
      addCloseButtonIfNeeded(controlEl, popover)
      positionPopover(popover, placement, newElPos)
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
      dom.document.querySelectorAllT("form.xforms-form .xforms-help-popover").foreach { popoverElem =>
        $(popoverElem.previousElementSibling).asInstanceOf[js.Dynamic].popover("destroy")
      }

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
                (_: dom.Event) => $(controlElem).asInstanceOf[js.Dynamic].popover("destroy")
              )
            )
        )

    /**
     * We re-implement the positioning and sizing done by Bootstrap. Instead of indirectly positioning
     * the popover by setting CSS properties on its container, we let Bootstrap give its best shot at
     * positioning and sizing, and we then adjust what Bootstrap did. Bootstrap code is in:
     * https://github.com/twbs/bootstrap/blob/v2.3.2/js/bootstrap-tooltip.js
     */
    def positionPopover(popover: html.Element, placement: Placement, elPos: PositionDetails): Unit = {

      // [1] It is unclear to me why we need to add the arrow width, as it should be counted in the width
      //     of the popover, which has a margin to reserve space for the arrow.
      val padding     = 2 // space left between popover and document border
      val arrowWidth  = popover.childrenT(".arrow").head.outerWidth
      val arrowHeight = popover.childrenT(".arrow").head.outerHeight

      // 2022-03-18: Not sure to what this comment applies.
      // [2] Bootstrap already positioned the popover mostly correctly, but since we can reduced its
      //     height, in case we do this might remove the need to a scrollbar, which changes the right offset
      //     of the control (e.g. when they are centered), and consequently the right offset of the popover
      //     also needs to be adjusted.

      // Constraint height if taller than viewport
      val maxHeight =
        placement match {
          case Placement.Right | Placement.Left | Placement.Over =>
            // Viewport height sets the limit
            dom.document.documentElement.clientHeight - 2 * padding
          case Placement.Bottom =>
            // Space below
            dom.document.documentElement.clientHeight - (elPos.offset.top - elPos.scrollTop + elPos.height + arrowHeight + padding)
          case Placement.Top =>
            // Space above
            elPos.offset.top - elPos.scrollTop - padding - arrowHeight
        }

      if (popover.outerHeight > maxHeight) {
        val title = popover.childrenT(".popover-title").head
        val content = popover.childrenT(".popover-content").head
        popover.setHeight(maxHeight)
        content.setHeight(maxHeight - title.outerHeight)
      }

      // Adjust position
      val newPopoverOffset = {

        val popoverOffset = popover.getOffset

        val newPopoverOffsetTop = {
          placement match {
            case Placement.Right | Placement.Left =>
              // Similar to the way Bootstrap would position the popover
              val bootstrapTop = elPos.offset.top + (elPos.height - popover.outerHeight) / 2
              // Top of the popover "against" the top of the viewport (modulo margin and padding)
              val topOfViewportTop = elPos.margins.top + elPos.scrollTop + padding
              Math.max(bootstrapTop, topOfViewportTop)
            case Placement.Top =>
              elPos.offset.top - popover.outerHeight - arrowHeight
            case Placement.Over =>
              // Center relative to viewport
              Math.max(0, ((dom.document.documentElement.clientHeight - popover.outerHeight) / 2) + dom.window.pageYOffset)
            case Placement.Bottom =>
              popoverOffset.top
          }
        }

        val newPopoverOffsetLeft = {
          placement match {
            case Placement.Right =>
              elPos.offset.left + elPos.width + arrowWidth
            case Placement.Left =>
              elPos.offset.left - popover.outerWidth - arrowWidth
            case Placement.Top | Placement.Bottom =>
              elPos.offset.left
            case Placement.Over =>
              // Center relative to viewport
              Math.max(0, ((dom.document.documentElement.clientWidth - popover.outerWidth) / 2) + dom.window.pageXOffset)
          }
        }

        DomSupport.Offset(newPopoverOffsetLeft, newPopoverOffsetTop)
      }

      popover.setOffset(newPopoverOffset)

      // Adjust arrow height for right/left
      placement match {
        case Placement.Right | Placement.Left =>
          val controlTopDoc = elPos.offset.top + elPos.height / 2
          val controlTopPopover = controlTopDoc - newPopoverOffset.top
          val arrowTop = (controlTopPopover / popover.outerHeight) * 100
          popover.childrenT(".arrow").foreach(_.style.top = s"$arrowTop%")
        case Placement.Top | Placement.Bottom | Placement.Over =>
          if (placement != Placement.Over)
            popover.childrenT(".arrow").foreach(_.style.left = "10%")
          val altMaxWidth =
            dom.document.documentElement.clientWidth -
            2 * (if (placement == Placement.Over) padding else newPopoverOffset.left)
          if (popover.contentWidthOrZero > altMaxWidth)
            popover.style.maxWidth = s"${altMaxWidth}px"
      }
    }
  }
}
