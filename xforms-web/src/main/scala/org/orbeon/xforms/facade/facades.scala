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

import org.orbeon.xforms.{$, YUICustomEvent}
import org.scalajs
import org.scalajs.dom
import org.scalajs.dom.Element
import org.scalajs.dom.{FocusEvent, UIEvent, html, raw}
import io.udash.wrappers.jquery.JQueryPromise

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|


@js.native
trait InitTrait extends js.Object {
  def _range(control: html.Element)         : Unit       = js.native
  def _compactSelect(control: html.Element) : Unit       = js.native
  def _dialog(control: html.Element)        : js.Dynamic = js.native
}

@js.native
@JSGlobal("ORBEON.xforms.Init")
object Init extends InitTrait

@js.native
@JSGlobal("ORBEON.xforms.AjaxServerResponse")
object AjaxServer extends js.Object {
  def handleResponseDom(
    responseXML  : dom.Document,
    formId       : String,
    ignoreErrors : Boolean
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

  def init()                                  : Unit                                         = ()
  def destroy()                               : Unit                                         = ()

  def xformsGetValue()                        : String                                       = null
  def xformsUpdateValue(newValue: String)     : js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = ()
  def xformsUpdateReadonly(readonly: Boolean) : Unit                                         = ()
  def xformsFocus()                           : Unit                                         = ()

  // https://github.com/orbeon/orbeon-forms/issues/5383
  def setUserValue(newValue: String)          : js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = ()

  // Helpers
  private def containerElem: html.Element = this.asInstanceOf[js.Dynamic].container.asInstanceOf[html.Element]
  def isMarkedReadonly: Boolean = containerElem.classList.contains("xforms-readonly")
}

@JSGlobal("ORBEON.xforms.XBL")
@js.native
object XBL extends js.Object {
  def declareCompanion(name: String, prototypeOrClass: js.Dynamic): Unit         = js.native
  def instanceForControl(control: html.Element)                   : XBLCompanion = js.native

  val componentInitialized                                        : YUICustomEvent = js.native
}

@JSGlobal("ORBEON.xforms.Controls")
@js.native
object Controls extends js.Object {
  def isReadonly(control: html.Element)                                                                  : Boolean                                      = js.native
  def setCurrentValue(control: html.Element, newControlValue: String, force: Boolean)                    : js.UndefOr[js.Promise[Unit] | JQueryPromise[js.Function1[js.Any, js.Any], js.Any]] = js.native
  def getCurrentValue(elem: html.Element)                                                                : js.UndefOr[String]                           = js.native
  def setFocus(controlId: String)                                                                        : Unit                                         = js.native
  def removeFocus(controlId: String)                                                                     : Unit                                         = js.native
  def getLabelMessage(elem: html.Element)                                                                : String                                       = js.native
  def getHelpMessage(elem: html.Element)                                                                 : String                                       = js.native
  def setRepeatIterationRelevance(formID: String, repeatID: String, iteration: String, relevant: Boolean): Unit                                         = js.native
  def getControlLHHA(documentElement: html.Element, lhha: String)                                        : js.UndefOr[html.Element]                     = js.native

  def setLabelMessage(documentElement: html.Element, newLabel: String)                                   : Unit                                         = js.native
  def setHelpMessage(documentElement: html.Element, newHelp: String)                                     : Unit                                         = js.native
  def setHintMessage(documentElement: html.Element, newHint: String)                                     : Unit                                         = js.native
  def setAlertMessage(documentElement: html.Element, newAlert: String)                                   : Unit                                         = js.native
  def setConstraintLevel(documentElement: html.Element, newLevel: String)                                : Unit                                         = js.native
  def setRelevant(documentElement: html.Element, relevant: Boolean)                                      : Unit                                         = js.native
  def setReadonly(documentElement: html.Element, readonly: Boolean)                                      : Unit                                         = js.native
  def updateVisited(documentElement: html.Element, newVisited: Boolean)                                  : Unit                                         = js.native
  def updateRequiredEmpty(documentElement: html.Element, emptyAttr: String)                              : Unit                                         = js.native

