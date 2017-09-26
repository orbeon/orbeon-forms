/**
 * Copyright (C) 2017 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.builder

import org.orbeon.builder.BlockCache.Block
import org.orbeon.xforms._
import org.orbeon.xforms.facade.Events
import org.scalajs.dom.{document, window}
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel, ScalaJSDefined}

@JSExportTopLevel("ORBEON.builder.Position")
@JSExportAll
object Position {

  // Keeps track of pointer position
  var pointerPos: Offset = new Offset {
    override val left: Double = 0
    override val top : Double = 0
  }

  $(document).on("mousemove", (event: JQueryEventObject) ⇒ {
    pointerPos =
      new Offset {
        override val left: Double = event.pageX
        override val top : Double = event.pageY
      }
  })

  // How much we need to add to offset to account for the form having been scrolled
  def scrollTop() : Double  = $(".fb-main").scrollTop ()
  def scrollLeft(): Double  = $(".fb-main").scrollLeft()

  @ScalaJSDefined
  class Offset extends js.Object {
    val left   : Double = 0
    val top    : Double = 0
  }

  // Typed version of JQuery's offset()
  def offset(el: JQuery, offset: Offset): Unit = el.offset(offset)
  def offset(el: JQuery): Offset = {
    val jOffset = el.offset().asInstanceOf[js.Dynamic]
    new Offset {
      override val left = jOffset.left.asInstanceOf[Double]
      override val top  = jOffset.top .asInstanceOf[Double]
    }
  }

  // Gets an element offset, normalizing for scrolling, so the offset can be stored in a cache
  def adjustedOffset(el: JQuery): Offset = {
    val rawOffset = offset(el)
    new Offset {
      override val left = rawOffset.left
      override val top  = rawOffset.top + scrollTop()
    }
  }

  // Calls listener when what is under the pointer has potentially changed
  def onUnderPointerChange(fn: ⇒ Unit): Unit = {
    $(document).on("mousemove", fn _)
    // Resizing the window might change what is under the pointer the last time we saw it in the window
    $(window).on("resize", fn _)
    Events.ajaxResponseProcessedEvent.subscribe(fn _)
  }

  // Call listener when anything on the page that could change element positions happened
  def onOffsetMayHaveChanged(fn: js.Function): Unit = {
      // After the form is first shown
      Events.orbeonLoadedEvent.subscribe(fn)
      // After an Ajax response, as it might have changed the DOM
      Events.ajaxResponseProcessedEvent.subscribe(fn)
      $(window).on("resize", fn)
  }

  // Finds the container, if any, based on a vertical position
  def findInCache(
    containerCache : js.Array[Block],
    top            : Double,
    left           : Double
  )                : js.UndefOr[Block] = {

    containerCache.find { container ⇒
      // Rounding when comparing as the offset of an element isn't always exactly the same as the offset it was set to
      val horizontalPosInside = Math.floor(container.left) <= left && left <= Math.ceil(container.left + container.width)
      val verticalPosInside   = Math.floor(container.top ) <= top  && top  <= Math.ceil(container.top  + container.height)
      horizontalPosInside && verticalPosInside
    }.orUndefined
  }

  // Container is either a section or grid; calls listeners passing old/new container
  def currentContainerChanged[T](
    containerCache : js.Array[Block],
    wasCurrent     : js.Function1[Block, T],
    becomesCurrent : js.Function1[Block, T])
                   : Unit = {

    val notifyChange = notifyOnChange(wasCurrent, becomesCurrent)
    onUnderPointerChange {
      val top  = pointerPos.top  + Position.scrollTop()
      val left = pointerPos.left + Position.scrollLeft()
      val newContainer = findInCache(containerCache, top, left)
      notifyChange(newContainer)
    }
  }

  // Calls listeners when, in a grid, the pointer moves out of or in a new row/cell
  def currentRowColChanged[T](
    gridsCache        : js.Array[Block],
    wasCurrentRow     : js.Function1[Block, T],
    becomesCurrentRow : js.Function1[Block, T],
    wasCurrentCol     : js.Function1[Block, T],
    becomesCurrentCol : js.Function1[Block, T])
                      : Unit = {

    var currentGridOpt: js.UndefOr[Block] = js.undefined
    currentContainerChanged(
      gridsCache,
      wasCurrent     = (g: Block) ⇒ currentGridOpt = js.undefined,
      becomesCurrent = (g: Block) ⇒ currentGridOpt = g
    )

    val notifyRowChange = Position.notifyOnChange(wasCurrentRow, becomesCurrentRow)
    val notifyColChange = Position.notifyOnChange(wasCurrentCol, becomesCurrentCol)

    Position.onUnderPointerChange {

      val (newRow, newCol) =
        currentGridOpt
          .map((currentGrid) ⇒
            {
              val newRow =
                currentGrid.rows.toList.find((r) ⇒ {
                  val pointerTop = pointerPos.top + Position.scrollTop()
                  r.top  <= pointerTop && pointerTop <= r.top + r.height
                })
              val newCol =
                currentGrid.cols.toList.find((c) ⇒
                  c.left <= pointerPos.left && pointerPos.left <= c.left + c.width
                )
              (newRow, newCol)
            })
          .getOrElse((None, None))

      notifyRowChange(newRow.orUndefined)
      notifyColChange(newCol.orUndefined)
    }
  }

  // Returns a function, which is expected to be called every time the value changes passing the new value, and which
  // will when appropriate notify the listeners `was` and `becomes` of the old and new value
  // TODO: replace `Any` by `Unit` once callers are all in Scala
  def notifyOnChange[T](
    was     : js.Function1[Block, T],
    becomes : js.Function1[Block, T])
            : js.Function1[js.UndefOr[Block], Any] = {

    var currentValueOpt: js.UndefOr[Block] = js.undefined

    (newValueOpt: js.UndefOr[Block]) ⇒ {
      newValueOpt.toOption match {
        case Some(newValue) ⇒
          val notify =
            currentValueOpt.toOption match {
              case None ⇒ true
              case Some(currentValue) ⇒
                // Typically after an Ajax request, maybe a column/row was added/removed, so we might consequently
                // need to update the icon position
                ! newValue.el.is(currentValue.el) ||
                // The elements could be the same, but their position could have changed, in which case want to
                // reposition relative icons, so we don't consider the value to be the "same"
                newValue.left != currentValue.left ||
                newValue.top != currentValue.top
            }
          if (notify) {
            currentValueOpt.toOption.foreach(was)
            currentValueOpt = newValueOpt
            becomes(newValue)
          }
        case None ⇒
          currentValueOpt.toOption.foreach(was)
          currentValueOpt = js.undefined
      }
    }
  }

}
