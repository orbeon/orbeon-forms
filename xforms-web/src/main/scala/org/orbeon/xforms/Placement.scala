package org.orbeon.xforms

import org.orbeon.jquery.Offset
import org.scalajs.dom
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel, JSGlobal}


// TODO: Remove this once Hint.js and Help.js have been migrated too.
@JSExportTopLevel("OrbeonPlacement")
object Placement {

  val RequiredSpaceHorizontal = 420
  val RequiredSpaceVertical   = 300

  trait TopLeftOffset extends js.Object {
    val top, left: Double
  }

  trait Margins extends js.Object {
    val top, right, bottom, left: Double
  }

  trait Placement extends js.Object {
    val width, height, scrollTop: Double
    val offset: TopLeftOffset
    val margins: Margins
  }

  /**
   * For the element, returns an object with the following properties:
   *      width, height, scrollTop                // Equivalent to jQuery functions
   *      offset: { top, left }                   // Position relative to the document
   *      margins: { top, right, bottom, left }   // In in a scrollable area (e.g. FB), space around that area
   */
  @JSExport
  def getPosition(el: JQuery): Placement = {

    def getElPosition = {
      val o = Offset(el)
      (
        el.outerWidth(),
        el.outerHeight(),
        $(dom.document).scrollTop(), // Will this work if we"re in a scrollable area?
        new TopLeftOffset {
          val top  = o.top
          val left = o.left
        },
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

    new Placement {
      val width     = widthV
      val height    = heightV
      val scrollTop = scrollTopV
      val offset    = offsetV
      val margins   =
        new Margins {
          val top    = topV
          val right  = rightV
          val bottom = bottomV
          val left   = leftV
        }
    }
  }

  /**
   * Figure where we want to place the popover: right, left, top, bottom, or over
   */
  @JSExport
  def getPlacement(elPos: Placement): String = {

    val left   = elPos.offset.left
    val right  = $(dom.window).width() - (elPos.offset.left + elPos.width)
    val top    = elPos.offset.top - elPos.scrollTop
    val bottom = $(dom.window).height() - (elPos.offset.top - elPos.scrollTop + elPos.height)

    if (right >= RequiredSpaceHorizontal || left >= RequiredSpaceHorizontal) {
      // If space to the left and right are the same (e.g. title with wide page), display to the left, which
      // will be closer to the text of the title
      if (right > left)
        "right"
      else
        "left"
    } else if (top >= RequiredSpaceVertical || bottom >= RequiredSpaceVertical) {
      if (top >= bottom)
        "top"
      else
        "bottom"
    } else {
      "over"
    }
  }
}
