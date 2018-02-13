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

import org.orbeon.xforms.{DocumentAPI, YUICustomEvent}
import org.scalajs.dom
import org.scalajs.dom.raw.XMLHttpRequest
import org.scalajs.dom.{html, raw}
import org.scalajs.jquery.{JQuery, JQueryCallback}

import scala.concurrent.{Future, Promise}
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
trait AjaxServerTrait extends js.Object {
  def handleResponseAjax(o: XMLHttpRequest)                                : Unit           = js.native
  def beforeSendingEvent                                                   : JQueryCallback = js.native
  def ajaxResponseReceived                                                 : JQueryCallback = js.native
  def fireEvents(events: js.Array[AjaxServer.Event], incremental: Boolean) : Unit           = js.native
}

@js.native
@JSGlobal("ORBEON.xforms.server.AjaxServer")
object AjaxServer extends AjaxServerTrait {
  @js.native
  class Event(args: js.Any*) extends js.Object
}

object AjaxServerOps {

  implicit class AjaxServerOps(val ajaxServer: AjaxServerTrait) extends AnyVal {

    def ajaxResponseReceivedF: Future[Unit] = {

      val result = Promise[Unit]()

      lazy val callback: js.Function = () ⇒ {
        ajaxServer.ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(callback) // because has `removed`
        result.success(())
      }

      ajaxServer.ajaxResponseReceived.add(callback)

      result.future
    }

    def ajaxResponseProcessedF(formId: String): Future[Unit] = {

      def getSeqNo =
        DocumentAPI.getFromClientState(formId, "sequence").toInt

      val initialSeqNo = getSeqNo

      val result = Promise[Unit]()

      lazy val callback: js.Function = () ⇒ {
        if (getSeqNo == initialSeqNo + 1) {
          Events.ajaxResponseProcessedEvent.unsubscribe(callback)
          result.success(())
        }
      }

      Events.ajaxResponseProcessedEvent.subscribe(callback)

      result.future
    }
  }

}

@JSGlobal("ORBEON.xforms.Globals")
@js.native
object Globals extends js.Object {
  val xformsServerUploadURL : js.Dictionary[String]     = js.native
  val formClientState       : js.Dictionary[html.Input] = js.native
  var loadingOtherPage      : Boolean                   = js.native
  val isReloading           : Boolean                   = js.native
  val repeatIndexes         : js.Dictionary[String]     = js.native
  val ns                    : js.Dictionary[String]     = js.native
  val eventQueue            : js.Array[js.Any]          = js.native
  val requestInProgress     : Boolean                   = js.native
  val dialogs               : js.Dictionary[js.Dynamic] = js.native
}

@js.native
trait Item extends js.Object {
  val label                 : String                     = js.native
  val value                 : String                     = js.native
  val attributes            : js.UndefOr[ItemAttributes] = js.native
  val children              : js.UndefOr[js.Array[Item]] = js.native
}

@js.native
trait ItemAttributes extends js.Object {
  val `class`               : js.UndefOr[String]         = js.native
  val style                 : js.UndefOr[String]         = js.native
  val `xxforms-open`        : js.UndefOr[String]         = js.native
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
  def declareCompanion(name: String, companion: XBLCompanion): Unit = js.native
}

@JSGlobal("ORBEON.xforms.Controls")
@js.native
object Controls extends js.Object {
  def setCurrentValue(control: html.Element, newControlValue: String) : Unit               = js.native
  def getCurrentValue(elem: html.Element)                             : js.UndefOr[String] = js.native
  def isInRepeatTemplate(elem: html.Element)                          : Boolean            = js.native
  def setFocus(controlId: String)                                     : Unit               = js.native
}

class ConnectCallbackArgument(val formId: String, val isUpload: js.UndefOr[Boolean]) extends js.Object

@JSGlobal("ORBEON.xforms.Events")
@js.native
object Events extends js.Object {
  val ajaxResponseProcessedEvent  : YUICustomEvent = js.native
  val orbeonLoadedEvent           : YUICustomEvent = js.native
  val componentChangedLayoutEvent : YUICustomEvent = js.native
}

@JSGlobal("ORBEON.util.Property")
@js.native
class Property[T] extends js.Object {
  def get(): T = js.native
}

@JSGlobal("ORBEON.util.Properties")
@js.native
object Properties extends js.Object {
  val delayBeforeIncrementalRequest    : Property[Int]    = js.native
  val delayBeforeUploadProgressRefresh : Property[Int]    = js.native
  val delayBeforeDisplayLoading        : Property[Int]    = js.native
  val internalShortDelay               : Property[Double] = js.native
}

@JSGlobal("ORBEON.util.Utils")
@js.native
object Utils extends js.Object {
  def appendToEffectiveId(effectiveId: String, ending: String) : String =           js.native
  def getRepeatIndexes(effectiveId: String)                    : js.Array[String] = js.native
  def findRepeatDelimiter(repeatId: String, iteration: Int)    : raw.Element =      js.native
}

@JSGlobal("ORBEON.xforms.control.Control")
@js.native
class Control extends js.Object {

  val container: html.Element = js.native

  def init(container: html.Element) : Unit      = js.native
  def getForm()                     : html.Form = js.native
  def change()                      : Unit      = js.native

  // Provide a new itemset for a control, if the control supports this.
  def setItemset(itemset: js.Any): Unit = js.native

  // Set the current value of the control in the UI, without sending an update to the server about the new value
  def setValue(value: String): Unit = js.native

  // Return the current value of the control
  def getValue(): String = js.native

  // Returns the first element with the given class name that are inside this control
  def getElementByClassName(className: String): js.UndefOr[html.Element] = js.native
}

@js.native
trait JQueryTooltip extends JQuery {
  def tooltip(config: JQueryTooltipConfig): Unit = js.native
  def tooltip(operation: String)          : Unit = js.native
}

object JQueryTooltip {
  implicit def jq2tooltip(jq: JQuery): JQueryTooltip =
    jq.asInstanceOf[JQueryTooltip]
}

abstract class JQueryTooltipConfig extends js.Object {
  val title: String
}

@js.native
@JSGlobal("tinymce.Editor")
class TinyMceEditor(containerId: String, config: TinyMceConfig) extends js.Object {
  val initialized            : js.UndefOr[Boolean] = js.native
  val onInit                 : TinyMceEvent        = js.native
  val editorContainer        : String              = js.native
  val container              : dom.Element         = js.native
  def render()               : Unit                = js.native
  def getWin()               : dom.Window          = js.native
  def getContent()           : String              = js.native
  def setContent(c: String)  : Unit                = js.native
  def execCommand(c: String) : Unit                = js.native
  def show()                 : Unit                = js.native
  def hide()                 : Unit                = js.native
  def focus()                : Unit                = js.native
}

@js.native
trait TinyMceEvent extends js.Object {
  def add(f: js.Function1[TinyMceEditor, Unit])
}

@js.native
trait TinyMceConfig extends js.Object {
  var plugins                  : String = js.native
  var autoresize_min_height    : Double = js.native
  var autoresize_bottom_margin : Double = js.native
}

@JSGlobal("YAHOO.xbl.fr.Tinymce.DefaultConfig")
@js.native
object TinyMceDefaultConfig extends TinyMceConfig

@JSGlobal("_")
@js.native
object Underscore extends js.Object {
  def uniqueId(): String = js.native
  def clone[T](o: T): T = js.native
}

