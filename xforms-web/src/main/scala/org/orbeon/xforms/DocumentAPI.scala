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

import org.orbeon.xforms.facade.{Controls, XBL}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.|


object DocumentAPI extends js.Object {

  import Private._

  // Dispatch an event defined by the properties on a JavaScript object
  // This is the method we document officially as of 2015-10 for JavaScript callers.
  // Q: Can we type `eventObject`?
  def dispatchEvent(eventObject: js.Dictionary[js.Any]): Unit =
    AjaxClient.fireEvent(new AjaxEvent(eventObject))

  // Dispatch an event
  // We do NOT document this as of 2015-10 for JavaScript callers. This should be
  // considered a backward compatibility method for older JavaScript code. It doesn't
  // support all the parameters.
  @deprecated("use `dispatchEvent(eventObject: js.Object)` instead", "Orbeon Forms 2016.1")
  def dispatchEvent(
    targetId     : String,
    eventName    : String,
    formElem     : js.UndefOr[html.Form]             = js.undefined,
    bubbles      : js.UndefOr[Boolean]               = js.undefined,
    cancelable   : js.UndefOr[Boolean]               = js.undefined,
    incremental  : js.UndefOr[Boolean]               = js.undefined,
    ignoreErrors : js.UndefOr[Boolean]               = js.undefined,
    properties   : js.UndefOr[js.Dictionary[js.Any]] = js.undefined
  ): Unit = {

    // `targetId` can be null for `xxforms-all-events-required` and `xxforms-server-events`
    require(eventName ne null)

    val eventObject  = js.Dictionary.empty[js.Any]

    eventObject += "eventName" -> eventName
    eventObject += "targetId"  -> targetId

    formElem     foreach (v => eventObject += "form"         -> v)
    incremental  foreach (v => eventObject += "incremental"  -> v)
    ignoreErrors foreach (v => eventObject += "ignoreErrors" -> v)
    properties   foreach (v => eventObject += "properties"   -> v)

    dispatchEvent(eventObject)
  }

  // Return the value of an XForms control
  def getValue(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): js.UndefOr[String] =
      Controls.getCurrentValue(findControlOrThrow(controlIdOrElem, formElem))

  // Set the value of an XForms control
  def setValue(
    controlIdOrElem : String | html.Element,
    newValue        : String | Double | Boolean,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): Future[Unit] = {

    val newStringValue = newValue.toString

    val control = findControlOrThrow(controlIdOrElem, formElem)

    require(
      ! (control.classList.contains("xforms-output") || control.classList.contains("xforms-upload")),
      s"Cannot set the value of an output or upload control for id `${control.id}`"
    )

    def fireValueEvent(): Unit =
      Controls.getCurrentValue(control).foreach { newValue =>
        // Use the value from the control, not the one received (2023-05-18 why?)
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName  = EventNames.XXFormsValue,
            targetId   = control.id,
            properties = Map("value" -> newValue)
          )
        )
      }

    // Directly change the value in the UI without waiting for an Ajax response; for an XBL component, this calls
    // `xformsUpdateValue()` on the companion object if supported, which doesn't dispatch an event (at least not directly)
    val undefOrPromiseOrFuture =
      if (XFormsXbl.isJavaScriptLifecycle(control)) {
        // Handle XBL components with JavaScript lifecycle

        val companion = XBL.instanceForControl(control)

        val hasXformsUpdateValue = XFormsXbl.isObjectWithMethod(companion, "xformsUpdateValue")
        val hasSetUserValue      = XFormsXbl.isObjectWithMethod(companion, "setUserValue")

        if (hasSetUserValue)
          companion.setUserValue(newStringValue) // https://github.com/orbeon/orbeon-forms/issues/5383
        else if (hasXformsUpdateValue)
          companion.xformsUpdateValue(newStringValue)
        else
          js.undefined
      } else {
        // This handles the native XForms controls
        Controls.setCurrentValue(control, newStringValue, force = false)
      }

    // If setting the value was synchronous, fire the event right away
    if (js.isUndefined(undefOrPromiseOrFuture)) {
      fireValueEvent()
      Future(())
    } else {
      XFormsUI.maybeFutureToScalaFuture(undefOrPromiseOrFuture).flatMap { _ =>
        Future(fireValueEvent())
      }
    }
  }

  def focus(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): Unit = {

    val control = findControlOrThrow(controlIdOrElem, formElem)

    Controls.setFocus(control.id)
    AjaxClient.fireEvent(
      AjaxEvent(
        eventName = EventNames.XFormsFocus,
        targetId  = control.id
      )
    )
  }

  private object Private {

    def findControlOrThrow(
      controlIdOrElem : String | html.Element,
      formElem        : js.UndefOr[html.Form]
    ): html.Element = {

      val (resolvedControlId, resolvedControlOpt) =
        (controlIdOrElem: Any) match {
          case givenControlId: String =>
            givenControlId -> Option(dom.document.getElementById(Support.adjustIdNamespace(formElem, givenControlId)._2))
          case givenElement: html.Element =>
            givenElement.id -> Some(givenElement)
        }

      resolvedControlOpt match {
        case Some(resolvedControl: html.Element) =>
          resolvedControl
        case _ =>
          throw new IllegalArgumentException(s"Cannot find control for id `$resolvedControlId`")
      }
    }
  }
}
