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

import io.circe.generic.auto._
import org.orbeon.xforms.facade.{AjaxServer, Controls}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.scalajs.js.|

@JSExportTopLevel("ORBEON.xforms.Document")
object DocumentAPI {

  import Private._

  // Dispatch an event
  // NOTE: This doesn't support all parameters.
  // Which should be deprecated, this or the other `dispatchEvent()`?
  @JSExport
  def dispatchEvent(
    targetId     : String,
    eventName    : String,
    formElem     : js.UndefOr[html.Element]          = js.undefined,
    bubbles      : js.UndefOr[Boolean]               = js.undefined,
    cancelable   : js.UndefOr[Boolean]               = js.undefined,
    incremental  : js.UndefOr[Boolean]               = js.undefined,
    ignoreErrors : js.UndefOr[Boolean]               = js.undefined,
    properties   : js.UndefOr[js.Dictionary[String]] = js.undefined
  ): Unit = {

    val eventObject  = new js.Object
    val eventDynamic = eventObject.asInstanceOf[js.Dynamic]

    eventDynamic.targetId  = targetId
    eventDynamic.eventName = eventName

    formElem     foreach (eventDynamic.form         = _)
    bubbles      foreach (eventDynamic.bubbles      = _)
    cancelable   foreach (eventDynamic.cancelable   = _)
    incremental  foreach (eventDynamic.incremental  = _)
    ignoreErrors foreach (eventDynamic.ignoreErrors = _)
    properties   foreach (eventDynamic.properties   = _)

    dispatchEvent(eventObject)
  }

  // Dispatch an event defined by an object
  // NOTE: Use the first XForms form on the page when no form is provided.
  // TODO: Can we type `eventObject`?
  @JSExport
  def dispatchEvent(eventObject: js.Object): Unit = {

    val eventDynamic = eventObject.asInstanceOf[js.Dynamic]

    val (resolvedForm, adjustedTargetId) =
      adjustIdNamespace(
        eventDynamic.form.asInstanceOf[html.Form],
        eventDynamic.targetId.asInstanceOf[String]
      )

    eventDynamic.form     = resolvedForm
    eventDynamic.targetId = adjustedTargetId

    AjaxServer.fireEvents(
      js.Array(new AjaxServer.Event(eventDynamic)),
      incremental = eventDynamic.incremental.asInstanceOf[js.UndefOr[Boolean]].getOrElse(false)
    )
  }

  // Return the value of an XForms control
  @JSExport
  def getValue(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): js.UndefOr[String] =
      Controls.getCurrentValue(findControlOrThrow(controlIdOrElem, formElem))

  // Set the value of an XForms control
  @JSExport
  def setValue(
    controlIdOrElem : String | html.Element,
    newValue        : String | Double | Boolean,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): Unit = {

    val newStringValue = newValue.toString

    val control = findControlOrThrow(controlIdOrElem, formElem)

    require(
      ! $(control).is(".xforms-output, .xforms-upload"),
      s"Cannot set the value of an output or upload control for id `${control.id}`"
    )

    // Directly change the value in the UI without waiting for an Ajax response
    Controls.setCurrentValue(control, newStringValue)

    // And also fire server event
    val event = new AjaxServer.Event(
      new js.Object {
        val targetId  = control.id
        val eventName = "xxforms-value"
        val value     = newStringValue
      }
    )

    AjaxServer.fireEvents(js.Array(event), incremental = false)
  }

  @JSExport
  def focus(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Form] = js.undefined
  ): Unit = {

    val control = findControlOrThrow(controlIdOrElem, formElem)

    Controls.setFocus(control.id)
    dispatchEvent(targetId = control.id, eventName = "xforms-focus")
  }

  private object Private {

    def adjustIdNamespace(
      formElem : js.UndefOr[html.Form],
      targetId : String
    ): (html.Element, String) = {

      val form   = Support.formElemOrDefaultForm(formElem)
      val formId = form.id

      // See comment on `namespaceIdIfNeeded`
      form → Page.namespaceIdIfNeeded(formId, targetId)
    }

    def findControlOrThrow(
      controlIdOrElem : String | html.Element,
      formElem        : js.UndefOr[html.Form]
    ): html.Element = {

      val (resolvedControlId, resolvedControlOpt) =
        (controlIdOrElem: Any) match {
          case givenControlId: String ⇒
            givenControlId → Option(dom.document.getElementById(adjustIdNamespace(formElem, givenControlId)._2))
          case givenElement: html.Element ⇒
            givenElement.id → Some(givenElement)
        }

      resolvedControlOpt match {
        case Some(resolvedControl: html.Element) ⇒
          resolvedControl
        case _ ⇒
          throw new IllegalArgumentException(s"Cannot find control for id `$resolvedControlId`")
      }
    }
  }
}
