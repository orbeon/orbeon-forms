package org.orbeon.xforms

import org.orbeon.jquery.Offset
import org.orbeon.oxf.util.CollectionUtils.IteratorWrapper
import org.orbeon.xforms.Placement.PositionDetails
import org.orbeon.xforms.facade.Controls
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.KeyboardEvent
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.annotation.tailrec
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonHelp")
object Help {

  import Private._

  $(dom.document).keyup(handleKeyUp: js.Function1[JQueryEventObject, js.Any])

  /**
   * We're asked to show the help popover for a control, either because the user clicked on the help icon,
   * or because the server asks us to do so.
   */
  @JSExport
  def showHelp(controlEl: html.Element): Unit = {

    val jControlEl    = $(controlEl)
    val jControlElDyn = jControlEl.asInstanceOf[js.Dynamic]

    val labelText = Controls.getLabelMessage(controlEl)
    val helpText  = Controls.getHelpMessage(controlEl)

    val declaredContainerOpt =
      if (controlEl.classList.contains("xforms-help-popover-control"))
        Some(controlEl)
      else
        Option(controlEl.querySelector(".xforms-help-popover-control"))

    // We want the arrow to point to the form field, not somewhere between the label and the field,
    // hence here we look for the first element which is not an LHHA. If we don't find any such element
    // we use the container as a fallback (e.g. `xf:group` that only contains the help and a label).
    val containerOpt =
      declaredContainerOpt
        .orElse(jCommonAncestor(jControlEl.find(":input:visible")))
        .getOrElse(controlEl)

    val elPos     = Placement.getPositionDetails($(containerOpt))
    val placement = Placement.getPlacement(elPos)

    val popoverAlreadyShown = jControlEl.next().is(".xforms-help-popover")

    // Hide other help popovers before (maybe) showing this one
    hideAllHelpPopovers()

    // We take users asking to show the popover when already shown as an order to hide it
    if (! popoverAlreadyShown) {

      // For top placement, popover must be above the label
      val newElPos =
        if (placement == Placement.Top)
          Placement.getPositionDetails(jControlEl)
        else
          elPos

      // [1] Using animation unnecessarily complicates things, by creating cases where we have two popovers
      //     in the DOM, when one is being hidden while the other is being shown, so we just disable animations.
      jControlElDyn.popover(js.Dynamic.literal(
        placement = placement.entryName,
        trigger   = "manual",
        title     = labelText,
        content   = helpText,
        html      = true,
        animation = false // [1]
      )).popover("show")

      // Decorate an position popover
      val popover = $(controlEl).next()
      popover.addClass("xforms-help-popover")
      addClose(jControlEl, popover)
      positionPopover(popover, placement, newElPos)
    }
  }

  private object Private {

    // Hide help when user presses the escape key
    def handleKeyUp(e: JQueryEventObject): js.Any = {
      val keyboardEvent = e.asInstanceOf[KeyboardEvent]
      if (keyboardEvent.keyCode == 27) {
        println("xxx esc")
        hideAllHelpPopovers()
      }
    }

    def hideAllHelpPopovers(): Unit =
      $("form.xforms-form .xforms-help-popover").toArray() foreach { popover =>
        $(popover).prev().asInstanceOf[js.Dynamic].popover("destroy")
      }

    /**
     * Adds an "x" at the top right of the popover, so users can close it with a click
     */
    def addClose(jControlEl: JQuery, jPopover: JQuery): Unit =
      if (! jPopover.children(".close").is("*")) {
        val close = $("""<button type="button" class="close" data-dismiss="modal" aria-hidden="true">×</button>""")
        jPopover.prepend(close)
        close.on(
          "click",
          ((_: JQueryEventObject) =>
            jControlEl.asInstanceOf[js.Dynamic].popover("destroy")): js.Function1[JQueryEventObject, js.Any]
        )
      }

    /**
     * We re-implement the positioning and sizing done by Bootstrap. Instead of indirectly positioning
     * the popover by setting CSS properties on its container, we let Bootstrap give its best shot at
     * positioning and sizing, and we then adjust what Bootstrap did. Bootstrap code is in:
     * https://github.com/twbs/bootstrap/blob/v2.3.2/js/bootstrap-tooltip.js
     */
    def positionPopover(popover: JQuery, placement: Placement, elPos: PositionDetails): Unit = {

      // [1] It is unclear to me why we need to add the arrow width, as it should be counted in the width
      //     of the popover, which has a margin to reserve space for the arrow.
      val padding     = 2 // space left between popover and document border
      val arrowWidth  = popover.children(".arrow").outerWidth() // [1]
      val arrowHeight = popover.children(".arrow").outerHeight()

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
            $(dom.window).height() - 2 * padding
          case Placement.Bottom =>
            // Space below
            $(dom.window).height() - (elPos.offset.top - elPos.scrollTop + elPos.height + arrowHeight + padding)
          case Placement.Top =>
            // Space above
            elPos.offset.top - elPos.scrollTop - padding - arrowHeight
        }

