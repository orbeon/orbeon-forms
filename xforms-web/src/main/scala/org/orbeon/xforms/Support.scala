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

import org.orbeon.oxf.util.StringUtils._
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.dom.experimental.domparser.{DOMParser, SupportedType}
import org.scalajs.dom.{Element, EventTarget, FocusEvent, FormData, html}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.|
import scala.util.control.NonFatal


object Support {

  def formElemOrDefaultForm(formElem: js.UndefOr[html.Form]): html.Form =
    formElem getOrElse getFirstForm

  def getFirstForm: html.Form =
    $(dom.document.forms).filter(".xforms-form")(0).asInstanceOf[html.Form]

  def adjustIdNamespace(
    formElem : js.UndefOr[html.Form],
    targetId : String
  ): (html.Element, String) = {

    val form   = Support.formElemOrDefaultForm(formElem)
    val formId = form.id

    // See comment on `namespaceIdIfNeeded`
    form -> Page.namespaceIdIfNeeded(formId, targetId)
  }

  def parseStringAsXml(xmlString: String): Option[dom.Document] =
    try {
      Option((new DOMParser).parseFromString(xmlString, SupportedType.`application/xml`)) filter
        (_.documentElement.getElementsByTagName("parsererror").length == 0)
    } catch {
      case NonFatal(_) =>
        // If `xmlString` can't be parsed, `parseFromString()` is expected to return a document with `<parsererror/>`
        // (which is crazy), but IE11 throws an exception instead.
        None
    }

  def getLocalName(n: dom.Node): Option[String] =
    Option(n) collect { case e: dom.Element => getLocalName(e)}

  def getLocalName(e: dom.Element): String =
    if (e.tagName.contains(":")) e.tagName.substringAfter(":") else e.tagName

  def fetchText(
    url         : String,
    requestBody : String | FormData,
    contentType : Option[String],
    formId      : String,
    abortSignal : Option[AbortSignal]
  ): Future[(Int, String, Option[dom.Document])] = {

    val fetchPromise =
      Fetch.fetch(
        url,
        new RequestInit {
          var method         : js.UndefOr[HttpMethod]         = HttpMethod.POST
          var body           : js.UndefOr[BodyInit]           = requestBody
          var headers        : js.UndefOr[HeadersInit]        = contentType map (ct => js.defined(js.Dictionary("Content-Type" -> ct))) getOrElse js.undefined
          var referrer       : js.UndefOr[String]             = js.undefined
          var referrerPolicy : js.UndefOr[ReferrerPolicy]     = js.undefined
          var mode           : js.UndefOr[RequestMode]        = js.undefined
          var credentials    : js.UndefOr[RequestCredentials] = js.undefined
          var cache          : js.UndefOr[RequestCache]       = js.undefined
          var redirect       : js.UndefOr[RequestRedirect]    = RequestRedirect.follow // only one supported with the polyfill
          var integrity      : js.UndefOr[String]             = js.undefined
          var keepalive      : js.UndefOr[Boolean]            = js.undefined
          var signal         : js.UndefOr[AbortSignal]        = abortSignal map (js.defined.apply) getOrElse js.undefined
          var window         : js.UndefOr[Null]               = null
        }
      )

    for {
      response <- fetchPromise.toFuture
      text     <- response.text().toFuture
    } yield
      (
        response.status,
        text,
        Support.parseStringAsXml(text)
      )
  }

  def stopFocusOutPropagation(
    element     : Element,
    eventTarget : FocusEvent => EventTarget,
    targetClass : String
  ): Unit = {
    element.addEventListener(
      "focusout",
      (event: FocusEvent) => {
        // 2020-12-22: Noted that `relatedTarget` can be `null` in plain XForms.
        // Not sure why, but protecting against crash here with pattern match.
        eventTarget(event) match {
          case relatedTarget: dom.html.Element =>
            if (relatedTarget.classList.contains(targetClass))
              event.stopPropagation()
          case _ =>
        }
      },
      useCapture = true
    )
  }
}
