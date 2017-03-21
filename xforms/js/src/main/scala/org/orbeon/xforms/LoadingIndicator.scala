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

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSGlobal, ScalaJSDefined}

@js.native
@JSGlobal
object NProgress extends js.Object {
  def configure(options: js.Object) : Unit = js.native
  def start()                       : Unit = js.native
  def done()                        : Unit = js.native
}

@ScalaJSDefined
class LoadingIndicator extends js.Object{

  private var nextConnectShow = false
  private var shownCounter    = 0

  locally {

    // Don't show NProgress spinner
    NProgress.configure(new js.Object { val showSpinner = false })

    // Whether this is an upload from the object passed to the callback. We only want the loading
    // indicator to show for Ajax request, not uploads, for which we have a different way of
    // indicating the upload is in progress.
    def isAjax(argument: js.UndefOr[ConnectCallbackArgument]): Boolean =
      ! (argument flatMap (_.isUpload) exists (_ == true))

    def requestStarted(typ: String, args: js.Array[js.UndefOr[ConnectCallbackArgument]]) =
      if (isAjax(args(1)) && nextConnectShow) { // `args(1)`, NOT `args(0)`!
        if (shownCounter == 0) {
          // Show the indicator after a delay
          js.timers.setTimeout(Properties.delayBeforeDisplayLoading.get()) {
            shownCounter += 1
            if (shownCounter == 1)
              show()
          }
        } else {
          // Indicator already shown, just increment counter
          shownCounter += 1
        }
      }

    def requestEnded(typ: String, args: js.Array[js.UndefOr[ConnectCallbackArgument]]) =
      if (isAjax(args(0)) && nextConnectShow) { // `args(0)`, NOT `args(1)`!
        // Defer hiding the indicator to give a chance to next request to start, so we don't flash the indicator
        js.timers.setTimeout(1) {
          shownCounter -= 1
          if (shownCounter == 0)
            hide()
        }
      }

    YUIConnect.startEvent.subscribe            (requestStarted _)
    Events.ajaxResponseProcessedEvent.subscribe(requestEnded   _)
    YUIConnect.failureEvent.subscribe          (requestEnded   _) // only for Ajax, YUI Connect doesn't call `failure` for uploads
  }

  // NOTE: Called externally from AjaxServer.
  def setNextConnectShow(nextConnectShow: Boolean): Unit =
    this.nextConnectShow = nextConnectShow

  // Actually shows the loading indicator (no delay or counter)
  // NOTE: Called externally from AjaxServer.
  def show() =
    NProgress.start()

  // Actually hides the loading indicator (no counter)
  private def hide() =
    if (! Globals.loadingOtherPage)
      NProgress.done()

}
