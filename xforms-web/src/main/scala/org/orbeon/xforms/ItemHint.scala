package org.orbeon.xforms

import org.orbeon.jquery.*
import org.scalajs.dom
import io.udash.wrappers.jquery.{JQuery, JQueryEvent}
import org.orbeon.web.DomSupport.*
import org.scalajs.dom.html

import scala.scalajs.js


object ItemHint {

  /**
   * Show, update, init, or destroy the tooltip on mouseover on a hint region
   *
   * [1] In Form Builder, the tooltip is absolutely positioned inside a div.fb-hover that gets inserted inside the
   *     cell, and which is position: relative. Thus, if we don't have a width on the tooltip, the browser tries
   *     to set its width to the tooltip doesn't "come out" of the div.fb-hover, which makes it extremely narrow
   *     since the tooltip is shown all the way to the right of the cell. To avoid this, if we detect that situation,
   *     we set the container to be the parent of the div.fb-hover (which is the td).
   */
  dom.document.addEventListener("mouseover", (event: dom.Event) =>
    event.targetT
      .closestOpt(".xforms-form .xforms-items .xforms-hint-region")
      .foreach { hintRegionEl =>

      val jHintRegionEl    = $(hintRegionEl)
      val jHintRegionElDyn = jHintRegionEl.asInstanceOf[js.Dynamic]

      // `UndefOr` to try to avoid occasional error with fastOptJS but that doesn't seem to work; `.html()` is meant to
      // return a `String` but occasionally returns `undefined`, and that's probably the main problem. Should be ok
      // with `fullOptJS`.
      val hintHtml: js.UndefOr[String] = jHintRegionEl.nextAll(".xforms-hint").html()

      val tooltipData        = jHintRegionEl.data("tooltip").map(_.asInstanceOf[js.Dynamic]).orNull
      val haveHint           = hintHtml.exists(_.nonEmpty)
      val tooltipInitialized = tooltipData != null

      // Compute placement, and don't use "over" since tooltips don"t support it
      val placement: js.Function = () => {
        val p = Placement.getPlacement(Placement.getPositionDetails(jHintRegionEl))
        if (p == Placement.Over) "bottom" else p.entryName
      }

      (haveHint, tooltipInitialized) match {
        case (true, true) =>
          // If already initialized:
          // - Update the message (it might have changed, e.g. if the language changed).
          // - Update the placement (it might have changed, e.g. the optimal placement might go from "bottom"
          //   to "top" when the user scrolls down and the control becomes closer to the top of the viewport).
          //   Also, we need to call `show()`, as the Bootstrap tooltip code gets the even before us, and otherwise
          //   has it already has shown the tooltip without using the updated placement.
          tooltipData.options.title = hintHtml
          tooltipData.options.placement = placement
          jHintRegionElDyn.tooltip("show")
        case (true, false) =>
          // Avoid super-narrow tooltip in Form Builder [1]
          val containerEl =
            hintRegionEl.closestOpt(".fb-hover") match {
              case Some(parentFbHover) => parentFbHover.parentElement
              case None                => hintRegionEl
            }

          // Create tooltip and show right away
          jHintRegionElDyn.tooltip(js.Dynamic.literal(
            title     = hintHtml,
            html      = true,
            animation = false,
            placement = placement,
            container = $(containerEl)
          ))
          jHintRegionElDyn.on("shown", (_ => shiftTooltipLeft(containerEl, jHintRegionEl)): js.Function1[JQueryEvent, Unit])
          jHintRegionElDyn.tooltip("show")
        case (false, true) =>
          // We had a tooltip, but we don't have anything for show anymore
          jHintRegionElDyn.tooltip("destroy")
        case (false, false) =>
        // NOP if not initialized and we don't have a tooltip
      }
    }
  )

  /**
   * Fixup position of tooltip element to be to the left of the checkbox/radio. Without this fixup, the tooltip is
   * shown to the left of the hint region, so it shows over the checkbox/radio.
   */
  private def shiftTooltipLeft(containerEl: html.Element, hintRegionEl: JQuery): Unit = {
    containerEl.children.find(_.matches(".tooltip.left")).foreach { tooltipEl =>
      val jTooltipEl = $(tooltipEl)
      val offset = Offset(jTooltipEl)
      // Add 5px spacing between arrow and checkbox/radio
      Offset.offset(jTooltipEl, offset.copy(left = Offset(hintRegionEl.parent()).left - jTooltipEl.outerWidth().getOrElse(0d) - 5))
    }
  }
}
