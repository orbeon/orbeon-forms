package org.orbeon.xforms

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.Bootstrap
import org.scalajs.dom

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

      // `UndefOr` to try to avoid occasional error with fastOptJS but that doesn't seem to work; `.html()` is meant to
      // return a `String` but occasionally returns `undefined`, and that's probably the main problem. Should be ok
      // with `fullOptJS`.
      val hintHtml: js.UndefOr[String] = hintRegionEl.nextElementSiblings(".xforms-hint").nextOption().get.outerHTML

      val existingTooltipOpt = Bootstrap.getTooltip(hintRegionEl)
      val haveHint           = hintHtml.exists(_.nonEmpty)

      // Compute placement, and don't use "over" since tooltips don't support it. As this is a function, it is
      // re-evaluated on each show (Popper), so the placement stays optimal (e.g. flips from "bottom" to "top" as the
      // control nears the top of the viewport when scrolling).
      val placement: js.Function = () => {
        val p = Placement.getPlacement(Placement.getPositionDetails(hintRegionEl))
        if (p == Placement.Over) "bottom" else p.entryName
      }

      (haveHint, existingTooltipOpt) match {
        case (true, Some(tooltip)) =>
          // Already initialized: update the message (it might have changed, e.g. if the language changed), then re-show
          // (the placement function is re-evaluated on show).
          tooltip.updateTitle(hintHtml)
          tooltip.show()
        case (true, None) =>
          val containerEl =
            hintRegionEl.closestOpt(".fb-hover") match {
              case Some(parentFbHover) => parentFbHover.parentElement // Avoid super-narrow tooltip in Form Builder
              case None                => hintRegionEl.parentElement  // Parent for `mouseleave` to trigger when over tooltip
            }

          // Create tooltip and show right away
          val tooltip = Bootstrap.newTooltip(hintRegionEl, js.Dynamic.literal(
            title     = hintHtml,
            html      = true,
            animation = false,
            placement = placement,
            container = containerEl
          ))
          tooltip.show()
        case (false, Some(tooltip)) =>
          // We had a tooltip, but we don't have anything to show anymore
          tooltip.destroy()
        case (false, None) =>
        // NOP if not initialized and we don't have a tooltip
      }
    }
  )
}
