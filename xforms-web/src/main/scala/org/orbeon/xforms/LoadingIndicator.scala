/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import org.orbeon.xforms.facade.Properties

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal
object NProgress extends js.Object {
  def configure(options: js.Object) : Unit = js.native
  def start()                       : Unit = js.native
  def done()                        : Unit = js.native
}

class LoadingIndicator extends js.Object { // so that properties/methods can be accessed from JavaScript

  private var shownCounter = 0

  NProgress.configure(new js.Object { val showSpinner = false })

  def requestStarted(showProgress: Boolean): Unit = {
    if (showProgress) {
      if (shownCounter == 0) {
        // Show the indicator after a delay
        val delay = Properties.delayBeforeDisplayLoading.get()
        if (delay > 0)
          js.timers.setTimeout(delay)(showIfNotAlreadyVisible _)
        else
          showIfNotAlreadyVisible()
      } else {
        // Indicator already shown, just increment counter
        shownCounter += 1
      }
    }
  }

  def requestEnded(showProgress: Boolean): Unit =
    if (showProgress)
      js.timers.setTimeout(1) { // Defer hiding the indicator to give a chance to next request to start,
        hideIfAlreadyVisible()  // so we don't flash the indicator.
      }

  // Public for `AjaxServer.js`
  def showIfNotAlreadyVisible(): Unit = {
    shownCounter += 1
    if (shownCounter == 1)
      show()
  }

  def hideIfAlreadyVisible(): Unit =
    if (shownCounter > 0) {
      shownCounter -= 1
      if (shownCounter == 0)
        hide()
    }

  // Actually shows the loading indicator (no delay or counter)
  private def show(): Unit =
    NProgress.start()

  // Actually hides the loading indicator (no counter)
  private def hide(): Unit =
    NProgress.done()
}
