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

import cats.syntax.option._
import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.xforms.EventNames._
import org.orbeon.xforms.rpc.{WireAjaxEvent, WireAjaxEventWithTarget, WireAjaxEventWithoutTarget}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.Dictionary
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportTopLevel


object AjaxEvent {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.AjaxEvent")

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
  ): AjaxEvent =
    new AjaxEvent(
      js.Dictionary[js.Any](
        "eventName"    -> eventName,
        "targetId"     -> targetId,
        "form"         -> form.orUndefined,
        "properties"   -> properties.toJSDictionary,
        "ignoreErrors" -> ignoreErrors,
        "showProgress" -> showProgress,
        "incremental"  -> incremental
      )
    )

  // Creator for events that don't have a `targetId`
  def apply(
    eventName    : String,
    form         : html.Form
  ): AjaxEvent =
    new AjaxEvent(
      js.Dictionary[js.Any](
        "form"      -> form,
        "eventName" -> eventName
      )
    )
}

@JSExportTopLevel("OrbeonAjaxEvent")
class AjaxEvent(args: js.Any*) extends js.Object {

  require(args.nonEmpty)

  import AjaxEvent._

  // Normalize parameters:
  //
  // - The "older" version has "n" parameters.
  // - The "newer" version has a single parameter which is an object.
  //
  private val argsDict: Dictionary[js.Any] =
    if (args.length > 1)
      js.Dictionary(AjaxEvent.ParamNames.zip(args): _*)
    else
      args.head.asInstanceOf[js.Dictionary[js.Any]]

  import scala.reflect.ClassTag

  private def checkT[T: ClassTag](v: Any): Option[T] = v match { case t: T => Some(t); case _ => None }

  private def checkArgOpt[T: ClassTag](name: String): Option[T] =
    argsDict.get(name).flatMap(checkT[T](_))

  private def checkArg[T: ClassTag](name: String, default: => T): T =
    checkArgOpt[T](name).getOrElse(default)

  val eventName: String =
    checkArg[String](
      "eventName",
      throw new IllegalArgumentException("eventName")
    )

  val (form: html.Form, targetIdOpt: Option[String]) = {

    val formOpt     = checkArgOpt[html.Form]("form")
    val targetIdOpt = checkArgOpt[String]("targetId")

    (formOpt, targetIdOpt) match {
      case (Some(form), None) if EventsWithoutTargetId(eventName) =>
        form -> None
      case (Some(_), None) =>
        throw new IllegalArgumentException("targetId")
      case (None, Some(targetId)) =>
        Option(dom.document.getElementById(targetId)) map (e => Page.findAncestorOrSelfHtmlFormFromHtmlElemOrDefault(e.asInstanceOf[html.Element])) match {
          case Some(form) =>
            form -> targetId.some // here we could check that the namespaces match!
          case None =>
            Support.getFirstForm -> targetId.some
        }
      case (Some(form), Some(targetId)) =>
        form -> Support.adjustIdNamespace(form, targetId)._2.some
    }
  }

  var properties: js.Dictionary[js.Any] = {
    // Don't use `checkArgOpt` to get the value of the "properties" argument, as `checkArgOpt` ends up doing an
    // `instanceof` which fails if the properties are passed from another window; instead just trust that if properties
    // are passed, they are an object
    val dict = argsDict
        .get("properties")
        // Handle both the case where no `properties` where passed, and where `undefined` was passed
        .flatMap(p => if (js.isUndefined(p)) None else Some(p))
        .getOrElse(new js.Object)
        .asInstanceOf[js.Dictionary[js.Any]]
    dict ++= checkArgOpt[String]("value") map (v => "value" -> (v: js.Any)) // `value` is now a property
    dict
  }

  val incremental : Boolean = checkArg[Boolean]("incremental",  DefaultIncremental)  // passed separately to `fireEvents()`
  val ignoreErrors: Boolean = checkArg[Boolean]("ignoreErrors", DefaultIgnoreErrors) // used by `AjaxServer`
  val showProgress: Boolean = checkArg[Boolean]("showProgress", DefaultShowProgress) // used by `AjaxServer`

  logger.debug(toString)

  def toWireAjaxEvent: WireAjaxEvent =
    targetIdOpt match {
      case Some(targetId) =>
        WireAjaxEventWithTarget(
          eventName,
          targetId,
          properties mapValues (_.toString) toMap
        )
      case None =>
        WireAjaxEventWithoutTarget(
          eventName,
          properties mapValues (_.toString) toMap
        )
    }

  override def toString =
    s"AjaxServerEvent(eventName = `$eventName`, targetIdOpt = `$targetIdOpt`, form = `${form.id}`, incremental = `$incremental`, ignoreErrors = `$ignoreErrors`, showProgress = `$showProgress`, properties = `${ properties.iterator map (kv => s"${kv._1} => ${kv._2}") mkString "/"}`)"
}
