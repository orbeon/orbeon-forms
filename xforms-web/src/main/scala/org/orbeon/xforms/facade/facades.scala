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

import io.udash.wrappers.jquery.JQueryPromise
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms
import org.orbeon.xforms.{$, Page}
import org.scalajs
import org.scalajs.dom.{Element, html}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|


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
  def isMarkedReadonly: Boolean = containerElem.hasClass("xforms-readonly")

  def getXFormsFormOrThrow: xforms.Form =
    Page.findXFormsForm(containerElem)
      .getOrElse(throw new IllegalStateException(
        s"XBL companion for element `${containerElem.id}` is not associated with a form"
      ))
}

class ConnectCallbackArgument(val formId: String, val isUpload: js.UndefOr[Boolean]) extends js.Object

@JSGlobal("ORBEON.util.Property")
@js.native
class Property[T] extends js.Object {
  def get(): T = js.native
}

// Minimal facades for Bootstrap 5 and helpers for Bootstrap 2/5 modal dialogs

object Bootstrap {

  // The Bootstrap bundle is part of the XForms asset baseline; it exposes itself as `ORBEON.bootstrap` instead of
  // `window.bootstrap`, so it can't clash with a Bootstrap copy loaded by the host page when embedded (#7809)
  @js.native
  @JSGlobal("ORBEON")
  private object OrbeonGlobal extends js.Object {
    def bootstrap: js.UndefOr[Bootstrap] = js.native
  }

  private def bootstrapGlobal: Bootstrap =
    OrbeonGlobal.bootstrap
      .getOrElse(throw new IllegalStateException("the `ORBEON.bootstrap` global is missing"))

  // Tooltips and popovers (native Bootstrap classes)
  def newTooltip(element: Element, configuration: js.Object): BootstrapTip = newTip(element, configuration, _.Tooltip, TooltipTitleSelector)
  def newPopover(element: Element, configuration: js.Object): BootstrapTip = newTip(element, configuration, _.Popover, PopoverTitleSelector)
  def getTooltip(element: Element): Option[BootstrapTip]                   = getTip(element, _.Tooltip, TooltipTitleSelector)
  def getPopover(element: Element): Option[BootstrapTip]                   = getTip(element, _.Popover, PopoverTitleSelector)

  // Where each tip kind displays its title, for setContent()
  private val TooltipTitleSelector = ".tooltip-inner"
  private val PopoverTitleSelector = ".popover-header"

  private def newTip(element: Element, configuration: js.Object, ctor: Bootstrap => js.Dynamic, titleSelector: String): BootstrapTip = {
    // Don't sanitize HTML tooltips/popovers: labels/hints/help are form-author content, same trust level as the rest of
    // the form.
    val dynConfig = configuration.asInstanceOf[js.Dynamic]
    if (dynConfig.html.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false) && js.isUndefined(dynConfig.sanitize))
      dynConfig.updateDynamic("sanitize")(false)
    // Set the container, as tips added to <body> render behind modal dialogs and don't get the .orbeon-scoped CSS
    if (js.isUndefined(dynConfig.container))
      element.closestOpt("dialog")
        .orElse(element.closestOpt(".orbeon"))
        .foreach(containerEl => dynConfig.container = containerEl)
    val tipCtor = ctor(bootstrapGlobal)
    val _ = tipCtor.getOrCreateInstance(element, configuration)
    new BootstrapTip(tipCtor, element, titleSelector)
  }

  private def getTip(element: Element, ctor: Bootstrap => js.Dynamic, titleSelector: String): Option[BootstrapTip] = {
    val tipCtor  = ctor(bootstrapGlobal)
    val instance = tipCtor.getInstance(element)
    if (instance == null || js.isUndefined(instance)) None else Some(new BootstrapTip(tipCtor, element, titleSelector))
  }
}

@js.native
trait Bootstrap extends js.Object {
  val Tooltip: js.Dynamic = js.native
  val Popover: js.Dynamic = js.native
}

// Handle on a native bootstrap.Tooltip / bootstrap.Popover (ctor is the Bootstrap class)
class BootstrapTip private[facade] (ctor: js.Dynamic, element: Element, titleSelector: String) {

  private def instanceOpt: Option[js.Dynamic] = {
    val instance = ctor.getInstance(element)
    if (instance == null || js.isUndefined(instance)) None else Some(instance.asInstanceOf[js.Dynamic])
  }

  def show()   : Unit = instanceOpt.foreach(_.show())
  def hide()   : Unit = instanceOpt.foreach(_.hide())
  def destroy(): Unit = instanceOpt.foreach(_.dispose())

  // The config title wins over data-orbeon-bs-original-title, so updates must go through setContent()
  def updateTitle(title: js.Any): Unit =
    instanceOpt.foreach(_.setContent(js.Dictionary(titleSelector -> title)))

  // The tip element in the DOM, once shown: Bootstrap links the trigger to its tip via aria-describedby.
  def tipElementOpt: Option[Element] =
    Option(element.getAttribute("aria-describedby")).flatMap(id => Option(scalajs.dom.document.getElementById(id)))
}

