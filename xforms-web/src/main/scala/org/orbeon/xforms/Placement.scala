package org.orbeon.xforms

import org.orbeon.jquery.Offset
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.scalajs.js


sealed trait Placement { val entryName: String }

object Placement {

  val RequiredSpaceHorizontal = 420
  val RequiredSpaceVertical   = 300

  case object Top    extends { val entryName = "top"    } with Placement
  case object Right  extends { val entryName = "right"  } with Placement
  case object Bottom extends { val entryName = "bottom" } with Placement
  case object Left   extends { val entryName = "left"   } with Placement
  case object Over   extends { val entryName = "over"   } with Placement

  case class Margins(
    top    : Double,
    right  : Double,
    bottom : Double,
    left   : Double
  )

  case class PositionDetails(
    width     : Double,
    height    : Double,
    scrollTop : Double,
    offset    : Offset,
    margins   : Margins
  )

  /**
   * For the element, returns an object with the following properties:
   *      width, height, scrollTop                // Equivalent to jQuery functions
   *      offset: { top, left }                   // Position relative to the document
   *      margins: { top, right, bottom, left }   // In in a scrollable area (e.g. FB), space around that area
   */
  def getPositionDetails(el: JQuery): PositionDetails = {

    def getElPosition = {
      val o = Offset(el)
      (
        el.outerWidth(),
        el.outerHeight(),
        $(dom.document).scrollTop(), // Will this work if we"re in a scrollable area?
        Offset(
          left = o.left,
          top  = o.top
        ),
      )
    }

    val (widthV, heightV, scrollTopV, offsetV) =
      if (el.is(":hidden")) {
        val originalStyle = el.attr("style")
        el.css("display", "inline-block")

        val r = getElPosition

        if (js.isUndefined(originalStyle))
          el.removeAttr("style")
        else
          el.attr("style", originalStyle)

        r
      } else {
        getElPosition
      }

    val autoOverflowElemOpt =
      Iterator.iterate(el(0))(_.parentElement).takeWhile(_ ne null).find { e =>
        dom.window.getComputedStyle(e).overflow == "auto"
      }

    val (topV, rightV, bottomV, leftV) =
      autoOverflowElemOpt match {
        case None =>
          (0.0, 0.0, 0.0, 0.0)
        case Some(autoOverflowElem) =>

          val jAutoOverflowElem = $(autoOverflowElem)

          val overflowOffset = Offset(jAutoOverflowElem)
          val overflowWidth  = jAutoOverflowElem.outerWidth()
          val overflowHeight = jAutoOverflowElem.outerHeight()

          (
            overflowOffset.top,
            $(dom.window).width()  - overflowOffset.left - overflowWidth,
            $(dom.window).height() - overflowOffset.top  - overflowHeight,
            overflowOffset.left
          )
      }

    PositionDetails(
      width     = widthV,
      height    = heightV,
      scrollTop = scrollTopV,
      offset    = offsetV,
      margins   =
        Margins(
          top    = topV,
          right  = rightV,
          bottom = bottomV,
          left   = leftV
        )
    )
  }

  /**
   * Figure where we want to place the popover: right, left, top, bottom, or over
   */
  def getPlacement(pos: PositionDetails): Placement = {

    val left   = pos.offset.left
    val right  = $(dom.window).width() - (pos.offset.left + pos.width)
    val top    = pos.offset.top - pos.scrollTop
    val bottom = $(dom.window).height() - (pos.offset.top - pos.scrollTop + pos.height)

    if (right >= RequiredSpaceHorizontal || left >= RequiredSpaceHorizontal) {
      // If space to the left and right are the same (e.g. title with wide page), display to the left, which
      // will be closer to the text of the title
      if (right > left)
        Placement.Right
      else
        Placement.Left
    } else if (top >= RequiredSpaceVertical || bottom >= RequiredSpaceVertical) {
      if (top >= bottom)
        Placement.Top
      else
        Placement.Bottom
    } else {
      Placement.Over
    }
  }
}
