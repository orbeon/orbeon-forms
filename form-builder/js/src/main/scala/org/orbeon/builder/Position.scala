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

import org.orbeon.datatypes.Orientation
import org.orbeon.facades.ResizeObserver
import org.orbeon.jquery.Offset
import org.orbeon.oxf.util.CoreUtils.asUnit
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.AjaxClient.AjaxResponseDetails
import org.orbeon.xforms._
import org.orbeon.xforms.facade.Events
import org.scalajs.dom.{document, window}
import org.scalajs.jquery.{JQuery, JQueryEventObject}

import scala.scalajs.js
import scala.util.Try

object Position {

  // Keeps track of pointer position
  var pointerPos: Offset = Offset(0, 0)

  $(document).on("mousemove.orbeon.builder", (event: JQueryEventObject) => asUnit {
    pointerPos =
      Offset(
        left = event.pageX,
        top  = event.pageY
    )
  })

  // How much we need to add to offset to account for the form having been scrolled
  def scrollTop() : Double  = $(".fb-main").scrollTop ()
  def scrollLeft(): Double  = $(".fb-main").scrollLeft()

  // Gets an element offset, normalizing for scrolling, so the offset can be stored in a cache
  def adjustedOffset(el: JQuery): Offset = {
    val rawOffset = Offset(el)
    Offset(
      left = rawOffset.left + scrollLeft(),
      top  = rawOffset.top  + scrollTop()
    )
  }

  // Calls listener when what is under the pointer has potentially changed
  def onUnderPointerChange(fn: => Unit): Unit = {
    $(document).on("mousemove.orbeon.builder", fn _)
    // Resizing the window might change what is under the pointer the last time we saw it in the window
    $(window).on("resize.orbeon.builder", fn _)
    AjaxClient.ajaxResponseProcessed.add(_ => fn)
  }

  // Call listener when anything on the page that could change element positions happened
  def onOffsetMayHaveChanged(fn: () => Unit): Unit = {
    Events.orbeonLoadedEvent.subscribe(fn)
    AjaxClient.ajaxResponseProcessed.add(_ => fn())
    Events.componentChangedLayoutEvent.subscribe(fn)

    // Can be removed once we only support Safari 14, which implements the `ResizeObserver`
    $(window).on("resize.orbeon.builder", fn)

    // `ResizeObserver` catches window resizes, but also Form Builder being moved or resized by the embedding app
    if (ResizeObserver.isDefined) {
      Events.orbeonLoadedEvent.subscribe(() => {
        val resizeObserver = new ResizeObserver(fn)
        val fbMainOpt      = Option(document.querySelector(".fb-main"))
        fbMainOpt.foreach(resizeObserver.observe)
      })
    }
  }

  // Finds the container, if any, based on a vertical position
  def findInCache(
    containerCache : BlockCache,
    top            : Double,
    left           : Double
  ): Option[Block] =
    containerCache.elems find { container =>
      // Rounding when comparing as the offset of an element isn't always exactly the same as the offset it was set to
      val horizontalPosInside = Math.round(container.left) <= Math.round(left) &&
                                Math.round(left)           <= Math.round(container.left + container.width)
      val verticalPosInside   = Math.round(container.top ) <= Math.round(top)  &&
                                Math.round(top)            <= Math.round(container.top  + container.height)
      horizontalPosInside && verticalPosInside
    }

  // Container is either a section or grid; calls listeners passing old/new container
  def currentContainerChanged(
    containerCache : BlockCache,
    wasCurrent     : Block => Unit,
    becomesCurrent : Block => Unit
  ): Unit = {

    val notifyChange = notifyOnChange(wasCurrent, becomesCurrent)
    onUnderPointerChange {
      val top  = pointerPos.top  + Position.scrollTop()
      val left = pointerPos.left + Position.scrollLeft()
      val dialogVisible =
        Globals.dialogs.exists {
          case (_: String, yuiDialog: js.Dynamic) =>
            yuiDialog.cfg.config.visible.value.asInstanceOf[Boolean]
        }
      val newContainer =
        if (dialogVisible)
          // Ignore container under the pointer if a dialog is visible
          None
        else
          findInCache(containerCache, top, left)
      notifyChange(newContainer)
    }
  }

  // Returns a function, which is expected to be called every time the value changes passing the new value, and which
  // will when appropriate notify the listeners `was` and `becomes` of the old and new value
  // TODO: replace `Any` by `Unit` once callers are all in Scala
  def notifyOnChange[T](
    was     : Block => Unit,
    becomes : Block => Unit
  ): Option[Block] => Unit = {

    var currentBlockOpt: Option[Block] = None

    (newBlockOpt: Option[Block]) => {
      newBlockOpt match {
        case Some(newBlock) =>
          val doNotify =
            currentBlockOpt match {
              case None => true
              case Some(currentBlock) =>
                // Typically after an Ajax request, maybe a column/row was added/removed, so we might consequently
                // need to update the icon position
                ! newBlock.el.is(currentBlock.el) ||
                // The elements could be the same, but their position could have changed, in which case want to
                // reposition relative icons, so we don't consider the value to be the "same"
                newBlock.left != currentBlock.left ||
                newBlock.top != currentBlock.top
            }
          if (doNotify) {
            currentBlockOpt.foreach(was)
            currentBlockOpt = newBlockOpt
            becomes(newBlock)
          }
        case None =>
          currentBlockOpt.foreach(was)
          currentBlockOpt = None
      }
    }
  }

  // Get the height of each row track
  def tracksWidth(
    gridBody    : JQuery,
    orientation : Orientation
  ): List[Double] = {
    val cssProperty = orientation match {
      case Orientation.Horizontal => "grid-template-rows"
      case Orientation.Vertical   => "grid-template-columns"
    }
    val cssValue = gridBody.css(cssProperty)

    // In the value of the CSS property returned by the browser, replace `repeat(X Ypx)` by `X` times `Ypx`
    // Unlike other browsers, Edge 17 returns values that contains `repeat()`
    val repeatRegex      = "repeat\\(([0-9]+), ([0-9\\.]+px)\\)".r
    val cssValueExpanded = repeatRegex.replaceAllIn(cssValue,  m => {
      val count = m.group(1).toInt
      val value = m.group(2)
      (1 to count).map(_ => value).mkString(" ")
    })

    cssValueExpanded
      .splitTo[List]()
      .map(w => w.substring(0, w.indexOf("px")))
      .flatMap(v => Try(v.toDouble).toOption) // https://github.com/orbeon/orbeon-forms/issues/3700
  }
}
