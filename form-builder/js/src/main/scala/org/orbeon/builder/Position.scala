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

import org.orbeon.builder.SideEditor.GridSectionInfo
import org.orbeon.xforms._
import org.orbeon.xforms.facade.Events
import org.scalajs.dom.{document, window}
import org.scalajs.jquery.JQuery

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel, ScalaJSDefined}

@JSExportTopLevel("ORBEON.builder.Position")
@JSExportAll
object Position {

  // How much we need to add to offset to account for the form having been scrolled
  def scrollTop() : Double  = $(".fb-main").scrollTop ()
  def scrollLeft(): Double  = $(".fb-main").scrollLeft()

  @ScalaJSDefined
  class Offset extends js.Object {
    val left   : Double = 0
    val top    : Double = 0
  }

  // Typed version of JQuery's offset()
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
  def onUnderPointerChange(fn: js.Function): Unit = {
    $(document).on("mousemove", fn)
    // Resizing the window might change what is under the pointer the last time we saw it in the window
    $(window).on("resize", fn)
    Events.ajaxResponseProcessedEvent.subscribe(fn)
  }

  // Call listener when anything on the page that could change element positions happened
  def onOffsetMayHaveChanged(fn: js.Function): Unit = {
      // After the form is first shown
      Events.orbeonLoadedEvent.subscribe(fn)
      // After an Ajax response, as it might have changed the DOM
      Events.ajaxResponseProcessedEvent.subscribe(fn)
      $(window).on("resize", fn)
  }

  // TODO: move to a case class once all the code creating a Container is in Scala.js
  @js.native
  trait Container extends js.Object {
    val left   : Double
    val top    : Double
    val width  : Double
    val height : Double
  }

  // Finds the container, if any, based on a vertical position
  def findInCache(
    containerCache : js.Array[Container],
    top            : Double,
    left           : Double
  )                : js.UndefOr[Container] = {

    containerCache.find { container ⇒
      val horizontalPosInside = container.left <= left && left <= container.left + container.width
      val verticalPosInside   = container.top  <= top  && top  <= container.top  + container.height
      horizontalPosInside && verticalPosInside
    }.orUndefined
  }

  // Container is either a section or grid; calls listeners passing old/new container
//  def currentContainerChanged(
//    containerCache : js.Array[GridSectionInfo],
//    wasCurrent     : js.Function,
//    becomesCurrent : js.Function)
//                   : Unit = {
//
//
//  }

  // Returns a function, which is expected to be called every time the value changes passing the new value, and which
  // will when appropriate notify the listeners `was` and `becomes` of the old and new value
  // TODO: replace `Any` by `Unit` once callers are all in Scala
  def notifyOnChange[T](
    was     : js.Function1[GridSectionInfo, js.Any],
    becomes : js.Function1[GridSectionInfo, js.Any])
            : js.Function1[js.UndefOr[GridSectionInfo], Any] = {

    var currentValueOpt: js.UndefOr[GridSectionInfo] = js.undefined

    (newValueOpt: js.UndefOr[GridSectionInfo]) ⇒ {
      newValueOpt.toOption match {
        case Some(newValue) ⇒
          val notify =
            currentValueOpt.toOption match {
              case None ⇒ true
              case Some(currentValue) ⇒
                // Typically after an Ajax request, maybe a column/row was added/removed, so we might consequently
                // need to update the icon position
                newValue.el.is(currentValue.el) ||
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
