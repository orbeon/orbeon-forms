/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms.facade

import org.orbeon.xforms.YUICustomEvent
import org.scalajs.dom
import org.scalajs.dom.{html, raw}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|

@js.native
trait DocumentTrait extends js.Object {

  def getValue(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): js.UndefOr[String] = js.native

  // Set the value of an XForms control
  def setValue(
    controlIdOrElem : String | html.Element,
    newValue        : String | Double | Boolean,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): Unit = js.native

  def focus(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): Unit = js.native

}

@js.native
@JSGlobal("ORBEON.xforms.Document")
object Document extends DocumentTrait

@js.native
trait InitTrait extends js.Object {
  def initializeGlobals()                   : Unit = js.native
  def _range(control: html.Element)         : Unit = js.native
  def _compactSelect(control: html.Element) : Unit = js.native
  def _dialog(control: html.Element)        : Unit = js.native
}

@js.native
@JSGlobal("ORBEON.xforms.Init")
object Init extends InitTrait

@js.native
trait AjaxServerTrait extends js.Object {
  def ajaxResponseProcessedForCurrentEventQueueP(): js.Promise[Unit] = js.native
}

@js.native
@JSGlobal("ORBEON.xforms.server.AjaxServer")
object AjaxServer extends AjaxServerTrait {
  def handleResponseDom(
    responseXML                  : dom.Document,
    isResponseToBackgroundUpload : Boolean,
    formId                       : String,
    ignoreErrors                 : Boolean
  ): Unit = js.native
}

@js.native
trait Item extends js.Object {
  val label                 : String                      = js.native
  val value                 : String                      = js.native
  val attributes            : js.UndefOr[ItemAttributes]  = js.native
  val children              : js.UndefOr[js.Array[Item]]  = js.native
}

@js.native
trait ItemAttributes extends js.Object {
  val `class`               : js.UndefOr[String]          = js.native
  val style                 : js.UndefOr[String]          = js.native
  val `xxforms-open`        : js.UndefOr[String]          = js.native
}

class XBLCompanion extends js.Object {

  // Lifecycle

  def init()                                  : Unit   = ()
  def destroy()                               : Unit   = ()

  def xformsGetValue()                        : String = null
  def xformsUpdateValue(newValue: String)     : Unit   = ()
  def xformsUpdateReadonly(readonly: Boolean) : Unit   = ()
  def xformsFocus()                           : Unit   = ()

  // Helpers

  def containerElem: html.Element = this.asInstanceOf[js.Dynamic].container.asInstanceOf[html.Element]
}

@JSGlobal("ORBEON.xforms.XBL")
@js.native
object XBL extends js.Object {
  def declareCompanion(name: String, companion: XBLCompanion): Unit         = js.native
  def isComponent(control: html.Element)                     : Boolean      = js.native
  def instanceForControl(control: html.Element)              : XBLCompanion = js.native
}

@JSGlobal("ORBEON.xforms.Controls")
@js.native
object Controls extends js.Object {
  def setCurrentValue(control: html.Element, newControlValue: String): Unit                  = js.native
  def getCurrentValue(elem: html.Element)                            : js.UndefOr[String]    = js.native
  def setFocus(controlId: String)                                    : Unit                  = js.native
  def removeFocus(controlId: String)                                 : Unit                  = js.native
  def getForm(control: dom.Element)                                  : js.UndefOr[html.Form] = js.native
}

class ConnectCallbackArgument(val formId: String, val isUpload: js.UndefOr[Boolean]) extends js.Object

@JSGlobal("ORBEON.xforms.Events")
@js.native
object Events extends js.Object {
  val ajaxResponseProcessedEvent  : YUICustomEvent = js.native
  val errorEvent                  : YUICustomEvent = js.native
  val orbeonLoadedEvent           : YUICustomEvent = js.native
  val componentChangedLayoutEvent : YUICustomEvent = js.native

  val focus                       : js.Function    = js.native
  val blur                        : js.Function    = js.native
  val change                      : js.Function    = js.native
  val keypress                    : js.Function    = js.native
  val keydown                     : js.Function    = js.native
  val keyup                       : js.Function    = js.native
  val mouseover                   : js.Function    = js.native
  val mouseout                    : js.Function    = js.native
  val click                       : js.Function    = js.native
  val scrollOrResize              : js.Function    = js.native
}

@JSGlobal("ORBEON.util.Property")
@js.native
class Property[T] extends js.Object {
  def get(): T = js.native
}

@JSGlobal("ORBEON.util.Properties")
@js.native
object Properties extends js.Object {

  def init()                           : Unit              = js.native

  val delayBeforeIncrementalRequest    : Property[Int]     = js.native
  val delayBeforeUploadProgressRefresh : Property[Int]     = js.native
  val delayBeforeDisplayLoading        : Property[Int]     = js.native
  val delayBeforeAjaxTimeout           : Property[Int]     = js.native
  val retryDelayIncrement              : Property[Int]     = js.native
  val retryMaxDelay                    : Property[Int]     = js.native
  val internalShortDelay               : Property[Double]  = js.native
  val revisitHandling                  : Property[String]  = js.native
  val sessionHeartbeat                 : Property[Boolean] = js.native
  val sessionHeartbeatDelay            : Property[Int]     = js.native

  val loginPageDetectionRegexp         : Property[String]  = js.native
  val showErrorDialog                  : Property[Boolean] = js.native
}

@js.native
trait InitData extends js.Object {
  val initializations : String = js.native
}

@JSGlobal("ORBEON.util.Utils")
@js.native
object Utils extends js.Object {
  def appendToEffectiveId(effectiveId: String, ending: String)              : String           = js.native
  def getRepeatIndexes(effectiveId: String)                                 : js.Array[String] = js.native
  def findRepeatDelimiter(formId: String, repeatId: String, iteration: Int) : raw.Element      = js.native
  def overlayUseDisplayHidden(o: js.Object)                                 : Unit             = js.native
  def isIOS()                                                               : Boolean          = js.native
  def getZoomLevel()                                                        : Double           = js.native
  def resetIOSZoom()                                                        : Unit             = js.native
}