  def toggleCase(id: String, visible: Boolean)                                                           : Unit                                         = js.native

  val afterValueChange                                                                                   : YUICustomEvent                               = js.native
}

class ConnectCallbackArgument(val formId: String, val isUpload: js.UndefOr[Boolean]) extends js.Object

@JSGlobal("ORBEON.xforms.Events")
@js.native
object Events extends js.Object {

  val errorEvent                  : YUICustomEvent                 = js.native
  val orbeonLoadedEvent           : YUICustomEvent                 = js.native
  val componentChangedLayoutEvent : YUICustomEvent                 = js.native

  val focus                       : js.Function1[FocusEvent, Unit] = js.native
  val blur                        : js.Function1[FocusEvent, Unit] = js.native
  val change                      : js.Function1[UIEvent, Unit]    = js.native
  val keypress                    : js.Function1[UIEvent, Unit]    = js.native
  val keydown                     : js.Function1[UIEvent, Unit]    = js.native
  val input                       : js.Function1[UIEvent, Unit]    = js.native
  val mouseover                   : js.Function1[UIEvent, Unit]    = js.native
  val mouseout                    : js.Function1[UIEvent, Unit]    = js.native
  val click                       : js.Function1[UIEvent, Unit]    = js.native

  def _findParentXFormsControl(t: dom.EventTarget): html.Element = js.native // can return `null`
}

@JSGlobal("ORBEON.util.Property")
@js.native
class Property[T] extends js.Object {
  def get(): T = js.native
}

@JSGlobal("ORBEON.util.Utils")
@js.native
object Utils extends js.Object {
  def getRepeatIndexes(effectiveId: String)                                 : js.Array[String] = js.native
  def findRepeatDelimiter(formId: String, repeatId: String, iteration: Int) : raw.Element      = js.native
  def overlayUseDisplayHidden(o: js.Object)                                 : Unit             = js.native
}

// Minimal facades for the Broadcast Channel API

@js.native
@JSGlobal("BroadcastChannel")
class BroadcastChannel(name: String) extends js.Object {
  def name: String = js.native
  def postMessage(message: js.Any): Unit = js.native
  def close(): Unit = js.native
  var onmessage: js.Function1[MessageEvent, Unit] = js.native
  var onmessageerror: js.Function1[MessageEvent, Unit] = js.native
}

@js.native
trait MessageEvent extends js.Object {
  val data: js.Any = js.native
}

// Minimal facades for Bootstrap 5 and helpers for Bootstrap 2/5 modal dialogs

@js.native
trait BootstrapWindow extends scalajs.dom.Window {
  def bootstrap: js.UndefOr[Bootstrap] = js.native
}

object Bootstrap {
  implicit def windowToBootstrapWindow(window: scalajs.dom.Window): BootstrapWindow =
    window.asInstanceOf[BootstrapWindow]

  implicit class BootstrapOps(private val bootstrap: Bootstrap) extends AnyVal {
    // Bootstrap 5 modal instantiation
    def newModal(dialog: Element, configuration: js.Object): Modal =
      js.Dynamic.newInstance(bootstrap.Modal)(dialog, configuration).asInstanceOf[Modal]
  }

  def newModal(dialog: Element, configuration: js.Object): GenericModal =
    scalajs.dom.window.bootstrap.toOption match {
      case Some(boostrap) => boostrap.newModal(dialog, configuration) // Bootstrap 5
      case None           => new Modal2(dialog, configuration)        // Bootstrap 2
    }
}

@js.native
trait Bootstrap extends js.Object {
  val Modal: js.Dynamic = js.native
}

trait GenericModal extends js.Any {
  def show(): Unit
  def hide(): Unit
}

// Bootstrap 2 modal
class Modal2(val dialog: Element, val configuration: js.Object) extends js.Object with GenericModal {
  private val jQuery = $(dialog).asInstanceOf[js.Dynamic]

  override def show(): Unit = jQuery.modal(configuration)
  override def hide(): Unit = jQuery.modal("hide")
}

// Bootstrap 5 modal
@js.native
trait Modal extends js.Object with GenericModal {
  def show(): Unit = js.native
  def hide(): Unit = js.native
}