      if (popover.outerHeight() > maxHeight) {
        val title = popover.children(".popover-title")
        val content = popover.children(".popover-content")
        popover.css("height", maxHeight + "px")
        content.css("height", (maxHeight - title.outerHeight()) + "px")
      }

      // Adjust position
      val newPopoverOffset = {

        val popoverOffset = Offset(popover)

        val newPopoverOffsetTop = {
          placement match {
            case Placement.Right | Placement.Left =>
              // Similar to the way Bootstrap would position the popover
              val bootstrapTop = elPos.offset.top + (elPos.height - popover.outerHeight()) / 2
              // Top of the popover "against" the top of the viewport (modulo margin and padding)
              val topOfViewportTop = elPos.margins.top + elPos.scrollTop + padding
              Math.max(bootstrapTop, topOfViewportTop)
            case Placement.Top =>
              elPos.offset.top - popover.outerHeight() - arrowHeight
            case Placement.Over =>
              // Center relative to viewport
              Math.max(0, (($(dom.window).height() - popover.outerHeight()) / 2) + $(dom.window).scrollTop())
            case Placement.Bottom =>
              popoverOffset.top
          }
        }

        val newPopoverOffsetLeft = {
          placement match {
            case Placement.Right =>
              elPos.offset.left + elPos.width + arrowWidth
            case Placement.Left =>
              elPos.offset.left - popover.outerWidth() - arrowWidth
            case Placement.Top | Placement.Bottom =>
              elPos.offset.left
            case Placement.Over =>
              // Center relative to viewport
              Math.max(0, (($(dom.window).width() - popover.outerWidth()) / 2) + $(dom.window).scrollLeft())
          }
        }

        Offset(newPopoverOffsetLeft, newPopoverOffsetTop)
      }

      Offset.offset(popover, newPopoverOffset)

      // Adjust arrow height for right/left
      placement match {
        case Placement.Right | Placement.Left =>
          println(s"xxx adjust arrow right/left")
          val controlTopDoc = elPos.offset.top + elPos.height / 2
          val controlTopPopover = controlTopDoc - newPopoverOffset.top
          val arrowTop = (controlTopPopover / popover.outerHeight()) * 100
          popover.children(".arrow").css("top", arrowTop + "%")
        case Placement.Top | Placement.Bottom =>
          println(s"xxx adjust arrow top/bottom")
          popover.children(".arrow").css("left", "10%")
          // Smaller max-width to avoid popover having no padding on the right, especially for mobile
          val altMaxWidth = $(dom.window).width() - 2 * newPopoverOffset.left
          if (popover.width() > altMaxWidth)
            popover.css("max-width", altMaxWidth + "px")
        case _ =>
          println(s"xxx no adjust arrow over")
      }
    }

    def ancestorOrSelfElem(elem: html.Element): Iterator[html.Element] =
      Iterator.iterate(elem)(_.parentElement).takeWhile(_ ne null)

    def jCommonAncestor(jElems: JQuery): Option[html.Element] =
      if (jElems.length >= 2)
        findCommonAncestor(jElems.toArray().toList.collect { case e: html.Element => e })
      else
        None

    // TODO: Move to common DOM utilities.
    def findCommonAncestor(elems: List[html.Element]): Option[html.Element] = {

      def commonAncestorForPair(elem1: html.Element, elem2: html.Element): Option[html.Element] =
        ancestorOrSelfElem(elem1).toList.reverseIterator
          .zip(ancestorOrSelfElem(elem2).toList.reverseIterator)
          .takeWhile { case (e1, e2) => e1.isSameNode(e2) }
          .map(_._1)
          .lastOption()

      @tailrec
      def recurse(elems: List[html.Element]): Option[html.Element] = {
        elems match {
          case Nil =>
            None
          case elem1 :: Nil =>
            Some(elem1)
          case elem1 :: elem2 :: rest =>
            commonAncestorForPair(elem1, elem2) match {
              case Some(elem) => recurse(elem :: rest)
              case None       => None
            }
          case _ =>
            None
        }
      }

      recurse(elems)
    }
  }
}
