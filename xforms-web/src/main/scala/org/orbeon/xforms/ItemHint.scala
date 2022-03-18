package org.orbeon.xforms

import org.orbeon.jquery._
import org.scalajs.dom
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js


object ItemHint {

  /**
   * Show, update, init, or destroy the tooltip on mouseover on a hint region
   *
   * [1] In Form Builder, the tooltip is absolutely positioned inside a div.fb-hover that gets inserted inside the
   *     the cell, and which is position: relative. Thus if we don't have a width on the tooltip, the browser tries
   *     to set its width to the tooltip doesn't "come out" of the div.fb-hover, which makes it extremely narrow
   *     since the tooltip is shown all the way to the right of the cell. To avoid this, if we detect that situation,
   *     we set the container to be the parent of the div.fb-hover (which is the td).
   */
  $(dom.document).onWithSelector(
    "mouseover",
    ".xforms-form .xforms-items .xforms-hint-region",
    (ev: JQueryEventObject) => {

      val hintRegionEl                 = $(ev.target)
      val hintHtml: js.UndefOr[String] = hintRegionEl.nextAll(".xforms-hint").html() // `UndefOr` to try to avoid occasional error with fastOptJS but doesn't seem to work
      val tooltipData                  = hintRegionEl.data("tooltip")
      val haveHint                     = hintHtml.exists(_.nonEmpty)
      val tooltipInitialized           = ! js.isUndefined(tooltipData)

      // Compute placement, and don"t use "over" since tooltips don"t support it
      val placement: js.Function = () => {
        val p = Placement.getPlacement(Placement.getPosition(hintRegionEl))
        if (p == "over") "bottom" else p
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
          hintRegionEl.asInstanceOf[js.Dynamic].tooltip("show")
        case (true, false) =>
          // Avoid super-narrow tooltip in Form Builder [1]
          val containerEl = {
            val parentFbHover = hintRegionEl.closest(".fb-hover");
            if (parentFbHover.is("*")) parentFbHover.parent() else hintRegionEl
          }

          // Create tooltip and show right away
          hintRegionEl.asInstanceOf[js.Dynamic].tooltip(js.Dynamic.literal(
            title     = hintHtml,
            html      = true,
            animation = false,
            placement = placement,
            container = containerEl
          ))
          hintRegionEl.on("shown", shiftTooltipLeft(containerEl, hintRegionEl): js.Function1[JQueryEventObject, Unit])
          hintRegionEl.asInstanceOf[js.Dynamic].tooltip("show")
        case (false, true) =>
          // We had a tooltip, but we don"t have anything for show anymore
          hintRegionEl.asInstanceOf[js.Dynamic].tooltip("destroy")
        case (false, false) =>
        // NOP if not initialized and we don"t have a tooltip
      }
    }
  )

  /**
   * Fixup position of tooltip element to be to the left of the checkbox/radio. Without this fixup, the tooltip is
   * shown to the left of the hint region, so it shows over the checkbox/radio.
   */
  private def shiftTooltipLeft(containerEl: JQuery, hintRegionEl: JQuery)(ev: JQueryEventObject): Unit = {
    val tooltipEl = containerEl.children(".tooltip")
    if (tooltipEl.is(".left")) {
      val offset = Offset(tooltipEl)
      // Add 5px spacing between arrow and checkbox/radio
      Offset.offset(tooltipEl, offset.copy(left = Offset(hintRegionEl.parent()).left - tooltipEl.outerWidth() - 5))
    }
  }
}
