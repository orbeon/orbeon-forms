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

import org.orbeon.xforms._
import org.orbeon.xforms.facade.Events
import org.scalajs.dom.{document, window}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("ORBEON.builder.Position")
@JSExportAll
object Position {

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

}
