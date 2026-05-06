package org.orbeon.xforms

import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.DomElemOps
import org.scalajs.dom
import org.scalajs.dom.html


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
    offset    : DomSupport.Offset,
    margins   : Margins
  )

  /**
   * For the element, returns an object with the following properties:
   *      width, height, scrollTop                // Equivalent to jQuery functions
   *      offset: { top, left }                   // Position relative to the document
   *      margins: { top, right, bottom, left }   // In a scrollable area (e.g. FB), space around that area
   */
  def getPositionDetails(el: html.Element): PositionDetails = {

    def getElPosition: (Double, Double, Double, DomSupport.Offset) = {
      val o = el.getOffset
      (
        el.outerWidth,
        el.outerHeight,
        dom.window.pageYOffset, // Will this work if we're in a scrollable area?
        DomSupport.Offset(
          left = o.left,
          top  = o.top
        ),
      )
    }

    val (widthV, heightV, scrollTopV, offsetV) =
      if (! el.isVisible) {
        val originalDisplay = el.style.display
        el.style.display = "inline-block"

        val r = getElPosition
        el.style.display = originalDisplay
        r
      } else {
        getElPosition
      }

    val autoOverflowElemOpt =
      el.ancestorOrSelfElem().find { e =>
        dom.window.getComputedStyle(e).overflow == "auto"
      }

    val (topV, rightV, bottomV, leftV) =
      autoOverflowElemOpt match {
        case None =>
          (0.0, 0.0, 0.0, 0.0)
        case Some(autoOverflowElem) =>

          val overflowOffset = autoOverflowElem.getOffset
          val overflowWidth  = autoOverflowElem.outerWidth
          val overflowHeight = autoOverflowElem.outerHeight

          (
            overflowOffset.top,
            dom.document.documentElement.clientWidth  - overflowOffset.left - overflowWidth,
            dom.document.documentElement.clientHeight - overflowOffset.top  - overflowHeight,
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
    val right  = dom.document.documentElement.clientWidth - (pos.offset.left + pos.width)
    val top    = pos.offset.top - pos.scrollTop
    val bottom = dom.document.documentElement.clientHeight - (pos.offset.top - pos.scrollTop + pos.height)

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
