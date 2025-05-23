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

import io.udash.wrappers.jquery.JQuery
import org.orbeon.datatypes.Orientation
import org.orbeon.facades.ResizeObserver
import org.orbeon.jquery.Offset
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport
import org.orbeon.xforms.*
import org.orbeon.xforms.facade.Events
import org.scalajs.dom
import org.scalajs.dom.{DocumentReadyState, document, html}

object Position {

  // Keeps track of pointer position
  var pointerPos: Offset = Offset(0, 0)

  document.addEventListener("mousemove", (event: dom.MouseEvent) => {
    pointerPos =
      Offset(
        left = event.pageX,
        top  = event.pageY
      )
  })

  // How much we need to add to offset to account for the form having been scrolled
  def scrollTop() : Double  = document.querySelector(".fb-main").scrollTop
  def scrollLeft(): Double  = document.querySelector(".fb-main").scrollLeft

  // Gets an element offset, normalizing for scrolling, so the offset can be stored in a cache
  def adjustedOffset(el: JQuery): Offset = {
    val rawOffset = Offset(el)
    Offset(
      left = rawOffset.left + scrollLeft(),
      top  = rawOffset.top  + scrollTop()
    )
  }

  // Calls listener when what is under the pointer has potentially changed
  def onUnderPointerChange(fn: () => Unit): Unit = {
    dom.document.addEventListener("mousemove", (_: dom.Event) => fn())
    onOffsetMayHaveChanged(fn)
  }

  // Call listener when anything on the page that could change element positions happened
  def onOffsetMayHaveChanged(fn: () => Unit): Unit = {
    Events.orbeonLoadedEvent.subscribe(fn) // TODO: not great for nth embedding of Form Builder
    AjaxClient.ajaxResponseProcessed.add(_ => fn())
    Events.componentChangedLayoutEvent.subscribe(fn)

    // Can be removed once we only support Safari 14, which implements the `ResizeObserver`
    dom.window.addEventListener("resize", (_: dom.Event) => fn)

    DomSupport.onElementFoundOrAdded(document.body, "img", (elem: html.Element) => {
      val image = elem.asInstanceOf[html.Image]
      // Calling the listener before all the CSS has been loaded can cause errors
      val fnIfComplete = () => if (document.readyState == DocumentReadyState.complete) fn()
      if (image.complete) {
        fnIfComplete()
      } else {
        image.addEventListener("load",  (_: dom.Event) => fnIfComplete())
        image.addEventListener("error", (_: dom.Event) => fnIfComplete())
      }
    })

    // `ResizeObserver` catches window resizes, but also Form Builder being moved or resized by the embedding app
    Events.orbeonLoadedEvent.subscribe(() => {
      val resizeObserver = new ResizeObserver(fn)
      val fbMainOpt      = Option(document.querySelector(".fb-main"))
      fbMainOpt.foreach(resizeObserver.observe)
    })
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
    onUnderPointerChange(() => {
      val top  = pointerPos.top  + Position.scrollTop()
      val left = pointerPos.left + Position.scrollLeft()
      val dialogVisible = document.querySelectorAll("dialog[open]").length > 0
      val newContainer =
        if (dialogVisible)
          // Ignore container under the pointer if a dialog is visible
          None
        else
          findInCache(containerCache, top, left)
      notifyChange(newContainer)
    })
  }

  // Returns a function, which is expected to be called every time the value changes passing the new value, and which
  // will when appropriate notify the listeners `was` and `becomes` of the old and new value
  // TODO: replace `Any` by `Unit` once callers are all in Scala
  private def notifyOnChange(
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
    val repeatRegex      = "repeat\\(([0-9]+), ([0-9.]+px)\\)".r
    val cssValueExpanded = repeatRegex.replaceAllIn(cssValue,  m => {
      val count = m.group(1).toInt
      val value = m.group(2)
      (1 to count).map(_ => value).mkString(" ")
    })

    cssValueExpanded
      .splitTo[List]()
      .map(w => w.replace("px", ""))
      .flatMap(v => v.toDoubleOption) // https://github.com/orbeon/orbeon-forms/issues/3700
  }
}
