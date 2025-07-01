package org.orbeon.xforms

import org.orbeon.jquery.Offset
import org.scalajs.dom
import io.udash.wrappers.jquery.JQuery
import org.orbeon.web.DomSupport.DomElemOps

import scala.scalajs.js


sealed trait Placement { val entryName: String }

object Placement {

  val RequiredSpaceHorizontal = 420
  val RequiredSpaceVertical   = 300

  case object Top    extends Placement { val entryName = "top"    }
  case object Right  extends Placement { val entryName = "right"  }
  case object Bottom extends Placement { val entryName = "bottom" }
  case object Left   extends Placement { val entryName = "left"   }
  case object Over   extends Placement { val entryName = "over"   }

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
   *      margins: { top, right, bottom, left }   // In a scrollable area (e.g. FB), space around that area
   */
  def getPositionDetails(el: JQuery): PositionDetails = {

    def getElPosition = {
      val o = Offset(el)
      (
        el.outerWidth().getOrElse(0d),
        el.outerHeight().getOrElse(0d),
        $(dom.document).scrollTop(), // Will this work if we"re in a scrollable area?
        Offset(
          left = o.left,
          top  = o.top
        ),
      )
    }

    val (widthV, heightV, scrollTopV, offsetV) =
      if (el.is(":hidden")) {
        val originalStyleOpt = el.attr("style")
        el.css("display", "inline-block")

        val r = getElPosition

        originalStyleOpt match {
          case None => el.removeAttr("style")
          case Some(originalStyle) => el.attr("style", originalStyle)

        }

        r
      } else {
        getElPosition
      }

    val autoOverflowElemOpt =
      el.get(0).get.ancestorOrSelfElem.find { e =>
        dom.window.getComputedStyle(e).overflow == "auto"
      }

    val (topV, rightV, bottomV, leftV) =
      autoOverflowElemOpt match {
        case None =>
          (0.0, 0.0, 0.0, 0.0)
        case Some(autoOverflowElem) =>

          val jAutoOverflowElem = $(autoOverflowElem)

          val overflowOffset = Offset(jAutoOverflowElem)
          val overflowWidth  = jAutoOverflowElem.outerWidth().getOrElse(0d)
          val overflowHeight = jAutoOverflowElem.outerHeight().getOrElse(0d)

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
