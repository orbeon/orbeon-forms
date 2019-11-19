/**
  * Copyright (C) 2019 Orbeon, Inc.
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
import org.orbeon.xforms.EventNames._
import org.orbeon.xforms.facade.{AjaxServer, Controls}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportTopLevel

object AjaxServerEvent {

  // This defines the order of parameters to the constructor of `AjaxServerEvent`. This must
  // be kept in this order for legacy JavaScript callers.
  private val ParamNames =
    List(
      "form",
      "targetId",
      "value",
      "eventName",
      "bubbles",      // unused
      "cancelable",   // unused
      "ignoreErrors",
      "showProgress"
    )

  private val DefaultIgnoreErrors = false
  private val DefaultShowProgress = true
  private val DefaultIncremental  = false

  // Main creator for Scala callers
  def apply(
    eventName    : String,
    targetId     : String,
    form         : Option[html.Form]   = None,
    properties   : Map[String, js.Any] = Map.empty,
    ignoreErrors : Boolean             = DefaultIgnoreErrors,
    showProgress : Boolean             = DefaultShowProgress,
    incremental  : Boolean             = DefaultIncremental
  ): AjaxServerEvent =
    new AjaxServerEvent(
      js.Dictionary[js.Any](
        "eventName"    → eventName,
        "targetId"     → targetId,
        "form"         → form.orUndefined,
        "properties"   → properties.toJSDictionary,
        "ignoreErrors" → ignoreErrors,
        "showProgress" → showProgress,
        "incremental"  → incremental
      )
    )

  // Creator for events that don't have a `targetId`
  def apply(
    eventName    : String,
    form         : html.Form
  ): AjaxServerEvent =
    new AjaxServerEvent(
      js.Dictionary[js.Any](
        "form"      → form,
        "eventName" → eventName
      )
    )

  def dispatchEvent(event: AjaxServerEvent): Unit =
    AjaxServer.fireEvents(
      events      = js.Array(event),
      incremental = event.incremental
    )
}

@JSExportTopLevel("ORBEON.xforms.server.AjaxServer.Event")
class AjaxServerEvent(args: js.Any*) extends js.Object {

  require(args.nonEmpty)

  import AjaxServerEvent._

  // Normalize parameters:
  //
  // - The "older" version has "n" parameters.
  // - The "newer" version has a single parameter which is an object.
  //
  private val argsDict =
    if (args.length > 1)
      js.Dictionary(AjaxServerEvent.ParamNames.zip(args): _*)
    else
      args.head.asInstanceOf[js.Dictionary[js.Any]]

  import scala.reflect.ClassTag

  private def checkT[T: ClassTag](v: Any): Option[T] = v match { case t: T ⇒ Some(t); case _ ⇒ None }

  private def checkArgOpt[T: ClassTag](name: String): Option[T] =
    argsDict.get(name).flatMap(checkT[T](_))

  private def checkArg[T: ClassTag](name: String, default: ⇒ T): T =
    checkArgOpt[T](name).getOrElse(default)

  val eventName: String =
    checkArg[String](
      "eventName",
      throw new IllegalArgumentException("eventName")
    )

  val (form: html.Form, targetId: String) = {

    val formOpt     = checkArgOpt[html.Form]("form")
    val targetIdOpt = checkArgOpt[String]("targetId")

    (formOpt, targetIdOpt) match {
      case (Some(form), None) if eventName == XXFormsAllEventsRequired || eventName == XXFormsServerEvents ⇒
        form → null
      case (Some(_), None) ⇒
        throw new IllegalArgumentException("targetId")
      case (None, Some(targetId)) ⇒
        Option(dom.document.getElementById(targetId)) flatMap (e ⇒ Controls.getForm(e).toOption) match {
          case Some(form) ⇒
            // TODO: namespace must match!
            form → targetId
          case None ⇒
            Support.getFirstForm → targetId
        }
      case (Some(form), Some(targetId)) ⇒
        form → Support.adjustIdNamespace(form, targetId)._2
    }
  }

  val properties: js.Dictionary[js.Any] = {
    // Use `js.Object` as `ClassTag` doesn't support `js.Dictionary`
    val dict = checkArg[js.Object]("properties", new js.Object).asInstanceOf[js.Dictionary[js.Any]]
    dict ++= checkArgOpt[String]("value") map (v ⇒ "value" → (v: js.Any)) // `value` is now a property
    dict
  }

  val incremental : Boolean = checkArg[Boolean]("incremental",  DefaultIncremental)  // passed separately to `fireEvents()`
  val ignoreErrors: Boolean = checkArg[Boolean]("ignoreErrors", DefaultIgnoreErrors) // used by `AjaxServer`
  val showProgress: Boolean = checkArg[Boolean]("showProgress", DefaultShowProgress) // used by `AjaxServer`
}
